package com.dillon.starsectormarines.ui.retained.markup;

/** Literal, entity-decoded character data. */
public record MarkupText(String text, int line, int column) implements MarkupNode {
    public MarkupText {
        if (text == null) throw new IllegalArgumentException("Markup text must not be null");
    }
}
