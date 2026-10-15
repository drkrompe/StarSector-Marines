package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import java.util.List;

/**
 * Small territorial lesson: advance by place and hand gains to the militia. Once a second site falls,
 * the reserve strikes the first gain and a column off the Rill road strikes the second, while the
 * company is committed further on.
 */
public final class JunctionTactics {
    private int firstSecured = -1, secondSecured = -1;
    public void observe(List<CompoundState> states) {
        if (states.size() != 4) throw new IllegalArgumentException("Junction requires four sites");
        if (firstSecured < 0) for (int i = 1; i < states.size(); i++) {
            if (states.get(i) == CompoundState.MARINE_HELD) { firstSecured = i; break; }
        }
        if (firstSecured >= 0 && secondSecured < 0) for (int i = 1; i < states.size(); i++) {
            if (i != firstSecured && states.get(i) == CompoundState.MARINE_HELD) { secondSecured = i; break; }
        }
    }
    public int firstSecured() { return firstSecured; }
    /** The second site the company secured, or -1; the road column's target. */
    public int secondSecured() { return secondSecured; }
    public boolean roadColumnCommitted() { return secondSecured >= 0; }
    public int assaultTarget(List<CompoundState> states) {
        for (int i = 0; i < states.size(); i++) if (states.get(i) != CompoundState.MARINE_HELD) return i;
        return 3;
    }
    public int militiaTarget() { return Math.max(0, firstSecured); }
    /**
     * Both counterattacks wait for a second gain: by then the company has moved past the first, Talon Two
     * has followed it, and the militia hold that ground alone.
     */
    public boolean reserveCommitted() { return secondSecured >= 0; }
}
