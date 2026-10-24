package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vision.RevealChangeLog;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.GlSurface;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Real-driver evidence that {@link NightShadeField} draws {@link NightLightingRenderSystem#shade} at every pixel:
 * the lamps' and fire's pools sampled bilinearly at half-cell resolution as the CPU path samples them, a hidden cell
 * losing its lamp light but keeping fire's, and an empty cell left unshaded. Checked over a grey ground with the
 * screen's blend, and premultiplied into a cleared target as the coverage composite takes it; then again after fire,
 * fog and ground have each changed in a corner, over two frames with one draw, so the resident textures take only
 * those boxes and still hold the whole picture. Opt-in under {@code shaderEvidence}.
 */
@Tag("shader-evidence")
class NightShadeFieldGlEvidence {
    private static final int CELLS = 4;
    private static final int SIZE = 64;
    private static final float DARKNESS = .7f;
    /** Levels a channel may differ by: 16-bit pools and the filter's fixed-point weights. */
    private static final int TOLERANCE = 3;

    @Test
    void theFieldDrawsTheShadeFunctionAtEveryPixel() {
        try (GlSurface gl = GlSurface.createOrNull(SIZE, SIZE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + GlSurface.unavailableReason());
                return;
            }
            System.out.println("[shader-evidence] context: " + gl.rendererDescription());
            int w = CELLS * 2 + 1;
            float[] pools = new float[w * w], reds = new float[w * w], greens = new float[w * w],
                    blues = new float[w * w], fire = new float[w * w];
            for (int y = 0; y < w; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    float lamp = Math.max(0f, 1.6f - Math.abs(x - 2) * .4f - Math.abs(y - 2) * .3f);
                    pools[i] = lamp;
                    reds[i] = lamp * .9f;
                    greens[i] = lamp * .8f;
                    blues[i] = lamp * .5f;
                    fire[i] = Math.max(0f, 1.2f - Math.abs(x - 6) * .35f - Math.abs(y - 6) * .25f);
                }
            }
            CellTopology ground = new CellTopology(CELLS, CELLS);
            for (int y = 0; y < CELLS; y++)
                for (int x = 0; x < CELLS; x++) ground.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            ground.setGroundKind(3, 0, CellTopology.GroundKind.VOID);
            boolean[] shown = new boolean[CELLS * CELLS];
            Arrays.fill(shown, true);
            shown[2 * CELLS + 1] = false;
            RevealChangeLog shownChanges = new RevealChangeLog();
            shownChanges.init(CELLS, CELLS);
            BattleCamera camera = new BattleCamera(CELLS, CELLS);
            camera.setViewport(0, 0, SIZE, SIZE, SIZE / (float) CELLS);

            NightShadeField field = new NightShadeField();
            try {
                field.bakeLamps(pools, reds, greens, blues, w, w);
                field.fire(fire, true, 0, 0, w - 1, w - 1);
                field.cells(ground, shown, shownChanges);
                field.frame(0, 0, CELLS - 1, CELLS - 1, DARKNESS, 1f, camera);
                check("whole upload", field, camera, ground, shown, pools, reds, greens, blues, fire, w);

                // Fire dims and goes out in one corner; the next frame, before any draw, a cell is hidden and
                // another's ground goes. Only those boxes are uploaded.
                for (int y = 3; y <= 7; y++)
                    for (int x = 4; x <= 8; x++) fire[y * w + x] = x >= 7 ? 0f : fire[y * w + x] * .5f;
                field.fire(fire, true, 4, 3, 8, 7);
                field.cells(ground, shown, shownChanges);
                field.frame(0, 0, CELLS - 1, CELLS - 1, DARKNESS, 1f, camera);
                shown[0] = false;
                shownChanges.note(0);
                shownChanges.seal();
                ground.setGroundKind(0, 3, CellTopology.GroundKind.VOID);
                field.fire(fire, true, 0, 0, -1, -1);
                field.cells(ground, shown, shownChanges);
                field.frame(0, 0, CELLS - 1, CELLS - 1, DARKNESS, 1f, camera);
                check("changed boxes", field, camera, ground, shown, pools, reds, greens, blues, fire, w);
            } finally {
                field.dispose();
            }
        }
    }

    private static void check(String what, NightShadeField field, BattleCamera camera, CellTopology ground,
                              boolean[] shown, float[] pools, float[] reds, float[] greens, float[] blues,
                              float[] fire, int w) {
        byte[] over = draw(field, false);
        byte[] premultiplied = draw(field, true);

        float s0x = camera.cellToScreenX(0), s1x = camera.cellToScreenX(1);
        float s0y = camera.cellToScreenY(0), s1y = camera.cellToScreenY(1);
        float[] c = new float[4];
        int worst = 0, shaded = 0;
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                float cx = (px + .5f - s0x) / (s1x - s0x), cy = (py + .5f - s0y) / (s1y - s0y);
                int cellX = (int) Math.floor(cx), cellY = (int) Math.floor(cy);
                float[] expectOver = {.5f, .5f, .5f}, expectPremultiplied = {0f, 0f, 0f, 0f};
                boolean solid = ground.getGroundKind(cellX, cellY) != CellTopology.GroundKind.VOID;
                float pool = shown[cellY * CELLS + cellX] ? sample(pools, w, cx, cy) : 0f;
                if (solid && NightLightingRenderSystem.shade(DARKNESS, pool, sample(fire, w, cx, cy),
                        sample(reds, w, cx, cy), sample(greens, w, cx, cy), sample(blues, w, cx, cy), 1f, c)) {
                    shaded++;
                    for (int k = 0; k < 3; k++) {
                        expectOver[k] = c[k] * c[3] + .5f * (1 - c[3]);
                        expectPremultiplied[k] = c[k] * c[3];
                    }
                    expectPremultiplied[3] = c[3];
                }
                for (int k = 0; k < 3; k++) {
                    worst = Math.max(worst, delta(over, px, py, k, expectOver[k]));
                    worst = Math.max(worst, delta(premultiplied, px, py, k, expectPremultiplied[k]));
                }
                worst = Math.max(worst, delta(premultiplied, px, py, 3, expectPremultiplied[3]));
            }
        }
        System.out.println("[shader-evidence] night shade field, " + what + ": " + shaded + " of " + SIZE * SIZE
                + " pixels shaded, worst channel " + worst + " levels from the CPU shade; pools stored at "
                + field.storedBits() + " bits");
        assertTrue(field.storedBits() >= NightShadeField.MIN_BITS,
                "this driver keeps the pools at " + field.storedBits() + " bits, which bands the shade");
        assertTrue(shaded > SIZE * SIZE / 2, "the frame has to be shaded to say anything");
        assertTrue(worst <= TOLERANCE, what + ": the field draws the CPU shade function; worst " + worst);
        assertTrue(!field.broken(), "and it drew without a GL error");
    }

    private static byte[] draw(NightShadeField field, boolean premultiplied) {
        glViewport(0, 0, SIZE, SIZE);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SIZE, 0, SIZE, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        if (premultiplied) {
            glClearColor(0f, 0f, 0f, 0f);
            glDisable(GL_BLEND);
        } else {
            glClearColor(.5f, .5f, .5f, 1f);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        glClear(GL_COLOR_BUFFER_BIT);
        assertTrue(field.draw(true, premultiplied), "the field has to draw on a GL 2.1 context");
        glUseProgram(0);
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] read = new byte[SIZE * SIZE * 4];
        pixels.get(read);
        return read;
    }

    /** {@code NightLightingRenderSystem.sample}: bilinear over half-cell points. */
    private static float sample(float[] values, int width, float x, float y) {
        float gx = x * 2, gy = y * 2;
        int ix = (int) gx, iy = (int) gy;
        float dx = gx - ix, dy = gy - iy;
        int i = iy * width + ix;
        return (values[i] * (1 - dx) + values[i + 1] * dx) * (1 - dy)
                + (values[i + width] * (1 - dx) + values[i + width + 1] * dx) * dy;
    }

    private static int delta(byte[] pixels, int x, int y, int channel, float expected) {
        int read = pixels[(y * SIZE + x) * 4 + channel] & 255;
        return Math.abs(read - Math.round(Math.max(0f, Math.min(1f, expected)) * 255f));
    }
}
