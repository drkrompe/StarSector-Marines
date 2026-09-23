package com.dillon.starsectormarines.battle.decision.goap.scoring;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Assigns candidates to named slots, where each slot defines how many it wants
 * and a {@link Scorer} to rank candidates by. Result is a map from slot name
 * to the candidates filling it; slot order in {@link #assign} is preserved in
 * the returned map's iteration order.
 *
 * <p><b>Algorithm:</b> greedy fill in slot-priority order (slots with the
 * highest mean score over the candidate pool go first), then a swap-improvement
 * pass that exchanges pairs across slots when the total assigned score
 * improves. The Hungarian algorithm would be optimal, but squads cap around 8
 * and slot counts are tiny — greedy + swap converges to optimal in practice
 * here while being a fraction of the code, and stays in the noise on the
 * parallel-replan budget. Upgrade if profiling shows otherwise.
 *
 * <p><b>Thread-safety:</b> the class is stateless; {@link #assign} only reads
 * its inputs. Safe to call concurrently from the parallel replan window as
 * long as the underlying scorers are themselves pure.
 */
public final class RoleAssigner {

    private RoleAssigner() {}

    public record Slot<C>(String name, int count, Scorer<C> scorer) {
        public Slot {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(scorer, "scorer");
            if (count < 0) throw new IllegalArgumentException("slot count must be >= 0: " + count);
        }
    }

    public static <C> Map<String, List<C>> assign(List<C> candidates, List<Slot<C>> slots) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(slots, "slots");

        // LinkedHashMap preserves the caller's slot order in the result so
        // downstream code can index by slot index if it wants to.
        Map<String, List<C>> result = new LinkedHashMap<>();
        for (Slot<C> s : slots) {
            result.put(s.name(), new ArrayList<>(s.count()));
        }
        if (candidates.isEmpty() || slots.isEmpty()) {
            return result;
        }

        int candidateCount = candidates.size();
        int slotCount = slots.size();
        // A scorer reads live member positions, but all scores in one assign
        // call observe the same replan snapshot. Evaluate each pair once;
        // sorting and iterative swap improvement then walk compact floats.
        float[][] scores = new float[slotCount][candidateCount];
        for (int i = 0; i < slotCount; i++) {
            Scorer<C> scorer = slots.get(i).scorer();
            for (int j = 0; j < candidateCount; j++) {
                scores[i][j] = scorer.score(candidates.get(j));
            }
        }
        List<Integer> pool = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++) pool.add(i);

        // Greedy phase: rank slots by their mean score over the pool, fill
        // the most-discriminating slot first so its top picks aren't stolen
        // by a less-picky slot that could've taken anyone.
        List<Integer> ordered = new ArrayList<>(slotCount);
        float[] means = new float[slotCount];
        for (int i = 0; i < slotCount; i++) {
            ordered.add(i);
            double sum = 0d;
            for (int j = 0; j < candidateCount; j++) sum += scores[i][j];
            means[i] = (float) (sum / candidateCount);
        }
        ordered.sort(Comparator.comparingDouble((Integer i) -> means[i]).reversed());

        List<List<Integer>> assigned = new ArrayList<>(slotCount);
        for (Slot<C> slot : slots) assigned.add(new ArrayList<>(slot.count()));

        for (int slotIndex : ordered) {
            Slot<C> slot = slots.get(slotIndex);
            int want = Math.min(slot.count(), pool.size());
            if (want <= 0) continue;
            // Sort the remaining pool by this slot's preference, take the top N.
            pool.sort(Comparator.comparingDouble(
                    (Integer c) -> scores[slotIndex][c]).reversed());
            List<Integer> bucket = assigned.get(slotIndex);
            for (int i = 0; i < want; i++) {
                bucket.add(pool.get(i));
            }
            // Trim taken candidates from the pool after the fact so the
            // sort-by-score stride above stays simple.
            pool.subList(0, want).clear();
        }

        improveBySwapping(assigned, scores);
        for (int i = 0; i < slotCount; i++) {
            List<C> bucket = result.get(slots.get(i).name());
            for (int candidateIndex : assigned.get(i)) {
                bucket.add(candidates.get(candidateIndex));
            }
        }
        return result;
    }

    /**
     * Pairwise swap pass. For each (slot-i candidate, slot-j candidate) pair,
     * swap if the sum of the two slots' scorers improves. Repeated until a
     * full pass produces no improvement; converges quickly because each swap
     * strictly increases a bounded total.
     */
    private static void improveBySwapping(List<List<Integer>> assigned, float[][] scores) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < assigned.size(); i++) {
                List<Integer> li = assigned.get(i);
                for (int j = i + 1; j < assigned.size(); j++) {
                    List<Integer> lj = assigned.get(j);
                    for (int a = 0; a < li.size(); a++) {
                        for (int b = 0; b < lj.size(); b++) {
                            int ca = li.get(a);
                            int cb = lj.get(b);
                            float before = scores[i][ca] + scores[j][cb];
                            float after  = scores[i][cb] + scores[j][ca];
                            if (after > before) {
                                li.set(a, cb);
                                lj.set(b, ca);
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
    }
}
