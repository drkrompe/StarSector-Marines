package com.dillon.starsectormarines.battle.objective;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.Unit;

/**
 * "Kill every alive unit on the target faction." The default objective both
 * sides carry in the current ASSAULT mission — and the only objective until
 * mission-specific ones (charge sites, extraction, raid crates) land.
 *
 * <p>For the marine-owned variant, inbound and deboarding shuttles count as
 * marines-in-play — otherwise the very first tick would flip this complete
 * before any marine has touched the ground. Defender shuttles aren't a thing
 * yet but the same logic would extend cleanly if reinforcement waves arrive
 * by air later.
 */
public final class EliminateFactionObjective implements Objective {

    private final Faction owner;
    private final Faction target;
    private boolean complete = false;

    public EliminateFactionObjective(Faction owner, Faction target) {
        this.owner = owner;
        this.target = target;
    }

    @Override
    public Faction owningFaction() { return owner; }

    @Override
    public void tick(BattleSimulation sim) {
        if (complete) return;
        for (Unit u : sim.getUnits()) {
            if (u.isAlive() && u.faction == target) return;
        }
        for (Shuttle s : sim.getShuttles()) {
            if (s.faction != target) continue;
            if (s.marinesRemaining > 0
                    && s.state != Shuttle.State.DEPARTING
                    && s.state != Shuttle.State.GONE) {
                return;
            }
        }
        complete = true;
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public boolean isFailed() { return false; }

    @Override
    public String displayName() { return "Eliminate " + target.name().toLowerCase(); }
}
