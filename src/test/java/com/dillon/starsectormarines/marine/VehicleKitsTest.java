package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vehicle kit rack's rules: slots, one of each, what suits a hull, the effect, and stores. */
class VehicleKitsTest {

    @Test
    void aRackHoldsOneKitPerSlotOneOfEachAndOnlyWhatSuitsTheHull() {
        assertEquals(1, VehicleKit.slots(VehicleType.SUPPLY_TRUCK), "a Light hull has one slot");
        assertEquals(2, VehicleKit.slots(VehicleType.HEAVY_APC), "a Medium hull two");
        assertEquals(3, VehicleKit.slots(VehicleType.BREAKTHROUGH_TANK), "a Heavy hull three");

        assertTrue(VehicleKit.fits(VehicleType.FIRE_SUPPORT_APC, List.of(VehicleKit.READY_RACK, VehicleKit.SPALL_LINER)));
        assertFalse(VehicleKit.fits(VehicleType.FIRE_SUPPORT_APC,
                List.of(VehicleKit.READY_RACK, VehicleKit.SPALL_LINER, VehicleKit.ACCELERATED_LOADER)), "no third slot");
        assertFalse(VehicleKit.fits(VehicleType.BREAKTHROUGH_TANK,
                List.of(VehicleKit.SPALL_LINER, VehicleKit.SPALL_LINER)), "no kit twice");
        assertFalse(VehicleKit.READY_RACK.suits(VehicleType.SUPPLY_TRUCK), "no magazine to extend");
        assertTrue(VehicleKit.ACCELERATED_LOADER.suits(VehicleType.FIRE_SUPPORT_APC));
        assertFalse(VehicleKit.READY_RACK.suits(VehicleType.HEAVY_APC), "a belt-fed light machine gun has no magazine");
        assertFalse(VehicleKit.ACCELERATED_LOADER.suits(VehicleType.HEAVY_APC), "nor a loader");
        assertFalse(VehicleKit.ACCELERATED_LOADER.suits(VehicleType.SUPPLY_TRUCK), "no gun to feed");
        assertTrue(VehicleKit.CAPACITOR_PACK.suits(VehicleType.LASER_TANK));
        assertTrue(VehicleKit.CAPACITOR_PACK.suits(VehicleType.BREAKTHROUGH_TANK), "every hull gun builds flux");
        assertFalse(VehicleKit.CAPACITOR_PACK.suits(VehicleType.SUPPLY_TRUCK), "no gun, no flux");
    }

    @Test
    void aSensorPairSuitsOnlyAHullWithNoBayOfItsOwn() {
        assertTrue(VehicleKit.SENSOR_PAIR.suits(VehicleType.LRM_CARRIER));
        assertFalse(VehicleKit.SENSOR_PAIR.suits(VehicleType.HOVER_SCOUT), "it has a sensor pair already");
        assertFalse(VehicleKit.SENSOR_PAIR.suits(VehicleType.DRONE_SKIMMER), "it has a bay");
        assertEquals(2, VehicleKit.SENSOR_PAIR.droneBay().size());
        assertTrue(VehicleKit.SPALL_LINER.droneBay().isEmpty());
    }

    @Test
    void kitsChangeOnlyTheNumbersTheHullIsSetDownWith() {
        VehicleKit.Effect effect = VehicleKit.Effect.of(List.of(VehicleKit.SPALL_LINER, VehicleKit.READY_RACK));
        assertEquals(1f, effect.armorFactor(), 1e-6f, "armor is the armor point's, not the rack's");
        assertEquals(1.2f, effect.structureFactor(), 1e-6f);
        assertEquals(1.5f, effect.ammoFactor(), 1e-6f);
        assertEquals(VehicleKit.Effect.NONE, VehicleKit.Effect.of(List.of()));

        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        HullLocations bare = HullLocations.of(tank, null);
        HullLocations lined = HullLocations.of(tank, null, VehicleKit.Effect.of(List.of(VehicleKit.SPALL_LINER)));
        assertEquals(bare.maxDrive() * 1.2f, lined.maxDrive(), 1e-3f, "the drive's share of the larger structure");
    }

    @Test
    void kitsFitFromStoresComeOffToStoresAndGoDownWithTheHullButNotWithAScrap() {
        MechBay bay = new MechBay();
        CampaignVehicle apc = new CampaignVehicle("v1", "Carrier", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, apc));
        assertTrue(apc.kits().isEmpty(), "a bought hull comes with an empty rack");

        assertFalse(bay.fitKit("v1", VehicleKit.SPALL_LINER), "nothing in stores");
        bay.addKit(VehicleKit.SPALL_LINER.id, 2);
        assertTrue(bay.fitKit("v1", VehicleKit.SPALL_LINER));
        assertEquals(1, bay.availableKit(VehicleKit.SPALL_LINER.id));
        assertEquals(List.of(VehicleKit.SPALL_LINER), apc.freezeForDeployment().kits(), "the drop carries the rack");

        assertTrue(bay.stripKit("v1", VehicleKit.SPALL_LINER));
        assertEquals(2, bay.availableKit(VehicleKit.SPALL_LINER.id), "a stripped kit is a spare");

        assertTrue(bay.fitKit("v1", VehicleKit.SPALL_LINER));
        assertTrue(bay.scrapVehicle("v1"));
        assertEquals(2, bay.availableKit(VehicleKit.SPALL_LINER.id), "a scrap spares the kit");

        CampaignVehicle lost = new CampaignVehicle("v2", "Lost", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, lost));
        assertTrue(bay.fitKit("v2", VehicleKit.SPALL_LINER));
        assertEquals(1, bay.loseVehicles(List.of("v2")));
        assertEquals(1, bay.ownedKit(VehicleKit.SPALL_LINER.id), "a wrecked hull takes its kit with it");
    }

    @Test
    void aVehicleSavedBeforeKitsHasAnEmptyRackAndKitsArePatterns() throws Exception {
        CampaignVehicle old = new CampaignVehicle("v1", "Old", VehicleType.BREAKTHROUGH_TANK);
        Field field = CampaignVehicle.class.getDeclaredField("kitIds");
        field.setAccessible(true);
        field.set(old, null);
        assertTrue(old.kits().isEmpty());
        assertTrue(old.fit(VehicleKit.SPALL_LINER), "an old hull still takes a kit");

        MechPattern pattern = MechPattern.of(VehicleKit.SPALL_LINER);
        assertEquals(pattern, MechPattern.parse(pattern.id()));
        assertEquals(MechPattern.Kind.KIT, pattern.kind());
        assertTrue(MechFabricationCatalog.kit(VehicleKit.SPALL_LINER) != null);
    }
}
