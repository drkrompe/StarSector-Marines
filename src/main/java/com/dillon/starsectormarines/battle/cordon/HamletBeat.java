package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * L5, The hamlet that will not leave ({@code cordon-nouns.md}, Optional objectives): the far rear hamlet's
 * last group gathers at its bell and will not walk until a Marine reaches them; then they walk to the rear
 * edge below the square. Out when the last of them leaves the map; caught, and failed, the first time the
 * swarm kills one, as a refugee group is. Still waiting at the end, they were left behind: failed.
 */
final class HamletBeat extends CordonBeat {

    /** When they gather at the bell: after the timetable's refugee groups have mostly gone. */
    static final int GATHER_SECONDS = 420;
    static final int PEOPLE = 6;
    static final int REACH = 6;
    /** Rows from the rear edge that count as off the map. */
    static final int EXIT_BAND = 1;

    private CordonWalkers group;
    private boolean reached;
    private int[] exit;
    private int rearRow;

    HamletBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: caught at the first loss, out once nobody is left on the field. */
    static Status rule(int spawned, int onField, int out, int lost) {
        if (spawned == 0) return Status.LAPSED;
        if (lost > 0) return Status.FAILED;
        if (onField > 0) return Status.ACTIVE;
        return out > 0 ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (group == null) {
            if (tick < CordonSequence.ticks(GATHER_SECONDS)) return Status.ACTIVE;
            int[] bell = beats.centre(CordonSetPieces.BELL_SQUARE);
            if (bell == null) return Status.LAPSED;
            rearRow = CordonFront.rearRow(beats.plan(), sim.getGrid().getHeight());
            exit = edge(sim, bell[0], rearRow);
            if (exit == null) return Status.LAPSED;
            group = CordonWalkers.stand(sim, "Hamlet", bell[0], bell[1], PEOPLE);
        }
        group.count(sim);
        if (!reached) reached = group.reachedByMarines(sim, REACH);
        return rule(group.spawned(), group.onField(), group.out(), group.lost());
    }

    /** Once reached they walk, and keep walking after a loss has failed the beat. */
    @Override
    void watch(BattleSimulation sim, int tick) {
        if (group != null && reached) group.walk(sim, exit[0], exit[1], (x, y) -> Math.abs(y - rearRow) <= EXIT_BAND);
    }

    /** The open cell of the rear edge nearest {@code x}. */
    private static int[] edge(BattleSimulation sim, int x, int row) {
        for (int d = 0; d < sim.getGrid().getWidth(); d++) {
            for (int cx : new int[]{x - d, x + d}) {
                if (sim.getGrid().inBounds(cx, row) && sim.getGrid().isWalkable(cx, row)) return new int[]{cx, row};
            }
        }
        return null;
    }

    @Override
    protected Status ended(BattleSimulation sim) {
        return group != null && !reached && group.spawned() > 0 ? Status.FAILED : Status.LAPSED;
    }

    @Override
    String describe(BattleSimulation sim) {
        int[] place = beats.centre(CordonSetPieces.BELL_SQUARE);
        String at = place == null ? "no place" : "at " + place[0] + "," + place[1];
        if (group == null) return at;
        return at + ", " + group.onField() + " on the field, " + group.out() + " out, " + group.lost() + " lost"
                + (reached ? ", reached" : "");
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (group == null || reached) return null;
        int[] at = group.where(sim);
        return at == null ? null : new int[]{at[0], at[1], 1};
    }
}
