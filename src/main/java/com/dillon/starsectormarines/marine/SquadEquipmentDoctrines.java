package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in squad equipment definitions in stable presentation order. Names and
 * descriptions are catalog keys resolved when shown; billet roles are English role
 * literals that {@link BilletRoles} translates, because they are copied into saves.
 */
public final class SquadEquipmentDoctrines {

    public static final String FIELD_SECURITY_WEAPONS = "weapons:field-security";
    public static final String LUDDIC_PATH_ASSAULT_WEAPONS = "weapons:luddic-path-assault";
    public static final String LINE_INFANTRY_WEAPONS = "weapons:line-infantry";
    public static final String ASSAULT_WEAPONS = "weapons:assault";
    public static final String FIRE_SUPPORT_WEAPONS = "weapons:fire-support";
    public static final String TRENCH_CLEARING_WEAPONS = "weapons:trench-clearing";
    // Arc issue cards: each stocked by the arc beat that teaches it (ArcIssueListings).
    public static final String CHECKPOINT_WEAPONS = "weapons:checkpoint";
    public static final String CONVOY_ESCORT_WEAPONS = "weapons:convoy-escort";
    public static final String COUNTER_BATTERY_WEAPONS = "weapons:counter-battery";
    public static final String BUILDERS_GUARD_WEAPONS = "weapons:builders-guard";

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
    public static final String FREEPORT_ASSAULT_ARMOR = "armor:freeport-assault";
    public static final String SALVAGE_ASSISTED_ARMOR = "armor:salvage-assisted";
    public static final String CORPORATE_SPECTER_SECTION_ARMOR = "armor:corporate-specter-section";
    public static final String HEGEMONY_ARBALEST_SECTION_ARMOR = "armor:hegemony-arbalest-section";
    /**
     * Two pirate Boarding Jacks among ten raiders. Story-only (its suit's template names
     * {@code a-small-war} as owner) and not awardable until the suit's presentation ships.
     */
    public static final String BOARDING_JACK_SECTION_ARMOR = "armor:boarding-jack-section";
    /** Stage one of the Hegemony auxiliary warrant: the recovered section, in approved Arbalests. */
    public static final String HEGEMONY_AUXILIARY_SECTION_ARMOR = "armor:hegemony-auxiliary-section";
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
            weaponDoctrine(FIELD_SECURITY_WEAPONS, "@squadDoctrineFieldSecurityWeaponsName",
                    "@squadDoctrineFieldSecurityWeaponsDescription",
                    team(
                            starterWeapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            starterWeapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            starterWeapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID),
                            starterWeapon("Security Rifleman", WeaponRegistry.STARTER_PRIMARY_ID))),
            weaponDoctrine(LUDDIC_PATH_ASSAULT_WEAPONS, "@squadDoctrineLuddicPathAssaultWeaponsName",
                    "@squadDoctrineLuddicPathAssaultWeaponsDescription",
                    team(
                            weapon("Assault Leader", WeaponRegistry.SMG_ID),
                            weapon("Breacher", WeaponRegistry.SMG_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Grenadier", WeaponRegistry.SMG_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.SMG_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID))),
            weaponDoctrine(LINE_INFANTRY_WEAPONS, "@squadDoctrineLineInfantryWeaponsName",
                    "@squadDoctrineLineInfantryWeaponsDescription",
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.PULSE_RIFLE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.PULSE_RIFLE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Grenadier", WeaponRegistry.PULSE_RIFLE_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Grenadier", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID))),
            weaponDoctrine(ASSAULT_WEAPONS, "@squadDoctrineAssaultWeaponsName",
                    "@squadDoctrineAssaultWeaponsDescription",
                    team(
                            weapon("Assault Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Breacher", WeaponRegistry.SMG_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.PULSE_RIFLE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.PULSE_RIFLE_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Grenadier", WeaponRegistry.SMG_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.PULSE_RIFLE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.PULSE_RIFLE_ID)),
                    team(
                            weapon("Assault Leader", WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Breacher", WeaponRegistry.SMG_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.PULSE_RIFLE_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID))),
            weaponDoctrine(FIRE_SUPPORT_WEAPONS, "@squadDoctrineFireSupportWeaponsName",
                    "@squadDoctrineFireSupportWeaponsDescription",
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            // The pod fires from the shoulder, so the automatic keeps firing under it.
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID,
                                    SpecialEquipmentRegistry.MISSILE_POD_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID),
                            weapon("Heavy Marksman", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            // The pod fires from the shoulder, so the automatic keeps firing under it.
                            weapon("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID,
                                    SpecialEquipmentRegistry.MISSILE_POD_ID),
                            weapon("Marksman", WeaponRegistry.DMR_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID))),
            // The bottom rung of the assault ladder: no new rifles, just something to break an
            // emplacement with and enough grenades to finish the trench behind it.
            weaponDoctrine(TRENCH_CLEARING_WEAPONS, "@squadDoctrineTrenchClearingWeaponsName",
                    "@squadDoctrineTrenchClearingWeaponsDescription",
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon("Grenadier", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Grenadier", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon("Grenadier", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Grenadier", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon("Grenadier", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.FRAG_GRENADE_ID))),
            // Wayline opening (Relief): holding a road, not taking one. No rockets on purpose.
            weaponDoctrine(CHECKPOINT_WEAPONS, "@squadDoctrineCheckpointWeaponsName",
                    "@squadDoctrineCheckpointWeaponsDescription",
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                            weapon("Sapper", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                            weapon("Sapper", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                            weapon("Sapper", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID))),
            // Broken Supply Route (Thunder Run).
            weaponDoctrine(CONVOY_ESCORT_WEAPONS, "@squadDoctrineConvoyEscortWeaponsName",
                    "@squadDoctrineConvoyEscortWeaponsDescription",
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon("Point Defence", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.POINT_DEFENCE_EMPLACEMENT_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Anti-Armor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID))),
            // Corvus Account (A Cold Account): Advanced kit and no Restricted kit, so no warrant.
            weaponDoctrine(COUNTER_BATTERY_WEAPONS, "@squadDoctrineCounterBatteryWeaponsName",
                    "@squadDoctrineCounterBatteryWeaponsDescription",
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Interceptor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.POINT_DEFENCE_PACK_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Heavy Marksman", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Interceptor", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.POINT_DEFENCE_PACK_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID))),
            // Shepherd recovery (Working Passage).
            weaponDoctrine(BUILDERS_GUARD_WEAPONS, "@squadDoctrineBuildersGuardWeaponsName",
                    "@squadDoctrineBuildersGuardWeaponsDescription",
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Sapper", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon("Point Defence", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.POINT_DEFENCE_EMPLACEMENT_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Sapper", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon("Point Defence", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.POINT_DEFENCE_EMPLACEMENT_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID)),
                    team(
                            weapon(BilletRoles.TEAM_LEADER, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon("Sapper", WeaponRegistry.STARTER_PRIMARY_ID,
                                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID),
                            weapon(BilletRoles.RIFLEMAN, WeaponRegistry.STARTER_PRIMARY_ID))));

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
            plan(FIELD_FATIGUES_ARMOR, "@squadDoctrineFieldFatiguesArmorName",
                    "@squadDoctrineFieldFatiguesArmorDescription",
                    ArmorTradition.INDEPENDENT, SquadRoleMix.IRREGULAR),
            plan(SINDRIAN_SECURITY_ARMOR, "@squadDoctrineSindrianSecurityArmorName",
                    "@squadDoctrineSindrianSecurityArmorDescription",
                    ArmorTradition.SINDRIAN_DIKTAT, SquadRoleMix.LINE_HOLD),
            plan(HEGEMONY_AUXILIARY_ARMOR, "@squadDoctrineHegemonyAuxiliaryArmorName",
                    "@squadDoctrineHegemonyAuxiliaryArmorDescription",
                    ArmorTradition.HEGEMONY, SquadRoleMix.LINE_HOLD),
            plan(LEAGUE_MOBILE_ARMOR, "@squadDoctrineLeagueMobileArmorName",
                    "@squadDoctrineLeagueMobileArmorDescription",
                    ArmorTradition.PERSEAN, SquadRoleMix.RECONNAISSANCE),
            plan(CHURCH_WARDEN_ARMOR, "@squadDoctrineChurchWardenArmorName",
                    "@squadDoctrineChurchWardenArmorDescription",
                    ArmorTradition.LUDDIC_CHURCH, SquadRoleMix.LINE_HOLD),
            plan(OUTLAW_RAIDER_ARMOR, "@squadDoctrineOutlawRaiderArmorName",
                    "@squadDoctrineOutlawRaiderArmorDescription",
                    ArmorTradition.PIRATES, SquadRoleMix.IRREGULAR),
            plan(RECON_ARMOR, "@squadDoctrineReconArmorName",
                    "@squadDoctrineReconArmorDescription",
                    ArmorTradition.TRITACHYON, SquadRoleMix.RECONNAISSANCE),
            plan(FLEET_COMBAT_ARMOR, "@squadDoctrineFleetCombatArmorName",
                    "@squadDoctrineFleetCombatArmorDescription",
                    ArmorTradition.HEGEMONY, SquadRoleMix.BALANCED),
            plan(LEAGUE_LINE_ARMOR, "@squadDoctrineLeagueLineArmorName",
                    "@squadDoctrineLeagueLineArmorDescription",
                    ArmorTradition.PERSEAN, SquadRoleMix.BALANCED),
            plan(CORPORATE_LINE_ARMOR, "@squadDoctrineCorporateLineArmorName",
                    "@squadDoctrineCorporateLineArmorDescription",
                    ArmorTradition.TRITACHYON, SquadRoleMix.BALANCED),
            plan(CHURCH_LINE_ARMOR, "@squadDoctrineChurchLineArmorName",
                    "@squadDoctrineChurchLineArmorDescription",
                    ArmorTradition.LUDDIC_CHURCH, SquadRoleMix.LINE_HOLD),
            plan(SINDRIAN_LINE_ARMOR, "@squadDoctrineSindrianLineArmorName",
                    "@squadDoctrineSindrianLineArmorDescription",
                    ArmorTradition.SINDRIAN_DIKTAT, SquadRoleMix.BALANCED),
            plan(OUTLAW_LINE_ARMOR, "@squadDoctrineOutlawLineArmorName",
                    "@squadDoctrineOutlawLineArmorDescription",
                    ArmorTradition.PIRATES, SquadRoleMix.BALANCED),
            plan(FREEPORT_ASSAULT_ARMOR, "@squadDoctrineFreeportAssaultArmorName",
                    "@squadDoctrineFreeportAssaultArmorDescription",
                    ArmorTradition.INDEPENDENT, SquadRoleMix.BALANCED),
            new SquadArmorPlan(SALVAGE_ASSISTED_ARMOR, "@loadoutSalvageAssistedName",
                    "@loadoutSalvageAssistedDescription",
                    ArmorTradition.INDEPENDENT, SquadRoleMix.BALANCED, ArmorClass.STANDARD,
                    List.of(ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT, ArmorClass.POWER_SUIT)),
            new SquadArmorPlan(CORPORATE_SPECTER_SECTION_ARMOR, "@loadoutCorporateSpecterSectionName",
                    "@loadoutCorporateSpecterSectionDescription",
                    ArmorTradition.TRITACHYON, SquadRoleMix.BALANCED, ArmorClass.STANDARD,
                    List.of(ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT, ArmorClass.STANDARD)),
            new SquadArmorPlan(BOARDING_JACK_SECTION_ARMOR, "@loadoutBoardingJackSectionName",
                    "@loadoutBoardingJackSectionDescription",
                    ArmorTradition.PIRATES, SquadRoleMix.BALANCED, ArmorClass.STANDARD,
                    List.of(ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT, ArmorClass.STANDARD)),
            new SquadArmorPlan(HEGEMONY_ARBALEST_SECTION_ARMOR, "@loadoutHegemonyArbalestSectionName",
                    "@loadoutHegemonyArbalestSectionDescription",
                    ArmorTradition.HEGEMONY, SquadRoleMix.BALANCED, ArmorClass.STANDARD,
                    List.of(ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT)),
            new SquadArmorPlan(HEGEMONY_AUXILIARY_SECTION_ARMOR, "@loadoutHegemonyAuxiliarySectionName",
                    "@loadoutHegemonyAuxiliarySectionDescription",
                    ArmorTradition.HEGEMONY, SquadRoleMix.BALANCED, ArmorClass.STANDARD,
                    List.of(ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD,
                            ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.STANDARD, ArmorClass.POWER_SUIT)),
            powerSuitPlan(HEGEMONY_SHOCK_ARMOR, "@squadDoctrineHegemonyShockArmorName",
                    "@squadDoctrineHegemonyShockArmorDescription",
                    ArmorTradition.HEGEMONY, SquadRoleMix.BREACH),
            powerSuitPlan(CORPORATE_HEAVY_ARMOR, "@squadDoctrineCorporateHeavyArmorName",
                    "@squadDoctrineCorporateHeavyArmorDescription",
                    ArmorTradition.TRITACHYON, SquadRoleMix.BREACH),
            powerSuitPlan(LEAGUE_HEAVY_ARMOR, "@squadDoctrineLeagueHeavyArmorName",
                    "@squadDoctrineLeagueHeavyArmorDescription",
                    ArmorTradition.PERSEAN, SquadRoleMix.BREACH),
            powerSuitPlan(KNIGHTS_HEAVY_ARMOR, "@squadDoctrineKnightsHeavyArmorName",
                    "@squadDoctrineKnightsHeavyArmorDescription",
                    ArmorTradition.KNIGHTS_OF_LUDD, SquadRoleMix.BREACH),
            powerSuitPlan(LIONS_GUARD_HEAVY_ARMOR, "@squadDoctrineLionsGuardHeavyArmorName",
                    "@squadDoctrineLionsGuardHeavyArmorDescription",
                    ArmorTradition.LIONS_GUARD, SquadRoleMix.BREACH),
            powerSuitPlan(OUTLAW_HEAVY_ARMOR, "@squadDoctrineOutlawHeavyArmorName",
                    "@squadDoctrineOutlawHeavyArmorDescription",
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

    private static SquadArmorPlan powerSuitPlan(String id, String name, String description,
            ArmorTradition tradition, SquadRoleMix mix) {
        return new SquadArmorPlan(id, name, description, tradition, mix, ArmorClass.POWER_SUIT);
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

    private static SquadWeaponIssue starterWeapon(String role, String weaponId) {
        return new SquadWeaponIssue(role, weaponId, EquipmentGrade.SURPLUS, null);
    }

    private static SquadWeaponIssue weapon(String role, String weaponId) {
        return weapon(role, weaponId, null);
    }

    private static SquadWeaponIssue weapon(
            String role, String weaponId, String specialEquipmentId) {
        return new SquadWeaponIssue(role, weaponId, EquipmentGrade.SERVICE,
                specialEquipmentId);
    }

    /** A billet with a second special slot: {@code beltEquipmentId}, a thrown item, beside its special. */
    private static SquadWeaponIssue weapon(
            String role, String weaponId, String specialEquipmentId, String beltEquipmentId) {
        return new SquadWeaponIssue(role, weaponId, EquipmentGrade.SERVICE,
                specialEquipmentId, beltEquipmentId);
    }
}
