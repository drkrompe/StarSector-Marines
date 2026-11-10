package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import java.util.EnumSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a sealed complex is owed and how its roads give way to halls ({@link SealedComplex}). */
class SealedComplexTest {

    private static TargetProfile world(Climate climate) {
        return new TargetProfile(5, 5, 2, 1, "independent", EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.ROCK, SettlementLink.ROAD).withClimate(climate);
    }

    private static final Climate RADIATION = new Climate(EnumSet.of(Climate.Hazard.RADIATION));

    @Test
    void anIrradiatedWorldLivesInSealedComplexesWithNoOutlyingPlaces() {
        PrecinctPlan plan = PrecinctPlan.derive(world(RADIATION), 240, 144, new Random(7));
        assertEquals("garrison", plan.objective().name());
        for (Precinct p : plan.precincts()) {
            if (p.isLanding()) continue;
            assertTrue(p.isProgrammed() && p.program().sealed(), p.name() + " is sealed");
            assertEquals(0, p.program().airfields(), p.name() + " keeps no airfield");
            assertFalse(p.name().startsWith("outlying"), p.name());
        }
    }

    @Test
    void anOpenWorldKeepsItsTown() {
        PrecinctPlan plan = PrecinctPlan.derive(world(Climate.NONE), 240, 144, new Random(7));
        for (Precinct p : plan.precincts()) {
            assertFalse(p.isProgrammed() && p.program().sealed(), p.name());
        }
    }

    @Test
    void aSealedComplexAsksForLessGroundThanTheSameRoomsWithAYard() {
        FortressProgram open = FortressProgram.habitat(5);
        assertTrue(open.sealedComplex().envelopeArea() < open.envelopeArea());
    }

    @Test
    void onlyTheRoadStubsAtTheGatesStayRoad() {
        int w = 20, h = 11;
        int[][] own = new int[w][h];
        boolean[][] buildable = new boolean[w][h];
        boolean[][] circulation = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) own[x][y] = 1;
            circulation[x][5] = true;
        }
        SealedComplex.gateStubsOnly(buildable, circulation, own, 1);
        // The edge is depth 1, so the stub runs GATE_STUB cells in from each end.
        assertTrue(circulation[0][5] && circulation[SealedComplex.GATE_STUB - 1][5]);
        assertFalse(circulation[w / 2][5]);
        assertTrue(buildable[w / 2][5]);
    }
}
