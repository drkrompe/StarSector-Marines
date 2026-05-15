package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.function.Consumer;

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
 * One clickable mission marker on the tactical map. Renders as a filled circle
 * in the {@link MissionType}'s color with a single-letter glyph centered. The
 * hover popup is rendered separately by {@link MissionPopupOverlay} — keeps
 * popups always on top of every node body regardless of widget ordering.
 *
 * <p>Hit-test is the bounding box (square), not the visible circle — close
 * enough at this scale and avoids the radius math per input event.
 */
public class MissionNodeWidget extends BaseWidget {

    private static final int CIRCLE_SEGS = 18;

    public final Mission mission;
    private final Consumer<Mission> onSelect;

    private boolean hovered;
    private boolean armed;

    public MissionNodeWidget(Mission mission,
                             float centerX, float centerY, float size,
                             Consumer<Mission> onSelect) {
        this.mission = mission;
        this.onSelect = onSelect;
        this.x = centerX - size * 0.5f;
        this.y = centerY - size * 0.5f;
        this.w = size;
        this.h = size;
    }

    public boolean isHovered() {
        return hovered;
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
            if (onSelect != null) onSelect.accept(mission);
            return true;
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        float cx = x + w * 0.5f;
        float cy = y + h * 0.5f;
        float r  = w * 0.5f;

        Color base = mission.type.color;
        float brightness = hovered ? 1.0f : 0.78f;
        Color fill = new Color(
                Math.min(255, (int)(base.getRed()   * brightness)),
                Math.min(255, (int)(base.getGreen() * brightness)),
                Math.min(255, (int)(base.getBlue()  * brightness)));

        fillCircle(cx, cy, r, fill, 0.92f * alphaMult);
        strokeCircle(cx, cy, r, new Color(0xFF, 0xFF, 0xFF), (hovered ? 0.95f : 0.7f) * alphaMult);

        // Single-letter type glyph centered in the circle.
        String label = String.valueOf(mission.type.glyph);
        float labelW = Fonts.ORBITRON_20_BOLD.measureWidth(label);
        float lineH  = Fonts.ORBITRON_20_BOLD.getLineHeight();
        float tx = cx - labelW * 0.5f;
        float ty = cy + lineH * 0.5f - 2f;
        Fonts.ORBITRON_20_BOLD.drawString(label, tx, ty, Color.WHITE, alphaMult);
    }

    private static void fillCircle(float cx, float cy, float r, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (int i = 0; i <= CIRCLE_SEGS; i++) {
            float t = (float)(i * 2.0 * Math.PI / CIRCLE_SEGS);
            glVertex2f(cx + (float)Math.cos(t) * r, cy + (float)Math.sin(t) * r);
        }
        glEnd();
    }

    private static void strokeCircle(float cx, float cy, float r, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < CIRCLE_SEGS; i++) {
            float t = (float)(i * 2.0 * Math.PI / CIRCLE_SEGS);
            glVertex2f(cx + (float)Math.cos(t) * r, cy + (float)Math.sin(t) * r);
        }
        glEnd();
    }
}
