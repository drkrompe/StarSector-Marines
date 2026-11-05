package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A departing hull is gone once it has climbed into the cloud deck, not when it reaches a map edge. */
class WarshipDeckDepartureTest {

    private static final float W = 400f;
    private static final float H = 300f;

    @BeforeEach
    void install() {
        PilotShipFixture.install();
    }

    @AfterEach
    void restore() {
        PilotShipFixture.restore();
    }

    @Test
    void anOrdinaryDepartureIsGoneOnceTheClimbIsSpent() throws Exception {
        Warship ship = PilotShipFixture.ship(200f, 150f);
        assertFalse(ship.intoTheDeck(), "a ship on station is not leaving");
        ship.departForward(W, H);
        ship.addDepartingSeconds(WarshipAtmosphere.CLIMB_SECONDS - 0.1f);
        assertFalse(ship.intoTheDeck(), "still climbing, still drawn");
        ship.addDepartingSeconds(0.1f);
        assertTrue(ship.intoTheDeck());
    }

    @Test
    void aHullLeavingAcrossAnEdgeNeverClimbsIntoTheDeck() throws Exception {
        Warship ship = PilotShipFixture.ship(200f, 150f);
        ship.leaveAcross(MapEdge.EAST, W, H, false);
        ship.addDepartingSeconds(WarshipAtmosphere.CLIMB_SECONDS * 10f);
        assertFalse(ship.intoTheDeck());
    }
}
