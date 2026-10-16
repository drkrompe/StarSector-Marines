package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The permanent right to fabricate one mech chassis or weapon component. A company that
 * knows a pattern may build as many assemblies as it can pay for; the assemblies are finite
 * and losable, the pattern is neither. The id is the cargo payload of a mech pattern card.
 */
public record MechPattern(Kind kind, String definitionId) {

    public enum Kind { CHASSIS, WEAPON }

    public static final String PREFIX = "mech-pattern:";

    /** Authored access bands. Starter patterns are listed but are never offered as cards. */
    private static final Map<MechVariant, EquipmentAccessTier> CHASSIS_TIERS = new EnumMap<>(MechVariant.class);
    private static final Map<MechWeaponComponent, EquipmentAccessTier> WEAPON_TIERS =
            new EnumMap<>(MechWeaponComponent.class);

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
    }

    /** What a new company knows: the Hound and the two weapons Wayward teaches. */
    public static final List<MechPattern> STARTER = List.of(
            of(MechVariant.HOUND),
            of(MechWeaponComponent.NOSE_CHAINGUN),
            of(MechWeaponComponent.SRM_5));

    public static MechPattern of(MechVariant variant) {
        return new MechPattern(Kind.CHASSIS, variant.id);
    }

    public static MechPattern of(MechWeaponComponent component) {
        return new MechPattern(Kind.WEAPON, component.id);
    }

    public String id() {
        return PREFIX + (kind == Kind.CHASSIS ? "chassis:" : "weapon:") + definitionId;
    }

    public MechVariant variant() {
        return kind == Kind.CHASSIS ? MechVariant.fromId(definitionId) : null;
    }

    public MechWeaponComponent component() {
        return kind == Kind.WEAPON ? MechWeaponComponent.findById(definitionId) : null;
    }

    public EquipmentAccessTier accessTier() {
        return kind == Kind.CHASSIS ? CHASSIS_TIERS.get(variant()) : WEAPON_TIERS.get(component());
    }

    public boolean starter() {
        return STARTER.contains(this);
    }

    public String displayName() {
        return kind == Kind.CHASSIS ? variant().displayName() : component().displayName();
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
        return null;
    }

    /** Every registered pattern, starters included. */
    public static List<MechPattern> all() {
        List<MechPattern> patterns = new ArrayList<>();
        for (MechVariant variant : CHASSIS_TIERS.keySet()) patterns.add(of(variant));
        for (MechWeaponComponent component : WEAPON_TIERS.keySet()) patterns.add(of(component));
        return Collections.unmodifiableList(patterns);
    }

    /** Patterns a source may offer as a card: everything but the starter set. */
    public static List<MechPattern> offerable() {
        List<MechPattern> patterns = new ArrayList<>();
        for (MechPattern pattern : all()) if (!pattern.starter()) patterns.add(pattern);
        return Collections.unmodifiableList(patterns);
    }

    private static MechVariant safeVariant(String id) {
        try {
            return MechVariant.fromId(id);
        } catch (RuntimeException unknown) {
            return null;
        }
    }
}
