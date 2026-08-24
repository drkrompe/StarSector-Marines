package com.dillon.starsectormarines.ui.retained.markup;

/** A keyed repetition declared by {@code each="item in items" key="{item.id}"}. */
public record MarkupLoop(String variable, MarkupExpression items, MarkupExpression key) {
    public MarkupLoop {
        if (variable == null || items == null || key == null) {
            throw new IllegalArgumentException("A markup loop needs a variable, items, and key");
        }
    }
}
