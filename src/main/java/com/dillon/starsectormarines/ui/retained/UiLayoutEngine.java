package com.dillon.starsectormarines.ui.retained;

import java.util.ArrayList;
import java.util.List;

/**
 * Retained layout: row, column, responsive grid, and stack with explicit
 * preferred sizes and flexible growth.
 *
 * <p>This is deliberately smaller than MoonLightEngine's CSS layout engine,
 * but preserves its most important boundary: the engine writes one retained
 * {@link LayoutBox} per element and every later phase reads those boxes.
 */
public final class UiLayoutEngine {

    private final UiTextMeasurer text;

    UiLayoutEngine(UiTextMeasurer text) {
        this.text = text;
    }

    /**
     * The clip rectangle an element's children inherit.
     *
     * <p>Copied from MoonLightEngine's retained layout contract and shared by
     * paint and hit-testing on purpose. CSS clips overflow to the padding box,
     * so the element's own border paints under the inherited clip while its
     * content and descendants receive the intersected one.
     */
    public static Rect clipForChildren(Overflow overflow, LayoutBox box, Rect inherited) {
        return overflow.clips() ? inherited.intersect(box.paddingBox()) : inherited;
    }

    public void layout(UiElement root, float width, float height) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        float availableWidth = Math.max(0f, width);
        arrange(root, new Rect(0f, 0f, availableWidth, Math.max(0f, height)), availableWidth);
    }

    private void arrange(UiElement element, Rect rect, float containingBlockWidth) {
        element.box().place(rect, element.resolvedPadding(containingBlockWidth), element.borderWidth());
        element.clearLayoutDirty();
        element.clearDescendantLayoutDirty();
        Rect content = element.box().contentBox();
        List<UiElement> allChildren = element.children();
        element.box().scrollHeight(0f);
        if (allChildren.isEmpty()) return;

        List<UiElement> children = inFlow(allChildren);
        arrangePositioned(allChildren, content);
        if (children.isEmpty()) return;

        if (element.layout() == UiLayout.STACK) {
            arrangeStack(children, content);
        } else if (element.layout() == UiLayout.GRID) {
            arrangeGrid(element, children, content);
        } else {
            arrangeFlow(element, children, content, element.layout() == UiLayout.ROW);
        }

        if (element.overflow().clips()) {
            float contentBottom = content.y();
            for (UiElement child : children) {
                contentBottom = Math.max(contentBottom, child.box().borderBox().bottom());
            }
            element.box().scrollHeight(contentBottom - content.y());
            float scrollTop = Math.min(element.scrollTop(), element.box().maxScrollTop());
            if (scrollTop > 0f) {
                for (UiElement child : children) translate(child, 0f, -scrollTop);
            }
        }
    }

    /**
     * The children row, column, grid, and stack distribute between them. An
     * absolutely positioned child is out of flow, so it neither consumes main
     * axis space nor shifts its siblings.
     */
    private static List<UiElement> inFlow(List<UiElement> children) {
        for (int index = 0; index < children.size(); index++) {
            if (children.get(index).position() != UiPosition.ABSOLUTE) continue;
            List<UiElement> flow = new ArrayList<>(children.size() - 1);
            for (UiElement child : children) {
                if (child.position() != UiPosition.ABSOLUTE) flow.add(child);
            }
            return flow;
        }
        return children;
    }

    /**
     * Places every absolutely positioned child against its parent's content
     * box. An {@code auto} offset is zero and an {@code auto} size fills the
     * remaining content extent, so a box that declares neither still lands
     * somewhere bounded rather than at an undefined origin.
     */
    private void arrangePositioned(List<UiElement> children, Rect content) {
        for (UiElement child : children) {
            if (child.position() != UiPosition.ABSOLUTE) continue;
            float left = orZero(child.resolvedLeft(content.width()));
            float top = orZero(child.resolvedTop(content.height()));
            float width = orRemaining(
                    preferredAxis(child, true, content.width(), content.height()),
                    content.width() - left);
            float height = orRemaining(
                    preferredAxis(child, false, content.width(), content.height()),
                    content.height() - top);
            arrange(child, new Rect(content.x() + left, content.y() + top,
                    Math.max(0f, width), Math.max(0f, height)), content.width());
        }
    }

    private static float orZero(float value) {
        return Float.isNaN(value) ? 0f : value;
    }

    private static float orRemaining(float preferred, float remaining) {
        return Float.isNaN(preferred) ? remaining : preferred;
    }

    private static void translate(UiElement element, float deltaX, float deltaY) {
        element.box().translate(deltaX, deltaY);
        for (UiElement child : element.children()) translate(child, deltaX, deltaY);
    }

    private void arrangeFlow(UiElement parent, List<UiElement> children,
                             Rect content, boolean horizontal) {
        float mainExtent = horizontal ? content.width() : content.height();
        float crossExtent = horizontal ? content.height() : content.width();
        float gap = parent.resolvedGap(content.width());
        float totalGap = gap * Math.max(0, children.size() - 1);
        float totalBasis = 0f;
        float totalGrow = 0f;

        for (UiElement child : children) {
            totalBasis += preferredMain(child, horizontal, content.width(), content.height());
            totalGrow += child.grow();
        }

        float free = Math.max(0f, mainExtent - totalGap - totalBasis);
        float cursor = horizontal ? content.x() : content.y();
        for (UiElement child : children) {
            float main = preferredMain(child, horizontal, content.width(), content.height());
            if (child.grow() > 0f && totalGrow > 0f) {
                main += free * child.grow() / totalGrow;
            }

            float preferredCross = preferredCross(child, horizontal, content.width(), content.height());
            UiAlign crossAlign = horizontal
                    ? child.verticalAlign() : child.horizontalAlign();
            float cross = Float.isNaN(preferredCross) || crossAlign == UiAlign.STRETCH
                    ? crossExtent : Math.min(crossExtent, Math.max(0f, preferredCross));
            float crossOrigin = crossOrigin(horizontal ? content.y() : content.x(),
                    crossExtent, cross, crossAlign);

            Rect childRect = horizontal
                    ? new Rect(cursor, crossOrigin, main, cross)
                    : new Rect(crossOrigin, cursor, cross, main);
            arrange(child, childRect, content.width());
            cursor += main + gap;
        }
    }

    /**
     * Auto-filling fixed-item grid. The widest preferred child defines the
     * column width; columns wrap responsively as the content box changes.
     * Rows use their tallest child's preferred height and participate in the
     * existing vertical overflow/scroll contract.
     */
    private void arrangeGrid(UiElement parent, List<UiElement> children, Rect content) {
        float columnGap = parent.resolvedColumnGap(content.width());
        float rowGap = parent.resolvedRowGap(content.width());
        float columnWidth = 0f;
        for (UiElement child : children) {
            columnWidth = Math.max(columnWidth,
                    preferredMain(child, true, content.width(), content.height()));
        }
        if (columnWidth <= 0f) columnWidth = content.width();
        columnWidth = Math.min(content.width(), columnWidth);

        int columns = Math.max(1, (int) Math.floor(
                (content.width() + columnGap) / (columnWidth + columnGap)));
        int rows = (children.size() + columns - 1) / columns;
        float[] rowHeights = new float[rows];
        for (int index = 0; index < children.size(); index++) {
            float height = preferredMain(children.get(index), false,
                    content.width(), content.height());
            rowHeights[index / columns] = Math.max(rowHeights[index / columns], height);
        }

        float[] rowOrigins = new float[rows];
        float y = content.y();
        for (int row = 0; row < rows; row++) {
            rowOrigins[row] = y;
            y += rowHeights[row] + rowGap;
        }

        for (int index = 0; index < children.size(); index++) {
            UiElement child = children.get(index);
            int column = index % columns;
            int row = index / columns;
            float preferredWidth = preferredMain(child, true,
                    content.width(), content.height());
            float width = preferredWidth > 0f
                    ? Math.min(columnWidth, preferredWidth) : columnWidth;
            float preferredHeight = preferredMain(child, false,
                    content.width(), content.height());
            float height = preferredHeight > 0f ? preferredHeight : rowHeights[row];
            arrange(child, new Rect(
                    content.x() + column * (columnWidth + columnGap),
                    rowOrigins[row], width, height), content.width());
        }
    }

    private void arrangeStack(List<UiElement> children, Rect content) {
        for (UiElement child : children) {
            float width = resolveStackExtent(preferredAxis(child, true,
                    content.width(), content.height()), content.width(),
                    child.horizontalAlign());
            float height = resolveStackExtent(preferredAxis(child, false,
                    content.width(), content.height()), content.height(),
                    child.verticalAlign());
            float x = crossOrigin(content.x(), content.width(), width,
                    child.horizontalAlign());
            float y = crossOrigin(content.y(), content.height(), height,
                    child.verticalAlign());
            arrange(child, new Rect(x, y, width, height), content.width());
        }
    }

    private float preferredMain(UiElement element, boolean horizontal,
                                float widthBasis, float heightBasis) {
        float preferred = preferredAxis(element, horizontal, widthBasis, heightBasis);
        return Float.isNaN(preferred) ? 0f : Math.max(0f, preferred);
    }

    private float preferredCross(UiElement element, boolean horizontal,
                                 float widthBasis, float heightBasis) {
        return preferredAxis(element, !horizontal, widthBasis, heightBasis);
    }

    private float preferredAxis(UiElement element, boolean horizontal,
                                float widthBasis, float heightBasis) {
        float preferred = horizontal ? element.resolvedPreferredWidth(widthBasis)
                : element.resolvedPreferredHeight(heightBasis, widthBasis);
        if (!Float.isNaN(preferred)) return preferred;
        if (element.tag() == UiTag.CANVAS) {
            return horizontal ? element.canvasWidth() : element.canvasHeight();
        }
        Insets padding = element.resolvedPadding(widthBasis);
        float textWidth = Math.max(0f, widthBasis - padding.horizontal()
                - element.borderWidth() * 2f);
        UiTextMeasurer.Measurement measured = text.measure(element, textWidth);
        if (measured.font() == null) return Float.NaN;
        float frame = (horizontal ? padding.horizontal() : padding.vertical())
                + element.borderWidth() * 2f;
        return (horizontal ? measured.width() : measured.height()) + frame;
    }

    private static float resolveStackExtent(float preferred, float available, UiAlign align) {
        if (Float.isNaN(preferred) || align == UiAlign.STRETCH) return available;
        return Math.min(available, Math.max(0f, preferred));
    }

    private static float crossOrigin(float origin, float available, float used, UiAlign align) {
        return switch (align) {
            case CENTER -> origin + (available - used) * 0.5f;
            case END -> origin + available - used;
            case START, STRETCH -> origin;
        };
    }
}
