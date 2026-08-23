package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiScrollTest {

    private static final float EPSILON = 0.001f;
    private static final float ROW_HEIGHT = 40f;
    private static final float VIEWPORT_HEIGHT = 100f;

    @Test
    void wheelMovesChildrenWithoutMovingTheirScrollContainer() {
        UiElement list = list(Overflow.SCROLL, 5);
        UiDocument document = document(list);
        Rect containerBefore = list.box().borderBox();
        float rowBefore = row(list, 0).box().borderBox().y();

        assertTrue(document.pointerScrolled(100f, 50f, 30f));

        assertEquals(30f, list.scrollTop(), EPSILON);
        assertEquals(rowBefore - 30f, row(list, 0).box().borderBox().y(), EPSILON);
        assertEquals(containerBefore, list.box().borderBox());
    }

    @Test
    void scrollingClampsAtBothEnds() {
        UiElement list = list(Overflow.SCROLL, 5);
        UiDocument document = document(list);

        assertTrue(document.pointerScrolled(100f, 50f, 10_000f));
        assertEquals(100f, list.box().maxScrollTop(), EPSILON);
        assertEquals(100f, list.scrollTop(), EPSILON);

        assertTrue(document.pointerScrolled(100f, 50f, -10_000f));
        assertEquals(0f, list.scrollTop(), EPSILON);
    }

    @Test
    void hitTestingMovesWithScrolledRowsAndIncludesNonClickableElements() {
        UiElement list = list(Overflow.SCROLL, 5);
        UiDocument document = document(list);
        assertSame(row(list, 0), document.elementAt(100f, 20f));

        document.pointerScrolled(100f, 50f, 100f);

        assertSame(row(list, 3), document.elementAt(100f, 30f));
        for (float y = 0f; y < VIEWPORT_HEIGHT; y += 5f) {
            assertFalse(document.elementAt(100f, y) == row(list, 0));
        }
    }

    @Test
    void visibleOverflowAndFittingContentDoNotScroll() {
        UiElement visible = list(Overflow.VISIBLE, 5);
        UiDocument visibleDocument = document(visible);
        assertFalse(visibleDocument.pointerScrolled(100f, 50f, 30f));
        assertEquals(0f, visible.scrollTop(), EPSILON);

        UiElement fitting = list(Overflow.SCROLL, 2);
        UiDocument fittingDocument = document(fitting);
        assertTrue(fittingDocument.pointerScrolled(100f, 50f, 30f));
        assertEquals(0f, fitting.scrollTop(), EPSILON);
        assertEquals(0f, fitting.box().maxScrollTop(), EPSILON);
    }

    @Test
    void innerSurfaceAtItsEndPassesWheelToScrollableAncestor() {
        UiElement inner = list(Overflow.SCROLL, 5).preferredHeight(100f);
        UiElement tail = new UiElement("tail").preferredHeight(40f);
        UiElement outer = new UiElement("outer")
                .layout(UiLayout.COLUMN)
                .overflow(Overflow.SCROLL)
                .child(inner)
                .child(tail);
        UiDocument document = document(outer);

        document.pointerScrolled(100f, 50f, 10_000f);
        assertEquals(100f, inner.scrollTop(), EPSILON);
        assertEquals(0f, outer.scrollTop(), EPSILON);

        document.pointerScrolled(100f, 50f, 20f);
        assertEquals(100f, inner.scrollTop(), EPSILON);
        assertEquals(20f, outer.scrollTop(), EPSILON);
    }

    @Test
    void terminalWheelIsStillOwnedByTheScrollSurface() {
        UiElement list = list(Overflow.SCROLL, 5);
        UiDocument document = document(list);
        document.pointerScrolled(100f, 50f, 10_000f);

        assertTrue(document.pointerScrolled(100f, 50f, 20f));
        assertFalse(document.pointerScrolled(250f, 50f, 20f));
    }

    @Test
    void overlayThumbTracksTheRetainedOffsetAndOnlyScrollOverflowDrawsIt() {
        UiElement list = list(Overflow.SCROLL, 5);
        UiDocument document = document(list);
        Rect atTop = UiPainter.scrollThumbRect(list);

        document.pointerScrolled(100f, 50f, 10_000f);
        Rect atBottom = UiPainter.scrollThumbRect(list);

        assertTrue(atTop != null);
        assertTrue(atBottom != null);
        assertTrue(atBottom.y() > atTop.y());
        assertEquals(atTop.height(), atBottom.height(), EPSILON);

        UiElement hidden = list(Overflow.HIDDEN, 5);
        document(hidden);
        assertNull(UiPainter.scrollThumbRect(hidden));
    }

    private static UiElement list(Overflow overflow, int rows) {
        UiElement list = new UiElement("list")
                .layout(UiLayout.COLUMN)
                .overflow(overflow);
        for (int index = 0; index < rows; index++) {
            list.child(new UiElement("row-" + index).preferredHeight(ROW_HEIGHT));
        }
        return list;
    }

    private static UiDocument document(UiElement root) {
        UiDocument document = new UiDocument(root);
        document.layout(200f, VIEWPORT_HEIGHT);
        return document;
    }

    private static UiElement row(UiElement list, int index) {
        return list.children().get(index);
    }
}
