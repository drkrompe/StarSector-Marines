package com.dillon.starsectormarines.battle.warship;

/**
 * What a {@link WarshipPosture} changes about the ship, and nothing else: the
 * parameters of machinery that already exists. No field is a damage multiplier;
 * a posture changes what the ship chooses to do and when.
 *
 * @param maxAreas        most areas the director may hold at once ({@link Integer#MAX_VALUE} for the ship's own limit)
 * @param radiusScale     share of the director's area radius used
 * @param bringGunsToBear whether the director may swing the hull to bring the guns to bear (the ship's own setting is
 *                        kept only when this is true)
 * @param opportunityShare share of the ship's longest reach a directed ground target may lie within; 0 means no limit
 * @param maxBurstShare   largest burst, as a share of flux capacity, a mount may start at ground (the secondary
 *                        battery); 0 means any mount. Lances count as the heaviest burst
 * @param softLimit       share of flux capacity at which the ship stops starting new fire
 * @param resumeLevel     share of flux capacity it must drain to before it starts again
 * @param weighted        whether the director weighs objective ground and friendlies (support and intercept)
 * @param denyLandings    whether inbound hostile drops are fired on at their landing zones, timed to touchdown
 * @param standoff        whether the station is kept clear of declared capital hazards
 */
public record PostureProfile(int maxAreas, float radiusScale, boolean bringGunsToBear, float opportunityShare,
                             float maxBurstShare,
                             float softLimit, float resumeLevel, boolean weighted, boolean denyLandings,
                             boolean standoff) {

    /** Today's director, untouched. */
    public static final PostureProfile BOMBARD = new PostureProfile(Integer.MAX_VALUE, 1f, true, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, false, false, false);

    /** The shipped director with objective bias and the friendly-fire rule. */
    public static final PostureProfile SUPPORT = new PostureProfile(Integer.MAX_VALUE, 1f, true, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, true, false, false);

    /** As support, biased to the defended ground, plus timed fire on inbound landing zones. */
    public static final PostureProfile INTERCEPT = new PostureProfile(Integer.MAX_VALUE, 1f, true, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, true, true, false);

    /**
     * Flux kept mostly empty for the brawl: the soft pair drops from 85/55 to 50/30. The director holds one
     * small area, never swings the heading for ground, and fires only inside 40% of the longest reach and only
     * from mounts whose burst costs under 8% of the pool: the secondary battery, not the main guns.
     */
    public static final PostureProfile AIR_SUPERIORITY = new PostureProfile(1, 0.5f, false, 0.4f, 0.08f,
            0.5f, 0.3f, false, false, true);

    /** Fires nothing at ground; the ship is leaving. */
    public static final PostureProfile WITHDRAW = new PostureProfile(0, 1f, false, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, false, false, false);

    /** The profile a posture carries. */
    public static PostureProfile of(WarshipPosture posture) {
        return switch (posture) {
            case BOMBARD -> BOMBARD;
            case SUPPORT -> SUPPORT;
            case INTERCEPT -> INTERCEPT;
            case AIR_SUPERIORITY -> AIR_SUPERIORITY;
            case WITHDRAW -> WITHDRAW;
        };
    }
}
