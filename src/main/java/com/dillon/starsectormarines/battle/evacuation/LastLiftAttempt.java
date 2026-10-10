package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import java.util.ArrayList;

/** Binds stable story people to this attempt's physical identities, then freezes once. */
public final class LastLiftAttempt {
    private final LastLiftProgress.Attempt attempt;
    private final CivilianEvacuationPayload payload;
    private LastLiftProgress.Outcome outcome;
    public LastLiftAttempt(LastLiftProgress.Attempt attempt, CivilianEvacuationPayload payload) {
        if (attempt.people().size() != payload.size() || attempt.people().isEmpty()
                || attempt.people().stream().distinct().count() != payload.size()
                || attempt.people().stream().anyMatch(i -> i < 0 || i >= LastLiftProgress.COHORT_SIZE))
            throw new IllegalArgumentException("Invalid Last Lift roster");
        this.attempt = attempt; this.payload = payload;
    }
    public LastLiftProgress.Attempt identity() { return attempt; }
    public long entityId(int index) { return payload.entityId(index); }
    public LastLiftProgress.Outcome outcome() { return outcome; }
    public void freeze(BattleSimulation sim) {
        if (outcome != null || !sim.isComplete()) return;
        var tracker = sim.getCivilianEvacuationTracker();
        var rescued = new ArrayList<Integer>();
        var dead = new ArrayList<Integer>();
        for (int i = 0; i < payload.size(); i++) {
            long id = payload.entityId(i);
            if (tracker.state(id) == CivilianEvacuationTracker.State.EVACUATED) rescued.add(attempt.people().get(i));
            else if (tracker.isConfirmedDead(id)) dead.add(attempt.people().get(i));
        }
        outcome = new LastLiftProgress.Outcome(attempt, rescued, dead, sim.getWinner() == Faction.MARINE);
    }
}
