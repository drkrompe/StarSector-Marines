package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiCanvasTest {

    private static final float EPSILON = 0.001f;

    @Test
    void stretchedSurfaceMapsPointsAndExtentsOnEachAxisIndependently() {
        CanvasMetrics metrics = new CanvasMetrics(
                new Rect(20f, 30f, 400f, 100f), 200, 200, 2f);

        assertEquals(2f, metrics.scaleX(), EPSILON);
        assertEquals(0.5f, metrics.scaleY(), EPSILON);
        assertEquals(4f, metrics.deviceScaleX(), EPSILON);
        assertEquals(1f, metrics.deviceScaleY(), EPSILON);
        assertEquals(50f, metrics.toCanvasX(120f), EPSILON);
        assertEquals(100f, metrics.toCanvasY(80f), EPSILON);
        assertEquals(120f, metrics.toDocumentX(50f), EPSILON);
        assertEquals(80f, metrics.toDocumentY(100f), EPSILON);
        assertEquals(0.5f, CanvasContext.strokeScale(metrics, 10f, 0f), EPSILON);
        assertEquals(2f, CanvasContext.strokeScale(metrics, 0f, 10f), EPSILON);
    }

    @Test
    void uiScaleChangesOnlyDeviceScaleNotDocumentCanvasMapping() {
        Rect content = new Rect(10f, 15f, 300f, 150f);
        CanvasMetrics one = new CanvasMetrics(content, 300, 150, 1f);
        CanvasMetrics two = new CanvasMetrics(content, 300, 150, 2f);

        assertEquals(one.toCanvasX(160f), two.toCanvasX(160f), EPSILON);
        assertEquals(one.toCanvasY(90f), two.toCanvasY(90f), EPSILON);
        assertEquals(one.deviceScaleX() * 2f, two.deviceScaleX(), EPSILON);
    }

    @Test
    void collapsedRenderedSurfaceHasNoInventedPointerCoordinate() {
        CanvasMetrics metrics = new CanvasMetrics(Rect.EMPTY, 300, 150, 1f);

        assertTrue(Float.isNaN(metrics.toCanvasX(0f)));
        assertTrue(Float.isNaN(metrics.toCanvasY(0f)));
        assertNull(UiPainter.canvasVisibleBounds(metrics, Rect.EMPTY));
    }

    @Test
    void canvasSurfaceProvidesIntrinsicLayoutSizeWhenNoPreferredSizeExists() {
        UiElement canvas = new UiElement("canvas")
                .tag(UiTag.CANVAS)
                .canvasSize(320, 180)
                .align(UiAlign.START, UiAlign.START);
        UiElement root = new UiElement("root").child(canvas);
        UiDocument document = new UiDocument(root);

        document.layout(500f, 400f);

        assertEquals(320f, canvas.box().borderBox().width(), EPSILON);
        assertEquals(180f, canvas.box().borderBox().height(), EPSILON);
    }

    @Test
    void visibleBoundsAreReportedInSurfaceCoordinates() {
        CanvasMetrics metrics = new CanvasMetrics(
                new Rect(10f, 20f, 200f, 100f), 400, 200, 1f);

        Rect visible = UiPainter.canvasVisibleBounds(metrics,
                new Rect(60f, 45f, 100f, 50f));

        assertNotNull(visible);
        assertEquals(new Rect(100f, 50f, 200f, 100f), visible);
    }

    @Test
    void registryRequiresAttachedCanvasAndTracksReplacementAndInvalidation() {
        UiElement canvas = new UiElement("canvas").tag(UiTag.CANVAS);
        UiDocument document = new UiDocument(canvas);
        CanvasProducer first = context -> { };
        CanvasProducer second = context -> { };

        document.canvases().set(canvas, first);
        assertSame(first, document.canvases().producerOf(canvas));
        document.canvases().set(canvas, second);
        assertSame(second, document.canvases().producerOf(canvas));
        long beforeInvalidate = document.canvases().revision();
        document.canvases().invalidate(canvas);
        assertEquals(beforeInvalidate + 1, document.canvases().revision());

        document.canvases().clear(canvas);
        assertNull(document.canvases().producerOf(canvas));
        assertEquals(0, document.canvases().registeredCount());

        UiElement detached = new UiElement("detached").tag(UiTag.CANVAS);
        assertThrows(IllegalArgumentException.class,
                () -> document.canvases().set(detached, first));
        assertThrows(IllegalArgumentException.class,
                () -> document.canvases().set(new UiElement("div"), first));
    }

    @Test
    void detachedCanvasRegistrationIsPrunedOnTheNextLayout() {
        UiElement canvas = new UiElement("canvas").tag(UiTag.CANVAS);
        UiElement root = new UiElement("root").child(canvas);
        UiDocument document = new UiDocument(root);
        document.canvases().set(canvas, context -> { });

        root.remove(canvas);
        document.layout(300f, 200f);

        assertEquals(0, document.canvases().registeredCount());
        assertNull(document.canvases().producerOf(canvas));
    }

    @Test
    void documentMetricsUseTheCanvasContentBoxRatherThanBorderOrPaddingBox() {
        UiElement canvas = new UiElement("canvas")
                .tag(UiTag.CANVAS)
                .canvasSize(200, 100)
                .padding(10f)
                .border(5f, null);
        UiDocument document = new UiDocument(canvas);
        document.layout(300f, 200f);

        CanvasMetrics metrics = document.canvasMetrics(canvas, 1f);

        assertEquals(canvas.box().contentBox(), metrics.contentBox());
        assertEquals(270f / 200f, metrics.scaleX(), EPSILON);
        assertEquals(170f / 100f, metrics.scaleY(), EPSILON);
    }
}
