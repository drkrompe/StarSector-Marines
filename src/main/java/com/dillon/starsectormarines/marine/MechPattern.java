package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The permanent right to fabricate one mech chassis, weapon component, internal, vehicle or vehicle kit. A company that
 * knows a pattern may build as many assemblies as it can pay for; the assemblies are finite
 * and losable, the pattern is neither. The id is the cargo payload of a mech pattern card.
 */
public record MechPattern(Kind kind, String definitionId) {

    public enum Kind { CHASSIS, WEAPON, INTERNAL, VEHICLE, KIT }

    public enum WeightClass { LIGHT, MEDIUM, HEAVY }

    public static final String PREFIX = "mech-pattern:";

    /** Authored access bands. Starter patterns are listed but are never offered as cards. */
    private static final Map<MechVariant, EquipmentAccessTier> CHASSIS_TIERS = new EnumMap<>(MechVariant.class);
    private static final Map<MechWeaponComponent, EquipmentAccessTier> WEAPON_TIERS =
            new EnumMap<>(MechWeaponComponent.class);
    private static final Map<VehicleType, EquipmentAccessTier> VEHICLE_TIERS = new EnumMap<>(VehicleType.class);
    private static final Map<VehicleKit, EquipmentAccessTier> KIT_TIERS = new EnumMap<>(VehicleKit.class);

    static {
        CHASSIS_TIERS.put(MechVariant.HOUND, EquipmentAccessTier.COMMON);
        CHASSIS_TIERS.put(MechVariant.WARDEN, EquipmentAccessTier.ADVANCED);
        CHASSIS_TIERS.put(MechVariant.BULWARK, EquipmentAccessTier.PRESTIGE);

        WEAPON_TIERS.put(MechWeaponComponent.SRM_5, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.AC_6_QS, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.SRM_3_QS, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.DC_4_QS, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.LIGHT_MACHINE_GUN, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.BL_1, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.BL_2, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.PL_3, EquipmentAccessTier.PRESTIGE);
        WEAPON_TIERS.put(MechWeaponComponent.PL_3_SWEEP, EquipmentAccessTier.PRESTIGE);
        WEAPON_TIERS.put(MechWeaponComponent.PL_2, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.PL_1, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.CHAINGUN, EquipmentAccessTier.COMMON);
        // The better chaingun: spin-up, refill and damage are what a better rotary gun buys.
        WEAPON_TIERS.put(MechWeaponComponent.CHAINGUN_HF, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.LRM_5, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.SRM_15, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.LRM_15, EquipmentAccessTier.ADVANCED);
        // A fragmentation rack is a sidegrade of its anti-armor rack: same tier, same room.
        WEAPON_TIERS.put(MechWeaponComponent.SRM_5_FRAG, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.LRM_5_FRAG, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.SRM_15_FRAG, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.LRM_15_FRAG, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.HC_10, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.HC_20, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.DC_5, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.AC_6, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.PL_2_AP, EquipmentAccessTier.PRESTIGE);
        WEAPON_TIERS.put(MechWeaponComponent.LL_3, EquipmentAccessTier.PRESTIGE);
        WEAPON_TIERS.put(MechWeaponComponent.THERMAL_LANCE, EquipmentAccessTier.PRESTIGE);

        // No starter vehicle: a company earns its first hull. The weight class follows the tier.
        VEHICLE_TIERS.put(VehicleType.HEAVY_APC, EquipmentAccessTier.COMMON);
        VEHICLE_TIERS.put(VehicleType.MILITIA_TECHNICAL, EquipmentAccessTier.ADVANCED);
        VEHICLE_TIERS.put(VehicleType.BREAKTHROUGH_TANK, EquipmentAccessTier.PRESTIGE);
        // The ladder above each line's first hull: each variant is its own card.
        VEHICLE_TIERS.put(VehicleType.FIRE_SUPPORT_APC, EquipmentAccessTier.ADVANCED);
        VEHICLE_TIERS.put(VehicleType.DOZER_BREACHER, EquipmentAccessTier.PRESTIGE);
        VEHICLE_TIERS.put(VehicleType.LASER_TANK, EquipmentAccessTier.PRESTIGE);
        // The light roles: lines of their own, one hull each.
        VEHICLE_TIERS.put(VehicleType.SUPPLY_TRUCK, EquipmentAccessTier.COMMON);
        VEHICLE_TIERS.put(VehicleType.SCOUT_CAR, EquipmentAccessTier.ADVANCED);
        // The missile carriers: a rocket rig any workshop bolts together, and the APC line's long arm against armor.
        VEHICLE_TIERS.put(VehicleType.SRM_TECHNICAL, EquipmentAccessTier.COMMON);
        VEHICLE_TIERS.put(VehicleType.LRM_CARRIER, EquipmentAccessTier.ADVANCED);
        // Tri-Tachyon's hover line: a screen first, the drone carrier above it.
        VEHICLE_TIERS.put(VehicleType.HOVER_SCOUT, EquipmentAccessTier.ADVANCED);
        VEHICLE_TIERS.put(VehicleType.DRONE_SKIMMER, EquipmentAccessTier.PRESTIGE);

        // Vehicle kits: the liner and magazine any workshop makes; the rest are military patterns.
        KIT_TIERS.put(VehicleKit.SPALL_LINER, EquipmentAccessTier.COMMON);
        KIT_TIERS.put(VehicleKit.READY_RACK, EquipmentAccessTier.COMMON);
        KIT_TIERS.put(VehicleKit.SENSOR_PAIR, EquipmentAccessTier.ADVANCED);
        // The upgrade on a hull's own systems, a faster feed, as the mech part it echoes.
        KIT_TIERS.put(VehicleKit.ACCELERATED_LOADER, EquipmentAccessTier.ADVANCED);
        // The interceptor system the Parapet was built around: a Prestige pattern, as that hull was.
        KIT_TIERS.put(VehicleKit.ACTIVE_PROTECTION, EquipmentAccessTier.PRESTIGE);
    }

