package com.dillon.starsectormarines.ui.retained;

import java.util.List;

/**
 * U1 retained layout: row, column, and stack with explicit preferred sizes and
 * flexible growth.
 *
 * <p>This is deliberately smaller than MoonLightEngine's CSS layout engine,
 * but preserves its most important boundary: the engine writes one retained
 * {@link LayoutBox} per element and every later phase reads those boxes.
 */
public final class UiLayoutEngine {

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
        Rect content = element.box().contentBox();
        List<UiElement> children = element.children();
        element.box().scrollHeight(0f);
        if (children.isEmpty()) return;

        if (element.layout() == UiLayout.STACK) {
            arrangeStack(children, content);
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

    private static float preferredMain(UiElement element, boolean horizontal,
                                       float widthBasis, float heightBasis) {
        float preferred = preferredAxis(element, horizontal, widthBasis, heightBasis);
        return Float.isNaN(preferred) ? 0f : Math.max(0f, preferred);
    }

    private static float preferredCross(UiElement element, boolean horizontal,
                                        float widthBasis, float heightBasis) {
        return preferredAxis(element, !horizontal, widthBasis, heightBasis);
    }

    private static float preferredAxis(UiElement element, boolean horizontal,
                                       float widthBasis, float heightBasis) {
        float preferred = horizontal ? element.resolvedPreferredWidth(widthBasis)
                : element.resolvedPreferredHeight(heightBasis, widthBasis);
        if (Float.isNaN(preferred) && element.tag() == UiTag.CANVAS) {
            return horizontal ? element.canvasWidth() : element.canvasHeight();
        }
        return preferred;
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
