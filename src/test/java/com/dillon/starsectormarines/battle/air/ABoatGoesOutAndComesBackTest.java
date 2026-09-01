package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ship's boat leaves her bay and comes back to it, as a flight.
 *
 * <p>The alternative was a countdown: empty the berth for a while, put a boat
 * back looking serviced, and from a deck screen the two are very nearly
 * indistinguishable. What a countdown is not is the same machinery — the berth
 * would empty without an aircraft existing, the hull that came home would not be
 * the hull that left, a boat could not be lost, and every consequence a garrison
 * field already has would have to be written a second time for ships.
 *
 * <p>So these ask for the flight rather than for the effect: an air entity, a
 * berth that genuinely empties, and a berth that comes back into a turnaround
 * because something landed on it.
 */
class ABoatGoesOutAndComesBackTest {

    private static final int W = 60;
    private static final int H = 60;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        // A deck is not a mission: left alone the sim wins on the first tick for
        // want of an enemy and stops advancing, and nothing here is about who
        // wins.
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static AirfieldService bayWithOneBoat(BattleSimulation sim) {
        AirfieldService bay = sim.getAirfieldService();
        bay.setOwner(Faction.MARINE);
        bay.addBayBerth(new Gantry(20, 30, 3, 2, Gantry.Facing.SOUTH, Gantry.Holds.BOAT),
                ShuttleType.AEROSHUTTLE, new float[]{20.5f, 4f});
        return bay;
    }

    private static void run(BattleSimulation sim, float seconds) {
        for (int tick = 0; tick < seconds * 20; tick++) sim.advance(1f / 20f);
    }

    /**
     * The berth empties, and what left it is an aircraft.
     *
     * <p>Watched across the window rather than sampled at the end of it. A boat
     * is out for about half a minute and the first version of this asked at a
     * fixed instant, which is a test that fails when the round trip gets faster
     * — a fact about the flight rather than about whether one happened.
     */
    @Test
    void aBoatActuallyLeavesTheBay() {
        BattleSimulation sim = openSim();
        AirfieldService bay = bayWithOneBoat(sim);
        AirfieldService.Berth boat = bay.berths().get(0);

        boolean everAway = false;
        boolean everAirborne = false;
        for (int tick = 0; tick < 120 * 20; tick++) {
            sim.advance(1f / 20f);
            if (boat.state == AirfieldService.BerthState.AWAY) everAway = true;
            if (sim.getAirEntityIds().length > 0) everAirborne = true;
        }

        assertTrue(everAway, "the boat never left its stand");
        assertTrue(everAirborne,
                "the berth emptied without an aircraft existing, which is a countdown");
    }

    /**
     * And comes home to the same berth, into a turnaround. Recovered rather than
     * written off — a berth whose craft ended any other way is finished for the
     * battle, so landing on the wrong side of that distinction would quietly
     * spend the ship's boats.
     */
    @Test
    void itComesHomeIntoATurnaround() {
        BattleSimulation sim = openSim();
        AirfieldService bay = bayWithOneBoat(sim);
        AirfieldService.Berth boat = bay.berths().get(0);

        run(sim, 200f);

        assertNotEquals(AirfieldService.BerthState.DESTROYED, boat.state,
                "the boat was written off rather than recovered");
        assertTrue(boat.state == AirfieldService.BerthState.REFITTING
                        || boat.state == AirfieldService.BerthState.PARKED,
                "the boat never came home: " + boat.state);
    }

    /**
     * One at a time. A ship in company is not flying an air operation, and a bay
     * with every berth empty is a bay that cannot answer anything.
     */
    @Test
    void onlyOneBoatIsOffTheShipAtOnce() {
        BattleSimulation sim = openSim();
        AirfieldService bay = sim.getAirfieldService();
        bay.setOwner(Faction.MARINE);
        for (int index = 0; index < 4; index++) {
            bay.addBayBerth(
                    new Gantry(12 + index * 8, 30, 3, 2, Gantry.Facing.SOUTH,
                            Gantry.Holds.BOAT),
                    ShuttleType.AEROSHUTTLE, new float[]{12.5f + index * 8, 4f});
        }

        int most = 0;
        for (int tick = 0; tick < 400 * 20; tick++) {
            sim.advance(1f / 20f);
            int out = 0;
            for (AirfieldService.Berth berth : bay.berths()) {
                if (berth.state == AirfieldService.BerthState.AWAY) out++;
            }
            most = Math.max(most, out);
        }

        assertTrue(most > 0, "a bay of four boats sent none in six minutes");
        assertEquals(1, most, "the ship emptied " + most + " berths at once");
    }

    /**
     * A garrison's hardstand is left alone. An aircraft on a lot has nowhere to
     * fly that is not the battle, so the gate is having somewhere off this map
     * to be — which means this does nothing on a ground map without being told
     * which kind of place it is on.
     */
    @Test
    void aGarrisonsHardstandIsNotABoatBerth() {
        BattleSimulation sim = openSim();
        AirfieldService field = sim.getAirfieldService();
        AirfieldService.Berth pad = field.addBerth(
                LandingPad.garrison(20, 30, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);

        run(sim, 200f);

        assertNotEquals(AirfieldService.BerthState.AWAY, pad.state,
                "a garrison's aircraft was flown off the map as though it were a ship's boat");
        assertEquals(0, sim.getAirEntityIds().length,
                "an aircraft was conjured off a hardstand with nowhere to send it");
    }
}
