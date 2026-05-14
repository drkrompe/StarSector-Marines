package com.dillon.starsectormarines.assets.animation;

import com.dillon.starsectormarines.assets.animation.Animation.KeyFrame;
import com.dillon.starsectormarines.assets.animation.Animation.NodeAnimation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retargets animations from a source skeleton onto a target skeleton using
 * world-space delta transfer.
 *
 * <p>The algorithm:
 * <ol>
 *   <li>For each frame, compute source bone world-space animated rotations by walking the hierarchy.</li>
 *   <li>For each mapped bone, compute the world-space delta: {@code worldDelta = srcWorldAnim * inv(srcWorldRest)}</li>
 *   <li>Apply the delta to the target bone's world rest: {@code tgtWorldAnim = worldDelta * tgtWorldRest}</li>
 *   <li>Convert back to local: {@code tgtLocalAnim = inv(tgtParentWorldAnim) * tgtWorldAnim}</li>
 * </ol>
 *
 * <p>World-space deltas are agnostic to bone axis conventions (skeletal vs pin bones),
 * making this approach work for any source/target skeleton combination without per-convention strategies.
 *
 * <p>For split bones (e.g. B-chest → Spine1 + Spine2), the world delta is distributed
 * fractionally via slerp so the chain composes to the exact desired rotation.
 */
public class AnimationRetargeter {

