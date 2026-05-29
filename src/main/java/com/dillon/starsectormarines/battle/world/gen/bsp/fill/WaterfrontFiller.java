package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * {@link BlockKind#WATERFRONT} filler — paints a non-walkable water band along
 * the leaf's map-edge side, a 1-cell sand shore strip between the water and
 * the dry interior, and a sand-filled remainder reading as a continuous beach.
 *
 * <p>Layout (assuming the chosen edge is the bottom of the leaf):
 * <pre>
 *   +-------------------+
 *   | sand sand sand    |  &lt;- dry interior (rest of leaf)
 *   | sand sand sand    |
 *   | sand sand sand    |  &lt;- shore strip (1 cell, walkable SAND)
 *   | WATER WATER WATER |  &lt;- water band (2-3 cells, non-walkable WATER)
 *   +-------------------+
 * </pre>
 *
 * <p>Critical invariant — water cells get {@code GroundKind.WATER} set AND are
 * flagged non-walkable on the nav grid. The orchestrator's
 * {@link CellTopology#tagDefaultWalls} skips water cells via {@link
 * CellTopology#isWater}, so they don't end up wearing a {@code WALL} tag.
 */
public final class WaterfrontFiller implements BlockFiller {

    /** Min depth of the water band in cells. */
    private static final int WATER_DEPTH_MIN = 2;
    /** Max depth of the water band in cells. */
    private static final int WATER_DEPTH_MAX = 3;
    /** Width of the sand shore strip between water and the dry interior. */
    private static final int SHORE_DEPTH = 1;

    /** Edge identifiers — which side of the leaf the water lies along. */
    private enum Edge { TOP, BOTTOM, LEFT, RIGHT }

    @Override
    public BlockKind kind() { return BlockKind.WATERFRONT; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        List<PointOfInterest> pois = ctx.pois;
        List<Doodad> doodads = ctx.doodads;
        Random rng = ctx.rng;

        // 1. Default the entire leaf to sand. Both water and shore overwrite
        //    selectively below; anything left over reads as continuous beach.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                topology.setGroundKind(x, y, GroundKind.SAND);
                grid.setWalkableFloor(x, y);
            }
        }

        // 2. Pick which side of the leaf hugs the water.
        Edge edge = pickEdge(leaf, rng);

        // 3. Size the water band, clamped so the dry interior keeps at least
        //    one walkable row/column between water and the road frame.
        int axisExtent = (edge == Edge.TOP || edge == Edge.BOTTOM) ? leaf.height() : leaf.width();
        int maxWater = Math.max(WATER_DEPTH_MIN,
                Math.min(WATER_DEPTH_MAX, axisExtent - SHORE_DEPTH - 1));
        int waterDepth = WATER_DEPTH_MIN
                + rng.nextInt(Math.max(1, maxWater - WATER_DEPTH_MIN + 1));
        if (waterDepth > axisExtent - SHORE_DEPTH - 1) {
            waterDepth = Math.max(1, axisExtent - SHORE_DEPTH - 1);
        }

        // 4. Carve the water band — flip cells to non-walkable WATER on both
        //    the nav grid and the topology.
        paintWater(leaf, edge, waterDepth, grid, topology);

        // 5. Drop 1-2 crate doodads on the shore strip for flavor.
        scatterShoreDoodads(leaf, edge, waterDepth, grid, topology, doodads, rng);
    }

    /**
     * Pick the side of the leaf that hugs the water. Prefers an edge that
     * touches the map edge — that's the entire point of a {@code WATERFRONT}
     * leaf. Falls back to the longest edge for the degenerate case where the
     * labeler picked the kind on an interior leaf.
     */
    private Edge pickEdge(BlockLeaf leaf, Random rng) {
        boolean topEdge    = leaf.top    <= 1;
        boolean leftEdge   = leaf.left   <= 1;
        // The orchestrator reserves only the 1-cell map perimeter; a leaf
        // sitting flush against the bottom/right interior boundary will have
        // its bottom/right at mapDim-2. The leaf doesn't know mapDim, so we
        // rely on touchesMapEdge to confirm and use a directional probe to
        // distinguish bottom/right from top/left.
        boolean bottomEdge = leaf.touchesMapEdge && !topEdge  && !leftEdge && leaf.height() >= leaf.width();
        boolean rightEdge  = leaf.touchesMapEdge && !topEdge  && !leftEdge && !bottomEdge;

        // Collect candidates that genuinely sit on the map edge.
        Edge[] candidates = new Edge[4];
        int n = 0;
        if (topEdge)    candidates[n++] = Edge.TOP;
        if (leftEdge)   candidates[n++] = Edge.LEFT;
        if (bottomEdge) candidates[n++] = Edge.BOTTOM;
        if (rightEdge)  candidates[n++] = Edge.RIGHT;

        if (n > 0) return candidates[rng.nextInt(n)];

        // Degenerate fallback — interior leaf got labeled WATERFRONT. Pick
        // the longest edge so the water strip has maximum reach.
        if (leaf.width() >= leaf.height()) {
            return rng.nextBoolean() ? Edge.TOP : Edge.BOTTOM;
        }
        return rng.nextBoolean() ? Edge.LEFT : Edge.RIGHT;
    }

    /**
     * Paint the water band along {@code edge}, {@code depth} cells deep. Each
     * water cell is flagged non-walkable on the nav grid AND tagged with
     * {@code GroundKind.WATER} so {@link CellTopology#tagDefaultWalls} skips it.
     */
    private void paintWater(BlockLeaf leaf, Edge edge, int depth,
                            NavigationGrid grid, CellTopology topology) {
        switch (edge) {
            case TOP: {
                int yEnd = Math.min(leaf.bottom, leaf.top + depth - 1);
                for (int y = leaf.top; y <= yEnd; y++) {
                    for (int x = leaf.left; x <= leaf.right; x++) {
                        markWater(x, y, grid, topology);
                    }
                }
                break;
            }
            case BOTTOM: {
                int yStart = Math.max(leaf.top, leaf.bottom - depth + 1);
                for (int y = yStart; y <= leaf.bottom; y++) {
                    for (int x = leaf.left; x <= leaf.right; x++) {
                        markWater(x, y, grid, topology);
                    }
                }
                break;
            }
            case LEFT: {
                int xEnd = Math.min(leaf.right, leaf.left + depth - 1);
                for (int x = leaf.left; x <= xEnd; x++) {
                    for (int y = leaf.top; y <= leaf.bottom; y++) {
                        markWater(x, y, grid, topology);
                    }
                }
                break;
            }
            case RIGHT: {
                int xStart = Math.max(leaf.left, leaf.right - depth + 1);
                for (int x = xStart; x <= leaf.right; x++) {
                    for (int y = leaf.top; y <= leaf.bottom; y++) {
                        markWater(x, y, grid, topology);
                    }
                }
                break;
            }
        }
    }

    /**
     * Stamp one water cell: WATER ground + non-walkable nav + SEE_THROUGH so
     * the LoS raycast lets shots fly across the pond. Units can't wade in,
     * but a marine on the shore can shoot a defender on the opposite bank.
     */
    private void markWater(int x, int y, NavigationGrid grid, CellTopology topology) {
        topology.setGroundKind(x, y, GroundKind.WATER);
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
    }

    /**
     * Drop 1-2 crate doodads on the sand strip immediately inland from the
     * water. Visual flavor only; doesn't affect nav.
     */
    private void scatterShoreDoodads(BlockLeaf leaf, Edge edge, int waterDepth,
                                     NavigationGrid grid, CellTopology topology,
                                     List<Doodad> doodads, Random rng) {
        if (TileManifest.WAREHOUSE_DOODADS.length == 0) return;
        int crates = 1 + rng.nextInt(2);
        for (int i = 0; i < crates; i++) {
            int[] xy = shoreCell(leaf, edge, waterDepth, rng);
            if (xy == null) return;
            int x = xy[0], y = xy[1];
            if (!grid.isWalkable(x, y) || topology.isWater(x, y)) continue;
            if (topology.getGroundKind(x, y) != GroundKind.SAND) continue;
            TileManifest.TileFrame tile =
                    TileManifest.WAREHOUSE_DOODADS[rng.nextInt(TileManifest.WAREHOUSE_DOODADS.length)];
            doodads.add(new Doodad(x, y, tile));
        }
    }

    /** Random cell on the 1-cell shore strip just inland from the water. */
    private int[] shoreCell(BlockLeaf leaf, Edge edge, int waterDepth, Random rng) {
        switch (edge) {
            case TOP: {
                int y = leaf.top + waterDepth;
                if (y > leaf.bottom) return null;
                int span = Math.max(1, leaf.right - leaf.left + 1);
                return new int[]{ leaf.left + rng.nextInt(span), y };
            }
            case BOTTOM: {
                int y = leaf.bottom - waterDepth;
                if (y < leaf.top) return null;
                int span = Math.max(1, leaf.right - leaf.left + 1);
                return new int[]{ leaf.left + rng.nextInt(span), y };
            }
            case LEFT: {
                int x = leaf.left + waterDepth;
                if (x > leaf.right) return null;
                int span = Math.max(1, leaf.bottom - leaf.top + 1);
                return new int[]{ x, leaf.top + rng.nextInt(span) };
            }
            case RIGHT:
            default: {
                int x = leaf.right - waterDepth;
                if (x < leaf.left) return null;
                int span = Math.max(1, leaf.bottom - leaf.top + 1);
                return new int[]{ x, leaf.top + rng.nextInt(span) };
            }
        }
    }
}
