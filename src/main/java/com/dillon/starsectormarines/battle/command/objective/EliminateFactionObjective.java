package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.unit.Unit;

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
    public void tick(BattleView sim) {
        if (complete) return;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Unit u = sim.liveUnitAt(i);
            if (u.faction == target) return;
        }
        for (Shuttle s : sim.getShuttles()) {
            if (s.faction != target) continue;
            if (s.mission.marinesRemaining > 0
                    && s.mission.state != Shuttle.State.DEPARTING
                    && s.mission.state != Shuttle.State.GONE) {
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