    /**
     * Retargets an animation from a source skeleton onto a target skeleton.
     *
     * @param sourceAnimation The animation to retarget (bone names match source skeleton).
     * @param sourceSkeleton  The skeleton the animation was authored for.
     * @param targetSkeleton  The skeleton to retarget onto (e.g., lowpoly-human mesh skeleton).
     * @param boneMap         Maps source bone name → target bone name.
     * @param splitMap        Maps source bone name → array of target bone names for split bones
     *                        (must be in parent→child chain order).
     * @return A new Animation with keyframes expressed in the target skeleton's local space.
     */
    public static Animation retarget(Animation sourceAnimation, Skeleton sourceSkeleton,
                                     Skeleton targetSkeleton, Map<String, String> boneMap,
                                     Map<String, String[]> splitMap) {

        // Build lookup maps
        Map<String, Bone> sourceBonesByName = new HashMap<>();
        for (Bone b : sourceSkeleton.getBones()) sourceBonesByName.put(b.getName(), b);
        Map<String, Bone> targetBonesByName = new HashMap<>();
        for (Bone b : targetSkeleton.getBones()) targetBonesByName.put(b.getName(), b);
        Map<String, NodeAnimation> sourceAnimByNode = new HashMap<>();
        for (NodeAnimation na : sourceAnimation.getNodeAnimations()) sourceAnimByNode.put(na.getNodeName(), na);

        // Fix quaternion sign discontinuities in source animation data.
        // Assimp can emit equivalent quaternions with flipped signs (q and -q represent
        // the same rotation), which causes world-space deltas to take the 360° arc.
        for (NodeAnimation na : sourceAnimation.getNodeAnimations()) {
            enforceQuaternionContinuity(na.getRotationKeys());
        }

        // Pre-compute world-space rest rotations and positions
        Map<String, Quaternionf> srcWorldRest = computeWorldRestRotations(sourceSkeleton);
        Map<String, Quaternionf> tgtWorldRest = computeWorldRestRotations(targetSkeleton);
        Map<String, Vector3f> srcWorldRestPos = computeWorldRestPositions(sourceSkeleton, srcWorldRest);
        Map<String, Vector3f> tgtWorldRestPos = computeWorldRestPositions(targetSkeleton, tgtWorldRest);

        // Build reverse mapping: target bone name → source bone name
        Map<String, String> tgtToSrc = new HashMap<>();
        for (var entry : boneMap.entrySet()) tgtToSrc.put(entry.getValue(), entry.getKey());

        // Build split chain info: target bone name → source name + fractional position
        record SplitInfo(String srcName, int index, int total) {}
        Map<String, SplitInfo> splitByTarget = new HashMap<>();
        for (var entry : splitMap.entrySet()) {
            String srcName = entry.getKey();
            String[] tgtNames = entry.getValue();
            for (int i = 0; i < tgtNames.length; i++) {
                splitByTarget.put(tgtNames[i], new SplitInfo(srcName, i, tgtNames.length));
            }
        }

        // Collect keyframe times from the most common rotation key count across channels.
        // Baked FBX animations have uniform sampling, but some bones may have extra
        // intermediate keys with unreliable data. Using the most common count avoids
        // sampling at spurious intermediate times.
        Map<Integer, List<Double>> timesByCount = new HashMap<>();
        for (NodeAnimation na : sourceAnimation.getNodeAnimations()) {
            int count = na.getRotationKeys().size();
            if (count > 0) {
                timesByCount.computeIfAbsent(count, k -> {
                    List<Double> times = new ArrayList<>();
                    for (KeyFrame<Quaternionf> kf : na.getRotationKeys()) {
                        times.add(kf.time());
                    }
                    return times;
                });
            }
        }
        // Pick the keyframe count that appears most frequently
        List<Double> keyframeTimes = new ArrayList<>();
        int bestFrequency = 0;
        for (var entry : timesByCount.entrySet()) {
            int count = entry.getKey();
            long frequency = sourceAnimation.getNodeAnimations().stream()
                    .filter(na -> na.getRotationKeys().size() == count).count();
            if (frequency > bestFrequency) {
                bestFrequency = (int) frequency;
                keyframeTimes = entry.getValue();
            }
        }
        int numFrames = keyframeTimes.size();

        // Per-target-bone output rotation keys
        Map<String, List<KeyFrame<Quaternionf>>> outputRotKeys = new HashMap<>();

        for (int f = 0; f < numFrames; f++) {
            double time = keyframeTimes.get(f);

            // Step 1: Compute source world animated rotations for this frame
            // by walking the source hierarchy (bones are in topological order).
            Map<String, Quaternionf> srcWorldAnim = new HashMap<>();
            for (Bone srcBone : sourceSkeleton.getBones()) {
                Quaternionf localAnim;
                NodeAnimation na = sourceAnimByNode.get(srcBone.getName());
                if (na != null && !na.getRotationKeys().isEmpty()) {
                    localAnim = sampleRotationAtTime(na.getRotationKeys(), time);
                } else {
                    localAnim = decomposeRotation(srcBone.getLocalRestTransform());
                }
                Quaternionf parentWorld = srcBone.getParent() != null
                        ? srcWorldAnim.getOrDefault(srcBone.getParent().getName(), new Quaternionf())
                        : new Quaternionf();
                srcWorldAnim.put(srcBone.getName(), new Quaternionf(parentWorld).mul(localAnim));
            }

            // Step 2: Walk target hierarchy (topological order), computing retargeted
            // world rotations and converting to local.
            Map<String, Quaternionf> tgtWorldAnim = new HashMap<>();
            for (Bone tgtBone : targetSkeleton.getBones()) {
                String tgtName = tgtBone.getName();
                Quaternionf tgtBoneWorldRest = tgtWorldRest.get(tgtName);
                Quaternionf parentWorldAnim = tgtBone.getParent() != null
                        ? tgtWorldAnim.getOrDefault(tgtBone.getParent().getName(), new Quaternionf())
                        : new Quaternionf();

                String srcName = tgtToSrc.get(tgtName);
                SplitInfo split = splitByTarget.get(tgtName);

                if (srcName != null) {
                    // 1:1 bone mapping — full world-space delta transfer
                    Quaternionf srcBoneWorldRest = srcWorldRest.get(srcName);
                    Quaternionf srcBoneWorldAnim = srcWorldAnim.getOrDefault(srcName, srcBoneWorldRest);
                    Quaternionf worldDelta = new Quaternionf(srcBoneWorldAnim)
                            .mul(new Quaternionf(srcBoneWorldRest).invert());

                    Quaternionf boneWorldAnim = new Quaternionf(worldDelta).mul(tgtBoneWorldRest);
                    tgtWorldAnim.put(tgtName, boneWorldAnim);

                    Quaternionf localAnim = new Quaternionf(parentWorldAnim).invert().mul(boneWorldAnim);
                    localAnim.normalize();
                    outputRotKeys.computeIfAbsent(tgtName, k -> new ArrayList<>())
                            .add(new KeyFrame<>(time, localAnim));

                } else if (split != null) {
                    // Split chain — fractional world-space delta
                    Quaternionf srcBoneWorldRest = srcWorldRest.get(split.srcName);
                    Quaternionf srcBoneWorldAnim = srcWorldAnim.getOrDefault(split.srcName, srcBoneWorldRest);
                    Quaternionf worldDelta = new Quaternionf(srcBoneWorldAnim)
                            .mul(new Quaternionf(srcBoneWorldRest).invert());

                    float fraction = (float) (split.index + 1) / split.total;
                    Quaternionf cumulativeDelta = (split.index + 1 == split.total)
                            ? new Quaternionf(worldDelta)
                            : new Quaternionf().slerp(worldDelta, fraction);

                    Quaternionf boneWorldAnim = new Quaternionf(cumulativeDelta).mul(tgtBoneWorldRest);
                    tgtWorldAnim.put(tgtName, boneWorldAnim);

                    Quaternionf localAnim = new Quaternionf(parentWorldAnim).invert().mul(boneWorldAnim);
                    localAnim.normalize();
                    outputRotKeys.computeIfAbsent(tgtName, k -> new ArrayList<>())
                            .add(new KeyFrame<>(time, localAnim));

                } else {
                    // Unmapped bone — stays at world rest
                    tgtWorldAnim.put(tgtName, tgtBoneWorldRest);
                }
            }
        }

        // Build output NodeAnimations with position keys
        List<NodeAnimation> retargetedAnims = new ArrayList<>();
        int mappedCount = 0;
        for (var entry : outputRotKeys.entrySet()) {
            String tgtName = entry.getKey();
            List<KeyFrame<Quaternionf>> rotKeys = entry.getValue();
            Bone tgtBone = targetBonesByName.get(tgtName);

            // Position: root bone (Hips) gets retargeted, others keep rest-pose local position
            List<KeyFrame<Vector3f>> posKeys;
            String srcName = tgtToSrc.get(tgtName);
            if ("Hips".equals(tgtName) && srcName != null) {
                NodeAnimation srcAnim = sourceAnimByNode.get(srcName);
                Bone srcBone = sourceBonesByName.get(srcName);
                if (srcAnim != null && srcBone != null) {
                    posKeys = retargetRootPosition(srcAnim, srcWorldRest, tgtWorldRest,
                            srcWorldRestPos, tgtWorldRestPos, srcBone, tgtBone);
                } else {
                    Vector3f localPos = tgtBone.getLocalRestTransform().getTranslation(new Vector3f());
                    posKeys = List.of(new KeyFrame<>(0.0, localPos));
                }
            } else {
                Vector3f localPos = tgtBone.getLocalRestTransform().getTranslation(new Vector3f());
                posKeys = List.of(new KeyFrame<>(0.0, localPos));
            }

            retargetedAnims.add(new NodeAnimation(tgtName, posKeys, rotKeys, List.of()));
            mappedCount++;
        }

//        log.info("Retargeted animation '{}': {} source channels → {} target channels",
//                sourceAnimation.getName(), sourceAnimation.getNodeAnimations().size(), mappedCount);

        return new Animation(sourceAnimation.getName(), sourceAnimation.getDuration(),
                sourceAnimation.getTicksPerSecond(), retargetedAnims);
    }

