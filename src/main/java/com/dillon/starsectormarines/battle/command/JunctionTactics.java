package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import java.util.List;

/**
 * Small territorial lesson: advance by place and hand gains to the militia. Once the company is fighting
 * for a second site, the reserve strikes the first gain; once it holds the second, a column off the Rill
 * road strikes that, while the company is committed further on.
 */
public final class JunctionTactics {
    private int firstSecured = -1, secondSecured = -1;
    private boolean pushedOn;
    public void observe(List<CompoundState> states) {
        if (states.size() != 4) throw new IllegalArgumentException("Junction requires four sites");
        if (firstSecured < 0) for (int i = 1; i < states.size(); i++) {
            if (states.get(i) == CompoundState.MARINE_HELD) { firstSecured = i; break; }
        }
        if (firstSecured >= 0 && secondSecured < 0) for (int i = 1; i < states.size(); i++) {
            if (i != firstSecured && states.get(i) == CompoundState.MARINE_HELD) { secondSecured = i; break; }
        }
        if (firstSecured >= 0) for (int i = 1; i < states.size(); i++)
            if (i != firstSecured && states.get(i) != CompoundState.DEFENDER_HELD) pushedOn = true;
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
     * The reserve goes in once the company is contesting a second site. Its way to the first gain runs past
     * the western landing ground: sent at the first gain, it met the company's later boatloads coming ashore
     * there and died without reaching the militia; sent at the second gain, it arrived after the company had
     * already taken Dispatch. A contested second site means the boats are in and the company has moved on.
     */
    public boolean reserveCommitted() { return pushedOn; }
}
