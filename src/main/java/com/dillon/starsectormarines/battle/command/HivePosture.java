package com.dillon.starsectormarines.battle.command;

/**
 * The player's pre-launch answer to Colony Breach's optional hive objective
 * ({@code colony-breach-nouns.md}, Hive posture). Chosen in the briefing and
 * frozen into the rescue commander when the battle is built; the
 * {@link ReadinessGate} is what stays live.
 *
 * <p>Posture governs only what the commander does unprompted. The player can
 * always order any squad onto a bore.
 */
public enum HivePosture {
    /** Bores are obstacles: one is engaged only when it sits on the walk to the lift. */
    EVACUATE,
    /** A strike detachment goes at a bore while the readiness gate is open. */
    OPPORTUNISTIC,
    /** The mother bore is a real objective: against it the gate is relaxed. */
    PURGE;

    /** The posture a mission that never chose one plays: the rescue is the win. */
    public static final HivePosture DEFAULT = EVACUATE;

    /** {@code value}, or {@link #DEFAULT} when it is {@code null}. */
    public static HivePosture orDefault(HivePosture value) {
        return value == null ? DEFAULT : value;
    }
}
