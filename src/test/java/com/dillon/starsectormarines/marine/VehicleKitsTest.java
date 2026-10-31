package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vehicle kit rack's rules: slots, one of each, what suits a hull, and stores. */
class VehicleKitsTest {

    @Test
    void aRackHoldsOneKitPerSlotBesideItsCompartmentPartsOneOfEachAndOnlyWhatSuitsTheHull() {
        assertEquals(2, VehicleKit.slots(VehicleType.SUPPLY_TRUCK), "a Light hull has two slots");
        assertEquals(3, VehicleKit.slots(VehicleType.HEAVY_APC), "a Medium hull three");
        assertEquals(4, VehicleKit.slots(VehicleType.BREAKTHROUGH_TANK), "a Heavy hull four");

        assertTrue(VehicleKit.fits(VehicleType.FIRE_SUPPORT_APC, List.of(VehicleKit.SENSOR_PAIR)));
        List<MechInternal> lined = new ArrayList<>(VehicleRack.stockFit(VehicleType.FIRE_SUPPORT_APC));
        lined.add(MechInternal.SPALL_LINER);
        assertFalse(VehicleRack.fits(VehicleType.FIRE_SUPPORT_APC, List.of(VehicleKit.SENSOR_PAIR), lined),
                "the stock bank, loader and a liner take all three slots");
        assertTrue(VehicleRack.fits(VehicleType.SUPPLY_TRUCK, List.of(VehicleKit.SENSOR_PAIR),
                List.of(MechInternal.SPALL_LINER)), "an unarmed hull needs no bank");
        assertFalse(VehicleKit.fits(VehicleType.BREAKTHROUGH_TANK,
                List.of(VehicleKit.SENSOR_PAIR, VehicleKit.SENSOR_PAIR)), "no kit twice");
    }

    @Test
    void aSensorPairSuitsOnlyAHullWithNoBayOfItsOwn() {
        assertTrue(VehicleKit.SENSOR_PAIR.suits(VehicleType.LRM_CARRIER));
        assertFalse(VehicleKit.SENSOR_PAIR.suits(VehicleType.HOVER_SCOUT), "it has a sensor pair already");
        assertFalse(VehicleKit.SENSOR_PAIR.suits(VehicleType.DRONE_SKIMMER), "it has a bay");
        assertEquals(2, VehicleKit.SENSOR_PAIR.droneBay().size());
        assertTrue(VehicleKit.ACTIVE_PROTECTION.droneBay().isEmpty());
    }

    @Test
    void activeProtectionSuitsOnlyAnArmedGunTankAndGivesItsHullThePointDefenceMount() {
        assertTrue(VehicleKit.ACTIVE_PROTECTION.suits(VehicleType.BREAKTHROUGH_TANK));
        assertTrue(VehicleKit.ACTIVE_PROTECTION.suits(VehicleType.LASER_TANK));
        for (VehicleType other : List.of(VehicleType.DOZER_BREACHER, VehicleType.HEAVY_APC, VehicleType.LRM_CARRIER,
                VehicleType.MILITIA_TECHNICAL, VehicleType.SUPPLY_TRUCK, VehicleType.HOVER_SCOUT)) {
            assertFalse(VehicleKit.ACTIVE_PROTECTION.suits(other), "not a gun tank: " + other);
        }
        assertEquals("structure.tank-aps", VehicleKit.activeProtectionStructureId(
                List.of(VehicleKit.SENSOR_PAIR, VehicleKit.ACTIVE_PROTECTION)));
        assertEquals(null, VehicleKit.activeProtectionStructureId(List.of(VehicleKit.SENSOR_PAIR)));
        assertTrue(VehicleKit.speedFactor(List.of(VehicleKit.ACTIVE_PROTECTION)) < 1f, "the launchers cost speed");
        assertEquals(1f, VehicleKit.speedFactor(List.of(VehicleKit.SENSOR_PAIR)), 0f);
    }

    @Test
    void kitsFitFromStoresComeOffToStoresAndGoDownWithTheHullButNotWithAScrap() {
        MechBay bay = new MechBay();
        CampaignVehicle apc = new CampaignVehicle("v1", "Carrier", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, apc));
        assertTrue(apc.kits().isEmpty(), "a bought hull comes with an empty rack");

        assertFalse(bay.fitKit("v1", VehicleKit.SENSOR_PAIR), "nothing in stores");
        bay.addKit(VehicleKit.SENSOR_PAIR.id, 2);
        assertTrue(bay.fitKit("v1", VehicleKit.SENSOR_PAIR));
        assertEquals(1, bay.availableKit(VehicleKit.SENSOR_PAIR.id));
        assertEquals(List.of(VehicleKit.SENSOR_PAIR), apc.freezeForDeployment().kits(), "the drop carries the rack");

        assertTrue(bay.stripKit("v1", VehicleKit.SENSOR_PAIR));
        assertEquals(2, bay.availableKit(VehicleKit.SENSOR_PAIR.id), "a stripped kit is a spare");

        assertTrue(bay.fitKit("v1", VehicleKit.SENSOR_PAIR));
        assertTrue(bay.scrapVehicle("v1"));
        assertEquals(2, bay.availableKit(VehicleKit.SENSOR_PAIR.id), "a scrap spares the kit");

        CampaignVehicle lost = new CampaignVehicle("v2", "Lost", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, lost));
        assertTrue(bay.fitKit("v2", VehicleKit.SENSOR_PAIR));
        assertEquals(1, bay.loseVehicles(List.of("v2")));
        assertEquals(1, bay.ownedKit(VehicleKit.SENSOR_PAIR.id), "a wrecked hull takes its kit with it");
    }

    @Test
    void aVehicleSavedBeforeKitsHasAnEmptyRackAndKitsArePatterns() throws Exception {
        CampaignVehicle old = new CampaignVehicle("v1", "Old", VehicleType.BREAKTHROUGH_TANK);
        Field field = CampaignVehicle.class.getDeclaredField("kitIds");
        field.setAccessible(true);
        field.set(old, null);
        assertTrue(old.kits().isEmpty());
        assertTrue(old.fit(VehicleKit.SENSOR_PAIR), "an old hull still takes a kit");

        MechPattern pattern = MechPattern.of(VehicleKit.SENSOR_PAIR);
        assertEquals(pattern, MechPattern.parse(pattern.id()));
        assertEquals(MechPattern.Kind.KIT, pattern.kind());
        assertTrue(MechFabricationCatalog.kit(VehicleKit.SENSOR_PAIR) != null);
    }
}
