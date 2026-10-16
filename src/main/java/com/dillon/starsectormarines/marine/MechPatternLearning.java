package com.dillon.starsectormarines.marine;

/**
 * Pure transition used by the mech pattern card to teach a {@link MechBay} one pattern.
 * Writes only to the bay; never to the Marine Armory or vanilla blueprint knowledge.
 */
public final class MechPatternLearning {

    /** Special item id registered in {@code special_items.csv}; its data is a pattern id. */
    public static final String ITEM_ID = "starsector_marines_mech_pattern_card";

    public enum Result {
        LEARNED,
        ALREADY_KNOWN,
        UNKNOWN_PATTERN,
        BAY_UNAVAILABLE
    }

    private MechPatternLearning() {}

    public static Result status(MechBay bay, String patternId) {
        if (bay == null) return Result.BAY_UNAVAILABLE;
        if (MechPattern.parse(patternId) == null) return Result.UNKNOWN_PATTERN;
        return bay.knowsPattern(patternId) ? Result.ALREADY_KNOWN : Result.LEARNED;
    }

    public static Result learn(MechBay bay, String patternId) {
        Result status = status(bay, patternId);
        if (status == Result.LEARNED) bay.learnPattern(patternId);
        return status;
    }
}
