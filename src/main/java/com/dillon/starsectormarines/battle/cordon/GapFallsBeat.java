package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * R6, The gap redoubt falls ({@code cordon-nouns.md}, Optional objectives): once one of the two redoubts
 * beside the gap has fallen, the company holds the gap's sandbag positions until the last convoy is home.
 * Succeeds when every convoy has come out or been lost with none lost since the fall; fails at the first
 * convoy lost after it, or when the gap closes. The fall is the battle's own, not scripted: a scripted fall
 * would spend the briefing's one allowed redoubt for the player. A battle in which neither gap redoubt falls
 * lapses it.
 */
final class GapFallsBeat extends CordonBeat {

    private boolean fallen;
    private int lostAtFall;

    GapFallsBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule after the fall: a loss since it or a closed gap fails, every convoy resolved succeeds. */
    static Status rule(boolean gapClosed, int lostSince, int resolved, int convoys) {
        if (gapClosed || lostSince > 0) return Status.FAILED;
        return resolved >= convoys ? Status.SUCCEEDED : Status.ACTIVE;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null || beats.sequence().gap().isEmpty()) return Status.LAPSED;
        int down = 0;
        for (int index : beats.sequence().gap()) {
            if (beats.sequence().redoubts().get(index).state() == CordonRedoubt.State.FALLEN) down++;
        }
        if (!fallen) {
            if (down == 0) return Status.ACTIVE;
            // Every convoy already home or lost: there is nothing left to hold the gap for.
            if (convoys.out() + convoys.lost() >= convoys.convoys()) return Status.LAPSED;
            fallen = true;
            lostAtFall = convoys.lost();
        }
        return rule(down >= beats.sequence().gap().size(), convoys.lost() - lostAtFall,
                convoys.out() + convoys.lost(), convoys.convoys());
    }

    /**
     * The battle ended with convoys still out: held when the Road was won with none lost since the fall, failed when
     * it was not; lapsed when no gap redoubt fell.
     */
    @Override
    protected Status ended(BattleSimulation sim) {
        if (!fallen) return Status.LAPSED;
        CordonConvoys convoys = beats.sequence().convoys();
        boolean held = beats.sequence().outcome() == CordonSequence.Outcome.HELD;
        return held && convoys != null && convoys.lost() == lostAtFall ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (!fallen) return null;
        int[] at = beats.centre(CordonSetPieces.GAP_POSITIONS);
        return at == null ? null : new int[]{at[0], at[1], 3};
    }

    /** The gap positions, whether or not a gap redoubt has fallen yet. */
    @Override
    int[] place(BattleSimulation sim) {
        return beats.centre(CordonSetPieces.GAP_POSITIONS);
    }
}
