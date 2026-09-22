package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Frozen public topology with no live-simulation or mutation surface. */
public final class CommandTopology {

    public record Zone(int id, int[] cells, List<Integer> adjacentZones) {
        public Zone {
            cells = cells.clone();
            adjacentZones = List.copyOf(adjacentZones);
        }

        @Override public int[] cells() { return cells.clone(); }
        public int cellCount() { return cells.length; }
    }

    private final NavigationGrid grid;
    private final int[] zoneByCell;
    private final List<Zone> zones;

    /**
     * Connected-component id per cell, {@code -1} where non-walkable. Computed
     * once here because the grid is frozen for the whole pulse, which turns
     * every reachability question into an array compare instead of a search —
     * see {@link #reachable}.
     */
    private final int[] componentByCell;

    private CommandTopology(NavigationGrid grid, int[] zoneByCell,
                            List<Zone> zones, int[] componentByCell) {
        this.grid = grid;
        this.zoneByCell = zoneByCell;
        this.zones = List.copyOf(zones);
        this.componentByCell = componentByCell;
    }

    public static CommandTopology freeze(BattleView sim) {
        NavigationGrid live = sim.getGrid();
        NavigationGrid copy = live.copyNavigationTopology();

        ZoneGraph graph = sim.getZoneGraph();
        int[] zoneByCell = new int[live.getWidth() * live.getHeight()];
        java.util.Arrays.fill(zoneByCell, -1);
        List<Zone> zones = new ArrayList<>(graph.getZones().size());
        for (NavigationZone zone : graph.getZones()) {
            int[] cells = zone.getCellIndices().clone();
            for (int cell : cells) zoneByCell[cell] = zone.getZoneId();
            zones.add(new Zone(zone.getZoneId(), cells,
                    graph.adjacentZones(zone.getZoneId())));
        }
        return new CommandTopology(copy, zoneByCell, zones,
                GridPathfinder.labelConnectedComponents(copy));
    }

    public int width() { return grid.getWidth(); }
    public int height() { return grid.getHeight(); }
    public boolean inBounds(int x, int y) { return grid.inBounds(x, y); }
    public boolean isWalkable(int x, int y) { return grid.isWalkable(x, y); }
    public boolean isDoorwayCell(int cellIndex) { return grid.isDoorwayAt(cellIndex); }

    public int zoneIdAt(int x, int y) {
        return inBounds(x, y) ? zoneByCell[y * width() + x] : -1;
    }

    public Zone zone(int id) {
        return id >= 0 && id < zones.size() ? zones.get(id) : null;
    }

    public List<Zone> zones() { return zones; }

    public boolean areZonesConnected(int startZone, int targetZone) {
        if (startZone < 0 || targetZone < 0) return false;
        if (startZone == targetZone) return true;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        queue.add(startZone);
        visited.add(startZone);
        while (!queue.isEmpty()) {
            Zone zone = zone(queue.removeFirst());
            if (zone == null) continue;
            for (int adjacent : zone.adjacentZones()) {
                if (adjacent == targetZone) return true;
                if (visited.add(adjacent)) queue.addLast(adjacent);
            }
        }
        return false;
    }

    /**
     * Whether a route exists between two cells on the frozen grid.
     *
     * <p>Answered from the precomputed component labeling rather than by running
     * a search. This is the same answer {@code GridPathfinder.findPath(...).length > 0}
     * gives — connectivity does not depend on step costs — for a fraction of the
     * cost: a commander pulse asks this a few hundred times over one frozen grid,
     * and every ask used to be a full A*.
     */
    public boolean reachable(int startX, int startY, int targetX, int targetY) {
        return sameComponent(startX, startY, targetX, targetY);
    }

    /** Frozen-grid route length, or {@code Integer.MAX_VALUE} when unreachable. */
    public int routeLength(int startX, int startY, int targetX, int targetY) {
        if (!inBounds(startX, startY) || !inBounds(targetX, targetY)) {
            return Integer.MAX_VALUE;
        }
        if (startX == targetX && startY == targetY) return 0;
        // Settle the unreachable case without searching. This is where a search
        // costs the most, not the least: A* only reports failure after draining
        // the entire reachable region.
        if (!sameComponent(startX, startY, targetX, targetY)) {
            return Integer.MAX_VALUE;
        }
        int length = GridPathfinder.findPath(grid, startX, startY,
                targetX, targetY).length;
        return length > 0 ? length : Integer.MAX_VALUE;
    }

    /** Frozen-grid route cells, or an empty path when the endpoints do not connect. */
    public int[] route(int startX, int startY, int targetX, int targetY) {
        if (!inBounds(startX, startY) || !inBounds(targetX, targetY)) {
            return GridPathfinder.EMPTY_PATH;
        }
        if (!sameComponent(startX, startY, targetX, targetY)) {
            return GridPathfinder.EMPTY_PATH;
        }
        return GridPathfinder.findPath(grid, startX, startY, targetX, targetY);
    }

    /**
     * Both cells walkable and mutually reachable. A non-walkable endpoint lands
     * on {@code -1} and fails, matching the pathfinder's own refusal to start or
     * end off the walkable set.
     */
    private boolean sameComponent(int startX, int startY, int targetX, int targetY) {
        if (!inBounds(startX, startY) || !inBounds(targetX, targetY)) return false;
        int start = componentByCell[startY * width() + startX];
        return start >= 0 && start == componentByCell[targetY * width() + targetX];
    }
}
