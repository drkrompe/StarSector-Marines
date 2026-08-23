package com.dillon.starsectormarines.ui.retained.style;

/** A load-time refusal from the retained CSS subset. */
public final class UiStyleException extends IllegalArgumentException {

    public UiStyleException(String message) {
        super(message);
    }
}
