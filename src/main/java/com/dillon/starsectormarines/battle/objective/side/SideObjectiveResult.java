package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;

/**
 * How one side objective came out, as the campaign receives it: its final state, the debrief line chosen by that state,
 * and the campaign mark it earned (empty for none).
 */
public record SideObjectiveResult(String key, String title, State outcome, String debrief, String mark) {
    public SideObjectiveResult {
        if (outcome == null || !outcome.isFinal()) throw new IllegalArgumentException("a side objective result is final");
        title = title == null ? key : title;
        debrief = debrief == null ? "" : debrief;
        mark = mark == null ? "" : mark;
    }

    public boolean marked() { return !mark.isEmpty(); }
}
