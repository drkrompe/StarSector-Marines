package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a round ends up, asked of the delivery itself.
 *
 * <p>{@code OrdnancePatternTest} flies the battle and looks at the ground; this
 * asks the arithmetic underneath it, because the three classes are supposed to
 * differ for a physical reason and a footprint cannot say which reason. The
 * claims are: a gun is a laser pointer, a bomb absorbs the aircraft's momentum
 * and falls behind it, and a missile has its own motor and reaches further than
 * either.
 */
class OrdnanceDeliveryTest {

    /**
     * A representative attack-run speed, cells/sec: what a Broadsword flies at
     * under the current atmosphere calibration.
     *
     * <p>Has to track that calibration rather than sit at a number nothing
     * flies at any more. Two of the three delivery classes do not care what it
     * is — a shell and a missile get their reach from their own motor — but a
     * bomb takes all of its forward reach from the aircraft, so measuring the
     * bomb against a stale speed is measuring an aircraft that does not exist.
     */
    private static final float CRAFT_SPEED = 19.8f;

    private static String describe(String name, AirOrdnance load) {
        OrdnanceFlight flight = load.flight;
        return String.format(
                "[delivery] %-10s flight %.3fs, lead %.2f cells, %s the aircraft by %.2f,"
                        + " firing range %.0f, lead/range %.2f",
                name, flight.flightTimeSec(), flight.leadCells(CRAFT_SPEED),
                flight.trailCells(CRAFT_SPEED) > 0f ? "behind" : "ahead of",
                Math.abs(flight.trailCells(CRAFT_SPEED)),
                load.firingRangeCells,
                flight.leadCells(CRAFT_SPEED) / load.firingRangeCells);
    }

    /**
     * A gun round is on the ground before the aircraft has gone anywhere, and
     * it lands where the nose was pointed at the ground rather than underneath.
     */
    @Test
    void aGunIsALaserPointer() {
        System.out.println(describe("cannon", AirOrdnance.AUTOCANNON));
        System.out.println(describe("beam", AirOrdnance.BEAM));

        for (AirOrdnance load : new AirOrdnance[]{ AirOrdnance.AUTOCANNON, AirOrdnance.BEAM }) {
            float flight = load.flight.flightTimeSec();
            assertTrue(flight < 0.05f,
                    "a gun round spent " + flight + "s in the air, which is not instant");
            // What the aircraft covers while the round is out there is a few
            // per cent of where the round ends up, so the impact is sight
            // geometry and nothing else. Held against the reach rather than in
            // cells, because both sides of it move with the airspeed and only
            // the ratio is the claim.
            float carried = CRAFT_SPEED * flight;
            assertTrue(carried < 0.1f * load.flight.leadCells(CRAFT_SPEED),
                    "the aircraft moved " + carried + " cells during a round's flight,"
                            + " against a reach of " + load.flight.leadCells(CRAFT_SPEED)
                            + " — the impact is the aircraft's motion, not its sight line");
            assertTrue(load.flight.leadCells(CRAFT_SPEED) > 10f,
                    "the strafe lands only " + load.flight.leadCells(CRAFT_SPEED)
                            + " cells ahead, which is under the aircraft rather than out in front");
        }
    }

    /**
     * A bomb keeps most of the aircraft's speed and none of its thrust, so the
     * aircraft outruns it during the fall and the impact is behind the machine
     * that dropped it. Every powered round is the other way round.
     */
    @Test
    void aBombFallsBehindTheAircraftAndEverythingElseStaysAhead() {
        System.out.println(describe("bombs", AirOrdnance.BOMBS));

        float trail = AirOrdnance.BOMBS.flight.trailCells(CRAFT_SPEED);
        assertTrue(trail > 0f,
                "the stick landed " + (-trail) + " cells in front of the bomber");
        assertTrue(AirOrdnance.BOMBS.flight.flightTimeSec() > 1f,
                "a bomb fell for only " + AirOrdnance.BOMBS.flight.flightTimeSec() + "s");

        for (AirOrdnance load : new AirOrdnance[]{
                AirOrdnance.AUTOCANNON, AirOrdnance.BEAM, AirOrdnance.MISSILES }) {
            assertTrue(load.flight.trailCells(CRAFT_SPEED) < 0f,
                    "a powered round landed behind the aircraft");
        }
    }

