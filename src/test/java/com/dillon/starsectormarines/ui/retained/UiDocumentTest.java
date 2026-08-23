package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentTest {

    private static final float EPSILON = 0.001f;

    @Test
    void columnPaysPreferredSizesAndGapsBeforeFlexibleGrowth() {
        UiElement header = new UiElement("header").preferredHeight(50f);
        UiElement body = new UiElement("body").grow(1f);
        UiElement footer = new UiElement("footer").preferredHeight(30f);
        UiElement root = new UiElement("root")
                .layout(UiLayout.COLUMN)
                .padding(10f)
                .gap(10f)
                .child(header)
                .child(body)
                .child(footer);

        UiDocument document = new UiDocument(root);
        document.layout(1000f, 600f);

        assertRect(root.box().borderBox(), 0f, 0f, 1000f, 600f);
        assertRect(header.box().borderBox(), 10f, 10f, 980f, 50f);
        assertRect(body.box().borderBox(), 10f, 70f, 980f, 480f);
        assertRect(footer.box().borderBox(), 10f, 560f, 980f, 30f);
    }

    @Test
    void rowDistributesFreeSpaceByGrowthWeight() {
        UiElement first = new UiElement("first").grow(1f);
        UiElement second = new UiElement("second").grow(2f);
        UiElement fixed = new UiElement("fixed").preferredWidth(90f);
        UiElement root = new UiElement("root")
                .layout(UiLayout.ROW)
                .gap(5f)
                .child(first)
                .child(second)
                .child(fixed);

        new UiDocument(root).layout(400f, 100f);

        assertRect(first.box().borderBox(), 0f, 0f, 100f, 100f);
        assertRect(second.box().borderBox(), 105f, 0f, 200f, 100f);
        assertRect(fixed.box().borderBox(), 310f, 0f, 90f, 100f);
    }

    @Test
    void stackCentersBoundedOverlayWithoutMovingFillChild() {
        UiElement fill = new UiElement("fill");
        UiElement overlay = new UiElement("overlay")
                .preferredSize(200f, 80f)
                .align(UiAlign.CENTER, UiAlign.CENTER);
        UiElement root = new UiElement("root")
                .layout(UiLayout.STACK)
                .child(fill)
                .child(overlay);

        new UiDocument(root).layout(600f, 400f);

        assertRect(fill.box().borderBox(), 0f, 0f, 600f, 400f);
        assertRect(overlay.box().borderBox(), 200f, 160f, 200f, 80f);
    }

    @Test
    void laterPaintedOverlappingElementReceivesClickFirst() {
        AtomicInteger lowerClicks = new AtomicInteger();
        AtomicInteger upperClicks = new AtomicInteger();
        UiElement lower = new UiElement("lower").onClick(lowerClicks::incrementAndGet);
        UiElement upper = new UiElement("upper").onClick(upperClicks::incrementAndGet);
        UiElement root = new UiElement("root")
                .layout(UiLayout.STACK)
                .child(lower)
                .child(upper);
        UiDocument document = new UiDocument(root);
        document.layout(100f, 100f);

        assertSame(upper, document.hit(50f, 50f));
        assertTrue(document.pointerDown(50f, 50f));
        assertTrue(document.pointerUp(50f, 50f));
        assertEquals(0, lowerClicks.get());
        assertEquals(1, upperClicks.get());
    }

    @Test
    void releasingAwayFromArmedElementCancelsClick() {
        AtomicInteger clicks = new AtomicInteger();
        UiElement button = new UiElement("button")
                .preferredSize(50f, 50f)
                .align(UiAlign.START, UiAlign.START)
                .onClick(clicks::incrementAndGet);
        UiElement root = new UiElement("root")
                .layout(UiLayout.STACK)
                .child(button);
        UiDocument document = new UiDocument(root);
        document.layout(100f, 100f);

        assertTrue(document.pointerDown(25f, 25f));
        assertTrue(document.pointerUp(75f, 75f));
        assertEquals(0, clicks.get());
        assertFalse(button.armed());
    }

    private static void assertRect(Rect rect, float x, float y, float width, float height) {
        assertEquals(x, rect.x(), EPSILON);
        assertEquals(y, rect.y(), EPSILON);
        assertEquals(width, rect.width(), EPSILON);
        assertEquals(height, rect.height(), EPSILON);
    }
}
