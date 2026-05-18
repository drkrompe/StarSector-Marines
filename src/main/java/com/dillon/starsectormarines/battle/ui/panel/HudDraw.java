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
