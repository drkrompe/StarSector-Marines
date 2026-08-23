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
    private Rect viewport = Rect.EMPTY;
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
        viewport = new Rect(0f, 0f, Math.max(0f, width), Math.max(0f, height));
        layout.layout(root, viewport.width(), viewport.height());
    }

    public void render(UiViewport viewport, float alphaMult) {
        painter.paint(root, viewport, alphaMult);
    }

    public void pointerMoved(float x, float y) {
        UiElement target = hit(x, y);
        if (target == hovered) return;
        if (hovered != null) hovered.hovered(false);
        hovered = target;
        if (hovered != null) hovered.hovered(true);
    }

    public boolean pointerDown(float x, float y) {
        UiElement target = hit(x, y);
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
        UiElement releasedOver = hit(x, y);
        if (releasedOver == wasPressed) wasPressed.click();
        return true;
    }

    /**
     * Applies a vertical wheel delta to the nearest scroll surface that can
     * move, then walks outward when an inner surface reaches its boundary.
     * A wheel over a scroll surface remains handled at its terminal boundary
     * so the host cannot also act on the same notch.
     */
    public boolean pointerScrolled(float x, float y, float deltaY) {
        if (deltaY == 0f) return false;
        boolean overScrollSurface = false;
        for (UiElement target = elementAt(x, y); target != null; target = target.parent()) {
            if (target.overflow() != Overflow.SCROLL) continue;
            overScrollSurface = true;
            float range = target.box().maxScrollTop();
            if (range <= 0f) continue;
            float from = Math.min(target.scrollTop(), range);
            float to = Math.max(0f, Math.min(from + deltaY, range));
            if (to == from) continue;
            target.scrollTop(to);
            layout.layout(root, viewport.width(), viewport.height());
            pointerMoved(x, y);
            return true;
        }
        return overScrollSurface;
    }

    UiElement hit(float x, float y) {
        for (UiElement target = elementAt(x, y); target != null; target = target.parent()) {
            if (target.clickable()) return target;
        }
        return null;
    }

    UiElement elementAt(float x, float y) {
        return elementAt(root, x, y, viewport);
    }

    private static UiElement elementAt(UiElement element, float x, float y,
                                       Rect inheritedClip) {
        Rect childClip = UiLayoutEngine.clipForChildren(
                element.overflow(), element.box(), inheritedClip);
        List<UiElement> children = element.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiElement hit = elementAt(children.get(i), x, y, childClip);
            if (hit != null) return hit;
        }
        return inheritedClip.contains(x, y)
                && element.box().borderBox().contains(x, y)
                ? element : null;
    }
}
