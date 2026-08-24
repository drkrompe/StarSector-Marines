package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.reactive.BindingScope;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;

import java.util.List;
import java.util.Map;

/** One fresh retained tree and the reactive lifetime that owns its bindings. */
public final class MarkupInstance implements AutoCloseable {
    private final UiElement root;
    private final List<StyleSheet> styles;
    private final Map<String, UiElement> ids;
    private final Reactor reactor;
    private final BindingScope bindings;

    MarkupInstance(UiElement root, List<StyleSheet> styles, Map<String, UiElement> ids,
                   Reactor reactor, BindingScope bindings) {
        this.root = root;
        this.styles = List.copyOf(styles);
        this.ids = Map.copyOf(ids);
        this.reactor = reactor;
        this.bindings = bindings;
    }

    public UiElement root() { return root; }
    public List<StyleSheet> styles() { return styles; }
    public int flush() { return reactor.flush(); }

    public UiElement requireElement(String id) {
        UiElement element = ids.get(id);
        if (element == null) element = findElement(root, id);
        if (element == null) throw new IllegalArgumentException("No markup element has id \"" + id + "\"");
        return element;
    }

    private static UiElement findElement(UiElement element, String id) {
        if (element.id().equals(id)) return element;
        for (UiElement child : element.children()) {
            UiElement match = findElement(child, id);
            if (match != null) return match;
        }
        return null;
    }

    @Override
    public void close() {
        bindings.close();
    }
}
