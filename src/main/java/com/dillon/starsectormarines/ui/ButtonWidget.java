package com.dillon.starsectormarines.ui;

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

/**
 * Rectangular pressable button. Renders as a filled quad with a 1px border;
 * the fill color shifts on hover/press so it's obvious the input layer is
 * working. Text is intentionally absent for now — bitmap font rendering inside
 * our GL pass is a separate concern from the input/event routing this layer
 * exists to prove.
 *
 * <p>{@link #onClick} fires on mouse-up inside the widget after a mouse-down
 * that also landed inside (standard "armed" semantics).
 */
public class ButtonWidget extends BaseWidget {

    public Runnable onClick;

    private boolean hovered;
    private boolean armed;

    public ButtonWidget(float x, float y, float w, float h, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.onClick = onClick;
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
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float r, g, b;
        if (armed)        { r = 0.45f; g = 0.65f; b = 0.85f; }
        else if (hovered) { r = 0.30f; g = 0.45f; b = 0.65f; }
        else              { r = 0.15f; g = 0.22f; b = 0.32f; }
        glColor4f(r, g, b, 0.85f * alphaMult);

        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();

        glColor4f(0.75f, 0.85f, 1.0f, 0.9f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }
}
