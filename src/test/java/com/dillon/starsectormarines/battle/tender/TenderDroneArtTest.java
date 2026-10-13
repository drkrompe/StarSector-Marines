package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneLook;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderDroneArtTest {

    @Test
    void theBorerSpriteIsReadFromItsHullSpec() {
        InstalledVanillaData.install();
        assertEquals("graphics/ships/drones/drone_borer.png", TenderDroneArt.spritePath());
    }

    @Test
    void aBorerStandsAtHullScaleSoItIsSmallBesideItsTenderButNotLost() {
        float borer = TenderDroneArt.cells(16f);
        float shepherd = TenderDroneArt.cells(92f);
        assertTrue(borer > 0.5f && borer < 1.2f, "a drone is about a cell, was " + borer);
        assertTrue(shepherd / borer > 4f, "the hull is several drones long");
    }

    @Test
    void aDroneKeepsItsLookOnItsBodyAndTheShippedOneIsUnchanged() {
        EntitySpec hub = Drone.create("h", Faction.DEFENDER, 3, 3, 0L);
        EntitySpec tender = Drone.create("t", Faction.MARINE, 3, 3, 0L, DroneLook.TENDER);
        assertEquals(DroneLook.STANDARD, DroneLook.of(hub.body));
        assertEquals(DroneLook.TENDER, DroneLook.of(tender.body));
        assertEquals(DroneLook.STANDARD, DroneLook.of(new AirBody()), "an unmarked body is the shipped look");
        assertEquals(DroneLook.STANDARD, DroneLook.of(null));
    }
}
