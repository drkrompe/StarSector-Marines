package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A warship brought in mid-battle breaks out of the cloud deck already pointed where it is going. */
class WarshipArrivalHeadingTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    @Test
    void anArrivingShipFacesWhereItIsGoingAndHoldsFireUntilItIsDown() throws Exception {
        // Fitted facing west, sent east: it must come in facing east, not come about after appearing.
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 20f, 40f, 90f);
        wolf.arriveFromAbove(120f, 40f);
        float[] nose = {(float) -Math.sin(Math.toRadians(wolf.facingDegrees)),
                (float) Math.cos(Math.toRadians(wolf.facingDegrees))};
        assertEquals(1f, nose[0], 1e-4f);
        assertEquals(0f, nose[1], 1e-4f);
        assertEquals(wolf.facingDegrees, wolf.desiredHeading, 1e-6f, "nothing left to turn");
        assertTrue(wolf.descending());
        wolf.addEntrySeconds(WarshipAtmosphere.DESCENT_SECONDS);
        assertFalse(wolf.descending(), "down through the deck after the descent");
    }

    @Test
    void aMissionMayNameTheHeading() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 20f, 40f, 90f);
        wolf.arriveFromAboveOnHeading(0f);
        assertEquals(0f, wolf.facingDegrees, 1e-6f);
        assertTrue(wolf.arrivedFromAbove());
    }
}
