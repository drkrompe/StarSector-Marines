package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
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
 * What a real driver draws for burnt ground, against {@link ScorchShading}, the
 * CPU model every review frame shows. The two are written line for line; this
 * is what keeps them so. A patch of grass is burnt up to a firebreak, the layer
 * draws it over a known ground colour through both of its passes (the multiply
 * and the overlay), and every pixel is compared with the model's multiply-then-
 * over of the same colour. A drift in either (a constant changed in one, a
 * channel read differently) moves pixels by far more than the tolerance, which
 * only absorbs 8-bit texture filtering.
 *
 * <p>Opt-in, and excluded from {@code test}, for {@code GroundShaderGlEvidence}'s
 * reasons: run it with {@code gradlew.bat shaderEvidence}.
 */
@Tag("shader-evidence")
class ScorchGlEvidence {

    private static final int CELLS_W = 24, CELLS_H = 16, CELL_PX = 16;
    private static final int SURFACE_W = CELLS_W * CELL_PX, SURFACE_H = CELLS_H * CELL_PX;
    /** Fuel stops here: everything east of it is bare ground the singe reaches into. */
    private static final int BREAK_X = 14;
    /** Pavement east of the break, so the smudge branch is drawn too. */
    private static final int PAVED_FROM = 19;
    /** The ground under the burn, in exact 8-bit levels so the clear itself reads back unchanged. */
    private static final float[] GROUND = {115 / 255f, 140 / 255f, 77 / 255f};
    /** Levels a channel may differ by: bilinear 8-bit texture filtering against floats. */
    private static final int TOLERANCE = 6;

    @Test
    void theShaderDrawsWhatTheModelDraws() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE_W, SURFACE_H)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            NavigationGrid grid = new NavigationGrid(CELLS_W, CELLS_H);
            CellTopology topology = new CellTopology(CELLS_W, CELLS_H);
            for (int y = 0; y < CELLS_H; y++) {
                for (int x = 0; x < CELLS_W; x++) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, x >= PAVED_FROM ? CellTopology.GroundKind.SIDEWALK
                            : CellTopology.GroundKind.GRASS);
                }
            }
            FireField fire = new FireField(CELLS_W, CELLS_H, 7L,
                    idx -> idx % CELLS_W < BREAK_X ? (idx % 3 == 0 ? FireFuel.BRUSH.bit | FireFuel.GRASS.bit : FireFuel.GRASS.bit) : 0);
            for (int y = 0; y < CELLS_H; y++) fire.addHeat(1, y, 3000);
            for (int t = 0; t < 30 * 40; t++) fire.tick(t);
            assertTrue(fire.burntCount() > 0, "the patch has to burn for there to be anything to compare");

            ScorchLayer layer = new ScorchLayer();
            layer.catchUp(fire, grid, topology);
            ScorchPass pass = new ScorchPass(layer);
            pass.minX = 0;
            pass.minY = 0;
            pass.maxX = CELLS_W - 1;
            pass.maxY = CELLS_H - 1;
            pass.screenX0 = 0f;
            pass.screenY0 = 0f;
            pass.cellPx = CELL_PX;
            pass.alpha = 1f;

            int[] target = target();
            glBindFramebuffer(GL_FRAMEBUFFER, target[0]);
            glViewport(0, 0, SURFACE_W, SURFACE_H);
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, SURFACE_W, 0, SURFACE_H, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            glClearColor(GROUND[0], GROUND[1], GROUND[2], 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            layer.draw(pass);
            ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
            glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);

            int ground0 = Math.round(GROUND[0] * 255f), ground1 = Math.round(GROUND[1] * 255f), ground2 = Math.round(GROUND[2] * 255f);
            ScorchShading.Scratch scratch = new ScorchShading.Scratch();
            float[] out = new float[7];
            int changed = 0, worst = 0, worstX = -1, worstY = -1;
            for (int py = 0; py < SURFACE_H; py++) {
                for (int px = 0; px < SURFACE_W; px++) {
                    int i = (py * SURFACE_W + px) * 4;
                    int[] got = {pixels.get(i) & 0xFF, pixels.get(i + 1) & 0xFF, pixels.get(i + 2) & 0xFF};
                    int[] want = {ground0, ground1, ground2};
                    if (ScorchShading.shade(layer, (px + 0.5f) / CELL_PX, (py + 0.5f) / CELL_PX, scratch, out)) {
                        for (int c = 0; c < 3; c++) {
                            float m = want[c] / 255f * out[c];
                            want[c] = Math.round((m + (out[3 + c] - m) * out[6]) * 255f);
                        }
                    }
                    for (int c = 0; c < 3; c++) {
                        int d = Math.abs(got[c] - want[c]);
                        if (d > worst) {
                            worst = d;
                            worstX = px;
                            worstY = py;
                        }
                    }
                    if (got[0] != ground0 || got[1] != ground1 || got[2] != ground2) changed++;
                }
            }
            System.out.println("[shader-evidence] Scorch: " + changed + " of " + SURFACE_W * SURFACE_H
                    + " pixels changed; worst channel difference from the model " + worst);
            assertTrue(changed > SURFACE_W * SURFACE_H / 4, "the burn has to be drawn: only " + changed + " pixels changed");
            assertTrue(changed < SURFACE_W * SURFACE_H * 9 / 10, "and bare ground away from it left alone: " + changed + " changed");
            assertTrue(worst <= TOLERANCE, "the shader must draw what the model draws: " + worst
                    + " levels apart at pixel " + worstX + "," + worstY);
            glDeleteFramebuffers(target[0]);
            glDeleteTextures(target[1]);
            layer.dispose();
        }
    }

    /** {@code {fbo, texture}} at the surface size. */
    private static int[] target() {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SURFACE_W, SURFACE_H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
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
}
