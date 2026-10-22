package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Marine objective of the heart (`heart-nouns.md`): every mother bore of the
 * battle collapsed. Satellites are never required. Complete only once there
 * has been a mother to collapse, so a battle whose bores have not opened yet
 * cannot be won by default.
 */
public final class HeartObjective implements Objective {

    private boolean complete;

    @Override public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete) return;
        complete = allMothersCollapsed(sim);
    }

    /** Whether {@code sim} has at least one mother bore and none of them lives. */
    static boolean allMothersCollapsed(BattleView sim) {
        int mothers = 0;
        for (SwarmBore bore : sim.getSwarmBores()) {
            if (!bore.mother()) continue;
            mothers++;
            if (!bore.collapsed()) return false;
        }
        return mothers > 0;
    }

    @Override public boolean isComplete() { return complete; }

    @Override public boolean isFailed() { return false; }

    @Override public String displayName() { return "Collapse the heart"; }
}
