package com.dillon.starsectormarines.assets.pipeline;

import com.dillon.starsectormarines.assets.LoadedModel;
import com.dillon.starsectormarines.assets.material.MaterialInfo;
import com.dillon.starsectormarines.assets.mesh.MeshData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips a programmatically-built {@link LoadedModel} through
 * {@link ModelSerializer} and confirms equality. Proves the runtime read/write path
 * end-to-end without needing an Assimp-parsed FBX.
 */
class ModelSerializerRoundTripTest {

    @Test
    void roundTripsAStaticCube() throws Exception {
        // Two-vertex "mesh" with a single triangle index — minimal but exercises
        // every primitive write/read path in the serializer.
        float[] vertices = {
                -1f, -1f, 0f, 0f, 0f, 1f, 0f, 0f,  // P + N + UV (stride 8)
                 1f, -1f, 0f, 0f, 0f, 1f, 1f, 0f,
                 0f,  1f, 0f, 0f, 0f, 1f, 0.5f, 1f,
        };
        int[] indices = {0, 1, 2};
        MeshData mesh = new MeshData(vertices, indices, 8);

        MaterialInfo mat = new MaterialInfo("Test", null, 0.8f, 0.4f, 0.2f, 1f, null, 0, 0);

        LoadedModel original = new LoadedModel(
                Map.of(0, mesh),
                List.of(mat),
                null,              // skeleton (skipped for this test)
                List.of()          // animations
        );

        // Write → bytes → Read
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ModelSerializer.write(original, baos);
        byte[] bytes = baos.toByteArray();
        assertTrue(bytes.length > 0, "serializer wrote zero bytes");

        LoadedModel restored = ModelSerializer.read(new ByteArrayInputStream(bytes));

        // Verify equality field-by-field. LoadedModel is a record but its components
        // (MeshData) aren't records, so .equals() won't deep-compare — we walk it.
        assertEquals(1, restored.meshDataParts().size());
        MeshData restoredMesh = restored.meshDataParts().get(0);
        assertNotNull(restoredMesh);
        assertEquals(8, restoredMesh.getVertexStride());
        assertArrayEquals(vertices, restoredMesh.getVertices());
        assertArrayEquals(indices, restoredMesh.getIndices());
        assertNull(restoredMesh.getWeights());
        assertNull(restoredMesh.getJointIndices());

        assertEquals(1, restored.materials().size());
        MaterialInfo restoredMat = restored.materials().get(0);
        assertEquals("Test", restoredMat.name());
        assertNull(restoredMat.texturePath());
        assertEquals(0.8f, restoredMat.r());
        assertEquals(0.4f, restoredMat.g());
        assertEquals(0.2f, restoredMat.b());
        assertEquals(1f, restoredMat.a());
        assertNull(restoredMat.textureData());

        assertNull(restored.skeleton());
        assertEquals(0, restored.animations().size());
    }
}
