package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Locale;

/**
 * Discoverable Mech Lab patterns and their material bills. Every bill is metals, heavy machinery and
 * transplutonics (the game's {@code rare_metals}); the lab spends no supplies.
 */
public final class MechFabricationCatalog {

    /** Each chassis's authored standard-fit bill: what a machine that can fight costs, frame and parts. */
    private static final Map<MechVariant, FabricationCost> STANDARD_FIT = new EnumMap<>(MechVariant.class);

    private static final List<Recipe> WEAPONS = List.of(
            weapon(MechWeaponComponent.CHAINGUN, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.NOSE_CHAINGUN, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.CHAINGUN_HF, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.NOSE_CHAINGUN_HF, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.LINEAR_CANNON, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.SINGLE_HEAVY_CANNON, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.PULSE_LASER, "@gearFabricationTriTachyonPattern"),
            weapon(MechWeaponComponent.BASTION_AUTOCANNON, "@gearFabricationHegemonyPattern"),
            weapon(MechWeaponComponent.DEMOLITION_CANNON, "@gearFabricationPatherConversion"),
            weapon(MechWeaponComponent.MUSTER_AUTOGUN, "@gearFabricationCommonMarket"),
            weapon(MechWeaponComponent.NOSE_MUSTER_AUTOGUN, "@gearFabricationCommonMarket"),
            weapon(MechWeaponComponent.QUARRY_BREAKER_CANNON, "@gearFabricationCommonMarket"),
            weapon(MechWeaponComponent.SRM_5, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.SRM_15, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_5, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_15, "@gearFabricationMilitaryPattern"),
            weapon(MechWeaponComponent.SRM_5_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.SRM_15_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_5_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.LRM_15_FRAG, "@gearFabricationStandardIssue"),
            weapon(MechWeaponComponent.SHOULDER_LASER_CANNON, "@gearFabricationTriTachyonPattern"),
            weapon(MechWeaponComponent.THERMAL_LANCE, "@gearFabricationLionsGuardPattern"),
            weapon(MechWeaponComponent.PIONEER_ROCKET_CRADLE, "@gearFabricationCommonMarket"));

