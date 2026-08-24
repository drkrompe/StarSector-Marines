package com.dillon.starsectormarines.ui.retained.markup;

/** One positioned node in a parsed {@code .mlx} template. */
public sealed interface MarkupNode permits MarkupElement, MarkupText, MarkupHole {
    int line();
    int column();
}
