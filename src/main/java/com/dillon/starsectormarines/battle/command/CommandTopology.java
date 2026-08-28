package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
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

    private CommandTopology(NavigationGrid grid, int[] zoneByCell,
                            List<Zone> zones) {
        this.grid = grid;
        this.zoneByCell = zoneByCell;
        this.zones = List.copyOf(zones);
    }

    public static CommandTopology freeze(BattleView sim) {
        NavigationGrid live = sim.getGrid();
        NavigationGrid copy = new NavigationGrid(live.getWidth(), live.getHeight());
        for (int y = 0; y < live.getHeight(); y++) {
            for (int x = 0; x < live.getWidth(); x++) {
                for (CellTag tag : CellTag.values()) {
                    copy.setTag(x, y, tag, live.hasTag(x, y, tag));
                }
                for (Direction direction : Direction.ALL) {
                    copy.setEdgePassable(x, y, direction,
                            live.isEdgePassable(x, y, direction));
                }
            }
        }

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
        return new CommandTopology(copy, zoneByCell, zones);
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

    public boolean reachable(int startX, int startY, int targetX, int targetY) {
        return GridPathfinder.findPath(grid, startX, startY,
                targetX, targetY).length > 0;
    }

    /** Frozen-grid route length, or {@code Integer.MAX_VALUE} when unreachable. */
    public int routeLength(int startX, int startY, int targetX, int targetY) {
        if (!inBounds(startX, startY) || !inBounds(targetX, targetY)) {
            return Integer.MAX_VALUE;
        }
        if (startX == targetX && startY == targetY) return 0;
        int length = GridPathfinder.findPath(grid, startX, startY,
                targetX, targetY).length;
        return length > 0 ? length : Integer.MAX_VALUE;
    }

    /** Frozen-grid route cells, or an empty path when the endpoints do not connect. */
    public int[] route(int startX, int startY, int targetX, int targetY) {
        if (!inBounds(startX, startY) || !inBounds(targetX, targetY)) {
            return GridPathfinder.EMPTY_PATH;
        }
        return GridPathfinder.findPath(grid, startX, startY, targetX, targetY);
    }
}
