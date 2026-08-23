package com.dillon.starsectormarines.ui.retained;

/** Four resolved document-pixel edges in CSS top/right/bottom/left order. */
public record Insets(float top, float right, float bottom, float left) {

    public static final Insets ZERO = new Insets(0f, 0f, 0f, 0f);

    public static Insets uniform(float all) {
        return new Insets(all, all, all, all);
    }

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }
}
