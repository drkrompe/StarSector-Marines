package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.map.WallMasks;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Shared carving helper for the three building BSP fillers
 * ({@link BuildingResidentialFiller}, {@link BuildingCommercialFiller},
 * {@link BuildingIndustrialFiller}). Encapsulates the perimeter wall + interior
 * partition + perimeter doorway logic lifted from the legacy
 * {@code UrbanMapGenerator.placeBuilding} pipeline so the fillers only differ
 * in ground kind, doodad pool, and POI tag.
 *
 * <p>Constraints honored:
 * <ul>
 *   <li>Operates entirely inside the leaf's inclusive rect — never touches the
 *       surrounding road frame.</li>
 *   <li>Below {@link #HOLLOW_MIN_SIZE} on either axis the leaf is stamped as a
 *       solid block (matching legacy fallback) and returns {@code null}.</li>
 *   <li>Closes the perimeter wall, punches one or two doorways with walkable
 *       cells on both sides, and scatters doodads on walkable, non-doorway
 *       interior cells only.</li>
 * </ul>
 *
 * <p>Package-private — only the three sibling fillers consume this. Stateless.
 */
final class BuildingShellCore {

    /** Building footprints with both dimensions {@code >=} this are carved hollow. Smaller fall back to solid. Mirrors legacy. */
    static final int HOLLOW_MIN_SIZE = 4;

    /**
     * Minimum building dimension along the split axis to qualify for an
     * interior partition wall. Mirrors legacy.
     */
    private static final int MULTI_ROOM_MIN_DIM = 7;
    /** Chance a sufficiently-large hollow building gets subdivided into multiple rooms. */
    private static final float MULTI_ROOM_CHANCE = 0.65f;
    /** Minimum dim (both axes) for a hollow building to qualify for a second perimeter doorway. */
    private static final int SECOND_DOORWAY_MIN_DIM = 5;
    /** Chance a qualifying hollow building gets a second perimeter doorway on the opposite side. */
    private static final float SECOND_DOORWAY_CHANCE = 0.7f;

    private BuildingShellCore() {}

    /** Configuration knob — supplied by each filler to flavor the carved shell. */
    static final class BuildingConfig {
        /** Ground kind painted across the interior floor. */
        final GroundKind interiorGround;
        /** Pool of doodad tiles used by the SHED/tiny fallback scatter and any recipe that draws from the per-type pool rather than literal frames. */
        final TileManifest.TileFrame[] doodadPool;
        /** POI kind reported for the carved building (or {@code null} if too small). */
        final PointOfInterest.Kind poiKind;
        /** Layout strategy applied to LARGE buildings. TINY buildings always fall back to {@link BuildingLayouts.LayoutRecipe#SHED}. */
        final BuildingLayouts.LayoutRecipe layoutRecipe;

        BuildingConfig(GroundKind interiorGround,
                       TileManifest.TileFrame[] doodadPool,
                       PointOfInterest.Kind poiKind,
                       BuildingLayouts.LayoutRecipe layoutRecipe) {
            this.interiorGround = interiorGround;
            this.doodadPool = doodadPool;
            this.poiKind = poiKind;
            this.layoutRecipe = layoutRecipe;
        }
    }

    /**
     * Carves the building described by {@code config} inside {@code leaf}.
     * Mutates {@code grid} + {@code topology} and appends any placed doodads
     * to {@code doodads}. Returns the POI the building should be registered as,
     * or {@code null} when the leaf was too small to enclose anything readable
     * (matching legacy plaza-stub behavior on tiny footprints).
     */
    static PointOfInterest carve(BlockLeaf leaf,
                                 NavigationGrid grid,
                                 CellTopology topology,
                                 List<Doodad> doodads,
                                 Random rng,
                                 BuildingConfig config) {
        int bl = leaf.left;
        int bt = leaf.top;
        int br = leaf.right;
        int bb = leaf.bottom;
        int w = br - bl + 1;
        int h = bb - bt + 1;

        // Tiny leaves can't enclose anything — solid block, no POI. The
        // surrounding road frame still reads it as something distinct, but the
        // generator doesn't track it as a building.
        if (w < HOLLOW_MIN_SIZE || h < HOLLOW_MIN_SIZE) {
            for (int y = bt; y <= bb; y++) {
                for (int x = bl; x <= br; x++) {
                    grid.setWalkable(x, y, false);
                    // Leave ground kind as configured interior so the renderer
                    // doesn't see STREET inside what is visually a building.
                    topology.setGroundKind(x, y, config.interiorGround);
                }
            }
            // Solid block — every cell is wall, but only the leaf-perimeter
            // cells face the outside. Interior cells of a solid block get a
            // null mask → render-time solid fill, matching a fully-enclosed wall.
            stampPerimeterMask(topology, bl, bt, br, bb);
            return null;
        }

        // Perimeter — non-walkable. Interior stays walkable from caller's init.
        for (int x = bl; x <= br; x++) {
            grid.setWalkable(x, bt, false);
            grid.setWalkable(x, bb, false);
        }
        for (int y = bt + 1; y <= bb - 1; y++) {
            grid.setWalkable(bl, y, false);
            grid.setWalkable(br, y, false);
        }
        // Wall-direction mask on every perimeter cell — building geometry
        // determines facing directly, no neighbor query at render time. Set
        // before doorway punching so the punch step can clear masks on the
        // cells it demotes from wall.
        stampPerimeterMask(topology, bl, bt, br, bb);
        // Interior floor — paint configured ground kind across every interior cell.
        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                topology.setGroundKind(x, y, config.interiorGround);
            }
        }
        // Perimeter cells get the interior ground too so when a wall is
        // breached the rubble doesn't expose STREET underneath.
        for (int x = bl; x <= br; x++) {
            topology.setGroundKind(x, bt, config.interiorGround);
            topology.setGroundKind(x, bb, config.interiorGround);
        }
        for (int y = bt + 1; y <= bb - 1; y++) {
            topology.setGroundKind(bl, y, config.interiorGround);
            topology.setGroundKind(br, y, config.interiorGround);
        }

        // Subdivide first so the perimeter-doorway picker can align with the
        // partition (vertical wall → left/right doors; horizontal wall →
        // top/bottom doors), giving each room its own exterior access when
        // the building scores a 2nd doorway.
        InteriorWall wall = maybeAddInteriorWall(grid, topology, bl, bt, br, bb, rng, config.interiorGround);
        punchPerimeterDoorways(grid, topology, bl, bt, br, bb, wall, rng, config.interiorGround);

        // Doodad layout — TINY buildings get sparse scatter (shed), LARGE
        // buildings apply the per-type recipe (warehouse / shop / home, etc.).
        BuildingLayouts.applyLayout(grid, bl, bt, br, bb,
                config.doodadPool, config.layoutRecipe, doodads, rng);

        int cx = (bl + br) / 2;
        int cy = (bt + bb) / 2;
        int[] anchor = findNearestWalkableFromBuilding(grid, cx, cy, bl, bt, br, bb);
        int[] interior = findInteriorAnchor(grid, cx, cy, bl, bt, br, bb);
        return new PointOfInterest(config.poiKind, bl, bt, br, bb,
                anchor[0], anchor[1], interior[0], interior[1]);
    }

    /**
     * BFS from {@code (cx, cy)} restricted to the building interior
     * ({@code (bl+1, bt+1)} .. {@code (br-1, bb-1)}), returning the first
     * walkable non-doorway cell. The center is the natural first hit; a
     * partition wall or doorway right at the middle nudges us one step.
     * Falls back to the building center if no interior cell qualifies — a
     * degenerate case (every interior cell is wall or doorway) that
     * shouldn't occur on valid carves, but keeps the return contract safe.
     */
    private static int[] findInteriorAnchor(NavigationGrid grid, int cx, int cy,
                                            int bl, int bt, int br, int bb) {
        boolean[] visited = new boolean[grid.getWidth() * grid.getHeight()];
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy});
        if (grid.inBounds(cx, cy)) visited[cy * grid.getWidth() + cx] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            boolean inInterior = p[0] > bl && p[0] < br && p[1] > bt && p[1] < bb;
            if (inInterior && grid.isWalkable(p[0], p[1]) && !grid.isDoorway(p[0], p[1])) {
                return p;
            }
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                // Don't escape the building bbox — interior anchor must be inside.
                if (nx < bl || nx > br || ny < bt || ny > bb) continue;
                int idx = ny * grid.getWidth() + nx;
                if (visited[idx]) continue;
                visited[idx] = true;
                q.add(new int[]{nx, ny});
            }
        }
        return new int[]{cx, cy};
    }

    /** Picks 1 or 2 perimeter doorways for a hollow building. Lifted verbatim from legacy. */
    private static void punchPerimeterDoorways(NavigationGrid grid, CellTopology topology,
                                               int bl, int bt, int br, int bb,
                                               InteriorWall wall, Random rng, GroundKind interiorGround) {
        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean twoDoors = w >= SECOND_DOORWAY_MIN_DIM
                && h >= SECOND_DOORWAY_MIN_DIM
                && rng.nextFloat() < SECOND_DOORWAY_CHANCE;

        if (!twoDoors) {
            punchDoorwayOnSide(grid, topology, bl, bt, br, bb, rng.nextInt(4), wall, rng, interiorGround);
            return;
        }

        int firstSide;
        switch (wall.orient) {
            case VERTICAL:   firstSide = rng.nextBoolean() ? 2 : 3; break;
            case HORIZONTAL: firstSide = rng.nextBoolean() ? 0 : 1; break;
            default:         firstSide = rng.nextInt(4);            break;
        }
        punchDoorwayOnSide(grid, topology, bl, bt, br, bb, firstSide,     wall, rng, interiorGround);
        punchDoorwayOnSide(grid, topology, bl, bt, br, bb, firstSide ^ 1, wall, rng, interiorGround);
    }

    /** Stamps a single perimeter doorway on the specified side. Lifted verbatim from legacy. */
    private static void punchDoorwayOnSide(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           int side, InteriorWall wall, Random rng,
                                           GroundKind interiorGround) {
        int doorX, doorY;
        switch (side) {
            case 0:  // top
                doorX = pickAlongRangeExcluding(bl + 1, br - 1,
                        wall.orient == InteriorWallOrient.VERTICAL ? wall.axis : Integer.MIN_VALUE, rng);
                doorY = bt;
                break;
            case 1:  // bottom
                doorX = pickAlongRangeExcluding(bl + 1, br - 1,
                        wall.orient == InteriorWallOrient.VERTICAL ? wall.axis : Integer.MIN_VALUE, rng);
                doorY = bb;
                break;
            case 2:  // left
                doorX = bl;
                doorY = pickAlongRangeExcluding(bt + 1, bb - 1,
                        wall.orient == InteriorWallOrient.HORIZONTAL ? wall.axis : Integer.MIN_VALUE, rng);
                break;
            default: // right
                doorX = br;
                doorY = pickAlongRangeExcluding(bt + 1, bb - 1,
                        wall.orient == InteriorWallOrient.HORIZONTAL ? wall.axis : Integer.MIN_VALUE, rng);
                break;
        }
        grid.setWalkable(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        // Doorway always reads as interior floor underneath the overhead door
        // overlay — even for commercial/industrial it should match the room it
        // leads into rather than poke through to STREET.
        topology.setGroundKind(doorX, doorY, interiorGround);
    }

    /** Delegates to {@link WallMasks#stampPerimeter} — shared with the preview-test render path. */
    private static void stampPerimeterMask(CellTopology topology,
                                           int bl, int bt, int br, int bb) {
        WallMasks.stampPerimeter(topology, bl, bt, br, bb);
    }

    /** Uniformly-random int in {@code [min, max]} skipping {@code exclude}. Verbatim from legacy. */
    private static int pickAlongRangeExcluding(int min, int max, int exclude, Random rng) {
        int size = max - min + 1;
        boolean hasExclude = exclude >= min && exclude <= max;
        if (hasExclude) size -= 1;
        if (size <= 0) return min;
        int pick = rng.nextInt(size);
        int v = min + pick;
        if (hasExclude && v >= exclude) v += 1;
        return v;
    }

    /** Three cases the doorway pickers distinguish. */
    private enum InteriorWallOrient { NONE, VERTICAL, HORIZONTAL }

    /** Partition tag returned by {@link #maybeAddInteriorWall}. */
    private static final class InteriorWall {
        static final InteriorWall NONE = new InteriorWall(InteriorWallOrient.NONE, -1);
        final InteriorWallOrient orient;
        final int axis;
        InteriorWall(InteriorWallOrient orient, int axis) {
            this.orient = orient;
            this.axis = axis;
        }
    }

    /** Optional single-wall subdivision. Verbatim from legacy with configurable interior ground. */
    private static InteriorWall maybeAddInteriorWall(NavigationGrid grid, CellTopology topology,
                                                     int bl, int bt, int br, int bb, Random rng,
                                                     GroundKind interiorGround) {
        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean canVert  = w >= MULTI_ROOM_MIN_DIM;
        boolean canHoriz = h >= MULTI_ROOM_MIN_DIM;
        if (!canVert && !canHoriz) return InteriorWall.NONE;
        if (rng.nextFloat() >= MULTI_ROOM_CHANCE) return InteriorWall.NONE;

        boolean vertical;
        if (canVert && canHoriz) {
            if (w > h)      vertical = true;
            else if (h > w) vertical = false;
            else            vertical = rng.nextBoolean();
        } else {
            vertical = canVert;
        }

        if (vertical) {
            int wx = bl + 3 + rng.nextInt(w - 6);
            for (int y = bt + 1; y <= bb - 1; y++) {
                grid.setWalkable(wx, y, false);
            }
            int dy = bt + 1 + rng.nextInt(h - 2);
            openInteriorDoorway(grid, topology, wx, dy, interiorGround);
            return new InteriorWall(InteriorWallOrient.VERTICAL, wx);
        } else {
            int wy = bt + 3 + rng.nextInt(h - 6);
            for (int x = bl + 1; x <= br - 1; x++) {
                grid.setWalkable(x, wy, false);
            }
            int dx = bl + 1 + rng.nextInt(w - 2);
            openInteriorDoorway(grid, topology, dx, wy, interiorGround);
            return new InteriorWall(InteriorWallOrient.HORIZONTAL, wy);
        }
    }

    /** Restores a single partition cell to a walkable doorway with interior floor underneath. */
    private static void openInteriorDoorway(NavigationGrid grid, CellTopology topology,
                                            int x, int y, GroundKind interiorGround) {
        grid.setWalkable(x, y, true);
        grid.setDoorway(x, y, true);
        grid.openAllEdges(x, y);
        topology.setGroundKind(x, y, interiorGround);
    }

    /**
     * BFS outward from (cx, cy), returning the first walkable cell outside the
     * building footprint. Used as the POI's interaction anchor. Verbatim from
     * legacy {@code UrbanMapGenerator.findNearestWalkableFromBuilding}.
     */
    private static int[] findNearestWalkableFromBuilding(NavigationGrid grid, int cx, int cy,
                                                         int bl, int bt, int br, int bb) {
        boolean[] visited = new boolean[grid.getWidth() * grid.getHeight()];
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy});
        if (grid.inBounds(cx, cy)) visited[cy * grid.getWidth() + cx] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            boolean inBuilding = p[0] >= bl && p[0] <= br && p[1] >= bt && p[1] <= bb;
            if (!inBuilding && grid.isWalkable(p[0], p[1])) return p;
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                int idx = ny * grid.getWidth() + nx;
                if (visited[idx]) continue;
                visited[idx] = true;
                q.add(new int[]{nx, ny});
            }
        }
        return new int[]{cx, cy};
    }
}
