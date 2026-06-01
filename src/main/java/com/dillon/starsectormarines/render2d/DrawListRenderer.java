package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.Map;

/**
 * The engine-side drain: replays a layer's pooled {@link DrawCommand} buffer in
 * <strong>strict submission order</strong>, turning the deferred command stream
 * into batched GL calls. Submission order <em>is</em> paint order — the drain
 * holds no per-layer config; the collecting system owns the order.
 *
 * <p>It coalesces consecutive same-sheet {@code SHEET_QUAD}s into one
 * {@link QuadBatch} flush, consecutive {@code SOLID_RECT}s into one
 * {@link SolidQuadBatch} flush, consecutive same-width {@code LINE}s into one
 * {@link LineBatch} flush, and consecutive {@code RIBBON}s into one
 * {@link RibbonBatch} flush, flipping the active batch (and flushing the previous)
 * whenever the sheet, line width, or command kind changes — so anything submitted
 * later lands on top. A whole run of batch/sprite work shares <em>one</em>
 * {@link GlStateBracket} ({@code glPushAttrib}/{@code glPopAttrib} is not cheap,
 * and {@code QuadBatch}/{@code SolidQuadBatch} are designed to interleave under a
 * single {@code textured2D()} bracket). A {@code CUSTOM} owns its own GL state, so
 * the drain flushes pending batches, closes the bracket, runs the callback, then
 * reopens lazily.
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
     * the shared fill batch for {@code SOLID_RECT} runs; {@code lineBatch} is the
     * shared batch for {@code LINE} runs; {@code ribbonBatch} is the shared batch
     * for {@code RIBBON} runs and {@code camera} is what it expands their samples
     * cell→screen with (the only kind that isn't already screen-space).
     */
    public static void drain(DrawCommand[] buf, int count,
                             Map<SpriteAPI, QuadBatch> batchBySheet, SolidQuadBatch solidBatch,
                             LineBatch lineBatch, RibbonBatch ribbonBatch, BattleCamera camera) {
        GlStateBracket bracket = null;
        QuadBatch activeSheet = null;     // sheet batch with pending appends (else null)
        SpriteAPI activeSheetKey = null;
        boolean solidPending = false;     // solidBatch has pending appends
        boolean linePending = false;      // lineBatch has pending appends
        boolean ribbonPending = false;    // ribbonBatch has pending appends
        // A SPRITE draws via SpriteAPI.renderAtCenter — a foreign call that mutates
        // the blend func / colorMask and doesn't restore them. When the next batch
        // op runs in the same bracket, re-assert our textured-2D state first, else
        // it inherits the foreign blend and draws transparent texels opaque (black).
        boolean spritePolluted = false;

        for (int i = 0; i < count; i++) {
            DrawCommand c = buf[i];
            switch (c.kind) {
                case SHEET_QUAD: {
                    if (c.sprite != activeSheetKey) {
                        if (activeSheet != null) activeSheet.flush();
                        if (solidPending) { solidBatch.flush(); solidPending = false; }
                        if (linePending) { lineBatch.flush(); linePending = false; }
                        if (ribbonPending) { ribbonBatch.flush(); ribbonPending = false; }
                        activeSheetKey = c.sprite;
                        activeSheet = batchBySheet.get(c.sprite);
                    }
                    if (bracket == null) bracket = GlStateBracket.textured2D();
                    else if (spritePolluted) { GlStateBracket.applyTextured2DState(); spritePolluted = false; }
                    if (activeSheet != null) {
                        if (c.angleDeg != 0f) {
                            activeSheet.appendRotated(c.srcX, c.srcY, c.srcW, c.srcH,
                                    c.cx, c.cy, c.w, c.h, c.angleDeg, c.r, c.g, c.b, c.a);
                        } else if (c.flipV) {
                            activeSheet.appendFlippedV(c.srcX, c.srcY, c.srcW, c.srcH,
                                    c.cx, c.cy, c.w, c.h, c.r, c.g, c.b, c.a);
                        } else {
                            activeSheet.append(c.srcX, c.srcY, c.srcW, c.srcH,
                                    c.cx, c.cy, c.w, c.h, c.r, c.g, c.b, c.a);
                        }
                    }
                    break;
                }
                case SOLID_RECT: {
                    if (activeSheet != null) { activeSheet.flush(); activeSheet = null; activeSheetKey = null; }
                    if (linePending) { lineBatch.flush(); linePending = false; }
                    if (ribbonPending) { ribbonBatch.flush(); ribbonPending = false; }
                    if (bracket == null) bracket = GlStateBracket.textured2D();
                    else if (spritePolluted) { GlStateBracket.applyTextured2DState(); spritePolluted = false; }
                    solidBatch.appendRect(c.cx, c.cy, c.w, c.h, c.r, c.g, c.b, c.a); // cx/cy=(x0,y0), w/h=(x1,y1)
                    solidPending = true;
                    break;
                }
                case LINE: {
                    if (activeSheet != null) { activeSheet.flush(); activeSheet = null; activeSheetKey = null; }
                    if (solidPending) { solidBatch.flush(); solidPending = false; }
                    if (ribbonPending) { ribbonBatch.flush(); ribbonPending = false; }
                    if (bracket == null) bracket = GlStateBracket.textured2D();
                    else if (spritePolluted) { GlStateBracket.applyTextured2DState(); spritePolluted = false; }
                    // Line width is per-flush GL state — flush the run before it changes.
                    if (linePending && c.angleDeg != lineBatch.width()) { lineBatch.flush(); }
                    lineBatch.setWidth(c.angleDeg);
                    lineBatch.append(c.cx, c.cy, c.w, c.h, c.r, c.g, c.b, c.a); // cx/cy=(x0,y0), w/h=(x1,y1), angleDeg=width
                    linePending = true;
                    break;
                }
                case RIBBON: {
                    if (activeSheet != null) { activeSheet.flush(); activeSheet = null; activeSheetKey = null; }
                    if (solidPending) { solidBatch.flush(); solidPending = false; }
                    if (linePending) { lineBatch.flush(); linePending = false; }
                    if (bracket == null) bracket = GlStateBracket.textured2D();
                    else if (spritePolluted) { GlStateBracket.applyTextured2DState(); spritePolluted = false; }
                    // RibbonBatch expands cell→screen itself, so it needs the camera; a sub-2-sample
                    // trail appends nothing. INVARIANT: ribbon styles drained here must be normal-blend
                    // — they share the textured2D() bracket (normal alpha blend). An additive ribbon
                    // style would need its own additiveBlend() bracket, which this shared run can't give.
                    ribbonBatch.append(c.trail, camera, c.a);
                    ribbonPending = true;
                    break;
                }
                case SPRITE: {
                    if (activeSheet != null) { activeSheet.flush(); activeSheet = null; activeSheetKey = null; }
                    if (solidPending) { solidBatch.flush(); solidPending = false; }
                    if (linePending) { lineBatch.flush(); linePending = false; }
                    if (ribbonPending) { ribbonBatch.flush(); ribbonPending = false; }
                    if (bracket == null) bracket = GlStateBracket.textured2D();
                    drawSprite(c);
                    spritePolluted = true;
                    break;
                }
                case CUSTOM:
                default: {
                    if (activeSheet != null) { activeSheet.flush(); activeSheet = null; activeSheetKey = null; }
                    if (solidPending) { solidBatch.flush(); solidPending = false; }
                    if (linePending) { lineBatch.flush(); linePending = false; }
                    if (ribbonPending) { ribbonBatch.flush(); ribbonPending = false; }
                    if (bracket != null) { bracket.close(); bracket = null; }
                    c.custom.run();
                    break;
                }
            }
        }

        if (activeSheet != null) activeSheet.flush();
        if (solidPending) solidBatch.flush();
        if (linePending) lineBatch.flush();
        if (ribbonPending) ribbonBatch.flush();
        if (bracket != null) bracket.close();
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
