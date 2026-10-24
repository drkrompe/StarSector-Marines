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

        assertTrue(VehicleKit.fits(VehicleType.HEAVY_APC, List.of(VehicleKit.APPLIQUE_PLATE, VehicleKit.SPALL_LINER)));
        assertFalse(VehicleKit.fits(VehicleType.HEAVY_APC,
                List.of(VehicleKit.APPLIQUE_PLATE, VehicleKit.SPALL_LINER, VehicleKit.READY_RACK)), "no third slot");
        assertFalse(VehicleKit.fits(VehicleType.BREAKTHROUGH_TANK,
                List.of(VehicleKit.APPLIQUE_PLATE, VehicleKit.APPLIQUE_PLATE)), "no kit twice");
        assertFalse(VehicleKit.READY_RACK.suits(VehicleType.SUPPLY_TRUCK), "no magazine to extend");
        assertFalse(VehicleKit.SPACED_SKIRTS.suits(VehicleType.MILITIA_TECHNICAL), "no tracks to skirt");
        assertTrue(VehicleKit.SPACED_SKIRTS.suits(VehicleType.BREAKTHROUGH_TANK));
    }

    @Test
    void kitsChangeOnlyTheNumbersTheHullIsSetDownWith() {
        VehicleKit.Effect effect = VehicleKit.Effect.of(List.of(VehicleKit.APPLIQUE_PLATE, VehicleKit.SPACED_SKIRTS));
        assertEquals(1.25f, effect.armorFactor(), 1e-6f);
        assertEquals(1f, effect.structureFactor(), 1e-6f);
        assertEquals(2f, effect.armorRatingBonus(), 1e-6f);
        assertEquals(VehicleKit.Effect.NONE, VehicleKit.Effect.of(List.of()));

        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        HullLocations bare = HullLocations.of(tank, null);
        HullLocations plated = HullLocations.of(tank, null, effect);
        assertEquals(bare.maxTotalArmor() * 1.25f, plated.maxTotalArmor(), 1e-3f, "every plate a quarter more");
        HullLocations lined = HullLocations.of(tank, null, VehicleKit.Effect.of(List.of(VehicleKit.SPALL_LINER)));
        assertEquals(bare.maxDrive() * 1.2f, lined.maxDrive(), 1e-3f, "the drive's share of the larger structure");
    }

    @Test
    void kitsFitFromStoresComeOffToStoresAndGoDownWithTheHullButNotWithAScrap() {
        MechBay bay = new MechBay();
        CampaignVehicle apc = new CampaignVehicle("v1", "Carrier", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, apc));
        assertTrue(apc.kits().isEmpty(), "a bought hull comes with an empty rack");

        assertFalse(bay.fitKit("v1", VehicleKit.APPLIQUE_PLATE), "nothing in stores");
        bay.addKit(VehicleKit.APPLIQUE_PLATE.id, 2);
        assertTrue(bay.fitKit("v1", VehicleKit.APPLIQUE_PLATE));
        assertEquals(1, bay.availableKit(VehicleKit.APPLIQUE_PLATE.id));
        assertEquals(List.of(VehicleKit.APPLIQUE_PLATE), apc.freezeForDeployment().kits(), "the drop carries the rack");

        assertTrue(bay.stripKit("v1", VehicleKit.APPLIQUE_PLATE));
        assertEquals(2, bay.availableKit(VehicleKit.APPLIQUE_PLATE.id), "a stripped kit is a spare");

        assertTrue(bay.fitKit("v1", VehicleKit.APPLIQUE_PLATE));
        assertTrue(bay.scrapVehicle("v1"));
        assertEquals(2, bay.availableKit(VehicleKit.APPLIQUE_PLATE.id), "a scrap spares the kit");

        CampaignVehicle lost = new CampaignVehicle("v2", "Lost", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, lost));
        assertTrue(bay.fitKit("v2", VehicleKit.APPLIQUE_PLATE));
        assertEquals(1, bay.loseVehicles(List.of("v2")));
        assertEquals(1, bay.ownedKit(VehicleKit.APPLIQUE_PLATE.id), "a wrecked hull takes its kit with it");
    }

    @Test
    void aVehicleSavedBeforeKitsHasAnEmptyRackAndKitsArePatterns() throws Exception {
        CampaignVehicle old = new CampaignVehicle("v1", "Old", VehicleType.BREAKTHROUGH_TANK);
        Field field = CampaignVehicle.class.getDeclaredField("kitIds");
        field.setAccessible(true);
        field.set(old, null);
        assertTrue(old.kits().isEmpty());
        assertTrue(old.fit(VehicleKit.SPACED_SKIRTS), "an old hull still takes a kit");

        MechPattern pattern = MechPattern.of(VehicleKit.SPACED_SKIRTS);
        assertEquals(pattern, MechPattern.parse(pattern.id()));
        assertEquals(MechPattern.Kind.KIT, pattern.kind());
        assertTrue(MechFabricationCatalog.kit(VehicleKit.SPACED_SKIRTS) != null);
    }
}
