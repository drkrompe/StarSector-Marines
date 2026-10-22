package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Writes an elevation plan into a map at generation time. Never call it on a
 * running battle: the elevation map is fixed for the battle.
 *
 * <p>Movement is expressed in the grid's own vocabulary and nothing else: face
 * cells become unwalkable natural solid, and every shared edge between walkable
 * cells on different tiers outside a ramp is closed. Every route authority —
 * infantry A*, the swarm's flow fields, vehicle clearance, the mech planner,
 * manual motion — therefore refuses a cliff without a rule of its own.
 */
public final class ElevationBrush {

    private ElevationBrush() {}

    public static ElevationMap stamp(ElevationPlan plan, NavigationGrid grid, CellTopology topology) {
        return stamp(plan, grid, topology, null);
    }

    /** Reserved cells are a host's circulation and objective ground; elevation never touches them. */
    public static ElevationMap stamp(ElevationPlan plan, NavigationGrid grid, CellTopology topology,
                                     boolean[] reserved) {
        int w = plan.width(), h = plan.height();
        if (grid.getWidth() != w || grid.getHeight() != h
                || topology.getWidth() != w || topology.getHeight() != h
                || (reserved != null && reserved.length != w * h)) {
            throw new IllegalArgumentException("Elevation and map dimensions differ");
        }
        if (grid.elevation() != null) {
            throw new IllegalArgumentException("Elevation already stamped; rebuild the map to edit it");
        }
        ElevationLayout layout = ElevationLayout.resolve(plan);
        // Preflight everything before writing anything: a refusal leaves the map untouched.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!layout.touched(x, y)) continue;
                boolean natural = switch (topology.getGroundKind(x, y)) {
                    case GRASS, DIRT, STONE, SAND, SNOW -> true;
                    default -> false;
                };
                if (!natural || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.isWall(x, y)
                        || topology.isFixture(x, y) || topology.getBuildingId(x, y) != 0
                        || topology.getSurface(x, y) != 0
                        || (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null)
                        || (reserved != null && reserved[y * w + x])) {
                    throw new IllegalArgumentException("Elevation overlaps protected ground at " + x + "," + y);
                }
            }
        }
        ElevationMap map = layout.toMap(ElevationMap.SightRule.configured());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!layout.isFace(x, y)) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, false);
                grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                grid.setWallHp(x, y, 0);
                topology.setFixture(x, y, true);
                topology.setNatureOverlayIndex(x, y, -1);
            }
        }
        // Close every cliff edge left between walkable cells (a face band's
        // corners can leave two tiers touching).
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                int i = y * w + x;
                if (x + 1 < w && grid.isWalkable(x + 1, y) && map.separates(i, i + 1)) {
                    grid.blockSharedEdge(x, y, Direction.E);
                }
                if (y + 1 < h && grid.isWalkable(x, y + 1) && map.separates(i, i + w)) {
                    grid.blockSharedEdge(x, y, Direction.N);
                }
            }
        }
        // Rim and foot cells take wall-like cover from the face beside them.
        int[] dx = {0, 1, 0, -1}, dy = {-1, 0, 1, 0};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                for (int facing = 0; facing < 4; facing++) {
                    int nx = x + dx[facing], ny = y + dy[facing];
                    if (layout.isFaceInBounds(nx, ny) && grid.getCoverAtFacing(x, y, facing) < 1) {
                        grid.setCoverAtFacing(x, y, facing, 1);
                    }
                }
            }
        }
        grid.setElevation(map);
        return map;
    }
}
