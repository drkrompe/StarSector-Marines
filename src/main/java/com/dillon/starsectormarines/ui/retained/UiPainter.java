package com.dillon.starsectormarines.ui.retained;

import java.awt.Color;

/** Walks one retained box tree and emits backend-neutral paint operations. */
final class UiPainter {

    private static final float SCROLL_THUMB_WIDTH = 4f;
    private static final float SCROLL_THUMB_INSET = 2f;
    private static final float SCROLL_THUMB_MIN_HEIGHT = 12f;
    private static final Color SCROLL_THUMB = new Color(0xC8, 0xD0, 0xD8, 0xA6);

    void paint(UiElement root, Rect viewport, float alphaMult,
               CanvasRegistry canvases, UiTextMeasurer text, UiPaintTarget target) {
        target.begin();
        try {
            paintElement(root, alphaMult, viewport, canvases, text, target);
        } finally {
            target.end();
        }
    }

    private void paintElement(UiElement element, float alphaMult, Rect inheritedClip,
                              CanvasRegistry canvases, UiTextMeasurer text,
                              UiPaintTarget target) {
        if (inheritedClip.width() <= 0f || inheritedClip.height() <= 0f) return;
        float elementAlpha = combinedAlpha(alphaMult, element);
        if (elementAlpha <= 0f) return;
        target.clip(inheritedClip);
        Rect rect = element.box().borderBox();
        Color background = element.paintedBackground();
        if (background != null && rect.width() > 0f && rect.height() > 0f) {
            target.fill(rect, background, elementAlpha);
        }
        if (element.borderColor() != null && element.borderWidth() > 0f
                && rect.width() > 0f && rect.height() > 0f) {
            target.outline(rect, element.borderColor(), element.borderWidth(), elementAlpha);
        }
        Rect childClip = UiLayoutEngine.clipForChildren(
                element.overflow(), element.box(), inheritedClip);
        target.clip(childClip);
        UiTextMeasurer.Measurement measured = text.measure(
                element, element.box().contentBox().width());
        if (measured.font() != null && element.text() != null && element.textColor() != null
                && childClip.width() > 0f && childClip.height() > 0f) {
            for (UiTextMeasurer.TextLine line : text.lineBoxes(element, measured)) {
                target.text(measured.font(), line.value(), line.box(),
                        element.textColor(), elementAlpha);
            }
        }
        paintCanvas(element, elementAlpha, childClip, canvases, target);
        for (UiElement child : element.children()) {
            paintElement(child, elementAlpha, childClip, canvases, text, target);
        }
        if (element.focusVisible() && element.focusOutlineColor() != null
                && element.focusOutlineWidth() > 0f) {
            target.clip(inheritedClip);
            target.outline(rect, element.focusOutlineColor(),
                    element.focusOutlineWidth(), elementAlpha);
        }
        Rect thumb = scrollThumbRect(element);
        if (thumb != null) {
            Rect thumbClip = inheritedClip.intersect(element.box().paddingBox());
            if (thumbClip.width() > 0f && thumbClip.height() > 0f) {
                target.clip(thumbClip);
                target.fill(thumb, SCROLL_THUMB, elementAlpha);
            }
        }
    }

    private static void paintCanvas(UiElement element, float alphaMult, Rect inheritedClip,
                                    CanvasRegistry canvases, UiPaintTarget target) {
        if (element.tag() != UiTag.CANVAS) return;
        CanvasProducer producer = canvases.producerOf(element);
        if (producer == null) return;
        Rect canvasClip = inheritedClip.intersect(element.box().contentBox());
        if (canvasClip.width() <= 0f || canvasClip.height() <= 0f) return;
        target.clip(canvasClip);
        CanvasMetrics metrics = CanvasMetrics.of(
                element, element.box(), target.devicePixelRatio());
        Rect visible = canvasVisibleBounds(metrics, canvasClip);
        if (visible == null) return;
        producer.draw(target.canvasContext(metrics, visible, alphaMult));
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
}
