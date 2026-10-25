package com.dillon.starsectormarines.battle.nav;

/**
 * Decides when a dense same-destination behavior routes through a shared-goal
 * reverse field instead of running its own A*.
 *
 * <p>A field costs one full-map reverse Dijkstra and then serves every mover
 * heading for that cell — for the rest of the frozen unit-update snapshot, and
 * for a bounded run of later snapshots. That trade only pays once enough movers
 * share goals, so below the crossover each behavior keeps running ordinary A*.
 *
 * <p>Live unit count stands in for goal fan-in. It is a proxy, but the right
 * kind: fan-in is not knowable before the goals are picked, and the crossover
 * is a property of the battle's size rather than of any one behavior.
 *
 * <p>Squad movement does not read it. A squad's members share one goal by
 * construction, so its fan-in is known, and its serially prepared corridor field
 * serves the squad at any battle size: gated at this crossover, a 286-unit Burn
 * stepped a whole battlegroup off with one cross-map A* per member, 152 on one
 * tick, where corridors at any size took that tick from 281 to 28 ms and left
 * Cordon Line and a 50-unit raid unchanged.
 *
 * <p>{@link #MAX_SQUAD_ROUTE_BUILDS_PER_TICK} bounds the serial corridor-build
 * wave after synchronized replans.
 */
public final class SharedGoalPolicy {

    /**
     * Fixed-slice crossover measured on the evacuation swarm: A* wins at 202
     * live units, shared fields win at 322 and above.
     */
    static final int MINIMUM_SHARED_GOAL_UNITS = 300;

    /** Serial squad-route builds started per tick; stale compatible routes roll forward. */
    static final int MAX_SQUAD_ROUTE_BUILDS_PER_TICK = 4;

    private SharedGoalPolicy() {}

    /** Whether a battle this size should serve dense same-destination routes off shared fields. */
    public static boolean usesSharedGoalFields(int liveUnits) {
        return liveUnits >= MINIMUM_SHARED_GOAL_UNITS;
    }

    public static int minimumSharedGoalUnits() {
        return MINIMUM_SHARED_GOAL_UNITS;
    }
}
