package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The orders a player would give, at the ticks a player would give them.
 *
 * <p>Every convenience here goes through the same order service the UI clicks
 * into, so a scene exercises acceptance, destination resolution and handback
 * rather than a shortcut past them. <b>Nothing here writes
 * {@code Squad.assignedObjective}</b> — that is the commander's field, and a
 * scene that sets it directly has tested the goal and skipped the whole player
 * path it meant to be about. Use {@code SceneBuilder}'s {@code assigned} for
 * the standing mission a scene opens with, and this for what the player does
 * while it runs.
 */
public final class ScriptedPlayer {

    private final SceneWorld world;
    private final List<Scheduled> actions = new ArrayList<>();

    private ScriptedPlayer(SceneWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public static ScriptedPlayer on(SceneWorld world) {
        return new ScriptedPlayer(world);
    }

    /** Runs {@code action} once, on {@code tick}. */
    public ScriptedPlayer at(int tick, Consumer<BattleSimulation> action) {
        actions.add(new Scheduled(tick, Objects.requireNonNull(action, "action")));
        return this;
    }

    /** The ordinary ground click: walk this squad there. */
    public ScriptedPlayer moveSquad(int tick, String squadKey, int x, int y) {
        int squadId = world.squadId(squadKey);
        return at(tick, sim -> sim.getSquadMoveOrderService().requestMove(squadId, x, y));
    }

    /** Places the persistent defence area on this squad. */
    public ScriptedPlayer defendArea(int tick, String squadKey, int x, int y) {
        int squadId = world.squadId(squadKey);
        return at(tick, sim -> sim.getSquadMoveOrderService().requestDefendArea(squadId, x, y));
    }

    /**
     * Moves one chassis of a lance. Per chassis rather than per squad because
     * that is how the mech order service is scoped: it borrows one mech's
     * locomotion and leaves the squad's plan alone.
     */
    public ScriptedPlayer moveMech(int tick, String squadKey, int memberIndex, int x, int y) {
        long memberId = world.members(squadKey)[memberIndex];
        return at(tick, sim -> sim.getMechMoveOrderService().requestMove(memberId, x, y));
    }

    /** Fires every action scheduled for {@code tick} that has not fired, in insertion order. */
    public void tick(BattleSimulation sim, int tick) {
        for (Scheduled scheduled : actions) {
            if (scheduled.fired || scheduled.tick != tick) continue;
            scheduled.fired = true;
            scheduled.action.accept(sim);
        }
    }

    /** How many scheduled actions have not fired yet. */
    public int pending() {
        int count = 0;
        for (Scheduled scheduled : actions) {
            if (!scheduled.fired) count++;
        }
        return count;
    }

    private static final class Scheduled {
        final int tick;
        final Consumer<BattleSimulation> action;
        boolean fired;

        Scheduled(int tick, Consumer<BattleSimulation> action) {
            this.tick = tick;
            this.action = action;
        }
    }
}
