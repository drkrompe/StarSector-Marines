package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens when a parked airframe is destroyed where it stands.
 *
 * <p>A flat, open 40x40 grid: the question is about the blast, so nothing
 * should be in the way of it but distance.
 */
class AirframeCookOffSystemTest {

    private static final int W = 40;
    private static final int H = 40;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 20;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Whether a unit is still on the live roster. */
    private static boolean onMap(BattleSimulation sim, long unit) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.liveUnitAt(i) == unit) return true;
        }
        return false;
    }

    /** An aircraft standing on a hardstand, placed the way a field places one. */
    private static long parkedAircraft(BattleSimulation sim, int x, int y) {
        sim.getAirfieldService().addBerth(
                LandingPad.garrison(x, y, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
        new AirfieldSystem(Faction.DEFENDER)
                .tick(BattleSimulation.TICK_DT, sim, sim.getAirfieldService());
        List<AirfieldService.Berth> berths = sim.getAirfieldService().berths();
        return berths.get(berths.size() - 1).airframeId;
    }

    private static long marine(BattleSimulation sim, String id, int x, int y) {
        return sim.spawn(new EntitySpec(id, Faction.MARINE, UnitType.MARINE, x, y));
    }

    /** The people who walked onto the apron to burn it are standing next to a full tank. */
    @Test
    void theBlastCatchesWhoeverIsBesideTheStand() {
        BattleSimulation sim = openSim();
        long airframe = parkedAircraft(sim, PAD_X, PAD_Y);
        long beside = marine(sim, "beside", PAD_X + 2, PAD_Y);
        long acrossTheField = marine(sim, "across", PAD_X + 12, PAD_Y);

        sim.applyDamage(airframe, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(onMap(sim, beside),
                "two cells from a burning aircraft is inside the fire");
        assertTrue(onMap(sim, acrossTheField),
                "twelve cells away is not");
    }

    /** The explosion is an event both presentation hosts already draw and play. */
    @Test
    void theExplosionIsSeenAndHeardWhereTheAircraftStood() {
        BattleSimulation sim = openSim();
        long airframe = parkedAircraft(sim, PAD_X, PAD_Y);

        sim.applyDamage(airframe, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        List<float[]> impacts = sim.getHeavyImpactsThisFrame();
        assertEquals(1, impacts.size(), "one heavy impact, where it stood");
        assertEquals(PAD_X + 0.5f, impacts.get(0)[0], 0.6f);
        assertEquals(PAD_Y + 0.5f, impacts.get(0)[1], 0.6f);
        assertTrue(impacts.get(0)[2] > 0f, "with a radius the FX can size itself to");
    }

    /**
     * One fire does not take the whole field. Authored hardstands sit eight
     * cells apart, and the blast deliberately stops short of the next one.
     */
    @Test
    void aBurningAircraftDoesNotTakeTheNextStandWithIt() {
        BattleSimulation sim = openSim();
        long first = parkedAircraft(sim, PAD_X, PAD_Y);
        long neighbour = parkedAircraft(sim, PAD_X + 8, PAD_Y);

        sim.applyDamage(first, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(onMap(sim, neighbour),
                "the aircraft on the next stand is still there to be burned separately");
        assertEquals(ShuttleType.AEROSHUTTLE.maxHp, sim.world().hp(neighbour), 0.01f,
                "and is not even scorched");
    }

    /** Everything else that dies on the field dies quietly. */
    @Test
    void anOrdinaryDeathDoesNotGoUp() {
        BattleSimulation sim = openSim();
        long standing = marine(sim, "standing", PAD_X, PAD_Y);
        long beside = marine(sim, "beside", PAD_X + 2, PAD_Y);

        sim.applyDamage(standing, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.getHeavyImpactsThisFrame().isEmpty(),
                "a rifleman is not a fuel tank");
        assertTrue(onMap(sim, beside));
    }
}
