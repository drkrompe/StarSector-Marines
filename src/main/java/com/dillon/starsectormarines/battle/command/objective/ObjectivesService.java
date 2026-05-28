package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.command.CommanderService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the mission-objective list and the per-tick dispatch. The
 * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation} delegates
 * {@code addObjective}/{@code getObjectives} here, calls
 * {@link #installEliminationBackstopIfEmpty(Faction, Faction)} at the top of
 * its tick so legacy missions without registered objectives still terminate,
 * and dispatches the OBJECTIVES phase through {@link #tick(Consumer)}.
 *
 * <p>Sibling slice to {@link CommanderService}
 * — same shape: per-tick dispatch goes through a {@link Consumer} so this
 * class doesn't import {@code BattleSimulation}.
 *
 * <p>Win-check logic (does a faction have any objective, are they all
 * complete / failed, who wins) stays on {@code BattleSimulation} because it
 * also reads/writes the sim's {@code complete} + {@code winner} fields and
 * isn't an objective-list concern.
 */
public final class ObjectivesService {

    private final List<Objective> objectives = new ArrayList<>();

    public void addObjective(Objective o) {
        objectives.add(o);
    }

    public List<Objective> getObjectives() { return objectives; }

    public boolean isEmpty() { return objectives.isEmpty(); }

    /**
     * If no objectives have been registered yet, installs a symmetric pair of
     * {@link EliminateFactionObjective}s — {@code a} aims to eliminate
     * {@code b}, and {@code b} aims to eliminate {@code a}. Idempotent on a
     * non-empty list, so callers that may or may not have registered
     * mission-specific objectives can call this unconditionally.
     */
    public void installEliminationBackstopIfEmpty(Faction a, Faction b) {
        if (!objectives.isEmpty()) return;
        objectives.add(new EliminateFactionObjective(a, b));
        objectives.add(new EliminateFactionObjective(b, a));
    }

    /** OBJECTIVES tick phase — dispatches every objective through {@code tickHandler}. The sim supplies {@code o -> o.tick(sim)} as the handler. */
    public void tick(Consumer<Objective> tickHandler) {
        for (Objective o : objectives) {
            tickHandler.accept(o);
        }
    }
}
