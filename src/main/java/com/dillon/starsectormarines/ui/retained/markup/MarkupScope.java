package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.i18n.Strings;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable nested name scope used while markup expressions are evaluated. */
public final class MarkupScope {

    /** Read-only catalog shared by nested components, resolved when their text is bound. */
    private static final MarkupPropertySource I18N = Strings::get;

    public static final MarkupScope EMPTY = new MarkupScope(null, Map.of());

    private final MarkupScope parent;
    private final Map<String, Object> values;

    private MarkupScope(MarkupScope parent, Map<String, Object> values) {
        this.parent = parent;
        this.values = values;
    }

    public static MarkupScope of(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        return values.isEmpty() ? EMPTY : new MarkupScope(null, new LinkedHashMap<>(values));
    }

    public static MarkupScope of(String name, Object value) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put(Objects.requireNonNull(name, "name"), value);
        return new MarkupScope(null, one);
    }

    public MarkupScope with(String name, Object value) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put(Objects.requireNonNull(name, "name"), value);
        return new MarkupScope(this, one);
    }

    public boolean has(String name) {
        if ("i18n".equals(name)) return true;
        for (MarkupScope scope = this; scope != null; scope = scope.parent) {
            if (scope.values.containsKey(name)) return true;
        }
        return false;
    }

    public Set<String> names() {
        Set<String> result = new LinkedHashSet<>(values.keySet());
        if (parent != null) result.addAll(parent.names());
        return result;
    }

    Object lookup(String name, MarkupExpression expression, String fileName) {
        if ("i18n".equals(name)) return I18N;
        for (MarkupScope scope = this; scope != null; scope = scope.parent) {
            if (scope.values.containsKey(name)) return scope.values.get(name);
        }
        throw UiMarkupException.at(fileName, expression.line(), expression.column(), expression
                + " reads \"" + name + "\", but that name was not supplied. In scope: "
                + (names().isEmpty() ? "nothing" : String.join(", ", names())) + ".");
    }
}