    private static final List<Recipe> CHASSIS = List.of(
            chassis(MechVariant.HOUND, "@gearFabricationLightChassisStandardFit", 247, 78, 12),
            chassis(MechVariant.WARDEN, "@gearFabricationSupportChassisStandardFit", 333, 107, 20),
            chassis(MechVariant.BULWARK, "@gearFabricationHeavyChassisStandardFit", 493, 157, 36));

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
            internal(MechInternal.ACCELERATED_LOADER, "@gearFabricationMilitaryPattern"));

    /** Bolt-on vehicle kits; bills are derived like a part's. */
    private static final List<Recipe> KITS = List.of(
            kit(VehicleKit.APPLIQUE_PLATE, "@gearFabricationStandardIssue"),
            kit(VehicleKit.SPALL_LINER, "@gearFabricationStandardIssue"),
            kit(VehicleKit.READY_RACK, "@gearFabricationStandardIssue"),
            kit(VehicleKit.SPACED_SKIRTS, "@gearFabricationMilitaryPattern"));

    /**
     * Bills sit beside the chassis of the same weight class: a hull is cheaper than a walker, and at base commodity
     * prices brings at least as much structure plus armor per credit as the class's mech (see
     * {@code armorEconomyEvidence}). The fire-support APC is the one deliberate exception per lift: it carries a
     * mortar instead of plate, so its armor per Medium lift is below the Warden's.
     */
    private static final List<Recipe> VEHICLES = List.of(
            vehicle(VehicleType.HEAVY_APC, "@gearFabricationLightVehicleStandardFit", 149, 41, 3),
            vehicle(VehicleType.MILITIA_TECHNICAL, "@gearFabricationFieldRig", 165, 50, 8),
            vehicle(VehicleType.ESCORT_APC, "@gearFabricationMediumVehicleStandardFit", 275, 85, 14),
            vehicle(VehicleType.BREAKTHROUGH_TANK, "@gearFabricationHeavyVehicleStandardFit", 440, 135, 28),
            // Variants cost more than the hull they improve on: plate in metals, guns in transplutonics.
            vehicle(VehicleType.UP_ARMORED_APC, "@gearFabricationMediumVehicleStandardFit", 227, 57, 8),
            vehicle(VehicleType.FIRE_SUPPORT_APC, "@gearFabricationMediumVehicleStandardFit", 202, 57, 11),
            vehicle(VehicleType.DOZER_BREACHER, "@gearFabricationHeavyVehicleStandardFit", 498, 152, 24),
            // The screened tank is the breakthrough tank plus its interceptor system: launchers and their sensor
            // are machinery and transplutonics, not plate.
            vehicle(VehicleType.SCREENED_TANK, "@gearFabricationHeavyVehicleStandardFit", 448, 157, 44),
            // The laser tank trades the shells for an emitter and the plant that feeds it: transplutonics, not plate.
            vehicle(VehicleType.LASER_TANK, "@gearFabricationHeavyVehicleStandardFit", 430, 150, 48),
            // The PD laser tank keeps the hull's plate and feeds a plainer emitter from three cells and a radar.
            vehicle(VehicleType.PD_LASER_TANK, "@gearFabricationHeavyVehicleStandardFit", 445, 145, 36),
            // Field rigs are workshop pickups, not issue hulls: the supply pickup is mostly bodywork, the spotter's
            // bill the transplutonics in its salvaged sensor mast.
            vehicle(VehicleType.SUPPLY_TRUCK, "@gearFabricationFieldRig", 162, 43, 4),
            vehicle(VehicleType.SCOUT_CAR, "@gearFabricationFieldRig", 97, 43, 12),
            // The rocket pickup is a stripped pickup plus a pod.
            vehicle(VehicleType.SRM_TECHNICAL, "@gearFabricationFieldRig", 135, 40, 6),
            // The missile carrier is the heavy APC's hull plus two LRM-15 racks.
            vehicle(VehicleType.LRM_CARRIER, "@gearFabricationMediumVehicleStandardFit", 285, 75, 13),
            // Tri-Tachyon's hover line is a corporate pattern, not a rig: lift fans, sensor clusters and drone
            // control are transplutonics, and the composite skirt is light on plate.
            vehicle(VehicleType.HOVER_SCOUT, "@gearFabricationTriTachyonPattern", 97, 49, 24),
            vehicle(VehicleType.DRONE_SKIMMER, "@gearFabricationTriTachyonPattern", 165, 71, 42));

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

    public static List<Recipe> kits() { return KITS; }

    public static Recipe kit(VehicleKit kit) {
        for (Recipe recipe : KITS) {
            if (recipe.kit() == kit) return recipe;
        }
        return null;
    }

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
                component, null, null, null, null, weaponCost(component));
    }

    private static Recipe internal(MechInternal internal, String provenance) {
        return new Recipe("recipe." + internal.id, null, provenance,
                null, null, internal, null, null, internalCost(internal));
    }

    /**
     * A chassis is sold as a bare frame: the authored bill is the standard-fit machine's, less
     * what its removable weapons, engine and compartment parts cost as parts. Plating and
     * factory-locked hardware are the frame and stay in its price.
     */
    private static Recipe chassis(MechVariant variant, String provenance,
                                  int metals, int machinery, int transplutonics) {
        STANDARD_FIT.put(variant, cost(metals, machinery, transplutonics));
        int[] bill = {metals, machinery, transplutonics};
        MechFrameBudget.Fit stock = MechFrameBudget.Fit.stock(variant);
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent weapon = stock.weaponAt(slot);
            if (weapon != null && !MechFrameBudget.integral(variant, slot)) less(bill, weaponCost(weapon));
        }
        less(bill, internalCost(stock.engine()));
        for (MechInternal part : stock.compartment()) less(bill, internalCost(part));
        return new Recipe("recipe.chassis." + variant.id, null,
                provenance, null, variant, null, null, null, cost(bill[0], bill[1], bill[2]));
    }

    /** Takes a part's bill off a frame's, never below one unit of any material. */
    private static void less(int[] bill, FabricationCost part) {
        for (FabricationCost.Line line : part.lines()) {
            int index = switch (line.commodityId()) {
                case Commodities.METALS -> 0;
                case Commodities.HEAVY_MACHINERY -> 1;
                default -> 2;
            };
            bill[index] = Math.max(1, bill[index] - line.quantity());
        }
    }

    private static Recipe vehicle(VehicleType type, String provenance,
                                  int metals, int machinery, int transplutonics) {
        return new Recipe("recipe.vehicle." + type.name().toLowerCase(Locale.ROOT), null,
                provenance, null, null, null, type, null, cost(metals, machinery, transplutonics));
    }

    private static Recipe kit(VehicleKit kit, String provenance) {
        return new Recipe("recipe." + kit.id, null, provenance, null, null, null, null, kit, kitCost(kit));
    }

    /**
     * A kit is priced like a part: its tier's worth, in what it is made of. Plate and skirts are mostly
     * metal, a liner is woven and bonded, a ready rack is racking and feed.
     */
    private static FabricationCost kitCost(VehicleKit kit) {
        float value = kitValue(kit);
        return switch (kit) {
            case APPLIQUE_PLATE, SPACED_SKIRTS -> split(value, .70f, .20f, .10f);
            case SPALL_LINER -> split(value, .45f, .30f, .25f);
            case READY_RACK -> split(value, .40f, .45f, .15f);
        };
    }

    /*
     * Hardware is priced, not authored: a part is worth what its access tier and its mass make it, in
     * credits at base commodity prices, and what it is made of decides which materials pay for that.
     * Mass is the authored measure of how much weapon a part is (footprint, tier weight and heavy
     * build), so a bigger or rarer gun costs more without a second table to keep in step with it.
     * Chassis and vehicles stay authored: their bills carry the armor-per-credit parity that
     * armorEconomyEvidence reads.
     */

    /** Credits per unit of weapon mass at the common tier. */
    private static final float CREDITS_PER_WEAPON_MASS = 250f;

    private static final int METALS_PRICE = 30;
    private static final int MACHINERY_PRICE = 150;
    private static final int TRANSPLUTONICS_PRICE = 200;

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
        };
        return base * tierMultiplier(internal.tier);
    }

    /** A kit's worth in credits: a common bolt-on's, times its tier. */
    public static float kitValue(VehicleKit kit) {
        return 900f * tierMultiplier(MechPattern.of(kit).accessTier());
    }

    private static FabricationCost weaponCost(MechWeaponComponent component) {
        float value = weaponValue(component);
        return switch (component.hardpointType) {
            // Breech, barrel and feed: steel and mechanism.
            case BALLISTIC -> split(value, .45f, .45f, .10f);
            // Rails and loaders, and the guidance in every round.
            case MISSILE -> split(value, .30f, .40f, .30f);
            // Emitters, focusing and capacitance.
            default -> split(value, .15f, .30f, .55f);
        };
    }

    private static FabricationCost internalCost(MechInternal internal) {
        float value = internalValue(internal);
        return switch (internal.role) {
            case ENGINE -> split(value, .25f, .60f, .15f);
            case PLATING -> split(value, .80f, .15f, .05f);
            case CAPACITOR_BANK, VENT_ARRAY -> split(value, .20f, .25f, .55f);
            case LOADER -> split(value, .30f, .40f, .30f);
        };
    }

    /** Pays {@code value} credits in the three materials by share; every material is at least one unit. */
    private static FabricationCost split(float value, float metals, float machinery, float transplutonics) {
        return cost(units(value * metals, METALS_PRICE), units(value * machinery, MACHINERY_PRICE),
                units(value * transplutonics, TRANSPLUTONICS_PRICE));
    }

    private static int units(float credits, int price) {
        return Math.max(1, Math.round(credits / price));
    }

    private static FabricationCost cost(int metals, int machinery, int transplutonics) {
        return new FabricationCost(List.of(
                new FabricationCost.Line(Commodities.METALS, metals),
                new FabricationCost.Line(Commodities.HEAVY_MACHINERY, machinery),
                new FabricationCost.Line(Commodities.RARE_METALS, transplutonics)));
    }

    public record Recipe(String id, String displayName, String provenance,
                         MechWeaponComponent component, MechVariant variant,
                         MechInternal internal, VehicleType vehicle, VehicleKit kit, FabricationCost cost) {
        public String displayName() {
            if (displayName != null) return Strings.resolve(displayName);
            if (internal != null) return internal.displayName();
            if (kit != null) return kit.displayName();
            if (vehicle != null) return CampaignVehicle.typeName(vehicle);
            return component != null ? component.displayName()
                    : Strings.format("gearChassisName", variant.displayName());
        }

        public String provenance() { return Strings.resolve(provenance); }

        public Recipe {
            int targets = (component != null ? 1 : 0) + (variant != null ? 1 : 0)
                    + (internal != null ? 1 : 0) + (vehicle != null ? 1 : 0) + (kit != null ? 1 : 0);
            if (targets != 1) {
                throw new IllegalArgumentException("recipe requires one fabrication target");
            }
        }
    }
}
