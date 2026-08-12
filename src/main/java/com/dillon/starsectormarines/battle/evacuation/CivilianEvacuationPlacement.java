package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic shelter/lift placement for the civilian-rescue payload.
 * Produces all eight unique spawn cells or no placement; callers never install
 * a partial representative cohort.
 */
public final class CivilianEvacuationPlacement {

    public static final int LIFT_ZONE_RADIUS = 1;
    public static final int SHELTER_ZONE_RADIUS = 5;
    private static final int EDGE_BAND = 2;

    public final int shelterX;
    public final int shelterY;
    public final int liftX;
    public final int liftY;
    private final int[] spawnCells;

    private CivilianEvacuationPlacement(int shelterX, int shelterY,
                                        int liftX, int liftY,
                                        int[] spawnCells) {
        this.shelterX = shelterX;
        this.shelterY = shelterY;
        this.liftX = liftX;
        this.liftY = liftY;
        this.spawnCells = spawnCells;
    }

    /**
     * Finds a complete reachable placement, or {@code null} if the map has no
     * suitable residential shelter, outer-band lift, or eight spawn cells.
     */
    public static CivilianEvacuationPlacement find(
            NavigationGrid grid, List<PointOfInterest> pointsOfInterest,
            long seed) {
        if (grid == null || pointsOfInterest == null) return null;
        List<PointOfInterest> shelters = new ArrayList<>();
        for (PointOfInterest poi : pointsOfInterest) {
            if (poi != null && poi.kind == PointOfInterest.Kind.RESIDENTIAL
                    && grid.isWalkable(poi.interiorAnchorX,
                    poi.interiorAnchorY)) {
                shelters.add(poi);
            }
        }
        shelters.sort(Comparator
                .comparingInt((PointOfInterest p) -> p.interiorAnchorY)
                .thenComparingInt(p -> p.interiorAnchorX)
                .thenComparingInt(p -> p.top)
                .thenComparingInt(p -> p.left));
        if (shelters.isEmpty()) return null;

        int start = Math.floorMod(mix32(seed), shelters.size());
        for (int offset = 0; offset < shelters.size(); offset++) {
            PointOfInterest shelter = shelters.get(
                    (start + offset) % shelters.size());
            CivilianEvacuationPlacement placement =
                    forShelter(grid, shelter);
            if (placement != null) return placement;
        }
        return null;
    }

    public int spawnCount() {
        return spawnCells.length / 2;
    }

    public int spawnX(int index) {
        checkSpawnIndex(index);
        return spawnCells[index * 2];
    }

    public int spawnY(int index) {
        checkSpawnIndex(index);
        return spawnCells[index * 2 + 1];
    }

    private static CivilianEvacuationPlacement forShelter(
            NavigationGrid grid, PointOfInterest shelter) {
        int sx = shelter.interiorAnchorX;
        int sy = shelter.interiorAnchorY;
        int[] lift = farthestReachableLift(grid, sx, sy);
        if (lift == null) return null;
        int[] spawns = reachableSpawnCells(
                grid, shelter, sx, sy, lift[0], lift[1]);
        if (spawns == null) return null;
        return new CivilianEvacuationPlacement(
                sx, sy, lift[0], lift[1], spawns);
    }

    private static int[] farthestReachableLift(NavigationGrid grid,
                                                int sx, int sy) {
        int bestX = -1;
        int bestY = -1;
        int bestDistance = -1;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!inOuterBand(grid, x, y) || !grid.isWalkable(x, y)) {
                    continue;
                }
                int distance = Math.abs(x - sx) + Math.abs(y - sy);
                if (distance < bestDistance) continue;
                int[] path = GridPathfinder.findPath(grid, sx, sy, x, y);
                if (Paths.isEmpty(path)) continue;
                if (distance > bestDistance
                        || y < bestY || (y == bestY && x < bestX)) {
                    bestX = x;
                    bestY = y;
                    bestDistance = distance;
                }
            }
        }
        return bestX >= 0 ? new int[]{bestX, bestY} : null;
    }

    private static int[] reachableSpawnCells(NavigationGrid grid,
                                              PointOfInterest shelter,
                                              int sx, int sy,
                                              int liftX, int liftY) {
        List<int[]> candidates = new ArrayList<>();
        for (int y = Math.max(0, sy - SHELTER_ZONE_RADIUS);
             y <= Math.min(grid.getHeight() - 1, sy + SHELTER_ZONE_RADIUS);
             y++) {
            for (int x = Math.max(0, sx - SHELTER_ZONE_RADIUS);
                 x <= Math.min(grid.getWidth() - 1,
                         sx + SHELTER_ZONE_RADIUS); x++) {
                if (!grid.isWalkable(x, y)) continue;
                // POI bounds are the wall ring. Keeping every representative
                // strictly inside it makes "residential shelter" a physical
                // placement guarantee rather than a loose radius around one
                // indoor anchor.
                if (x <= shelter.left || x >= shelter.right
                        || y <= shelter.top || y >= shelter.bottom) continue;
                if (Math.abs(x - sx) + Math.abs(y - sy)
                        > SHELTER_ZONE_RADIUS) continue;
                if (insideLiftZone(x, y, liftX, liftY)) continue;
                if (Paths.isEmpty(GridPathfinder.findPath(
                        grid, x, y, liftX, liftY))) continue;
                candidates.add(new int[]{x, y});
            }
        }
        candidates.sort(Comparator
                .comparingInt((int[] c) ->
                        Math.abs(c[0] - sx) + Math.abs(c[1] - sy))
                .thenComparingInt(c -> c[1])
                .thenComparingInt(c -> c[0]));
        int count = CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT;
        if (candidates.size() < count) return null;
        int[] result = new int[count * 2];
        for (int i = 0; i < count; i++) {
            result[i * 2] = candidates.get(i)[0];
            result[i * 2 + 1] = candidates.get(i)[1];
        }
        return result;
    }

    private static boolean insideLiftZone(int x, int y,
                                          int liftX, int liftY) {
        return Math.abs(x - liftX) <= LIFT_ZONE_RADIUS
                && Math.abs(y - liftY) <= LIFT_ZONE_RADIUS;
    }

    private static boolean inOuterBand(NavigationGrid grid, int x, int y) {
        return x < EDGE_BAND || y < EDGE_BAND
                || x >= grid.getWidth() - EDGE_BAND
                || y >= grid.getHeight() - EDGE_BAND;
    }

    private void checkSpawnIndex(int index) {
        if (index < 0 || index >= spawnCount()) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private static int mix32(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return (int) value;
    }
}
