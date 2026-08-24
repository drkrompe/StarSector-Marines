package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.List;
import java.util.Map;

/** A compiled whole-value expression: optional negation followed by a dotted name path. */
public final class MarkupExpression {

    private final boolean negated;
    private final List<String> path;
    private final String source;
    private final int line;
    private final int column;

    public MarkupExpression(boolean negated, List<String> path, String source, int line, int column) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Expression needs a path");
        if (source == null) throw new IllegalArgumentException("Expression source must not be null");
        this.negated = negated;
        this.path = List.copyOf(path);
        this.source = source;
        this.line = line;
        this.column = column;
    }

    public boolean negated() { return negated; }
    public List<String> path() { return path; }
    public String source() { return source; }
    public int line() { return line; }
    public int column() { return column; }

    public String rootName() { return path.get(0); }

    public boolean isName() { return !negated && path.size() == 1; }

    Object lookup(MarkupScope scope, String fileName) {
        return scope.lookup(rootName(), this, fileName);
    }

    public Object evaluate(MarkupScope scope, String fileName) {
        Object current = unwrap(scope.lookup(rootName(), this, fileName));
        for (int step = 1; step < path.size(); step++) {
            if (current == null) {
                throw error(fileName, "\"" + path.get(step - 1) + "\" is null, so \""
                        + path.get(step) + "\" cannot be read from it.");
            }
            current = unwrap(read(current, step, fileName));
        }
        if (!negated) return current;
        if (!(current instanceof Boolean)) {
            throw error(fileName, "! requires a boolean, but resolved " + describe(current) + ".");
        }
        return !((Boolean) current);
    }

    public String asText(MarkupScope scope, String fileName) {
        Object value = evaluate(scope, fileName);
        return value == null ? "" : String.valueOf(value);
    }

    public String asAttribute(MarkupScope scope, String fileName) {
        Object value = evaluate(scope, fileName);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value ? "" : null;
        return String.valueOf(value);
    }

    private static Object unwrap(Object value) {
        Object current = value;
        while (current instanceof Signal<?>) current = ((Signal<?>) current).get();
        return current;
    }

    private Object read(Object owner, int step, String fileName) {
        String member = path.get(step);
        if (owner instanceof MarkupPropertySource source) {
            try {
                return source.markupProperty(member);
            } catch (IllegalArgumentException failure) {
                throw error(fileName, describe(owner) + " does not expose \"" + member
                        + "\" to MLX: " + failure.getMessage());
            } catch (RuntimeException failure) {
                UiMarkupException error = error(fileName, "reading \"" + member + "\" on "
                        + describe(owner) + " threw " + failure + ".");
                error.initCause(failure);
                throw error;
            }
        }
        if (owner instanceof Map<?, ?> values) {
            if (values.containsKey(member)) return values.get(member);
            throw error(fileName, "Map does not expose \"" + member + "\" to MLX.");
        }
        throw error(fileName, describe(owner) + " is not an MLX property source. Implement "
                + "MarkupPropertySource or supply a Map.");
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + " \"" + value + "\"";
    }

    private UiMarkupException error(String fileName, String message) {
        return UiMarkupException.at(fileName, line, column, source + ": " + message);
    }

    @Override
    public String toString() { return source; }
}
