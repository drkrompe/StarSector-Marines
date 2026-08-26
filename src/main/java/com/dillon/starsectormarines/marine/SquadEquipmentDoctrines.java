package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Built-in squad equipment definitions in stable presentation order. */
public final class SquadEquipmentDoctrines {

    public static final String FIELD_SECURITY_WEAPONS = "weapons:field-security";
    public static final String LUDDIC_PATH_ASSAULT_WEAPONS = "weapons:luddic-path-assault";
    public static final String LINE_INFANTRY_WEAPONS = "weapons:line-infantry";
    public static final String ASSAULT_WEAPONS = "weapons:assault";
    public static final String FIRE_SUPPORT_WEAPONS = "weapons:fire-support";

    public static final String FIELD_FATIGUES_ARMOR = "armor:field-fatigues";
    public static final String SINDRIAN_SECURITY_ARMOR = "armor:sindrian-security";
    public static final String FLEET_COMBAT_ARMOR = "armor:fleet-combat";
    public static final String RECON_ARMOR = "armor:recon";

    private static final List<SquadWeaponDoctrine> WEAPONS = List.of(
            weaponDoctrine(FIELD_SECURITY_WEAPONS, "Frontier Security Equipment",
                    "Reliable field rifles with one trained pulse-rifle lead in each team.",
                    team(
                            weapon("Team Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon("Team Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon("Team Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID))),
            weaponDoctrine(LUDDIC_PATH_ASSAULT_WEAPONS, "Luddic Path Assault Equipment",
                    "Recovered and improvised close-assault weapons built around breach tools.",
                    team(
                            weapon("Assault Leader", WeaponRegistry.SMG_ID),
                            weapon("Breacher", WeaponRegistry.SMG_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Grenadier", WeaponRegistry.SMG_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                            weapon("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.SMG_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID))),
            weaponDoctrine(LINE_INFANTRY_WEAPONS, "League Coalition Line Equipment",
                    "Mixed coalition issue with standardized leaders, support fire, and smoke cover.",
                    team(
                            weapon("Team Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID)),
                    team(
                            weapon("Team Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Grenadier", WeaponRegistry.PULSE_RIFLE_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID)),
                    team(
                            weapon("Team Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Grenadier", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID))),
            weaponDoctrine(ASSAULT_WEAPONS, "Corporate Blacksite Breach",
                    "High-tech line weapons and unmarked breaching carbines for a short, decisive entry.",
                    team(
                            weapon("Assault Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Breacher", WeaponRegistry.SMG_ID),
                            weapon("Rifleman", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Rifleman", WeaponRegistry.PULSE_RIFLE_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Grenadier", WeaponRegistry.SMG_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Rifleman", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Rifleman", WeaponRegistry.PULSE_RIFLE_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Breacher", WeaponRegistry.SMG_ID),
                            weapon("Rifleman", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID))),
            weaponDoctrine(FIRE_SUPPORT_WEAPONS, "Hegemony Auxiliary Fire Support",
                    "Rugged automatics and rail marksmen backed by scarce heavy armory issue.",
                    team(
                            weapon("Team Leader", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)),
                    team(
                            weapon("Team Leader", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID),
                            weapon("Heavy Marksman", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID)),
                    team(
                            weapon("Team Leader", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID))));

    private static final List<SquadArmorDoctrine> ARMOR = List.of(
            armorDoctrine(FIELD_FATIGUES_ARMOR, "Frontier Patchwork Protection",
                    "Scarce militia plate protects each fire-team leader; line billets keep mobile fatigues.",
                    concat(
                            List.of(MarineArmorPattern.MILITIA,
                                    MarineArmorPattern.ARMORLESS,
                                    MarineArmorPattern.ARMORLESS,
                                    MarineArmorPattern.ARMORLESS),
                            List.of(MarineArmorPattern.MILITIA,
                                    MarineArmorPattern.ARMORLESS,
                                    MarineArmorPattern.ARMORLESS,
                                    MarineArmorPattern.ARMORLESS),
                            List.of(MarineArmorPattern.MILITIA,
                                    MarineArmorPattern.ARMORLESS,
                                    MarineArmorPattern.ARMORLESS,
                                    MarineArmorPattern.ARMORLESS))),
            armorDoctrine(SINDRIAN_SECURITY_ARMOR, "Sindrian Civilian Security Equipment",
                    "Low-to-medium tier security protection mixed by billet.",
                    concat(repeat(MarineArmorPattern.ARMORLESS, 4),
                            repeat(MarineArmorPattern.MILITIA, 4),
                            repeat(MarineArmorPattern.BLUE_SCOUT, 4))),
            armorDoctrine(FLEET_COMBAT_ARMOR, "Hegemony Line Protection",
                    "Standardized Legionary and Bastion plates concentrated on contact-line billets.",
                    concat(repeat(MarineArmorPattern.CHARCOAL, 6),
                            repeat(MarineArmorPattern.ARMY_GREEN, 4),
                            repeat(MarineArmorPattern.ARMORLESS, 2))),
            armorDoctrine(RECON_ARMOR, "Tri-Tachyon Recon Protection",
                    "Janus scout suits concentrated on operators with lighter local-security backing.",
                    concat(repeat(MarineArmorPattern.BLUE_SCOUT, 8),
                            repeat(MarineArmorPattern.MILITIA, 4))));

    private SquadEquipmentDoctrines() {}

    public static List<SquadWeaponDoctrine> weaponDoctrines() { return WEAPONS; }
    public static List<SquadArmorDoctrine> armorDoctrines() { return ARMOR; }

    public static SquadWeaponDoctrine weaponById(String id) {
        for (SquadWeaponDoctrine doctrine : WEAPONS) if (doctrine.id().equals(id)) return doctrine;
        return null;
    }

    public static SquadArmorDoctrine armorById(String id) {
        for (SquadArmorDoctrine doctrine : ARMOR) if (doctrine.id().equals(id)) return doctrine;
        return null;
    }

    @SafeVarargs
    private static SquadWeaponDoctrine weaponDoctrine(
            String id, String name, String description, List<SquadWeaponIssue>... teams) {
        List<SquadWeaponIssue> issues = new ArrayList<>();
        for (List<SquadWeaponIssue> team : teams) issues.addAll(team);
        return new SquadWeaponDoctrine(id, name, description, issues);
    }

    private static SquadArmorDoctrine armorDoctrine(
            String id, String name, String description, List<MarineArmorPattern> issues) {
        return new SquadArmorDoctrine(id, name, description, issues);
    }

    @SafeVarargs
    private static <T> List<T> concat(List<T>... parts) {
        List<T> result = new ArrayList<>();
        for (List<T> part : parts) result.addAll(part);
        return Collections.unmodifiableList(result);
    }

    private static <T> List<T> repeat(T value, int count) {
        return Collections.nCopies(count, value);
    }

    private static List<SquadWeaponIssue> team(SquadWeaponIssue... issues) {
        return List.of(issues);
    }

    private static SquadWeaponIssue weapon(String role, String weaponId) {
        return weapon(role, weaponId, null);
    }

    private static SquadWeaponIssue weapon(
            String role, String weaponId, String specialEquipmentId) {
        return new SquadWeaponIssue(role, weaponId, EquipmentGrade.SERVICE,
                specialEquipmentId);
    }
}
