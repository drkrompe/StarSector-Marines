package com.dillon.starsectormarines.assets.animation;

import com.dillon.starsectormarines.assets.animation.Animation.KeyFrame;
import com.dillon.starsectormarines.assets.animation.Animation.NodeAnimation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remaps bone/node names in animation data from a source convention to HumanIK standard.
 * Supports splitting a single source bone's animation across two target bones (e.g., spine).
 */
public class BoneRemapConfig {

    private final Map<String, String> renameMap;
    private final Map<String, String[]> splitMap; // sourceName -> [target1, target2]

    private BoneRemapConfig(Map<String, String> renameMap, Map<String, String[]> splitMap) {
        this.renameMap = renameMap;
        this.splitMap = splitMap;
    }

    /**
     * Returns the HumanIK name for a source bone, or null if the bone should be dropped.
     */
    public String remap(String sourceName) {
        return renameMap.get(sourceName);
    }

    /**
     * Returns the source→target rename map (1:1 mappings only). Suitable for
     * passing directly to {@link AnimationRetargeter#retarget}.
     */
    public Map<String, String> getRenameMap() {
        return Collections.unmodifiableMap(renameMap);
    }

    /**
     * Returns the source→[target1, target2, ...] split map (one source bone
     * fanned out across a chain of target bones). Suitable for passing
     * directly to {@link AnimationRetargeter#retarget}.
     */
    public Map<String, String[]> getSplitMap() {
        return Collections.unmodifiableMap(splitMap);
    }

    /**
     * Returns true if this source bone should be split into two target bones.
     */
    public boolean isSplit(String sourceName) {
        return splitMap.containsKey(sourceName);
    }

    /**
     * Returns the two target bone names for a split bone.
     */
    public String[] getSplitTargets(String sourceName) {
        return splitMap.get(sourceName);
    }

    /**
     * Remaps a list of node animations: renames nodes, splits where configured, drops unmapped nodes.
     */
    public List<NodeAnimation> remapAnimations(List<NodeAnimation> sourceAnims) {
        List<NodeAnimation> result = new ArrayList<>();
        for (NodeAnimation na : sourceAnims) {
            String sourceName = na.getNodeName();

            if (isSplit(sourceName)) {
                String[] targets = getSplitTargets(sourceName);
                result.add(splitNodeAnimation(na, targets[0], 0.5f));
                result.add(splitNodeAnimation(na, targets[1], 0.5f));
            } else {
                String targetName = remap(sourceName);
                if (targetName != null) {
                    result.add(new NodeAnimation(targetName,
                            na.getPositionKeys(), na.getRotationKeys(), na.getScalingKeys()));
                }
                // null mapping = drop the channel (no HumanIK equivalent)
            }
        }
        return result;
    }

    /**
     * Creates a node animation with rotation interpolated partway from identity.
     * factor=0.5 means half the original rotation (used for splitting one bone into two).
     */
    private static NodeAnimation splitNodeAnimation(NodeAnimation source, String targetName, float factor) {
        Quaternionf identity = new Quaternionf();

        List<KeyFrame<Quaternionf>> splitRotKeys = new ArrayList<>(source.getRotationKeys().size());
        for (KeyFrame<Quaternionf> kf : source.getRotationKeys()) {
            Quaternionf halfRot = new Quaternionf(identity).slerp(kf.value(), factor);
            splitRotKeys.add(new KeyFrame<>(kf.time(), halfRot));
        }

        // Position keys: only the first bone in the chain gets position, second gets zero offset
        List<KeyFrame<Vector3f>> posKeys = source.getPositionKeys();

        return new NodeAnimation(targetName, posKeys, splitRotKeys, source.getScalingKeys());
    }

