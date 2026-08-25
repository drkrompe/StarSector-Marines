package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.style.ComputedStyle;
import com.dillon.starsectormarines.ui.retained.style.Length;
import com.dillon.starsectormarines.ui.retained.style.StyleDeclaration;
import com.dillon.starsectormarines.ui.retained.style.StyleProperty;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One stable node in a retained UI document.
 *
 * <p>U1 intentionally keeps construction in Java and presentation properties
 * directly on the element. Later theme and markup stories project onto this
 * same tree rather than introducing a privileged second element model.
 */
public final class UiElement {

    private final String id;
    private final List<UiElement> children = new ArrayList<>();
    private final LayoutBox box = new LayoutBox();
    private final Set<String> classes = new LinkedHashSet<>();
    private final StyleDeclaration authoredStyle = StyleDeclaration.empty();
    private StyleDeclaration inlineStyle = StyleDeclaration.empty();

    private UiElement parent;
    private String key;
    private UiTag tag = UiTag.DIV;
    private Integer tabIndex;
    private boolean disabled;
    private int canvasWidth = 300;
    private int canvasHeight = 150;
    private int inputMaxLength = 64;

    private UiLayout layout = UiLayout.COLUMN;
    private Insets padding = Insets.ZERO;
    private float gap;
    private float preferredWidth = Float.NaN;
    private float preferredHeight = Float.NaN;
    private float grow;
    private UiAlign horizontalAlign = UiAlign.STRETCH;
    private UiAlign verticalAlign = UiAlign.STRETCH;
    private Overflow overflow = Overflow.VISIBLE;
    private float borderWidth;
    private float scrollTop;
    private float focusOutlineWidth;

    private Color background;
    private Color hoverBackground;
    private Color armedBackground;
    private Color borderColor;
    private Color focusOutlineColor;
    private BitmapFont font;
    private String text;
    private Color textColor;
    private Runnable onClick;
    private Consumer<String> onInput;
    private Consumer<UiPointerEvent> onPointerMove;
    private Consumer<UiPointerEvent> onPointerDown;
    private Consumer<UiPointerEvent> onPointerUp;

    private boolean hovered;
    private boolean armed;
    private boolean focused;
    private boolean focusVisible;
    private ComputedStyle computedStyle;
    private long styleRevision;
    private boolean styleDirty = true;
    private boolean descendantStyleDirty;
    private boolean layoutDirty = true;
    private boolean descendantLayoutDirty;

    public UiElement(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() {
        return id;
    }

    public List<UiElement> children() {
        return Collections.unmodifiableList(children);
    }

    public UiElement parent() {
        return parent;
    }

    /** Stable reconciliation identity when this element belongs to a bound child list. */
    public String key() {
        return key;
    }

    public UiElement key(String key) {
        this.key = key;
        return this;
    }

    public int childCount() {
        return children.size();
    }

    public UiElement childAt(int index) {
        return children.get(index);
    }

    public LayoutBox box() {
        return box;
    }

    public UiTag tag() {
        return tag;
    }

    public UiElement tag(UiTag tag) {
        UiTag next = Objects.requireNonNull(tag, "tag");
        if (this.tag == next) return this;
        this.tag = next;
        touchStyle();
        touchLayout();
        return this;
    }

    public UiElement addClass(String className) {
        requireClassName(className);
        if (classes.add(className)) touchStyle();
        return this;
    }

    public UiElement removeClass(String className) {
        if (classes.remove(className)) touchStyle();
        return this;
    }

    public UiElement classed(String className, boolean present) {
        return present ? addClass(className) : removeClass(className);
    }

    public boolean hasClass(String className) {
        return classes.contains(className);
    }

    public Set<String> classes() {
        return Collections.unmodifiableSet(classes);
    }

    public UiElement selected(boolean selected) {
        return classed("selected", selected);
    }

    public boolean selected() {
        return hasClass("selected");
    }

    public UiElement style(String declaration) {
        inlineStyle = StyleDeclaration.parse(declaration);
        touchStyle();
        return this;
    }

    private static void requireClassName(String className) {
        if (className == null || className.isBlank() || className.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Class name must be one non-blank token");
        }
    }

    /** Sets the independent HTML canvas drawing-surface size. */
    public UiElement canvasSize(int width, int height) {
        if (tag != UiTag.CANVAS) {
            throw new IllegalStateException("Only a canvas has a drawing surface");
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Canvas surface size cannot be negative");
        }
        canvasWidth = width;
        canvasHeight = height;
        touchLayout();
        return this;
    }

    public int canvasWidth() {
        requireCanvas();
        return canvasWidth;
    }

    public int canvasHeight() {
        requireCanvas();
        return canvasHeight;
    }

    private void requireCanvas() {
        if (tag != UiTag.CANVAS) {
            throw new IllegalStateException(id + " is not a canvas");
        }
    }

    public UiElement tabIndex(int tabIndex) {
        if (tabIndex != -1 && tabIndex != 0) {
            throw new IllegalArgumentException("tabIndex supports only -1 or 0");
        }
        this.tabIndex = tabIndex;
        return this;
    }

    public Integer tabIndex() {
        return tabIndex;
    }

    public UiElement disabled(boolean disabled) {
        if (this.disabled == disabled) return this;
        this.disabled = disabled;
        touchStyle();
        return this;
    }

    public boolean disabled() {
        return disabled;
    }

    public boolean focusable() {
        return !disabled && (tag == UiTag.BUTTON || tag == UiTag.INPUT || tabIndex != null);
    }

    boolean tabbable() {
        return focusable() && (tabIndex == null || tabIndex == 0);
    }

    public UiLayout layout() {
        if (layout != UiLayout.STACK && computedStyle != null) return computedStyle.direction();
        return layout;
    }

    public UiElement layout(UiLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        if (layout == UiLayout.STACK) {
            authoredStyle.remove(StyleProperty.FLEX_DIRECTION);
        } else {
            authoredStyle.put(StyleProperty.FLEX_DIRECTION, layout);
        }
        touchStyle();
        touchLayout();
        return this;
    }

    public UiElement child(UiElement child) {
        return insert(children.size(), child);
    }

    /** Inserts or moves a child while preserving the child's retained identity. */
    public UiElement insert(int index, UiElement child) {
        Objects.requireNonNull(child, "child");
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException("Child index " + index + " for " + children.size());
        }
        if (child == this || child.isAncestorOf(this)) {
            throw new IllegalArgumentException("Adding " + child.id + " would create a cycle");
        }
        if (child.parent != null) {
            UiElement previousParent = child.parent;
            previousParent.children.remove(child);
            previousParent.touchStyle();
            previousParent.touchLayout();
        }
        child.parent = this;
        children.add(Math.min(index, children.size()), child);
        child.touchStyle();
        touchLayout();
        return this;
    }

