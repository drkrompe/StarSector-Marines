package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The claim's stakes as the garrison and its allies fight them (`fallen-hull-nouns.md`, The company's levers;
 * `long-survey-arc.md`, The Line): what each standing stake is killed by, and what that kill does. A planted stake is
 * scrapped by killing its folded pylon; a raised or established stake is stranded by killing its node, and so is every
 * stake whose chain runs through it ({@link RelayChain}). A stranded stake builds nothing more and is no longer a
 * target: the claim is down when no stake stands unstranded ({@link #down}), The Line's win.
 */
public final class StakeTargets {

    /** What a standing stake is killed by: a planted stake's pylon, or a raised or established stake's node. */
    public enum By { PYLON, NODE }

    /**
     * One standing stake as a target: its lattice point, where it stands (its footprint centre, cells), the unit to kill
     * and how, and how many standing stakes that kill scraps or strands, itself included (the stakes whose chain runs
     * through it).
     */
    public record Target(int pointId, int x, int y, SurveyStake.Stage stage, By by, long unitId, int strands) { }

    private StakeTargets() { }

    /**
     * Every standing stake as a target, the kill that strands the most first, then by lattice point. A stake whose
     * pylon or node is not on the field (a raised stake's node not yet stood up), or that has no seat, is left out.
     *
     * @param stakes the claim's stakes by lattice point ({@link FallenHull#stakes})
     * @param pylons each planted stake's pylon unit, by lattice point
     * @param nodes  each raised or established stake's node unit, by lattice point
     * @param seats  each stake's footprint centre {x, y}, by lattice point
     */
    public static List<Target> of(Map<Integer, SurveyStake> stakes, Map<Integer, Long> pylons, Map<Integer, Long> nodes,
                                  Map<Integer, int[]> seats) {
        List<Target> out = new ArrayList<>();
        for (SurveyStake stake : stakes.values()) {
            if (stake.stranded() || stake.stage() == SurveyStake.Stage.SCRAP) continue;
            boolean planted = stake.stage() == SurveyStake.Stage.PLANTED;
            Long unit = (planted ? pylons : nodes).get(stake.pointId);
            int[] seat = seats.get(stake.pointId);
            if (unit == null || unit == 0L || seat == null) continue;
            out.add(new Target(stake.pointId, seat[0], seat[1], stake.stage(), planted ? By.PYLON : By.NODE, unit,
                    beyond(stake.pointId, stakes)));
        }
        out.sort(Comparator.comparingInt((Target t) -> -t.strands()).thenComparingInt(Target::pointId));
        return out;
    }

    /**
     * Whether the claim is down: every stake on it scrapped or stranded. A claim with no stakes is down, so a battle
     * that wins on it reads it only once the hull has planted.
     */
    public static boolean down(Map<Integer, SurveyStake> stakes) {
        for (SurveyStake stake : stakes.values()) {
            if (!stake.stranded() && stake.stage() != SurveyStake.Stage.SCRAP) return false;
        }
        return true;
    }

    /** The standing stakes whose chain runs through {@code point}, itself included. */
    private static int beyond(int point, Map<Integer, SurveyStake> stakes) {
        int n = 0;
        for (SurveyStake stake : stakes.values()) {
            if (stake.stranded() || stake.stage() == SurveyStake.Stage.SCRAP) continue;
            int at = stake.pointId;
            for (int hops = 0; hops <= stakes.size(); hops++) {
                if (at == point) {
                    n++;
                    break;
                }
                SurveyStake up = stakes.get(at);
                if (up == null) break;
                at = up.parent;
            }
        }
        return n;
    }
}
