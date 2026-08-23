package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    private UiElement parent;
    private UiTag tag = UiTag.DIV;
    private Integer tabIndex;
    private boolean disabled;
    private int canvasWidth = 300;
    private int canvasHeight = 150;

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
    private Consumer<UiPointerEvent> onPointerMove;
    private Consumer<UiPointerEvent> onPointerDown;
    private Consumer<UiPointerEvent> onPointerUp;

    private boolean hovered;
    private boolean armed;
    private boolean focused;
    private boolean focusVisible;

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

    public LayoutBox box() {
        return box;
    }

    public UiTag tag() {
        return tag;
    }

    public UiElement tag(UiTag tag) {
        this.tag = Objects.requireNonNull(tag, "tag");
        return this;
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
        this.disabled = disabled;
        return this;
    }

    public boolean disabled() {
        return disabled;
    }

    public boolean focusable() {
        return !disabled && (tag == UiTag.BUTTON || tabIndex != null);
    }

    boolean tabbable() {
        return focusable() && (tabIndex == null || tabIndex == 0);
    }

    public UiLayout layout() {
        return layout;
    }

    public UiElement layout(UiLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        return this;
    }

    public UiElement child(UiElement child) {
        Objects.requireNonNull(child, "child");
        if (child == this || child.isAncestorOf(this)) {
            throw new IllegalArgumentException("Adding " + child.id + " would create a cycle");
        }
        if (child.parent != null) child.parent.children.remove(child);
        child.parent = this;
        children.add(child);
        return this;
    }

    public UiElement remove(UiElement child) {
        Objects.requireNonNull(child, "child");
        if (child.parent != this) {
            throw new IllegalArgumentException(child.id + " is not a child of " + id);
        }
        children.remove(child);
        child.parent = null;
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
        return this;
    }

    public UiElement padding(Insets padding) {
        this.padding = Objects.requireNonNull(padding, "padding");
        return this;
    }

    public Insets padding() {
        return padding;
    }

    public UiElement gap(float gap) {
        this.gap = Math.max(0f, gap);
        return this;
    }

    public float gap() {
        return gap;
    }

    public UiElement preferredSize(float width, float height) {
        this.preferredWidth = width;
        this.preferredHeight = height;
        return this;
    }

    public UiElement preferredWidth(float width) {
        this.preferredWidth = width;
        return this;
    }

    public UiElement preferredHeight(float height) {
        this.preferredHeight = height;
        return this;
    }

    public float preferredWidth() {
        return preferredWidth;
    }

    public float preferredHeight() {
        return preferredHeight;
    }

    public UiElement grow(float grow) {
        this.grow = Math.max(0f, grow);
        return this;
    }

    public float grow() {
        return grow;
    }

    public UiElement align(UiAlign horizontal, UiAlign vertical) {
        this.horizontalAlign = Objects.requireNonNull(horizontal, "horizontal");
        this.verticalAlign = Objects.requireNonNull(vertical, "vertical");
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
        return this;
    }

    public Overflow overflow() {
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
        return this;
    }

    public Color background() {
        return background;
    }

    public UiElement hoverBackground(Color color) {
        this.hoverBackground = color;
        return this;
    }

    public UiElement armedBackground(Color color) {
        this.armedBackground = color;
        return this;
    }

    public Color paintedBackground() {
        if (armed && armedBackground != null) return armedBackground;
        if (hovered && hoverBackground != null) return hoverBackground;
        return background;
    }

    public UiElement border(float width, Color color) {
        this.borderWidth = Math.max(0f, width);
        this.borderColor = color;
        return this;
    }

    public float borderWidth() {
        return borderWidth;
    }

    public Color borderColor() {
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
        return this;
    }

    public UiElement text(String text) {
        this.text = text;
        return this;
    }

    public BitmapFont font() {
        return font;
    }

    public String text() {
        return text;
    }

    public Color textColor() {
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
        this.hovered = hovered;
    }

    public boolean armed() {
        return armed;
    }

    void armed(boolean armed) {
        this.armed = armed;
    }

    public boolean focused() {
        return focused;
    }

    void focused(boolean focused, boolean focusVisible) {
        this.focused = focused;
        this.focusVisible = focused && focusVisible;
    }

    public boolean focusVisible() {
        return focusVisible;
    }
}
