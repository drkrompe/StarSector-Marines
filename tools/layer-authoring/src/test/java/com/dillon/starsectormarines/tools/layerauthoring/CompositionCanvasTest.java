package com.dillon.starsectormarines.tools.layerauthoring;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositionCanvasTest {

    @Test
    void rotationHandleTracksAuthoredCounterClockwiseAngle() {
        Point pivot = new Point(100, 100);

        assertEquals(new Point(100, 62),
                CompositionCanvas.rotationHandlePoint(pivot, 0.0));
        assertEquals(new Point(62, 100),
                CompositionCanvas.rotationHandlePoint(pivot, 90.0));
        assertEquals(new Point(138, 100),
                CompositionCanvas.rotationHandlePoint(pivot, -90.0));
    }

    @Test
    void rotationDragUsesShortestDeltaAcrossAngleSeam() {
        assertEquals(-160.0,
                CompositionCanvas.draggedAngle(-180.0, 170.0, -170.0), 0.000001);
        assertEquals(160.0,
                CompositionCanvas.draggedAngle(-180.0, -170.0, 170.0), 0.000001);
    }

    @Test
    void pointerAngleMatchesScreenSpaceHandleConvention() {
        Point pivot = new Point(100, 100);

        assertEquals(0.0,
                CompositionCanvas.pointerAngle(pivot, new Point(100, 50)), 0.000001);
        assertEquals(90.0,
                CompositionCanvas.pointerAngle(pivot, new Point(50, 100)), 0.000001);
        assertEquals(-90.0,
                CompositionCanvas.pointerAngle(pivot, new Point(150, 100)), 0.000001);
    }
}
