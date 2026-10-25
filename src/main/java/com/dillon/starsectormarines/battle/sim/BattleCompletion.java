package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Single terminal boundary shared by objective completion and player withdrawal. */
final class BattleCompletion {
    private final CivilianEvacuationTracker evacuation;
    private boolean enabled = true;
    private boolean complete;
    private boolean retreated;
    private Faction winner;

    BattleCompletion(CivilianEvacuationTracker evacuation) {
        this.evacuation = evacuation;
    }

    boolean isComplete() { return complete; }
    /** Whether the terminal result was the player's withdrawal rather than objectives. */
    boolean retreated() { return retreated; }
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
        if (!finish(Faction.DEFENDER)) return false;
        retreated = true;
        return true;
    }

    /** Setup seam for embedded rooms, matching their non-terminal battle clock. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            complete = false;
            retreated = false;
            winner = null;
        }
    }
}
