package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.UiLayout;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One CSS declaration block, stored as typed longhand values. */
public final class StyleDeclaration {

    private final Map<StyleProperty, Object> values = new EnumMap<>(StyleProperty.class);

    public static StyleDeclaration empty() {
        return new StyleDeclaration();
    }

    public static StyleDeclaration parse(String source) {
        StyleDeclaration result = new StyleDeclaration();
        for (String rawEntry : source.split(";")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue;
            if (entry.contains("!important")) {
                throw new UiStyleException("!important is not supported; order the rules explicitly.");
            }
            int colon = entry.indexOf(':');
            if (colon < 0) {
                throw new UiStyleException("Expected property: value, got \"" + entry + "\".");
            }
            result.set(entry.substring(0, colon).trim(), entry.substring(colon + 1).trim());
        }
        return result;
    }

    public StyleDeclaration set(String cssName, String value) {
        StyleProperty property = StyleProperty.parse(cssName);
        if (property == StyleProperty.PADDING) {
            StyleEdges edges = StyleValues.parsePadding(value);
            values.put(StyleProperty.PADDING_TOP, edges.top());
            values.put(StyleProperty.PADDING_RIGHT, edges.right());
            values.put(StyleProperty.PADDING_BOTTOM, edges.bottom());
            values.put(StyleProperty.PADDING_LEFT, edges.left());
        } else if (property == StyleProperty.GAP) {
            Length gap = StyleValues.parseGap(value);
            values.put(StyleProperty.ROW_GAP, gap);
            values.put(StyleProperty.COLUMN_GAP, gap);
        } else {
            values.put(property, StyleValues.parse(property, value));
        }
        return this;
    }

    public StyleDeclaration put(StyleProperty property, Object value) {
        if (!accepts(property, value)) {
            throw new IllegalArgumentException("Wrong typed value for " + property.cssName()
                    + ": " + value);
        }
        values.put(property, value);
        return this;
    }

    private static boolean accepts(StyleProperty property, Object value) {
        return switch (property) {
            case FLEX_DIRECTION -> value instanceof UiLayout;
            case WIDTH, HEIGHT, ROW_GAP, COLUMN_GAP,
                 PADDING_TOP, PADDING_RIGHT, PADDING_BOTTOM, PADDING_LEFT,
                 BORDER_WIDTH -> value instanceof Length;
            case FLEX_GROW, OPACITY -> value instanceof Float;
            case OVERFLOW -> value instanceof Overflow;
            case BORDER_COLOR, BACKGROUND_COLOR, COLOR -> value == null || value instanceof Color;
            case FONT_FAMILY -> value == null || value instanceof String || value instanceof BitmapFont;
            case TRANSITION -> value instanceof List<?>;
            case GAP, PADDING -> false;
        };
    }

    public Object value(StyleProperty property) {
        return values.get(property);
    }

    public Map<StyleProperty, Object> values() {
        return Collections.unmodifiableMap(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public StyleDeclaration copy() {
        StyleDeclaration copy = new StyleDeclaration();
        copy.values.putAll(values);
        return copy;
    }
}
