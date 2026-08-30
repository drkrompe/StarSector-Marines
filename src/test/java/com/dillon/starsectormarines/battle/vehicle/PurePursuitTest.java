package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The carrot picker's crossed-waypoint cursor, at the one index that used to be
 * unreachable.
 *
 * <p>Every caller passed {@code startIdx 1} because a cursor left at zero stuck
 * there and dragged the carrot back onto the body. That made zero a trap rather
 * than a value, so these pin what it means: consume the first waypoint when the
 * body is past it, keep it when the body is not.
 */
class PurePursuitTest {

    /** A straight run along +x, so every expected number is arithmetic rather than geometry. */
    private static final float[] XS = {0f, 10f, 20f};
    private static final float[] YS = {0f, 0f, 0f};

    @Test
    @DisplayName("a cursor at zero is consumed once the body is past the first waypoint")
    void cursorAdvancesOffTheFirstWaypoint() {
        PurePursuit.Carrot carrot = PurePursuit.pick(5f, 0f, XS, YS, 0, 2f);

        assertEquals(1, carrot.nextIdx, "waypoint 0 is behind the body and should be consumed");
        assertEquals(7f, carrot.x, 1e-4f, "carrot sits lookAhead ahead of the body, not behind it");
        assertEquals(0f, carrot.y, 1e-4f);
        assertFalse(carrot.atEnd);
    }

    @Test
    @DisplayName("starting at zero and starting at one agree once the first waypoint is behind")
    void zeroAndOneAgreeOnceTheFirstWaypointIsBehind() {
        PurePursuit.Carrot fromZero = PurePursuit.pick(5f, 0f, XS, YS, 0, 2f);
        PurePursuit.Carrot fromOne = PurePursuit.pick(5f, 0f, XS, YS, 1, 2f);

        assertEquals(fromOne.nextIdx, fromZero.nextIdx);
        assertEquals(fromOne.x, fromZero.x, 1e-4f);
        assertEquals(fromOne.y, fromZero.y, 1e-4f);
    }

    @Test
    @DisplayName("a cursor at zero is kept while the body is still short of the first waypoint")
    void firstWaypointIsKeptWhileTheBodyIsShortOfIt() {
        // Same path shifted so the body starts ten cells before waypoint 0.
        float[] xs = {10f, 20f, 30f};
        float[] ys = {0f, 0f, 0f};

        PurePursuit.Carrot carrot = PurePursuit.pick(5f, 0f, xs, ys, 0, 2f);

        assertEquals(0, carrot.nextIdx, "waypoint 0 is still ahead and must not be skipped");
        assertEquals(7f, carrot.x, 1e-4f);
    }

    @Test
    @DisplayName("the flat cell-path overload advances off zero the same way")
    void cellPathCursorAdvancesOffTheFirstWaypoint() {
        // Waypoints are cell centres: (0.5, 0.5), (10.5, 0.5), (20.5, 0.5).
        int[] cells = {0, 0, 10, 0, 20, 0};

        PurePursuit.Carrot carrot = PurePursuit.pick(5.5f, 0.5f, cells, 0, 2f);

        assertEquals(1, carrot.nextIdx);
        assertEquals(7.5f, carrot.x, 1e-4f);
        assertEquals(0.5f, carrot.y, 1e-4f);
    }

    @Test
    @DisplayName("the flat cell-path overload keeps zero while the first cell is ahead")
    void cellPathKeepsFirstWaypointWhileItIsAhead() {
        int[] cells = {10, 0, 20, 0, 30, 0};

        PurePursuit.Carrot carrot = PurePursuit.pick(5.5f, 0.5f, cells, 0, 2f);

        assertEquals(0, carrot.nextIdx);
        assertEquals(7.5f, carrot.x, 1e-4f);
    }
}
