package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import java.util.Comparator;
import java.util.List;

/** Records belong to dispatch; the installation footprint is the strike target. */
public record ColdAccountLayout(int evidenceX, int evidenceY, int withdrawalX, int withdrawalY,
                                float strikeX, float strikeY, float strikeRadius) {
    public static final float WITHDRAWAL_RADIUS = 12f;
    public static final float BLAST_MARGIN = 6f;
    /** A capture floor with nothing on it: all twenty-five cells of its five-by-five. */
    static final int CLEAR_FLOOR = 25;
    /** A furnished office's capture floor: a clear core and most of the rest. */
    static final int FURNISHED_FLOOR = 20;
    public static ColdAccountLayout select(MapResult map, List<LandingPad> pads) {
        LandingPad exit = pads.get(0);
        var places = map.pointsOfInterest.stream()
                .filter(p -> p.kind != PointOfInterest.Kind.RESIDENTIAL && p.kind != PointOfInterest.Kind.LANDING_SITE)
                .filter(p -> map.grid.isWalkable(p.interiorAnchorX, p.interiorAnchorY))
                .filter(p -> GridPathfinder.findPath(map.grid, exit.centerX, exit.centerY,
                        p.interiorAnchorX, p.interiorAnchorY).length > 0).toList();
        // Records go to the nearest dispatch office with room to stand a squad in. The nearest
        // office alone can be a cramped one, which left a seed with no capture floor at all.
        List<PointOfInterest> offices = places.stream()
                .filter(p -> p.kind == PointOfInterest.Kind.ADMINISTRATIVE)
                .sorted(Comparator.comparingDouble(p -> distance(p, exit)))
                .toList();
        if (offices.isEmpty()) throw new IllegalStateException("Cold Account needs an accessible dispatch office");
        // A wholly clear floor in any office first; then a furnished one with a clear core.
        int[] evidence = null;
        for (int open : new int[] {CLEAR_FLOOR, FURNISHED_FLOOR}) {
            for (PointOfInterest office : offices) {
                evidence = captureFloor(map.grid, map.topology, office, open);
                if (evidence != null) break;
            }
            if (evidence != null) break;
        }
        if (evidence == null) throw new IllegalStateException("Dispatch needs a reachable five-cell-wide capture floor");
        int left = places.stream().mapToInt(p -> p.left).min().orElseThrow();
        int right = places.stream().mapToInt(p -> p.right).max().orElseThrow();
        int top = places.stream().mapToInt(p -> p.top).min().orElseThrow();
        int bottom = places.stream().mapToInt(p -> p.bottom).max().orElseThrow();
        float radius = (float) Math.hypot(right - left + 1, bottom - top + 1) / 2f + 1f;
        float x = (left + right + 1) / 2f;
        float y = (top + bottom + 1) / 2f;
        if (Math.hypot(x - exit.centerX - .5f, y - exit.centerY - .5f)
                <= radius + BLAST_MARGIN + WITHDRAWAL_RADIUS)
            throw new IllegalStateException("Cold Account withdrawal must lie outside the bombardment exclusion area");
        return new ColdAccountLayout(evidence[0], evidence[1],
                exit.centerX, exit.centerY, x, y, radius);
    }

    /** The interaction needs formation space, not merely the first reachable indoor tile. */
    static int[] evidenceCell(NavigationGrid grid, CellTopology topology, PointOfInterest room) {
        int[] best = captureFloor(grid, topology, room, CLEAR_FLOOR);
        if (best == null) best = captureFloor(grid, topology, room, FURNISHED_FLOOR);
        if (best == null) throw new IllegalStateException("Dispatch needs a reachable five-cell-wide capture floor");
        return best;
    }

    /**
     * The capture cell nearest the room's centre whose five-by-five floor has at least
     * {@code open} cells a squad can stand on, its middle three-by-three always clear, all of it
     * reachable indoor floor of the room's own building; or null. A furnished office (desks
     * dotted through it) rarely leaves a whole five-by-five clear, but its aisles still hold a
     * squad.
     */
    private static int[] captureFloor(NavigationGrid grid, CellTopology topology, PointOfInterest room, int open) {
        int[] best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int building = topology.getBuildingId(room.interiorAnchorX, room.interiorAnchorY);
        if (building == 0) return null;
        for (int y = room.top + 3; y <= room.bottom - 3; y++)
            for (int x = room.left + 3; x <= room.right - 3; x++) {
                int standing = 0;
                boolean coreClear = true;
                for (int dy = -2; dy <= 2 && coreClear; dy++) for (int dx = -2; dx <= 2; dx++) {
                    boolean floor = grid.isWalkable(x + dx, y + dy) && !grid.isDoorway(x + dx, y + dy)
                            && topology.getBuildingId(x + dx, y + dy) == building;
                    if (floor) standing++;
                    else if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                        coreClear = false;
                        break;
                    }
                }
                double distance = Math.hypot(x - (room.left + room.right) / 2.0,
                        y - (room.top + room.bottom) / 2.0);
                if (!coreClear || standing < open || distance >= bestDistance) continue;
                if (GridPathfinder.findPath(grid, room.interiorAnchorX, room.interiorAnchorY, x, y).length == 0)
                    continue;
                best = new int[]{x, y};
                bestDistance = distance;
            }
        return best;
    }

    private static double distance(PointOfInterest p, LandingPad exit) {
        return Math.hypot(p.interiorAnchorX - exit.centerX, p.interiorAnchorY - exit.centerY);
    }
}
