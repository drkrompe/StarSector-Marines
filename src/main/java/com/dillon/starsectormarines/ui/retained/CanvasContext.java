package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;

import java.awt.Color;

/** Fixed-function Starsector drawing surface exposed to canvas producers. */
public final class CanvasContext {

    private final CanvasMetrics metrics;
    private final Rect visibleBounds;
    private final UiViewport viewport;
    private final float alphaMult;

    CanvasContext(CanvasMetrics metrics, Rect visibleBounds, UiViewport viewport,
                  float alphaMult) {
        this.metrics = metrics;
        this.visibleBounds = visibleBounds;
        this.viewport = viewport;
        this.alphaMult = alphaMult;
    }

    public CanvasMetrics metrics() {
        return metrics;
    }

    /** Visible portion of this paint in canvas-local coordinates. */
    public Rect visibleBounds() {
        return visibleBounds;
    }

    public void fillRect(float x, float y, float width, float height, Color color) {
        requireRect(x, y, width, height);
        UiPainter.fill(documentRect(x, y, width, height), viewport,
                requireColor(color), alphaMult);
    }

    public void strokeRect(float x, float y, float width, float height,
                           Color color, float strokeWidth) {
        requireRect(x, y, width, height);
        requirePositive(strokeWidth, "stroke width");
        line(x, y, x + width, y, color, strokeWidth);
        line(x + width, y, x + width, y + height, color, strokeWidth);
        line(x + width, y + height, x, y + height, color, strokeWidth);
        line(x, y + height, x, y, color, strokeWidth);
    }

    public void line(float x1, float y1, float x2, float y2,
                     Color color, float strokeWidth) {
        requireFinite(x1, y1, x2, y2);
        requirePositive(strokeWidth, "stroke width");
        UiPainter.line(metrics.toDocumentX(x1), metrics.toDocumentY(y1),
                metrics.toDocumentX(x2), metrics.toDocumentY(y2), viewport,
                requireColor(color), strokeWidth * strokeScale(metrics, x2 - x1, y2 - y1),
                alphaMult);
    }

    public void text(BitmapFont font, String text, float x, float y, Color color) {
        if (font == null || text == null) throw new IllegalArgumentException("font and text required");
        requireFinite(x, y);
        font.drawStringScaled(text, viewport.screenXFor(metrics.toDocumentX(x)),
                viewport.screenTopFor(metrics.toDocumentY(y)), metrics.scaleX(),
                metrics.scaleY(), requireColor(color), alphaMult);
    }

    /**
     * Document scaling of a line's normal. This preserves anisotropic canvas
     * stretching for horizontal and vertical strokes despite OpenGL's scalar
     * line-width API.
     */
    static float strokeScale(CanvasMetrics metrics, float deltaX, float deltaY) {
        float length = (float) Math.hypot(deltaX, deltaY);
        if (!(length > 0f)) {
            return (float) Math.sqrt(metrics.scaleX() * metrics.scaleY());
        }
        float normalX = -deltaY / length;
        float normalY = deltaX / length;
        return (float) Math.hypot(normalX * metrics.scaleX(),
                normalY * metrics.scaleY());
    }

    private Rect documentRect(float x, float y, float width, float height) {
        return new Rect(metrics.toDocumentX(x), metrics.toDocumentY(y),
                width * metrics.scaleX(), height * metrics.scaleY());
    }

    private static Color requireColor(Color color) {
        if (color == null) throw new IllegalArgumentException("color required");
        return color;
    }

    private static void requireRect(float x, float y, float width, float height) {
        requireFinite(x, y, width, height);
        if (width < 0f || height < 0f) {
            throw new IllegalArgumentException("rectangle extent cannot be negative");
        }
    }

    private static void requirePositive(float value, String label) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireFinite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("canvas coordinates must be finite");
            }
        }
    }
}
