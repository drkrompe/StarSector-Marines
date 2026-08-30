package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Getting an aircraft off the ground the long way: out of its shed, down the
 * taxiway, onto the strip, and along it.
 *
 * <p>Driven by setting the phase and advancing the simulation, the way the
 * other shuttle-lifecycle tests do. The question here is whether each phase
 * hands to the next on the right condition — not whether a whole sortie can be
 * dispatched, which is a wiring question and belongs to whatever dispatches
 * one.
 */
class RunwayProcedureTest {

    private static final int W = 60;
    private static final int H = 40;
    /** A strip along the south edge, thirty cells of it. */
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);
    /** A shed at the back of the base, well behind the strip. */
    private static final float SHELTER_X = 25.5f;
    private static final float SHELTER_Y = 20.5f;

    private static BattleSimulation openSimulation() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        // Nobody is on the ground here, and a battle with one side present is a
        // decided battle — which stops ticking everything, aircraft included.
        // The question is about a procedure, not about who wins.
        sim.setMissionCompletionEnabled(false);
        sim.getAirfieldService().installRunway(STRIP);
        return sim;
    }

    /** A craft standing in its shed, told to fly to {@code (toX, toY)}. */
    private static long inTheShed(BattleSimulation sim, float toX, float toY) {
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                toX, toY, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, toX, toY);
        sim.world().kinematics(craft).teleport(SHELTER_X, SHELTER_Y, 0f);
        return craft;
    }

    private static void advance(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    /**
     * Ticks until {@code mission} leaves {@code phase}, and answers where it
     * went.
     *
     * <p>Waited for rather than counted out. A fixed number of ticks either
     * stops short of the handoff or runs past it into the rest of the sortie —
     * and the rest of the sortie ends with the craft flown, delivered and
     * reaped, which reads as "the procedure never happened" when what actually
     * happened is that it finished.
     */
    private static ShuttleState leaving(BattleSimulation sim, ShuttleMission mission,
                                        ShuttleState phase) {
        for (int i = 0; i < 3000 && mission.state == phase; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        return mission.state;
    }

    /**
     * The whole way out: shed, threshold, strip, air.
     *
     * <p>One assertion per handoff rather than only on the end state, because
     * a procedure that skipped straight to flying would satisfy "it took off"
     * and be exactly the bug worth catching.
     */
    @Test
    void anAircraftTaxisToTheThresholdAndRollsBeforeItFlies() {
        try (BattleSimulation sim = openSimulation()) {
            // Going east, so the roll should run east and start from the west end.
            long craft = inTheShed(sim, 55.5f, 30.5f);
            ShuttleMission mission = sim.world().mission(craft);
            assertEquals(ShuttleState.TAXI_OUT, mission.state);
            assertEquals(10.5f, mission.holdX, 1e-3f, "started from the wrong end");
            assertEquals(40.5f, mission.rollX, 1e-3f, "rolling the wrong way");

            // Out of the shed and down to the threshold. It is on the ground
            // the whole way, which is the point of making it roll at all.
            for (int i = 0; i < 3000 && mission.state == ShuttleState.TAXI_OUT; i++) {
                sim.advance(BattleSimulation.TICK_DT);
                assertEquals(0f, sim.world().altitudeT(craft), 1e-4f,
                        "airborne while taxiing");
            }
            assertEquals(ShuttleState.HOLDING_SHORT, mission.state,
                    "never reached the threshold");
            assertTrue(sim.world().kinematics(craft).distanceTo(10.5f, 6.5f) < 2f,
                    "held short somewhere that is not the threshold");

            // The strip is free, so the next tick takes it.
            advance(sim, 1);
            assertEquals(ShuttleState.TAKEOFF_ROLL, mission.state);
            assertEquals(craft, sim.getAirfieldService().runwayOccupant(),
                    "rolling without holding the strip");
            assertTrue(sim.world().altitudeT(craft) < 0.2f,
                    "airborne at the start of its own roll");

            assertEquals(ShuttleState.INCOMING, leaving(sim, mission, ShuttleState.TAKEOFF_ROLL),
                    "never got airborne");
            assertFalse(sim.getAirfieldService().runwayBusy(),
                    "left the strip claimed behind it");
        }
    }

    /**
     * The second aircraft waits.
     *
     * <p>A single strip is what makes a base a queue, and the queue is the
     * thing an attacker standing on the taxiway is interrupting.
     */
    @Test
    void aSecondAircraftHoldsShortWhileTheStripIsInUse() {
        try (BattleSimulation sim = openSimulation()) {
            long first = inTheShed(sim, 55.5f, 30.5f);
            long second = inTheShed(sim, 55.5f, 30.5f);
            ShuttleMission firstMission = sim.world().mission(first);
            ShuttleMission secondMission = sim.world().mission(second);
            firstMission.state = ShuttleState.HOLDING_SHORT;
            secondMission.state = ShuttleState.HOLDING_SHORT;

            advance(sim, 1);

            assertEquals(ShuttleState.TAKEOFF_ROLL, firstMission.state);
            assertEquals(ShuttleState.HOLDING_SHORT, secondMission.state,
                    "both aircraft took the same strip");
            assertEquals(first, sim.getAirfieldService().runwayOccupant());

            // Once the first is away, the second goes.
            assertEquals(ShuttleState.TAKEOFF_ROLL,
                    leaving(sim, secondMission, ShuttleState.HOLDING_SHORT),
                    "the strip never came free");
        }
    }

    /**
     * Coming home: down on the strip, along it, and back into the shed.
     *
     * <p>The craft holds the runway for the rollout, because it is standing on
     * it, and gives it up the moment it turns off.
     */
    @Test
    void anAircraftRollsOutAndTaxisBackToItsShed() {
        try (BattleSimulation sim = openSimulation()) {
            long craft = inTheShed(sim, 55.5f, 30.5f);
            ShuttleMission mission = sim.world().mission(craft);
            // Arriving from the east, so it touches down on the east threshold
            // and rolls out to the west one.
            float[] touchdown = STRIP.touchdownThreshold(55.5f, 30.5f);
            assertEquals(40.5f, touchdown[0], 1e-3f, "landed into the wrong end");
            mission.landOnRunway(STRIP, 55.5f, 30.5f, SHELTER_X, SHELTER_Y);
            assertEquals(10.5f, mission.holdX, 1e-3f, "rolled out the wrong way");

            sim.world().kinematics(craft).teleport(touchdown[0], touchdown[1], 0f);
            sim.getAirfieldService().claimRunway(craft);
            mission.state = ShuttleState.LANDING_ROLL;

            for (int i = 0; i < 3000 && mission.state == ShuttleState.LANDING_ROLL; i++) {
                assertEquals(craft, sim.getAirfieldService().runwayOccupant(),
                        "on the strip without holding it");
                sim.advance(BattleSimulation.TICK_DT);
            }
            assertEquals(ShuttleState.TAXI_IN, mission.state, "never rolled out");
            assertFalse(sim.getAirfieldService().runwayBusy(),
                    "still holding the strip after turning off it");

            // Where it stops matters, so measure before the craft is reaped.
            float stoppedX = SHELTER_X + 99f;
            float stoppedY = SHELTER_Y + 99f;
            for (int i = 0; i < 3000 && mission.state == ShuttleState.TAXI_IN; i++) {
                stoppedX = sim.world().kinematics(craft).x;
                stoppedY = sim.world().kinematics(craft).y;
                sim.advance(BattleSimulation.TICK_DT);
            }
            assertEquals(ShuttleState.GONE, mission.state, "never got back to the shed");
            assertTrue(Math.hypot(stoppedX - SHELTER_X, stoppedY - SHELTER_Y) < 3f,
                    "shut down at (" + stoppedX + "," + stoppedY + ") not at its shed");
        }
    }
}
