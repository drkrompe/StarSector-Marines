package com.dillon.starsectormarines.battle.nav;

/** Frozen serial input for one squad's current movement step. */
public record SquadRouteRequest(int squadId, long routingEpoch, Object routeToken,
                                int goalX, int goalY, int[] startCells,
                                RouteCostField cost, String actionName) {
    public SquadRouteRequest {
        startCells = startCells.clone();
        actionName = actionName == null ? "unknown" : actionName;
    }

    /** Compatibility for callers without diagnostic action context. */
    public SquadRouteRequest(int squadId, long routingEpoch, Object routeToken,
                             int goalX, int goalY, int[] startCells, RouteCostField cost) {
        this(squadId, routingEpoch, routeToken, goalX, goalY, startCells, cost, "unknown");
    }

    @Override public int[] startCells() { return startCells.clone(); }
}
