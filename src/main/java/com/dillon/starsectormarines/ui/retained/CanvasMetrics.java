package com.dillon.starsectormarines.ui.retained;

import java.util.Objects;

/**
 * The one mapping between a canvas drawing surface and its rendered document
 * content box. UI scale is intentionally absent from point conversion.
 */
public record CanvasMetrics(Rect contentBox, int surfaceWidth, int surfaceHeight,
                            float devicePixelRatio) {

    public CanvasMetrics {
        Objects.requireNonNull(contentBox, "contentBox");
        if (!Float.isFinite(contentBox.width()) || !Float.isFinite(contentBox.height())
                || contentBox.width() < 0f || contentBox.height() < 0f) {
            throw new IllegalArgumentException("Canvas content box must be finite and non-negative");
        }
        if (surfaceWidth < 0 || surfaceHeight < 0) {
            throw new IllegalArgumentException("Canvas surface size cannot be negative");
        }
        if (!Float.isFinite(devicePixelRatio) || devicePixelRatio <= 0f) {
            throw new IllegalArgumentException("Device pixel ratio must be finite and positive");
        }
    }

    public static CanvasMetrics of(UiElement canvas, LayoutBox box, float devicePixelRatio) {
        int width = canvas.canvasWidth();
        return box == null ? null : new CanvasMetrics(box.contentBox(), width,
                canvas.canvasHeight(), devicePixelRatio);
    }

    public float scaleX() {
        return surfaceWidth == 0 ? Float.NaN : contentBox.width() / surfaceWidth;
    }

    public float scaleY() {
        return surfaceHeight == 0 ? Float.NaN : contentBox.height() / surfaceHeight;
    }

    public float deviceScaleX() {
        return scaleX() * devicePixelRatio;
    }

    public float deviceScaleY() {
        return scaleY() * devicePixelRatio;
    }

    public float toCanvasX(float documentX) {
        float scale = scaleX();
        return scale > 0f ? (documentX - contentBox.x()) / scale : Float.NaN;
    }

    public float toCanvasY(float documentY) {
        float scale = scaleY();
        return scale > 0f ? (documentY - contentBox.y()) / scale : Float.NaN;
    }

    public float toDocumentX(float canvasX) {
        return contentBox.x() + canvasX * scaleX();
    }

    public float toDocumentY(float canvasY) {
        return contentBox.y() + canvasY * scaleY();
    }
}
