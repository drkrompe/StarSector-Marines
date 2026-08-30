package com.dillon.starsectormarines.battle.nav;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Termination of the exact-segment grid walk. Both continuous-point traversals
 * used to be bounded only by their two axes arriving on the same iteration; an
 * endpoint sitting exactly on a cell boundary put the last crossing at
 * {@code t == 1}, the walk took it, and because each axis steps monotonically
 * the end cell could never be reached again — the traversal ran off the grid
 * forever. Any code that places a unit on whole-number coordinates reaches
 * that, which is why these ask about integer endpoints specifically.
 */
class NavigationGridRayWalkTest {

    @Test
    void fireTraceTerminatesWhenTheTargetSitsExactlyOnACellBoundary() {
        NavigationGrid grid = openGrid(64, 32);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertTrue(grid.hasLineOfFire(10.5f, 16.5f, 30f, 15f),
                        "an open field is clear however the endpoints round"));
    }

    @Test
    void everyBoundaryExactEndpointTerminates() {
        NavigationGrid grid = openGrid(24, 16);

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            for (int sx = 0; sx < 24; sx++) {
                for (int sy = 0; sy < 16; sy++) {
                    for (int tx = 0; tx < 24; tx++) {
                        for (int ty = 0; ty < 16; ty++) {
                            grid.hasLineOfFire(sx + 0.5f, sy + 0.5f, tx, ty);
                            grid.hasLineOfFire(sx, sy, tx + 0.5f, ty + 0.5f);
                        }
                    }
                }
            }
        });
    }

    @Test
    void smokeDepthTerminatesOnTheSameGeometry() {
        NavigationGrid grid = openGrid(64, 32);
        grid.addTransientOpacityAt(grid.index(20, 15));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertEquals(1, grid.smokeDepthOnLine(10.5f, 16.5f, 30f, 15f)));
    }

    @Test
    void aRetiredAxisNeverLeavesTheSegment() {
        NavigationGrid grid = openGrid(64, 32);
        // The segment descends from y 16.5 to exactly y 15.0, so it never
        // enters row 14 — a wall there cannot block it, and a wall on the
        // row it does cross must.
        grid.setWalkable(20, 14, false);
        grid.setWalkable(40, 15, false);

        assertAll(
                () -> assertTrue(grid.hasLineOfFire(10.5f, 16.5f, 30f, 15f),
                        "row 14 is below the segment's own footprint"),
                () -> assertTrue(!grid.hasLineOfFire(10.5f, 16.5f, 50f, 15f),
                        "a wall on the row the segment does cross still stops it"));
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