    public UiElement remove(UiElement child) {
        Objects.requireNonNull(child, "child");
        if (child.parent != this) {
            throw new IllegalArgumentException(child.id + " is not a child of " + id);
        }
        children.remove(child);
        child.parent = null;
        touchStyle();
        child.touchStyle();
        touchLayout();
        return this;
    }

    private boolean isAncestorOf(UiElement other) {
        for (UiElement candidate = other.parent; candidate != null; candidate = candidate.parent) {
            if (candidate == this) return true;
        }
        return false;
    }

    public UiElement padding(float all) {
        this.padding = Insets.uniform(all);
        putPadding(this.padding);
        return this;
    }

    public UiElement padding(Insets padding) {
        this.padding = Objects.requireNonNull(padding, "padding");
        putPadding(this.padding);
        return this;
    }

    public Insets padding() {
        if (computedStyle != null) return computedStyle.padding(box.borderBox().width());
        return padding;
    }

    Insets resolvedPadding(float basis) {
        return computedStyle == null ? padding : computedStyle.padding(basis);
    }

    public UiElement gap(float gap) {
        this.gap = Math.max(0f, gap);
        authoredStyle.put(StyleProperty.ROW_GAP, Length.px(this.gap));
        authoredStyle.put(StyleProperty.COLUMN_GAP, Length.px(this.gap));
        touchStyle();
        return this;
    }

    public float gap() {
        if (computedStyle != null) return computedStyle.gap(box.contentBox().width());
        return gap;
    }

    float resolvedGap(float basis) {
        return computedStyle == null ? gap : computedStyle.gap(basis);
    }

    float resolvedRowGap(float basis) {
        return computedStyle == null ? gap : computedStyle.rowGap(basis);
    }

    float resolvedColumnGap(float basis) {
        return computedStyle == null ? gap : computedStyle.columnGap(basis);
    }

    public UiElement preferredSize(float width, float height) {
        this.preferredWidth = width;
        this.preferredHeight = height;
        touchLayout();
        return this;
    }

    public UiElement preferredWidth(float width) {
        this.preferredWidth = width;
        touchLayout();
        return this;
    }

    public UiElement preferredHeight(float height) {
        this.preferredHeight = height;
        touchLayout();
        return this;
    }

    public float preferredWidth() {
        return resolvedPreferredWidth(box.borderBox().width());
    }

