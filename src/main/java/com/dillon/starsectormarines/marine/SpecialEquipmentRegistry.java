package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;

import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in stable special-equipment identities consumed by campaign loadouts. */
public final class SpecialEquipmentRegistry {

    public static final String ROCKET_LAUNCHER_ID = "special.rocket-launcher";
    public static final String ANTI_MATERIEL_RIFLE_ID = "special.anti-materiel-rifle";
    public static final String SMOKE_GRENADE_ID = "special.smoke-grenade";
    public static final String SATCHEL_CHARGE_ID = "special.satchel-charge";

    private static final Map<String, SpecialEquipmentDef> BY_ID = new LinkedHashMap<>();

    static {
        register(new SpecialEquipmentDef(
                ROCKET_LAUNCHER_ID, "Annihilator Rocket Launcher",
                SpecialActivation.DIRECT_EXPLOSIVE, "weapon.annihilator-launcher", 3,
                "graphics/battle/marine-rocket.png",
                "graphics/battle/marine-modular-topdown/variants/weapons/rocket-launcher.png"));
        register(new SpecialEquipmentDef(
                ANTI_MATERIEL_RIFLE_ID, "Breachlight Anti-Materiel Rifle",
                SpecialActivation.DIRECT_PRECISION, "weapon.anti-materiel-rifle", 4,
                null, "graphics/ui/armory/special-anti-materiel-rifle.png"));
        register(new SpecialEquipmentDef(
                SMOKE_GRENADE_ID, "Wayfarer Smoke Grenades",
                SpecialActivation.UTILITY_SMOKE, null, 2,
                null, "graphics/ui/armory/special-smoke-grenades.png",
                new SmokeGrenadeSpec(8f, 0.8f, 0.65f, 1.8f,
                        2.25f, 12f)));
        register(new SpecialEquipmentDef(
                SATCHEL_CHARGE_ID, "Mag-Clamp Satchel Kit",
                SpecialActivation.UTILITY_SATCHEL, null, 0,
                null, "graphics/ui/armory/special-satchel-charge.png",
                null, new SatchelChargeSpec(1.6f, 0.9f, 2.4f, 22f,
                        1.75f, 850f, 30f)));
    }

    private SpecialEquipmentRegistry() {}

    public static SpecialEquipmentDef require(String id) {
        SpecialEquipmentDef def = BY_ID.get(id);
        if (def == null) throw new IllegalArgumentException("Unknown special-equipment id '" + id + "'");
        return def;
    }

    public static SpecialEquipmentDef get(String id) {
        return id != null ? BY_ID.get(id) : null;
    }

    public static MarineSecondary compatibilityHandle(String id) {
        if (ROCKET_LAUNCHER_ID.equals(id)) return MarineSecondary.ROCKET_LAUNCHER;
        if (ANTI_MATERIEL_RIFLE_ID.equals(id)) return MarineSecondary.ANTI_MATERIEL_RIFLE;
        if (SMOKE_GRENADE_ID.equals(id)) return MarineSecondary.SMOKE_GRENADE;
        if (SATCHEL_CHARGE_ID.equals(id)) return MarineSecondary.SATCHEL_CHARGE;
        return null;
    }

    private static void register(SpecialEquipmentDef def) {
        if (BY_ID.put(def.id(), def) != null) {
            throw new IllegalStateException("Duplicate special-equipment id '" + def.id() + "'");
        }
    }
}
