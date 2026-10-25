package com.dillon.starsectormarines.battle.objective.optional;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * One entry in a mission's pool of optional objectives.
 *
 * @param key stable id; the beats it publishes and its result are keyed by it
 * @param title player-facing name
 * @param briefing player-facing line for the briefing
 * @param needs names of placed structures in the map's laid layout; it fits only when each stands, whole or in part.
 *              A need may name alternatives joined by {@code |} (a farmstead in open country, a tenement in a city),
 *              met by whichever stands. Empty fits every map
 * @param excludes keys never drawn in the same battle as this one (checked both ways)
 * @param weight relative chance in the roll, at least one
 * @param reward paid on success
 * @param cost carried downstream on failure
 * @param start builds the battle-local runtime once the objective is rolled
 */
public record OptionalObjective(String key, String title, String briefing, Set<String> needs, Set<String> excludes,
                                int weight, Reward reward, Cost cost,
                                Function<ObjectiveContext, ObjectiveRuntime> start) {

    public OptionalObjective {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("An optional objective needs a key");
        if (weight < 1) throw new IllegalArgumentException("Objective " + key + " needs a weight of at least one");
        if (start == null) throw new IllegalArgumentException("Objective " + key + " needs a runtime");
        title = title == null ? key : title;
        briefing = briefing == null ? "" : briefing;
        needs = needs == null ? Set.of() : Set.copyOf(needs);
        excludes = excludes == null ? Set.of() : Set.copyOf(excludes);
        reward = reward == null ? Reward.NONE : reward;
        cost = cost == null ? Cost.NONE : cost;
    }

    /** The piece names one need is met by: its alternatives, or the need itself. */
    public static List<String> alternatives(String need) {
        return List.of(need.split("\\|"));
    }

    /** Whether either of the two names the other in its excludes. */
    public boolean conflictsWith(OptionalObjective other) {
        return excludes.contains(other.key) || other.excludes.contains(key);
    }
}
