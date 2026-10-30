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
 * <p>The suits are data: each tactic sheet's {@code suits} in {@code data/marines/loadout-cards.json}
 * ({@link KitLadderCatalog}).
 *
 * <p>A seated sheet names each billet's suit, so it covers its jobs by construction. An
 * unseated sheet has to cover every job its role mix asks for. Where it does not, the
 * resolver falls back to line kit from the same bundle, so a one-suit sheet is a squad in
 * twelve identical suits. The early mixed sections hold a few recovered power suits among
 * ordinary kit on purpose: they are the transition into powered armor.
 */
final class ArmorSheetBundles {

    private ArmorSheetBundles() {}

    /**
     * Armor catalog ids that make up a sheet, including patterns a catalog contribution adds
     * to it; empty only for an id naming no sheet.
     */
    static List<String> patternsFor(String sheetId) {
        List<String> patterns = new ArrayList<>(KitLadderCatalog.require().suits(sheetId));
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
