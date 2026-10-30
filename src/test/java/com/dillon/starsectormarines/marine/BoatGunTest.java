package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A save names a boat gun by id, so an id a rename retired must still find its gun. */
class BoatGunTest {

    @Test
    void aRetiredGunIdFindsItsSuccessor() {
        assertSame(BoatGun.TWIN_PE_1, BoatGun.findById("boat_pulse_laser"));
        assertSame(BoatGun.PL_3, BoatGun.findById("boat_burst_laser"));
        assertSame(BoatGun.PL_3, BoatGun.findById(BoatGun.PL_3.id()));
        assertNull(BoatGun.findById("boat_no_such_gun"));
    }
}
