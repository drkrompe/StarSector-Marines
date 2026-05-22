package com.dillon.starsectormarines.campaign;

import java.util.Random;

/**
 * Composes the final briefing text the player reads on the mission
 * select / briefing surface. Layers three pieces:
 *
 * <ol>
 *   <li>{@code prefix} — the officer's framing line, picked from the
 *       current {@link OfficerMood}'s prefix pool</li>
 *   <li>{@code body} — the archetype-driven briefing prose from
 *       {@link PatronBriefingFlavor#render}</li>
 *   <li>{@code suffix} — optional closing aside from the mood's suffix
 *       pool; rolled in deterministically per contract</li>
 * </ol>
 *
 * <p>Composable by design: mood content is mood-pure (no archetype or
 * patron references), archetype content is archetype-pure (no mood
 * references). The cartesian product never has to be authored —
 * adding a new mood or archetype touches only one bank.
 *
 * <p>All picks are deterministic seeded by {@code contractId} so the
 * same contract always reads identically across save/load and
 * re-renders, but two contracts with the same archetype + mood land
 * different combinations of prefix/body/suffix.
 *
 * <p>See {@code [[project_comms_officer_narrator]]} for the broader
 * design — this class is the seam where the comms-officer frame
 * actually wraps the patron prose.
 */
public final class BriefingComposer {

    /** Per-contract probability of a suffix landing. 0..1. */
    static final float SUFFIX_PROBABILITY = 0.6f;

    /** Mixer constants — distinct per pick so the rolls don't correlate. */
    private static final long SEED_PREFIX  = 0xA5A5A5A5A5A5A5A5L;
    private static final long SEED_SUFFIX  = 0x5A5A5A5A5A5A5A5AL;
    private static final long SEED_INCLUDE = 0x3C3C3C3C3C3C3C3CL;

    private BriefingComposer() {}

    /**
     * Render the final briefing text for a contract. See class
     * javadoc for the layering.
     *
     * @param archetype       patron archetype (drives the body register)
     * @param mood            officer mood (drives prefix/suffix delivery)
     * @param contractId      persistent contract id — seeds determinism
     * @param patron          patron house display name
     * @param target          target planet name
     * @param payoutFormatted formatted payout including cash multiplier
     * @param salvagePct      negotiated salvage % (0..baseline)
     */
    public static String compose(PatronArchetype archetype,
                                 OfficerMood mood,
                                 long contractId,
                                 String patron,
                                 String target,
                                 String payoutFormatted,
                                 int salvagePct) {
        if (archetype == null) archetype = PatronArchetype.TIME_RUSHED;
        if (mood == null)      mood      = OfficerMood.STEADY;

        CommsOfficerVoice.Frame frame = CommsOfficerVoice.forMood(mood);
        String prefix = pick(frame.prefix, contractId, SEED_PREFIX);
        String body   = PatronBriefingFlavor.render(archetype, contractId,
                patron, target, payoutFormatted, salvagePct);

        StringBuilder out = new StringBuilder(prefix.length() + body.length() + 64);
        out.append(prefix).append(' ').append(body);
        if (shouldIncludeSuffix(contractId)) {
            String suffix = pick(frame.suffix, contractId, SEED_SUFFIX);
            out.append(' ').append(suffix);
        }
        return out.toString();
    }

    static boolean shouldIncludeSuffix(long contractId) {
        long seed = contractId * 0x9E3779B97F4A7C15L + SEED_INCLUDE;
        return new Random(seed).nextFloat() < SUFFIX_PROBABILITY;
    }

    private static String pick(String[] pool, long contractId, long mixer) {
        long seed = contractId * 0x9E3779B97F4A7C15L + mixer;
        int idx = Math.floorMod(new Random(seed).nextInt(), pool.length);
        return pool[idx];
    }
}
