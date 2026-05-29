package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.Map;

/**
 * The engine-side drain: replays a layer's pooled {@link DrawCommand} buffer in
 * <strong>strict submission order</strong>, turning the deferred command stream
 * into batched GL calls. Submission order <em>is</em> paint order — the drain
 * holds no per-layer config; the collecting system owns the order (and emits each
 * sheet's quads contiguously so they batch into one flush).
 *
 * <p>It coalesces runs: consecutive {@code SHEET_QUAD}s sharing a sheet append to
 * that sheet's {@link QuadBatch} and flush once; a run of {@code SOLID_RECT}s
 * fills the shared {@link SolidQuadBatch} and flushes; a run of {@code SPRITE}s
 * renders each via {@code renderAtCenter}; a {@code CUSTOM} runs standalone (it
 * owns its own GL state). The batch flush flips when the sheet or command kind
 * changes, so anything painted later in submission order lands on top.
 *
 * <p>Pure mechanism: knows nothing about which layers exist or their order — the
 * caller hands one layer's buffer plus the sheet→batch map and the solid batch.
 */
public final class DrawListRenderer {

    private DrawListRenderer() {}

    /**
     * Drain {@code count} commands from {@code buf} (a pooled backing array; only
     * indices {@code 0..count} are live). {@code batchBySheet} resolves a
     * {@code SHEET_QUAD}'s sheet to its {@link QuadBatch}; {@code solidBatch} is
     * the shared fill batch for {@code SOLID_RECT} runs.
     */
    public static void drain(DrawCommand[] buf, int count,
                             Map<SpriteAPI, QuadBatch> batchBySheet, SolidQuadBatch solidBatch) {
        int i = 0;
        while (i < count) {
            DrawCommand c = buf[i];
            switch (c.kind) {
                case SHEET_QUAD: {
                    SpriteAPI sheet = c.sprite;
                    QuadBatch b = batchBySheet.get(sheet);
                    try (GlStateBracket gl = GlStateBracket.textured2D()) {
                        // Same-sheet run: append contiguous SHEET_QUADs of this sheet, then flush once.
                        while (i < count
                                && buf[i].kind == DrawCommand.Kind.SHEET_QUAD
                                && buf[i].sprite == sheet) {
                            DrawCommand q = buf[i];
                            if (b != null) {
                                b.append(q.srcX, q.srcY, q.srcW, q.srcH,
                                        q.cx, q.cy, q.w, q.h,
                                        q.r, q.g, q.b, q.a);
                            }
                            i++;
                        }
                        if (b != null) b.flush();
                    }
                    break;
                }
                case SOLID_RECT: {
                    try (GlStateBracket gl = GlStateBracket.textured2D()) {
                        while (i < count && buf[i].kind == DrawCommand.Kind.SOLID_RECT) {
                            DrawCommand q = buf[i];
                            // cx/cy = (x0,y0), w/h = (x1,y1).
                            solidBatch.appendRect(q.cx, q.cy, q.w, q.h, q.r, q.g, q.b, q.a);
                            i++;
                        }
                        solidBatch.flush();
                    }
                    break;
                }
                case SPRITE: {
                    try (GlStateBracket gl = GlStateBracket.textured2D()) {
                        while (i < count && buf[i].kind == DrawCommand.Kind.SPRITE) {
                            drawSprite(buf[i]);
                            i++;
                        }
                    }
                    break;
                }
                case CUSTOM:
                default:
                    c.custom.run();
                    i++;
                    break;
            }
        }
    }

    private static void drawSprite(DrawCommand q) {
        SpriteAPI sprite = q.sprite;
        sprite.setSize(q.w, q.h);
        sprite.setAngle(q.angleDeg);
        sprite.setAlphaMult(q.a);
        sprite.setNormalBlend();
        // Avoid a per-quad Color alloc for the common untinted case (all SHOTS
        // projectiles are white); only build a Color when an actual tint is set.
        sprite.setColor(q.r == 1f && q.g == 1f && q.b == 1f
                ? Color.WHITE : new Color(q.r, q.g, q.b));
        sprite.renderAtCenter(q.cx, q.cy);
        // Reset rotation so the shared cached sprite carries no angle into other passes.
        sprite.setAngle(0f);
    }
}
