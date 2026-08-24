package com.dillon.starsectormarines.ui.retained.markup;

/** One ordered literal or whole-expression attribute. */
public record MarkupAttribute(String name, String value, MarkupExpression expression,
                              int line, int column) {
    public MarkupAttribute {
        if (name == null || value == null) throw new IllegalArgumentException("Attribute needs a name and value");
    }

    public MarkupAttribute(String name, String value, int line, int column) {
        this(name, value, null, line, column);
    }

    public boolean isExpression() {
        return expression != null;
    }

    public String written() {
        return expression == null ? value : expression.toString();
    }
}
