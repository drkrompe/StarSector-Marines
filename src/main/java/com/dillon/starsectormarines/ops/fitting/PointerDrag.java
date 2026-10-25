package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.ui.retained.UiPointerEvent;

/**
 * A part picked up with the pointer: what is carried and where the press
 * began. Nothing is carried until the pointer has moved a few pixels, so a
 * press let go where it started is still a click.
 *
 * @param <T> what the room carries
 */
public final class PointerDrag<T> {

    /** Four pixels of travel, squared: less than that is a click. */
    public static final float THRESHOLD_SQUARED = 16f;

    private T carried;
    /** The board this drag hides the tooltip on, or null. */
    private BoardHover hover;
    private float startX;
    private float startY;
    private boolean moved;

    /** Holds {@code hover} for every drag: from the press to the drop its board names nothing. */
    public PointerDrag<T> holding(BoardHover hover) {
        this.hover = hover;
        return this;
    }

    /** Picks {@code carried} up at the press, capturing the pointer so the drag follows it anywhere. */
    public void begin(UiPointerEvent event, T carried) {
        this.carried = carried;
        if (hover != null) hover.hold();
        startX = event.x();
        startY = event.y();
        moved = false;
        event.capturePointer();
        event.preventDefault();
    }

    /** Whether this move carries the part: false until it has travelled past the threshold. */
    public boolean move(UiPointerEvent event) {
        if (carried == null) return false;
        if (!moved) {
            float dx = event.x() - startX;
            float dy = event.y() - startY;
            if (dx * dx + dy * dy < THRESHOLD_SQUARED) return false;
            moved = true;
        }
        return true;
    }

    public boolean active() { return carried != null; }

    /** Whether the pointer has travelled far enough that letting go is a drop, not a click. */
    public boolean moved() { return moved; }

    public T carried() { return carried; }

    /** Lets go: releases the pointer and forgets what was carried. */
    public void end(UiPointerEvent event) {
        if (carried != null) event.releasePointerCapture();
        cancel();
    }

    /** Forgets what is carried without a release, as when the room is closed under the pointer. */
    public void cancel() {
        if (carried != null && hover != null) hover.release();
        carried = null;
        moved = false;
    }
}
