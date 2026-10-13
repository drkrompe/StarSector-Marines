package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunshipOrbitTest {

    private static final float R = 10f;

    @Test
    void aCraftOnTheRingIsAimedAheadAlongItInTheDirectionOfTheOrbit() {
        // Due east of the centre. Clockwise (y up) goes south; counter-clockwise goes north.
        float[] cw = GunshipOrbit.carrot(R, 0f, 0f, 0f, R, true);
        float[] ccw = GunshipOrbit.carrot(R, 0f, 0f, 0f, R, false);
        assertTrue(cw[1] < 0f, "clockwise carrot lies south of an eastern craft");
        assertTrue(ccw[1] > 0f, "counter-clockwise carrot lies north of an eastern craft");
        assertEquals(R, (float) Math.hypot(cw[0], cw[1]), 1e-3f, "the carrot is on the ring");
        assertEquals(R, (float) Math.hypot(ccw[0], ccw[1]), 1e-3f, "the carrot is on the ring");
    }

    @Test
    void aCraftWellOutsideIsAimedAtTheTangentPointNotTheCentre() {
        // From (40, 0) a clockwise ring is joined where the line from the craft just grazes it.
        float[] tangent = GunshipOrbit.carrot(40f, 0f, 0f, 0f, R, true);
        double toCarrotX = tangent[0] - 40.0;
        double toCarrotY = tangent[1];
        double radialX = tangent[0];
        double radialY = tangent[1];
        double dot = toCarrotX * radialX + toCarrotY * radialY;
        assertEquals(0.0, dot / (Math.hypot(toCarrotX, toCarrotY) * Math.hypot(radialX, radialY)), 1e-4,
                "the approach meets the ring at a right angle to the radius, so it joins without crossing");
        assertTrue(tangent[1] < 0f, "clockwise joins on the south side of an eastern approach");
    }

    @Test
    void aCraftInsideTheRingIsPushedOutToItNotAcrossIt() {
        float[] carrot = GunshipOrbit.carrot(2f, 0f, 0f, 0f, R, true);
        assertEquals(R, (float) Math.hypot(carrot[0], carrot[1]), 1e-3f);
    }

    @Test
    void theDirectionChosenAsksTheSmallerTurnOnArrival() {
        // Flying west toward a centre at the origin from the east, offset a little north of the line.
        // facing 90 = west. Clockwise (south-of-line tangent) vs counter-clockwise (north): the craft is
        // north of the axis, so it reaches the north tangent without crossing the line.
        boolean clockwise = GunshipOrbit.clockwiseFrom(40f, 6f, 90f, 0f, 0f, R);
        boolean mirror = GunshipOrbit.clockwiseFrom(40f, -6f, 90f, 0f, 0f, R);
        assertFalse(clockwise == mirror, "mirrored approaches take opposite directions round the ring");
    }

    @Test
    void aCraftAlreadyOnTheRingKeepsGoingTheWayItIsHeaded() {
        // On the eastern edge heading south (facing 180): that is clockwise.
        assertTrue(GunshipOrbit.clockwiseFrom(R, 0f, 180f, 0f, 0f, R));
        // Heading north (facing 0): counter-clockwise.
        assertFalse(GunshipOrbit.clockwiseFrom(R, 0f, 0f, 0f, 0f, R));
    }

    @Test
    void theClockStartsOnceTheCraftIsNearTheRing() {
        assertFalse(GunshipOrbit.onTheRing(40f, 0f, 0f, 0f, R));
        assertTrue(GunshipOrbit.onTheRing(R * GunshipOrbit.ENTER_FACTOR - 0.1f, 0f, 0f, 0f, R));
    }

    @Test
    void theRingsPaceEasesDownFromCruiseRatherThanSnapping() {
        AirHandling base = ShuttleType.MUDSKIPPER;
        float dt = 1f / 30f;
        AirHandling fresh = GunshipOrbit.paced(base, base.maxSpeed(), dt);
        assertTrue(fresh.maxSpeed() < base.maxSpeed(), "the cap starts coming down at once");
        assertTrue(fresh.maxSpeed() > GunshipOrbit.ORBIT_SPEED, "but only at the airframe's braking rate");
        AirHandling slow = GunshipOrbit.paced(base, 1f, dt);
        assertEquals(GunshipOrbit.ORBIT_SPEED, slow.maxSpeed(), 1e-4f, "a craft already slower flies the ring's pace");
        assertEquals(base.maxTurnRateDegPerSec(), slow.maxTurnRateDegPerSec(), 1e-4f, "everything else is the airframe's own");
    }

    @Test
    void theInboardFlankIsTheRightHandOneOnAClockwiseRing() {
        assertEquals(1f, GunshipOrbit.inboardSide(true));
        assertEquals(-1f, GunshipOrbit.inboardSide(false));
    }

    @Test
    void theRingIsTightEnoughForTheAirframeToFly() {
        AirHandling paced = GunshipOrbit.paced(ShuttleType.MUDSKIPPER, 0f, 1f / 30f);
        assertTrue(paced.minTurnRadiusCells() < GunshipOrbit.RADIUS_CELLS,
                "a ring tighter than the craft's turning circle would be an orbit it can never join");
    }
}
