package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;

/**
 * How one side objective came out, as the campaign receives it: its final state, the debrief line chosen by that state
 * (empty for a hidden job missed without an authored line), the campaign mark it earned (empty for none) and the credits
 * it earned, paid at settlement.
 */
public record SideObjectiveResult(String key, String title, State outcome, String debrief, String mark, int credits) {
    public SideObjectiveResult(String key, String title, State outcome, String debrief, String mark) {
        this(key, title, outcome, debrief, mark, 0);
    }

    public SideObjectiveResult {
        if (outcome == null || !outcome.isFinal()) throw new IllegalArgumentException("a side objective result is final");
        if (credits < 0) throw new IllegalArgumentException("a side objective earns no negative credits");
        title = title == null ? key : title;
        debrief = debrief == null ? "" : debrief;
        mark = mark == null ? "" : mark;
    }

    public boolean marked() { return !mark.isEmpty(); }

    /** Whether the debrief names this job: every job the player knew of, and a missed one only with an authored line. */
    public boolean reported() { return outcome != State.MISSED || !debrief.isEmpty(); }
}
