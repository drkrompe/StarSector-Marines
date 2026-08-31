package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aircraft arrives under its own power, and nothing puts it down.
 *
 * <p>Both landings used to end in a {@code teleport}: a runway one was placed
 * on the centreline pointing along it, and a pad one was placed on its landing
 * zone. A teleport is invisible in a state machine and glaring on screen — the
 * craft stops being where it was and stops moving, in one frame — and it is
 * exactly what the owner reported as a weird handoff from flying to landing.
 *
 * <p>So the property is kinematic rather than about phases: across the whole
 * arrival, the craft never moves further in a tick than its speed allows and
 * never loses more speed in a tick than its brakes allow. A teleport breaks
 * both at once.
 */
class LandingIsFlownTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);
    private static final float SHELTER_X = 25.5f;
    private static final float SHELTER_Y = 20.5f;

    /**
     * How much over the profile's own limits a single tick is allowed.
     *
     * <p>Slack for the tick the phase changes on, where the wheeled model
     * recomposes the velocity it inherited. Nowhere near enough to hide a
     * placement: the runway teleport moved the craft more than a cell and took
     * its whole cruise speed off in the same frame.
     */
    private static final float TOLERANCE = 1.6f;

    private static BattleSimulation openSimulation() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** The worst single-tick position jump and speed loss across an arrival. */
    private record Continuity(float jumpCells, float decelCellsPerSec2,
                              EnumSet<ShuttleState> seen) {}

    /**
     * Flies {@code craft} until it stops being airborne, watching every tick it
     * starts in the air — the transition tick included, which is the one a
     * takeover would have hidden in.
     */
    private static Continuity flyItDown(BattleSimulation sim, long craft, int maxTicks) {
        AirBody body = sim.world().kinematics(craft);
        ShuttleMission mission = sim.world().mission(craft);
        EnumSet<ShuttleState> seen = EnumSet.noneOf(ShuttleState.class);
        float worstJump = 0f;
        float worstDecel = 0f;
        boolean everAirborne = false;
        for (int i = 0; i < maxTicks; i++) {
            boolean startedAirborne = AirLocomotion.of(mission.state).airborne();
            everAirborne |= startedAirborne;
            float wasX = body.x;
            float wasY = body.y;
            float wasSpeed = body.speed();
            seen.add(mission.state);
            sim.advance(BattleSimulation.TICK_DT);
            if (!startedAirborne) {
                if (everAirborne) break;
                continue;
            }
            worstJump = Math.max(worstJump, (float) Math.hypot(body.x - wasX, body.y - wasY));
            worstDecel = Math.max(worstDecel,
                    (wasSpeed - body.speed()) / BattleSimulation.TICK_DT);
        }
        return new Continuity(worstJump, worstDecel, seen);
    }

    /**
     * A vertical lift settles onto its pad instead of arriving on it.
     *
     * <p>The Huey shape: brake out of the run in, hold over the spot, and sink.
     * The settle is a phase of its own because it is a different way of moving
     * — and because the arrival it replaces was one tick long.
     *
     * <p>Run across every {@link ShuttleType} rather than just the Aeroshuttle
     * this test used to fly alone. The settle used to end on a stated
     * duration regardless of whether the craft had actually stopped, and a
     * bus-tier hull — a Buffalo, a Mule — brakes at a third of the Aeroshuttle's
     * rate: measured, that duration expired with the bus still doing several
     * cells a second and it was snapped to a stop several cells short of the
     * pad, shedding well over a hundred cells/sec² in the one tick the clock
     * ran out on. The nimble tier passed this test the whole time; only the
     * bus tier proves the fix.
     */
    @ParameterizedTest
    @EnumSource(ShuttleType.class)
    void aShuttleSettlesOntoItsPadRatherThanArrivingOnIt(ShuttleType type) {
        try (BattleSimulation sim = openSimulation()) {
            long craft = sim.spawnShuttle(type, Faction.DEFENDER,
                    30.5f, 20.5f, -6f, 20.5f, -6f, 20.5f, 0f, 1);
            Continuity flown = flyItDown(sim, craft, 8000);

            assertTrue(flown.seen().contains(ShuttleState.PAD_DESCENT),
                    type + " arrived on the pad without settling onto it: " + flown.seen());
            assertTrue(flown.seen().contains(ShuttleState.LANDED),
                    type + " never got down: " + flown.seen());

            AirHandling flight = type;
            assertTrue(flown.jumpCells()
                            <= flight.maxSpeed() * BattleSimulation.TICK_DT * TOLERANCE,
                    type + " moved " + flown.jumpCells() + " cells in a tick, which is further "
                            + "than it can fly — something placed it");
            assertTrue(flown.decelCellsPerSec2() <= flight.brakingAccel() * TOLERANCE,
                    type + " shed " + flown.decelCellsPerSec2() + " cells/sec^2 in a tick, which "
                            + "is harder than it can brake — something stopped it");
        }
    }

    /**
     * An aircraft coming home flies itself onto the strip.
     *
     * <p>The same property at the other kind of field, and the one the teleport
     * was doing the most work in: the craft was put on the centreline pointing
     * along it and its speed was zeroed, so a rollout began from a standstill
     * at the threshold. Flown, it crosses the threshold at approach speed and
     * the wheels bleed that off down the runway, which is what a landing is.
     */
    @Test
    void anAircraftComingHomeFliesItselfOntoTheStrip() {
        try (BattleSimulation sim = openSimulation()) {
            AirfieldService airfield = sim.getAirfieldService();
            airfield.installRunway(STRIP);
            AirfieldService.Berth shed = airfield.addShelterBerth(
                    new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                    ShuttleType.AEROSHUTTLE);
            long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                    52.5f, 32.5f, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f, 1);
            ShuttleMission mission = sim.world().mission(craft);
            mission.homeBerth = shed;
            mission.usesRunway = true;
            mission.shelterX = SHELTER_X;
            mission.shelterY = SHELTER_Y;
            sim.world().setHp(craft, airfield.launch(shed));
            sim.world().kinematics(craft).teleport(52.5f, 32.5f, 0f);
            mission.state = ShuttleState.LANDED;
            mission.marinesRemaining = 0;

            Continuity flown = flyItDown(sim, craft, 6000);

            assertTrue(flown.seen().contains(ShuttleState.RETURNING),
                    "never flew an approach: " + flown.seen());
            assertTrue(flown.seen().contains(ShuttleState.LANDING_ROLL),
                    "never got down on the strip: " + flown.seen());

            AirHandling flight = ShuttleType.AEROSHUTTLE;
            assertTrue(flown.jumpCells()
                            <= flight.maxSpeed() * BattleSimulation.TICK_DT * TOLERANCE,
                    "moved " + flown.jumpCells() + " cells in a tick on approach — "
                            + "something placed it on the strip");
            assertTrue(flown.decelCellsPerSec2() <= flight.brakingAccel() * TOLERANCE,
                    "shed " + flown.decelCellsPerSec2() + " cells/sec^2 crossing the threshold — "
                            + "it was stopped rather than landed");

            // And it is still rolling when the wheels take over: a landing that
            // arrives at a standstill is a placement however smooth.
            assertTrue(sim.world().kinematics(craft).speed() > 1f,
                    "touched down stationary");
        }
    }
}
