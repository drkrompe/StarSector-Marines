package com.dillon.starsectormarines.assets.mesh;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * Simple container class to hold generated mesh data.
 * It now also calculates and stores the axis-aligned bounding box of the mesh.
 */
public class MeshData {
    @Getter
    public final float[] vertices;
    @Getter
    public final int[] indices;
    @Getter
    private final int vertexStride;

    /**
     * Optional: Bone weights for skeletal animation (4 weights per vertex).
     */
    @Getter
    @Setter
    private float[] weights;

    /**
     * Optional: Bone indices for skeletal animation (4 indices per vertex).
     */
    @Getter
    @Setter
    private int[] jointIndices;

    /**
     * -- GETTER --
     *
     * @return The minimum corner of the axis-aligned bounding box.
     */
    @Getter
    private final Vector3f min = new Vector3f();
    /**
     * -- GETTER --
     *
     * @return The maximum corner of the axis-aligned bounding box.
     */
    @Getter
    private final Vector3f max = new Vector3f();

    /**
     * The primary constructor for MeshData.
     * @param vertices The raw vertex data, which can be interleaved.
     * @param indices The index data.
     * @param vertexStride The number of floats that represent a single vertex (e.g., 3 for P, 6 for P+N, 8 for P+N+UV).
     */
    public MeshData(float[] vertices, int[] indices, int vertexStride) {
        this.vertices = vertices;
        this.indices = indices;
        this.vertexStride = vertexStride;
        this.calculateBounds(vertexStride);
    }

    /**
     * Convenience constructor for dynamically built lists.
     * @param vertexList The dynamically built list of vertex data.
     * @param indexList The dynamically built list of index data.
     * @param vertexStride The number of floats per vertex.
     */
    public MeshData(List<Float> vertexList, List<Integer> indexList, int vertexStride) {
        // Convert vertex list to a primitive float array
        this.vertices = new float[vertexList.size()];
        for (int i = 0; i < vertexList.size(); i++) {
            this.vertices[i] = vertexList.get(i);
        }

        // Convert index list to a primitive int array
        this.indices = new int[indexList.size()];
        for (int i = 0; i < indexList.size(); i++) {
            this.indices[i] = indexList.get(i);
        }
        this.vertexStride = vertexStride;

        this.calculateBounds(vertexStride);
    }

    private void calculateBounds(int vertexStride) {
        if (vertices == null || vertices.length == 0) {
            min.set(0, 0, 0);
            max.set(0, 0, 0);
            return;
        }
        if (vertexStride < 3) {
            throw new IllegalArgumentException("Vertex stride must be at least 3 (for x, y, z). Received: " + vertexStride);
        }

        min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (int i = 0; i < vertices.length; i += vertexStride) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];
            min.x = Math.min(min.x, x);
            min.y = Math.min(min.y, y);
            min.z = Math.min(min.z, z);
            max.x = Math.max(max.x, x);
            max.y = Math.max(max.y, y);
            max.z = Math.max(max.z, z);
        }
    }

}
