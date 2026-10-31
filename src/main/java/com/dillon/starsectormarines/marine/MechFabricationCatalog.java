package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Locale;

/**
 * Discoverable Mech Lab patterns and their credit prices. Parts are priced from their worth
 * ({@link #weaponValue}, {@link #internalValue}); chassis and vehicles are authored.
 */
public final class MechFabricationCatalog {

    /** Each chassis's authored standard-fit bill: what a machine that can fight costs, frame and parts. */
    private static final Map<MechVariant, FabricationCost> STANDARD_FIT = new EnumMap<>(MechVariant.class);

    private static final List<Recipe> WEAPONS = List.of(
            weapon(MechWeaponComponent.CHAINGUN, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.CHAINGUN_HF, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.HC_10, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.HC_20, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.PL_2_AP, "@gearFabricationTriTachyonPattern"),
            weapon(MechWeaponComponent.AC_6, "@gearFabricationHegemonyPattern"),
            weapon(MechWeaponComponent.DC_5, "@gearFabricationPatherConversion"),
            weapon(MechWeaponComponent.AC_6_QS, "@gearFabricationCommonMarket"),
            weapon(MechWeaponComponent.DC_4_QS, "@gearFabricationCommonMarket"),
            weapon(MechWeaponComponent.SRM_5, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.SRM_15, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_5, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_15, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.SRM_5_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.SRM_15_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_5_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_15_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LL_3, "@gearFabricationTriTachyonPattern"),
            weapon(MechWeaponComponent.THERMAL_LANCE, "@gearFabricationLionsGuardPattern"),
            weapon(MechWeaponComponent.SRM_3_QS, "@gearFabricationCommonMarket"),
            weapon(MechWeaponComponent.LIGHT_MACHINE_GUN, "@gearFabricationCommonMarket"),
            weapon(MechWeaponComponent.BL_1, "@gearFabricationTriTachyonPattern"),
            weapon(MechWeaponComponent.PL_3, "@gearFabricationTriTachyonPattern"),
            weapon(MechWeaponComponent.BL_2, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.PL_3_SWEEP, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.PL_2, "@gearFabricationTriTachyonPattern"),
            weapon(MechWeaponComponent.PL_1, "@gearFabricationTriTachyonPattern"));

    private static final List<Recipe> CHASSIS = List.of(
            chassis(MechVariant.HOUND, "@gearFabricationLightChassisStandardFit", 21510),
            chassis(MechVariant.WARDEN, "@gearFabricationSupportChassisStandardFit", 30040),
            chassis(MechVariant.BULWARK, "@gearFabricationHeavyChassisStandardFit", 45540));

    /** Stock parts are built too: a replacement plate or engine is still an assembly. Bills are derived. */
    private static final List<Recipe> INTERNALS = List.of(
            internal(MechInternal.INDUSTRIAL_ENGINE, "@gearFabricationStandardIssue"),
            internal(MechInternal.UPRATED_ENGINE, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.MILITARY_ENGINE, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.FACTORY_PLATING, "@gearFabricationStandardIssue"),
            internal(MechInternal.COMPOSITE_PLATING, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.HEAVY_PLATING, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.FACTORY_CAPACITOR_BANK, "@gearFabricationStandardIssue"),
            internal(MechInternal.DOUBLE_CAPACITOR_BANK, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.MILITARY_CAPACITOR_BANK, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.AUXILIARY_VENT_ARRAY, "@gearFabricationStandardIssue"),
            internal(MechInternal.STANDARD_LOADER, "@gearFabricationStandardIssue"),
            internal(MechInternal.ACCELERATED_LOADER, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.SPALL_LINER, "@gearFabricationStandardIssue"),
            internal(MechInternal.READY_RACK, "@gearFabricationStandardIssue"),
            internal(MechInternal.SENSOR_PAIR, "@gearFabricationMilitaryPattern"),
            internal(MechInternal.ACTIVE_PROTECTION, "@gearFabricationMilitaryPattern"));

    /**
     * Prices sit beside the chassis of the same weight class: a hull is cheaper than a walker, and brings at least as much structure plus armor per credit as the class's mech (see
     * {@code armorEconomyEvidence}). The fire-support APC is the one deliberate exception per lift: it carries a
     * mortar instead of plate, so its armor per Medium lift is below the Warden's.
     */
    private static final List<Recipe> VEHICLES = List.of(
            vehicle(VehicleType.HEAVY_APC, "@gearFabricationLightVehicleStandardFit", 11220),
            vehicle(VehicleType.MILITIA_TECHNICAL, "@gearFabricationFieldRig", 14050),
            vehicle(VehicleType.BREAKTHROUGH_TANK, "@gearFabricationHeavyVehicleStandardFit", 39050),
            // Variants cost more than the hull they improve on.
            vehicle(VehicleType.FIRE_SUPPORT_APC, "@gearFabricationMediumVehicleStandardFit", 16810),
            vehicle(VehicleType.DOZER_BREACHER, "@gearFabricationHeavyVehicleStandardFit", 42540),
            // The laser tank trades the shells for an emitter and the plant that feeds it: transplutonics, not plate.
            vehicle(VehicleType.LASER_TANK, "@gearFabricationHeavyVehicleStandardFit", 45000),
            // The PD laser tank keeps the hull's plate and feeds a plainer emitter from three cells and a radar.
            // Field rigs are workshop pickups, not issue hulls: the supply pickup is mostly bodywork, the spotter's
            // bill the transplutonics in its salvaged sensor mast.
            vehicle(VehicleType.SUPPLY_TRUCK, "@gearFabricationFieldRig", 12110),
            vehicle(VehicleType.SCOUT_CAR, "@gearFabricationFieldRig", 11760),
            // The rocket pickup is a stripped pickup plus a pod.
            vehicle(VehicleType.SRM_TECHNICAL, "@gearFabricationFieldRig", 11250),
            // The missile carrier is the APC chassis plus its LRM rack.
            vehicle(VehicleType.LRM_CARRIER, "@gearFabricationMediumVehicleStandardFit", 22400),
            // Tri-Tachyon's hover line is a corporate pattern, not a rig: lift fans, sensor clusters and drone
            // control are transplutonics, and the composite skirt is light on plate.
            vehicle(VehicleType.HOVER_SCOUT, "@gearFabricationTriTachyonPattern", 15060),
            vehicle(VehicleType.DRONE_SKIMMER, "@gearFabricationTriTachyonPattern", 24000));

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

