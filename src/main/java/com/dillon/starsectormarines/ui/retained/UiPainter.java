package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.style.StyleResolver;
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

/** Paints retained boxes into Starsector's fixed-function UI pass. */
final class UiPainter {

    private static final float SCROLL_THUMB_WIDTH = 4f;
    private static final float SCROLL_THUMB_INSET = 2f;
    private static final float SCROLL_THUMB_MIN_HEIGHT = 12f;
    private static final Color SCROLL_THUMB = new Color(0xC8, 0xD0, 0xD8, 0xA6);

    void paint(UiElement root, UiViewport viewport, float alphaMult,
               CanvasRegistry canvases, StyleResolver styles) {
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_ENABLE_BIT
                | GL_LINE_BIT | GL_TEXTURE_BIT | GL_SCISSOR_BIT);
        try {
            glUseProgram(0);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glEnable(GL_SCISSOR_TEST);
            Rect viewportClip = new Rect(0f, 0f, viewport.width(), viewport.height());
            paintElement(root, viewport, alphaMult, viewportClip, canvases, styles);
        } finally {
            glUseProgram(0);
            glPopAttrib();
        }
    }

    private void paintElement(UiElement element, UiViewport viewport, float alphaMult,
                              Rect inheritedClip, CanvasRegistry canvases,
                              StyleResolver styles) {
        if (inheritedClip.width() <= 0f || inheritedClip.height() <= 0f) return;
        float elementAlpha = combinedAlpha(alphaMult, element);
        if (elementAlpha <= 0f) return;
        applyClip(viewport, inheritedClip);
        Rect rect = element.box().borderBox();
        Color background = element.paintedBackground();
        if (background != null && rect.width() > 0f && rect.height() > 0f) {
            fill(rect, viewport, background, elementAlpha);
        }
        if (element.borderColor() != null && element.borderWidth() > 0f
                && rect.width() > 0f && rect.height() > 0f) {
            outline(rect, viewport, element.borderColor(), element.borderWidth(), elementAlpha);
        }
        Rect childClip = UiLayoutEngine.clipForChildren(
                element.overflow(), element.box(), inheritedClip);
        applyClip(viewport, childClip);
        BitmapFont font = styles.fontFor(element);
        if (font != null && element.text() != null && element.textColor() != null
                && childClip.width() > 0f && childClip.height() > 0f) {
            Rect content = element.box().contentBox();
            font.drawString(element.text(), viewport.screenXFor(content.x()),
                    viewport.screenTopFor(content.y()), element.textColor(), elementAlpha);
        }
        paintCanvas(element, viewport, elementAlpha, childClip, canvases);
        for (UiElement child : element.children()) {
            paintElement(child, viewport, elementAlpha, childClip, canvases, styles);
        }
        if (element.focusVisible() && element.focusOutlineColor() != null
                && element.focusOutlineWidth() > 0f) {
            applyClip(viewport, inheritedClip);
            outline(rect, viewport, element.focusOutlineColor(),
                    element.focusOutlineWidth(), elementAlpha);
        }
        Rect thumb = scrollThumbRect(element);
        if (thumb != null) {
            Rect thumbClip = inheritedClip.intersect(element.box().paddingBox());
            if (thumbClip.width() > 0f && thumbClip.height() > 0f) {
                applyClip(viewport, thumbClip);
                fill(thumb, viewport, SCROLL_THUMB, elementAlpha);
            }
        }
    }

    private static void paintCanvas(UiElement element, UiViewport viewport, float alphaMult,
                                    Rect inheritedClip, CanvasRegistry canvases) {
        if (element.tag() != UiTag.CANVAS) return;
        CanvasProducer producer = canvases.producerOf(element);
        if (producer == null) return;
        Rect canvasClip = inheritedClip.intersect(element.box().contentBox());
        if (canvasClip.width() <= 0f || canvasClip.height() <= 0f) return;
        applyClip(viewport, canvasClip);
        float devicePixelRatio = Display.getWidth()
                / Math.max(1f, Global.getSettings().getScreenWidth());
        CanvasMetrics metrics = CanvasMetrics.of(element, element.box(), devicePixelRatio);
        Rect visible = canvasVisibleBounds(metrics, canvasClip);
        if (visible == null) return;
        producer.draw(new CanvasContext(metrics, visible, viewport, alphaMult));
    }

    static Rect canvasVisibleBounds(CanvasMetrics metrics, Rect documentClip) {
        float left = metrics.toCanvasX(documentClip.x());
        float top = metrics.toCanvasY(documentClip.y());
        float right = metrics.toCanvasX(documentClip.right());
        float bottom = metrics.toCanvasY(documentClip.bottom());
        if (!Float.isFinite(left) || !Float.isFinite(top)
                || !Float.isFinite(right) || !Float.isFinite(bottom)) return null;
        return new Rect(left, top, Math.max(0f, right - left),
                Math.max(0f, bottom - top));
    }

    static float combinedAlpha(float inheritedAlpha, UiElement element) {
        return inheritedAlpha * element.opacity();
    }

    /** Pure geometry seam for the overlay scrollbar and its headless tests. */
    static Rect scrollThumbRect(UiElement element) {
        if (element.overflow() != Overflow.SCROLL) return null;
        LayoutBox box = element.box();
        float range = box.maxScrollTop();
        if (range <= 0f) return null;
        Rect padding = box.paddingBox();
        float trackHeight = padding.height() - SCROLL_THUMB_INSET * 2f;
        float visible = box.contentBox().height();
        float content = box.scrollHeight();
        if (trackHeight <= 0f || visible <= 0f || content <= 0f
                || padding.width() < SCROLL_THUMB_WIDTH + SCROLL_THUMB_INSET) {
            return null;
        }
        float thumbHeight = Math.min(trackHeight, Math.max(SCROLL_THUMB_MIN_HEIGHT,
                trackHeight * visible / content));
        float travel = trackHeight - thumbHeight;
        float fraction = travel <= 0f ? 0f
                : Math.max(0f, Math.min(1f, element.scrollTop() / range));
        return new Rect(
                padding.right() - SCROLL_THUMB_INSET - SCROLL_THUMB_WIDTH,
                padding.y() + SCROLL_THUMB_INSET + travel * fraction,
                SCROLL_THUMB_WIDTH,
                thumbHeight);
    }

    private static void applyClip(UiViewport viewport, Rect clip) {
        FramebufferScissor scissor = FramebufferScissor.from(viewport, clip,
                Display.getWidth(), Display.getHeight(),
                Global.getSettings().getScreenWidth(),
                Global.getSettings().getScreenHeight());
        glScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
    }

    static void fill(Rect rect, UiViewport viewport, Color color, float alphaMult) {
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

    static void outline(Rect rect, UiViewport viewport, Color color,
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

    static void line(float x1, float y1, float x2, float y2, UiViewport viewport,
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
