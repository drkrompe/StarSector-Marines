package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierTargeting.Candidate;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierTargeting.Prey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** {@code m} riflemen spaced round the area's centre, and {@code n} divers coming in from the ring. */
    private static long[] spread(int n, int m) {
        List<Candidate> targets = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            double a = 2 * Math.PI * i / m;
            targets.add(new Candidate(10 + i, (float) Math.cos(a) * 3f, (float) Math.sin(a) * 3f, Prey.INFANTRY, 0));
        }
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            xs[i] = (float) Math.cos(a) * 30f;
            ys[i] = (float) Math.sin(a) * 30f;
        }
        return HarrierTargeting.assign(targets, 0f, 0f, xs, ys);
    }

    @Test
    void everyDiverHasItsOwnPreyWhileThereAreEnoughToGoRound() {
        for (int n = 1; n <= 10; n++) {
            for (int m = n; m <= 12; m++) {
                long[] picks = spread(n, m);
                Set<Long> distinct = new HashSet<>();
                for (long p : picks) {
                    assertTrue(p != 0L);
                    distinct.add(p);
                }
                assertEquals(n, distinct.size(), n + " divers on " + m + " bodies");
            }
        }
    }

    @Test
    void shortOfPreyTheDiversShareAsEvenlyAsTheyCan() {
        long[] picks = spread(10, 4);
        Map<Long, Integer> claims = new HashMap<>();
        for (long p : picks) claims.merge(p, 1, Integer::sum);
        assertEquals(4, claims.size());
        for (int c : claims.values()) assertTrue(c == 2 || c == 3, "shared " + claims);
    }

    @Test
    void onlyBodiesNearThePlaceThePackChoseAreShared() {
        List<Candidate> targets = List.of(
                new Candidate(1, 0, 0, Prey.GUN, 0),
                new Candidate(2, 2, 0, Prey.INFANTRY, 0),
                new Candidate(3, HarrierRules.SPREAD_RADIUS + 5f, 0, Prey.MEDIC, 0));
        long[] picks = HarrierTargeting.assign(targets, 0f, 0f, new float[]{20, 20, 20}, new float[]{0, 1, 2});
        assertEquals(1L, picks[0], "the first diver takes the best body there");
        assertEquals(2L, picks[1]);
        assertTrue(picks[2] == 1L || picks[2] == 2L, "the medic out of the area is left alone");
    }
}