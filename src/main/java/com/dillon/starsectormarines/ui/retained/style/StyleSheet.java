package com.dillon.starsectormarines.ui.retained.style;

import java.util.ArrayList;
import java.util.List;

/** An ordered CSS rule set. Later rules and later sheets win. */
public record StyleSheet(String name, List<StyleRule> rules) {

    public StyleSheet {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Sheet name must not be blank");
        rules = List.copyOf(rules);
    }

    public static StyleSheet parse(String name, String source) {
        String text = stripComments(source);
        List<StyleRule> rules = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int open = text.indexOf('{', cursor);
            if (open < 0) {
                if (!text.substring(cursor).trim().isEmpty()) {
                    throw new UiStyleException("Sheet \"" + name + "\" has text outside a rule.");
                }
                break;
            }
            int close = text.indexOf('}', open + 1);
            if (close < 0) throw new UiStyleException("Sheet \"" + name + "\" has an unclosed rule.");
            String selectorSource = text.substring(cursor, open).trim();
            if (selectorSource.startsWith("@")) {
                throw new UiStyleException("At-rules are not supported: " + selectorSource);
            }
            List<Selector> selectors = new ArrayList<>();
            for (String selector : selectorSource.split(",")) selectors.add(Selector.parse(selector));
            StyleDeclaration declaration = StyleDeclaration.parse(text.substring(open + 1, close));
            rules.add(new StyleRule(selectors, declaration));
            cursor = close + 1;
        }
        return new StyleSheet(name, rules);
    }

    public StyleSheet scopedTo(String className) {
        List<StyleRule> scoped = new ArrayList<>();
        for (StyleRule rule : rules) scoped.add(rule.scopedTo(className));
        return new StyleSheet(name, scoped);
    }

    private static String stripComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < source.length()) {
            int start = source.indexOf("/*", cursor);
            if (start < 0) {
                result.append(source, cursor, source.length());
                break;
            }
            result.append(source, cursor, start);
            int end = source.indexOf("*/", start + 2);
            if (end < 0) throw new UiStyleException("Unclosed CSS comment.");
            cursor = end + 2;
        }
        return result.toString();
    }
}
