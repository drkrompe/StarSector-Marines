package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import java.util.List;

/** Small territorial lesson: advance by place, hold the first gain, counterattack it once. */
public final class JunctionTactics {
    private int firstSecured = -1;
    public void observe(List<CompoundState> states) {
        if (states.size() != 4) throw new IllegalArgumentException("Junction requires four sites");
        if (firstSecured < 0) for (int i = 1; i < states.size(); i++) {
            if (states.get(i) == CompoundState.MARINE_HELD) { firstSecured = i; break; }
        }
    }
    public int firstSecured() { return firstSecured; }
    public int assaultTarget(List<CompoundState> states) {
        for (int i = 0; i < states.size(); i++) if (states.get(i) != CompoundState.MARINE_HELD) return i;
        return 3;
    }
    public int militiaTarget() { return Math.max(0, firstSecured); }
    public boolean reserveCommitted() { return firstSecured >= 0; }
}