    /**
     * Retargets root (Hips) position keys via world-space delta transfer.
     *
     * <p>The source animation's local position is interpreted in the source bone's parent frame,
     * lifted into world via the parent's world rest rotation, then a world delta from source rest
     * is applied to the target world rest, and finally pulled back into the target's parent local
     * frame. Doing the math fully in world space and round-tripping through both parent frames
     * means axis-convention differences between source and target armatures (e.g. Mixamo's -90°X
     * Armature node vs a flat hierarchy) no longer leak into the position track.
     *
     * <p>Caveat: positions are still in the source's distance units. If source and target use
     * different scales (cm vs m), the bone tracks will be physically wrong by that ratio — fix
     * the data so both use the same units, or normalize at clip authoring.
     */
    private static List<KeyFrame<Vector3f>> retargetRootPosition(
            NodeAnimation srcAnim,
            Map<String, Quaternionf> srcWorldRest, Map<String, Quaternionf> tgtWorldRest,
            Map<String, Vector3f> srcWorldRestPos, Map<String, Vector3f> tgtWorldRestPos,
            Bone srcBone, Bone tgtBone) {

        Quaternionf srcParentWorldRot = srcBone.getParent() != null
                ? srcWorldRest.getOrDefault(srcBone.getParent().getName(), new Quaternionf())
                : new Quaternionf();
        Vector3f srcParentWorldPos = srcBone.getParent() != null
                ? srcWorldRestPos.getOrDefault(srcBone.getParent().getName(), new Vector3f())
                : new Vector3f();
        Quaternionf tgtParentWorldRot = tgtBone.getParent() != null
                ? tgtWorldRest.getOrDefault(tgtBone.getParent().getName(), new Quaternionf())
                : new Quaternionf();
        Vector3f tgtParentWorldPos = tgtBone.getParent() != null
                ? tgtWorldRestPos.getOrDefault(tgtBone.getParent().getName(), new Vector3f())
                : new Vector3f();

        Vector3f srcSelfWorldRest = srcWorldRestPos.getOrDefault(srcBone.getName(), new Vector3f());
        Vector3f tgtSelfWorldRest = tgtWorldRestPos.getOrDefault(tgtBone.getName(), new Vector3f());

        Quaternionf invTgtParent = new Quaternionf(tgtParentWorldRot).invert();

        List<KeyFrame<Vector3f>> posKeys = new ArrayList<>();
        for (KeyFrame<Vector3f> kf : srcAnim.getPositionKeys()) {
            // Source local → source world.
            Vector3f srcWorldAnim = srcParentWorldRot.transform(new Vector3f(kf.value()), new Vector3f())
                    .add(srcParentWorldPos);
            // World delta from rest → applied to target rest.
            Vector3f tgtWorldAnim = new Vector3f(srcWorldAnim).sub(srcSelfWorldRest).add(tgtSelfWorldRest);
            // Target world → target local (parent frame).
            Vector3f tgtLocalAnim = invTgtParent.transform(new Vector3f(tgtWorldAnim).sub(tgtParentWorldPos), new Vector3f());
            posKeys.add(new KeyFrame<>(kf.time(), tgtLocalAnim));
        }
        return posKeys;
    }

