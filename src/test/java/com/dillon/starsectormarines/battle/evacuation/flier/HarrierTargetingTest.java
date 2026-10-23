package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierTargeting.Candidate;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierTargeting.Prey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a pack dives on: what the line protects first, less what the line's air guns cover. */
class HarrierTargetingTest {

    @Test
    void gunsThenLogisticsThenMedicsThenTheIsolatedThenTheLine() {
        List<Candidate> all = List.of(
                new Candidate(1, 10, 0, Prey.INFANTRY, 0),
                new Candidate(2, 0, 10, Prey.ISOLATED, 0),
                new Candidate(3, -10, 0, Prey.MEDIC, 0),
                new Candidate(4, 0, -10, Prey.LOGISTICS, 0),
                new Candidate(5, 7, 7, Prey.GUN, 0));
        assertEquals(5, HarrierTargeting.pick(all, 0, 0, 0));
        assertEquals(4, HarrierTargeting.pick(all.subList(0, 4), 0, 0, 0));
        assertEquals(3, HarrierTargeting.pick(all.subList(0, 3), 0, 0, 0));
        assertEquals(2, HarrierTargeting.pick(all.subList(0, 2), 0, 0, 0));
    }

    @Test
    void anAirGunOverATargetMakesTheSameKindElsewhereTheChoice() {
        List<Candidate> all = List.of(
                new Candidate(1, 10, 0, Prey.GUN, 2),
                new Candidate(2, 14, 0, Prey.GUN, 0));
        assertEquals(2, HarrierTargeting.pick(all, 0, 0, 0));
    }

    @Test
    void aCurrentPreyIsKeptUnlessAnotherIsClearlyBetter() {
        List<Candidate> all = List.of(
                new Candidate(1, 10, 0, Prey.GUN, 0),
                new Candidate(2, 8, 0, Prey.GUN, 0));
        assertEquals(1, HarrierTargeting.pick(all, 0, 0, 1));
        assertEquals(2, HarrierTargeting.pick(all, 0, 0, 0));
    }

    @Test
    void nothingBeyondSenseRangeAndTiesGoToTheLowerId() {
        float far = HarrierRules.SENSE_RANGE + 1f;
        assertEquals(0, HarrierTargeting.pick(List.of(new Candidate(1, far, 0, Prey.GUN, 0)), 0, 0, 0));
        List<Candidate> tied = List.of(new Candidate(7, 5, 0, Prey.MEDIC, 0), new Candidate(3, 0, 5, Prey.MEDIC, 0));
        assertEquals(3, HarrierTargeting.pick(tied, 0, 0, 0));
    }
}
