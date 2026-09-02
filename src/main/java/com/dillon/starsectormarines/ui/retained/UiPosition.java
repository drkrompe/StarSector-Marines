package com.dillon.starsectormarines.ui.retained;

/**
 * CSS {@code position}: whether an element takes part in its parent's flow.
 *
 * <p>{@link #ABSOLUTE} is the bounded form of the CSS property the noun doc
 * anticipated when it listed the layout contexts: the child is removed from
 * row, column, grid, and stack distribution and placed at {@code left} /
 * {@code top} inside its parent's content box. There is no positioned-ancestor
 * search and no {@code right} / {@code bottom} pair; the containing block is
 * always the immediate parent, which is what a document-level overlay needs and
 * all the layout pass can honour without a second placement phase.
 */
public enum UiPosition {
    STATIC,
    ABSOLUTE
}
