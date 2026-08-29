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

    /**
     * The authored armour intent, one entry per named section. Each says what
     * jobs its billets do and whose kit it draws on; what those billets actually
     * wear is resolved against available stock by
     * {@link ArmorIssueResolver}.
     *
     * <p>These replaced nineteen doctrines that each named twelve concrete
     * patterns, and seventeen of those named the <em>same</em> pattern twelve
     * times. That is why a fully equipped company fielded twelve identical
     * breachers: the only way to express "better kit" was to adopt a different
     * doctrine, and at the top of the ladder every doctrine was an assault one.
     */
    private static final List<SquadArmorPlan> ARMOR_PLANS = List.of(
            plan(FIELD_FATIGUES_ARMOR, "Frontier Patchwork Protection",
                    "Whatever the station had, organised the way a band organises itself.",
                    ArmorTradition.INDEPENDENT, SquadRoleMix.IRREGULAR),
            plan(SINDRIAN_SECURITY_ARMOR, "Sindrian Civilian Security Equipment",
                    "Security contracts, run as a line section because that is the drill they were taught.",
                    ArmorTradition.SINDRIAN_DIKTAT, SquadRoleMix.LINE_HOLD),
            plan(HEGEMONY_AUXILIARY_ARMOR, "Hegemony Auxiliary Protection",
                    "Auxiliary sections keep the Hegemony's shape on a fraction of its issue.",
                    ArmorTradition.HEGEMONY, SquadRoleMix.LINE_HOLD),
            plan(LEAGUE_MOBILE_ARMOR, "League Mobile Guard Protection",
                    "A coalition screen that expects to move and to be seen doing it.",
                    ArmorTradition.PERSEAN, SquadRoleMix.RECONNAISSANCE),
            plan(CHURCH_WARDEN_ARMOR, "Church Warden Protection",
                    "Parish wardens hold a place; they do not go out looking for one.",
                    ArmorTradition.LUDDIC_CHURCH, SquadRoleMix.LINE_HOLD),
            plan(OUTLAW_RAIDER_ARMOR, "Outlaw Raider Protection",
                    "A raiding band: everybody fights, two carry the heavy thing, nobody scouts for long.",
                    ArmorTradition.PIRATES, SquadRoleMix.IRREGULAR),
            plan(RECON_ARMOR, "Tri-Tachyon Recon Protection",
                    "Corporate operators, weighted toward the half of the section that is paid to see.",
                    ArmorTradition.TRITACHYON, SquadRoleMix.RECONNAISSANCE),
            plan(FLEET_COMBAT_ARMOR, "Hegemony Line Protection",
                    "The standard section, and what every other composition is measured against.",
                    ArmorTradition.HEGEMONY, SquadRoleMix.BALANCED),
            plan(LEAGUE_LINE_ARMOR, "League Coalition Line Protection",
                    "Interchangeable by design, so a member world can replace any billet in it.",
                    ArmorTradition.PERSEAN, SquadRoleMix.BALANCED),
            plan(CORPORATE_LINE_ARMOR, "Tri-Tachyon Response Protection",
                    "A response section: quick, well-sighted, and unwilling to stand and trade.",
                    ArmorTradition.TRITACHYON, SquadRoleMix.BALANCED),
            plan(CHURCH_LINE_ARMOR, "Church Palatine Protection",
                    "Sanctioned kit and a section that intends to still be there afterwards.",
                    ArmorTradition.LUDDIC_CHURCH, SquadRoleMix.LINE_HOLD),
            plan(SINDRIAN_LINE_ARMOR, "Sindrian State Line Protection",
                    "A conspicuous advance, which is most of the point of it.",
                    ArmorTradition.SINDRIAN_DIKTAT, SquadRoleMix.BALANCED),
            plan(OUTLAW_LINE_ARMOR, "Outlaw Veteran Protection",
                    "Veterans of a trade that rewards volume and speed over anything else.",
                    ArmorTradition.PIRATES, SquadRoleMix.BALANCED),
            plan(HEGEMONY_SHOCK_ARMOR, "Hegemony XIV Shock Protection",
                    "A deliberate breach: two teams through the door and one making the hole bigger.",
                    ArmorTradition.HEGEMONY, SquadRoleMix.BREACH),
            plan(CORPORATE_HEAVY_ARMOR, "Tri-Tachyon Specter Protection",
                    "The same breach, bought from a company that sells prediction rather than plate.",
                    ArmorTradition.TRITACHYON, SquadRoleMix.BREACH),
            plan(LEAGUE_HEAVY_ARMOR, "League Bulwark Protection",
                    "Coalition breach formations, built so the marine beside you need not be from your navy.",
                    ArmorTradition.PERSEAN, SquadRoleMix.BREACH),
            plan(KNIGHTS_HEAVY_ARMOR, "Knights Reliquary Protection",
                    "A Knight crosses the room in front of somebody else, and the section is arranged around that.",
                    ArmorTradition.KNIGHTS_OF_LUDD, SquadRoleMix.BREACH),
            plan(LIONS_GUARD_HEAVY_ARMOR, "Lion's Guard Mantle Protection",
                    "Prestige issue, spent as spectacle, in the composition spectacle requires.",
                    ArmorTradition.LIONS_GUARD, SquadRoleMix.BREACH),
            plan(OUTLAW_HEAVY_ARMOR, "Outlaw Foundry-Breaker Protection",
                    "Industrial rigs pointed at a door by people with no formal doctrine at all.",
                    ArmorTradition.PIRATES, SquadRoleMix.BREACH));

    private SquadEquipmentDoctrines() {}

    public static List<SquadWeaponDoctrine> weaponDoctrines() { return WEAPONS; }
    public static List<SquadArmorPlan> armorPlans() { return ARMOR_PLANS; }

    public static SquadWeaponDoctrine weaponById(String id) {
        for (SquadWeaponDoctrine doctrine : WEAPONS) if (doctrine.id().equals(id)) return doctrine;
        return null;
    }

    public static SquadArmorPlan armorPlanById(String id) {
        for (SquadArmorPlan plan : ARMOR_PLANS) if (plan.id().equals(id)) return plan;
        return null;
    }

    @SafeVarargs
    private static SquadWeaponDoctrine weaponDoctrine(
            String id, String name, String description, List<SquadWeaponIssue>... teams) {
        List<SquadWeaponIssue> issues = new ArrayList<>();
        for (List<SquadWeaponIssue> team : teams) issues.addAll(team);
        return new SquadWeaponDoctrine(id, name, description, issues);
    }

    private static SquadArmorPlan plan(String id, String name, String description,
                                       ArmorTradition tradition, SquadRoleMix mix) {
        return new SquadArmorPlan(id, name, description, tradition, mix);
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
