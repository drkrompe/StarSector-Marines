package com.dillon.starsectormarines.ui.retained.markup;

/** One whole-value expression in content position. */
public record MarkupHole(MarkupExpression expression, int line, int column) implements MarkupNode {
    public MarkupHole {
        if (expression == null) throw new IllegalArgumentException("Markup hole needs an expression");
    }
}