    /**
     * Computes the world-space rest rotation for every bone by walking the hierarchy.
     */
    private static Map<String, Quaternionf> computeWorldRestRotations(Skeleton skeleton) {
        Map<String, Quaternionf> worldRots = new HashMap<>();
        for (Bone bone : skeleton.getBones()) {
            Quaternionf localRot = decomposeRotation(bone.getLocalRestTransform());
            if (bone.getParent() != null) {
                Quaternionf parentWorld = worldRots.getOrDefault(bone.getParent().getName(), new Quaternionf());
                worldRots.put(bone.getName(), new Quaternionf(parentWorld).mul(localRot));
            } else {
                worldRots.put(bone.getName(), localRot);
            }
        }
        return worldRots;
    }

    /**
     * Computes the world-space rest position for every bone by walking the hierarchy.
     * Each bone's local position is rotated by its parent's world rotation before adding
     * to the parent's world position.
     */
    private static Map<String, Vector3f> computeWorldRestPositions(Skeleton skeleton,
                                                                    Map<String, Quaternionf> worldRotations) {
        Map<String, Vector3f> worldPositions = new HashMap<>();
        for (Bone bone : skeleton.getBones()) {
            Vector3f localPos = new Vector3f();
            bone.getLocalRestTransform().getTranslation(localPos);
            if (bone.getParent() != null) {
                Vector3f parentWorldPos = worldPositions.getOrDefault(bone.getParent().getName(), new Vector3f());
                Quaternionf parentWorldRot = worldRotations.getOrDefault(bone.getParent().getName(), new Quaternionf());
                Vector3f rotatedLocal = parentWorldRot.transform(new Vector3f(localPos));
                worldPositions.put(bone.getName(), new Vector3f(parentWorldPos).add(rotatedLocal));
            } else {
                worldPositions.put(bone.getName(), localPos);
            }
        }
        return worldPositions;
    }

    /**
     * Samples a rotation key sequence at a specific time. If the channel has keys at
     * different times than requested (e.g. extra intermediate keys), this finds the
     * closest key by time rather than assuming index alignment.
     */
    private static Quaternionf sampleRotationAtTime(List<KeyFrame<Quaternionf>> keys, double time) {
        // Fast path: if there's an exact or near-exact time match at a reasonable index, use it
        int bestIdx = 0;
        double bestDist = Math.abs(keys.get(0).time() - time);
        for (int i = 1; i < keys.size(); i++) {
            double dist = Math.abs(keys.get(i).time() - time);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
            // Keys are sorted by time — once we start moving away, stop
            if (keys.get(i).time() > time + bestDist) break;
        }
        return keys.get(bestIdx).value();
    }

    /**
     * Ensures consecutive quaternions in a rotation key sequence are in the same
     * hemisphere. Quaternions q and -q represent the same rotation, but interpolation
     * and delta computation between opposite-sign quaternions takes the long arc (360°).
     * Negating to keep {@code dot(q[i], q[i-1]) >= 0} fixes this.
     */
    private static void enforceQuaternionContinuity(List<KeyFrame<Quaternionf>> rotationKeys) {
        for (int i = 1; i < rotationKeys.size(); i++) {
            Quaternionf prev = rotationKeys.get(i - 1).value();
            Quaternionf curr = rotationKeys.get(i).value();
            if (prev.dot(curr) < 0) {
                curr.set(-curr.x, -curr.y, -curr.z, -curr.w);
            }
        }
    }

    private static Quaternionf decomposeRotation(Matrix4f matrix) {
        Quaternionf rot = new Quaternionf();
        matrix.getUnnormalizedRotation(rot);
        rot.normalize();
        return rot;
    }
}
