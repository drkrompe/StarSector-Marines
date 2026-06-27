package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.FillerParams;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.Random;

/**
 * Shared filler for the {@code NATURE_*} block kinds — grassland, wetland,
 * beach. One class with a per-kind preset rather than three near-identical
 * fillers: the structure is "pick a ground tile per cell from a kind-specific
 * pool, then maybe stamp a {@link TileDef} overlay on top," and only the
 * pools differ.
 *
 * <p>Behavior by kind:
 * <ul>
 *   <li>{@link BlockKind#NATURE_GRASSLAND} — grass-dominant. Sparse dirt
 *       patches; shrubs and grass tufts scatter on grass cells; small rocks
 *       on either. All walkable. Reads as wild meadow.</li>
 *   <li>{@link BlockKind#NATURE_WETLAND} — clustered water pools
 *       (non-walkable but see-through, same convention as {@link
 *       WaterfrontFiller#markWater}), dirt banks around the water, grass
 *       islands further out. Plant overlays land on the grass; impassable
 *       rocks are rare.</li>
 *   <li>{@link BlockKind#NATURE_BEACH} — sand-dominant with occasional
 *       dirt. Rock overlays of mixed sizes scatter across; medium / large
 *       rocks add tactical cover on what would otherwise be an open landing
 *       zone. No plants — sand can't host the plant overlays per
 *       {@link TileDef#canOverlayOn}.</li>
 * </ul>
 *
 * <p>Wetland water cells use the same "non-walkable + see-through + WATER
 * ground" treatment as {@link WaterfrontFiller#markWater}: marines on one
 * bank can fire across a puddle to a defender on the other. This keeps
 * water from acting as a sightline blocker in addition to a movement
 * blocker — important on small wetland leaves where a 2-cell pool would
 * otherwise eat both options.
 *
 * <p>Overlay placement respects {@link TileDef#canOverlayOn}: plants only
 * on grass, rocks on any non-water. Cells that would violate the rule are
 * silently skipped — the per-cell roll falls through to "no overlay"
 * rather than substituting a different one.
 *
 * <p>All overlay cells stay walkable for now — the {@code passable} field on
 * a {@link TileDef} is a designer hint we don't enforce on the nav grid yet.
 * Flipping a cell
 * non-walkable here would force the orchestrator's {@code tagDefaultWalls}
 * pass to tag it as {@code WALL} (the post-fill sweep can't distinguish
 * "boulder" from "building wall"), which would render urban wall art on
 * top of the rock sprite. Proper "natural obstacle" walkability blocking
 * needs a {@code Tag.OBSTACLE} bit on the topology — deferred until the
 * rocks are doing enough work gameplay-wise to justify the extra plumbing.
 */
public final class NatureZoneFiller implements BlockFiller {

    private final BlockKind kind;

    public NatureZoneFiller(BlockKind kind) {
        if (kind != BlockKind.NATURE_GRASSLAND
                && kind != BlockKind.NATURE_WETLAND
                && kind != BlockKind.NATURE_BEACH) {
            throw new IllegalArgumentException("NatureZoneFiller can't handle " + kind);
        }
        this.kind = kind;
    }

    @Override
    public BlockKind kind() { return kind; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        Random rng = ctx.rng;
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        FillerParams params = (mapping == null) ? null : mapping.fillerParams(kind);
        paintBase(leaf, grid, topology, rng, params);
        TileRegistry reg = TileRegistry.installed();
        // Overlays need both the tile defs (TileRegistry) and the pools/chances
        // (FillerParams). Skip them if either is unavailable — base ground is still
        // painted. Both are installed at app load + by the test bootstrap.
        if (reg == null || params == null) return;
        scatterOverlays(leaf, topology, rng, reg, params);
    }

