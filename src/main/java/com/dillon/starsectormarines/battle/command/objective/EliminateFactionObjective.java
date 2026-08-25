package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * "Kill every alive unit on the target faction." The default objective both
 * sides carry in the current ASSAULT mission — and the only objective until
 * mission-specific ones (charge sites, extraction, raid crates) land.
 *
 * <p>The target faction remains in play through live ground units, a current
 * inbound shuttle payload, or a committed future shuttle sortie. Otherwise
 * an objective targeting marines could complete before the first landing or
 * during the empty-ground rearm interval between authored cycles.
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
    public void tick(BattleView sim) {
        if (complete) return;
        if (ObjectiveFactionPresence.anyInPlay(sim, target)) return;
        complete = true;
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public boolean isFailed() { return false; }

    @Override
    public String displayName() { return "Eliminate " + target.name().toLowerCase(); }
}