    /**
     * Creates a remap config for the B-* (Blender Rigify) animation convention
     * used by the LOWPOLY_MEDIEVAL_WORLD animation pack.
     *
     * <p>Maps B-* bone names to HumanIK/Mixamo standard names.
     * B-chest is split into Spine1 + Spine2 (half rotation each) to bridge
     * the 2-spine (animation) to 3-spine (mesh) mismatch.
     */
    public static BoneRemapConfig blenderToHumanIK() {
        Map<String, String> rename = new HashMap<>();
        Map<String, String[]> splits = new HashMap<>();

        // Core spine (B-chest splits into Spine1 + Spine2)
        rename.put("B-hips", "Hips");
        rename.put("B-spine", "Spine");
        splits.put("B-chest", new String[]{"Spine1", "Spine2"});

        // Head/neck
        rename.put("B-neck", "Neck");
        rename.put("B-head", "Head");

        // Left arm
        rename.put("B-shoulder.L", "LeftShoulder");
        rename.put("B-upperArm.L", "LeftArm");
        rename.put("B-forearm.L", "LeftForeArm");
        rename.put("B-hand.L", "LeftHand");

        // Left hand fingers
        rename.put("B-indexFinger01.L", "LeftHandIndex1");
        rename.put("B-indexFinger02.L", "LeftHandIndex2");
        rename.put("B-indexFinger03.L", "LeftHandIndex3");
        rename.put("B-middleFinger01.L", "LeftHandMiddle1");
        rename.put("B-middleFinger02.L", "LeftHandMiddle2");
        rename.put("B-middleFinger03.L", "LeftHandMiddle3");
        rename.put("B-ringFinger01.L", "LeftHandRing1");
        rename.put("B-ringFinger02.L", "LeftHandRing2");
        rename.put("B-ringFinger03.L", "LeftHandRing3");
        rename.put("B-pinky01.L", "LeftHandPinky1");
        rename.put("B-pinky02.L", "LeftHandPinky2");
        rename.put("B-pinky03.L", "LeftHandPinky3");
        rename.put("B-thumb01.L", "LeftHandThumb1");
        rename.put("B-thumb02.L", "LeftHandThumb2");
        rename.put("B-thumb03.L", "LeftHandThumb3");

        // Right arm
        rename.put("B-shoulder.R", "RightShoulder");
        rename.put("B-upperArm.R", "RightArm");
        rename.put("B-forearm.R", "RightForeArm");
        rename.put("B-hand.R", "RightHand");

        // Right hand fingers
        rename.put("B-indexFinger01.R", "RightHandIndex1");
        rename.put("B-indexFinger02.R", "RightHandIndex2");
        rename.put("B-indexFinger03.R", "RightHandIndex3");
        rename.put("B-middleFinger01.R", "RightHandMiddle1");
        rename.put("B-middleFinger02.R", "RightHandMiddle2");
        rename.put("B-middleFinger03.R", "RightHandMiddle3");
        rename.put("B-ringFinger01.R", "RightHandRing1");
        rename.put("B-ringFinger02.R", "RightHandRing2");
        rename.put("B-ringFinger03.R", "RightHandRing3");
        rename.put("B-pinky01.R", "RightHandPinky1");
        rename.put("B-pinky02.R", "RightHandPinky2");
        rename.put("B-pinky03.R", "RightHandPinky3");
        rename.put("B-thumb01.R", "RightHandThumb1");
        rename.put("B-thumb02.R", "RightHandThumb2");
        rename.put("B-thumb03.R", "RightHandThumb3");

        // Left leg
        rename.put("B-thigh.L", "LeftUpLeg");
        rename.put("B-shin.L", "LeftLeg");
        rename.put("B-foot.L", "LeftFoot");
        rename.put("B-toe.L", "LeftToeBase");

        // Right leg
        rename.put("B-thigh.R", "RightUpLeg");
        rename.put("B-shin.R", "RightLeg");
        rename.put("B-foot.R", "RightFoot");
        rename.put("B-toe.R", "RightToeBase");

        // Dropped: B-root, B-jaw, B-handProp.L/R, B-spineProxy (no HumanIK equivalent)

        return new BoneRemapConfig(rename, splits);
    }

}