    /**
     * What a new company knows, the Kit Ladder's start ({@link KitLadder}): the Hound, a light machine gun and the
     * fragmentation SRM-5 to arm it against the men the first operations are fought against, and the stock frame's
     * internals every chassis is built with. The chaingun and the anti-armor SRM-5 go on sale after Counterattack.
     */
    public static final List<MechPattern> STARTER = List.of(
            of(MechVariant.HOUND),
            of(MechWeaponComponent.LIGHT_MACHINE_GUN),
            of(MechWeaponComponent.SRM_5_FRAG),
            of(MechInternal.INDUSTRIAL_ENGINE),
            of(MechInternal.FACTORY_PLATING),
            of(MechInternal.FACTORY_CAPACITOR_BANK),
            of(MechInternal.STANDARD_LOADER));

    public static MechPattern of(MechVariant variant) {
        return new MechPattern(Kind.CHASSIS, variant.id);
    }

    public static MechPattern of(MechWeaponComponent component) {
        return new MechPattern(Kind.WEAPON, component.id);
    }

    public static MechPattern of(MechInternal internal) {
        return new MechPattern(Kind.INTERNAL, internal.id);
    }

    public static MechPattern of(VehicleType type) {
        return new MechPattern(Kind.VEHICLE, type.name().toLowerCase(Locale.ROOT));
    }

    public static MechPattern of(VehicleKit kit) {
        return new MechPattern(Kind.KIT, kit.id);
    }

    public String id() {
        return PREFIX + switch (kind) {
            case CHASSIS -> "chassis:";
            case WEAPON -> "weapon:";
            case INTERNAL -> "internal:";
            case VEHICLE -> "vehicle:";
            case KIT -> "kit:";
        } + definitionId;
    }

    public MechVariant variant() {
        return kind == Kind.CHASSIS ? MechVariant.fromId(definitionId) : null;
    }

    public MechWeaponComponent component() {
        return kind == Kind.WEAPON ? MechWeaponComponent.findById(definitionId) : null;
    }

    public MechInternal internal() {
        return kind == Kind.INTERNAL ? MechInternal.findById(definitionId) : null;
    }

    public VehicleType vehicleType() {
        return kind == Kind.VEHICLE ? safeVehicle(definitionId) : null;
    }

    public VehicleKit kit() {
        return kind == Kind.KIT ? VehicleKit.findById(definitionId) : null;
    }

    public EquipmentAccessTier accessTier() {
        return switch (kind) {
            case CHASSIS -> CHASSIS_TIERS.get(variant());
            case WEAPON -> WEAPON_TIERS.get(component());
            case INTERNAL -> internal().tier;
            case VEHICLE -> VEHICLE_TIERS.get(vehicleType());
            case KIT -> KIT_TIERS.get(kit());
        };
    }

