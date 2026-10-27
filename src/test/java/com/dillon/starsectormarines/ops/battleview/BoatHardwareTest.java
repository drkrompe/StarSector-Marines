package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.BoatGun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoatHardwareTest {

    @Test
    void aGunIsDrawnInItsFamilysColourOnAnOmniHardpoint() {
        for (BoatGun gun : BoatGun.catalog()) {
            assertEquals(switch (gun.family()) {
                case BALLISTIC -> HardpointBricks.BALLISTIC;
                case ENERGY -> HardpointBricks.ENERGY;
                case MISSILE -> HardpointBricks.MISSILE;
            }, BoatHardware.piece(BoatDoll.Kind.HARDPOINT, gun.id()).tone(), gun.id());
        }
        assertEquals(HardpointBricks.OMNI, BoatHardware.tone(BoatDoll.Kind.HARDPOINT),
                "any family goes on a hardpoint, so the socket is omni");
    }
}
