package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

/**
 * Whether an ordinary ground click is really an <b>occupy</b> order: the click landed on a
 * {@link FightingPositionIndex fighting-position} cell, so the squad is asked to man that
 * run rather than to walk to it. The one place the question is answered; the order system
 * and the cursor preview both ask it, so the verb the cursor promises is the one the click
 * gives.
 *
 * <p>Pure: no world, no state. Callers decide which squads the question applies to (infantry
 * only) and pass whether fighting positions are in effect.
 */
public final class OccupyPositionDecision {

    /** The answer when the click is not on prepared ground. */
    public static final int NONE = -1;

    private OccupyPositionDecision() { }

    /**
     * The run a click at ({@code cellX}, {@code cellY}) occupies, or {@link #NONE}.
     *
     * @param index   the battle's current positions; may be {@code null}
     * @param enabled whether fighting positions are in effect ({@code DigInSystem.isEnabled()})
     */
    public static int runUnderClick(FightingPositionIndex index, boolean enabled,
                                    int cellX, int cellY) {
        if (!enabled || index == null || index.runCount() == 0) return NONE;
        return index.runIdAt(cellX, cellY);
    }
}
