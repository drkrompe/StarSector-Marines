package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each line's fixed variants: their cards, tiers, art gate, bills and trade-offs. */
class VehicleVariantLadderTest {

    private static final List<VehicleType> VARIANTS = List.of(VehicleType.UP_ARMORED_APC,
            VehicleType.FIRE_SUPPORT_APC, VehicleType.HEAVY_GUN_TANK, VehicleType.DOZER_BREACHER);

    @Test
    void eachVariantIsItsOwnVehiclePatternAtAnAuthoredTier() {
        assertEquals(EquipmentAccessTier.ADVANCED, MechPattern.of(VehicleType.UP_ARMORED_APC).accessTier());
        assertEquals(EquipmentAccessTier.ADVANCED, MechPattern.of(VehicleType.FIRE_SUPPORT_APC).accessTier());
        assertEquals(EquipmentAccessTier.PRESTIGE, MechPattern.of(VehicleType.HEAVY_GUN_TANK).accessTier());
        assertEquals(EquipmentAccessTier.PRESTIGE, MechPattern.of(VehicleType.DOZER_BREACHER).accessTier());
        for (VehicleType type : VARIANTS) {
            MechPattern pattern = MechPattern.of(type);
            assertEquals(MechPattern.Kind.VEHICLE, pattern.kind());
            assertEquals(pattern, MechPattern.parse(pattern.id()), type.name());
            assertFalse(pattern.starter(), type.name());
            assertTrue(MechPattern.all().contains(pattern), type.name());
        }
    }

    @Test
    void liftClassFollowsTheHullAndMatchesTheWeightClass() {
        assertEquals(LiftClass.MEDIUM, VehicleType.UP_ARMORED_APC.liftClass());
        assertEquals(LiftClass.MEDIUM, VehicleType.FIRE_SUPPORT_APC.liftClass());
        assertEquals(LiftClass.HEAVY, VehicleType.HEAVY_GUN_TANK.liftClass());
        assertEquals(LiftClass.HEAVY, VehicleType.DOZER_BREACHER.liftClass());
        for (VehicleType type : VARIANTS) {
            assertEquals(type.liftClass().name(), MechPattern.weightClass(type).name(), type.name());
        }
    }

    @Test
    void everyVariantIsDrawnAndOfferedAsACard() {
        for (VehicleType type : VARIANTS) {
            assertTrue(type.presentationDeclared(), type.name());
            assertTrue(MechPattern.offerable().contains(MechPattern.of(type)), type.name());
            assertNotNull(MechPatternCardItemPlugin.itemData(MechPattern.of(type).id()), type.name());
        }
    }

    @Test
    void aFactionWeightsUpItsOwnVariant() {
        MechPattern upArmored = MechPattern.of(VehicleType.UP_ARMORED_APC);
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT, MechPatternPicker.weight("hegemony", upArmored));
        assertEquals(MechPatternPicker.BASE_WEIGHT, MechPatternPicker.weight("independent", upArmored));
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT,
                MechPatternPicker.weight("tritachyon", MechPattern.of(VehicleType.HEAVY_GUN_TANK)));
    }

    @Test
    void everyVariantHasABillAboveTheHullItImprovesOn() {
        assertTrue(total(VehicleType.UP_ARMORED_APC) > total(VehicleType.HEAVY_APC));
        assertTrue(total(VehicleType.FIRE_SUPPORT_APC) > total(VehicleType.HEAVY_APC));
        assertTrue(total(VehicleType.HEAVY_GUN_TANK) > total(VehicleType.BREAKTHROUGH_TANK));
        assertTrue(total(VehicleType.DOZER_BREACHER) > total(VehicleType.BREAKTHROUGH_TANK));
    }

    @Test
    void eachVariantTradesSomethingAwayForWhatItGains() {
        VehicleType apc = VehicleType.HEAVY_APC;
        VehicleType armored = VehicleType.UP_ARMORED_APC;
        assertTrue(armored.maxArmor > apc.maxArmor && armored.maxSpeed < apc.maxSpeed);
        assertEquals(apc.capacity, armored.capacity);

        VehicleType support = VehicleType.FIRE_SUPPORT_APC;
        assertEquals(0, support.capacity);
        assertEquals(TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID, support.turretStructureId);
        assertTrue(support.hasTurretWeapon() && support.turretArtFromMount());

        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        VehicleType heavyGun = VehicleType.HEAVY_GUN_TANK;
        assertEquals(TurretCatalogRegistry.TANK_HEAVY_GUN_STRUCTURE_ID, heavyGun.turretStructureId);
        assertTrue(heavyGun.maxSpeed < tank.maxSpeed);
        VehicleType dozer = VehicleType.DOZER_BREACHER;
        assertTrue(dozer.maxArmor > tank.maxArmor && dozer.maxSpeed < tank.maxSpeed);
        assertEquals(TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, dozer.turretStructureId);
    }

    /** Among hulls on the same lift with the same gun, none is at least as good at everything. */
    @Test
    void noFabricableHullStrictlyBeatsAnotherAtTheSameLift() {
        List<VehicleType> hulls = new ArrayList<>();
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.vehicles()) hulls.add(recipe.vehicle());
        for (VehicleType a : hulls) {
            for (VehicleType b : hulls) {
                if (a == b || a.liftClass() != b.liftClass()
                        || !Objects.equals(a.turretStructureId, b.turretStructureId)) continue;
                assertFalse(dominates(a, b), a + " strictly beats " + b);
            }
        }
    }

    private static boolean dominates(VehicleType a, VehicleType b) {
        return a.maxStructure >= b.maxStructure && a.maxArmor >= b.maxArmor
                && a.armorRating >= b.armorRating && a.maxSpeed >= b.maxSpeed && a.capacity >= b.capacity;
    }

    private static int total(VehicleType type) {
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.vehicle(type);
        assertNotNull(recipe, type.name());
        return recipe.cost().lines().stream().mapToInt(FabricationCost.Line::quantity).sum();
    }
}
