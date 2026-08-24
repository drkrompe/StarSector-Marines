package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A compiled whole-value expression: optional negation followed by a dotted name path. */
public final class MarkupExpression {

    private final boolean negated;
    private final List<String> path;
    private final String source;
    private final int line;
    private final int column;
    private final Class<?>[] cachedOwner;
    private final Method[] cachedAccessor;

    public MarkupExpression(boolean negated, List<String> path, String source, int line, int column) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Expression needs a path");
        if (source == null) throw new IllegalArgumentException("Expression source must not be null");
        this.negated = negated;
        this.path = List.copyOf(path);
        this.source = source;
        this.line = line;
        this.column = column;
        cachedOwner = new Class<?>[path.size()];
        cachedAccessor = new Method[path.size()];
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
        Class<?> type = owner.getClass();
        Method accessor = cachedOwner[step] == type ? cachedAccessor[step] : null;
        if (accessor == null) {
            accessor = findAccessor(type, path.get(step), fileName);
            cachedOwner[step] = type;
            cachedAccessor[step] = accessor;
        }
        try {
            return accessor.invoke(owner);
        } catch (IllegalAccessException failure) {
            throw error(fileName, "\"" + path.get(step) + "\" on " + type.getSimpleName()
                    + " cannot be read: " + failure.getMessage());
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            UiMarkupException error = error(fileName, "reading \"" + path.get(step) + "\" on "
                    + type.getSimpleName() + " threw " + cause + ".");
            error.initCause(cause);
            throw error;
        }
    }

    private Method findAccessor(Class<?> type, String member, String fileName) {
        String capitalized = Character.toUpperCase(member.charAt(0)) + member.substring(1);
        for (String name : List.of(member, "get" + capitalized, "is" + capitalized)) {
            Method method = accessor(type, name);
            if (method != null) return method;
        }
        throw error(fileName, type.getSimpleName() + " has no \"" + member
                + "\" accessor; expected " + member + "(), get" + capitalized
                + "(), or is" + capitalized + "().");
    }

    private static Method accessor(Class<?> type, String name) {
        Method found;
        try {
            found = type.getMethod(name);
        } catch (NoSuchMethodException failure) {
            return null;
        }
        if (found.getReturnType() == void.class) return null;
        if (Modifier.isPublic(found.getDeclaringClass().getModifiers())) return found;
        for (Class<?> ancestor : ancestors(type)) {
            if (!Modifier.isPublic(ancestor.getModifiers())) continue;
            try {
                Method redeclared = ancestor.getMethod(name);
                if (redeclared.getReturnType() != void.class) return redeclared;
            } catch (NoSuchMethodException ignored) {
                // Keep looking.
            }
        }
        try {
            found.setAccessible(true);
        } catch (RuntimeException ignored) {
            // Invocation below reports a positioned failure if access remains refused.
        }
        return found;
    }

    private static List<Class<?>> ancestors(Class<?> type) {
        List<Class<?>> found = new ArrayList<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(type);
        while (!pending.isEmpty()) {
            Class<?> current = pending.remove();
            if (current == Object.class || found.contains(current)) continue;
            found.add(current);
            if (current.getSuperclass() != null) pending.add(current.getSuperclass());
            pending.addAll(List.of(current.getInterfaces()));
        }
        return found;
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
