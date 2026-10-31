package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.VehicleGunCard;
import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each line's fixed variants: their cards, tiers, art gate and trade-offs. */
class VehicleVariantLadderTest {

    private static final List<VehicleType> VARIANTS = List.of(VehicleType.LRM_CARRIER,
            VehicleType.FIRE_SUPPORT_APC, VehicleType.DOZER_BREACHER,
            VehicleType.LASER_TANK);

    @Test
    void eachVariantIsItsOwnVehiclePatternAtAnAuthoredTier() {
        assertEquals(EquipmentAccessTier.ADVANCED, MechPattern.of(VehicleType.LRM_CARRIER).accessTier());
        assertEquals(EquipmentAccessTier.ADVANCED, MechPattern.of(VehicleType.FIRE_SUPPORT_APC).accessTier());
        assertEquals(EquipmentAccessTier.PRESTIGE, MechPattern.of(VehicleType.DOZER_BREACHER).accessTier());
        assertEquals(EquipmentAccessTier.PRESTIGE, MechPattern.of(MechInternal.ACTIVE_PROTECTION).accessTier(),
                "the Parapet's interceptor system is a Prestige part, as the hull was");
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
        assertEquals(LiftClass.MEDIUM, VehicleType.LRM_CARRIER.liftClass());
        assertEquals(LiftClass.MEDIUM, VehicleType.FIRE_SUPPORT_APC.liftClass());
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
        MechPattern carrier = MechPattern.of(VehicleType.LRM_CARRIER);
        assertEquals(MechPatternPicker.FACTION_ISSUE_WEIGHT, MechPatternPicker.weight("hegemony", carrier));
        assertEquals(MechPatternPicker.BASE_WEIGHT, MechPatternPicker.weight("independent", carrier));
        // Tri-Tachyon is known for its hover line, not for anybody's tank.
        assertEquals(MechPatternPicker.BASE_WEIGHT,
                MechPatternPicker.weight("tritachyon", MechPattern.of(VehicleType.BREAKTHROUGH_TANK)));
    }

    @Test
    void eachVariantTradesSomethingAwayForWhatItGains() {
        VehicleType support = VehicleType.FIRE_SUPPORT_APC;
        assertEquals(0, support.capacity);
        assertEquals(TurretCatalogRegistry.VEHICLE_MORTAR_STRUCTURE_ID, support.turretStructureId);
        assertTrue(support.hasTurretWeapon());

        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        VehicleType dozer = VehicleType.DOZER_BREACHER;
        assertTrue(dozer.maxArmor > tank.maxArmor && dozer.maxSpeed < tank.maxSpeed);
        assertEquals(TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, dozer.turretStructureId);
        VehicleStockFit parapet = VehicleStockFit.PARAPET;
        assertEquals(tank, parapet.type, "the Parapet is the Onager with active protection on its rack");
        assertTrue(MechCompartment.systems(parapet.compartment).activeProtection());
        assertTrue(MechCompartment.systems(parapet.compartment).speedFactor() < 1f);
    }

    /**
     * The APC is one chassis: its variants share its footprint, handling and durability and differ only in the turret
     * their gun card takes and the troop bay that turret replaces. A tougher or faster APC is a fit, never a hull.
     */
    @Test
    void theApcVariantsAreTheApcChassisWithATypedTurretInPlaceOfTheBay() {
        VehicleType apc = VehicleType.HEAVY_APC;
        assertTrue(apc.capacity > 0);
        assertEquals(VehicleGunCard.SMALL, VehicleGunCard.of(apc));
        for (VehicleType variant : List.of(VehicleType.FIRE_SUPPORT_APC, VehicleType.LRM_CARRIER)) {
            assertTrue(variant.isApc(), variant.name());
            assertEquals(0, variant.capacity, variant.name());
            assertTrue(VehicleGunCard.of(variant).hardpoint != null && VehicleGunCard.of(variant).rows > 1, variant.name());
            assertEquals(apc.maxStructure, variant.maxStructure, variant.name());
            assertEquals(apc.maxArmor, variant.maxArmor, variant.name());
            assertEquals(apc.armorRating, variant.armorRating, variant.name());
            assertEquals(apc.maxSpeed, variant.maxSpeed, variant.name());
            assertEquals(apc.accel, variant.accel, variant.name());
            assertEquals(apc.visualLengthCells, variant.visualLengthCells, variant.name());
            assertEquals(apc.visualWidthCells, variant.visualWidthCells, variant.name());
        }
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

    /**
     * The direction says a vehicle costs less material for the armor it brings than the mech of its weight class.
     * Priced at the game's base commodity prices (supplies 100, heavy machinery 150, metals 30, rare metals 200,
     * from {@code commodities.csv}), every hull brings at least as much structure plus armor per credit as its
     * class's mech. A role hull is bought for its job rather than its plate (the fire-support APC's mortar, the
     * scout car's sight, the supply truck's stock), so it is held only to costing less than the mech.
     */
    @Test
    void everyHullBringsAtLeastTheMechsArmorPerCredit() {
        for (VehicleType type : VehicleType.values()) {
            if (MechFabricationCatalog.vehicle(type) == null) continue;
            MechVariant mech = mechOf(type.liftClass());
            // A walker that can fight: the frame and its standard parts.
            float mechCredits = MechFabricationCatalog.standardFitCost(mech).credits();
            float vehicleCredits = MechFabricationCatalog.vehicle(type).cost().credits();
            if (ROLE_HULLS.contains(type)) {
                assertTrue(vehicleCredits < mechCredits, type + " costs " + vehicleCredits + " against " + mech);
                continue;
            }
            float vehicle = (type.maxStructure + type.maxArmor) / vehicleCredits;
            float walker = (mech.maxStructure + mech.armorCapacity) / mechCredits;
            assertTrue(vehicle >= walker, type + " " + vehicle + " armor/credit against " + mech + " " + walker);
        }
    }

    private static final Set<VehicleType> ROLE_HULLS =
            EnumSet.of(VehicleType.FIRE_SUPPORT_APC, VehicleType.SCOUT_CAR, VehicleType.SUPPLY_TRUCK,
                    VehicleType.HOVER_SCOUT, VehicleType.DRONE_SKIMMER, VehicleType.LRM_CARRIER,
                    VehicleType.SRM_TECHNICAL);

    private static MechVariant mechOf(LiftClass lift) {
        for (MechVariant variant : MechVariant.values()) if (variant.liftClass() == lift) return variant;
        throw new IllegalArgumentException(lift.name());
    }

    private static boolean dominates(VehicleType a, VehicleType b) {
        return a.maxStructure >= b.maxStructure && a.maxArmor >= b.maxArmor
                && a.armorRating >= b.armorRating && a.maxSpeed >= b.maxSpeed && a.capacity >= b.capacity;
    }
}

