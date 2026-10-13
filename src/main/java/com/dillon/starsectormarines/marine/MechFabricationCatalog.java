package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.List;

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

    private MechFabricationCatalog() {
    }

    public static List<Recipe> weapons() { return WEAPONS; }

    public static List<Recipe> chassis() { return CHASSIS; }

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
                component, null, cost(supplies, machinery, metals, rareMetals));
    }

    private static Recipe chassis(MechVariant variant, String provenance,
                                  int supplies, int machinery, int metals, int rareMetals) {
        return new Recipe("recipe.chassis." + variant.id, null,
                provenance, null, variant, cost(supplies, machinery, metals, rareMetals));
    }

    private static FabricationCost cost(int supplies, int machinery,
                                            int metals, int rareMetals) {
        return new FabricationCost(List.of(
                new FabricationCost.Line(Commodities.SUPPLIES, supplies),
                new FabricationCost.Line(Commodities.HEAVY_MACHINERY, machinery),
                new FabricationCost.Line(Commodities.METALS, metals),
                new FabricationCost.Line(Commodities.RARE_METALS, rareMetals)));
    }

    public record Recipe(String id, String displayName, String provenance,
                         MechWeaponComponent component, MechVariant variant,
                         FabricationCost cost) {
        public String displayName() {
            if (displayName != null) return Strings.resolve(displayName);
            return component != null ? component.displayName()
                    : Strings.format("gearChassisName", variant.displayName());
        }

        public String provenance() { return Strings.resolve(provenance); }

        public Recipe {
            if ((component == null) == (variant == null)) {
                throw new IllegalArgumentException("recipe requires one fabrication target");
            }
        }
    }
}
