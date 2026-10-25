package com.dillon.starsectormarines.battle.cordon;

/**
 * The briefing's choice of how far the company goes out ({@code cordon-nouns.md}, Line posture), the Cordon's version
 * of Colony Breach's hive posture. Frozen into the company commander when the battle is built; the readiness gate
 * ({@link CordonStrikes}) is what stays live. It governs only what the commander does unprompted: the player can
 * always order any squad at a bore.
 */
public enum CordonPosture {
    /** The company only relieves and escorts. */
    HOLD,
    /** Between tides, with the readiness gate open, one squad strikes the bore pressing hardest. */
    OPPORTUNISTIC,
    /** The bores in front of the line are objectives: two squads strike, tide or not, the held window waived. */
    PUSH;

    /** The posture a battle no briefing chose plays: holding the line is the win, the bores a bonus. */
    public static final CordonPosture DEFAULT = HOLD;

    /** {@code value}, or {@link #DEFAULT} when it is {@code null}. */
    public static CordonPosture orDefault(CordonPosture value) {
        return value == null ? DEFAULT : value;
    }
}
