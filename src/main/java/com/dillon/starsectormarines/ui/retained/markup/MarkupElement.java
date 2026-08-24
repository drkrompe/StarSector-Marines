package com.dillon.starsectormarines.ui.retained.markup;

import java.util.List;

/** An element as authored, before it becomes a retained {@code UiElement}. */
public record MarkupElement(String tagName, List<MarkupAttribute> attributes,
                            MarkupLoop loop, List<MarkupNode> children,
                            int line, int column) implements MarkupNode {
    public MarkupElement {
        if (tagName == null) throw new IllegalArgumentException("Element needs a tag name");
        attributes = List.copyOf(attributes);
        children = List.copyOf(children);
    }

    public boolean isComponent() {
        return tagName.indexOf('-') >= 0;
    }

    public boolean repeats() {
        return loop != null;
    }

    public MarkupAttribute attribute(String name) {
        for (MarkupAttribute attribute : attributes) {
            if (attribute.name().equals(name)) return attribute;
        }
        return null;
    }
}
