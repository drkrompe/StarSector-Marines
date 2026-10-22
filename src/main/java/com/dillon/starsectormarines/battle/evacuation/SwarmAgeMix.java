package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * Which growth stage a surging body is born at (`colony-breach-nouns.md`, Age
 * mix): a table keyed by the bore's tier, shifted older by every body the bore
 * has eaten in its life. An unfed fissure throws pale whelps; a bore that has
 * eaten a dozen colonists throws adults and elders, even at fissure tier.
 *
 * <p>The Matriarch's stage is not drawn here: hers follows her mother's feeding
 * ({@link SwarmStageStats#matriarchStage}).
 */
public final class SwarmAgeMix {

    /** Weight moved from whelp to adult, and from adult to elder, per body the bore has been fed. */
    static final int WHELP_TO_ADULT_PER_BODY = 4;
    static final int ADULT_TO_ELDER_PER_BODY = 2;
    /** The most weight the elder share may reach. */
    static final int ELDER_CAP = 45;

    /** Salt that keeps a body's stage draw apart from its strain draw on the same key. */
    private static final long STAGE_SALT = 0x5DEECE66DL;

    private SwarmAgeMix() {}

    /** {@code {whelp, adult, elder}} at {@code tier}, out of a hundred, before feeding. */
    static int[] base(BoreTier tier) {
        return switch (tier) {
            case FISSURE -> new int[]{70, 30, 0};
            case BORE -> new int[]{35, 55, 10};
            case MAW -> new int[]{15, 55, 30};
        };
    }

    /**
     * {@code {whelp, adult, elder}} at {@code tier} for a bore that has eaten
     * {@code bodiesFed} bodies in its life: whelps age into adults first, then
     * adults into elders, no share below zero and the elders capped. The
     * shares always sum to a hundred.
     */
    public static int[] weights(BoreTier tier, float bodiesFed) {
        int[] w = base(tier);
        float fed = Math.max(0f, bodiesFed);
        int toAdult = Math.min(w[0], Math.round(fed * WHELP_TO_ADULT_PER_BODY));
        w[0] -= toAdult;
        w[1] += toAdult;
        int toElder = Math.min(w[1], Math.max(0, Math.min(ELDER_CAP - w[2], Math.round(fed * ADULT_TO_ELDER_PER_BODY))));
        w[1] -= toElder;
        w[2] += toElder;
        return w;
    }

    /** The stage a draw of {@code roll} picks from {@code weights}: the same roll always picks the same stage. */
    public static SwarmStage pick(int[] weights, long roll) {
        int total = weights[0] + weights[1] + weights[2];
        long at = Math.floorMod(roll, (long) total);
        for (SwarmStage stage : SwarmStage.values()) {
            at -= weights[stage.ordinal()];
            if (at < 0) return stage;
        }
        throw new IllegalStateException("empty age mix");
    }

    /**
     * The stage roll for body {@code index} of pulse {@code pulse} of bore
     * {@code bore}: keyed, not streamed, as the impale draw is keyed by the
     * strike, so switching stages off changes no other roll in the battle.
     */
    public static long roll(long seed, int bore, int pulse, int index) {
        return SwarmBores.mix(STAGE_SALT ^ seed ^ (bore * 0x9E3779B97F4A7C15L)
                ^ ((long) pulse << 20) ^ ((long) index * 0xC2B2AE3D27D4EB4FL));
    }
}