    /**
     * A missile has a motor, so it is out there for a real fraction of a second
     * and it goes further than a sight line does — which is what lets a missile
     * boat release without ever coming over the position.
     */
    @Test
    void aMissileReachesFurtherThanAGunOrABomb() {
        System.out.println(describe("missiles", AirOrdnance.MISSILES));

        float missile = AirOrdnance.MISSILES.flight.leadCells(CRAFT_SPEED);
        assertTrue(missile > AirOrdnance.AUTOCANNON.flight.leadCells(CRAFT_SPEED),
                "the missile reached " + missile + " cells, no further than the gun");
        assertTrue(missile > AirOrdnance.BOMBS.flight.leadCells(CRAFT_SPEED),
                "the missile reached no further than a bomb falls forward");
        assertTrue(AirOrdnance.MISSILES.firingRangeCells > AirOrdnance.AUTOCANNON.firingRangeCells,
                "the missile pod released from no further out than the gun does");
        // Real flight, but a motor rather than a fall.
        float flight = AirOrdnance.MISSILES.flight.flightTimeSec();
        assertTrue(flight > 0.1f && flight < AirOrdnance.BOMBS.flight.flightTimeSec(),
                "the missile's " + flight + "s flight is neither a shell's nor a bomb's");
    }

    /**
     * Reach and firing range are one decision, not two.
     *
     * <p>Rounds land from {@code range - lead} short of the target to about
     * {@code lead} past it as the craft closes, so a lead far under half the
     * range drops the whole burst short — the fault that made a strafe read as
     * fire landing under the aircraft — and one far over it throws the whole
     * burst long. Every class is held to the same relation, whatever it
     * delivers.
     */
    @Test
    void everyClassLeadsAboutHalfItsOwnFiringRange() {
        AirOrdnance[] all = { AirOrdnance.AUTOCANNON, AirOrdnance.BEAM,
                AirOrdnance.MISSILES, AirOrdnance.BOMBS };
        for (AirOrdnance load : all) {
            float ratio = load.flight.leadCells(CRAFT_SPEED) / load.firingRangeCells;
            assertTrue(ratio > 0.35f && ratio < 0.65f,
                    "fire would sweep " + ratio + " of the way through the target,"
                            + " so the burst lands entirely short or entirely long");
        }
    }

    /**
     * A finite load lasts long enough to cover the position it was released
     * against.
     *
     * <p>A gun stops when the target passes off the nose, so its reach alone
     * decides where its burst ends. A pod or a bomb bay stops when it is empty,
     * and an early-emptying load puts every round short however good the reach
     * is — measured once at four missiles a second, which finished eight cells
     * in front of the position with the whole stick wasted. The release window
     * has to carry the aircraft across the gap between its standoff and its
     * reach.
     */
    @Test
    void aFiniteLoadOutlastsTheGapBetweenItsStandoffAndItsReach() {
        for (AirOrdnance load : new AirOrdnance[]{ AirOrdnance.MISSILES, AirOrdnance.BOMBS }) {
            float window = load.roundsPerPass / load.roundsPerSecond;
            float covered = load.flight.leadCells(CRAFT_SPEED) + CRAFT_SPEED * window;
            assertTrue(covered > load.firingRangeCells,
                    "the load was gone " + (load.firingRangeCells - covered)
                            + " cells short of the target");
        }
    }

    /** The nose is where a round goes, and the compass convention is honoured. */
    @Test
    void aRoundGoesWhereTheNoseIsPointed() {
        // facing + 90 is the nose in map degrees, so -90 points down +X.
        OrdnanceFlight.Impact east = AirOrdnance.AUTOCANNON.flight.deliver(
                20f, 20f, -90f, CRAFT_SPEED, 0f);
        assertEquals(20f, east.y(), 0.01f, "a round fired down +X drifted off the axis");
        assertEquals(20f + AirOrdnance.AUTOCANNON.flight.leadCells(CRAFT_SPEED), east.x(), 0.05f,
                "the impact was not the reach the flight model claims");

        OrdnanceFlight.Impact north = AirOrdnance.AUTOCANNON.flight.deliver(
                20f, 20f, 0f, 0f, CRAFT_SPEED);
        assertEquals(20f, north.x(), 0.01f, "a round fired down +Y drifted off the axis");
        assertTrue(north.y() > 20f, "a round fired down +Y landed behind the aircraft");
    }
}
