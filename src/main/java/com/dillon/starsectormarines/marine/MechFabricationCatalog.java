package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.List;
import java.util.Locale;

/** Discoverable Mech Lab patterns and their material bills. */
public final class MechFabricationCatalog {

    private static final List<Recipe> WEAPONS = List.of(
            weapon(MechWeaponComponent.DUAL_CHAINGUNS, "@gearFabricationStandardIssue", 18, 8, 28, 2),
            weapon(MechWeaponComponent.NOSE_CHAINGUN, "@gearFabricationStandardIssue", 10, 4, 16, 1),
            weapon(MechWeaponComponent.DUAL_LINEAR_CANNONS, "@gearFabricationMilitaryPattern", 24, 12, 34, 5),
            weapon(MechWeaponComponent.SINGLE_HEAVY_CANNON, "@gearFabricationMilitaryPattern", 18, 10, 30, 4),
            weapon(MechWeaponComponent.DUAL_PULSE_LASERS, "@gearFabricationTriTachyonPattern", 30, 18, 26, 12),
            weapon(MechWeaponComponent.DUAL_BASTION_AUTOCANNONS, "@gearFabricationHegemonyPattern", 26, 14, 42, 7),
            weapon(MechWeaponComponent.DEMOLITION_CANNON, "@gearFabricationPatherConversion", 18, 9, 32, 3),
            weapon(MechWeaponComponent.DUAL_MUSTER_AUTOGUNS, "@gearFabricationCommonMarket", 12, 5, 24, 1),
            weapon(MechWeaponComponent.NOSE_MUSTER_AUTOGUN, "@gearFabricationCommonMarket", 7, 3, 14, 1),
            weapon(MechWeaponComponent.QUARRY_BREAKER_CANNON, "@gearFabricationCommonMarket", 9, 5, 28, 1),
            weapon(MechWeaponComponent.SRM_5, "@gearFabricationStandardIssue", 8, 3, 12, 1),
            weapon(MechWeaponComponent.SRM_15, "@gearFabricationStandardIssue", 18, 8, 28, 3),
            weapon(MechWeaponComponent.LRM_5, "@gearFabricationStandardIssue", 10, 4, 14, 2),
            weapon(MechWeaponComponent.LRM_15, "@gearFabricationMilitaryPattern", 22, 10, 30, 5),
            weapon(MechWeaponComponent.SHOULDER_LASER_CANNON, "@gearFabricationTriTachyonPattern", 28, 16, 22, 10),
            weapon(MechWeaponComponent.THERMAL_LANCE, "@gearFabricationLionsGuardPattern", 24, 14, 28, 8),
            weapon(MechWeaponComponent.PIONEER_ROCKET_CRADLE, "@gearFabricationCommonMarket", 8, 3, 12, 1));

    private static final List<Recipe> CHASSIS = List.of(
            chassis(MechVariant.HOUND, "@gearFabricationLightChassisStandardFit", 70, 55, 130, 12),
            chassis(MechVariant.SIROCCO, "@gearFabricationSupportChassisStandardFit", 95, 75, 175, 20),
            chassis(MechVariant.BULWARK, "@gearFabricationHeavyChassisStandardFit", 140, 110, 260, 36));

    /** Stock parts are built too: a replacement plate or engine is still an assembly. */
    private static final List<Recipe> INTERNALS = List.of(
            internal(MechInternal.INDUSTRIAL_ENGINE, "@gearFabricationStandardIssue", 14, 10, 24, 2),
            internal(MechInternal.UPRATED_ENGINE, "@gearFabricationMilitaryPattern", 24, 18, 34, 6),
            internal(MechInternal.MILITARY_ENGINE, "@gearFabricationMilitaryPattern", 34, 26, 44, 12),
            internal(MechInternal.FACTORY_PLATING, "@gearFabricationStandardIssue", 6, 4, 30, 1),
            internal(MechInternal.COMPOSITE_PLATING, "@gearFabricationMilitaryPattern", 12, 8, 46, 4),
            internal(MechInternal.HEAVY_PLATING, "@gearFabricationMilitaryPattern", 18, 12, 70, 8),
            internal(MechInternal.FACTORY_HEAT_SINK, "@gearFabricationStandardIssue", 6, 3, 10, 1),
            internal(MechInternal.DOUBLE_HEAT_SINK, "@gearFabricationMilitaryPattern", 12, 6, 18, 4),
            internal(MechInternal.MILITARY_HEAT_SINK, "@gearFabricationMilitaryPattern", 18, 10, 24, 8),
            internal(MechInternal.AUXILIARY_COOLER, "@gearFabricationStandardIssue", 10, 5, 14, 3));

