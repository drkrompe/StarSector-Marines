package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

/**
 * What a real driver draws out of a resident relief field, patched against
 * rebuilt.
 *
 * <p>{@link ReliefFieldMeshTest} pins the bookkeeping — which cells a change
 * re-resolves and which slot each one lands in — and that is arithmetic a GPU
 * adds nothing to. The question left over is the one residency is actually
 * risky for: whether a field that has been <em>patched</em> across a battle
 * still looks like the field a fresh bake of the same world produces. A
 * resident field that has drifted is wrong however fast it is, and the drift
 * would be invisible — a slot written to the wrong offset, a sub-upload of the
 * wrong length, a released slot that still draws — none of which any CPU-side
 * assertion about the arrays can see, because they would all be about the
 * arrays rather than about the buffers the driver reads.
 *
 * <p><b>Opt-in, and excluded from {@code test}</b>, for
 * {@code GroundShaderGlEvidence}'s reasons: it needs an accelerated driver, and
 * a suite that requires a GPU fails on the machine that has none. Run it with
 * {@code gradlew.bat shaderEvidence}; where no context can be made it reports
 * that it skipped rather than failing.
 */
@Tag("shader-evidence")
class ReliefFieldGlEvidence {

    /** Eight cells at eight pixels each; big enough that a slot landing wrong lands visibly wrong. */
    private static final int CELLS = 8;
    private static final int CELL_PX = 8;
    private static final int SURFACE = CELLS * CELL_PX;

    @Test
    void aPatchedFieldDrawsWhatAFreshBakeOfTheSameWorldDraws() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            System.out.println("[shader-evidence] context: " + gl.rendererDescription());

            int atlas = derivedAtlas();
            SpriteAPI sheet = sheetBoundTo(atlas);
            int[] target = target();
            BattleCamera camera = camera();

            CellTopology topology = world();
            Field field = new Field(topology, sheet);
            ReliefFieldMesh resident = new ReliefFieldMesh("height");

            assertTrue(resident.sync(topology, field), "the field has to bake before it can drift");
            byte[] before = draw(target[0], resident, camera);

            // The two changes the per-frame rebuild existed for: a wall breached
            // and a roof caved in. Both reach the field through the change log,
            // and both move a cell between buckets or within one.
            field.resolved = 0;
            topology.setWall(3, 3, false);
            topology.setRoofDestroyed(5, 5, true);
            assertTrue(resident.sync(topology, field));
            assertTrue(field.resolved > 0 && field.resolved < CELLS * CELLS,
                    "this has to be a patch to be worth comparing, not a rebuild; resolved "
                            + field.resolved);
            byte[] patched = draw(target[0], resident, camera);

            ReliefFieldMesh fresh = new ReliefFieldMesh("height");
            assertTrue(fresh.sync(topology, new Field(topology, sheet)));
            byte[] rebuilt = draw(target[0], fresh, camera);

            assertFalse(java.util.Arrays.equals(before, patched),
                    "a breach and a cave-in have to change the picture, or nothing is measured");
            assertArrayEquals(rebuilt, patched,
                    "a patched field must draw exactly what a fresh bake of the same world draws");

            resident.dispose();
            fresh.dispose();
            glDeleteFramebuffers(target[0]);
            glDeleteTextures(target[1]);
            glDeleteTextures(atlas);
        }
    }

    // ---- the world ----------------------------------------------------------

    /** Walls round the rim, floor inside, one street run, one roofed block. */
    private static CellTopology world() {
        CellTopology topology = new CellTopology(CELLS, CELLS);
        for (int y = 0; y < CELLS; y++) {
            for (int x = 0; x < CELLS; x++) {
                topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            }
        }
        for (int i = 0; i < CELLS; i++) {
            topology.setWall(i, 0, true);
            topology.setWall(i, CELLS - 1, true);
            topology.setWall(0, i, true);
            topology.setWall(CELLS - 1, i, true);
        }
        topology.setWall(3, 3, true);
        for (int x = 1; x < CELLS - 1; x++) topology.setGroundKind(x, 2, CellTopology.GroundKind.STREET);
        return topology;
    }

    /**
     * A field that stands walls tall and untextured, samples the derived atlas
     * everywhere else, and drops a caved-in cell back to the datum — the same
     * three answers {@code GroundHeightPass} gives, without a tile registry.
     */
    private static final class Field implements ReliefFieldMesh.CellResolver {
        private final CellTopology topology;
        private final SpriteAPI sheet;
        int resolved;

        Field(CellTopology topology, SpriteAPI sheet) {
            this.topology = topology;
            this.sheet = sheet;
        }

        @Override
        public void resolve(int x, int y, ReliefFieldMesh.CellSink sink) {
            resolved++;
            if (topology.isWall(x, y)) {
                sink.solid(1f, 0.5f, 0f, 0f);
                return;
            }
            float macro = topology.isRoofDestroyed(x, y) ? 0.125f : 0.5f;
            int texel = (x + y) % 2;
            sink.quad(sheet, 2, 2, texel, (x / 2) % 2, 1, 1, macro, 0f, 0f, 1f);
        }
    }

    // ---- GL scaffolding -----------------------------------------------------

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(CELLS, CELLS);
        camera.setViewport(0f, 0f, SURFACE, SURFACE, CELL_PX);
        return camera;
    }

    /** Draws the field into {@code fbo} over a known clear, and reads it back. */
    private static byte[] draw(int fbo, ReliefFieldMesh mesh, BattleCamera camera) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, SURFACE, SURFACE);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE, 0, SURFACE, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        mesh.draw(camera, null, null);

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE * SURFACE * 4);
        glReadPixels(0, 0, SURFACE, SURFACE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[SURFACE * SURFACE * 4];
        pixels.get(read);
        return read;
    }

    /** {@code {fbo, texture}} at the surface size. */
    private static int[] target() {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SURFACE, SURFACE, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);

        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return new int[]{fbo, texture};
    }

    /** A 2x2 stand-in for a derived height atlas: four texels a UV mistake cannot confuse. */
    private static int derivedAtlas() {
        ByteBuffer texels = BufferUtils.createByteBuffer(2 * 2 * 4);
        int[] rgba = {
                0x20FF00FF, 0x40FF00FF,
                0x60FF00FF, 0x80FF00FF,
        };
        for (int value : rgba) {
            texels.put((byte) (value >>> 24)).put((byte) (value >>> 16))
                    .put((byte) (value >>> 8)).put((byte) value);
        }
        texels.flip();
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, texels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
        return texture;
    }

    /** The mesh only ever asks a sheet for its normalised extent and to bind itself. */
    private static SpriteAPI sheetBoundTo(int texture) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getWidth", "getHeight" -> 2f;
            case "getTextureWidth", "getTextureHeight" -> 1f;
            case "bindTexture" -> {
                glBindTexture(GL_TEXTURE_2D, texture);
                yield null;
            }
            case "toString" -> "derived-atlas";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultOf(method.getReturnType());
        };
        return (SpriteAPI) Proxy.newProxyInstance(ReliefFieldGlEvidence.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, handler);
    }

    private static Object defaultOf(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == long.class) return 0L;
        if (type == void.class) return null;
        return 0;
    }
}
