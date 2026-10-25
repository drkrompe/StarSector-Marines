package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws a battle's optional objectives from a mission's pool.
 *
 * <p>A pure function of {@code (pool, laid, seed, min, max)}: it draws from its
 * own salted {@link Random}, never the simulation's, so a briefing and the
 * battle it describes roll the same set, and rolling never moves the battle.
 */
public final class ObjectiveRoll {

    private static final long SALT = 0x6F70_7469_6F6E_616CL;

    private ObjectiveRoll() { }

    /**
     * Filters the pool to what fits the laid map, picks a count in
     * {@code [min, max]} clamped to what fits, and draws that many by weight
     * without replacement, never two that exclude each other. The draw may come
     * up short when exclusions leave nothing drawable. Returned in draw order.
     */
    public static List<OptionalObjective> pick(List<OptionalObjective> pool, LaidLayout laid, long seed,
                                               int min, int max) {
        List<OptionalObjective> open = new ArrayList<>();
        for (OptionalObjective objective : pool) if (fits(objective, laid)) open.add(objective);
        int hi = Math.min(Math.max(0, max), open.size());
        int lo = Math.min(Math.max(0, min), hi);
        if (hi == 0) return List.of();
        Random random = new Random(seed ^ SALT);
        int count = lo + random.nextInt(hi - lo + 1);
        List<OptionalObjective> drawn = new ArrayList<>();
        while (drawn.size() < count) {
            open.removeIf(candidate -> drawn.stream().anyMatch(candidate::conflictsWith));
            if (open.isEmpty()) break;
            int total = 0;
            for (OptionalObjective candidate : open) total += candidate.weight();
            int roll = random.nextInt(total);
            for (int i = 0; i < open.size(); i++) {
                roll -= open.get(i).weight();
                if (roll < 0) {
                    drawn.add(open.remove(i));
                    break;
                }
            }
        }
        return List.copyOf(drawn);
    }

    /** Whether every structure the objective needs stands on the map, whole or in part (one of a need's alternatives). */
    public static boolean fits(OptionalObjective objective, LaidLayout laid) {
        LaidLayout layout = laid == null ? LaidLayout.EMPTY : laid;
        for (String need : objective.needs()) {
            boolean stands = false;
            for (String name : OptionalObjective.alternatives(need)) stands |= layout.has(name);
            if (!stands) return false;
        }
        return true;
    }
}
