package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The permanent right to fabricate one mech chassis, weapon component, internal or vehicle. A company that
 * knows a pattern may build as many assemblies as it can pay for; the assemblies are finite
 * and losable, the pattern is neither. The id is the cargo payload of a mech pattern card.
 */
public record MechPattern(Kind kind, String definitionId) {

    public enum Kind { CHASSIS, WEAPON, INTERNAL, VEHICLE }

    public enum WeightClass { LIGHT, MEDIUM, HEAVY }

    public static final String PREFIX = "mech-pattern:";

    /** Authored access bands. Starter patterns are listed but are never offered as cards. */
    private static final Map<MechVariant, EquipmentAccessTier> CHASSIS_TIERS = new EnumMap<>(MechVariant.class);
    private static final Map<MechWeaponComponent, EquipmentAccessTier> WEAPON_TIERS =
            new EnumMap<>(MechWeaponComponent.class);
    private static final Map<VehicleType, EquipmentAccessTier> VEHICLE_TIERS = new EnumMap<>(VehicleType.class);

    static {
        CHASSIS_TIERS.put(MechVariant.HOUND, EquipmentAccessTier.COMMON);
        CHASSIS_TIERS.put(MechVariant.SIROCCO, EquipmentAccessTier.ADVANCED);
        CHASSIS_TIERS.put(MechVariant.BULWARK, EquipmentAccessTier.PRESTIGE);

        WEAPON_TIERS.put(MechWeaponComponent.NOSE_CHAINGUN, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.SRM_5, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.NOSE_MUSTER_AUTOGUN, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.DUAL_MUSTER_AUTOGUNS, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.PIONEER_ROCKET_CRADLE, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.QUARRY_BREAKER_CANNON, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.DUAL_CHAINGUNS, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.LRM_5, EquipmentAccessTier.COMMON);
        WEAPON_TIERS.put(MechWeaponComponent.SRM_15, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.LRM_15, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.DUAL_LINEAR_CANNONS, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.SINGLE_HEAVY_CANNON, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.DEMOLITION_CANNON, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.DUAL_BASTION_AUTOCANNONS, EquipmentAccessTier.ADVANCED);
        WEAPON_TIERS.put(MechWeaponComponent.DUAL_PULSE_LASERS, EquipmentAccessTier.PRESTIGE);
        WEAPON_TIERS.put(MechWeaponComponent.SHOULDER_LASER_CANNON, EquipmentAccessTier.PRESTIGE);
        WEAPON_TIERS.put(MechWeaponComponent.THERMAL_LANCE, EquipmentAccessTier.PRESTIGE);

        // No starter vehicle: a company earns its first hull. The weight class follows the tier.
        VEHICLE_TIERS.put(VehicleType.HEAVY_APC, EquipmentAccessTier.COMMON);
        VEHICLE_TIERS.put(VehicleType.ESCORT_APC, EquipmentAccessTier.ADVANCED);
        VEHICLE_TIERS.put(VehicleType.MILITIA_TECHNICAL, EquipmentAccessTier.ADVANCED);
        VEHICLE_TIERS.put(VehicleType.BREAKTHROUGH_TANK, EquipmentAccessTier.PRESTIGE);
        // The ladder above each line's first hull: each variant is its own card.
        VEHICLE_TIERS.put(VehicleType.UP_ARMORED_APC, EquipmentAccessTier.ADVANCED);
        VEHICLE_TIERS.put(VehicleType.FIRE_SUPPORT_APC, EquipmentAccessTier.ADVANCED);
        VEHICLE_TIERS.put(VehicleType.HEAVY_GUN_TANK, EquipmentAccessTier.PRESTIGE);
        VEHICLE_TIERS.put(VehicleType.DOZER_BREACHER, EquipmentAccessTier.PRESTIGE);
    }

    /**
     * What a new company knows: the Hound, the two weapons Wayward teaches, and the stock
     * frame's internals every chassis is built with.
     */
    public static final List<MechPattern> STARTER = List.of(
            of(MechVariant.HOUND),
            of(MechWeaponComponent.NOSE_CHAINGUN),
            of(MechWeaponComponent.SRM_5),
            of(MechInternal.INDUSTRIAL_ENGINE),
            of(MechInternal.FACTORY_PLATING),
            of(MechInternal.FACTORY_HEAT_SINK));

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

    public String id() {
        return PREFIX + switch (kind) {
            case CHASSIS -> "chassis:";
            case WEAPON -> "weapon:";
            case INTERNAL -> "internal:";
            case VEHICLE -> "vehicle:";
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

    public EquipmentAccessTier accessTier() {
        return switch (kind) {
            case CHASSIS -> CHASSIS_TIERS.get(variant());
            case WEAPON -> WEAPON_TIERS.get(component());
            case INTERNAL -> internal().tier;
            case VEHICLE -> VEHICLE_TIERS.get(vehicleType());
        };
    }

    /** Chassis or vehicle weight class; null for a weapon or internal pattern. */
    public WeightClass weightClass() {
        if (kind == Kind.VEHICLE) return weightClass(vehicleType());
        if (kind != Kind.CHASSIS) return null;
        switch (variant()) {
            case HOUND: return WeightClass.LIGHT;
            case SIROCCO: return WeightClass.MEDIUM;
            default: return WeightClass.HEAVY;
        }
    }

    /** A vehicle's weight class mirrors its access band; null for a hull with no pattern. */
    public static WeightClass weightClass(VehicleType type) {
        EquipmentAccessTier tier = type != null ? VEHICLE_TIERS.get(type) : null;
        if (tier == null) return null;
        return switch (tier) {
            case COMMON -> WeightClass.LIGHT;
            case ADVANCED -> WeightClass.MEDIUM;
            default -> WeightClass.HEAVY;
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
