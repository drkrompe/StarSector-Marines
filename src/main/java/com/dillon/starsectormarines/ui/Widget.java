package com.dillon.starsectormarines.ui;

/**
 * Minimal widget interface for the mod's own in-FBO UI. Coordinates are in
 * Starsector UI-space pixels (matching {@code InputEventAPI.getX/getY} and
 * {@code PositionAPI.getX/getY}). The widget tree is owned by
 * {@link WidgetRoot}, which routes input and walks render/advance.
 */
public interface Widget {

    /** Hit-test in UI-space pixels. */
    boolean contains(int px, int py);

    /** Per-frame draw in immediate-mode GL. Called after the FBO blit. */
    void render(float alphaMult);

    /** Per-frame tick (animation, hover transitions, etc.). */
    void advance(float dt);

    /** Pointer moved over the widget's bounds. */
    void onMouseMove(int px, int py);

    /** Left-mouse pressed inside the widget. Return {@code true} to consume. */
    boolean onMouseDown(int px, int py);

    /** Left-mouse released. Return {@code true} to consume. */
    boolean onMouseUp(int px, int py);

    /**
     * Mouse-wheel scroll over the widget. {@code delta} mirrors LWJGL's wheel
     * sign convention — positive = scroll up, negative = scroll down. Return
     * {@code true} to consume so widgets behind the scroll-capturing region
     * (the rest of the screen) don't also react.
     *
     * <p>Default no-op — most widgets ignore scroll. Scrollable regions
     * (lists, dossier stacks) override this.
     */
    default boolean onMouseScroll(int px, int py, int delta) { return false; }
}
