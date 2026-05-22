package com.dillon.starsectormarines.campaign;

import java.util.Random;

/**
 * Renders the briefing prose for a patron-house contract. The
 * archetype drives register (terse / formal / ideological / oblique /
 * curt / clumsy); the same patron rotates through a small bank of
 * templates per archetype so repeat work doesn't repeat verbatim.
 *
 * <p>Templates live in {@code mod/data/marines/patron_briefings.json}
 * and are loaded by {@link PatronBriefingTemplates}. Content authors
 * (and translation mods) edit the JSON, not this class.
 *
 * <p>UI surface is intentionally writing-only: archetype manifests
 * only through how the briefing reads, never via a label or chip.
 * Discoverable-narrative principle per
 * {@code [[feedback_patron_narrative_discoverable]]}.
 *
 * <h2>Substitution tokens</h2>
 * <ul>
 *   <li>{@code {patron}} — patron house display name</li>
 *   <li>{@code {target}} — target planet name (player-facing)</li>
 *   <li>{@code {payout}} — formatted payout including cash multiplier</li>
 *   <li>{@code {salvage}} — negotiated salvage % (0..baseline)</li>
 * </ul>
 */
public final class PatronBriefingFlavor {

    private PatronBriefingFlavor() {}

    /**
     * Render a briefing flavor paragraph. The variant is picked deterministically
     * from {@code (contractId, archetype)} so the same contract always reads the
     * same way across save/load and re-renders, but two contracts from the same
     * patron pick different variants.
     */
    public static String render(PatronArchetype archetype,
                                long contractId,
                                String patron, String target,
                                String payoutFormatted, int salvagePct) {
        if (archetype == null) archetype = PatronArchetype.TIME_RUSHED;
        String[] pool = PatronBriefingTemplates.forArchetype(archetype);
        int idx = variantIndex(contractId, archetype, pool.length);
        String template = pool[idx];
        return template
                .replace("{patron}",  patron  != null ? patron  : "the patron")
                .replace("{target}",  target  != null ? target  : "the target")
                .replace("{payout}",  payoutFormatted != null ? payoutFormatted : "")
                .replace("{salvage}", Integer.toString(salvagePct));
    }

    /**
     * Deterministic variant pick. A {@link Random} seeded from
     * {@code (contractId, archetype.ordinal())} round-trips through saves
     * (contractId is persistent) and isolates per archetype (changing the
     * archetype roll wouldn't shift other archetypes' variants).
     */
    private static int variantIndex(long contractId, PatronArchetype archetype, int poolSize) {
        long seed = contractId * 0x9E3779B97F4A7C15L + archetype.ordinal();
        return Math.floorMod(new Random(seed).nextInt(), poolSize);
    }
}
