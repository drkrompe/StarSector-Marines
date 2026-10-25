package com.dillon.starsectormarines.ops.fitting;

/**
 * Where the pointer is on a fitting board, and whether a part is in hand.
 *
 * <p>The one rule every board shares: what is under the pointer is named by the board
 * tooltip, except while a part is held, when nothing may cover the socket it is going into:
 * neither the board's tooltip nor a spec sheet ({@code SpecSheetBinder#heldWhile}).
 * A room's {@link PointerDrag} holds this for the whole drag, from the press to the drop.
 */
public final class BoardHover {

    private float x = Float.NaN;
    private float y = Float.NaN;
    private boolean held;

    /** The pointer is at this canvas point. */
    public void at(float canvasX, float canvasY) {
        x = canvasX;
        y = canvasY;
    }

    /** A part was picked up: the board names nothing until it is let go. */
    void hold() {
        held = true;
    }

    /** The part was let go: the board names what is under the pointer once it moves again. */
    void release() {
        held = false;
        x = Float.NaN;
        y = Float.NaN;
    }

    public float x() { return x; }

    public float y() { return y; }

    /** Whether a part is in hand, from the press that picked it up to the drop. */
    public boolean held() {
        return held;
    }

    /** Whether the board may name what is under the pointer now. */
    public boolean names() {
        return !held && Float.isFinite(x) && Float.isFinite(y);
    }
}
