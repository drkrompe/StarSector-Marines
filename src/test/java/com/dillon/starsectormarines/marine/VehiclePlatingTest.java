package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A vehicle's armor point: the plating it takes, what it makes of the hull, and how it is kept in stores. */
class VehiclePlatingTest {

    @Test
    void platingMovesTheArmorAlongTheHullsBandByTheMechsRuleAndOnlyAHeavyPlateSkirtsATrackedHull() {
        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        VehicleKit.Effect composite = VehicleKit.Effect.of(List.of(), VehiclePlating.COMPOSITE, tank);
        assertEquals(1f + MechInternal.COMPOSITE_PLATING.armorFraction * (VehicleType.REFIT_ARMOR_FACTOR - 1f),
                composite.armorFactor(), 1e-6f, "half the band");
        assertEquals(1.2f, composite.armorFactor(), 1e-6f);
        assertEquals(0f, composite.armorRatingBonus(), 1e-6f);

        VehicleKit.Effect heavy = VehicleKit.Effect.of(List.of(), VehiclePlating.HEAVY, tank);
        assertEquals(1.4f, heavy.armorFactor(), 1e-6f);
        assertEquals(2f, heavy.armorRatingBonus(), 1e-6f, "skirts on a tracked hull");
        assertEquals(0f, VehicleKit.Effect.of(List.of(), VehiclePlating.HEAVY, VehicleType.HEAVY_APC)
                .armorRatingBonus(), 1e-6f, "an APC runs on wheels");

        assertEquals(VehicleKit.Effect.NONE, VehicleKit.Effect.of(List.of(), VehiclePlating.FACTORY, tank));
    }

    @Test
    void theArmorPointTakesEveryPlatingAndEveryHullIsBuiltInFactoryPlating() {
        assertTrue(CampaignVehicle.takesPlating(MechInternal.FACTORY_PLATING), "factory plating is a part");
        assertTrue(CampaignVehicle.takesPlating(MechInternal.COMPOSITE_PLATING));
        assertTrue(CampaignVehicle.takesPlating(MechInternal.HEAVY_PLATING));
        assertFalse(CampaignVehicle.takesPlating(MechInternal.UPRATED_ENGINE));

        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertEquals(MechInternal.FACTORY_PLATING, tank.plating());
        assertEquals(VehiclePlating.FACTORY, tank.platingTier());
        assertEquals(VehiclePlating.FACTORY, tank.freezeForDeployment().plating());
    }

    @Test
    void platingFitsFromStoresReplacesToStoresAndGoesDownWithTheHullButNotWithAScrap() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));

        assertFalse(bay.fitPlating("v1", MechInternal.COMPOSITE_PLATING), "nothing in stores");
        bay.addInternal(MechInternal.COMPOSITE_PLATING.id, 1);
        bay.addInternal(MechInternal.HEAVY_PLATING.id, 1);
        assertTrue(bay.fitPlating("v1", MechInternal.COMPOSITE_PLATING));
        assertEquals(0, bay.availableInternal(MechInternal.COMPOSITE_PLATING.id));
        assertEquals(VehiclePlating.COMPOSITE, tank.freezeForDeployment().plating(), "the drop carries the plate");

        assertTrue(bay.fitPlating("v1", MechInternal.HEAVY_PLATING));
        assertEquals(1, bay.availableInternal(MechInternal.COMPOSITE_PLATING.id), "the outgoing plate is a spare");

        assertFalse(bay.stripPlating("v1"), "plating is only ever replaced, as on a mech");
        assertEquals(MechInternal.HEAVY_PLATING, tank.plating());

        assertTrue(bay.scrapVehicle("v1"));
        assertEquals(1, bay.availableInternal(MechInternal.HEAVY_PLATING.id), "a scrap spares the plate");

        CampaignVehicle lost = new CampaignVehicle("v2", "Lost", VehicleType.HEAVY_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, lost));
        assertTrue(bay.fitPlating("v2", MechInternal.HEAVY_PLATING));
        assertEquals(1, bay.loseVehicles(List.of("v2")));
        assertEquals(0, bay.ownedInternal(MechInternal.HEAVY_PLATING.id), "a wrecked hull takes its plate with it");
    }

    @Test
    void aSavedVehicleWithTheRetiredArmorKitsWearsThePlatingTheyBecame() throws Exception {
        CampaignVehicle both = vehicleWithKitIds(List.of("kit.applique-plate", "kit.spall-liner", "kit.spaced-skirts"));
        assertEquals(MechInternal.HEAVY_PLATING, both.plating(), "the heavier of the two wins");
        assertEquals(List.of(VehicleKit.SPALL_LINER), both.kits(), "the rest of the rack is kept");

        assertEquals(MechInternal.COMPOSITE_PLATING, vehicleWithKitIds(List.of("kit.applique-plate")).plating());
    }

    @Test
    void sparesOfTheRetiredArmorKitsAndTheirPatternsBecomeTheirPlating() throws Exception {
        MechBay bay = new MechBay();
        Field kits = MechBay.class.getDeclaredField("ownedKits");
        kits.setAccessible(true);
        Map<String, Integer> owned = new HashMap<>();
        owned.put("kit.applique-plate", 2);
        owned.put("kit.spaced-skirts", 1);
        owned.put("kit.spall-liner", 1);
        kits.set(bay, owned);
        Field patterns = MechBay.class.getDeclaredField("knownPatternIds");
        patterns.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> known = (java.util.Set<String>) patterns.get(bay);
        // As a real save stores it: learnPattern keeps the full pattern id.
        known.add(MechPattern.PREFIX + "kit:kit.applique-plate");
        known.add(MechPattern.PREFIX + "kit:kit.spaced-skirts");

        Method resolve = MechBay.class.getDeclaredMethod("readResolve");
        resolve.setAccessible(true);
        resolve.invoke(bay);

        assertEquals(2, bay.ownedInternal(MechInternal.COMPOSITE_PLATING.id));
        assertEquals(1, bay.ownedInternal(MechInternal.HEAVY_PLATING.id));
        assertEquals(0, bay.ownedKit("kit.applique-plate"));
        assertEquals(1, bay.ownedKit(VehicleKit.SPALL_LINER.id), "the rest of the kits stay");
        assertTrue(bay.knowsPattern(MechPattern.of(MechInternal.COMPOSITE_PLATING).id()),
                "a company that knew the kit knows its plating");
        assertTrue(bay.knowsPattern(MechPattern.of(MechInternal.HEAVY_PLATING).id()));
        assertFalse(bay.knownPatternIds().contains(MechPattern.PREFIX + "kit:kit.applique-plate"),
                "the retired pattern id is gone");
    }

    private static CampaignVehicle vehicleWithKitIds(List<String> ids) throws Exception {
        CampaignVehicle vehicle = new CampaignVehicle("v", "Old", VehicleType.BREAKTHROUGH_TANK);
        Field field = CampaignVehicle.class.getDeclaredField("kitIds");
        field.setAccessible(true);
        field.set(vehicle, new ArrayList<>(ids));
        Method resolve = CampaignVehicle.class.getDeclaredMethod("readResolve");
        resolve.setAccessible(true);
        resolve.invoke(vehicle);
        return vehicle;
    }
}
