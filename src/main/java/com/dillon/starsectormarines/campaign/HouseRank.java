package com.dillon.starsectormarines.campaign;

/**
 * Tier 1-4 rank for sub-faction entities (per <code>roadmap/campaign/mechanics.md</code>).
 * Drives visibility (planet → system → faction → sector) and contract-type gating.
 *
 * <p>Tier-4 promotion is the T3 endgame — that's when the mod's state finally
 * crosses into vanilla faction state. Tier 1-3 stays entirely in our layer.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .houseRank[]} — never reorder.
 */
public enum HouseRank {
    TIER_1(100),
    TIER_2(300),
    TIER_3(1000),
    TIER_4(Integer.MAX_VALUE);

    /** Progress required to promote out of this rank. {@code MAX_VALUE} on TIER_4 = terminal. */
    public final int promotionThreshold;

    HouseRank(int promotionThreshold) {
        this.promotionThreshold = promotionThreshold;
    }

    private static final HouseRank[] VALUES = values();

    public static HouseRank fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }

    /** Next-rank-up, or self if already TIER_4. */
    public HouseRank next() {
        if (ordinal() + 1 >= VALUES.length) return this;
        return VALUES[ordinal() + 1];
    }

    /** Vocabulary for this (rank, flavor) pair. See themes.md rank-ladder table. */
    public String displayName(HouseFlavor flavor) {
        switch (flavor) {
            case CORPORATE:
                switch (this) { case TIER_1: return "Manager";     case TIER_2: return "Director";    case TIER_3: return "VP";       default: return "CEO"; }
            case FEUDAL:
                switch (this) { case TIER_1: return "Baron";       case TIER_2: return "Count";       case TIER_3: return "Duke";     default: return "Crown Claimant"; }
            case UNDERWORLD:
                switch (this) { case TIER_1: return "Capo";        case TIER_2: return "Boss";        case TIER_3: return "Don";      default: return "Kingpin"; }
            case SECTARIAN:
                switch (this) { case TIER_1: return "Cell Leader"; case TIER_2: return "Coordinator"; case TIER_3: return "Diocese";  default: return "Patriarch"; }
        }
        return name();
    }
}
