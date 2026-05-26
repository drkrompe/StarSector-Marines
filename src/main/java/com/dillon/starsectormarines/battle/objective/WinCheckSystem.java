package com.dillon.starsectormarines.battle.objective;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * Stateless tick consumer that evaluates the win condition off the current
 * objective set. Tallies per-side complete/failed state, applies the
 * "conjunctive complete, disjunctive fail" rule, and returns a
 * {@link WinResult} when the battle should end. {@link WinResult#isOngoing()}
 * is true while neither side has won or lost.
 *
 * <p>The "complete" check is conjunctive per side ({@link Objective#isComplete()}
 * across all marine objectives, then all defender ones); "failed" is
 * disjunctive (any single failure flips the side to lost). With only the
 * default {@code EliminateFactionObjective} pair, this reduces to the old
 * "last faction standing" behavior — but mission-specific objectives
 * (charge sites, extraction, raid crates) layer in without changing this
 * code.
 *
 * <p>Sibling to {@link ObjectivesService} (which owns the objective list +
 * per-tick dispatch). Lives in this same package because win evaluation is
 * objective-driven — no other dependency.
 */
public final class WinCheckSystem {

    /**
     * Outcome of one tick of evaluation. {@link #isOngoing()} means no
     * terminal state; otherwise {@link #winner()} is the victor, or
     * {@code null} for a mutual-failure draw.
     */
    public record WinResult(boolean complete, Faction winner) {
        public static final WinResult ONGOING = new WinResult(false, null);
        public boolean isOngoing() { return !complete; }
    }

    /**
     * Returns {@link WinResult#ONGOING} when the battle hasn't terminated,
     * otherwise a result with {@code complete=true} and the winning side
     * (or {@code null} for a mutual-failure tie). Pure function over the
     * current objective list; the sim owns the writeback to its
     * {@code complete} / {@code winner} fields.
     */
    public WinResult tick(List<Objective> objectives) {
        boolean marineFailed = false, marineAllComplete = true, marineHasObjective = false;
        boolean defenderFailed = false, defenderAllComplete = true, defenderHasObjective = false;
        for (int i = 0, n = objectives.size(); i < n; i++) {
            Objective o = objectives.get(i);
            if (o.owningFaction() == Faction.MARINE) {
                marineHasObjective = true;
                if (o.isFailed()) marineFailed = true;
                if (!o.isComplete()) marineAllComplete = false;
            } else if (o.owningFaction() == Faction.DEFENDER) {
                defenderHasObjective = true;
                if (o.isFailed()) defenderFailed = true;
                if (!o.isComplete()) defenderAllComplete = false;
            }
        }
        boolean marineWin = marineHasObjective && marineAllComplete && !marineFailed;
        boolean defenderWin = defenderHasObjective && defenderAllComplete && !defenderFailed;
        if (!marineWin && !defenderWin && !marineFailed && !defenderFailed) return WinResult.ONGOING;
        Faction winner;
        if (marineWin && !defenderWin)            winner = Faction.MARINE;
        else if (defenderWin && !marineWin)       winner = Faction.DEFENDER;
        else if (marineFailed && !defenderFailed) winner = Faction.DEFENDER;
        else if (defenderFailed && !marineFailed) winner = Faction.MARINE;
        else                                      winner = null;
        return new WinResult(true, winner);
    }
}
