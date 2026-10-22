package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * The Burn's battery held (`heart-nouns.md`, Battery): lost when the swarm has
 * overrun it, every gun of it dead before the heart is down, since a battery
 * with no gun cannot fire the barrage the burn is pushed under. Complete once
 * the heart is down with a gun still standing.
 */
public final class BatteryObjective implements Objective {

    private final long[] guns;
    private boolean complete;
    private boolean failed;

    public BatteryObjective(List<Long> guns) {
        this.guns = guns.stream().mapToLong(Long::longValue).toArray();
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;
        // A battle that dug no gun in has no battery to lose.
        boolean standing = guns.length == 0;
        for (long gun : guns) if (sim.resolveUnit(gun) != 0L) { standing = true; break; }
        if (HeartObjective.allMothersCollapsed(sim)) complete = standing;
        failed = !complete && !standing;
    }

    @Override public boolean isComplete() { return complete; }

    @Override public boolean isFailed() { return failed; }

    @Override public String displayName() { return "Hold the battery"; }
}
