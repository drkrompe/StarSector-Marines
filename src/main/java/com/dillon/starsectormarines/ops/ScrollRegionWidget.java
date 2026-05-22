package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BaseWidget;

import java.util.function.IntConsumer;

/**
 * Invisible widget that captures mouse-wheel events over a rectangular region
 * and forwards the scroll delta to a callback. Used as a scroll-capture layer
 * behind a list / stack — added BEFORE the actual content in the widget tree
 * so the content widgets (cards, buttons) get input priority for everything
 * except scroll events.
 *
 * <p>Renders nothing. Does not absorb mouse clicks — only scroll.
 */
public class ScrollRegionWidget extends BaseWidget {

    private final IntConsumer onScroll;

    public ScrollRegionWidget(float x, float y, float w, float h, IntConsumer onScroll) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.onScroll = onScroll;
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        // Don't absorb clicks — cards / buttons on top of this region need them.
        return false;
    }

    @Override
    public boolean onMouseScroll(int px, int py, int delta) {
        if (!contains(px, py)) return false;
        if (onScroll != null) onScroll.accept(delta);
        return true;
    }
}
