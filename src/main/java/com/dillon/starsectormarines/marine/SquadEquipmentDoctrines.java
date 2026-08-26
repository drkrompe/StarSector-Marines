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
    public static final String HEGEMONY_AUXILIARY_ARMOR = "armor:hegemony-auxiliary";
    public static final String LEAGUE_MOBILE_ARMOR = "armor:league-mobile";
    public static final String CHURCH_WARDEN_ARMOR = "armor:church-wardens";
    public static final String OUTLAW_RAIDER_ARMOR = "armor:outlaw-raiders";
    public static final String LEAGUE_LINE_ARMOR = "armor:league-line";
    public static final String CORPORATE_LINE_ARMOR = "armor:corporate-line";
    public static final String CHURCH_LINE_ARMOR = "armor:church-line";
    public static final String SINDRIAN_LINE_ARMOR = "armor:sindrian-line";
    public static final String OUTLAW_LINE_ARMOR = "armor:outlaw-line";
    public static final String HEGEMONY_SHOCK_ARMOR = "armor:hegemony-shock";
    public static final String CORPORATE_HEAVY_ARMOR = "armor:corporate-heavy";
    public static final String LEAGUE_HEAVY_ARMOR = "armor:league-heavy";
    public static final String KNIGHTS_HEAVY_ARMOR = "armor:knights-heavy";
    public static final String LIONS_GUARD_HEAVY_ARMOR = "armor:lions-guard-heavy";
    public static final String OUTLAW_HEAVY_ARMOR = "armor:outlaw-heavy";

    private static final String AEGIS_COMPOSITE = "armor.aegis-composite";
    private static final String CORDON_SHELL = "armor.cordon-shell";
    private static final String LASHPLATE_HARNESS = "armor.lashplate-harness";
    private static final String PALATINE = "armor.palatine";
    private static final String FURNACE_LINE = "armor.furnace-line";
    private static final String REAVER = "armor.reaver";
    private static final String SPECTER_HEAVY = "armor.specter-heavy";
    private static final String BULWARK_HEAVY = "armor.bulwark-heavy";
    private static final String RELIQUARY_HEAVY = "armor.reliquary-heavy";
    private static final String LIONS_MANTLE = "armor.lions-mantle";
    private static final String FOUNDRY_BREAKER = "armor.foundry-breaker";

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
            armorDoctrineIds(FIELD_FATIGUES_ARMOR, "Frontier Patchwork Protection",
                    "Each team stretches one Ward kit over fatigues, a surplus shell, and a breaker harness.",
                    concat(
                            List.of(MarineArmorPattern.MILITIA.id,
                                    CORDON_SHELL, LASHPLATE_HARNESS,
                                    MarineArmorPattern.ARMORLESS.id),
                            List.of(MarineArmorPattern.MILITIA.id,
                                    CORDON_SHELL, LASHPLATE_HARNESS,
                                    MarineArmorPattern.ARMORLESS.id),
                            List.of(MarineArmorPattern.MILITIA.id,
                                    CORDON_SHELL, LASHPLATE_HARNESS,
                                    MarineArmorPattern.ARMORLESS.id))),
            armorDoctrine(SINDRIAN_SECURITY_ARMOR, "Sindrian Civilian Security Equipment",
                    "Low-to-medium tier security protection mixed by billet.",
                    concat(repeat(MarineArmorPattern.ARMORLESS, 4),
                            repeat(MarineArmorPattern.MILITIA, 4),
                            repeat(MarineArmorPattern.BLUE_SCOUT, 4))),
            armorDoctrine(HEGEMONY_AUXILIARY_ARMOR, "Hegemony Auxiliary Protection",
                    "Ward kits back a limited Legionary issue for disciplined auxiliary sections.",
                    concat(repeat(MarineArmorPattern.MILITIA, 9),
                            repeat(MarineArmorPattern.ARMY_GREEN, 3))),
            armorDoctrine(LEAGUE_MOBILE_ARMOR, "League Mobile Guard Protection",
                    "Imported Janus suits and common Ward kits keep a coalition screen moving.",
                    concat(repeat(MarineArmorPattern.BLUE_SCOUT, 6),
                            repeat(MarineArmorPattern.MILITIA, 6))),
            armorDoctrine(CHURCH_WARDEN_ARMOR, "Church Warden Protection",
                    "Parish Ward kits surround a few carefully maintained Legionary shells.",
                    concat(repeat(MarineArmorPattern.MILITIA, 10),
                            repeat(MarineArmorPattern.ARMY_GREEN, 2))),
            armorDoctrine(OUTLAW_RAIDER_ARMOR, "Outlaw Raider Protection",
                    "Quick Blackforge rigs cover the assault billets; expendable hands keep fatigues.",
                    concat(repeat(MarineArmorPattern.OUTLAW, 10),
                            repeat(MarineArmorPattern.ARMORLESS, 2))),
            armorDoctrine(RECON_ARMOR, "Tri-Tachyon Recon Protection",
                    "Janus scout suits concentrated on operators with lighter local-security backing.",
                    concat(repeat(MarineArmorPattern.BLUE_SCOUT, 8),
                            repeat(MarineArmorPattern.MILITIA, 4))),
            armorDoctrine(FLEET_COMBAT_ARMOR, "Hegemony Line Protection",
                    "Standardized Legionary plate dominates a fully powered contact-line issue.",
                    concat(repeat(MarineArmorPattern.ARMY_GREEN, 8),
                            repeat(MarineArmorPattern.CHARCOAL, 4))),
            armorDoctrine(LEAGUE_LINE_ARMOR, "League Coalition Line Protection",
                    "A uniform Bastion schedule keeps member-world replacements interchangeable.",
                    repeat(MarineArmorPattern.CHARCOAL, MarineSquad.CAPACITY)),
            armorDoctrineIds(CORPORATE_LINE_ARMOR, "Tri-Tachyon Response Protection",
                    "Aegis composite suits trade plate mass for speed and hostile-fire disruption.",
                    repeat(AEGIS_COMPOSITE, MarineSquad.CAPACITY)),
            armorDoctrineIds(CHURCH_LINE_ARMOR, "Church Palatine Protection",
                    "Sanctioned legacy suits make a slow, unusually resistant defensive line.",
                    repeat(PALATINE, MarineSquad.CAPACITY)),
            armorDoctrineIds(SINDRIAN_LINE_ARMOR, "Sindrian State Line Protection",
                    "Furnace suits carry thick sacrificial laminates behind a conspicuous advance.",
                    repeat(FURNACE_LINE, MarineSquad.CAPACITY)),
            armorDoctrineIds(OUTLAW_LINE_ARMOR, "Outlaw Veteran Protection",
                    "Reaver rigs favor raw plate volume and assault speed over resistance quality.",
                    repeat(REAVER, MarineSquad.CAPACITY)),
            armorDoctrine(HEGEMONY_SHOCK_ARMOR, "Hegemony XIV Shock Protection",
                    "A full establishment of standardized XIV battlesuits for a deliberate breach.",
                    repeat(MarineArmorPattern.RED_ELITE, MarineSquad.CAPACITY)),
            armorDoctrineIds(CORPORATE_HEAVY_ARMOR, "Tri-Tachyon Specter Protection",
                    "Composite battlesuits preserve corporate mobility and target denial at heavy scale.",
                    repeat(SPECTER_HEAVY, MarineSquad.CAPACITY)),
            armorDoctrineIds(LEAGUE_HEAVY_ARMOR, "League Bulwark Protection",
                    "Modular coalition battlesuits balance plate, handling, and field replacement.",
                    repeat(BULWARK_HEAVY, MarineSquad.CAPACITY)),
            armorDoctrineIds(KNIGHTS_HEAVY_ARMOR, "Knights Reliquary Protection",
                    "Consecrated legacy shells accept immense weight to turn aside heavy fire.",
                    repeat(RELIQUARY_HEAVY, MarineSquad.CAPACITY)),
            armorDoctrineIds(LIONS_GUARD_HEAVY_ARMOR, "Lion's Guard Mantle Protection",
                    "Prestige suits carry a vast armor reserve at the cost of speed and subtlety.",
                    repeat(LIONS_MANTLE, MarineSquad.CAPACITY)),
            armorDoctrineIds(OUTLAW_HEAVY_ARMOR, "Outlaw Foundry-Breaker Protection",
                    "Industrial walking tanks survive through crude mass rather than rated protection.",
                    repeat(FOUNDRY_BREAKER, MarineSquad.CAPACITY)));

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

    private static SquadArmorDoctrine armorDoctrineIds(
            String id, String name, String description, List<String> issueIds) {
        return SquadArmorDoctrine.fromIds(id, name, description, issueIds);
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
