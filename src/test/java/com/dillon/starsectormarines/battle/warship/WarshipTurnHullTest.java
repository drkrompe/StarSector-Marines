package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where a hull's nose goes when nothing is being fought: along the way it was sent, piloted or ordered, hovering or not. */
class WarshipTurnHullTest {

    private static final float DT = 1f / 30f;

    @BeforeEach
    void install() {
        PilotShipFixture.install();
    }

    @AfterEach
    void restore() {
        PilotShipFixture.restore();
    }

    @Test
    void aPilotedLowHoverHullTurnsToItsTravel() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.lowHoverFootprint(ship.lengthCells, ship.widthCells);
        ship.takeHelm();
        ship.pilotMove(1f, 0f);
        for (int i = 0; i < 9000; i++) {
            WarshipHelm.drive(ship, DT, 400, 400);
            WarshipSystem.turnHull(DT, ship);
        }
        assertEquals(AirBody.facingToward(1f, 0f), ship.facingDegrees, 1e-3f);
    }

    @Test
    void aHullOnAMoveOrderWithNothingToFightPointsAlongItsTravel() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.remoteOrder(Warship.RemoteOrder.MOVE, 100f, 10f);
        ship.velocityX = 0f;
        ship.velocityY = -2f;
        for (int i = 0; i < 9000; i++) WarshipSystem.turnHull(DT, ship);
        assertEquals(AirBody.facingToward(0f, -1f), ship.facingDegrees, 1e-3f);
    }
}
