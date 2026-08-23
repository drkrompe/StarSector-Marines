package com.dillon.starsectormarines.ui.retained;

/**
 * Mutable retained layout result for one {@link UiElement}.
 *
 * <p>The stored rectangle is the border box. Painter and hit-testing hold this
 * same object so a screen cannot accidentally draw and click two different
 * derivations of its geometry.
 */
public final class LayoutBox {

    private Rect borderBox = Rect.EMPTY;
    private Insets padding = Insets.ZERO;
    private float borderWidth;
    private float scrollHeight;

    public Rect borderBox() {
        return borderBox;
    }

    public Rect paddingBox() {
        return borderBox.inset(Insets.uniform(borderWidth));
    }

    public Rect contentBox() {
        return paddingBox().inset(padding);
    }

    public Insets padding() {
        return padding;
    }

    public float borderWidth() {
        return borderWidth;
    }

    /** Natural vertical content extent, recorded for clipping boxes. */
    public float scrollHeight() {
        return scrollHeight;
    }

    /** Largest vertical offset that still leaves content in view. */
    public float maxScrollTop() {
        return Math.max(0f, scrollHeight - contentBox().height());
    }

    void place(Rect borderBox, Insets padding, float borderWidth) {
        this.borderBox = borderBox;
        this.padding = padding;
        this.borderWidth = borderWidth;
    }

    void scrollHeight(float scrollHeight) {
        this.scrollHeight = Math.max(0f, scrollHeight);
    }

    void translate(float deltaX, float deltaY) {
        borderBox = new Rect(borderBox.x() + deltaX, borderBox.y() + deltaY,
                borderBox.width(), borderBox.height());
    }

    @Override
    public String toString() {
        return "LayoutBox(" + borderBox.x() + ", " + borderBox.y() + ", "
                + borderBox.width() + "x" + borderBox.height() + ")";
    }
}
