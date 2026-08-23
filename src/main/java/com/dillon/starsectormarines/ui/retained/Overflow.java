package com.dillon.starsectormarines.ui.retained;

/**
 * What happens to content that exceeds an element's box.
 *
 * <p>Copied from MoonLightEngine's retained UI style vocabulary. {@code hidden}
 * and {@code scroll} establish the same clip; scrolling behavior is layered on
 * that shared geometry in the next U2 slice.
 */
public enum Overflow {
    VISIBLE,
    HIDDEN,
    SCROLL;

    /** Whether this value clips the element's content and descendants. */
    public boolean clips() {
        return this != VISIBLE;
    }
}
