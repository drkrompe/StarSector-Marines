package com.dillon.starsectormarines.marine;

import java.util.List;

/**
 * The built-in loadout cards' ids, and the cards themselves in stable presentation order. The cards are data
 * ({@code data/marines/loadout-cards.json}, loaded by {@link KitLadderCatalog}): names and descriptions are string keys
 * resolved when shown; billet roles are English role literals that {@link BilletRoles} translates, because they are
 * copied into saves. The constants name the cards code refers to; a card needs no constant to exist.
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

    private SquadEquipmentDoctrines() {}

    /** The built-in weapon cards, in presentation order (the Wayline paths' free picks are {@link FirstRefit}'s). */
    public static List<SquadWeaponDoctrine> weaponDoctrines() { return KitLadderCatalog.require().builtInWeaponCards(); }

    /**
     * The tactic sheets: each the authored armour intent of one named section, saying what jobs its billets do and
     * whose kit it draws on; what those billets actually wear is resolved from the sheet's own suits by
     * {@link ArmorIssueResolver}.
     */
    public static List<SquadArmorPlan> armorPlans() { return KitLadderCatalog.require().sheets(); }

    public static SquadWeaponDoctrine weaponById(String id) {
        SquadWeaponDoctrine card = KitLadderCatalog.require().weaponCard(id);
        return card != null && !KitLadderCatalog.require().freePick(id) ? card : null;
    }

    public static SquadArmorPlan armorPlanById(String id) {
        return KitLadderCatalog.require().sheet(id);
    }
}
