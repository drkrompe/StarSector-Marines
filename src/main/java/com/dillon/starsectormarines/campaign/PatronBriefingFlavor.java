package com.dillon.starsectormarines.campaign;

import java.util.Random;

/**
 * Generates the briefing prose for a patron-house contract. The
 * archetype drives register (terse / formal / ideological / oblique /
 * curt / clumsy); the same patron rotates through a small bank of
 * templates per archetype so repeat work doesn't repeat verbatim.
 *
 * <p>Per the {@code procedural fatigue} mitigations in
 * {@code roadmap/campaign/contracts.md}, V1 ships three templates per
 * archetype. Future passes add (1) more templates per cell, (2)
 * personality modifier traits stacked on the archetype, (3)
 * per-flavor renderings of each archetype (a Feudal FALLEN_NOBLE
 * reads different from an Underworld FALLEN_NOBLE).
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
        String[] pool = TEMPLATES[archetype.ordinal()];
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

    // ---------- Templates ----------
    // Per-archetype banks of 3+ variants. Index by PatronArchetype.ordinal().
    // Each template is 1–2 sentences. Voice carries the archetype: a
    // TIME_RUSHED brief OMITS context the player would expect; an
    // ESTABLISHED brief uses words a Tier-3 patron would use; a NEWCOMER
    // brief over-specifies what a veteran would leave implicit.

    private static final String[][] TEMPLATES = new String[PatronArchetype.values().length][];

    static {
        TEMPLATES[PatronArchetype.TIME_RUSHED.ordinal()] = new String[] {
                "Hit {target}. Tonight. No time to brief — full file's on the dropship terminal.",
                "{target}. Three days, then our window closes. {salvage}% salvage if you make it.",
                "Don't ask why this one's urgent. {target}. {payout} on completion. Move."
        };

        TEMPLATES[PatronArchetype.FALLEN_NOBLE.ordinal()] = new String[] {
                "What was once {patron}'s charge has slipped to other hands. {target}, brought low — and the matter settled. {payout} for your part.",
                "{patron} would once have arranged this over dinner. Now there is only the dispatch. The target is {target}. The terms are what they are.",
                "Forgive the brevity. {patron}'s circumstances have, in recent years, narrowed. {target}. {salvage}% salvage. Your discretion is, of course, assumed."
        };

        TEMPLATES[PatronArchetype.TRUE_BELIEVER.ordinal()] = new String[] {
                "The work at {target} is an affront. {patron} asks only soldiers willing to see it ended — not the ones who came for the {payout}.",
                "They call what they do at {target} commerce. {patron} calls it what it is. End it. The salvage is whatever remains.",
                "If you take {patron}'s coin, you take {patron}'s convictions. {target}. The pay is what it is. The work is what matters."
        };

        TEMPLATES[PatronArchetype.ESTABLISHED.ordinal()] = new String[] {
                "{patron} would consider it a personal favor were the situation at {target} resolved by quarter's end. {payout}; salvage per the enclosed schedule.",
                "Per the attached memorandum: {patron} seeks the cessation of operations at {target}. {salvage}% recovery cap, {payout} on confirmation. Discretion expected.",
                "{patron} prefers not to elaborate on the {target} matter beyond what the file contains. We trust that suffices. {payout}."
        };

        TEMPLATES[PatronArchetype.SUSPICIOUS.ordinal()] = new String[] {
                "{target}. No questions. {payout} cash, {salvage}% salvage on anything you find worth taking.",
                "{patron} doesn't need to know why. Neither do you. {target}. Done by month's end, or don't come back.",
                "There won't be a record of this brief. {target}. Pay's good. Don't make {patron} regret picking you."
        };

        TEMPLATES[PatronArchetype.NEWCOMER.ordinal()] = new String[] {
                "Thank you for considering this opportunity. {patron} would like to formally engage your services for operations at {target}. Per the contract draft, payment terms are {payout} with {salvage}% recovered material reserved for your fleet.",
                "{patron} represents a serious commercial proposition. Our objective at {target} aligns with established practice in the marine ops industry. Payment: {payout}. We have prepared the necessary documentation.",
                "Should you find {payout} acceptable for the conclusion of operations at {target}, kindly indicate so via the standard channels. {patron} is prepared to proceed at your earliest convenience."
        };
    }
}
