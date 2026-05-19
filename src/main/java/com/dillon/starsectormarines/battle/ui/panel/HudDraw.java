package com.dillon.starsectormarines.battle.ui.panel;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Tiny immediate-mode helpers shared by the HUD panels. Same idiom the
 * existing {@link com.dillon.starsectormarines.ui.ButtonWidget} uses —
 * disable textures, enable alpha blend, emit a quad. Kept here so the panel
 * classes read as "what to draw" rather than "GL state plumbing."
 */
final class HudDraw {

    private HudDraw() {}

    static void prepBlend() {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    static void filledRect(float x, float y, float w, float h, Color c, float alphaMult) {
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                c.getAlpha() / 255f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }

    static void borderRect(float x, float y, float w, float h, Color c, float alphaMult) {
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                c.getAlpha() / 255f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }

    /** Renders an HP bar (background + fill). HP color grades green→yellow→red as the fraction drops. */
    static void hpBar(float x, float y, float w, float h, float frac, float alphaMult) {
        frac = Math.max(0f, Math.min(1f, frac));
        filledRect(x, y, w, h, new Color(0x40, 0x20, 0x20), alphaMult);
        if (frac > 0f) {
            Color fg;
            if (frac > 0.6f)      fg = new Color(0x40, 0xC0, 0x40);
            else if (frac > 0.3f) fg = new Color(0xE0, 0xC0, 0x40);
            else                  fg = new Color(0xE0, 0x50, 0x40);
            filledRect(x, y, w * frac, h, fg, alphaMult);
        }
    }

    /**
     * Renders a squad morale bar — background + value fill + break-threshold
     * tick + bordered. Fill is {@code morale / cap}, so the bar reads as
     * "how much fight is left given current strength" — a fresh squad and a
     * lone survivor both look fully resilient when at their respective caps.
     * The break tick at {@code breakThreshold} (interpreted as a fraction of
     * cap, matching the model in {@link com.dillon.starsectormarines.battle.BattleSimulation})
     * stays at a fixed position on the bar regardless of cap.
     *
     * <p>Color grades by the fill fraction; border flips red when
     * {@code broken} is true so the bar reads as an alert state at a glance.
     *
     * @param morale         current absolute morale
     * @param cap            current resilience ceiling (alive/originalSize)
     * @param broken         hysteresis-driven broken flag (red border when true)
     * @param breakThreshold fraction of cap at which the break tick is drawn
     */
    static void moraleBar(float x, float y, float w, float h,
                          float morale, float cap, boolean broken, float breakThreshold,
                          float alphaMult) {
        float fill = (cap > 0f) ? morale / cap : 0f;
        fill = Math.max(0f, Math.min(1f, fill));
        // Background — dark slate, distinct from the HP bar's reddish bg.
        filledRect(x, y, w, h, new Color(0x14, 0x18, 0x20), alphaMult);
        if (fill > 0f) {
            Color fg;
            if (fill > 0.5f)      fg = new Color(0x40, 0xC0, 0x40);
            else if (fill > 0.3f) fg = new Color(0xE0, 0xC0, 0x40);
            else                  fg = new Color(0xE0, 0x50, 0x40);
            filledRect(x, y, w * fill, h, fg, alphaMult);
        }
        // Break-threshold tick: a vertical white sliver poking above and below
        // the bar so it reads against any fill color.
        float tickX = x + w * Math.max(0f, Math.min(1f, breakThreshold));
        filledRect(tickX, y - 1f, 1.5f, h + 2f,
                new Color(0xF0, 0xF0, 0xF0, 0xD0), alphaMult);
        // Border — red when broken so the bar reads as an alert state at a
        // glance, not just a low-fill bar.
        Color border = broken
                ? new Color(0xE0, 0x40, 0x40)
                : new Color(0x60, 0x80, 0xA0);
        borderRect(x, y, w, h, border, alphaMult);
    }

    /** Small filled disc — used as the per-row alert indicator. */
    static void disc(float cx, float cy, float r, Color c, float alphaMult, int segments) {
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                c.getAlpha() / 255f * alphaMult);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            double a = (Math.PI * 2.0 * i) / segments;
            glVertex2f(cx + (float)(Math.cos(a) * r), cy + (float)(Math.sin(a) * r));
        }
        glEnd();
    }
}
