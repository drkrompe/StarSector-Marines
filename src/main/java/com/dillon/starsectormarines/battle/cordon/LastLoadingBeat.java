package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.List;

/**
 * R3, The last loading ({@code cordon-nouns.md}, Optional objectives): the town's last people are its
 * wounded and board slowly, so the last convoy waits two minutes longer at the compound's gate, its people
 * climbing aboard at a pace that fills its seats over that time ({@link CordonConvoys#boardTicks}), while the
 * company holds the streets round it. Succeeds when that convoy leaves the town loaded; fails when it is
 * lost first; lapses when it never reaches the town.
 */
final class LastLoadingBeat extends CordonBeat {

    /** The extra wait at the gate. */
    static final int EXTRA_SECONDS = 120;

    private boolean extended;

    LastLoadingBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: loaded succeeds, lost first fails. */
    static Status rule(boolean loaded, boolean lost) {
        if (loaded) return Status.SUCCEEDED;
        return lost ? Status.FAILED : Status.ACTIVE;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null || convoys.convoys() == 0) return Status.LAPSED;
        int last = convoys.convoys();
        if (!extended) {
            extended = true;
            convoys.extendLoading(last, CordonSequence.ticks(EXTRA_SECONDS));
        }
        return rule(sim.getStoryBeatLog().has("cordon.convoy_loaded_" + last),
                sim.getStoryBeatLog().has("cordon.convoy_lost_" + last));
    }

    @Override
    int[] call(BattleSimulation sim) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null || !"loading".equals(convoys.phase(convoys.convoys()))) return null;
        List<int[]> corridor = convoys.corridor();
        int[] gate = corridor.get(corridor.size() - 1);
        return new int[]{gate[0], gate[1], 3};
    }

    /** The town gate the last convoy loads at. */
    @Override
    int[] place(BattleSimulation sim) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null || convoys.corridor().isEmpty()) return null;
        int[] gate = convoys.corridor().get(convoys.corridor().size() - 1);
        return new int[]{gate[0], gate[1]};
    }
}
