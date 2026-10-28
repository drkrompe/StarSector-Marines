package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A vehicle's engine core: the engines it takes, what they make of the hull, and how they are kept in stores. */
class VehicleEnginesTest {

    @Test
    void theEngineCoreTakesOnlyEnginesAboveTheHullsOwn() {
        assertFalse(CampaignVehicle.takesEngine(MechInternal.INDUSTRIAL_ENGINE), "the hull's own drive is no part");
        assertTrue(CampaignVehicle.takesEngine(MechInternal.UPRATED_ENGINE));
        assertTrue(CampaignVehicle.takesEngine(MechInternal.MILITARY_ENGINE));
        assertFalse(CampaignVehicle.takesEngine(MechInternal.HEAVY_PLATING));

        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertEquals(VehicleEngine.STOCK, tank.engineTier());
        assertEquals(VehicleEngine.STOCK, tank.freezeForDeployment().engine());
    }

    @Test
    void anEngineFitsFromStoresReplacesToStoresAndGoesDownWithTheHullButNotWithAScrap() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));

        assertFalse(bay.fitEngine("v1", MechInternal.UPRATED_ENGINE), "nothing in stores");
        bay.addInternal(MechInternal.UPRATED_ENGINE.id, 1);
        bay.addInternal(MechInternal.MILITARY_ENGINE.id, 1);
        assertTrue(bay.fitEngine("v1", MechInternal.UPRATED_ENGINE));
        assertEquals(0, bay.availableInternal(MechInternal.UPRATED_ENGINE.id));
        assertEquals(VehicleEngine.UPRATED, tank.freezeForDeployment().engine(), "the drop carries the engine");

        assertTrue(bay.fitEngine("v1", MechInternal.MILITARY_ENGINE));
        assertEquals(1, bay.availableInternal(MechInternal.UPRATED_ENGINE.id), "the outgoing engine is a spare");
        assertEquals(VehicleEngine.MILITARY, tank.freezeForDeployment().engine());

        assertTrue(bay.stripEngine("v1"));
        assertNull(tank.engine());
        assertFalse(bay.stripEngine("v1"), "the hull runs its own drive already");
        assertEquals(1, bay.availableInternal(MechInternal.MILITARY_ENGINE.id));

        assertTrue(bay.fitEngine("v1", MechInternal.MILITARY_ENGINE));
        assertTrue(bay.scrapVehicle("v1"));
        assertEquals(1, bay.availableInternal(MechInternal.MILITARY_ENGINE.id), "a scrap spares the engine");

        CampaignVehicle lost = new CampaignVehicle("v2", "Lost", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, lost));
        assertTrue(bay.fitEngine("v2", MechInternal.MILITARY_ENGINE));
        assertEquals(1, bay.loseVehicles(List.of("v2")));
        assertEquals(0, bay.ownedInternal(MechInternal.MILITARY_ENGINE.id), "a wrecked hull takes its engine with it");
    }

    @Test
    void aFittedEngineCountsAsInstalledSoItIsNeverAlsoASpare() {
        MechBay bay = new MechBay();
        CampaignVehicle apc = new CampaignVehicle("v1", "Mule", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, apc));
        bay.addInternal(MechInternal.UPRATED_ENGINE.id, 1);
        assertTrue(bay.fitEngine("v1", MechInternal.UPRATED_ENGINE));
        assertEquals(1, bay.installedInternal(MechInternal.UPRATED_ENGINE.id));
        assertEquals(0, bay.availableInternal(MechInternal.UPRATED_ENGINE.id));
    }
}
