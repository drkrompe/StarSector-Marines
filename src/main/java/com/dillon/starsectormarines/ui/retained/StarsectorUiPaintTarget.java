package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.fs.starfarer.api.Global;
import org.lwjgl.opengl.Display;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_BIT;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
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
import static org.lwjgl.opengl.GL11.glScissor;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL20.glUseProgram;

/** Paint target for Starsector's fixed-function compatibility UI pass. */
final class StarsectorUiPaintTarget implements UiPaintTarget {

    private final UiViewport viewport;

    StarsectorUiPaintTarget(UiViewport viewport) {
        this.viewport = viewport;
    }

    @Override
    public void begin() {
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_ENABLE_BIT
                | GL_LINE_BIT | GL_TEXTURE_BIT | GL_SCISSOR_BIT);
        glUseProgram(0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_SCISSOR_TEST);
    }

    @Override
    public void end() {
        glUseProgram(0);
        glPopAttrib();
    }

    @Override
    public float devicePixelRatio() {
        return viewport.documentScale()
                * Display.getWidth() / Math.max(1f, Global.getSettings().getScreenWidth());
    }

    @Override
    public void clip(Rect clip) {
        FramebufferScissor scissor = FramebufferScissor.from(viewport, clip,
                Display.getWidth(), Display.getHeight(),
                Global.getSettings().getScreenWidth(),
                Global.getSettings().getScreenHeight());
        glScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
    }

    @Override
    public void fill(Rect rect, Color color, float alphaMult) {
        fillGl(rect, viewport, color, alphaMult);
    }

    @Override
    public void outline(Rect rect, Color color, float width, float alphaMult) {
        outlineGl(rect, viewport, color, width * devicePixelRatio(), alphaMult);
    }

    @Override
    public void text(BitmapFont font, String text, Rect lineBox,
                     Color color, float alphaMult) {
        font.drawStringScaled(text, viewport.screenXFor(lineBox.x()),
                viewport.screenTopFor(lineBox.y()),
                viewport.documentScale(), viewport.documentScale(),
                color, alphaMult);
    }

    @Override
    public CanvasContext canvasContext(CanvasMetrics metrics, Rect visibleBounds,
                                       float alphaMult) {
        return new StarsectorCanvasContext(metrics, visibleBounds, viewport, alphaMult);
    }

    static void fillGl(Rect rect, UiViewport viewport, Color color, float alphaMult) {
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

    static void fillQuadGl(float x0, float y0, float x1, float y1,
                           float x2, float y2, float x3, float y3,
                           UiViewport viewport, Color color, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        setColor(color, alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(viewport.screenXFor(x0), viewport.screenTopFor(y0));
        glVertex2f(viewport.screenXFor(x1), viewport.screenTopFor(y1));
        glVertex2f(viewport.screenXFor(x2), viewport.screenTopFor(y2));
        glVertex2f(viewport.screenXFor(x3), viewport.screenTopFor(y3));
        glEnd();
    }

    static void outlineGl(Rect rect, UiViewport viewport, Color color,
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

    static void lineGl(float x1, float y1, float x2, float y2, UiViewport viewport,
                       Color color, float width, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        setColor(color, alphaMult);
        glLineWidth(width);
        glBegin(GL_LINES);
        glVertex2f(viewport.screenXFor(x1), viewport.screenTopFor(y1));
        glVertex2f(viewport.screenXFor(x2), viewport.screenTopFor(y2));
        glEnd();
    }

    private static void setColor(Color color, float alphaMult) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f * alphaMult);
    }
}
