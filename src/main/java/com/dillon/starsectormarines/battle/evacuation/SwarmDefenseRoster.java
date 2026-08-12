package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic, mission-local runner roster for civilian rescue battles. */
public final class SwarmDefenseRoster {

    public static final int LOW_COUNT = 12;
    public static final int MEDIUM_COUNT = 24;
    public static final int HIGH_COUNT = 40;

    private final long[] entityIds;

    private SwarmDefenseRoster(long[] entityIds) {
        this.entityIds = entityIds;
    }

    /**
     * Plans every runner cell before spawning any entity. Returns {@code null}
     * when a complete reachable roster cannot be placed.
     */
    public static SwarmDefenseRoster install(
            BattleSimulation sim, CivilianEvacuationPlacement placement,
            RiskLevel risk, long seed) {
        if (sim == null || placement == null) return null;
        int count = countFor(risk);
        NavigationGrid grid = sim.getGrid();
        boolean[] reachable = reachableFromShelter(grid, placement);
        boolean[] occupied = occupiedCells(sim, grid);
        List<Integer> candidates = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int cell = grid.index(x, y);
                if (!reachable[cell] || occupied[cell]
                        || insideShelterZone(x, y, placement)
                        || insideLiftZone(x, y, placement)) {
                    continue;
                }
                candidates.add(cell);
            }
        }
        candidates.sort(Comparator
                .comparingLong((Integer cell) -> priority(seed, cell))
                .thenComparingInt(Integer::intValue));
        if (candidates.size() < count) return null;

        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            int cell = candidates.get(i);
            int x = cell % grid.getWidth();
            int y = cell / grid.getWidth();
            ids[i] = sim.spawn(new EntitySpec(
                    "Swarm Runner " + (i + 1), Faction.DEFENDER,
                    UnitType.SWARM_RUNNER, x, y)
                    .role(UnitRole.SWARM_PRESSURE));
        }
        return new SwarmDefenseRoster(ids);
    }

    public static int countFor(RiskLevel risk) {
        if (risk == null) return LOW_COUNT;
        switch (risk) {
            case MEDIUM: return MEDIUM_COUNT;
            case HIGH: return HIGH_COUNT;
            case LOW:
            default: return LOW_COUNT;
        }
    }

    public int size() {
        return entityIds.length;
    }

    public long entityId(int index) {
        return entityIds[index];
    }

    private static boolean[] reachableFromShelter(
            NavigationGrid grid, CivilianEvacuationPlacement placement) {
        boolean[] reachable = new boolean[
                grid.getWidth() * grid.getHeight()];
        if (!grid.isWalkable(placement.shelterX, placement.shelterY)) {
            return reachable;
        }
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int start = grid.index(placement.shelterX, placement.shelterY);
        reachable[start] = true;
        open.add(start);
        while (!open.isEmpty()) {
            int cell = open.removeFirst();
            int x = cell % grid.getWidth();
            int y = cell / grid.getWidth();
            for (Direction direction : Direction.ALL) {
                int nx = x + direction.dx;
                int ny = y + direction.dy;
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)
                        || !grid.isEdgePassable(x, y, direction)) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (reachable[next]) continue;
                reachable[next] = true;
                open.addLast(next);
            }
        }
        return reachable;
    }

    private static boolean[] occupiedCells(BattleSimulation sim,
                                            NavigationGrid grid) {
        boolean[] occupied = new boolean[grid.getWidth() * grid.getHeight()];
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long entity = sim.liveUnitAt(i);
            int x = sim.world().cellX(entity);
            int y = sim.world().cellY(entity);
            if (grid.inBounds(x, y)) occupied[grid.index(x, y)] = true;
        }
        return occupied;
    }

    private static boolean insideShelterZone(
            int x, int y, CivilianEvacuationPlacement placement) {
        return Math.abs(x - placement.shelterX)
                + Math.abs(y - placement.shelterY)
                <= CivilianEvacuationPlacement.SHELTER_ZONE_RADIUS;
    }

    private static boolean insideLiftZone(
            int x, int y, CivilianEvacuationPlacement placement) {
        return Math.abs(x - placement.liftX)
                <= CivilianEvacuationPlacement.LIFT_ZONE_RADIUS
                && Math.abs(y - placement.liftY)
                <= CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
    }

    private static long priority(long seed, int cell) {
        long value = seed ^ (cell * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
