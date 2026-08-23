package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.UiElement;

import java.util.ArrayList;
import java.util.List;

/** One selector list and the declaration it applies. */
public record StyleRule(List<Selector> selectors, StyleDeclaration declaration) {

    public StyleRule {
        if (selectors.isEmpty()) throw new IllegalArgumentException("A style rule needs a selector");
        selectors = List.copyOf(selectors);
    }

    public boolean matches(UiElement element) {
        for (Selector selector : selectors) {
            if (selector.matches(element)) return true;
        }
        return false;
    }

    public StyleRule scopedTo(String className) {
        List<Selector> scoped = new ArrayList<>();
        for (Selector selector : selectors) scoped.add(selector.scopedTo(className));
        return new StyleRule(scoped, declaration);
    }
}
