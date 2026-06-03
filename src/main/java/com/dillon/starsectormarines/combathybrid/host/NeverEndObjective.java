package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * A do-nothing {@link Objective} that never completes and never fails, used to
 * keep a probe's one-unit sim from auto-terminating.
 *
 * <p>A bare {@code BattleSimulation} with no registered objectives installs the
 * default eliminate-each-other backstop on its first tick. With a single
 * {@link com.dillon.starsectormarines.battle.unit.Faction#DEFENDER} turret and no
 * opposing side, that backstop would declare the battle over on tick one — and a
 * completed sim early-returns from {@code advance()} before it can drain the death
 * mailbox, so the turret's death event would never reach our subscriber.
 *
 * <p>Registering a single objective suppresses the backstop
 * ({@code installEliminationBackstopIfEmpty} is a no-op once the list is
 * non-empty). One that is forever {@code !complete && !failed} leaves
 * {@code WinCheckSystem} permanently {@code ONGOING}, so the sim ticks (and drains
 * deaths) for as long as the combat instance lives — exactly what the bridge needs.
 * The combat side, not the sim, owns when the battle ends.
 *
 * <p>Throwaway dev scaffolding for the S3a coupling slice.
 */
@DebugOnly
final class NeverEndObjective implements Objective {

    private final Faction owner;

    NeverEndObjective(Faction owner) {
        this.owner = owner;
    }

    @Override public Faction owningFaction() { return owner; }
    @Override public void tick(BattleView sim) { /* no state to advance */ }
    @Override public boolean isComplete() { return false; }
    @Override public boolean isFailed() { return false; }
    @Override public String displayName() { return "Bridge: hold (never ends)"; }
}
