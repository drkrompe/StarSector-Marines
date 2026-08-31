package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corridor derivation, asked directly.
 *
 * <p>Every question here is about {@link VehicleCorridorPlan#forAxis} and is
 * answered by one trunk plan, because that is the unit. Whether a generated
 * fortress leaves the band drivable is a different question about a whole map
 * and belongs to the validation scan.
 */
class VehicleCorridorPlanTest {

    private static final int W = 240;
    private static final int H = 160;

    private static TrunkPlan.Plan plan() {
        return TrunkPlan.generate(W, H, new Random(1L));
    }

    /** The trunk chosen is the one parallel to the axis, not merely the first in the list. */
    @Test
    void theCorridorFollowsTheTrunkThatRunsAlongTheTraversalAxis() {
        TrunkPlan.Plan plan = plan();
        TrunkPlan.TrunkSegment vertical = plan.trunks.stream()
                .filter(t -> !t.horizontal).findFirst().orElseThrow();
        TrunkPlan.TrunkSegment horizontal = plan.trunks.stream()
                .filter(t -> t.horizontal).findFirst().orElseThrow();

        VehicleCorridor northward = VehicleCorridorPlan.forAxis(plan, TraversalAxis.SOUTH_TO_NORTH);
        assertTrue(northward.contains((vertical.left + vertical.right) / 2, H / 2),
                "a south-to-north corridor runs down the vertical trunk");
        assertFalse(northward.contains(W / 4, (horizontal.top + horizontal.bottom) / 2),
                "and not along the horizontal one it crosses");

        VehicleCorridor eastward = VehicleCorridorPlan.forAxis(plan, TraversalAxis.WEST_TO_EAST);
        assertTrue(eastward.contains(W / 4, (horizontal.top + horizontal.bottom) / 2),
                "a west-to-east corridor runs along the horizontal trunk");
    }

    /**
     * The band is {@link VehicleCorridor#WIDTH} wide on every line it crosses.
     * A width that varies with the trunk it was cut from is the defect this
     * whole layer replaces: the old kept route was as wide as the geometry
     * happened to leave it, which was sometimes two.
     */
    @Test
    void theBandIsAUniformFiveCellsWideAlongItsWholeLength() {
        TrunkPlan.Plan plan = plan();

        VehicleCorridor northward = VehicleCorridorPlan.forAxis(plan, TraversalAxis.SOUTH_TO_NORTH);
        for (int y = 0; y < H; y++) {
            int wide = 0;
            for (int x = 0; x < W; x++) {
                if (northward.contains(x, y)) wide++;
            }
            assertEquals(VehicleCorridor.WIDTH, wide, "corridor width at y=" + y);
        }

        VehicleCorridor eastward = VehicleCorridorPlan.forAxis(plan, TraversalAxis.WEST_TO_EAST);
        for (int x = 0; x < W; x++) {
            int wide = 0;
            for (int y = 0; y < H; y++) {
                if (eastward.contains(x, y)) wide++;
            }
            assertEquals(VehicleCorridor.WIDTH, wide, "corridor width at x=" + x);
        }
    }

    /**
     * A seven-cell PRIMARY trunk gives back a cell of kerb on each side rather
     * than claiming the whole band. The corridor asks for what a vehicle needs,
     * so a turret may still stand on the pavement beside a road it cannot block.
     */
    @Test
    void aWideTrunkYieldsItsFlanksRatherThanTheWholeBand() {
        TrunkPlan.Plan plan = plan();
        TrunkPlan.TrunkSegment primary = plan.trunks.stream()
                .filter(t -> t.horizontal).findFirst().orElseThrow();
        assertEquals(TrunkPlan.PRIMARY_WIDTH, primary.bottom - primary.top + 1,
                "guard: the horizontal trunk is the wide one");

        VehicleCorridor corridor = VehicleCorridorPlan.forAxis(plan, TraversalAxis.WEST_TO_EAST);
        assertFalse(corridor.contains(W / 2, primary.top),
                "the outer kerb row stays available to build on");
        assertFalse(corridor.contains(W / 2, primary.bottom), "and so does the other one");
        assertTrue(corridor.contains(W / 2, primary.top + 1), "the driving surface starts inside it");
        assertTrue(corridor.contains(W / 2, primary.bottom - 1));
    }

    /**
     * The entry sits <em>on</em> the defender's rear border, not one cell short
     * of it. The wall stamper's orphan-pocket seal floods from walkable border
     * cells, so a corridor that stops short is not a flood seed and its
     * fortress-side half can be filled in solid.
     */
    @Test
    void theEntryLandsOnTheDefendersRearBorderAtTheBandsCentre() {
        TrunkPlan.Plan plan = plan();

        VehicleCorridor northward = VehicleCorridorPlan.forAxis(plan, TraversalAxis.SOUTH_TO_NORTH);
        assertEquals(H - 1, northward.entryY, "south-to-north enters from the top border");
        assertTrue(northward.contains(northward.entryX, northward.entryY),
                "and the entry is a corridor cell");

        VehicleCorridor eastward = VehicleCorridorPlan.forAxis(plan, TraversalAxis.WEST_TO_EAST);
        assertEquals(W - 1, eastward.entryX, "west-to-east enters from the right border");
        assertTrue(eastward.contains(eastward.entryX, eastward.entryY));
    }

    /** The corridor spans the map, so the convoy that enters at the rear can reach the city. */
    @Test
    void theBandRunsFromOneEdgeToTheOther() {
        TrunkPlan.Plan plan = plan();
        VehicleCorridor northward = VehicleCorridorPlan.forAxis(plan, TraversalAxis.SOUTH_TO_NORTH);
        assertTrue(northward.contains(northward.entryX, 0), "reaches the attacker's edge");
        assertTrue(northward.contains(northward.entryX, H - 1), "and the defender's");
        assertEquals(VehicleCorridor.WIDTH * H, northward.cellCount());
    }

    /**
     * No axis is no corridor. A map family with no traversal axis has no
     * defender rear edge for a convoy to arrive from, so binding nothing is the
     * right answer rather than an error — every consumer then behaves as it did
     * before the corridor existed.
     */
    @Test
    void aMapWithNoTraversalAxisGetsNoCorridor() {
        assertNull(VehicleCorridorPlan.forAxis(plan(), null));
        assertNull(VehicleCorridorPlan.forAxis(null, TraversalAxis.SOUTH_TO_NORTH));
    }
}