    /**
     * Bills sit beside the chassis of the same weight class: a hull is cheaper than a walker, and at base commodity
     * prices brings at least as much structure plus armor per credit as the class's mech (see
     * {@code armorEconomyEvidence}). The fire-support APC is the one deliberate exception per lift: it carries a
     * mortar instead of plate, so its armor per Medium lift is below the Sirocco's.
     */
    private static final List<Recipe> VEHICLES = List.of(
            vehicle(VehicleType.HEAVY_APC, "@gearFabricationLightVehicleStandardFit", 38, 28, 88, 3),
            vehicle(VehicleType.MILITIA_TECHNICAL, "@gearFabricationFieldRig", 45, 35, 90, 8),
            vehicle(VehicleType.ESCORT_APC, "@gearFabricationMediumVehicleStandardFit", 75, 60, 150, 14),
            vehicle(VehicleType.BREAKTHROUGH_TANK, "@gearFabricationHeavyVehicleStandardFit", 120, 95, 240, 28),
            // Variants cost more than the hull they improve on: plate in metals, guns in rare metals.
            vehicle(VehicleType.UP_ARMORED_APC, "@gearFabricationMediumVehicleStandardFit", 52, 40, 140, 8),
            vehicle(VehicleType.FIRE_SUPPORT_APC, "@gearFabricationMediumVehicleStandardFit", 52, 40, 115, 11),
            vehicle(VehicleType.HEAVY_GUN_TANK, "@gearFabricationHeavyVehicleStandardFit", 130, 100, 255, 38),
            vehicle(VehicleType.DOZER_BREACHER, "@gearFabricationHeavyVehicleStandardFit", 125, 110, 290, 24),
            // Field rigs are workshop pickups, not issue hulls: the supply pickup's bill is mostly the supplies it is
            // stocked with, the spotter's the rare metals in its salvaged sensor mast.
            vehicle(VehicleType.SUPPLY_TRUCK, "@gearFabricationFieldRig", 55, 25, 70, 4),
            vehicle(VehicleType.SCOUT_CAR, "@gearFabricationFieldRig", 25, 35, 55, 12),
            // Tri-Tachyon's hover line is a corporate pattern, not a rig: lift fans, sensor clusters and drone
            // control are rare metals, and the composite skirt is light on plate.
            vehicle(VehicleType.HOVER_SCOUT, "@gearFabricationTriTachyonPattern", 28, 40, 50, 24),
            vehicle(VehicleType.DRONE_SKIMMER, "@gearFabricationTriTachyonPattern", 48, 55, 85, 42));

    private MechFabricationCatalog() {
    }

    public static List<Recipe> weapons() { return WEAPONS; }

    public static List<Recipe> chassis() { return CHASSIS; }

    public static List<Recipe> vehicles() { return VEHICLES; }

    public static Recipe vehicle(VehicleType type) {
        for (Recipe recipe : VEHICLES) {
            if (recipe.vehicle() == type) return recipe;
        }
        return null;
    }

    public static List<Recipe> internals() { return INTERNALS; }

    public static Recipe internal(MechInternal internal) {
        for (Recipe recipe : INTERNALS) {
            if (recipe.internal() == internal) return recipe;
        }
        return null;
    }

    public static Recipe weapon(MechWeaponComponent component) {
        for (Recipe recipe : WEAPONS) {
            if (recipe.component() == component) return recipe;
        }
        return null;
    }

    public static Recipe chassis(MechVariant variant) {
        for (Recipe recipe : CHASSIS) {
            if (recipe.variant() == variant) return recipe;
        }
        return null;
    }

    private static Recipe weapon(MechWeaponComponent component, String provenance,
                                 int supplies, int machinery, int metals, int rareMetals) {
        return new Recipe("recipe." + component.id, null, provenance,
                component, null, null, null, cost(supplies, machinery, metals, rareMetals));
    }

    private static Recipe internal(MechInternal internal, String provenance,
                                   int supplies, int machinery, int metals, int rareMetals) {
        return new Recipe("recipe." + internal.id, null, provenance,
                null, null, internal, null, cost(supplies, machinery, metals, rareMetals));
    }

    private static Recipe chassis(MechVariant variant, String provenance,
                                  int supplies, int machinery, int metals, int rareMetals) {
        return new Recipe("recipe.chassis." + variant.id, null,
                provenance, null, variant, null, null, cost(supplies, machinery, metals, rareMetals));
    }

    private static FabricationCost cost(int supplies, int machinery,
                                            int metals, int rareMetals) {
        return new FabricationCost(List.of(
                new FabricationCost.Line(Commodities.SUPPLIES, supplies),
                new FabricationCost.Line(Commodities.HEAVY_MACHINERY, machinery),
                new FabricationCost.Line(Commodities.METALS, metals),
                new FabricationCost.Line(Commodities.RARE_METALS, rareMetals)));
    }

    private static Recipe vehicle(VehicleType type, String provenance,
                                  int supplies, int machinery, int metals, int rareMetals) {
        return new Recipe("recipe.vehicle." + type.name().toLowerCase(Locale.ROOT), null,
                provenance, null, null, null, type, cost(supplies, machinery, metals, rareMetals));
    }

    public record Recipe(String id, String displayName, String provenance,
                         MechWeaponComponent component, MechVariant variant,
                         MechInternal internal, VehicleType vehicle, FabricationCost cost) {
        public String displayName() {
            if (displayName != null) return Strings.resolve(displayName);
            if (internal != null) return internal.displayName();
            if (vehicle != null) return CampaignVehicle.typeName(vehicle);
            return component != null ? component.displayName()
                    : Strings.format("gearChassisName", variant.displayName());
        }

        public String provenance() { return Strings.resolve(provenance); }

        public Recipe {
            int targets = (component != null ? 1 : 0) + (variant != null ? 1 : 0)
                    + (internal != null ? 1 : 0) + (vehicle != null ? 1 : 0);
            if (targets != 1) {
                throw new IllegalArgumentException("recipe requires one fabrication target");
            }
        }
    }
}
