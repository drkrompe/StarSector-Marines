package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.List;

/**
 * L3, Fall back to the switch line ({@code cordon-nouns.md}, Optional objectives): when the second tide
 * comes the allied command gives up the middle redoubt the switch line stands behind, and orders its
 * garrison back to the switch line. The redoubt is then given up, not fallen: it no longer calls or
 * counts against the line, so the order never costs the briefing's allowance. Succeeds when most of the
 * garrison that set out is in the switch line; fails once too few are left alive for that.
 */
final class SwitchLineBeat extends CordonBeat {

    /** Cells round the switch line's works a member counts as in it. */
    static final int IN_LINE = 3;

    private CordonRedoubt redoubt;
    private List<Integer> teams;
    private int setOut;
    private int[] line;

    SwitchLineBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: most of those who set out are in the line, or too few are left alive for that. */
    static Status rule(int setOut, int alive, int arrived) {
        if (setOut <= 0) return Status.LAPSED;
        if (arrived * 2 > setOut) return Status.SUCCEEDED;
        if (alive * 2 <= setOut) return Status.FAILED;
        return Status.ACTIVE;
    }

    /**
     * Where team {@code k} of {@code n} holds the switch line: the middle of its own stretch along the line's long side.
     * Told to hold a circle round the whole line, a garrison whose redoubt stood inside the circle never moved: on
     * urban Line seeds 1 and 11 the given-up garrison stood all battle where it was, short of the line.
     */
    static int[] stretch(int[] line, int k, int n) {
        int w = line[2] - line[0], h = line[3] - line[1];
        float along = (k + 0.5f) / n;
        return w >= h ? new int[]{line[0] + Math.round(along * w), (line[1] + line[3]) / 2}
                : new int[]{(line[0] + line[2]) / 2, line[1] + Math.round(along * h)};
    }

    /** The middle redoubt the switch line was laid behind: the one whose middle is nearest it, or {@code null}. */
    static CordonRedoubt switchRedoubt(CordonBeats beats) {
        int[] at = beats.centre(CordonPlan.SWITCH_LINE);
        if (at == null) return null;
        CordonRedoubt best = null;
        int bestD = Integer.MAX_VALUE;
        for (CordonRedoubt r : beats.sequence().redoubts()) {
            if (r.number() == 1 || r.number() == CordonPlan.REDOUBTS) continue;
            int d = Math.abs(r.centreX() - at[0]);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return best;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (redoubt == null) {
            if (beats.sequence().tides() < 2) return Status.ACTIVE;
            line = beats.bounds(CordonPlan.SWITCH_LINE);
            CordonRedoubt r = switchRedoubt(beats);
            if (line == null || r == null || !r.manned()) return Status.LAPSED;
            setOut = CordonSequence.standing(sim, r);
            if (setOut == 0 || !beats.sequence().giveUp(r)) return Status.LAPSED;
            redoubt = r;
            teams = new ArrayList<>(r.teams());
            for (int k = 0; k < teams.size(); k++) {
                int[] at = stretch(line, k, teams.size());
                sim.assignSquadCommand(ObjectiveAssignment.defendArea(teams.get(k), at[0], at[1], IN_LINE),
                        CommandAuthority.GARRISON, CordonGarrisons.ISSUER, "fall back to the switch line");
            }
        }
        int alive = 0, arrived = 0;
        for (int squad : teams) {
            for (int i = 0, n = sim.squadMemberCount(squad); i < n; i++) {
                long m = sim.squadMemberAt(squad, i);
                if (m == 0L || !sim.world().isAlive(m)) continue;
                alive++;
                int x = sim.world().cellX(m), y = sim.world().cellY(m);
                if (x >= line[0] - IN_LINE && x <= line[2] + IN_LINE && y >= line[1] - IN_LINE && y <= line[3] + IN_LINE) {
                    arrived++;
                }
            }
        }
        return rule(setOut, alive, arrived);
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (redoubt == null) return null;
        return new int[]{(redoubt.centreX() + (line[0] + line[2]) / 2) / 2, (redoubt.centreY() + (line[1] + line[3]) / 2) / 2, 2};
    }
}