    /**
     * Paint the base ground layer for this leaf. Walkability follows the
     * GroundKind — water cells are flipped to non-walkable + see-through;
     * everything else stays walkable.
     */
    private void paintBase(BlockLeaf leaf, NavigationGrid grid,
                           CellTopology topology, Random rng, FillerParams params) {
        if (kind == BlockKind.NATURE_WETLAND) {
            paintWetlandBase(leaf, grid, topology, rng);
            return;
        }
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                GroundKind g = pickBaseGround(rng, params);
                topology.setGroundKind(x, y, g);
                grid.setWalkableFloor(x, y);
            }
        }
    }

    /**
     * Per-cell pick for grassland / beach (no water cluster logic) from the
     * data-driven weighted ground pool. The {@code params == null} fallback (no
     * registry — degraded) paints the kind's dominant ground uniformly.
     */
    private GroundKind pickBaseGround(Random rng, FillerParams params) {
        GroundKind dominant = (kind == BlockKind.NATURE_GRASSLAND) ? GroundKind.GRASS : GroundKind.SAND;
        return (params == null) ? dominant : params.pickGround(rng, dominant);
    }

    /**
     * Wetland base — places 1-2 water "puddle" centers inside the leaf,
     * carves a radius-1 disc of water around each, surrounds water cells
     * with a 1-cell dirt bank, and leaves the rest as grass. Producing
     * water clusters rather than per-cell salt-and-pepper keeps the leaf
     * tactically readable (a flank channel between two ponds vs. random
     * unwadeable noise).
     *
     * <p>Puddles are seeded 2 cells in from the leaf's edge so they can't
     * land on the perimeter row/col — the perimeter must stay walkable so
     * the leaf connects to the surrounding road frame. Even with the
     * inset, two overlapping puddles can occasionally arch a water ring
     * around a single corner cell; {@link #rescueIsolatedWalkableCells}
     * sweeps after layout to revert the enclosing water back to dirt,
     * preserving the connectivity invariant the orchestrator's preview
     * test enforces.
     */
    private void paintWetlandBase(BlockLeaf leaf, NavigationGrid grid,
                                  CellTopology topology, Random rng) {
        int w = leaf.width();
        int h = leaf.height();
        // Default the leaf to grass; water + dirt overwrite below.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                topology.setGroundKind(x, y, GroundKind.GRASS);
                grid.setWalkableFloor(x, y);
            }
        }
        // Skip wetland water on tiny leaves — no room for a pond + 2-cell
        // perimeter buffer. The leaf still gets grass + plant overlays from
        // the overlay pass, which is enough to read as a damp meadow.
        if (w < 5 || h < 5) return;

        // 1-2 puddle centers, biased to the leaf interior so a pond doesn't
        // butt up against the road frame. Inset of 2 keeps even the radius
        // from spilling onto the perimeter row/col.
        int puddles = 1 + rng.nextInt(Math.min(2, Math.max(1, (w * h) / 24)));
        int innerW = Math.max(1, w - 4);
        int innerH = Math.max(1, h - 4);
        int radius = 1;
        for (int p = 0; p < puddles; p++) {
            int cx = leaf.left + 2 + rng.nextInt(innerW);
            int cy = leaf.top  + 2 + rng.nextInt(innerH);
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int x = cx - radius; x <= cx + radius; x++) {
                    if (!leaf.contains(x, y)) continue;
                    int dx = x - cx;
                    int dy = y - cy;
                    if (dx * dx + dy * dy > radius * radius) continue;
                    markWater(x, y, grid, topology);
                }
            }
        }
        // Connectivity rescue — revert water that's enclosing an otherwise
        // unreachable walkable cell. Cheap (most leaves are already fine);
        // catches the worst-case overlap that breaks the map's flood-fill.
        rescueIsolatedWalkableCells(leaf, grid, topology);

        // Dirt banks — any grass cell adjacent to water becomes dirt. Single
        // sweep over the leaf after water is laid down (and possibly partly
        // reverted by the rescue pass).
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.getGroundKind(x, y) != GroundKind.GRASS) continue;
                if (hasWaterNeighbor(topology, x, y, leaf)) {
                    topology.setGroundKind(x, y, GroundKind.DIRT);
                }
            }
        }
    }

    /**
     * BFS the walkable cells inside {@code leaf} from a perimeter seed; any
     * walkable cell not reached gets its enclosing water neighbors reverted
     * to walkable dirt. Repeats until every walkable cell is reachable from
     * a leaf-perimeter cell, which guarantees connectivity to the
     * surrounding road frame (the orchestrator pre-fills the frame walkable
     * STREET, so any leaf-edge walkable cell touches it).
     *
     * <p>No-op when every walkable cell is already reachable, which is the
     * common case — water layout almost always produces a single connected
     * non-water region inside the leaf.
     */
    private static void rescueIsolatedWalkableCells(BlockLeaf leaf,
                                                    NavigationGrid grid,
                                                    CellTopology topology) {
        int w = leaf.width();
        int h = leaf.height();
        boolean[][] reached = new boolean[w][h];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        // Seed: every walkable cell on the leaf's outermost row/col.
        for (int x = leaf.left; x <= leaf.right; x++) {
            seedIfWalkable(grid, leaf, reached, queue, x, leaf.top);
            seedIfWalkable(grid, leaf, reached, queue, x, leaf.bottom);
        }
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            seedIfWalkable(grid, leaf, reached, queue, leaf.left,  y);
            seedIfWalkable(grid, leaf, reached, queue, leaf.right, y);
        }
        // BFS — flood walkable cells from the perimeter inwards.
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int px = p[0], py = p[1];
            int[][] dirs = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
            for (int[] d : dirs) {
                int nx = px + d[0];
                int ny = py + d[1];
                if (!leaf.contains(nx, ny)) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                int li = nx - leaf.left;
                int lj = ny - leaf.top;
                if (reached[li][lj]) continue;
                reached[li][lj] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        // Repair pass — for any unreached walkable cell, flip its cardinal
        // water neighbors back to dirt and re-seed BFS. Stable when every
        // walkable cell is reached.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    int li = x - leaf.left;
                    int lj = y - leaf.top;
                    if (!grid.isWalkable(x, y) || reached[li][lj]) continue;
                    // Flip every cardinal water neighbor to dirt; mark this
                    // cell reached (its neighbors will fall into it on the
                    // next BFS sweep).
                    int[][] dirs = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
                    for (int[] d : dirs) {
                        int nx = x + d[0];
                        int ny = y + d[1];
                        if (!leaf.contains(nx, ny)) continue;
                        if (!topology.isWater(nx, ny)) continue;
                        topology.setGroundKind(nx, ny, GroundKind.DIRT);
                        grid.setWalkable(nx, ny, true);
                        reached[nx - leaf.left][ny - leaf.top] = true;
                        changed = true;
                    }
                    if (changed) {
                        reached[li][lj] = true;
                    }
                }
            }
        }
    }

    private static void seedIfWalkable(NavigationGrid grid, BlockLeaf leaf,
                                       boolean[][] reached,
                                       java.util.ArrayDeque<int[]> queue,
                                       int x, int y) {
        if (!leaf.contains(x, y)) return;
        if (!grid.isWalkable(x, y)) return;
        int li = x - leaf.left;
        int lj = y - leaf.top;
        if (reached[li][lj]) return;
        reached[li][lj] = true;
        queue.add(new int[]{x, y});
    }

    /** Same water-stamp convention as {@link WaterfrontFiller} — non-walkable, see-through, WATER ground. */
    private static void markWater(int x, int y, NavigationGrid grid, CellTopology topology) {
        topology.setGroundKind(x, y, GroundKind.WATER);
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
    }

    /** True if any cardinal neighbor of {@code (x, y)} inside the leaf is a water cell. Used to find the bank ring. */
    private static boolean hasWaterNeighbor(CellTopology topology, int x, int y, BlockLeaf leaf) {
        return (leaf.contains(x + 1, y) && topology.isWater(x + 1, y))
                || (leaf.contains(x - 1, y) && topology.isWater(x - 1, y))
                || (leaf.contains(x, y + 1) && topology.isWater(x, y + 1))
                || (leaf.contains(x, y - 1) && topology.isWater(x, y - 1));
    }

    /**
     * Pass-2: per-cell roll for an overlay tile. Plants tried first (cheap
     * to skip if the base isn't grass); rocks tried second. Plant + rock
     * are mutually exclusive in a single cell — first match wins.
     */
    private void scatterOverlays(BlockLeaf leaf, CellTopology topology, Random rng, TileRegistry reg, FillerParams params) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                GroundKind base = topology.getGroundKind(x, y);
                if (base == GroundKind.WATER) continue;
                String baseId = baseTileIdFor(base);
                if (baseId == null) continue;
                TileDef baseDef = reg.tile(baseId);
                if (baseDef == null) continue;

                // Plant attempt — only meaningful on grass cells.
                if (base == GroundKind.GRASS && rng.nextFloat() < params.plantChance) {
                    TileDef plantDef = reg.tile(params.pickPlantId(rng));
                    if (plantDef.canOverlayOn(baseDef)) {
                        topology.setNatureOverlayIndex(x, y, plantDef.index);
                        continue;
                    }
                }
                // Rock attempt — valid on grass/dirt/sand; the per-kind density
                // (denser on beach) is carried by params.rockChance.
                if (rng.nextFloat() < params.rockChance) {
                    TileDef rockDef = reg.tile(params.pickRockId(rng));
                    if (!rockDef.canOverlayOn(baseDef)) continue;
                    topology.setNatureOverlayIndex(x, y, rockDef.index);
                }
            }
        }
    }

    /**
     * Maps a {@link GroundKind} back to a representative tile id so
     * {@link TileDef#canOverlayOn} can decide whether a given overlay is
     * legal on this surface. Returns {@code null} for ground kinds that
     * never host overlays — wall + indoor cells fall through.
     */
    private static String baseTileIdFor(GroundKind kind) {
        switch (kind) {
            case GRASS: return "nature.grass-1";
            case DIRT:  return "nature.dirt-1";
            case SAND:  return "nature.sand";
            case WATER: return "nature.water-1";
            default:    return null;
        }
    }

}
