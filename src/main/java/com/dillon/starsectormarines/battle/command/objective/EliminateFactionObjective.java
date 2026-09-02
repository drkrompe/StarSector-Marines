package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.EnumSet;
import java.util.Set;

/**
 * "Kill every alive unit on the counted factions." The default objective both
 * sides carry in the current ASSAULT mission — and the only objective until
 * mission-specific ones (charge sites, extraction, raid crates) land.
 *
 * <p><b>A terminal check names the sides it counts.</b> The counted set is
 * explicit rather than "everybody who is not me", because the simulation has a
 * third side that fights: an allied militia is hostile to the defender and
 * friendly to the player, and neither side's victory is decided by it. The
 * marine side counts {@code DEFENDER}; the defender side counts {@code MARINE}.
 * So an allied wipe never ends the battle, and a marine wipe with allies still
 * standing is the player's defeat — allies do not keep the player's battle
 * alive. A singleton is the common case; the set exists so a mission that
 * genuinely wants "kill the militia too" can say so.
 *
 * <p>A counted faction remains in play through live ground units, a current
 * inbound shuttle payload, or a committed future shuttle sortie. Otherwise
 * an objective targeting marines could complete before the first landing or
 * during the empty-ground rearm interval between authored cycles.
 */
public final class EliminateFactionObjective implements Objective {

    private final Faction owner;
    private final Set<Faction> targets;
    private boolean complete = false;

    /** The common case: one counted side. */
    public EliminateFactionObjective(Faction owner, Faction target) {
        this(owner, EnumSet.of(target));
    }

    public EliminateFactionObjective(Faction owner, Set<Faction> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "an elimination objective must count at least one faction");
        }
        this.owner = owner;
        this.targets = EnumSet.copyOf(targets);
    }

    @Override
    public Faction owningFaction() { return owner; }

    /** The sides this check counts. Never empty. */
    public Set<Faction> countedFactions() { return EnumSet.copyOf(targets); }

    @Override
    public void tick(BattleView sim) {
        if (complete) return;
        if (ObjectiveFactionPresence.anyInPlay(sim, targets)) return;
        complete = true;
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public boolean isFailed() { return false; }

    @Override
    public String displayName() {
        StringBuilder name = new StringBuilder("Eliminate ");
        boolean first = true;
        for (Faction target : targets) {
            if (!first) name.append(" and ");
            name.append(target.name().toLowerCase());
            first = false;
        }
        return name.toString();
    }
}
