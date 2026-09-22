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
 * is a property of the battle's size rather than of any one behavior. Both the
 * evacuation swarm and the zone advance read this one number so a profiler
 * comparison moves both together.
 *
 * <p>{@code -Dbattle.pathfinding.sharedGoalFields=false} forces A* everywhere;
 * {@code -Dbattle.pathfinding.minimumSharedGoalUnits=0} forces fields, and
 * {@link Integer#MAX_VALUE} forces A*. Those two are the repeatable ends of a
 * profiler sweep. {@code -Dbattle.pathfinding.squadRouteCorridors=false}
 * restores the former whole-map squad fields while leaving evacuation swarms
 * unchanged. {@code -Dbattle.pathfinding.maxSquadRouteBuildsPerTick=N}
 * bounds the serial corridor-build wave after synchronized replans.
 */
public final class SharedGoalPolicy {

    public static final String SHARED_GOAL_FIELDS_PROPERTY =
            "battle.pathfinding.sharedGoalFields";
    public static final String MINIMUM_SHARED_GOAL_UNITS_PROPERTY =
            "battle.pathfinding.minimumSharedGoalUnits";
    public static final String SQUAD_ROUTE_CORRIDORS_PROPERTY =
            "battle.pathfinding.squadRouteCorridors";
    public static final String MAX_SQUAD_ROUTE_BUILDS_PROPERTY =
            "battle.pathfinding.maxSquadRouteBuildsPerTick";

    /**
     * Fixed-slice crossover measured on the evacuation swarm: A* wins at 202
     * live units, shared fields win at 322 and above.
     */
    static final int DEFAULT_MINIMUM_SHARED_GOAL_UNITS = 300;

    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty(SHARED_GOAL_FIELDS_PROPERTY, "true"));
    private static final int MINIMUM_UNITS = loadMinimumSharedGoalUnits();
    private static final boolean SQUAD_ROUTE_CORRIDORS = Boolean.parseBoolean(
            System.getProperty(SQUAD_ROUTE_CORRIDORS_PROPERTY, "true"));
    private static final int MAX_SQUAD_ROUTE_BUILDS = loadMaximumSquadRouteBuilds();

    private SharedGoalPolicy() {}

    /** Whether a battle this size should serve dense same-destination routes off shared fields. */
    public static boolean usesSharedGoalFields(int liveUnits) {
        return ENABLED && liveUnits >= MINIMUM_UNITS;
    }

    /** Whether squad movement should use serially prepared local route fields. */
    public static boolean usesSquadRouteCorridors(int liveUnits) {
        return SQUAD_ROUTE_CORRIDORS && usesSharedGoalFields(liveUnits);
    }

    /** Compile-once switch used by the navigation service after caller gating. */
    static boolean squadRouteCorridorsEnabled() {
        return SQUAD_ROUTE_CORRIDORS && ENABLED;
    }

    /** Deterministic serial-build budget; stale compatible routes roll forward. */
    static int maximumSquadRouteBuildsPerTick() {
        return MAX_SQUAD_ROUTE_BUILDS;
    }

    public static int configuredMinimumSharedGoalUnits() {
        return MINIMUM_UNITS;
    }

    private static int loadMinimumSharedGoalUnits() {
        int configured = Integer.getInteger(
                MINIMUM_SHARED_GOAL_UNITS_PROPERTY,
                DEFAULT_MINIMUM_SHARED_GOAL_UNITS);
        if (configured < 0) {
            throw new IllegalArgumentException(
                    MINIMUM_SHARED_GOAL_UNITS_PROPERTY + " must be non-negative");
        }
        return configured;
    }

    private static int loadMaximumSquadRouteBuilds() {
        int configured = Integer.getInteger(MAX_SQUAD_ROUTE_BUILDS_PROPERTY, 4);
        if (configured < 0) {
            throw new IllegalArgumentException(
                    MAX_SQUAD_ROUTE_BUILDS_PROPERTY + " must be non-negative");
        }
        return configured;
    }
}
