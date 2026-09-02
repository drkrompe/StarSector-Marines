package com.dillon.starsectormarines.ops;

/**
 * Reads an {@link OperationTier} off a vanilla raid's ground strength.
 *
 * <p>Vanilla's raid strength is roughly "marines landed" — {@code MarketCMD.getRaidStr}
 * counts the raiding fleet's marine complement — and {@link OperationTier#defenderBase}
 * is the defender count a tier is written for. So the smallest tier whose base matches
 * the landing is the tier whose map scale and force envelope fit the fight that is
 * actually arriving, rather than one read off a patron's demand or a risk label.
 *
 * <p>See `mission-tier-nouns.md`.
 */
public final class OperationTierForStrength {

    private OperationTierForStrength() {}

    /**
     * The smallest tier that can hold {@code strength} landed attackers, capped at
     * {@link OperationTier#FULL_STRENGTH}.
     *
     * <p>Anything at or below zero — including an unestimated raid, which reports 0, and
     * a non-finite reading — is {@link OperationTier#FIRST_CONTRACT}: the floor of the
     * ladder is the honest answer when there is nothing to size against.
     */
    public static OperationTier forGroundStrength(float strength) {
        if (!(strength > 0f)) return OperationTier.FIRST_CONTRACT;
        for (OperationTier tier : OperationTier.values()) {
            if (strength <= tier.defenderBase) return tier;
        }
        return OperationTier.FULL_STRENGTH;
    }
}
