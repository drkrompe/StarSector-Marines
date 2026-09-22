package com.dillon.starsectormarines.battle.nav;

/** Frozen serial input for one squad's current movement step. */
public record SquadRouteRequest(int squadId, long routingEpoch, Object routeToken,
                                int goalX, int goalY, int[] startCells,
                                RouteCostField cost) {
    public SquadRouteRequest {
        startCells = startCells.clone();
    }

    @Override public int[] startCells() { return startCells.clone(); }
}
