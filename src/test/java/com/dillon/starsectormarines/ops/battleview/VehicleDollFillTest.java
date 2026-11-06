package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A built-in gun is drawn in its own colour; the card around it keeps the card's (owner: omni does not colour a weapon). */
class VehicleDollFillTest {

    @Test
    void aBuiltInGunInAnOmniCardIsTheGunsColourAndTheCardStaysGrey() {
        assertEquals(HardpointBricks.OMNI, VehicleDoll.tone(VehicleType.BREAKTHROUGH_TANK, SocketId.ARMS));
        assertEquals(HardpointBricks.BALLISTIC, VehicleDoll.fill(VehicleType.BREAKTHROUGH_TANK, SocketId.ARMS));
        assertEquals(HardpointBricks.MISSILE, VehicleDoll.fill(VehicleType.SRM_TECHNICAL, SocketId.ARMS));
    }

    @Test
    void aLockedCardsPieceAndEveryOtherSealedCardKeepTheCardsColour() {
        assertEquals(HardpointBricks.MISSILE, VehicleDoll.fill(VehicleType.LRM_CARRIER, SocketId.ARMS));
        assertEquals(VehicleDoll.tone(VehicleType.HEAVY_APC, SocketId.RIGHT_SHOULDER),
                VehicleDoll.fill(VehicleType.HEAVY_APC, SocketId.RIGHT_SHOULDER));
    }
}
