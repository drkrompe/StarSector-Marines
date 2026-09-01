package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.List;

/** Discoverable Mech Lab patterns and their material bills. */
public final class MechFabricationCatalog {

    private static final List<Recipe> WEAPONS = List.of(
            weapon(MechWeaponComponent.DUAL_CHAINGUNS, "STANDARD ISSUE", 18, 8, 28, 2),
            weapon(MechWeaponComponent.NOSE_CHAINGUN, "STANDARD ISSUE", 10, 4, 16, 1),
            weapon(MechWeaponComponent.DUAL_LINEAR_CANNONS, "MILITARY PATTERN", 24, 12, 34, 5),
            weapon(MechWeaponComponent.SINGLE_HEAVY_CANNON, "MILITARY PATTERN", 18, 10, 30, 4),
            weapon(MechWeaponComponent.DUAL_PULSE_LASERS, "TRI-TACHYON PATTERN", 30, 18, 26, 12),
            weapon(MechWeaponComponent.DUAL_BASTION_AUTOCANNONS, "HEGEMONY PATTERN", 26, 14, 42, 7),
            weapon(MechWeaponComponent.DEMOLITION_CANNON, "PATHER CONVERSION", 18, 9, 32, 3),
            weapon(MechWeaponComponent.DUAL_MUSTER_AUTOGUNS, "COMMON MARKET", 12, 5, 24, 1),
            weapon(MechWeaponComponent.NOSE_MUSTER_AUTOGUN, "COMMON MARKET", 7, 3, 14, 1),
            weapon(MechWeaponComponent.QUARRY_BREAKER_CANNON, "COMMON MARKET", 9, 5, 28, 1),
            weapon(MechWeaponComponent.SRM_5, "STANDARD ISSUE", 8, 3, 12, 1),
            weapon(MechWeaponComponent.SRM_15, "STANDARD ISSUE", 18, 8, 28, 3),
            weapon(MechWeaponComponent.LRM_5, "STANDARD ISSUE", 10, 4, 14, 2),
            weapon(MechWeaponComponent.LRM_15, "MILITARY PATTERN", 22, 10, 30, 5),
            weapon(MechWeaponComponent.SHOULDER_LASER_CANNON, "TRI-TACHYON PATTERN", 28, 16, 22, 10),
            weapon(MechWeaponComponent.THERMAL_LANCE, "LIONS GUARD PATTERN", 24, 14, 28, 8),
            weapon(MechWeaponComponent.PIONEER_ROCKET_CRADLE, "COMMON MARKET", 8, 3, 12, 1));

    private static final List<Recipe> CHASSIS = List.of(
            chassis(MechVariant.HOUND, "LIGHT CHASSIS / STANDARD FIT", 70, 55, 130, 12),
            chassis(MechVariant.SIROCCO, "SUPPORT CHASSIS / STANDARD FIT", 95, 75, 175, 20),
            chassis(MechVariant.BULWARK, "HEAVY CHASSIS / STANDARD FIT", 140, 110, 260, 36));

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
        return new Recipe("recipe." + component.id, component.displayName, provenance,
                component, null, cost(supplies, machinery, metals, rareMetals));
    }

    private static Recipe chassis(MechVariant variant, String provenance,
                                  int supplies, int machinery, int metals, int rareMetals) {
        return new Recipe("recipe.chassis." + variant.id, variant.displayName + " chassis",
                provenance, null, variant, cost(supplies, machinery, metals, rareMetals));
    }

    private static MechFabricationCost cost(int supplies, int machinery,
                                            int metals, int rareMetals) {
        return new MechFabricationCost(List.of(
                new MechFabricationCost.Line(Commodities.SUPPLIES, supplies),
                new MechFabricationCost.Line(Commodities.HEAVY_MACHINERY, machinery),
                new MechFabricationCost.Line(Commodities.METALS, metals),
                new MechFabricationCost.Line(Commodities.RARE_METALS, rareMetals)));
    }

    public record Recipe(String id, String displayName, String provenance,
                         MechWeaponComponent component, MechVariant variant,
                         MechFabricationCost cost) {
        public Recipe {
            if ((component == null) == (variant == null)) {
                throw new IllegalArgumentException("recipe requires one fabrication target");
            }
        }
    }
}
