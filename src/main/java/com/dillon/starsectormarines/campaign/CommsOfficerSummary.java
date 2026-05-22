package com.dillon.starsectormarines.campaign;

import java.util.Random;

/**
 * Renders the officer's summary header line for the mission-select
 * surface. Two flavors:
 *
 * <ul>
 *   <li>{@link #renderOverview} — no client is selected; references total
 *       offer counts across all patrons</li>
 *   <li>{@link #renderForClient} — a client is selected; references the
 *       selected patron + their offer counts</li>
 * </ul>
 *
 * <p>Both flavors are mood-driven (pool lives on
 * {@link CommsOfficerVoice.Frame#summary}) and stable per-day: the variant
 * pick is seeded by {@code currentDay} (overview) or
 * {@code (currentDay, clientId)} (client) so the line doesn't jitter as
 * the player interacts with the screen within a single day.
 *
 * <p>Tokens (substituted from the call args, never from state):
 * <ul>
 *   <li>{@code {patron}} — selected client display name (client form only)</li>
 *   <li>{@code {offerCount}}, {@code {clientCount}}, {@code {lapsingCount}}</li>
 * </ul>
 *
 * <p>See {@code [[project_comms_officer_narrator]]} memory for the
 * composable-layering principle this is the third axis of (after
 * patron archetype and officer mood prefix/suffix).
 */
public final class CommsOfficerSummary {

    /** Mixer constants — distinct per flavor so rolls don't correlate. */
    private static final long SEED_OVERVIEW = 0xA1A1A1A1A1A1A1A1L;
    private static final long SEED_CLIENT   = 0xB2B2B2B2B2B2B2B2L;

    private CommsOfficerSummary() {}

    /**
     * Render the "no client selected" header line for the dossier stack.
     *
     * @param mood          officer mood
     * @param currentDay    sector day — seeds the variant pick so it's stable for the day
     * @param offerCount    total OFFERED contracts visible to the player
     * @param clientCount   number of distinct patrons with at least one OFFERED contract
     * @param lapsingCount  number of OFFERED contracts whose days-left is at or below the UI's "lapsing soon" threshold
     */
    public static String renderOverview(OfficerMood mood,
                                        int currentDay,
                                        int offerCount,
                                        int clientCount,
                                        int lapsingCount) {
        if (mood == null) mood = OfficerMood.STEADY;
        String[] pool = CommsOfficerVoice.forMood(mood).summary.overview;
        String template = pick(pool, currentDay, SEED_OVERVIEW);
        return template
                .replace("{offerCount}",   Integer.toString(offerCount))
                .replace("{clientCount}",  Integer.toString(clientCount))
                .replace("{lapsingCount}", Integer.toString(lapsingCount));
    }

    /**
     * Render the "client selected" header line for the dossier stack.
     *
     * @param mood          officer mood
     * @param currentDay    sector day — combined with {@code clientId} to seed the pick
     * @param clientId      selected patron house id — seeds the per-client variant pick
     * @param patron        patron display name — substituted into {@code {patron}}
     * @param offerCount    OFFERED contracts from this patron
     * @param lapsingCount  of those, how many are at/under the UI's "lapsing soon" threshold
     */
    public static String renderForClient(OfficerMood mood,
                                         int currentDay,
                                         long clientId,
                                         String patron,
                                         int offerCount,
                                         int lapsingCount) {
        if (mood == null) mood = OfficerMood.STEADY;
        String[] pool = CommsOfficerVoice.forMood(mood).summary.client;
        String template = pickForClient(pool, currentDay, clientId);
        return template
                .replace("{patron}",       patron != null ? patron : "the client")
                .replace("{offerCount}",   Integer.toString(offerCount))
                .replace("{lapsingCount}", Integer.toString(lapsingCount));
    }

    private static String pick(String[] pool, int currentDay, long mixer) {
        long seed = ((long) currentDay) * 0x9E3779B97F4A7C15L + mixer;
        int idx = Math.floorMod(new Random(seed).nextInt(), pool.length);
        return pool[idx];
    }

    private static String pickForClient(String[] pool, int currentDay, long clientId) {
        long seed = (((long) currentDay) * 0x9E3779B97F4A7C15L + clientId) ^ SEED_CLIENT;
        int idx = Math.floorMod(new Random(seed).nextInt(), pool.length);
        return pool[idx];
    }
}
