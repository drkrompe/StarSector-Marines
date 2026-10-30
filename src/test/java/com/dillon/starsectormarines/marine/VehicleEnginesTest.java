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
    void theEngineCoreTakesEveryEngineAndEveryHullIsBuiltWithTheIndustrialOne() {
        assertTrue(CampaignVehicle.takesEngine(MechInternal.INDUSTRIAL_ENGINE), "the industrial engine is a part");
        assertTrue(CampaignVehicle.takesEngine(MechInternal.UPRATED_ENGINE));
        assertTrue(CampaignVehicle.takesEngine(MechInternal.MILITARY_ENGINE));
        assertFalse(CampaignVehicle.takesEngine(MechInternal.HEAVY_PLATING));

        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertEquals(MechInternal.INDUSTRIAL_ENGINE, tank.engine());
        assertEquals(VehicleEngine.STOCK, tank.engineTier());
        assertEquals(VehicleEngine.STOCK, tank.freezeForDeployment().engine());
    }

    @Test
    void anEngineMovesTheHullAlongItsSpeedBandByTheMechsRuleAndTheMilitaryOneRunsHotter() {
        assertEquals(1f, VehicleEngine.STOCK.speedFactor(), 1e-6f);
        assertEquals(1.1f, VehicleEngine.UPRATED.speedFactor(), 1e-6f, "half the band");
        assertEquals(VehicleType.REFIT_SPEED_FACTOR, VehicleEngine.MILITARY.speedFactor(), 1e-6f, "the whole band");
        assertEquals(MechInternal.MILITARY_ENGINE.dissipation, VehicleEngine.MILITARY.dissipation(), 1e-6f);
        assertEquals(0f, VehicleEngine.UPRATED.dissipation(), 1e-6f);
    }

    @Test
    void anEmptyEngineCoreIsNoEngineAndKeepsTheHullOffTheDrop() {
        MechBay bay = new MechBay();
        CampaignVehicle apc = new CampaignVehicle("v1", "Mule", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, apc));
        assertTrue(apc.complete());
        assertTrue(bay.stripEngine("v1"));
        assertFalse(apc.complete());
        assertEquals(List.of(MechInternal.Role.ENGINE), apc.missingInternals());
        assertEquals(1, bay.availableInternal(MechInternal.INDUSTRIAL_ENGINE.id), "the engine is a spare in stores");
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
        assertFalse(bay.stripEngine("v1"), "the core is empty already");
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
