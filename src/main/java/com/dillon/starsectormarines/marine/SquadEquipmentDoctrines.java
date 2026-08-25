package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Built-in squad equipment definitions in stable presentation order. */
public final class SquadEquipmentDoctrines {

    public static final String FIELD_SECURITY_WEAPONS = "weapons:field-security";
    public static final String LUDDIC_PATH_ASSAULT_WEAPONS = "weapons:luddic-path-assault";
    public static final String LINE_INFANTRY_WEAPONS = "weapons:line-infantry";
    public static final String FIRE_SUPPORT_WEAPONS = "weapons:fire-support";

    public static final String FIELD_FATIGUES_ARMOR = "armor:field-fatigues";
    public static final String SINDRIAN_SECURITY_ARMOR = "armor:sindrian-security";
    public static final String FLEET_COMBAT_ARMOR = "armor:fleet-combat";
    public static final String RECON_ARMOR = "armor:recon";

    private static final List<SquadWeaponDoctrine> WEAPONS = List.of(
            weaponDoctrine(FIELD_SECURITY_WEAPONS, "Frontier Security Equipment",
                    "Reliable field rifles with one trained pulse-rifle lead in each team.",
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE)),
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE)),
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Security Rifleman", MarineWeapon.FIELD_RIFLE))),
            weaponDoctrine(LUDDIC_PATH_ASSAULT_WEAPONS, "Luddic Path Assault Equipment",
                    "Close-to-medium-range assault issue with demolition and screening gear.",
                    team(
                            weapon("Assault Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Breacher", MarineWeapon.SMG, MarineSecondary.SATCHEL_CHARGE),
                            weapon("Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Rifleman", MarineWeapon.FIELD_RIFLE)),
                    team(
                            weapon("Assault Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Grenadier", MarineWeapon.SMG, MarineSecondary.SMOKE_GRENADE),
                            weapon("Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Rifleman", MarineWeapon.FIELD_RIFLE)),
                    team(
                            weapon("Assault Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Anti-Armor", MarineWeapon.SMG, MarineSecondary.ROCKET_LAUNCHER),
                            weapon("Rifleman", MarineWeapon.FIELD_RIFLE),
                            weapon("Marksman", MarineWeapon.DMR))),
            weaponDoctrine(LINE_INFANTRY_WEAPONS, "Fleet Line Equipment",
                    "Pulse-rifle line issue with organic marksmen and smoke cover.",
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Rifleman", MarineWeapon.PULSE_RIFLE),
                            weapon("Rifleman", MarineWeapon.PULSE_RIFLE),
                            weapon("Marksman", MarineWeapon.DMR)),
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Rifleman", MarineWeapon.PULSE_RIFLE),
                            weapon("Grenadier", MarineWeapon.PULSE_RIFLE, MarineSecondary.SMOKE_GRENADE),
                            weapon("Marksman", MarineWeapon.DMR)),
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Rifleman", MarineWeapon.PULSE_RIFLE),
                            weapon("Grenadier", MarineWeapon.PULSE_RIFLE, MarineSecondary.SMOKE_GRENADE),
                            weapon("Marksman", MarineWeapon.DMR))),
            weaponDoctrine(FIRE_SUPPORT_WEAPONS, "Fleet Fire Support Equipment",
                    "Marksmen and automatic weapons backed by scarce heavy issue.",
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Automatic Rifleman", MarineWeapon.SQUAD_AUTOMATIC),
                            weapon("Marksman", MarineWeapon.DMR),
                            weapon("Anti-Armor", MarineWeapon.PULSE_RIFLE, MarineSecondary.ROCKET_LAUNCHER)),
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Automatic Rifleman", MarineWeapon.SQUAD_AUTOMATIC),
                            weapon("Marksman", MarineWeapon.DMR),
                            weapon("Heavy Marksman", MarineWeapon.PULSE_RIFLE,
                                    MarineSecondary.ANTI_MATERIEL_RIFLE)),
                    team(
                            weapon("Team Leader", MarineWeapon.PULSE_RIFLE),
                            weapon("Automatic Rifleman", MarineWeapon.SQUAD_AUTOMATIC),
                            weapon("Marksman", MarineWeapon.DMR),
                            weapon("Anti-Armor", MarineWeapon.PULSE_RIFLE, MarineSecondary.ROCKET_LAUNCHER))));

    private static final List<SquadArmorDoctrine> ARMOR = List.of(
            armorDoctrine(FIELD_FATIGUES_ARMOR, "Field Fatigues",
                    "Minimal protection with maximum mobility.",
                    repeat(MarineArmorPattern.ARMORLESS, MarineSquad.CAPACITY)),
            armorDoctrine(SINDRIAN_SECURITY_ARMOR, "Sindrian Civilian Security Equipment",
                    "Low-to-medium tier security protection mixed by billet.",
                    concat(repeat(MarineArmorPattern.ARMORLESS, 4),
                            repeat(MarineArmorPattern.MILITIA, 4),
                            repeat(MarineArmorPattern.BLUE_SCOUT, 4))),
            armorDoctrine(FLEET_COMBAT_ARMOR, "Fleet Combat Protection",
                    "Combat armor concentrated on the lead billets of each team.",
                    concat(repeat(MarineArmorPattern.CHARCOAL, 6),
                            repeat(MarineArmorPattern.ARMY_GREEN, 4),
                            repeat(MarineArmorPattern.ARMORLESS, 2))),
            armorDoctrine(RECON_ARMOR, "Recon Protection",
                    "Mobile scout armor with lighter militia backing.",
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

    private static SquadWeaponIssue weapon(String role, MarineWeapon weapon) {
        return weapon(role, weapon, null);
    }

    private static SquadWeaponIssue weapon(
            String role, MarineWeapon weapon, MarineSecondary special) {
        return new SquadWeaponIssue(role, weapon, EquipmentGrade.SERVICE, special);
    }
}
