package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A hardpoint takes one 2x2 gun, or two 2x1 guns top and bottom, and says what a refit takes off. */
class BoatHardpointTest {

    private static final BoatGun LASER = BoatGun.TWIN_PE_1;
    private static final BoatGun MG = BoatGun.HEAVY_MG;
    private static final BoatGun ARBALEST = BoatGun.ARBALEST;

    @Test
    void twoHalfGunsShareAHardpointAndEachKeepsItsPosition() {
        BoatGun[] pod = BoatHardpoint.placed(BoatHardpoint.placed(null, BoatHardpoint.TOP, LASER),
                BoatHardpoint.BOTTOM, LASER);

        assertArrayEquals(new BoatGun[]{LASER, LASER}, pod);
        assertEquals(List.of(), BoatHardpoint.displaced(new BoatGun[]{LASER, null}, pod),
                "the second laser goes in beside the first, taking nothing off");
        BoatGun[] mixed = BoatHardpoint.placed(pod, BoatHardpoint.BOTTOM, MG);
        assertArrayEquals(new BoatGun[]{LASER, MG}, mixed, "families mix in one pod");
        assertEquals(List.of(LASER), BoatHardpoint.displaced(pod, mixed));
        assertArrayEquals(new BoatGun[]{null, MG}, BoatHardpoint.placed(mixed, BoatHardpoint.TOP, null),
                "stripping the top leaves the bottom where it is");
    }

    @Test
    void aWholeGunFillsBothPositionsAndTakesOffWhatWasThere() {
        BoatGun[] whole = BoatHardpoint.placed(new BoatGun[]{LASER, MG}, BoatHardpoint.BOTTOM, ARBALEST);

        assertArrayEquals(new BoatGun[]{ARBALEST, null}, whole, "a 2x2 gun sits on top wherever it was let go");
        assertEquals(List.of(LASER, MG), BoatHardpoint.displaced(new BoatGun[]{LASER, MG}, whole));
        assertSame(ARBALEST, BoatHardpoint.occupant(whole, BoatHardpoint.BOTTOM), "it fills the bottom too");

        BoatGun[] half = BoatHardpoint.placed(whole, BoatHardpoint.BOTTOM, LASER);
        assertArrayEquals(new BoatGun[]{null, LASER}, half, "a 2x1 gun beside a 2x2 one takes its place");
        assertEquals(List.of(ARBALEST), BoatHardpoint.displaced(whole, half));
        assertArrayEquals(new BoatGun[]{null, null}, BoatHardpoint.placed(whole, BoatHardpoint.BOTTOM, null),
                "stripping either position of a 2x2 gun takes the gun");
        assertNull(BoatHardpoint.occupant(half, BoatHardpoint.TOP));
    }
}
