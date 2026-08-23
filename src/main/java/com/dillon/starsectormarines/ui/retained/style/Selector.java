package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tag, class, id, and interaction-state matching with descendant combinators. */
public final class Selector {

    private final List<Compound> parts;
    private final String source;

    private Selector(List<Compound> parts, String source) {
        this.parts = List.copyOf(parts);
        this.source = source;
    }

    public boolean matches(UiElement element) {
        int index = parts.size() - 1;
        if (!parts.get(index).matches(element)) return false;
        index--;
        UiElement ancestor = element.parent();
        while (index >= 0) {
            if (ancestor == null) return false;
            if (parts.get(index).matches(ancestor)) index--;
            ancestor = ancestor.parent();
        }
        return true;
    }

    /** Adds a component scope class to the subject compound only. */
    public Selector scopedTo(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Scope class must not be blank");
        }
        int subjectIndex = parts.size() - 1;
        Compound subject = parts.get(subjectIndex);
        if (subject.classes.contains(className)) return this;
        List<Compound> scoped = new ArrayList<>(parts);
        List<String> classes = new ArrayList<>(subject.classes);
        classes.add(className);
        scoped.set(subjectIndex, subject.withClasses(classes));
        return new Selector(scoped, source + "." + className);
    }

    public static Selector parse(String source) {
        String value = source.trim();
        if (value.isEmpty()) throw new UiStyleException("A selector cannot be empty.");
        List<Compound> compounds = new ArrayList<>();
        for (String part : value.split("\\s+")) compounds.add(parseCompound(part, value));
        return new Selector(compounds, value);
    }

    private static Compound parseCompound(String source, String whole) {
        UiTag tag = null;
        String id = null;
        boolean root = false;
        List<String> classes = new ArrayList<>();
        List<Pseudo> pseudos = new ArrayList<>();
        int cursor = 0;
        int delimiter = nextDelimiter(source, 0);
        String tagName = delimiter < 0 ? source : source.substring(0, delimiter);
        if (!tagName.isEmpty() && !tagName.equals("*")) {
            try {
                tag = UiTag.valueOf(tagName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new UiStyleException("Selector \"" + whole
                        + "\" uses unsupported tag \"" + tagName + "\".");
            }
        }
        cursor = delimiter < 0 ? source.length() : delimiter;
        while (cursor < source.length()) {
            char prefix = source.charAt(cursor);
            int next = nextDelimiter(source, cursor + 1);
            String name = source.substring(cursor + 1, next < 0 ? source.length() : next);
            if (name.isEmpty()) {
                throw new UiStyleException("Selector \"" + whole + "\" has an empty component.");
            }
            if (prefix == '.') classes.add(name);
            else if (prefix == '#') {
                if (id != null) throw new UiStyleException("Selector \"" + whole + "\" names two ids.");
                id = name;
            } else if (prefix == ':') {
                if (name.equals("root")) root = true;
                else pseudos.add(Pseudo.parse(name, whole));
            }
            cursor = next < 0 ? source.length() : next;
        }
        return new Compound(tag, id, classes, pseudos, root);
    }

    private static int nextDelimiter(String source, int from) {
        for (int index = from; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '.' || value == '#' || value == ':') return index;
        }
        return -1;
    }

    @Override
    public String toString() {
        return source;
    }

    private record Compound(UiTag tag, String id, List<String> classes,
                            List<Pseudo> pseudos, boolean root) {
        private Compound {
            classes = List.copyOf(classes);
            pseudos = List.copyOf(pseudos);
        }

        private Compound withClasses(List<String> replacement) {
            return new Compound(tag, id, replacement, pseudos, root);
        }

        private boolean matches(UiElement element) {
            if (root && element.parent() != null) return false;
            if (tag != null && element.tag() != tag) return false;
            if (id != null && !id.equals(element.id())) return false;
            for (String className : classes) {
                if (!element.hasClass(className)) return false;
            }
            for (Pseudo pseudo : pseudos) {
                if (!pseudo.matches(element)) return false;
            }
            return true;
        }
    }

    private enum Pseudo {
        HOVER,
        ACTIVE,
        FOCUS,
        FOCUS_VISIBLE,
        DISABLED;

        private boolean matches(UiElement element) {
            return switch (this) {
                case HOVER -> element.hovered();
                case ACTIVE -> element.armed();
                case FOCUS -> element.focused();
                case FOCUS_VISIBLE -> element.focusVisible();
                case DISABLED -> element.disabled();
            };
        }

        private static Pseudo parse(String name, String whole) {
            try {
                return valueOf(name.replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new UiStyleException("Selector \"" + whole
                        + "\" uses unsupported pseudo-class :" + name + ".");
            }
        }
    }
}
