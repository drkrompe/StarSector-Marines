package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
     * The whole round trip, driven by nothing but the clock: out of the shed,
     * down the strip, out to the objective, home, down, and back in the shed.
     *
     * <p>The three tests above each set a phase and watch one handoff. This
     * one sets none after dispatch, which is the only way to catch the leg
     * that was missing: a craft that had rolled off a strip still flew its
     * egress to an off-map exit and was written home from there, so the
     * landing procedure existed and nothing ever entered it.
     */
    @Test
    void aSortieFlownOffTheStripComesHomeToIt() {
        try (BattleSimulation sim = openSimulation()) {
            AirfieldService airfield = sim.getAirfieldService();
            AirfieldService.Berth shed = airfield.addShelterBerth(
                    new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                    ShuttleType.AEROSHUTTLE);
            long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                    50.5f, 30.5f, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f, 1);
            ShuttleMission mission = sim.world().mission(craft);
            mission.homeBerth = shed;
            mission.hp = airfield.launch(shed);
            mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, 50.5f, 30.5f);
            sim.world().kinematics(craft).teleport(SHELTER_X, SHELTER_Y, 0f);

            // Every phase in order, each waited for rather than counted out.
            assertEquals(ShuttleState.HOLDING_SHORT, leaving(sim, mission, ShuttleState.TAXI_OUT));
            assertEquals(ShuttleState.TAKEOFF_ROLL, leaving(sim, mission, ShuttleState.HOLDING_SHORT));
            assertEquals(ShuttleState.INCOMING, leaving(sim, mission, ShuttleState.TAKEOFF_ROLL));
            assertEquals(ShuttleState.LANDED, leaving(sim, mission, ShuttleState.INCOMING));

            // The leg that was missing. One passenger down and nothing to
            // loiter with, so the craft turns for home as soon as it is empty.
            assertEquals(ShuttleState.RETURNING, leaving(sim, mission, ShuttleState.LANDED),
                    "flew off to an off-map exit instead of coming home");
            assertTrue(sim.world().kinematics(craft).distanceTo(40.5f, 6.5f) > 2f,
                    "already at the threshold before the approach started");

            assertEquals(ShuttleState.LANDING_ROLL, leaving(sim, mission, ShuttleState.RETURNING),
                    "never got down on the strip");
            assertEquals(craft, airfield.runwayOccupant(),
                    "rolling out without holding the strip");
            assertEquals(ShuttleState.TAXI_IN, leaving(sim, mission, ShuttleState.LANDING_ROLL));
            assertEquals(ShuttleState.GONE, leaving(sim, mission, ShuttleState.TAXI_IN));

            assertFalse(airfield.runwayBusy(), "left the strip claimed behind it");
            assertEquals(AirfieldService.BerthState.REFITTING, shed.state,
                    "came home and the shed does not have it back");
        }
    }

    /**
     * A fighter is an aircraft the air system can fly.
     *
     * <p>Slice 4b made a berth able to <em>keep</em> one — parked, drawn, shot
     * at, wrecked — while the air entity was still a {@code ShuttleType},
     * so the same hull could stand in a shed and not take off from it. This
     * flies a Broadsword through the whole ground procedure to show the two
     * halves now meet.
     *
     * <p>Its handling comes off its own hull spec rather than an authored
     * tier, which with no game to read a spec out of resolves to the flyable
     * fallback — that is the point of the fallback, and the procedure has to
     * run on it.
     */
    @Test
    void aFighterFliesTheSameProcedureAsATransport() {
        try (BattleSimulation sim = openSimulation()) {
            AirfieldService airfield = sim.getAirfieldService();
            AirfieldService.Berth shed = airfield.addShelterBerth(
                    new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                    FighterProfile.BROADSWORD);
            long fighter = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                    50.5f, 30.5f, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f);
            ShuttleMission mission = sim.world().mission(fighter);
            mission.homeBerth = shed;
            mission.hp = airfield.launch(shed);
            mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, 50.5f, 30.5f);
            sim.world().kinematics(fighter).teleport(SHELTER_X, SHELTER_Y, 0f);

            assertSame(FighterProfile.BROADSWORD, sim.world().airframe(fighter),
                    "the air entity is not the airframe it was launched as");

            assertEquals(ShuttleState.HOLDING_SHORT, leaving(sim, mission, ShuttleState.TAXI_OUT));
            assertEquals(ShuttleState.TAKEOFF_ROLL, leaving(sim, mission, ShuttleState.HOLDING_SHORT));
            assertEquals(ShuttleState.INCOMING, leaving(sim, mission, ShuttleState.TAKEOFF_ROLL),
                    "a fighter never got off the strip");
            assertEquals(ShuttleState.LANDED, leaving(sim, mission, ShuttleState.INCOMING));
            assertEquals(ShuttleState.RETURNING, leaving(sim, mission, ShuttleState.LANDED));
            assertEquals(ShuttleState.LANDING_ROLL, leaving(sim, mission, ShuttleState.RETURNING));
            assertEquals(ShuttleState.TAXI_IN, leaving(sim, mission, ShuttleState.LANDING_ROLL));
            assertEquals(ShuttleState.GONE, leaving(sim, mission, ShuttleState.TAXI_IN));

            assertEquals(AirfieldService.BerthState.REFITTING, shed.state,
                    "a fighter came home and its shed does not have it back");
        }
    }

    /**
     * A strike sortie works over its objective and never lands on it.
     *
     * <p>The distinction the phase exists for. A transport's business at the
     * far end is its ramp, so it touches down; an aircraft sent to attack a
     * position has no reason to put its wheels on it and every reason not to.
     * Before this, reaching the fire support went by way of a landing, so a
     * fighter sent against an objective sat on it at zero altitude for a tick
     * first — and then hovered over it, which is the other half of the same
     * mistake.
     */
    @Test
    void aStrikeSortieRunsAtItsObjectiveAndNeverLandsOnIt() {
        try (BattleSimulation sim = openSimulation()) {
            AirfieldService airfield = sim.getAirfieldService();
            AirfieldService.Berth shed = airfield.addShelterBerth(
                    new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                    FighterProfile.BROADSWORD);
            long fighter = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                    50.5f, 30.5f, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f);
            ShuttleMission mission = sim.world().mission(fighter);
            mission.homeBerth = shed;
            mission.strikeSortie = true;
            mission.hp = airfield.launch(shed);
            mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, 50.5f, 30.5f);
            sim.world().kinematics(fighter).teleport(SHELTER_X, SHELTER_Y, 0f);

            // Fly the whole thing, recording every phase and the lowest the
            // aircraft ever gets while it is out over the objective.
            EnumSet<ShuttleState> seen = EnumSet.noneOf(ShuttleState.class);
            float lowestOverTarget = 1f;
            for (int i = 0; i < 6000 && mission.state != ShuttleState.GONE; i++) {
                sim.advance(BattleSimulation.TICK_DT);
                seen.add(mission.state);
                if (mission.state == ShuttleState.ATTACK_RUN
                        || mission.state == ShuttleState.REPOSITION) {
                    lowestOverTarget = Math.min(lowestOverTarget, sim.world().altitudeT(fighter));
                }
            }

            assertTrue(seen.contains(ShuttleState.ATTACK_RUN),
                    "never made a run at the objective: " + seen);
            assertTrue(seen.contains(ShuttleState.REPOSITION),
                    "made one pass and left rather than coming round: " + seen);
            assertFalse(seen.contains(ShuttleState.LANDED),
                    "a strike aircraft touched down on its own target");
            assertTrue(lowestOverTarget > 0.5f,
                    "dropped to " + lowestOverTarget + " altitude over the objective");

            // And it is still a based aircraft: home down the strip and into
            // the shed it came out of.
            assertTrue(seen.contains(ShuttleState.RETURNING), "never turned for home: " + seen);
            assertTrue(seen.contains(ShuttleState.LANDING_ROLL), "never got down: " + seen);
            assertTrue(seen.contains(ShuttleState.TAXI_IN), "never left the strip: " + seen);
            assertEquals(AirfieldService.BerthState.REFITTING, shed.state,
                    "flew a strike and the shed does not have it back");
        }
    }

    /**
     * A landing aircraft is lined up with the strip before it touches down,
     * whichever direction it came home from.
     *
     * <p>The approach is two legs: out to the extended centreline, then down
     * it. That second leg is the runway axis, so the aircraft is straight by
     * the time it reaches the threshold without anybody testing its heading —
     * and the touchdown itself is a takeover rather than a manoeuvre, because
     * asking the steering to brake a flying body onto a point left it arriving
     * crabbed and sorting itself out on the runway.
     */
    @Test
    void aLandingAircraftIsLinedUpWithTheStripBeforeItTouchesDown() {
        // Coming home from three very different directions, including one that
        // has to fly right past the field to get onto its centreline.
        float[][] fromWhere = { {55.5f, 30.5f}, {3.5f, 33.5f}, {25.5f, 35.5f} };
        for (float[] from : fromWhere) {
            try (BattleSimulation sim = openSimulation()) {
                AirfieldService airfield = sim.getAirfieldService();
                AirfieldService.Berth shed = airfield.addShelterBerth(
                        new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                        ShuttleType.AEROSHUTTLE);
                long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                        from[0], from[1], SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f, 1);
                ShuttleMission mission = sim.world().mission(craft);
                mission.homeBerth = shed;
                mission.usesRunway = true;
                mission.shelterX = SHELTER_X;
                mission.shelterY = SHELTER_Y;
                mission.hp = airfield.launch(shed);
                sim.world().kinematics(craft).teleport(from[0], from[1], 0f);
                mission.state = ShuttleState.LANDED;
                mission.marinesRemaining = 0;

                assertEquals(ShuttleState.RETURNING, leaving(sim, mission, ShuttleState.LANDED),
                        "never turned for home from " + from[0] + "," + from[1]);
                assertEquals(ShuttleState.LANDING_ROLL,
                        leaving(sim, mission, ShuttleState.RETURNING),
                        "never got down from " + from[0] + "," + from[1]);

                // Down on the strip and pointing along it, whichever way it
                // came home. Asserted by rolling it rather than by reading its
                // heading: what "lined up" means is that the aircraft runs
                // down the runway instead of across it, and an angle in
                // isolation only restates whichever convention the code used.
                AirBody body = sim.world().kinematics(craft);
                assertEquals(6.5f, body.y, 0.6f, "touched down off the centreline");
                float startedFromHold = body.distanceTo(mission.holdX, mission.holdY);
                float worstDrift = 0f;
                for (int i = 0; i < 60; i++) {
                    sim.advance(BattleSimulation.TICK_DT);
                    worstDrift = Math.max(worstDrift, Math.abs(body.y - 6.5f));
                }
                assertTrue(body.distanceTo(mission.holdX, mission.holdY) < startedFromHold - 1f,
                        "from " + from[0] + "," + from[1] + " it did not roll out down the strip");
                assertTrue(worstDrift < 1.5f,
                        "from " + from[0] + "," + from[1] + " it wandered "
                                + worstDrift + " cells off the centreline");
            }
        }
    }

    /**
     * An aircraft on approach does not land on a strip somebody else is using.
     */
    @Test
    void aReturningAircraftWaitsForTheStrip() {
        try (BattleSimulation sim = openSimulation()) {
            AirfieldService airfield = sim.getAirfieldService();
            long departing = inTheShed(sim, 55.5f, 30.5f);
            long homebound = inTheShed(sim, 55.5f, 30.5f);
            ShuttleMission returning = sim.world().mission(homebound);
            returning.landOnRunway(STRIP, 55.5f, 30.5f, SHELTER_X, SHELTER_Y);
            float[] touchdown = STRIP.touchdownThreshold(55.5f, 30.5f);
            returning.exitX = touchdown[0];
            returning.exitY = touchdown[1];
            returning.state = ShuttleState.RETURNING;
            sim.world().kinematics(homebound).teleport(touchdown[0], touchdown[1], 0f);
            // Somebody else already has it.
            airfield.claimRunway(departing);

            advance(sim, 60);

            assertEquals(ShuttleState.RETURNING, returning.state,
                    "landed on an occupied strip");
            assertEquals(departing, airfield.runwayOccupant());

            // Once the strip is free it goes down.
            airfield.releaseRunway(departing);
            assertEquals(ShuttleState.LANDING_ROLL,
                    leaving(sim, returning, ShuttleState.RETURNING),
                    "the strip came free and nothing landed on it");
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
