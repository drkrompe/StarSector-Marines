package com.dillon.starsectormarines.ui.retained.markup;

/**
 * Explicit, Starsector-safe property surface for dotted MLX expressions.
 * Implementations throw {@link IllegalArgumentException} for names they do not expose.
 */
public interface MarkupPropertySource {

    Object markupProperty(String name);
}
