package com.dillon.starsectormarines.assets.animation;

import com.dillon.starsectormarines.assets.animation.Animation.KeyFrame;
import com.dillon.starsectormarines.assets.animation.Animation.NodeAnimation;
import lombok.extern.log4j.Log4j2;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVectorKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class AnimationExtractor {

    public static Skeleton extractSkeleton(AIScene scene, Map<String, Integer> boneNameToIndex) {
        // Collect inverse bind matrices from mesh bone references
        Map<String, Matrix4f> boneOffsetMatrices = new HashMap<>();
        int numMeshes = scene.mNumMeshes();
        for (int i = 0; i < numMeshes; i++) {
            AIMesh mesh = AIMesh.create(scene.mMeshes().get(i));
            int numBones = mesh.mNumBones();
            for (int j = 0; j < numBones; j++) {
                AIBone bone = AIBone.create(mesh.mBones().get(j));
                String boneName = bone.mName().dataString();
                if (!boneOffsetMatrices.containsKey(boneName)) {
                    boneOffsetMatrices.put(boneName, toMatrix4f(bone.mOffsetMatrix()));
                }
            }
        }

        // Build Skeleton Hierarchy — all node transforms are passed through as-is from Assimp.
        // Assimp's FBX importer places Z-up→Y-up conversion rotations on structural nodes
        // (scene root, armature containers). These rotations are needed so the bone hierarchy
        // ends up in the same Y-up space as the converted mesh vertices and offset matrices.
        List<Bone> allBones = new ArrayList<>();
        Bone rootBone = buildSkeleton(scene.mRootNode(), null, allBones, boneNameToIndex, boneOffsetMatrices);
        return new Skeleton(allBones, rootBone);
    }

    public static List<Animation> extractAnimations(AIScene scene) {
        List<Animation> animations = new ArrayList<>();
        int numAnimations = scene.mNumAnimations();
        if (scene.mAnimations() == null) return animations;

        for (int i = 0; i < numAnimations; i++) {
            AIAnimation aiAnim = AIAnimation.create(scene.mAnimations().get(i));
            String name = aiAnim.mName().dataString();
            double duration = aiAnim.mDuration();
            double ticksPerSecond = aiAnim.mTicksPerSecond() != 0 ? aiAnim.mTicksPerSecond() : 25.0;

            List<NodeAnimation> nodeAnimations = new ArrayList<>();
            int numChannels = aiAnim.mNumChannels();
            for (int j = 0; j < numChannels; j++) {
                AINodeAnim channel = AINodeAnim.create(aiAnim.mChannels().get(j));
                String nodeName = channel.mNodeName().dataString();

                List<KeyFrame<Vector3f>> posKeys = new ArrayList<>();
                List<KeyFrame<Quaternionf>> rotKeys = new ArrayList<>();
                List<KeyFrame<Vector3f>> scaleKeys = new ArrayList<>();

                for (int k = 0; k < channel.mNumPositionKeys(); k++) {
                    AIVectorKey key = channel.mPositionKeys().get(k);
                    posKeys.add(new KeyFrame<>(key.mTime(), new Vector3f(key.mValue().x(), key.mValue().y(), key.mValue().z())));
                }
                for (int k = 0; k < channel.mNumRotationKeys(); k++) {
                    AIQuatKey key = channel.mRotationKeys().get(k);
                    rotKeys.add(new KeyFrame<>(key.mTime(), new Quaternionf(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w())));
                }
                // Scale keys are forced to (1,1,1). FBX files frequently carry unit
                // conversion scales on armature/root nodes (e.g. 100x for cm→m). The
                // rest-pose localRestTransform already includes these scales for the bone
                // hierarchy, and inverse bind matrices are in the final scaled space.
                // Passing through animation scale keys would double-apply the conversion.
                for (int k = 0; k < channel.mNumScalingKeys(); k++) {
                    AIVectorKey key = channel.mScalingKeys().get(k);
                    scaleKeys.add(new KeyFrame<>(key.mTime(), new Vector3f(1.0f)));
                }

                nodeAnimations.add(new NodeAnimation(nodeName, posKeys, rotKeys, scaleKeys));
            }
            animations.add(new Animation(name, duration, ticksPerSecond, nodeAnimations));
        }
        return animations;
    }

    private static Bone buildSkeleton(AINode node, Bone parent, List<Bone> allBones,
                                       Map<String, Integer> boneNameToIndex,
                                       Map<String, Matrix4f> boneOffsetMatrices) {
        String nodeName = node.mName().dataString();
        Matrix4f offsetMatrix = boneOffsetMatrices.getOrDefault(nodeName, new Matrix4f()); // Identity if not a skinned bone
        Matrix4f localRestTransform = toMatrix4f(node.mTransformation());

        if (boneNameToIndex.containsKey(nodeName)) {
            log.warn("Duplicate bone/node name detected in hierarchy: '{}'. Skipping duplicate. Existing index: {}",
                nodeName, boneNameToIndex.get(nodeName));
            return null;
        }

        int index = allBones.size();
        Bone bone = new Bone(index, nodeName, offsetMatrix, localRestTransform);
        allBones.add(bone);
        boneNameToIndex.put(nodeName, index);

        if (parent != null) {
            parent.addChild(bone);
        }

        int numChildren = node.mNumChildren();
        for (int i = 0; i < numChildren; i++) {
            AINode childNode = AINode.create(node.mChildren().get(i));
            buildSkeleton(childNode, bone, allBones, boneNameToIndex, boneOffsetMatrices);
        }

        return bone;
    }

    private static Matrix4f toMatrix4f(AIMatrix4x4 m) {
        return new Matrix4f(
            m.a1(), m.b1(), m.c1(), m.d1(),
            m.a2(), m.b2(), m.c2(), m.d2(),
            m.a3(), m.b3(), m.c3(), m.d3(),
            m.a4(), m.b4(), m.c4(), m.d4()
        );
    }
}
