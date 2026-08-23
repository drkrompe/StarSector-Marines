package com.dillon.starsectormarines.ui.retained;

import java.util.List;

/**
 * One retained element tree with layout and pointer state.
 *
 * <p>Pointer methods speak document coordinates. The hosting screen converts
 * through {@link UiViewport} once before calling them.
 */
public final class UiDocument {

    private final UiElement root;
    private final UiLayoutEngine layout = new UiLayoutEngine();
    private final UiPainter painter = new UiPainter();
    private UiElement hovered;
    private UiElement pressed;

    public UiDocument(UiElement root) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        this.root = root;
    }

    public UiElement root() {
        return root;
    }

    public void layout(float width, float height) {
        layout.layout(root, width, height);
    }

    public void render(UiViewport viewport, float alphaMult) {
        painter.paint(root, viewport, alphaMult);
    }

    public void pointerMoved(float x, float y) {
        UiElement target = hit(root, x, y);
        if (target == hovered) return;
        if (hovered != null) hovered.hovered(false);
        hovered = target;
        if (hovered != null) hovered.hovered(true);
    }

    public boolean pointerDown(float x, float y) {
        UiElement target = hit(root, x, y);
        if (pressed != null) pressed.armed(false);
        pressed = target;
        if (pressed == null) return false;
        pressed.armed(true);
        return true;
    }

    public boolean pointerUp(float x, float y) {
        UiElement wasPressed = pressed;
        if (wasPressed == null) return false;
        pressed = null;
        wasPressed.armed(false);
        UiElement releasedOver = hit(root, x, y);
        if (releasedOver == wasPressed) wasPressed.click();
        return true;
    }

    UiElement hit(float x, float y) {
        return hit(root, x, y);
    }

    private static UiElement hit(UiElement element, float x, float y) {
        List<UiElement> children = element.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiElement hit = hit(children.get(i), x, y);
            if (hit != null) return hit;
        }
        return element.clickable() && element.box().borderBox().contains(x, y)
                ? element : null;
    }
}
