package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
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
        /** Building-kind hint stamped across every cell of the carved footprint; the flood-fill pass votes the dominant value per connected room to flavor the resulting {@link com.dillon.starsectormarines.battle.world.model.Building}. */
        final BuildingKind buildingKind;
        /**
         * {@link RoomPurpose} labels indexed by Manhattan-distance from the
         * chamber containing the interior anchor (the side a COMMAND_POST
         * tactical node would land on). Index 0 = anchor's chamber itself
         * (THRONE for keep); index 1 = chambers one partition away (INNER for
         * a three-chamber keep); index 2 = chambers two partitions away
         * (ENTRY for a three-chamber keep). Cells in chambers beyond the
         * array's length get no label — caller can intentionally label only
         * the inner ring by passing a short array.
         *
         * <p>{@code null} (or empty) means "don't label any room" — non-keep
         * buildings leave their interior unlabeled so post-fill stampers can
         * ignore them. Individual entries may be {@code null} to skip
         * labeling at that distance (e.g., binary partition that wants to
         * tag only the anchor side — {@code [KEEP_THRONE, null]}).
         *
         * <p>Distance-indexed (not chamber-index-indexed) so the same purpose
         * array works regardless of which physical chamber the anchor lands
         * in — partition orientation + split position vary per carve. For
         * the binary keep case ({@code [KEEP_THRONE, KEEP_ENTRY]}) the
         * anchor-side chamber gets THRONE and the antechamber gets ENTRY
         * whether the partition lands above, below, left, or right.
         */
        final RoomPurpose[] chamberPurposesByAnchorDistance;
        final PartitionStrategy partitionStrategy;

        BuildingConfig(GroundKind interiorGround,
                       TileManifest.TileFrame[] doodadPool,
                       PointOfInterest.Kind poiKind,
                       BuildingLayouts.LayoutRecipe layoutRecipe,
                       BuildingKind buildingKind) {
            this(interiorGround, doodadPool, poiKind, layoutRecipe, buildingKind, null);
        }

        BuildingConfig(GroundKind interiorGround,
                       TileManifest.TileFrame[] doodadPool,
                       PointOfInterest.Kind poiKind,
                       BuildingLayouts.LayoutRecipe layoutRecipe,
                       BuildingKind buildingKind,
                       RoomPurpose[] chamberPurposesByAnchorDistance) {
            this(interiorGround, doodadPool, poiKind, layoutRecipe, buildingKind,
                    chamberPurposesByAnchorDistance, BinaryPartitionStrategy.DEFAULT);
        }

        BuildingConfig(GroundKind interiorGround,
                       TileManifest.TileFrame[] doodadPool,
                       PointOfInterest.Kind poiKind,
                       BuildingLayouts.LayoutRecipe layoutRecipe,
                       BuildingKind buildingKind,
                       RoomPurpose[] chamberPurposesByAnchorDistance,
                       PartitionStrategy partitionStrategy) {
            this.interiorGround = interiorGround;
            this.doodadPool = doodadPool;
            this.poiKind = poiKind;
            this.layoutRecipe = layoutRecipe;
            this.buildingKind = buildingKind;
            this.chamberPurposesByAnchorDistance = chamberPurposesByAnchorDistance;
            this.partitionStrategy = partitionStrategy;
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
                    topology.setBuildingKindHint(x, y, config.buildingKind);
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
        // The building-kind hint goes onto the same cells so the flood-fill's
        // vote sees a consistent stamp across the room.
        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                topology.setGroundKind(x, y, config.interiorGround);
                topology.setBuildingKindHint(x, y, config.buildingKind);
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
        PartitionLayout layout = config.partitionStrategy.partition(
                grid, topology, bl, bt, br, bb, rng, config.interiorGround);
        punchPerimeterDoorways(grid, topology, bl, bt, br, bb, layout, rng, config.interiorGround);

        // Doodad layout — TINY buildings get sparse scatter (shed), LARGE
        // buildings apply the per-type recipe (warehouse / shop / home, etc.).
        BuildingLayouts.applyLayout(grid, bl, bt, br, bb,
                config.doodadPool, config.layoutRecipe, doodads, rng);

        int cx = (bl + br) / 2;
        int cy = (bt + bb) / 2;
        int[] anchor = findNearestWalkableFromBuilding(grid, cx, cy, bl, bt, br, bb);
        int[] interior = findInteriorAnchor(grid, cx, cy, bl, bt, br, bb);

        // Label rooms based on the partition wall + interior anchor. Non-keep
        // configs leave both purposes null and skip labeling entirely; the keep
        // COMMAND config stamps THRONE on the anchor side and ENTRY on the
        // other side so post-fill stampers can identify chambers by direct
        // lookup instead of zone-graph inference.
        labelRooms(grid, topology, bl, bt, br, bb, layout, interior, config);

        return new PointOfInterest(config.poiKind, bl, bt, br, bb,
                anchor[0], anchor[1], interior[0], interior[1]);
    }

    /**
     * Stamps {@link RoomPurpose} labels onto walkable, non-doorway interior
     * cells. Skips entirely when the config supplies no purpose array (the
     * default for non-keep callers). For each interior cell, computes the
     * chamber it lives in (single chamber when un-partitioned; one of two
     * sides of the axis when partitioned), then takes the
     * Manhattan-distance from the interior-anchor's chamber and reads
     * {@code chamberPurposesByAnchorDistance[distance]}. The partition wall
     * itself (non-walkable) and the partition doorway (walkable but
     * "between" rooms) are left unlabeled — they don't belong to either
     * chamber.
     *
     * <p>Distance-indexed addressing generalizes cleanly to ternary
     * partitions (Slice B): three chambers, anchor in one of them,
     * distances {0, 1, 2} map to {THRONE, INNER, ENTRY}. The labeling
     * function doesn't need to know how many partition walls exist — only
     * "which chamber is this cell in" via {@link PartitionLayout#chamberIndex}
     * and "where is the anchor's chamber" via the same call on the anchor coords.
     */
    private static void labelRooms(NavigationGrid grid, CellTopology topology,
                                   int bl, int bt, int br, int bb,
                                   PartitionLayout layout, int[] interior, BuildingConfig config) {
        RoomPurpose[] purposes = config.chamberPurposesByAnchorDistance;
        if (purposes == null || purposes.length == 0) return;

        int anchorChamber = layout.chamberIndex(interior[0], interior[1]);
        // Anchor on a partition axis is degenerate (BFS-from-center reached a
        // doorway or wall cell only); fall back to chamber 0 so labeling
        // still happens. Cells on the other side then take distance 1.
        if (anchorChamber < 0) anchorChamber = 0;

        for (int y = bt + 1; y <= bb - 1; y++) {
            for (int x = bl + 1; x <= br - 1; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (grid.isDoorway(x, y)) continue;
                int chamber = layout.chamberIndex(x, y);
                if (chamber < 0) continue;
                int distance = Math.abs(chamber - anchorChamber);
                if (distance >= purposes.length) continue;
                RoomPurpose p = purposes[distance];
                if (p != null) topology.setRoomPurpose(x, y, p);
            }
        }
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
                                               PartitionLayout layout, Random rng, GroundKind interiorGround) {
        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean twoDoors = w >= SECOND_DOORWAY_MIN_DIM
                && h >= SECOND_DOORWAY_MIN_DIM
                && rng.nextFloat() < SECOND_DOORWAY_CHANCE;

        if (!twoDoors) {
            punchDoorwayOnSide(grid, topology, bl, bt, br, bb, rng.nextInt(4), layout, rng, interiorGround);
            return;
        }

        int firstSide;
        switch (layout.orient) {
            case VERTICAL:   firstSide = rng.nextBoolean() ? 2 : 3; break;
            case HORIZONTAL: firstSide = rng.nextBoolean() ? 0 : 1; break;
            default:         firstSide = rng.nextInt(4);            break;
        }
        punchDoorwayOnSide(grid, topology, bl, bt, br, bb, firstSide,     layout, rng, interiorGround);
        punchDoorwayOnSide(grid, topology, bl, bt, br, bb, firstSide ^ 1, layout, rng, interiorGround);
    }

    /** Stamps a single perimeter doorway on the specified side. */
    private static void punchDoorwayOnSide(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           int side, PartitionLayout layout, Random rng,
                                           GroundKind interiorGround) {
        int[] excluded = (side <= 1)
                ? (layout.orient == PartitionLayout.Orient.VERTICAL   ? layout.axes : NO_EXCLUDES)
                : (layout.orient == PartitionLayout.Orient.HORIZONTAL ? layout.axes : NO_EXCLUDES);

        int doorX, doorY;
        switch (side) {
            case 0:  // top
                doorX = pickAlongRange(bl + 1, br - 1, excluded, rng);
                doorY = bt;
                break;
            case 1:  // bottom
                doorX = pickAlongRange(bl + 1, br - 1, excluded, rng);
                doorY = bb;
                break;
            case 2:  // left
                doorX = bl;
                doorY = pickAlongRange(bt + 1, bb - 1, excluded, rng);
                break;
            default: // right
                doorX = br;
                doorY = pickAlongRange(bt + 1, bb - 1, excluded, rng);
                break;
        }
        grid.setWalkable(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        topology.setGroundKind(doorX, doorY, interiorGround);
    }

    private static final int[] NO_EXCLUDES = new int[0];

    /** Delegates to {@link WallMasks#stampPerimeter} — shared with the preview-test render path. */
    private static void stampPerimeterMask(CellTopology topology,
                                           int bl, int bt, int br, int bb) {
        WallMasks.stampPerimeter(topology, bl, bt, br, bb);
    }

    /** Uniformly-random int in {@code [min, max]} skipping any values in {@code excludes}. */
    private static int pickAlongRange(int min, int max, int[] excludes, Random rng) {
        int excludeCount = 0;
        for (int e : excludes) {
            if (e >= min && e <= max) excludeCount++;
        }
        int size = max - min + 1 - excludeCount;
        if (size <= 0) return min;
        int pick = min + rng.nextInt(size);
        for (int e : excludes) {
            if (e >= min && e <= max && pick >= e) pick++;
        }
        return pick;
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
