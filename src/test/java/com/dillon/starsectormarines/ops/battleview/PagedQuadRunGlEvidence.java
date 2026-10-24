package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.DrawCensus;
import com.dillon.starsectormarines.render2d.DrawListRenderer;
import com.dillon.starsectormarines.render2d.LineBatch;
import com.dillon.starsectormarines.render2d.PagedQuadRun;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.RibbonBatch;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
 * What a real driver draws out of one paged run against the one run per sheet it replaces
 * ({@code programmable-quad-batch.md}).
 *
 * <p>The frame is built to be the paged run's hard case: overlapping quads from ten sheets in turn, so painter
 * order decides every overlap and a run of eight pages has to close and open again; quads turned, mirrored, tinted
 * and faded; additive quads among the normal ones, which share the run premultiplied; whole sprites of either blend,
 * turned and tinted, which join the run as quads of their own texture; and a solid fill in the middle. Colour is compared within {@link #COLOUR_TOLERANCE}, the premultiplied rounding, and
 * alpha is reported rather than compared. Every pixel of the paged frame is compared with the fixed-function one.
 *
 * <p>It carries the {@link UnitAtlasGlEvidence} controls against measuring nothing: the frame has to be painted,
 * and the same frame moved one column has to differ by far more than the two drains do.
 *
 * <p>Opt-in under {@code shaderEvidence}; where no context can be made it reports that it skipped.
 */
@Tag("shader-evidence")
class PagedQuadRunGlEvidence {

    private static final int W = 960;
    private static final int H = 540;

    private static final String[] IMAGES = {
            "graphics/battle/marine-modular-topdown/variants/armor/army-green/body.png",
            "graphics/battle/marine-modular-topdown/variants/armor/army-green/head.png",
            "graphics/battle/marine-modular-topdown/variants/armor/army-green/downed-0.png",
            "graphics/battle/marine-modular-topdown/variants/armor/army-green/downed-1.png",
            "graphics/battle/marine-modular-topdown/variants/armor/army-green/downed-2.png",
            "graphics/battle/marine-modular-topdown/variants/armor/army-green/downed-3.png",
            "graphics/battle/rav-runner/arm-0-l.png",
            "graphics/battle/rav-runner/arm-0-r.png",
            "graphics/battle/rav-runner/arm-1-l.png",
            "graphics/battle/rav-runner/arm-1-r.png",
    };
    private static final int[][] SIZES = {
            {150, 113}, {72, 85}, {360, 520}, {360, 520}, {360, 520}, {360, 520},
            {66, 148}, {66, 148}, {40, 105}, {40, 105},
    };

    @Test
    void aPagedRunDrawsWhatOneRunPerSheetDraws() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(W, H)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            System.out.println("[shader-evidence] context: " + gl.rendererDescription());
            int[] target = null;
            PagedQuadRun paged = new PagedQuadRun();
            try (GlSpriteTokens tokens = new GlSpriteTokens()) {
                target = target();
                Path mod = Path.of("mod").toAbsolutePath().normalize();
                List<SpriteAPI> sheets = new ArrayList<>();
                Map<SpriteAPI, QuadBatch> batches = new IdentityHashMap<>();
                for (int i = 0; i < IMAGES.length; i++) {
                    SpriteAPI sheet = tokens.token(IMAGES[i], mod.resolve(IMAGES[i]), SIZES[i][0], SIZES[i][1]);
                    sheets.add(sheet);
                    batches.put(sheet, new QuadBatch(sheet, SIZES[i][0], SIZES[i][1], 16));
                }
                DrawList frame = frame(sheets);
                assertTrue(paged.ready(), "a GL 2.1 context with this many units has to page");

                DrawCensus perSheet = new DrawCensus();
                byte[] control = draw(target[0], frame, batches, null, perSheet);
                DrawCensus pagedCensus = new DrawCensus();
                byte[] subject = draw(target[0], frame, batches, paged, pagedCensus);

                Difference same = compare(control, subject);
                Difference displaced = compare(control, shiftedOneColumn(control));
                System.out.println("[shader-evidence] paged run: draws " + perSheet.drawCalls() + " -> "
                        + pagedCensus.drawCalls() + ", binds " + perSheet.textureBinds() + " -> "
                        + pagedCensus.textureBinds() + "; pixels " + same.painted() + " painted, "
                        + same.differingPixels() + " differ in colour by more than " + COLOUR_TOLERANCE + ", "
                        + same.roundedPixels() + " by up to it, " + same.alphaPixels() + " in alpha; "
                        + displaced.differingPixels() + " against the frame moved one column");
                assertEquals(perSheet.sheetQuads(), pagedCensus.sheetQuads(), "the same quads either way");
                assertTrue(pagedCensus.drawCalls() < perSheet.drawCalls() / 4,
                        "a paged run closes only for a full page table");
                assertEquals(perSheet.sprites(), pagedCensus.sprites(), "and the same whole sprites");
                assertTrue(same.painted() > 20_000, "the frame has to have art in it; painted " + same.painted());
                assertTrue(displaced.differingPixels() > 20 * Math.max(1, same.differingPixels()),
                        "a metric that cannot tell a displaced frame from a matching one measures nothing");
                assertEquals(0, same.differingPixels(),
                        "and the paged run has to draw the same picture, up to premultiplied rounding");
            } finally {
                paged.dispose();
                if (target != null) {
                    glDeleteFramebuffers(target[0]);
                    glDeleteTextures(target[1]);
                }
            }
        }
    }

    /** Overlapping quads from every sheet in turn, every shape a sheet quad takes, a fill and an additive quad. */
    private static DrawList frame(List<SpriteAPI> sheets) {
        DrawList out = new DrawList();
        int n = 0;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 14; col++, n++) {
                int s = n % sheets.size();
                int[] size = SIZES[s];
                float cx = 40 + col * 64f + (row % 2) * 20f;
                float cy = 50 + row * 82f;
                float w = 70f + (n % 5) * 9f;
                float h = w * size[1] / size[0];
                float tint = 0.6f + 0.4f * ((n * 37) % 11) / 10f;
                float alpha = 0.55f + 0.45f * ((n * 53) % 7) / 6f;
                if (n == 40) {
                    out.addSolidRect(RenderLayer.UNITS, cx - 30, cy - 30, cx + 30, cy + 30, 0.2f, 0.5f, 0.9f, 0.7f);
                }
                if (n == 55) {
                    out.addAdditiveSprite(RenderLayer.UNITS, sheets.get(s), cx, cy, w, h, 20f, 1f, 0.8f, 0.5f, 0.8f);
                    continue;
                }
                if (n % 7 == 3) {
                    // A whole sprite, normal or additive, turned and tinted among the sheet quads: a shot.
                    if (n % 2 == 0) {
                        out.addSprite(RenderLayer.UNITS, sheets.get(s), cx, cy, w, h, n * 17f, tint, 1f, tint, alpha);
                    } else {
                        out.addAdditiveSprite(RenderLayer.UNITS, sheets.get(s), cx, cy, w, h, n * 13f,
                                1f, tint, 0.6f, alpha);
                    }
                    continue;
                }
                if (n >= 20 && n % 3 == 0) {
                    // A glow over what came before it: an additive quad, as a held additive image is drawn.
                    out.addSheetQuad(RenderLayer.UNITS, sheets.get(s), 0, 0, size[0], size[1],
                            cx, cy, w, h, tint, 1f, tint, alpha);
                    out.buffer(RenderLayer.UNITS)[out.count(RenderLayer.UNITS) - 1].setAdditiveSheetQuad(
                            sheets.get(s), 0, 0, size[0], size[1], cx, cy, w, h, n * 11f, 1f, 0.7f, 0.4f, alpha);
                } else if (n % 4 == 0) {
                    out.addSheetQuad(RenderLayer.UNITS, sheets.get(s), 0, 0, size[0], size[1],
                            cx, cy, w, h, tint, 1f, tint, alpha);
                } else if (n % 4 == 1) {
                    out.addSheetQuadFlippedV(RenderLayer.UNITS, sheets.get(s), 0, 0, size[0], size[1],
                            cx, cy, w, h, 1f, tint, 1f, alpha);
                } else {
                    // A cut of the image, turned: what an atlas slot or a sheet cell is.
                    out.addSheetQuad(RenderLayer.UNITS, sheets.get(s), size[0] / 6, size[1] / 8,
                            size[0] * 2 / 3, size[1] * 3 / 4, cx, cy, w, h, n * 23f, 1f, 1f, tint, alpha);
                }
            }
        }
        return out;
    }

    private static byte[] draw(int fbo, DrawList frame, Map<SpriteAPI, QuadBatch> batches, PagedQuadRun paged,
                               DrawCensus census) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, W, H);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, W, 0, H, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glClearColor(1f, 0f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        DrawListRenderer.drain(frame.buffer(RenderLayer.UNITS), frame.count(RenderLayer.UNITS), batches,
                new SolidQuadBatch(16), new LineBatch(16), new RibbonBatch(16), null, census, paged);
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[W * H * 4];
        pixels.get(read);
        return read;
    }

    /**
     * The colour levels a paged pixel may differ by. A paged run is premultiplied: the shader rounds colour times
     * alpha to the target once, where the fixed-function blend multiplied in the blend unit, and overlapping
     * translucent quads carry that rounding up; three levels is what the frame's deepest stack reaches.
     */
    private static final int COLOUR_TOLERANCE = 3;

    /**
     * Painted pixels; pixels whose colour differs by more than the tolerance, and by up to it; and pixels whose alpha
     * differs, which an additive quad over a translucent destination alpha does by design (law 27).
     */
    private record Difference(int painted, int differingPixels, int roundedPixels, int alphaPixels) { }

    private static Difference compare(byte[] control, byte[] subject) {
        int painted = 0;
        int differing = 0;
        int rounded = 0;
        int alpha = 0;
        for (int pixel = 0; pixel < control.length; pixel += 4) {
            if (!((control[pixel] & 0xFF) == 255 && control[pixel + 1] == 0 && (control[pixel + 2] & 0xFF) == 255)) {
                painted++;
            }
            int worst = 0;
            for (int channel = 0; channel < 3; channel++) {
                worst = Math.max(worst, Math.abs((control[pixel + channel] & 0xFF) - (subject[pixel + channel] & 0xFF)));
            }
            if (worst > COLOUR_TOLERANCE) differing++;
            else if (worst > 0) rounded++;
            if (control[pixel + 3] != subject[pixel + 3]) alpha++;
        }
        return new Difference(painted, differing, rounded, alpha);
    }

    private static byte[] shiftedOneColumn(byte[] frame) {
        byte[] moved = new byte[frame.length];
        for (int row = 0; row < H; row++) {
            int at = row * W * 4;
            System.arraycopy(frame, at, moved, at + 4, (W - 1) * 4);
            moved[at] = (byte) 255;
            moved[at + 1] = 0;
            moved[at + 2] = (byte) 255;
            moved[at + 3] = (byte) 255;
        }
        return moved;
    }

    /** {@code {fbo, texture}} at the surface size. */
    private static int[] target() {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
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
