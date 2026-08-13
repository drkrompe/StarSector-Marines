package com.dillon.starsectormarines.ui;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
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
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/** Compact, non-interactive capability meter used by equipment comparison UI. */
public final class StatBarWidget extends BaseWidget {

    private static final Color BACKGROUND = new Color(0x16, 0x1E, 0x29);
    private static final Color BORDER = new Color(0x6B, 0x7D, 0x91);

    private final float fill;
    private final Color color;

    public StatBarWidget(float x, float y, float w, float h, float fill, Color color) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.fill = Math.max(0f, Math.min(1f, fill));
        this.color = color;
    }

    @Override
    public boolean contains(int px, int py) {
        return false;
    }

    @Override
    public void render(float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        color(BACKGROUND, 0.92f * alphaMult);
        quad(x, y, w, h);
        if (fill > 0f) {
            color(color, 0.92f * alphaMult);
            quad(x + 1f, y + 1f, Math.max(0f, (w - 2f) * fill), Math.max(0f, h - 2f));
        }

        color(BORDER, 0.9f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private static void quad(float x, float y, float w, float h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private static void color(Color color, float alpha) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
    }
}