    float resolvedPreferredWidth(float basis) {
        if (computedStyle == null) return preferredWidth;
        float contentWidth = computedStyle.width(basis);
        if (Float.isNaN(contentWidth)) return preferredWidth;
        return contentWidth + computedStyle.padding(basis).horizontal()
                + computedStyle.borderWidth() * 2f;
    }

    public float preferredHeight() {
        return resolvedPreferredHeight(box.borderBox().height(), box.borderBox().width());
    }

    float resolvedPreferredHeight(float heightBasis, float widthBasis) {
        if (computedStyle == null) return preferredHeight;
        float contentHeight = computedStyle.height(heightBasis);
        if (Float.isNaN(contentHeight)) return preferredHeight;
        return contentHeight + computedStyle.padding(widthBasis).vertical()
                + computedStyle.borderWidth() * 2f;
    }

    public UiElement grow(float grow) {
        this.grow = Math.max(0f, grow);
        authoredStyle.put(StyleProperty.FLEX_GROW, this.grow);
        touchStyle();
        return this;
    }

    public float grow() {
        if (computedStyle != null) return computedStyle.grow();
        return grow;
    }

    public UiElement align(UiAlign horizontal, UiAlign vertical) {
        this.horizontalAlign = Objects.requireNonNull(horizontal, "horizontal");
        this.verticalAlign = Objects.requireNonNull(vertical, "vertical");
        touchLayout();
        return this;
    }

    public UiAlign horizontalAlign() {
        return horizontalAlign;
    }

    public UiAlign verticalAlign() {
        return verticalAlign;
    }

    public UiElement overflow(Overflow overflow) {
        this.overflow = Objects.requireNonNull(overflow, "overflow");
        authoredStyle.put(StyleProperty.OVERFLOW, overflow);
        touchStyle();
        return this;
    }

    public Overflow overflow() {
        if (computedStyle != null) return computedStyle.overflow();
        return overflow;
    }

    /** How far this element's content is scrolled up, in document pixels. */
    public float scrollTop() {
        return scrollTop;
    }

    /**
     * Stores a non-negative scroll offset. Layout owns the bottom clamp because
     * it alone knows the current content extent.
     */
    public UiElement scrollTop(float value) {
        scrollTop = Math.max(0f, value);
        return this;
    }

    public UiElement background(Color color) {
        this.background = color;
        authoredStyle.put(StyleProperty.BACKGROUND_COLOR, color);
        touchStyle();
        return this;
    }

    public Color background() {
        if (computedStyle != null) return computedStyle.backgroundColor();
        return background;
    }

    public UiElement hoverBackground(Color color) {
        this.hoverBackground = color;
        touchStyle();
        return this;
    }

    public UiElement armedBackground(Color color) {
        this.armedBackground = color;
        touchStyle();
        return this;
    }

    public Color paintedBackground() {
        if (computedStyle != null) return computedStyle.backgroundColor();
        if (armed && armedBackground != null) return armedBackground;
        if (hovered && hoverBackground != null) return hoverBackground;
        return background;
    }

    public UiElement border(float width, Color color) {
        this.borderWidth = Math.max(0f, width);
        this.borderColor = color;
        authoredStyle.put(StyleProperty.BORDER_WIDTH, Length.px(this.borderWidth));
        authoredStyle.put(StyleProperty.BORDER_COLOR, color);
        touchStyle();
        return this;
    }

    public float borderWidth() {
        if (computedStyle != null) return computedStyle.borderWidth();
        return borderWidth;
    }

    public Color borderColor() {
        if (computedStyle != null) return computedStyle.borderColor();
        return borderColor;
    }

    public UiElement focusOutline(float width, Color color) {
        focusOutlineWidth = Math.max(0f, width);
        focusOutlineColor = color;
        return this;
    }

    public float focusOutlineWidth() {
        return focusOutlineWidth;
    }

    public Color focusOutlineColor() {
        return focusOutlineColor;
    }

    public UiElement text(BitmapFont font, String text, Color color) {
        this.font = font;
        this.text = text;
        this.textColor = color;
        authoredStyle.put(StyleProperty.FONT_FAMILY, font);
        authoredStyle.put(StyleProperty.COLOR, color);
        touchStyle();
        touchLayout();
        return this;
    }

    public UiElement text(String text) {
        if (Objects.equals(this.text, text)) return this;
        this.text = text;
        touchLayout();
        return this;
    }

    public UiElement inputMaxLength(int value) {
        if (value <= 0) throw new IllegalArgumentException("Input max length must be positive");
        inputMaxLength = value;
        if (text != null && text.length() > inputMaxLength) text(text.substring(0, inputMaxLength));
        return this;
    }

    public int inputMaxLength() {
        return inputMaxLength;
    }

