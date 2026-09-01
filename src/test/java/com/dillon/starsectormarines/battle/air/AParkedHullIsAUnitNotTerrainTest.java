package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aircraft standing on a berth is a unit, and only a unit.
 *
 * <p>It used to be two things at once: a unit, and a 3x3 patch of navigation
 * grid stamped around it with an opaque centre and a packed mask remembering
 * what to put back. That second copy had to be handed back exactly, by every
 * ending a berth has — launch, burn, loss over the objective, and whichever
 * ending nobody has written yet — and every way of getting it wrong was silent:
 * a stand that never closed was an apron with pictures on it, and a claim
 * outliving its hull was an invisible wall on the concrete for the rest of the
 * battle.
 *
 * <p>These pin that it is gone: the concrete under and around a parked hull is
 * ordinary concrete, at every point in the berth's life. What the aircraft
 * still is — something on the field that can be seen, shot and killed — it is
 * by being a unit.
 */
class AParkedHullIsAUnitNotTerrainTest {

    private static final int W = 20;
    private static final int H = 20;
    private static final int PAD_X = 10;
    private static final int PAD_Y = 10;
    private static final float DT = 1f / 30f;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static AirfieldService.Berth berth(BattleSimulation sim) {
        return sim.getAirfieldService().addBerth(
                LandingPad.garrison(PAD_X, PAD_Y, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
    }

    /** An aircraft arriving on its stand writes no terrain at all. */
    @Test
    void anAircraftOnItsStandLeavesTheConcreteAsItFoundIt() {
        BattleSimulation sim = openSim();
        berth(sim);
        NavigationGrid grid = sim.getGrid();

        new AirfieldSystem().tick(DT, sim, sim.getAirfieldService());

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                assertTrue(grid.isWalkable(PAD_X + dx, PAD_Y + dy),
                        "the hull closed the apron at " + dx + "," + dy);
                assertFalse(grid.blocksLineOfSight(PAD_X + dx, PAD_Y + dy),
                        "the hull blinded the apron at " + dx + "," + dy);
            }
        }
    }

    /** What it is instead: a live unit on the pad, with hull to shoot off. */
    @Test
    void whatStandsOnTheStandIsAUnit() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim);

        new AirfieldSystem().tick(DT, sim, sim.getAirfieldService());

        assertNotEquals(0L, berth.airframeId, "the stand produced no aircraft");
        assertTrue(sim.world().isAlive(berth.airframeId));
        assertEquals(PAD_X, sim.world().cellX(berth.airframeId));
        assertEquals(PAD_Y, sim.world().cellY(berth.airframeId));
        assertTrue(sim.world().hp(berth.airframeId) > 0f);
    }

    /**
     * The one thing the stamp genuinely carried, kept: an arriving hull moves
     * whoever is under its wheels.
     *
     * <p>Not this class's rule any more — it is the ordinary rule for any
     * immobile arrival, the same one that seats a turret on its mount. Pinned
     * here because a berth is one of the callers that depends on it, and
     * because the alternative to moving them is two bodies in one cell.
     */
    @Test
    void anArrivingHullMovesWhoeverIsUnderIt() {
        BattleSimulation sim = openSim();
        berth(sim);
        long onTheStand = sim.spawn(
                new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, PAD_X, PAD_Y));
        long besideIt = sim.spawn(
                new EntitySpec("m1", Faction.MARINE, UnitType.MARINE, PAD_X + 1, PAD_Y));

        new AirfieldSystem().tick(DT, sim, sim.getAirfieldService());

        assertNotEquals(PAD_X + "," + PAD_Y,
                sim.world().cellX(onTheStand) + "," + sim.world().cellY(onTheStand),
                "the aircraft and the marine are standing in the same cell");
        assertTrue(sim.getGrid().isWalkable(sim.world().cellX(onTheStand),
                        sim.world().cellY(onTheStand)),
                "sealed into a cell they can never leave");

        // And nobody else. A berth places at the start of the battle and again
        // on every completed refit, so shoving whoever is merely near a pad
        // would be a free repeatable push for the side that owns the field.
        assertEquals(PAD_X + 1, sim.world().cellX(besideIt), "the hull shoved a bystander");
        assertEquals(PAD_Y, sim.world().cellY(besideIt));
    }

    /**
     * A rifle team on the apron can hit the aircraft, which is the entire
     * reason walking onto an apron is worth a fire team's time.
     *
     * <p>Was a real defect while the hull stamped itself opaque: a sight line
     * exempts its two endpoints and nothing else, so a hull opaque across its
     * own footprint is a hull no round can reach, and the raid on the field
     * went from three aircraft burned in five seconds to none in three minutes.
     * It cannot recur, because the hull writes nothing to be opaque in.
     */
    @Test
    void aRifleTeamOnTheApronCanStillHitTheAircraft() {
        BattleSimulation sim = openSim();
        berth(sim);
        new AirfieldSystem().tick(DT, sim, sim.getAirfieldService());

        for (int dx = -4; dx <= 4; dx++) {
            assertTrue(sim.getGrid().hasLineOfSight(PAD_X + dx, PAD_Y - 4, PAD_X, PAD_Y),
                    "the aircraft shielded itself from a shot at " + dx);
        }
    }

    /**
     * A stand an aircraft has left, and one it has come back to, are the same
     * concrete as one it is standing on.
     *
     * <p>Asserted across the whole cycle rather than at one moment, because the
     * failure this replaces was never visible when it happened: a claim that
     * outlived its hull looked like nothing at all until somebody tried to walk
     * there, and the berth said it was away the whole time.
     */
    @Test
    void theStandIsOrdinaryConcreteThroughTheWholeCycle() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem();

        system.tick(DT, sim, airfield);
        assertOpen(sim, "with the aircraft on it");

        airfield.launch(berth);
        for (int i = 0; i < 60; i++) system.tick(DT, sim, airfield);
        assertOpen(sim, "with the sortie away");

        airfield.recover(berth, ShuttleType.AEROSHUTTLE.maxHp());
        system.tick(DT, sim, airfield);
        assertEquals(AirfieldService.BerthState.REFITTING, berth.state);
        assertOpen(sim, "with the aircraft back and being worked on");
    }

    /**
     * An aircraft lost over the objective leaves an empty stand, and empty
     * means empty. No wreck comes down on this one.
     */
    @Test
    void anAircraftLostAwayFromItsStandLeavesNothingOnIt() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem();
        system.tick(DT, sim, airfield);
        airfield.launch(berth);
        airfield.destroyed(berth);

        system.tick(DT, sim, airfield);

        assertEquals(AirfieldService.BerthState.DESTROYED, berth.state);
        assertOpen(sim, "after the aircraft was lost");
    }

    private static void assertOpen(BattleSimulation sim, String when) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertTrue(sim.getGrid().isWalkable(PAD_X + dx, PAD_Y + dy),
                        "an invisible wall at " + dx + "," + dy + " " + when);
                assertFalse(sim.getGrid().blocksLineOfSight(PAD_X + dx, PAD_Y + dy),
                        "something to see round at " + dx + "," + dy + " " + when);
            }
        }
    }
}
