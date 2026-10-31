package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportFireTest {

    @Test
    void aSupportGunFiresOnlyOnASightedMassWithNoFriendUnderTheRound() {
        assertTrue(SupportFire.admits(true, false, SupportFire.MIN_MASS));
        assertFalse(SupportFire.admits(false, false, 12), "a mass nobody of the side has in sight is not fired on");
        assertFalse(SupportFire.admits(true, true, 12), "never danger close");
        assertFalse(SupportFire.admits(true, false, SupportFire.MIN_MASS - 1), "a straggler is not worth a salvo");
    }

    @Test
    void anEnrolledGunIsTheOnlySupportGun() {
        SupportFire fire = new SupportFire();
        assertFalse(fire.enrolled(7L));
        fire.enrol(7L);
        assertTrue(fire.enrolled(7L));
        assertFalse(fire.enrolled(8L));
    }
}
