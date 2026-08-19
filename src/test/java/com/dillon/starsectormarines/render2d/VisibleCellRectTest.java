package com.dillon.starsectormarines.render2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleCellRectTest {

    @Test
    void emptyNeverContainsOrIntersects() {
        VisibleCellRect empty = VisibleCellRect.EMPTY;
        assertTrue(empty.isEmpty());
        assertFalse(empty.contains(0, 0));
        assertFalse(empty.intersects(0, 10, 0, 10));
        assertFalse(empty.intersectsCells(0, 0, 2, 2));
    }

    @Test
    void containsIsInclusive() {
        VisibleCellRect view = new VisibleCellRect(10, 20, 12, 22);
        assertTrue(view.contains(10, 20));
        assertTrue(view.contains(12, 22));
        assertFalse(view.contains(9, 21));
        assertFalse(view.contains(11, 23));
    }

    @Test
    void intersectsBuildingAabb() {
        VisibleCellRect view = new VisibleCellRect(10, 10, 20, 20);
        assertTrue(view.intersects(18, 30, 18, 30), "partial overlap should hit");
        assertTrue(view.intersects(10, 20, 10, 20), "identical rect should hit");
        assertFalse(view.intersects(21, 30, 10, 20), "touching just outside max is a miss");
        assertFalse(view.intersects(0, 5, 0, 5));
    }

    @Test
    void intersectsCellsUsesInclusiveFootprint() {
        VisibleCellRect view = new VisibleCellRect(10, 10, 12, 12);
        assertTrue(view.intersectsCells(12, 12, 2, 2), "2x2 anchored on the max corner overlaps");
        assertFalse(view.intersectsCells(13, 13, 2, 2), "just outside is a miss");
        assertFalse(view.intersectsCells(0, 0, 0, 1));
    }
}
