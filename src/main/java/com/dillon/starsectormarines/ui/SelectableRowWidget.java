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

/** Clickable inventory row with persistent selected and locked visual states. */
public final class SelectableRowWidget extends BaseWidget {

    private static final Color DEFAULT = new Color(0x18, 0x22, 0x2E);
    private static final Color HOVER = new Color(0x24, 0x36, 0x4C);
    private static final Color SELECTED = new Color(0x2C, 0x48, 0x66);
    private static final Color LOCKED = new Color(0x10, 0x14, 0x19);
    private static final Color BORDER = new Color(0x66, 0x7E, 0x98);
    private static final Color ACCENT = new Color(0xFF, 0xE0, 0x70);

    private final boolean selected;
    private final boolean locked;
    private final Runnable onSelect;
    private boolean hovered;
    private boolean armed;

    public SelectableRowWidget(float x, float y, float w, float h,
                               boolean selected, boolean locked, Runnable onSelect) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.selected = selected;
        this.locked = locked;
        this.onSelect = onSelect;
    }

    @Override
    public void onMouseMove(int px, int py) {
        hovered = contains(px, py);
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        armed = true;
        return true;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        boolean wasArmed = armed;
        armed = false;
        if (wasArmed && contains(px, py)) {
            if (onSelect != null) onSelect.run();
            return true;
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        Color background = selected ? SELECTED : locked ? LOCKED : hovered || armed ? HOVER : DEFAULT;
        rect(background, 0.9f * alphaMult, false);
        rect(BORDER, (locked ? 0.45f : 0.82f) * alphaMult, true);
        if (selected) {
            glColor4f(ACCENT.getRed() / 255f, ACCENT.getGreen() / 255f,
                    ACCENT.getBlue() / 255f, 0.95f * alphaMult);
            glBegin(GL_QUADS);
            glVertex2f(x, y);
            glVertex2f(x + 4f, y);
            glVertex2f(x + 4f, y + h);
            glVertex2f(x, y + h);
            glEnd();
        }
    }

    private void rect(Color color, float alpha, boolean outline) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
        glLineWidth(1f);
        glBegin(outline ? GL_LINE_LOOP : GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }
}
