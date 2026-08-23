package com.dillon.starsectormarines.ui.retained;

/** A bubbling pointer event in document coordinates. */
public final class UiPointerEvent {

    private final UiDocument document;
    private final UiElement target;
    private final float x;
    private final float y;
    private final PointerButton button;
    private UiElement currentTarget;
    private boolean propagationStopped;
    private boolean defaultPrevented;

    UiPointerEvent(UiDocument document, UiElement target, float x, float y,
                   PointerButton button) {
        this.document = document;
        this.target = target;
        this.x = x;
        this.y = y;
        this.button = button;
    }

    public UiElement target() {
        return target;
    }

    public UiElement currentTarget() {
        return currentTarget;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public PointerButton button() {
        return button;
    }

    public void stopPropagation() {
        propagationStopped = true;
    }

    public void preventDefault() {
        defaultPrevented = true;
    }

    public boolean defaultPrevented() {
        return defaultPrevented;
    }

    /** Captures subsequent pointer delivery to the current bubbling element. */
    public void capturePointer() {
        document.setPointerCapture(currentTarget);
    }

    public void releasePointerCapture() {
        document.releasePointerCapture();
    }

    boolean propagationStopped() {
        return propagationStopped;
    }

    void currentTarget(UiElement currentTarget) {
        this.currentTarget = currentTarget;
    }
}
