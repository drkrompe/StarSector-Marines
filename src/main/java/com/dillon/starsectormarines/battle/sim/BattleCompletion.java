package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Single terminal boundary shared by objective completion and player withdrawal. */
final class BattleCompletion {
    private final CivilianEvacuationTracker evacuation;
    private boolean enabled = true;
    private boolean complete;
    private Faction winner;

    BattleCompletion(CivilianEvacuationTracker evacuation) {
        this.evacuation = evacuation;
    }

    boolean isComplete() { return complete; }
    Faction winner() { return winner; }

    /** First terminal result wins; non-mission room hosts cannot be completed. */
    boolean finish(Faction winner) {
        if (!enabled || complete) return false;
        this.winner = winner;
        complete = true;
        // Incomplete setup retains the no-report sentinel rather than inventing facts.
        evacuation.seal();
        return true;
    }

    boolean retreat() {
        return finish(Faction.DEFENDER);
    }

    /** Setup seam for embedded rooms, matching their non-terminal battle clock. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            complete = false;
            winner = null;
        }
    }
}
