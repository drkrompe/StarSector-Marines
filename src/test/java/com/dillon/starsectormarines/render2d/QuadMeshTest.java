package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadMeshTest {

    /** A mesh drawn at a scale writes the vertices the same quads drawn one by one at that scale write. */
    @Test
    void meshWritesTheQuadsItRecordedScaledAndMoved() throws Exception {
        SpriteAPI atlas = sprite();
        SpriteAPI legImage = sprite(), splatImage = sprite();
        int[][] slots = {{4, 8, 20, 30}, {40, 0, 16, 16}};
        QuadMesh mesh = new QuadMesh();
        mesh.add(splatImage, 0.1f, -0.2f, 0.6f, 0.5f, 33f, 1f, 1f, 1f, 1f);
        mesh.add(legImage, -0.3f, 0.4f, 0.2f, 0.9f, -127.5f, 0.8f, 0.8f, 0.8f, 0.5f);
        assertTrue(mesh.addressAtlas("atlas", atlas, image -> image == splatImage ? slots[1] : slots[0]));

        float x = 300f, y = 200f, ppc = 24.6f, alpha = 0.7f;
        QuadBatch quadByQuad = new QuadBatch(atlas, 128, 64, 1);
        quadByQuad.appendRotated(40, 0, 16, 16, x + 0.1f * ppc, y - 0.2f * ppc, 0.6f * ppc, 0.5f * ppc, 33f,
                1f, 1f, 1f, alpha);
        quadByQuad.appendRotated(4, 8, 20, 30, x - 0.3f * ppc, y + 0.4f * ppc, 0.2f * ppc, 0.9f * ppc, -127.5f,
                0.8f, 0.8f, 0.8f, 0.5f * alpha);
        QuadBatch meshed = new QuadBatch(atlas, 128, 64, 1);
        mesh.appendTo(meshed, x, y, ppc, alpha);

        assertArrayEquals(data(quadByQuad, "posData"), data(meshed, "posData"), 1e-3f);
        assertArrayEquals(data(quadByQuad, "uvData"), data(meshed, "uvData"));
        assertArrayEquals(data(quadByQuad, "colData"), data(meshed, "colData"), 1e-6f);
    }

    /**
     * A part of a quad is drawn between the whole quad's own corners: all of it is the whole quad to the bit, and the
     * edges a part shares with the whole are the whole's, which is what keeps a trimmed atlas image's coverage.
     */
    @Test
    void aPartOfAQuadKeepsTheEdgesItSharesWithTheWhole() throws Exception {
        SpriteAPI atlas = sprite();
        QuadBatch whole = new QuadBatch(atlas, 128, 64, 4);
        whole.appendRotated(4, 8, 20, 30, 300.3f, 200.7f, 18f, 70.56f, 94.838f, false, 1f, 1f, 1f, 1f);
        QuadBatch all = new QuadBatch(atlas, 128, 64, 4);
        all.appendRotatedPart(4, 8, 20, 30, 300.3f, 200.7f, 18f, 70.56f, 94.838f, false, 0f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f);
        assertArrayEquals(data(whole, "posData"), data(all, "posData"), 0f);
        assertArrayEquals(data(whole, "uvData"), data(all, "uvData"), 0f);

        // The left fifth trimmed away: the right edge (BR, TR) is the whole quad's, bit for bit.
        QuadBatch part = new QuadBatch(atlas, 128, 64, 4);
        part.appendRotatedPart(8, 8, 16, 30, 300.3f, 200.7f, 18f, 70.56f, 94.838f, false, 0.2f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f);
        float[] w = data(whole, "posData"), p = data(part, "posData");
        for (int i = 2; i < 6; i++) assertEquals(w[i], p[i], 0f, "right edge coordinate " + i);
        // And its left edge lies a fifth of the way across.
        assertEquals(w[0] + (w[2] - w[0]) * 0.2f, p[0], 1e-3f);
        assertEquals(w[1] + (w[3] - w[1]) * 0.2f, p[1], 1e-3f);
    }

    /** One image the atlas does not hold sends the whole mesh back to drawing through its images. */
    @Test
    void anImageMissingFromTheAtlasLeavesTheMeshUnaddressed() {
        SpriteAPI held = sprite(), missing = sprite();
        QuadMesh mesh = new QuadMesh();
        mesh.add(held, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f);
        mesh.add(missing, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f);
        assertFalse(mesh.addressAtlas("atlas", sprite(), image -> image == held ? new int[]{0, 0, 1, 1} : null));
    }

    private static SpriteAPI sprite() {
        return (SpriteAPI) Proxy.newProxyInstance(QuadMeshTest.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTextureWidth", "getTextureHeight" -> 1f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static float[] data(QuadBatch batch, String name) throws Exception {
        Field field = QuadBatch.class.getDeclaredField(name);
        field.setAccessible(true);
        float[] all = (float[]) field.get(batch);
        int used = batch.quadCount() * ("colData".equals(name) ? 16 : 8);
        float[] out = new float[used];
        System.arraycopy(all, 0, out, 0, used);
        return out;
    }
}
