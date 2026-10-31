package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * One rolled Cordon beat while it runs ({@code cordon-nouns.md}, Optional objectives): an
 * {@link ObjectiveRuntime} over the battle and the Cordon's own pieces, which may ask the company for
 * squads at a place while it is active. Its rule is a pure function of what it read this tick, so it is
 * tested at its unit; the reading is the battle's.
 */
abstract class CordonBeat implements ObjectiveRuntime {

    protected final CordonBeats beats;
    private Status status = Status.ACTIVE;

    CordonBeat(CordonBeats beats) {
        this.beats = beats;
    }

    @Override
    public final Status advance(ObjectiveContext ctx, int tick) {
        if (status.isFinal()) return status;
        Status next = step(ctx.sim(), tick);
        status = next == null ? Status.ACTIVE : next;
        return status;
    }

    @Override
    public final Status end(ObjectiveContext ctx) {
        if (status.isFinal()) return status;
        Status last = ended(ctx.sim());
        status = last == null || !last.isFinal() ? Status.LAPSED : last;
        return status;
    }

    /** One tick of the beat. */
    protected abstract Status step(BattleSimulation sim, int tick);

    /** The battle ended with this still running; lapsed unless the beat says otherwise. */
    protected Status ended(BattleSimulation sim) { return Status.LAPSED; }

    /**
     * Every tick, whatever its status: what the beat keeps doing to the battle after it has gone final (guns
     * left rationed, a road opened late). Before {@link #step}.
     */
    void watch(BattleSimulation sim, int tick) { }

    /** What the beat is doing, in a line for evidence; empty when it has nothing to add to its status. */
    String describe(BattleSimulation sim) { return ""; }

    /**
     * A hull of the beat's own on the road that wants an allied escort beside it (R1's dozer), or {@code 0}. Asked only
     * while active. The surplus armour takes it ({@code cordon-allied-command.md}, rule 3); the beat's {@link #call}
     * asks the company only when no hull was free.
     */
    long escortHull(BattleSimulation sim) { return 0L; }

    /** Where the beat wants the company now, {x, y, squads}, or {@code null}. Asked only while active. */
    int[] call(BattleSimulation sim) { return null; }

    /**
     * Where the beat is on the map for the player's picture, {x, y}, or {@code null} when it has no place of its own
     * yet ({@link CordonBeats#readings} then falls back to its set piece). Where it calls the company, by default.
     */
    int[] place(BattleSimulation sim) {
        int[] call = call(sim);
        return call == null ? null : new int[]{call[0], call[1]};
    }

    final boolean active() { return status == Status.ACTIVE; }

    final Status status() { return status; }
}