    /** Chassis or vehicle weight class; null for a weapon or internal pattern. */
    public WeightClass weightClass() {
        if (kind == Kind.VEHICLE) return weightClass(vehicleType());
        if (kind != Kind.CHASSIS) return null;
        switch (variant()) {
            case HOUND: return WeightClass.LIGHT;
            case WARDEN: return WeightClass.MEDIUM;
            default: return WeightClass.HEAVY;
        }
    }

    /**
     * A vehicle's weight class is the lift it asks for, so the card says what the drop will need; null for a hull
     * with no pattern. Rarity is the access tier's business: a scout car is hard to come by and still rides a Light
     * cradle.
     */
    public static WeightClass weightClass(VehicleType type) {
        if (type == null || !VEHICLE_TIERS.containsKey(type) || type.liftClass() == null) return null;
        return switch (type.liftClass()) {
            case LIGHT -> WeightClass.LIGHT;
            case MEDIUM -> WeightClass.MEDIUM;
            case HEAVY -> WeightClass.HEAVY;
        };
    }

    /** False only for a vehicle variant whose hull or turret art is still pending. */
    public boolean presentationDeclared() {
        if (kind != Kind.VEHICLE) return true;
        VehicleType type = vehicleType();
        return type != null && type.presentationDeclared();
    }

    public boolean starter() {
        return STARTER.contains(this);
    }

    public String displayName() {
        return switch (kind) {
            case CHASSIS -> variant().displayName();
            case WEAPON -> component().displayName();
            case INTERNAL -> internal().displayName();
            case VEHICLE -> CampaignVehicle.typeName(vehicleType());
            case KIT -> kit().displayName();
        };
    }

    /** Null for anything that is not a registered pattern id. */
    public static MechPattern parse(String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        String rest = id.substring(PREFIX.length());
        if (rest.startsWith("chassis:")) {
            MechVariant variant = safeVariant(rest.substring("chassis:".length()));
            return variant != null && CHASSIS_TIERS.containsKey(variant) ? of(variant) : null;
        }
        if (rest.startsWith("weapon:")) {
            MechWeaponComponent component = MechWeaponComponent.findById(rest.substring("weapon:".length()));
            return component != null && WEAPON_TIERS.containsKey(component) ? of(component) : null;
        }
        if (rest.startsWith("vehicle:")) {
            VehicleType type = safeVehicle(rest.substring("vehicle:".length()));
            return type != null && VEHICLE_TIERS.containsKey(type) ? of(type) : null;
        }
        if (rest.startsWith("kit:")) {
            VehicleKit kit = VehicleKit.findById(rest.substring("kit:".length()));
            return kit != null && KIT_TIERS.containsKey(kit) ? of(kit) : null;
        }
        if (rest.startsWith("internal:")) {
            MechInternal internal = MechInternal.findById(rest.substring("internal:".length()));
            return internal != null ? of(internal) : null;
        }
        return null;
    }

    /** Every registered pattern, starters included. */
    public static List<MechPattern> all() {
        List<MechPattern> patterns = new ArrayList<>();
        for (MechVariant variant : CHASSIS_TIERS.keySet()) patterns.add(of(variant));
        for (MechWeaponComponent component : WEAPON_TIERS.keySet()) patterns.add(of(component));
        for (MechInternal internal : MechInternal.values()) patterns.add(of(internal));
        for (VehicleType type : VEHICLE_TIERS.keySet()) patterns.add(of(type));
        for (VehicleKit kit : KIT_TIERS.keySet()) patterns.add(of(kit));
        return Collections.unmodifiableList(patterns);
    }

    /** Patterns a source may offer as a card: everything but the starter set and art still pending. */
    public static List<MechPattern> offerable() {
        List<MechPattern> patterns = new ArrayList<>();
        for (MechPattern pattern : all()) {
            if (!pattern.starter() && pattern.presentationDeclared()) patterns.add(pattern);
        }
        return Collections.unmodifiableList(patterns);
    }

    private static VehicleType safeVehicle(String id) {
        if (id == null) return null;
        try {
            return VehicleType.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static MechVariant safeVariant(String id) {
        try {
            return MechVariant.fromId(id);
        } catch (RuntimeException unknown) {
            return null;
        }
    }
}
