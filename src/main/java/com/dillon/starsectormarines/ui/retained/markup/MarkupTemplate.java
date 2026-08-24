package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.style.StyleSheet;

import java.util.List;

/** One parsed single-file component. */
public record MarkupTemplate(String name, String fileName, List<String> props,
                             MarkupElement root, StyleSheet style) {
    private static final String SCOPE_PREFIX = "mlx-";

    public MarkupTemplate {
        props = List.copyOf(props);
    }

    public String scopeClass() {
        return SCOPE_PREFIX + name;
    }
}
