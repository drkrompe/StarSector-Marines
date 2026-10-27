package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/**
 * The armor patterns each tactic sheet is made of. A sheet card defines the whole squad:
 * {@link ArmorIssueResolver#issueSheet} issues every sheet from exactly these patterns and
 * from nothing else the company owns, so two companies holding the same card field the same
 * twelve marines. Learning the card teaches the sheet together with the patterns; owning every
 * one of them is also what teaches the sheet by any other acquisition channel.
 *
 * <p>Each row therefore has to cover every job its role mix asks for. Where it does not, the
 * resolver falls back to line kit from the same bundle, so a one-pattern row is a squad in
 * twelve identical suits. The early mixed sections hold a few recovered power suits among
 * ordinary kit on purpose: they are the transition into powered armor.
 */
final class ArmorSheetBundles {

    /** Each row is a sheet id followed by the armor catalog ids that make it issuable. */
    static final String[][] GRANTS = {
            {SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR, "armor.field-fatigues", "armor.cordon-shell"},
            {SquadEquipmentDoctrines.RECON_ARMOR, "armor.scout", "armor.cordon-shell", "armor.packframe",
                    "armor.riot-shell"},
            {SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR, "armor.militia", "armor.orderly"},
            {SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR, "armor.line", "armor.redoubt", "armor.pathfinder",
                    "armor.corpsman", "armor.riot-shell"},
            {SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR, "armor.combat", "armor.outrider", "armor.packframe",
                    "armor.riot-shell"},
            {SquadEquipmentDoctrines.CORPORATE_LINE_ARMOR, "armor.aegis-composite", "armor.scout",
                    "armor.combat", "armor.riot-shell"},
            {SquadEquipmentDoctrines.CHURCH_LINE_ARMOR, "armor.palatine", "armor.orderly", "armor.scout"},
            {SquadEquipmentDoctrines.SINDRIAN_LINE_ARMOR, "armor.furnace-line", "armor.vigil", "armor.scout",
                    "armor.packframe"},
            {SquadEquipmentDoctrines.OUTLAW_LINE_ARMOR, "armor.reaver", "armor.outlaw", "armor.packframe",
                    "armor.scout"},
            // The lighter rung below the Arbalest Section and the XIV Shock: two packframes at the
            // front of a section in militia plate.
            {SquadEquipmentDoctrines.FREEPORT_ASSAULT_ARMOR, "armor.militia", "armor.packframe"},
            {SquadEquipmentDoctrines.OUTLAW_RAIDER_ARMOR, "armor.outlaw", "armor.lashplate-harness",
                    "armor.packframe"},
            {SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR, "armor.vigil", "armor.cordon-shell",
                    "armor.orderly", "armor.scout"},
            {SquadEquipmentDoctrines.CHURCH_WARDEN_ARMOR, "armor.orderly", "armor.cordon-shell",
                    "armor.lashplate-harness"},
            {SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR, "armor.scout", "armor.orderly", "armor.packframe"},
            {SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR, "armor.heavy", "armor.arbalest"},
            {SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR, "armor.specter-heavy"},
            {SquadEquipmentDoctrines.LEAGUE_HEAVY_ARMOR, "armor.bulwark-heavy"},
            {SquadEquipmentDoctrines.KNIGHTS_HEAVY_ARMOR, "armor.reliquary-heavy"},
            {SquadEquipmentDoctrines.LIONS_GUARD_HEAVY_ARMOR, "armor.lions-mantle"},
            {SquadEquipmentDoctrines.OUTLAW_HEAVY_ARMOR, "armor.foundry-breaker"},
            // The early mixed sections: a few recovered power suits among ordinary kit.
            {SquadEquipmentDoctrines.CORPORATE_SPECTER_SECTION_ARMOR, "armor.specter-recovered",
                    "armor.scout", "armor.combat", "armor.aegis-composite"},
            // The pirate rung: two rebuilt boarding frames among a raider section in plain kit.
            {SquadEquipmentDoctrines.BOARDING_JACK_SECTION_ARMOR, "armor.boarding-jack",
                    "armor.scout", "armor.outlaw", "armor.packframe"},
            {SquadEquipmentDoctrines.HEGEMONY_ARBALEST_SECTION_ARMOR, "armor.arbalest-recovered",
                    "armor.pathfinder", "armor.line"},
            // The same section in approved suits: stage one of the Hegemony auxiliary warrant.
            {SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR, "armor.arbalest",
                    "armor.pathfinder", "armor.line"},
            {SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR, "armor.specter-recovered",
                    "armor.arbalest-recovered", "armor.scout", "armor.militia"}
    };

    private ArmorSheetBundles() {}

    /**
     * Armor catalog ids that make up a sheet, including patterns a catalog contribution adds
     * to it; empty only for an id naming no sheet.
     */
    static List<String> patternsFor(String sheetId) {
        List<String> patterns = new ArrayList<>();
        for (String[] grant : GRANTS) {
            if (grant[0].equals(sheetId)) {
                patterns.addAll(List.of(grant).subList(1, grant.length));
            }
        }
        if (patterns.isEmpty()) return List.of();
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog != null) {
            for (MarineArmorCatalogDef pattern : catalog.all()) {
                if (pattern.sheets().contains(sheetId) && !patterns.contains(pattern.id())) {
                    patterns.add(pattern.id());
                }
            }
        }
        return List.copyOf(patterns);
    }
}
