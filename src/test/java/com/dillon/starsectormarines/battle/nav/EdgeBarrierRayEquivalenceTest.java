package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the segment walk that finds the first authored shared-edge barrier a
 * ray crosses against the exhaustive scan it replaced.
 *
 * <p>The walk only visits the grid lines the segment actually crosses, and on
 * each line only the two or three cells whose edge band could contain the
 * crossing. That is a claim about geometry, so it is checked the way a
 * geometric claim should be: tens of thousands of random rays over random
 * barrier layouts, including rays that start or end inside a barrier's own
 * cell, run along a grid line, or are axis-aligned.
 */
class EdgeBarrierRayEquivalenceTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 40;

    @Test
    void segmentWalkFindsTheSameFirstCrossingAsAnExhaustiveScan() {
        Random random = new Random(20260831L);
        int rays = 0;
        int hits = 0;
        for (int map = 0; map < 20; map++) {
            NavigationGrid grid = randomBarrierGrid(random);
            List<SharedEdgeBarrier> all = grid.getEdgeBarriers();
            for (int probe = 0; probe < 1500; probe++) {
                float x0 = randomCoordinate(random, WIDTH);
                float y0 = randomCoordinate(random, HEIGHT);
                float x1 = randomCoordinate(random, WIDTH);
                float y1 = randomCoordinate(random, HEIGHT);

                for (int filter = 0; filter < 3; filter++) {
                    SharedEdgeBarrier expected =
                            exhaustiveFirstCrossing(all, x0, y0, x1, y1, filter);
                    SharedEdgeBarrier actual = switch (filter) {
                        case 1 -> grid.firstSightBlockingEdgeBarrierOnLine(x0, y0, x1, y1);
                        case 2 -> grid.firstProjectileBlockingEdgeBarrierOnLine(x0, y0, x1, y1);
                        default -> grid.firstEdgeBarrierOnLine(x0, y0, x1, y1);
                    };
                    assertEquals(expected, actual,
                            "walk and exhaustive scan disagree for filter " + filter
                                    + " on ray (" + x0 + "," + y0 + ")->(" + x1 + "," + y1 + ")");
                    if (expected != null) hits++;
                }
                rays++;
            }
        }
        assertTrue(hits > rays / 4,
                "probes must actually cross barriers to be worth anything; "
                        + hits + " crossings over " + rays + " rays");
    }

    /** The scan exactly as it read before: every barrier on the map, nearest crossing wins. */
    private static SharedEdgeBarrier exhaustiveFirstCrossing(
            List<SharedEdgeBarrier> barriers,
            float x0, float y0, float x1, float y1, int filter) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float bestT = Float.POSITIVE_INFINITY;
        SharedEdgeBarrier best = null;
        for (SharedEdgeBarrier barrier : barriers) {
            if (filter == 1 && !barrier.kind().blocksSight()) continue;
            if (filter == 2 && !barrier.kind().blocksProjectiles()) continue;
            float t;
            float along;
            if (barrier.direction() == Direction.E) {
                if (Math.abs(dx) < 1e-7f) continue;
                t = (barrier.cellX() + 1f - x0) / dx;
                along = y0 + dy * t;
                if (along < barrier.cellY() - 1e-6f
                        || along > barrier.cellY() + 1f + 1e-6f) continue;
            } else {
                if (Math.abs(dy) < 1e-7f) continue;
                t = (barrier.cellY() + 1f - y0) / dy;
                along = x0 + dx * t;
                if (along < barrier.cellX() - 1e-6f
                        || along > barrier.cellX() + 1f + 1e-6f) continue;
            }
            if (t <= 1e-6f || t >= 1f - 1e-6f || t >= bestT) continue;
            bestT = t;
            best = barrier;
        }
        return best;
    }

    /**
     * Mixes cell centres, cell corners and arbitrary points, so the probe set
     * includes the near-integer crossings where the edge band's tolerance
     * decides which cell owns a hit.
     */
    private static float randomCoordinate(Random random, int extent) {
        int shape = random.nextInt(3);
        int cell = random.nextInt(extent);
        if (shape == 0) return cell + 0.5f;
        if (shape == 1) return cell;
        return cell + random.nextFloat();
    }

    private static NavigationGrid randomBarrierGrid(Random random) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        SharedEdgeBarrier.Kind[] kinds = SharedEdgeBarrier.Kind.values();
        for (int i = 0; i < 400; i++) {
            int x = random.nextInt(WIDTH - 1);
            int y = random.nextInt(HEIGHT - 1);
            Direction direction = random.nextBoolean() ? Direction.E : Direction.N;
            grid.tryPlaceEdgeBarrier(x, y, direction,
                    kinds[random.nextInt(kinds.length)]);
        }
        return grid;
    }
}
