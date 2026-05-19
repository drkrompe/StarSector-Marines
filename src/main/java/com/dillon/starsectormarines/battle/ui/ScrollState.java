package com.dillon.starsectormarines.battle.ui;

import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Reusable scroll-offset bookkeeping for HUD panels with vertical content
 * that may exceed the panel's viewport. Owns the scalar offset, clamps it
 * against the current content / viewport sizes, intercepts mouse-wheel events
 * scoped to a rect, and offers per-line visibility checks and an optional
 * scrollbar render.
 *
 * <p><b>Coordinate convention.</b> Y-up (matches the existing HUD code).
 * "Content space" is measured downward from {@code contentTopY = 0}; "screen
 * space" is the rendered pixel Y. {@code offset()} is the content-space Y
 * currently sitting at the top of the viewport — bumping it up scrolls deeper
 * into the content (you see lower lines, the top scrolls out).
 *
 * <p><b>Bracket-free.</b> No GL scissor — callers do a one-line visibility
 * check before each {@code drawString}, so half-clipped glyphs are skipped
 * rather than half-drawn. Matches the project's "avoid scissor unless we own
 * the GL state bracket" rule for Starsector UI hooks.
 *
 * <p><b>Typical use:</b>
 * <pre>{@code
 * scroll.setMetrics(contentH, viewportH);   // per frame, before render
 * float lineY = vpTopScreenY - LINE_H + scroll.offset();
 * for (...) {
 *     if (scroll.lineVisible(lineY, LINE_H, vpBottomScreenY, vpTopScreenY)) {
 *         font.drawString(text, x, lineY + LINE_H - 6, color, alpha);
 *     }
 *     lineY -= LINE_H;
 * }
 * // input handling:
 * scroll.handleWheel(events, panelX, panelY, panelW, panelH, LINE_H * 3f);
 * }</pre>
 */
public final class ScrollState {

    private float offset = 0f;
    private float contentH = 0f;
    private float viewportH = 0f;

    public float offset()    { return offset; }
    public float contentH()  { return contentH; }
    public float viewportH() { return viewportH; }

    /** Largest valid offset given current metrics. Zero when content fits the viewport. */
    public float maxOffset() {
        return Math.max(0f, contentH - viewportH);
    }

    /** True when content overflows the viewport — i.e. scrolling actually does something. */
    public boolean overflows() {
        return contentH > viewportH;
    }

    /** Set per-frame size info and re-clamp the offset in case content shrank under the scroll. */
    public void setMetrics(float contentH, float viewportH) {
        this.contentH = contentH;
        this.viewportH = viewportH;
        clamp();
    }

    /** Reset offset to top — call when the underlying content changes identity (e.g. selection switches squads). */
    public void reset() {
        offset = 0f;
    }

    /** Adjust offset by a pixel delta and clamp. Positive scrolls down (later content visible); negative scrolls up. */
    public void scrollBy(float pixels) {
        offset += pixels;
        clamp();
    }

    private void clamp() {
        float max = maxOffset();
        if (offset < 0f) offset = 0f;
        else if (offset > max) offset = max;
    }

    /**
     * Consumes mouse-wheel events whose cursor is inside the given rect and
     * scrolls by {@code pxPerNotch} per wheel notch (sign follows screen
     * convention — wheel-up shows earlier content, i.e. decreases offset).
     * Returns {@code true} if anything was consumed, so the caller can short
     * circuit other wheel handlers.
     *
     * <p>Place this <em>before</em> camera-zoom handling in the input chain —
     * panels run first in {@code BattleHud.processInput}, so this is the
     * natural ordering already.
     */
    public boolean handleWheel(List<InputEventAPI> events,
                                float rectX, float rectY, float rectW, float rectH,
                                float pxPerNotch) {
        if (events == null) return false;
        boolean consumed = false;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isMouseScrollEvent()) continue;
            int px = e.getX();
            int py = e.getY();
            if (px < rectX || px >= rectX + rectW || py < rectY || py >= rectY + rectH) continue;
            int raw = e.getEventValue();
            float notches = raw > 0 ? 1f : (raw < 0 ? -1f : 0f);
            // Wheel up (positive notches) → content moves up visually → offset decreases.
            scrollBy(-notches * pxPerNotch);
            e.consume();
            consumed = true;
        }
        return consumed;
    }

    /**
     * Cheap visibility test for a single line. Returns true when any pixel of
     * the line band {@code [lineBottomY, lineBottomY + lineH)} intersects the
     * viewport band {@code [vpBottomY, vpTopY)}.
     *
     * <p>"Line bottom" because {@code drawString} in this codebase uses the
     * font baseline + ascent → top, so positions are stored as the row's
     * bottom edge and {@code +lineH} gives the top.
     */
    public boolean lineVisible(float lineBottomY, float lineH, float vpBottomY, float vpTopY) {
        return (lineBottomY + lineH) > vpBottomY && lineBottomY < vpTopY;
    }

    /**
     * Renders a thin scrollbar inside the given gutter rect. Track + thumb;
     * the thumb's height is proportional to {@code viewportH/contentH} and
     * its position to {@code offset/maxOffset}. No-op when {@link #overflows()}
     * is false — when everything fits, no bar.
     *
     * <p>Caller is responsible for the surrounding GL state (blend on, texture
     * off). Pairs with {@link com.dillon.starsectormarines.battle.ui.panel.HudDraw#prepBlend()}.
     */
    public void renderScrollbar(float gutterX, float gutterY, float gutterW, float gutterH,
                                 Color trackColor, Color thumbColor, float alphaMult) {
        if (!overflows() || gutterW <= 0f || gutterH <= 0f) return;
        // Track.
        fillRect(gutterX, gutterY, gutterW, gutterH, trackColor, alphaMult);
        // Thumb sized as viewport/content ratio, with a floor so it stays grabbable
        // even for very long content (debug-only here but worth not-degenerating).
        float ratio = viewportH / contentH;
        float thumbH = Math.max(12f, gutterH * ratio);
        thumbH = Math.min(thumbH, gutterH);
        float maxOff = maxOffset();
        float t = maxOff > 0f ? offset / maxOff : 0f;
        // offset=0 ⇒ thumb at top of gutter (which is high screen-Y in Y-up).
        float thumbTopY = gutterY + gutterH - thumbH - t * (gutterH - thumbH);
        fillRect(gutterX, thumbTopY, gutterW, thumbH, thumbColor, alphaMult);
    }

    /** Private quad helper — kept local so this file doesn't depend on package-private HudDraw. */
    private static void fillRect(float x, float y, float w, float h, Color c, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                c.getAlpha() / 255f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }
}
