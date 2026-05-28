package com.dillon.starsectormarines.battle.world.gen.bsp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone shape tests for {@link TrunkPlan}. Runs without the Starsector
 * runtime — pure mask-and-sub-rect math.
 */
public class TrunkPlanTest {

    private static final int W = 80;
    private static final int H = 80;

    @Test
    public void perimeterIsPainted() {
        for (long seed : new long[]{1L, 42L, 100L, 777L, 9999L}) {
            TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(seed));

            for (int x = 0; x < W; x++) {
                assertTrue(plan.roadCells[x][0],     "seed=" + seed + " top perimeter at x=" + x);
                assertTrue(plan.roadCells[x][H - 1], "seed=" + seed + " bottom perimeter at x=" + x);
            }
            for (int y = 0; y < H; y++) {
                assertTrue(plan.roadCells[0][y],     "seed=" + seed + " left perimeter at y=" + y);
                assertTrue(plan.roadCells[W - 1][y], "seed=" + seed + " right perimeter at y=" + y);
            }
        }
    }

    @Test
    public void crossLayoutHasOnePrimaryAndOneSecondary() {
        TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(42L));
        assertEquals(2, plan.trunks.size());

        TrunkPlan.TrunkSegment primary   = findTrunk(plan, TrunkPlan.TrunkKind.PRIMARY);
        TrunkPlan.TrunkSegment secondary = findTrunk(plan, TrunkPlan.TrunkKind.SECONDARY);

        assertTrue(primary.horizontal,
                "v1 lays the primary boulevard horizontally");
        assertFalse(secondary.horizontal,
                "v1 lays the secondary cross-street vertically");
    }

    @Test
    public void primaryPaintsFullWidthBand() {
        TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(42L));
        TrunkPlan.TrunkSegment primary = findTrunk(plan, TrunkPlan.TrunkKind.PRIMARY);
        int bandHeight = primary.bottom - primary.top + 1;
        assertEquals(TrunkPlan.PRIMARY_WIDTH, bandHeight);
        for (int y = primary.top; y <= primary.bottom; y++) {
            for (int x = 0; x < W; x++) {
                assertTrue(plan.roadCells[x][y], "primary trunk row y=" + y + " x=" + x);
            }
        }
    }

    @Test
    public void secondaryPaintsFullHeightBand() {
        TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(42L));
        TrunkPlan.TrunkSegment secondary = findTrunk(plan, TrunkPlan.TrunkKind.SECONDARY);
        int bandWidth = secondary.right - secondary.left + 1;
        assertEquals(TrunkPlan.SECONDARY_WIDTH, bandWidth);
        for (int x = secondary.left; x <= secondary.right; x++) {
            for (int y = 0; y < H; y++) {
                assertTrue(plan.roadCells[x][y], "secondary trunk column x=" + x + " y=" + y);
            }
        }
    }

    @Test
    public void intersectionExposedAndPainted() {
        TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(42L));
        assertNotNull(plan.intersection);
        TrunkPlan.TrunkSegment primary   = findTrunk(plan, TrunkPlan.TrunkKind.PRIMARY);
        TrunkPlan.TrunkSegment secondary = findTrunk(plan, TrunkPlan.TrunkKind.SECONDARY);
        assertEquals(secondary.left,  plan.intersection.x0);
        assertEquals(secondary.right, plan.intersection.x1);
        assertEquals(primary.top,     plan.intersection.y0);
        assertEquals(primary.bottom,  plan.intersection.y1);
        for (int y = plan.intersection.y0; y <= plan.intersection.y1; y++) {
            for (int x = plan.intersection.x0; x <= plan.intersection.x1; x++) {
                assertTrue(plan.roadCells[x][y], "intersection cell at " + x + "," + y);
            }
        }
    }

    @Test
    public void fourSubRectsHaveCleanInterior() {
        TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(42L));
        assertEquals(4, plan.subRects.size());
        for (TrunkPlan.SubRect r : plan.subRects) {
            assertTrue(r.width() > 0 && r.height() > 0,
                    "sub-rect must be non-empty: "
                            + r.x0 + "," + r.y0 + ".." + r.x1 + "," + r.y1);
            for (int y = r.y0; y <= r.y1; y++) {
                for (int x = r.x0; x <= r.x1; x++) {
                    assertFalse(plan.roadCells[x][y],
                            "sub-rect interior at " + x + "," + y + " must not already be road");
                }
            }
        }
    }

    private static TrunkPlan.TrunkSegment findTrunk(TrunkPlan.Plan plan, TrunkPlan.TrunkKind kind) {
        for (TrunkPlan.TrunkSegment t : plan.trunks) {
            if (t.kind == kind) return t;
        }
        throw new AssertionError("no " + kind + " trunk in plan");
    }
}
