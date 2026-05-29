package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * A mission goal that the simulation evaluates each tick. Replaces the
 * hardcoded "last faction standing" rule with a pluggable list: each side
 * carries its own objectives, and the battle ends when one side has all of
 * its objectives complete or the other has failed all of theirs.
 *
 * <p>Each objective is owned by one {@link Faction} — typically the side that
 * needs to satisfy it ("plant all charges" is a marine objective; "kill all
 * marines" is the implicit defender objective). The same battle can carry
 * multiple objectives per side; completion is conjunctive (all must be
 * complete), failure is disjunctive (any failure flips that side to lost).
 *
 * <p>Implementations are typically stateful — a charge-site objective tracks
 * per-site plant progress, an extraction objective tracks whether the VIP
 * has reached the exfil. The simulation calls {@link #tick(BattleView)}
 * once per fixed timestep so objectives can drive their own state machines
 * (timers, distance checks, contested actions) without needing hooks back
 * into the unit update loop.
 */
public interface Objective {

    /** Which side this objective belongs to — i.e., who needs to satisfy it to win. */
    Faction owningFaction();

    /** Advances any internal state for this tick. Called once per simulation tick. Read-only against the sim. */
    void tick(BattleView sim);

    /** Once true, never reverts — the side that owns this objective has fulfilled it. */
    boolean isComplete();

    /** Once true, never reverts — the owning side has failed this objective and lost the battle. */
    boolean isFailed();

    /** Short human-readable label, for the future objective HUD overlay. */
    String displayName();
}