    public UiElement onInput(Consumer<String> handler) {
        onInput = handler;
        return this;
    }

    boolean editInput(String value) {
        if (tag != UiTag.INPUT || disabled) return false;
        String next = value == null ? "" : value;
        if (next.length() > inputMaxLength) next = next.substring(0, inputMaxLength);
        text(next);
        if (onInput != null) onInput.accept(next);
        return true;
    }

    public BitmapFont font() {
        return font;
    }

    public String text() {
        return text;
    }

    public UiTextAlign textAlign() {
        return computedStyle == null ? UiTextAlign.START : computedStyle.textAlign();
    }

    public UiWhiteSpace whiteSpace() {
        return computedStyle == null ? UiWhiteSpace.NOWRAP : computedStyle.whiteSpace();
    }

    public Color textColor() {
        if (computedStyle != null) return computedStyle.color();
        return textColor;
    }

    public UiElement onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public boolean clickable() {
        return onClick != null && !disabled;
    }

    void click() {
        if (clickable()) onClick.run();
    }

    public UiElement onPointerMove(Consumer<UiPointerEvent> handler) {
        onPointerMove = handler;
        return this;
    }

    public UiElement onPointerDown(Consumer<UiPointerEvent> handler) {
        onPointerDown = handler;
        return this;
    }

    public UiElement onPointerUp(Consumer<UiPointerEvent> handler) {
        onPointerUp = handler;
        return this;
    }

    void pointerMoved(UiPointerEvent event) {
        if (onPointerMove != null) onPointerMove.accept(event);
    }

    void pointerDown(UiPointerEvent event) {
        if (onPointerDown != null) onPointerDown.accept(event);
    }

    void pointerUp(UiPointerEvent event) {
        if (onPointerUp != null) onPointerUp.accept(event);
    }

    public boolean hovered() {
        return hovered;
    }

    void hovered(boolean hovered) {
        if (this.hovered == hovered) return;
        this.hovered = hovered;
        touchStyle();
    }

    public boolean armed() {
        return armed;
    }

    void armed(boolean armed) {
        if (this.armed == armed) return;
        this.armed = armed;
        touchStyle();
    }

    public boolean focused() {
        return focused;
    }

    void focused(boolean focused, boolean focusVisible) {
        boolean nextVisible = focused && focusVisible;
        if (this.focused == focused && this.focusVisible == nextVisible) return;
        this.focused = focused;
        this.focusVisible = nextVisible;
        touchStyle();
    }

    public boolean focusVisible() {
        return focusVisible;
    }

    public float opacity() {
        return computedStyle == null ? 1f : computedStyle.opacity();
    }

    public StyleDeclaration authoredStyle() {
        StyleDeclaration combined = authoredStyle.copy();
        inlineStyle.values().forEach(combined::put);
        return combined;
    }

    public long styleRevision() {
        return styleRevision;
    }

    public boolean styleDirty() {
        return styleDirty;
    }

    public boolean descendantStyleDirty() {
        return descendantStyleDirty;
    }

    boolean layoutDirty() {
        return layoutDirty;
    }

    boolean descendantLayoutDirty() {
        return descendantLayoutDirty;
    }

    void clearLayoutDirty() {
        layoutDirty = false;
    }

    void clearDescendantLayoutDirty() {
        descendantLayoutDirty = false;
    }

    public void clearStyleDirty() {
        styleDirty = false;
    }

    public void clearDescendantStyleDirty() {
        descendantStyleDirty = false;
    }

    /** Marks this element for a new cascade pass. Used by inherited transition propagation. */
    public void invalidateStyle() {
        touchStyle();
    }

    public void computedStyle(ComputedStyle style) {
        computedStyle = style;
    }

    public Color hoverBackgroundOverride() {
        return hoverBackground;
    }

    public Color armedBackgroundOverride() {
        return armedBackground;
    }

    private void putPadding(Insets value) {
        authoredStyle.put(StyleProperty.PADDING_TOP, Length.px(value.top()));
        authoredStyle.put(StyleProperty.PADDING_RIGHT, Length.px(value.right()));
        authoredStyle.put(StyleProperty.PADDING_BOTTOM, Length.px(value.bottom()));
        authoredStyle.put(StyleProperty.PADDING_LEFT, Length.px(value.left()));
        touchStyle();
    }

    private void touchStyle() {
        styleRevision++;
        styleDirty = true;
        for (UiElement ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor.descendantStyleDirty) break;
            ancestor.descendantStyleDirty = true;
        }
    }

    private void touchLayout() {
        layoutDirty = true;
        for (UiElement ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor.descendantLayoutDirty) break;
            ancestor.descendantLayoutDirty = true;
        }
    }
}
