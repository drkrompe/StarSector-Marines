package com.dillon.starsectormarines.battle.ai.goap.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

        List<C> pool = new ArrayList<>(candidates);

        // Greedy phase: rank slots by their mean score over the pool, fill
        // the most-discriminating slot first so its top picks aren't stolen
        // by a less-picky slot that could've taken anyone.
        List<Slot<C>> ordered = new ArrayList<>(slots);
        Map<Slot<C>, Float> meanCache = new HashMap<>();
        for (Slot<C> s : slots) meanCache.put(s, meanScore(s.scorer(), pool));
        ordered.sort(Comparator.comparingDouble((Slot<C> s) -> meanCache.get(s)).reversed());

        for (Slot<C> slot : ordered) {
            int want = Math.min(slot.count(), pool.size());
            if (want <= 0) continue;
            // Sort the remaining pool by this slot's preference, take the top N.
            pool.sort(Comparator.comparingDouble((C c) -> slot.scorer().score(c)).reversed());
            List<C> bucket = result.get(slot.name());
            for (int i = 0; i < want; i++) {
                bucket.add(pool.get(i));
            }
            // Trim taken candidates from the pool after the fact so the
            // sort-by-score stride above stays simple.
            pool.subList(0, want).clear();
        }

        improveBySwapping(result, slots);
        return result;
    }

    /**
     * Pairwise swap pass. For each (slot-i candidate, slot-j candidate) pair,
     * swap if the sum of the two slots' scorers improves. Repeated until a
     * full pass produces no improvement; converges quickly because each swap
     * strictly increases a bounded total.
     */
    private static <C> void improveBySwapping(Map<String, List<C>> assigned, List<Slot<C>> slots) {
        Map<String, Slot<C>> bySlotName = new HashMap<>();
        for (Slot<C> s : slots) bySlotName.put(s.name(), s);

        List<String> names = new ArrayList<>(assigned.keySet());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < names.size(); i++) {
                Slot<C> si = bySlotName.get(names.get(i));
                List<C> li = assigned.get(names.get(i));
                for (int j = i + 1; j < names.size(); j++) {
                    Slot<C> sj = bySlotName.get(names.get(j));
                    List<C> lj = assigned.get(names.get(j));
                    for (int a = 0; a < li.size(); a++) {
                        for (int b = 0; b < lj.size(); b++) {
                            C ca = li.get(a);
                            C cb = lj.get(b);
                            float before = si.scorer().score(ca) + sj.scorer().score(cb);
                            float after  = si.scorer().score(cb) + sj.scorer().score(ca);
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

    private static <C> float meanScore(Scorer<C> s, List<C> pool) {
        if (pool.isEmpty()) return 0f;
        double sum = 0d;
        for (C c : pool) sum += s.score(c);
        return (float) (sum / pool.size());
    }
}
