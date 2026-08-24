package com.dillon.starsectormarines.ui.retained;

/** How an element arranges its immediate children. */
public enum UiLayout {
    ROW,
    COLUMN,
    /** Responsive fixed-item gallery: fill columns, then wrap into rows. */
    GRID,
    STACK
}
