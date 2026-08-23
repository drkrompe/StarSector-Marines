package com.dillon.starsectormarines.ui.retained;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL20.glUseProgram;

/** Paints retained boxes into Starsector's fixed-function UI pass. */
final class UiPainter {

    void paint(UiElement root, UiViewport viewport, float alphaMult) {
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_ENABLE_BIT
                | GL_LINE_BIT | GL_TEXTURE_BIT);
        try {
            glUseProgram(0);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            paintElement(root, viewport, alphaMult);
        } finally {
            glUseProgram(0);
            glPopAttrib();
        }
    }

    private void paintElement(UiElement element, UiViewport viewport, float alphaMult) {
        Rect rect = element.box().borderBox();
        Color background = element.paintedBackground();
        if (background != null && rect.width() > 0f && rect.height() > 0f) {
            fill(rect, viewport, background, alphaMult);
        }
        if (element.borderColor() != null && element.borderWidth() > 0f
                && rect.width() > 0f && rect.height() > 0f) {
            outline(rect, viewport, element.borderColor(), element.borderWidth(), alphaMult);
        }
        if (element.font() != null && element.text() != null && element.textColor() != null) {
            Rect content = element.box().contentBox();
            element.font().drawString(element.text(), viewport.screenXFor(content.x()),
                    viewport.screenTopFor(content.y()), element.textColor(), alphaMult);
        }
        for (UiElement child : element.children()) {
            paintElement(child, viewport, alphaMult);
        }
    }

    private static void fill(Rect rect, UiViewport viewport, Color color, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        setColor(color, alphaMult);
        float left = viewport.screenXFor(rect.x());
        float right = viewport.screenXFor(rect.right());
        float bottom = viewport.screenBottomFor(rect);
        float top = viewport.screenTopFor(rect.y());
        glBegin(GL_QUADS);
        glVertex2f(left, bottom);
        glVertex2f(right, bottom);
        glVertex2f(right, top);
        glVertex2f(left, top);
        glEnd();
    }

    private static void outline(Rect rect, UiViewport viewport, Color color,
                                float width, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        setColor(color, alphaMult);
        glLineWidth(width);
        float left = viewport.screenXFor(rect.x());
        float right = viewport.screenXFor(rect.right());
        float bottom = viewport.screenBottomFor(rect);
        float top = viewport.screenTopFor(rect.y());
        glBegin(GL_LINE_LOOP);
        glVertex2f(left, bottom);
        glVertex2f(right, bottom);
        glVertex2f(right, top);
        glVertex2f(left, top);
        glEnd();
    }

    private static void setColor(Color color, float alphaMult) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f * alphaMult);
    }
}
