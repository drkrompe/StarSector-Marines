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
    public static final int PICKUP_FORMATION_POINTS = 5;
    /** Thirteen-cell radius gives the five-point line an approximately 25x25 footprint. */
    public static final int PICKUP_FORMATION_RADIUS = 13;
    /** Keeps the full production-sized formation inside the map with a small rear margin. */
    public static final int PICKUP_EDGE_INSET = 15;
    private static final int PICKUP_BAND_WIDTH = 2;
    private static final int FORMATION_SEARCH_RADIUS = 3;

    public final int shelterX;
    public final int shelterY;
    /** Exterior doorway-side rally point where marines make contact and open the shelter. */
    public final int shelterApproachX;
    public final int shelterApproachY;
    public final int liftX;
    public final int liftY;
    private final int formationRadius;
    private final int[] spawnCells;
    private final int[] formationCells;

    private CivilianEvacuationPlacement(int shelterX, int shelterY,
                                        int shelterApproachX,
                                        int shelterApproachY,
                                        int liftX, int liftY,
                                        int formationRadius,
                                        int[] spawnCells,
                                        int[] formationCells) {
        this.shelterX = shelterX;
        this.shelterY = shelterY;
        this.shelterApproachX = shelterApproachX;
        this.shelterApproachY = shelterApproachY;
        this.liftX = liftX;
        this.liftY = liftY;
        this.formationRadius = formationRadius;
        this.spawnCells = spawnCells;
        this.formationCells = formationCells;
    }

    /**
     * Finds a complete reachable placement, or {@code null} if the map has no
     * suitable residential shelter, inset pickup formation, or eight spawn cells.
     */
    public static CivilianEvacuationPlacement find(
            NavigationGrid grid, List<PointOfInterest> pointsOfInterest,
            long seed) {
        return find(grid, pointsOfInterest, seed,
                CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT);
    }

    public static CivilianEvacuationPlacement find(
            NavigationGrid grid, List<PointOfInterest> pointsOfInterest,
            long seed, int representativeCount) {
        if (representativeCount <= 0) return null;
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
                    forShelter(grid, shelter, representativeCount);
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

    public int formationPointCount() {
        return formationCells.length / 2;
    }

    public int formationRadius() {
        return formationRadius;
    }

    public int formationX(int index) {
        checkFormationIndex(index);
        return formationCells[index * 2];
    }

    public int formationY(int index) {
        checkFormationIndex(index);
        return formationCells[index * 2 + 1];
    }

    public int[] formationCells() {
        return formationCells.clone();
    }

    private static CivilianEvacuationPlacement forShelter(
            NavigationGrid grid, PointOfInterest shelter,
            int representativeCount) {
        int sx = shelter.interiorAnchorX;
        int sy = shelter.interiorAnchorY;
        LiftSite lift = farthestReachableLift(grid, sx, sy);
        if (lift == null) return null;
        int[] spawns = reachableSpawnCells(
                grid, shelter, sx, sy, lift.x, lift.y,
                representativeCount);
        if (spawns == null) return null;
        return new CivilianEvacuationPlacement(
                sx, sy, shelter.anchorCellX, shelter.anchorCellY,
                lift.x, lift.y, lift.formationRadius,
                spawns, lift.formationCells);
    }

    private static LiftSite farthestReachableLift(NavigationGrid grid,
                                                   int sx, int sy) {
        int bestX = -1;
        int bestY = -1;
        int bestDistance = -1;
        int[] bestFormation = null;
        int formationRadius = formationRadius(grid);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!inPickupBand(grid, x, y) || !grid.isWalkable(x, y)) {
                    continue;
                }
                int distance = Math.abs(x - sx) + Math.abs(y - sy);
                if (distance < bestDistance) continue;
                int[] path = GridPathfinder.findPath(grid, sx, sy, x, y);
                if (Paths.isEmpty(path)) continue;
                int[] formation = formationCells(
                        grid, x, y, formationRadius);
                if (formation == null) continue;
                if (distance > bestDistance
                        || y < bestY || (y == bestY && x < bestX)) {
                    bestX = x;
                    bestY = y;
                    bestDistance = distance;
                    bestFormation = formation;
                }
            }
        }
        return bestX >= 0
                ? new LiftSite(bestX, bestY, formationRadius, bestFormation)
                : null;
    }

    private static int[] reachableSpawnCells(NavigationGrid grid,
                                              PointOfInterest shelter,
                                              int sx, int sy,
                                              int liftX, int liftY,
                                              int representativeCount) {
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
        int count = representativeCount;
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

    private static boolean inPickupBand(NavigationGrid grid, int x, int y) {
        int edgeDistance = Math.min(Math.min(x, grid.getWidth() - 1 - x),
                Math.min(y, grid.getHeight() - 1 - y));
        int maxInset = Math.max(1,
                (Math.min(grid.getWidth(), grid.getHeight()) - 1) / 2);
        int inset = Math.min(PICKUP_EDGE_INSET, maxInset);
        return edgeDistance >= inset
                && edgeDistance <= Math.min(maxInset, inset + PICKUP_BAND_WIDTH);
    }

    private static int[] formationCells(NavigationGrid grid,
                                         int liftX, int liftY,
                                         int formationRadius) {
        int[] result = new int[PICKUP_FORMATION_POINTS * 2];
        for (int point = 0; point < PICKUP_FORMATION_POINTS; point++) {
            double angle = -Math.PI / 2.0
                    + point * Math.PI * 2.0 / PICKUP_FORMATION_POINTS;
            int idealX = (int) Math.round(liftX
                    + Math.cos(angle) * formationRadius);
            int idealY = (int) Math.round(liftY
                    + Math.sin(angle) * formationRadius);
            int bestX = -1;
            int bestY = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int y = idealY - FORMATION_SEARCH_RADIUS;
                 y <= idealY + FORMATION_SEARCH_RADIUS; y++) {
                for (int x = idealX - FORMATION_SEARCH_RADIUS;
                     x <= idealX + FORMATION_SEARCH_RADIUS; x++) {
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)
                            || alreadySelected(result, point, x, y)) continue;
                    int fromCenter = Math.max(Math.abs(x - liftX),
                            Math.abs(y - liftY));
                    if (fromCenter < formationRadius - 2) continue;
                    if (Paths.isEmpty(GridPathfinder.findPath(
                            grid, x, y, liftX, liftY))) continue;
                    int dx = x - idealX;
                    int dy = y - idealY;
                    int distance = dx * dx + dy * dy;
                    if (distance < bestDistance
                            || (distance == bestDistance
                            && (y < bestY || (y == bestY && x < bestX)))) {
                        bestX = x;
                        bestY = y;
                        bestDistance = distance;
                    }
                }
            }
            if (bestX < 0) return null;
            result[point * 2] = bestX;
            result[point * 2 + 1] = bestY;
        }
        return result;
    }

    private static int formationRadius(NavigationGrid grid) {
        int available = (Math.min(grid.getWidth(), grid.getHeight()) - 3) / 2;
        return Math.max(3, Math.min(PICKUP_FORMATION_RADIUS, available));
    }

    private static boolean alreadySelected(int[] cells, int count,
                                            int x, int y) {
        for (int i = 0; i < count; i++) {
            if (cells[i * 2] == x && cells[i * 2 + 1] == y) return true;
        }
        return false;
    }

    private void checkSpawnIndex(int index) {
        if (index < 0 || index >= spawnCount()) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private void checkFormationIndex(int index) {
        if (index < 0 || index >= formationPointCount()) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private record LiftSite(int x, int y, int formationRadius,
                            int[] formationCells) {}

    private static int mix32(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return (int) value;
    }
}
