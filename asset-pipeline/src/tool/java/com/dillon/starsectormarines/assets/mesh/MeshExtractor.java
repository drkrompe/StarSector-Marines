package com.dillon.starsectormarines.assets.mesh;

import com.dillon.starsectormarines.assets.material.MaterialInfo;
import com.dillon.starsectormarines.assets.mesh.MeshData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVertexWeight;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshExtractor {

    public static Map<Integer, MeshData> extract(AIScene scene, List<MaterialInfo> materials, Map<String, Integer> boneNameToIndex) {
        Map<Integer, MeshData> meshDataParts = new HashMap<>();

        // Walk the node hierarchy to compute the accumulated world transform for each mesh.
        // For non-skinned meshes this bakes the FBX root node's coordinate-system rotation
        // (e.g. Z-up → Y-up) into the vertex data. For skinned meshes the skeleton hierarchy
        // already carries that rotation, so we use identity to avoid double-applying it.
        Map<Integer, Matrix4f> meshTransforms = computeMeshTransforms(scene);

        int numMaterials = scene.mNumMaterials();
        // Heuristic: If we have only 1 instancePayload info but the scene has multiple materials,
        // it implies we are in "Palette Mode" (generated texture from colors).
        boolean isPaletteMode = materials.size() == 1 && numMaterials > 1;

        // Temporary storage for merging meshes per instancePayload index
        // Key: Material Index (or 0 if palette mode)
        Map<Integer, List<Float>> verticesMap = new HashMap<>();
        Map<Integer, List<Integer>> indicesMap = new HashMap<>();
        Map<Integer, List<Float>> weightsMap = new HashMap<>();
        Map<Integer, List<Integer>> jointIndicesMap = new HashMap<>();
        Map<Integer, Integer> vertexOffsetMap = new HashMap<>();

        int numMeshes = scene.mNumMeshes();

        for (int i = 0; i < numMeshes; i++) {
            AIMesh mesh = AIMesh.create(scene.mMeshes().get(i));
            int materialIndex = mesh.mMaterialIndex();

            // If palette mode, everything goes to slot 0.
            // Otherwise, it goes to the actual instancePayload index.
            int targetIndex = isPaletteMode ? 0 : materialIndex;

            // Ensure lists exist
            verticesMap.putIfAbsent(targetIndex, new ArrayList<>());
            indicesMap.putIfAbsent(targetIndex, new ArrayList<>());
            weightsMap.putIfAbsent(targetIndex, new ArrayList<>());
            jointIndicesMap.putIfAbsent(targetIndex, new ArrayList<>());
            vertexOffsetMap.putIfAbsent(targetIndex, 0);

            List<Float> vertices = verticesMap.get(targetIndex);
            List<Integer> indices = indicesMap.get(targetIndex);
            List<Float> weights = weightsMap.get(targetIndex);
            List<Integer> jointIndices = jointIndicesMap.get(targetIndex);
            int currentOffset = vertexOffsetMap.get(targetIndex);

            // Skinned meshes: identity transform (skeleton handles coordinate conversion).
            // Non-skinned meshes: apply the node's accumulated world transform.
            boolean isSkinned = mesh.mNumBones() > 0;
            Matrix4f nodeTransform = isSkinned ? new Matrix4f() : meshTransforms.getOrDefault(i, new Matrix4f());

            processMesh(mesh, vertices, indices, currentOffset, materialIndex, numMaterials, isPaletteMode, weights, jointIndices, nodeTransform);
            processBones(mesh, boneNameToIndex, weights, jointIndices, currentOffset);

            vertexOffsetMap.put(targetIndex, currentOffset + mesh.mNumVertices());
        }

        // Convert lists to MeshData
        for (Integer key : verticesMap.keySet()) {
            List<Float> vertices = verticesMap.get(key);
            List<Integer> indices = indicesMap.get(key);
            List<Float> weights = weightsMap.get(key);
            List<Integer> jointIndices = jointIndicesMap.get(key);

            int stride = 8;
            MeshData meshData = new MeshData(vertices, indices, stride);

            // Convert weights and indices to arrays
            if (!weights.isEmpty()) {
                float[] weightsArray = new float[weights.size()];
                for (int i = 0; i < weights.size(); i++) weightsArray[i] = weights.get(i);
                meshData.setWeights(weightsArray);
            }
            if (!jointIndices.isEmpty()) {
                int[] indicesArray = new int[jointIndices.size()];
                for (int i = 0; i < jointIndices.size(); i++) indicesArray[i] = jointIndices.get(i);
                meshData.setJointIndices(indicesArray);
            }

            meshDataParts.put(key, meshData);
        }

        return meshDataParts;
    }

    private static void processMesh(AIMesh mesh, List<Float> vertices, List<Integer> indices, int vertexOffset, int materialIndex, int numMaterials, boolean usePaletteMode, List<Float> weights, List<Integer> jointIndices, Matrix4f nodeTransform) {
        AIVector3D.Buffer aiVertices = mesh.mVertices();
        AIVector3D.Buffer aiNormals = mesh.mNormals();
        AIVector3D.Buffer aiTexCoords = mesh.mTextureCoords(0);

        // Precompute normal matrix (inverse-transpose of upper-left 3x3) for transforming normals.
        Matrix3f normalMatrix = new Matrix3f();
        nodeTransform.normal(normalMatrix);

        Vector3f pos = new Vector3f();
        Vector3f norm = new Vector3f();

        int numVertices = mesh.mNumVertices();
        for (int i = 0; i < numVertices; i++) {
            AIVector3D vertex = aiVertices.get(i);

            pos.set(vertex.x(), vertex.y(), vertex.z());
            pos.mulPosition(nodeTransform);
            vertices.add(pos.x);
            vertices.add(pos.y);
            vertices.add(pos.z);

            if (aiNormals != null) {
                AIVector3D normal = aiNormals.get(i);
                norm.set(normal.x(), normal.y(), normal.z());
                normalMatrix.transform(norm);
                norm.normalize();
                vertices.add(norm.x);
                vertices.add(norm.y);
                vertices.add(norm.z);
            } else {
                vertices.add(0.0f);
                vertices.add(1.0f);
                vertices.add(0.0f);
            }

            // UVs
            if (usePaletteMode && numMaterials > 0) {
                // Bake instancePayload index into UVs
                float u = (materialIndex + 0.5f) / numMaterials;
                float v = 0.5f;
                vertices.add(u);
                vertices.add(v);
            } else if (aiTexCoords != null) {
                AIVector3D texCoord = aiTexCoords.get(i);
                vertices.add(texCoord.x());
                vertices.add(texCoord.y());
            } else {
                vertices.add(0.0f);
                vertices.add(0.0f);
            }

            // Initialize weights and indices (4 per vertex)
            weights.add(0.0f); weights.add(0.0f); weights.add(0.0f); weights.add(0.0f);
            jointIndices.add(0); jointIndices.add(0); jointIndices.add(0); jointIndices.add(0);
        }

        int numFaces = mesh.mNumFaces();
        AIFace.Buffer aiFaces = mesh.mFaces();
        for (int i = 0; i < numFaces; i++) {
            AIFace face = aiFaces.get(i);
            if (face.mNumIndices() != 3) continue;
            IntBuffer faceIndices = face.mIndices();
            indices.add(faceIndices.get(0) + vertexOffset);
            indices.add(faceIndices.get(1) + vertexOffset);
            indices.add(faceIndices.get(2) + vertexOffset);
        }
    }

    /**
     * Walks the Assimp node hierarchy, accumulating parent transforms, and records
     * the world-space transform for each mesh index referenced by a node.
     */
    private static Map<Integer, Matrix4f> computeMeshTransforms(AIScene scene) {
        Map<Integer, Matrix4f> meshTransforms = new HashMap<>();
        walkNodes(scene.mRootNode(), new Matrix4f(), meshTransforms);
        return meshTransforms;
    }

    private static void walkNodes(AINode node, Matrix4f parentTransform, Map<Integer, Matrix4f> meshTransforms) {
        Matrix4f localTransform = toMatrix4f(node.mTransformation());
        Matrix4f worldTransform = new Matrix4f(parentTransform).mul(localTransform);

        int numMeshes = node.mNumMeshes();
        if (numMeshes > 0) {
            IntBuffer meshIndices = node.mMeshes();
            for (int i = 0; i < numMeshes; i++) {
                meshTransforms.put(meshIndices.get(i), new Matrix4f(worldTransform));
            }
        }

        int numChildren = node.mNumChildren();
        for (int i = 0; i < numChildren; i++) {
            AINode child = AINode.create(node.mChildren().get(i));
            walkNodes(child, worldTransform, meshTransforms);
        }
    }

    private static Matrix4f toMatrix4f(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4()
        );
    }

    private static void processBones(AIMesh mesh, Map<String, Integer> boneNameToIndex, List<Float> weights, List<Integer> jointIndices, int vertexOffset) {
        int numBones = mesh.mNumBones();
        // Track how many weights we've assigned to each vertex in this mesh
        byte[] weightsPerVertex = new byte[mesh.mNumVertices()];

        for (int i = 0; i < numBones; i++) {
            AIBone bone = AIBone.create(mesh.mBones().get(i));
            String boneName = bone.mName().dataString();
            int boneIndex = boneNameToIndex.getOrDefault(boneName, -1);

            if (boneIndex == -1) continue;

            int numWeights = bone.mNumWeights();
            for (int j = 0; j < numWeights; j++) {
                AIVertexWeight weight = bone.mWeights().get(j);
                int vertexId = weight.mVertexId();
                float weightValue = weight.mWeight();

                if (vertexId < weightsPerVertex.length && weightsPerVertex[vertexId] < 4) {
                    int slot = weightsPerVertex[vertexId];
                    int globalVertexIndex = vertexOffset + vertexId;

                    // 4 weights/indices per vertex
                    int baseIndex = globalVertexIndex * 4;

                    weights.set(baseIndex + slot, weightValue);
                    jointIndices.set(baseIndex + slot, boneIndex);

                    weightsPerVertex[vertexId]++;
                }
            }
        }
    }
}
