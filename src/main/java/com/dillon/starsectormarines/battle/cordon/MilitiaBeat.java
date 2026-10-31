package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.List;

/**
 * R4, The militia ride out ({@code cordon-nouns.md}, Optional objectives): when the last convoy is at the gate
 * the town's militia come out too, down the corridor to the gap, and hold it beside the company. They stay
 * allied, under garrison authority, never in the player's command pool (the Cordon's law: the company
 * commands none of the allies). Succeeds when one of them reaches the gap; fails when every one of them
 * dies on the way; lapses when the last convoy never reaches the town or no militiaman is left to ride.
 */
final class MilitiaBeat extends CordonBeat {

    /** Cells from the gap's crossing a militiaman has reached it, and the ground they then hold. */
    static final int AT_GAP = 10, HOLD = 6;

    private List<Integer> squads;
    private int[] gap;

    MilitiaBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: one at the gap succeeds, none left alive fails. */
    static Status rule(int alive, int atGap) {
        if (atGap > 0) return Status.SUCCEEDED;
        return alive == 0 ? Status.FAILED : Status.ACTIVE;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null) return Status.LAPSED;
        if (squads == null) {
            int last = convoys.lastConvoy();
            // The last convoy is known when it reaches the gate; none will be once every convoy is home or lost.
            if (last == 0) return convoys.out() + convoys.lost() >= convoys.convoys() ? Status.LAPSED : Status.ACTIVE;
            if (sim.getStoryBeatLog().has("cordon.convoy_lost_" + last)) return Status.LAPSED;
            if (!sim.getStoryBeatLog().has("cordon.convoy_at_town_" + last)) return Status.ACTIVE;
            squads = convoys.townMilitia();
            int standing = 0;
            for (int squad : squads) standing += sim.squadMemberCount(squad);
            // Nobody left in the compound to ride out: the beat never came.
            if (standing == 0) return Status.LAPSED;
            int[] crossing = convoys.corridor().get(convoys.gapIndex());
            gap = CordonGround.nearestWalkable(sim.getGrid(), crossing[0], crossing[1]);
            if (gap == null) return Status.LAPSED;
            for (int squad : squads) {
                sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, gap[0], gap[1], HOLD),
                        CommandAuthority.GARRISON, CordonGarrisons.ISSUER, "ride out to the gap");
            }
        }
        int alive = 0, atGap = 0;
        for (int squad : squads) {
            for (int i = 0, n = sim.squadMemberCount(squad); i < n; i++) {
                long m = sim.squadMemberAt(squad, i);
                if (m == 0L || !sim.world().isAlive(m)) continue;
                alive++;
                if (Math.abs(sim.world().cellX(m) - gap[0]) <= AT_GAP && Math.abs(sim.world().cellY(m) - gap[1]) <= AT_GAP) {
                    atGap++;
                }
            }
        }
        return rule(alive, atGap);
    }

    /** The gap the militia ride out to. */
    @Override
    int[] place(BattleSimulation sim) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null || convoys.corridor().isEmpty()) return null;
        int[] crossing = convoys.corridor().get(Math.min(convoys.gapIndex(), convoys.corridor().size() - 1));
        return new int[]{crossing[0], crossing[1]};
    }
}
