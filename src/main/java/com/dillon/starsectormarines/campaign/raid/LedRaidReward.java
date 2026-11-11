package com.dillon.starsectormarines.campaign.raid;

import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;

/**
 * What a won led raid pays ({@code raid-resolution.md}, law 9; owner: "We just need to offer more potential rewards
 * for leading on the ground to compensate"). The company needs none of the cargo marines the standard raid would, and
 * a raid it wins is committed as the market's full raid, with a premium for a harder market:
 *
 * <ul>
 *   <li>at full effectiveness, whatever share the cargo marines would have earned;</li>
 *   <li>with the raid's marine tokens filled to {@link #FULL_TOKENS} times its security's premium
 *       ({@link #premium}), spread over the objectives the player chose in proportion to their choice, so loot and
 *       disruption, which the raid command reckons per token, come at that size;</li>
 *   <li>and a disrupting raid's tokens times the share of the works it wrecked, so every hall brought down adds days
 *       down ({@code raid-mission.md}, Break).</li>
 * </ul>
 */
public final class LedRaidReward {
    private LedRaidReward() {}

    /** The raid command's most marine tokens, its raid at full effectiveness ({@code MarketCMD.MAX_MARINE_TOKENS}). */
    public static final int FULL_TOKENS = 10;

    /** The premium a harder market pays on a won led raid: open none, guarded a quarter, fortified a half. UNTUNED. */
    public static float premium(RaidSiteTemplate.Security security) {
        if (security == null) return 1f;
        return switch (security) {
            case OPEN -> 1f;
            case GUARDED -> 1.25f;
            case FORTIFIED -> 1.5f;
        };
    }

    /**
     * The marine tokens a won led raid commits: the full raid at {@code premium}, and for a disrupting raid that times
     * {@code worksShare}, the share of the works wrecked (at least one token for any share).
     */
    public static int tokens(float premium, boolean disrupting, float worksShare) {
        float full = FULL_TOKENS * Math.max(1f, premium);
        if (!disrupting) return Math.round(full);
        if (worksShare <= 0f) return 0;
        return Math.max(1, Math.round(full * Math.min(1f, worksShare)));
    }

    /**
     * Whether the fleet's marines are too few for this raid on their own: its effectiveness under the raid command's
     * threshold for the kind of raid ({@code raid-resolution.md}, law 1). Such a raid can only be led; the company's
     * battle stands in for the marines the command would have wanted.
     */
    public static boolean shortOfMarines(float raidMult, boolean disrupting) {
        return raidMult < MarketCMD.VALUABLES_THRESHOLD
                || (disrupting && raidMult < MarketCMD.DISRUPTION_THRESHOLD);
    }

    /** The tokens per objective a won led raid commits at its full size: {@link #fill} to {@link #tokens}. */
    public static int[] ledAssignment(int[] assigned, float premium, boolean disrupting) {
        return fill(assigned, tokens(premium, disrupting, 1f));
    }

    /**
     * {@code assigned} tokens per objective filled to {@code total}, in proportion to how the player spread them (the
     * largest remainders taking the odd tokens, earlier objectives first on a tie). No objective goes below what it
     * had, and one with none stays empty; a raid already at or past {@code total} is left as it is.
     */
    public static int[] fill(int[] assigned, int total) {
        int[] out = assigned.clone();
        int sum = 0;
        for (int a : assigned) sum += Math.max(0, a);
        if (sum <= 0 || sum >= total) return out;
        float[] remainder = new float[assigned.length];
        int given = 0;
        for (int i = 0; i < assigned.length; i++) {
            if (assigned[i] <= 0) continue;
            float exact = (float) assigned[i] * total / sum;
            out[i] = (int) Math.floor(exact);
            remainder[i] = exact - out[i];
            given += out[i];
        }
        while (given < total) {
            int best = -1;
            for (int i = 0; i < assigned.length; i++) {
                if (assigned[i] > 0 && (best < 0 || remainder[i] > remainder[best])) best = i;
            }
            out[best]++;
            remainder[best] = -1f;
            given++;
        }
        for (int i = 0; i < out.length; i++) out[i] = Math.max(out[i], assigned[i]);
        return out;
    }
}
