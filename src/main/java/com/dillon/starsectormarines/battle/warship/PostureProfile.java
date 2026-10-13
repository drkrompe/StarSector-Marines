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
 * @param standoffScale   multiple of the standard standoff margin kept beyond a hazard (1 for a combat hull)
 */
public record PostureProfile(int maxAreas, float radiusScale, boolean bringGunsToBear, float opportunityShare,
                             float maxBurstShare,
                             float softLimit, float resumeLevel, boolean weighted, boolean denyLandings,
                             boolean standoff, float standoffScale) {

    /** Standoff margin multiple a carrier keeps beyond a hazard: 15 cells where a combat hull keeps 5. */
    public static final float CARRIER_STANDOFF_SCALE = 3f;
    /** A carrier's flux pair in the air fight, lower than the conserving 50/30: the pool is nearly empty for the brawl. */
    static final float CARRIER_AIR_SOFT = 0.40f;
    static final float CARRIER_AIR_RESUME = 0.25f;
    /** A carrier's flux pair when it fires support or intercept: between the shipped 85/55 and 50/30. */
    static final float CARRIER_GROUND_SOFT = 0.60f;
    static final float CARRIER_GROUND_RESUME = 0.40f;

    /** Today's director, untouched. */
    public static final PostureProfile BOMBARD = new PostureProfile(Integer.MAX_VALUE, 1f, true, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, false, false, false, 1f);

    /** The shipped director with objective bias and the friendly-fire rule. */
    public static final PostureProfile SUPPORT = new PostureProfile(Integer.MAX_VALUE, 1f, true, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, true, false, false, 1f);

    /** As support, biased to the defended ground, plus timed fire on inbound landing zones. */
    public static final PostureProfile INTERCEPT = new PostureProfile(Integer.MAX_VALUE, 1f, true, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, true, true, false, 1f);

    /**
     * Flux kept mostly empty for the brawl: the soft pair drops from 85/55 to 50/30. The director holds one
     * small area, never swings the heading for ground, and fires only inside 40% of the longest reach and only
     * from mounts whose burst costs under 8% of the pool: the secondary battery, not the main guns.
     */
    public static final PostureProfile AIR_SUPERIORITY = new PostureProfile(1, 0.5f, false, 0.4f, 0.08f,
            0.5f, 0.3f, false, false, true, 1f);

    /** Fires nothing at ground; the ship is leaving. */
    public static final PostureProfile WITHDRAW = new PostureProfile(0, 1f, false, 0f, 0f,
            WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL, false, false, false, 1f);

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

    /**
     * The profile a posture carries for a ship of this class. A combat or support hull gets the posture's own
     * profile. A carrier is biased, in parameters only; nothing here changes damage:
     * <ul>
     *   <li>it saves flux for its wings' fight: 40/25 in air superiority, 60/40 when firing support or
     *       intercept, the shipped 85/55 for bombard (no fight to save for) and for withdraw;</li>
     *   <li>it hangs back: standoff is on in every posture but withdraw, at {@link #CARRIER_STANDOFF_SCALE}
     *       times the margin.</li>
     * </ul>
     * Point defence is not a profile parameter and stays on.
     */
    public static PostureProfile of(WarshipPosture posture, ShipClass shipClass) {
        PostureProfile base = of(posture);
        if (shipClass.role() != ShipClass.Role.CARRIER || posture == WarshipPosture.WITHDRAW) return base;
        float soft = base.softLimit;
        float resume = base.resumeLevel;
        switch (posture) {
            case AIR_SUPERIORITY -> {
                soft = CARRIER_AIR_SOFT;
                resume = CARRIER_AIR_RESUME;
            }
            case SUPPORT, INTERCEPT -> {
                soft = CARRIER_GROUND_SOFT;
                resume = CARRIER_GROUND_RESUME;
            }
            default -> { }
        }
        return new PostureProfile(base.maxAreas, base.radiusScale, base.bringGunsToBear, base.opportunityShare,
                base.maxBurstShare, soft, resume, base.weighted, base.denyLandings, true, CARRIER_STANDOFF_SCALE);
    }
}
