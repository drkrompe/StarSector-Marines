package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hazardous world's town is sealed inside one wall with a few locks ({@link SealedTown}). */
class SealedTownTest {

    private static TargetProfile world(Climate climate) {
        return new TargetProfile(5, 5, 2, 1, "independent", EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.ROCK, SettlementLink.ROAD).withClimate(climate);
    }

    private static Precinct settlement(PrecinctPlan plan) {
        return plan.precincts().stream().filter(p -> p.name().equals("settlement")).findFirst().orElseThrow();
    }

    @Test
    void aHazardousWorldsTownIsWalledWithAFewLocksAndNothingOutside() {
        for (Climate.Hazard hazard : List.of(Climate.Hazard.TOXIC, Climate.Hazard.AIRLESS, Climate.Hazard.RADIATION)) {
            PrecinctPlan plan = PrecinctPlan.derive(world(new Climate(EnumSet.of(hazard))), 240, 144, new Random(7));
            Precinct town = settlement(plan);
            assertFalse(town.isProgrammed(), hazard + ": still a town of streets and blocks");
            assertEquals(Precinct.Boundary.WALLED, town.boundary(), hazard.toString());
            assertEquals(SealedTown.LOCKS, town.fortification(), hazard.toString());
            assertEquals("garrison", plan.objective().name(), hazard + ": the garrison stays the objective");
            for (Precinct p : plan.precincts()) assertFalse(p.name().startsWith("outlying"), hazard + ": " + p.name());
        }
    }

    @Test
    void anOpenWorldsTownIsOpen() {
        Precinct town = settlement(PrecinctPlan.derive(world(Climate.NONE), 240, 144, new Random(7)));
        assertEquals(Precinct.Boundary.OPEN, town.boundary());
    }

    @Test
    void aLockIsMarkedAtItsGateAndAtItsInnerDoorWhereTheRoadRuns() {
        int[][] claim = new int[10][10], road = new int[10][10];
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                claim[x][y] = x >= 2 ? 1 : 0;
                road[x][y] = y == 5 || y == 6 ? 1 : GrownTrunkPlan.UNOWNED;
            }
        }
        // A two-cell gate on the west wall; the road runs in along rows 5 and 6, but row 6 stops short.
        road[2 + SealedTown.LOCK_DEPTH][6] = GrownTrunkPlan.UNOWNED;
        PrecinctBoundary.Gate gate = new PrecinctBoundary.Gate(List.of(new int[]{2, 5}, new int[]{2, 6}), false);
        List<int[]> lines = SealedTown.lockFloors(List.of(gate), claim, road, 1);
        assertTrue(has(lines, 2, 5) && has(lines, 2, 6), "the outer door is the gate");
        assertTrue(has(lines, 2 + SealedTown.LOCK_DEPTH, 5), "the inner door, in from the gate");
        assertFalse(has(lines, 2 + SealedTown.LOCK_DEPTH, 6), "no door where the road does not run");
        assertEquals(3, lines.size(), "the chamber between is left to the road");
    }

    private static boolean has(List<int[]> cells, int x, int y) {
        return cells.stream().anyMatch(c -> c[0] == x && c[1] == y);
    }
}
