package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.Runway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which end of the strip a sortie takes off from.
 *
 * <p>Asked of {@link Runway} directly. It sits beside the rest of the runway
 * procedure's tests because the choice is Air's rather than the lot's: the lot
 * lays a strip with two thresholds and privileges neither.
 */
class RunwayDepartureEndTest {

    /** Thirty cells of strip along the south edge, west end first. */
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);

    /**
     * A craft parked beside one threshold does not taxi to the other one to
     * turn round there.
     *
     * <p>The owner's report, in miniature. Choosing on the destination alone
     * picks the end farthest from the target, which is right about the
     * departure heading and blind to the taxi and to the turn at the far end of
     * it — so an aircraft in a shed next to the west threshold, sent west, was
     * walked the whole length of its own field to the east end and then asked
     * to turn most of the way round.
     */
    @Test
    void aCraftParkedByOneThresholdDoesNotTaxiToTheOther() {
        float shedX = 12.5f, shedY = 12.5f;
        float targetX = 2.5f, targetY = 35.5f;

        assertEquals(40.5f, STRIP.departureThreshold(targetX, targetY)[0], 1e-3f,
                "the destination-only rule no longer picks the far end; this case is not the one described");

        assertEquals(10.5f, STRIP.departureThreshold(shedX, shedY, targetX, targetY)[0], 1e-3f,
                "sent the aircraft across its own field to turn round at the far end");
    }

    /**
     * And it still rolls toward where the sortie is going.
     *
     * <p>The reason the destination-only rule existed in the first place, and
     * the half of it worth keeping: an aircraft that leaves the strip pointing
     * at its objective has no turn to make once it is airborne. The shed here
     * sits behind the middle of the strip and a shade nearer the wrong end, so
     * the taxi and the turn at the threshold both narrowly favour taking off
     * away from the objective — and the flight out is what overrules them.
     */
    @Test
    void aCraftStillRollsTowardWhereItIsGoing() {
        float shedX = 27.5f, shedY = 30.5f;
        float targetX = 90.5f, targetY = 20.5f;

        assertEquals(10.5f, STRIP.departureThreshold(shedX, shedY, targetX, targetY)[0], 1e-3f,
                "took off away from the objective and had to turn round in the air");
    }
}
