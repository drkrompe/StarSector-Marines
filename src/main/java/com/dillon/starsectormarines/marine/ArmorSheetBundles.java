package com.dillon.starsectormarines.marine;

import java.util.List;

/**
 * The armor patterns each tactic sheet is built around. Learning a sheet card
 * teaches the sheet together with these patterns; owning every one of them is also
 * what teaches the sheet by any other acquisition channel.
 */
final class ArmorSheetBundles {

    /** Each row is a sheet id followed by the armor catalog ids that make it issuable. */
    static final String[][] GRANTS = {
            {SquadEquipmentDoctrines.RECON_ARMOR, "armor.scout"},
            {SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR, "armor.line"},
            {SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR, "armor.line", "armor.combat"},
            {SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR, "armor.combat"},
            {SquadEquipmentDoctrines.CORPORATE_LINE_ARMOR, "armor.aegis-composite"},
            {SquadEquipmentDoctrines.CHURCH_LINE_ARMOR, "armor.palatine"},
            {SquadEquipmentDoctrines.SINDRIAN_LINE_ARMOR, "armor.furnace-line"},
            {SquadEquipmentDoctrines.OUTLAW_LINE_ARMOR, "armor.reaver"},
            {SquadEquipmentDoctrines.OUTLAW_RAIDER_ARMOR, "armor.outlaw"},
            {SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR, "armor.vigil"},
            {SquadEquipmentDoctrines.CHURCH_WARDEN_ARMOR, "armor.orderly"},
            {SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR, "armor.outrider"},
            {SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR, "armor.heavy"},
            {SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR, "armor.specter-heavy"},
            {SquadEquipmentDoctrines.LEAGUE_HEAVY_ARMOR, "armor.bulwark-heavy"},
            {SquadEquipmentDoctrines.KNIGHTS_HEAVY_ARMOR, "armor.reliquary-heavy"},
            {SquadEquipmentDoctrines.LIONS_GUARD_HEAVY_ARMOR, "armor.lions-mantle"},
            {SquadEquipmentDoctrines.OUTLAW_HEAVY_ARMOR, "armor.foundry-breaker"}
    };

    private ArmorSheetBundles() {}

    /** Armor catalog ids bundled with a sheet; empty when the sheet has no purchasable bundle. */
    static List<String> patternsFor(String sheetId) {
        for (String[] grant : GRANTS) {
            if (grant[0].equals(sheetId)) {
                return List.of(grant).subList(1, grant.length);
            }
        }
        return List.of();
    }
}
