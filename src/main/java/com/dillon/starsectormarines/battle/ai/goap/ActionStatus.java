package com.dillon.starsectormarines.battle.ai.goap;

/**
 * Result of one tick of an {@link Action}'s {@code execute} call.
 * Drives the per-tick step machine in {@code GoapInfantryBehavior}:
 *
 * <ul>
 *   <li>{@link #RUNNING} — action is mid-execution (still moving, still
 *       firing). The plan does not advance; the same action ticks again
 *       next frame.</li>
 *   <li>{@link #SUCCESS} — action completed its effect this tick. The
 *       plan advances to the next step.</li>
 *   <li>{@link #FAILURE} — action's precondition no longer holds, or it
 *       failed to produce its effect (path blocked, target died mid-aim,
 *       etc.). The plan is invalidated and a replan is triggered.</li>
 * </ul>
 */
public enum ActionStatus {
    RUNNING,
    SUCCESS,
    FAILURE
}