    /**
     * What the chassis costs fitted to its standard roll-out: the frame's bill plus its parts'.
     * Repairs and the armor economy price a fighting machine, so they read this, not the frame.
     */
    public static FabricationCost standardFitCost(MechVariant variant) {
        return STANDARD_FIT.get(variant);
    }

    public static Recipe chassis(MechVariant variant) {
        for (Recipe recipe : CHASSIS) {
            if (recipe.variant() == variant) return recipe;
        }
        return null;
    }

    private static Recipe weapon(MechWeaponComponent component, String provenance) {
        return new Recipe("recipe." + component.id, null, provenance,
                component, null, null, null, weaponCost(component));
    }

    private static Recipe internal(MechInternal internal, String provenance) {
        return new Recipe("recipe." + internal.id, null, provenance,
                null, null, internal, null, internalCost(internal));
    }

    /**
     * A chassis is sold as a bare frame: the authored bill is the standard-fit machine's, less
     * what its removable weapons, engine and compartment parts cost as parts. Plating and
     * factory-locked hardware are the frame and stay in its price.
     */
    private static Recipe chassis(MechVariant variant, String provenance, int credits) {
        STANDARD_FIT.put(variant, new FabricationCost(credits));
        int frame = credits;
        MechFrameBudget.Fit stock = MechFrameBudget.Fit.stock(variant);
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent weapon = stock.weaponAt(slot);
            if (weapon != null && !MechFrameBudget.integral(variant, slot)) frame -= weaponCost(weapon).credits();
        }
        frame -= internalCost(stock.engine()).credits();
        for (MechInternal part : stock.compartment()) frame -= internalCost(part).credits();
        return new Recipe("recipe.chassis." + variant.id, null, provenance, null, variant, null, null,
                new FabricationCost(Math.max(MINIMUM_FRAME, frame)));
    }

    /** A bare frame is never priced below this, however much of its standard fit is removable parts. */
    private static final int MINIMUM_FRAME = 500;

    private static Recipe vehicle(VehicleType type, String provenance, int credits) {
        return new Recipe("recipe.vehicle." + type.name().toLowerCase(Locale.ROOT), null,
                provenance, null, null, null, type, new FabricationCost(credits));
    }

    /*
     * Hardware is priced, not authored: a part is worth what its access tier and its mass make it, in
     * credits.
     * Mass is the authored measure of how much weapon a part is (footprint, tier weight and heavy
     * build), so a bigger or rarer gun costs more without a second table to keep in step with it.
     * Chassis and vehicles stay authored: their bills carry the armor-per-credit parity that
     * armorEconomyEvidence reads.
     */

    /** Credits per unit of weapon mass at the common tier. */
    private static final float CREDITS_PER_WEAPON_MASS = 250f;

    /** What a better pattern multiplies its part's worth by. */
    static float tierMultiplier(EquipmentAccessTier tier) {
        return switch (tier) {
            case COMMON -> 1f;
            case ADVANCED -> 1.6f;
            case PRESTIGE -> 2.4f;
            case RESTRICTED -> 3.2f;
        };
    }

    /** A weapon's worth in credits: its mass at the common rate, times its tier. */
    public static float weaponValue(MechWeaponComponent component) {
        return CREDITS_PER_WEAPON_MASS * MechFrameBudget.mass(component)
                * tierMultiplier(MechPattern.of(component).accessTier());
    }

    /**
     * An internal's worth in credits: what its role's stock part is worth, times its tier. Internals have
     * no common mass scale (plating's is in plating units, a stock part's is zero), so the role sets the base.
     */
    public static float internalValue(MechInternal internal) {
        float base = switch (internal.role) {
            case ENGINE -> 4000f;
            case PLATING -> 2300f;
            case CAPACITOR_BANK, VENT_ARRAY -> 1550f;
            // A loader is a small autoforge: guidance stock and feed mechanism.
            case LOADER -> 1200f;
            // A liner or a ready rack is a common bolt-on: the price it had as a vehicle kit.
            // The sensor pair and active protection keep the price they had as vehicle kits too.
            case LINER, READY_RACK, DRONE_BAY, ACTIVE_PROTECTION -> 900f;
        };
        return base * tierMultiplier(internal.tier);
    }

    private static FabricationCost weaponCost(MechWeaponComponent component) {
        return price(weaponValue(component));
    }

    private static FabricationCost internalCost(MechInternal internal) {
        return price(internalValue(internal));
    }

    /** A worth in credits as a price, to the nearest ten. */
    private static FabricationCost price(float value) {
        return new FabricationCost(Math.max(10, Math.round(value / 10f) * 10));
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
