package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Snaps every {@link PointOfInterest}'s interior anchor onto a cell that is
 * actually standable in the finished map.
 *
 * <h2>Why this is a whole-map pass and not a per-filler concern</h2>
 * A filler picks the interior anchor while it is carving, but the cell it picks
 * keeps changing afterwards: its own furnishing pass drops a crate on it, a
 * wall stamper paints over the footprint, a post-finalize tower stage lands an
 * emplacement in the room. Every producer would have to re-check after every
 * later stage, which no producer can do. Running once at the end, against the
 * grid nobody will touch again, is the only place the guarantee actually
 * holds — hence {@code InteriorAnchorFitStage} closing every recipe.
 *
 * <h2>The guarantee</h2>
 * After this pass, a POI's interior anchor is a walkable non-doorway cell
 * inside the footprint whenever the footprint encloses one. A footprint with no
 * open interior — a solid, uncarved block — falls back to the exterior anchor,
 * which is the contract {@link PointOfInterest} already documents for that case.
 *
 * <p>Consumers depend on this being true, not merely intended. Mission layouts
 * filter candidate sites on interior-anchor walkability, so a blocked anchor
 * silently drops a building from the objective pool; worse, anything that
 * resolves a <em>room</em> from the anchor (zone-scoped capture and recovery
 * objectives) reads no zone at all at a blocked cell and can never complete.
 */
public final class InteriorAnchorFit {

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private InteriorAnchorFit() { }

    /** Rewrites {@code pois} in place, replacing any POI whose interior anchor no longer stands in its room. */
    public static void apply(List<PointOfInterest> pois, NavigationGrid grid) {
        for (int i = 0; i < pois.size(); i++) {
            PointOfInterest poi = pois.get(i);
            if (poi == null) continue;
            int[] fitted = fit(poi, grid);
            if (fitted[0] == poi.interiorAnchorX && fitted[1] == poi.interiorAnchorY) continue;
            pois.set(i, new PointOfInterest(poi.kind, poi.left, poi.top, poi.right, poi.bottom,
                    poi.anchorCellX, poi.anchorCellY, fitted[0], fitted[1]));
        }
    }

    /**
     * Interior anchor this POI should carry given the finished grid: its own
     * anchor when that still stands, otherwise the nearest standable cell in
     * the footprint, otherwise the exterior anchor.
     *
     * <p>Searched breadth first from the existing anchor rather than re-derived
     * from the footprint centre, so a repaired anchor stays in the chamber its
     * filler chose — room labels and garrison placement were authored against
     * that side of the building.
     */
    private static int[] fit(PointOfInterest poi, NavigationGrid grid) {
        int left = Math.max(0, poi.left);
        int top = Math.max(0, poi.top);
        int right = Math.min(grid.getWidth() - 1, poi.right);
        int bottom = Math.min(grid.getHeight() - 1, poi.bottom);
        if (left > right || top > bottom) {
            return new int[]{poi.anchorCellX, poi.anchorCellY};
        }
        int width = right - left + 1;
        boolean[] visited = new boolean[width * (bottom - top + 1)];
        Deque<int[]> queue = new ArrayDeque<>();
        int startX = Math.min(right, Math.max(left, poi.interiorAnchorX));
        int startY = Math.min(bottom, Math.max(top, poi.interiorAnchorY));
        queue.add(new int[]{startX, startY});
        visited[(startY - top) * width + (startX - left)] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (standable(grid, cell[0], cell[1])) return cell;
            for (int[] step : NEIGHBOURS) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < left || nx > right || ny < top || ny > bottom) continue;
                int index = (ny - top) * width + (nx - left);
                if (visited[index]) continue;
                visited[index] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return new int[]{poi.anchorCellX, poi.anchorCellY};
    }

    /**
     * Doorways are excluded alongside walls: a doorway cell is walkable but
     * belongs to no zone, so an objective anchored on one is exactly as
     * uncapturable as one anchored on a wall.
     */
    private static boolean standable(NavigationGrid grid, int x, int y) {
        return grid.isWalkable(x, y) && !grid.isDoorway(x, y);
    }
}
