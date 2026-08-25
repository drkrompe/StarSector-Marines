package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.Insets;
import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiTextAlign;
import com.dillon.starsectormarines.ui.retained.UiWhiteSpace;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The typed, presented style consumed by layout and paint. */
public final class ComputedStyle {

    private final Map<StyleProperty, Object> values = new EnumMap<>(StyleProperty.class);

    public ComputedStyle() {
        values.put(StyleProperty.DISPLAY, UiDisplay.FLEX);
        values.put(StyleProperty.FLEX_DIRECTION, UiLayout.COLUMN);
        values.put(StyleProperty.WIDTH, Length.AUTO);
        values.put(StyleProperty.HEIGHT, Length.AUTO);
        values.put(StyleProperty.FLEX_GROW, 0f);
        values.put(StyleProperty.ROW_GAP, Length.ZERO);
        values.put(StyleProperty.COLUMN_GAP, Length.ZERO);
        values.put(StyleProperty.PADDING_TOP, Length.ZERO);
        values.put(StyleProperty.PADDING_RIGHT, Length.ZERO);
        values.put(StyleProperty.PADDING_BOTTOM, Length.ZERO);
        values.put(StyleProperty.PADDING_LEFT, Length.ZERO);
        values.put(StyleProperty.OVERFLOW, Overflow.VISIBLE);
        values.put(StyleProperty.BORDER_WIDTH, Length.ZERO);
        values.put(StyleProperty.BORDER_COLOR, null);
        values.put(StyleProperty.BACKGROUND_COLOR, null);
        values.put(StyleProperty.COLOR, null);
        values.put(StyleProperty.FONT_FAMILY, null);
        values.put(StyleProperty.TEXT_ALIGN, UiTextAlign.START);
        values.put(StyleProperty.WHITE_SPACE, UiWhiteSpace.NOWRAP);
        values.put(StyleProperty.OPACITY, 1f);
        values.put(StyleProperty.TRANSITION, List.of());
    }

    void inheritFrom(ComputedStyle parent) {
        if (parent == null) return;
        for (StyleProperty property : StyleProperty.values()) {
            if (property.inherited()) values.put(property, parent.value(property));
        }
    }

    void apply(StyleDeclaration declaration) {
        declaration.values().forEach(values::put);
    }

    public Object value(StyleProperty property) {
        return values.get(property);
    }

    void set(StyleProperty property, Object value) {
        values.put(property, value);
    }

    public UiLayout direction() {
        if (value(StyleProperty.DISPLAY) == UiDisplay.GRID) return UiLayout.GRID;
        return (UiLayout) value(StyleProperty.FLEX_DIRECTION);
    }

    public float width(float basis) {
        return ((Length) value(StyleProperty.WIDTH)).resolve(basis);
    }

    public float height(float basis) {
        return ((Length) value(StyleProperty.HEIGHT)).resolve(basis);
    }

    public float grow() {
        return (Float) value(StyleProperty.FLEX_GROW);
    }

    public float gap(float basis) {
        return direction() == UiLayout.ROW
                ? ((Length) value(StyleProperty.COLUMN_GAP)).resolve(basis)
                : ((Length) value(StyleProperty.ROW_GAP)).resolve(basis);
    }

    public float rowGap(float basis) {
        return ((Length) value(StyleProperty.ROW_GAP)).resolve(basis);
    }

    public float columnGap(float basis) {
        return ((Length) value(StyleProperty.COLUMN_GAP)).resolve(basis);
    }

    public Insets padding(float basis) {
        return new Insets(
                ((Length) value(StyleProperty.PADDING_TOP)).resolve(basis),
                ((Length) value(StyleProperty.PADDING_RIGHT)).resolve(basis),
                ((Length) value(StyleProperty.PADDING_BOTTOM)).resolve(basis),
                ((Length) value(StyleProperty.PADDING_LEFT)).resolve(basis));
    }

    public Overflow overflow() {
        return (Overflow) value(StyleProperty.OVERFLOW);
    }

    public float borderWidth() {
        return ((Length) value(StyleProperty.BORDER_WIDTH)).resolve(0f);
    }

    public Color borderColor() {
        return (Color) value(StyleProperty.BORDER_COLOR);
    }

    public Color backgroundColor() {
        return (Color) value(StyleProperty.BACKGROUND_COLOR);
    }

    public Color color() {
        return (Color) value(StyleProperty.COLOR);
    }

    public Object fontFamily() {
        return value(StyleProperty.FONT_FAMILY);
    }

    public UiTextAlign textAlign() {
        return (UiTextAlign) value(StyleProperty.TEXT_ALIGN);
    }

    public UiWhiteSpace whiteSpace() {
        return (UiWhiteSpace) value(StyleProperty.WHITE_SPACE);
    }

    public float opacity() {
        return (Float) value(StyleProperty.OPACITY);
    }

    @SuppressWarnings("unchecked")
    public List<TransitionSpec> transitions() {
        return (List<TransitionSpec>) value(StyleProperty.TRANSITION);
    }

    public boolean differsForLayout(ComputedStyle other) {
        return differs(other, true);
    }

    public boolean differsForPaint(ComputedStyle other) {
        return differs(other, false);
    }

    private boolean differs(ComputedStyle other, boolean layout) {
        for (StyleProperty property : StyleProperty.values()) {
            boolean relevant = layout ? property.affectsLayout() : property.affectsPaint();
            if (relevant && !Objects.equals(value(property), other.value(property))) return true;
        }
        return false;
    }
}
