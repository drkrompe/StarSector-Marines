package com.dillon.starsectormarines.ui.retained.style;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Closed CSS-named property surface supported by the Marine Ops retained tree.
 * Unsupported names fail at authoring time rather than becoming silent no-ops.
 */
public enum StyleProperty {
    FLEX_DIRECTION("flex-direction", false, true, false, false),
    WIDTH("width", false, true, false, true),
    HEIGHT("height", false, true, false, true),
    FLEX_GROW("flex-grow", false, true, false, true),
    ROW_GAP("row-gap", false, true, false, true),
    COLUMN_GAP("column-gap", false, true, false, true),
    GAP("gap", false, false, false, false),
    PADDING_TOP("padding-top", false, true, false, true),
    PADDING_RIGHT("padding-right", false, true, false, true),
    PADDING_BOTTOM("padding-bottom", false, true, false, true),
    PADDING_LEFT("padding-left", false, true, false, true),
    PADDING("padding", false, false, false, false),
    OVERFLOW("overflow", false, true, true, false),
    BORDER_WIDTH("border-width", false, true, true, true),
    BORDER_COLOR("border-color", false, false, true, true),
    BACKGROUND_COLOR("background-color", false, false, true, true),
    COLOR("color", true, false, true, true),
    FONT_FAMILY("font-family", true, true, true, false),
    TEXT_ALIGN("text-align", true, false, true, false),
    OPACITY("opacity", false, false, true, true),
    TRANSITION("transition", false, false, false, false);

    private static final Map<String, StyleProperty> BY_NAME = buildLookup();

    private final String cssName;
    private final boolean inherited;
    private final boolean affectsLayout;
    private final boolean affectsPaint;
    private final boolean animatable;

    StyleProperty(String cssName, boolean inherited, boolean affectsLayout,
                  boolean affectsPaint, boolean animatable) {
        this.cssName = cssName;
        this.inherited = inherited;
        this.affectsLayout = affectsLayout;
        this.affectsPaint = affectsPaint;
        this.animatable = animatable;
    }

    public String cssName() {
        return cssName;
    }

    public boolean inherited() {
        return inherited;
    }

    public boolean affectsLayout() {
        return affectsLayout;
    }

    public boolean affectsPaint() {
        return affectsPaint;
    }

    public boolean animatable() {
        return animatable;
    }

    public static StyleProperty parse(String cssName) {
        StyleProperty property = BY_NAME.get(cssName.toLowerCase(Locale.ROOT));
        if (property == null) {
            throw new UiStyleException("Unsupported style property \"" + cssName
                    + "\". The retained CSS property surface is closed.");
        }
        return property;
    }

    private static Map<String, StyleProperty> buildLookup() {
        Map<String, StyleProperty> values = new HashMap<>();
        for (StyleProperty property : values()) values.put(property.cssName, property);
        return Map.copyOf(values);
    }
}
