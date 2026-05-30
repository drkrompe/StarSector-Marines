package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.List;
import java.util.Random;

/**
 * Step 3b' — beach shoreline. Carves a wavy water band along the
 * attacker-facing map edge inside the BEACH biome. Runs after the SAND override
 * so the water cells survive (the override skips {@link GroundKind#WATER}).
 * Strips any leaf-fill state at the shoreline cells — doodads, walls, building
 * hints, nature overlays — so nothing else "lives" in the reserved water strip.
 * Conquest-recipe-only — requires {@link BspKeys#BIOME_MAP} and throws if
 * invoked without it (recipe membership keeps it off the legacy path).
 *
 * <p>For {@link TraversalAxis#SOUTH_TO_NORTH} the band sits at the bottom of
 * the map (low {@code y}); for {@link TraversalAxis#WEST_TO_EAST} at the left
 * (low {@code x}). Depth varies per-column (or per-row) via smoothed random
 * jitter so the sand/water boundary reads as a gentle coastline.
 *
 * <p>For each cell flipped to water this strips the {@code WALL} tag, the
 * nature overlay, the building-kind hint + building id, and any {@link Doodad}
 * whose cell falls inside the band — guaranteeing the shoreline strip contains
 * nothing but walkable-blocking water. Wall-direction masks at the water cells
 * are cleared so the subsequent {@code tagDefaultWalls} pass can't pick up
 * stale bits.
 */
public final class BeachShorelineStage implements GenStage {

    /** Nominal water depth at the attacker-facing edge of the BEACH biome, in cells. The wavy shore jitters around this baseline. */
    private static final int SHORELINE_NOMINAL_DEPTH = 3;
    /** Per-column jitter amplitude on the shore depth. Smoothed across neighbors so the boundary reads as gentle waves rather than sawtooth. */
    private static final int SHORELINE_JITTER = 2;
    /** Rolling window radius for the shore-boundary smoothing. Larger = longer wavelength. */
    private static final int SHORELINE_SMOOTH_RADIUS = 4;
    /** Minimum guaranteed water depth at every column. Keeps at least one continuous water row so the shoreline never has a "dry gap". */
    private static final int SHORELINE_MIN_DEPTH = 1;
    /** Maximum allowed water depth at any column. Prevents the jitter from eating the entire beach. */
    private static final int SHORELINE_MAX_DEPTH = 5;

    @Override
    public void run(GenContext ctx) {
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        if (biomeMap == null) {
            throw new IllegalStateException(
                    "BeachShorelineStage requires BIOME_MAP — conquest recipe only");
        }
        applyBeachShoreline(ctx.grid, ctx.topology, ctx.get(BspKeys.AXIS), biomeMap,
                ctx.get(BspKeys.ROAD_RESERVATION), ctx.doodads, ctx.rng);
    }

    private void applyBeachShoreline(NavigationGrid grid, CellTopology topology,
                                     TraversalAxis axis, BiomeMap biomeMap,
                                     boolean[][] roadReservation,
                                     List<Doodad> doodads, Random rng) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        boolean horizontalAxis = (axis == TraversalAxis.SOUTH_TO_NORTH);

        // Per-perpendicular-coordinate shore depth. For S->N, depth indexed
        // by x; for W->E, depth indexed by y. Smoothed noise so the boundary
        // reads as gentle waves.
        int spanLen = horizontalAxis ? w : h;
        int[] depths = noisyShoreDepth(spanLen, SHORELINE_NOMINAL_DEPTH,
                SHORELINE_JITTER, SHORELINE_SMOOTH_RADIUS, rng);

        for (int i = 0; i < spanLen; i++) {
            int depth = clamp(depths[i], SHORELINE_MIN_DEPTH, SHORELINE_MAX_DEPTH);
            for (int j = 0; j < depth; j++) {
                int x = horizontalAxis ? i : j;
                int y = horizontalAxis ? j : i;
                if (x < 0 || x >= w || y < 0 || y >= h) continue;
                if (biomeMap.biomeAt(x, y) != BiomeKind.BEACH) continue;
                // Trunk centerline meets the shore — the road runs onto a
                // jetty or pier here; drowning the cell would orphan the
                // convoy's perimeter exit. Leave it dry.
                if (roadReservation[x][y]) continue;
                stampShorelineCell(grid, topology, x, y);
            }
        }

        // Drop any doodad whose cell got drowned. Pre-shoreline filler doodads
        // (LZ arrows, beach crates, etc.) would otherwise float on water.
        if (!doodads.isEmpty()) {
            doodads.removeIf(d -> {
                if (!grid.inBounds(d.cellX, d.cellY)) return false;
                return topology.isWater(d.cellX, d.cellY);
            });
        }
    }

    /** Stamp a single shoreline cell as water + strip everything else that might still be tagged at this cell. */
    private static void stampShorelineCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        topology.setGroundKind(x, y, GroundKind.WATER);
        topology.setTag(x, y, CellTopology.Tag.WALL,    false);
        topology.setTag(x, y, CellTopology.Tag.VEHICLE, false);
        topology.setWallDirMask(x, y, 0);
        topology.setBuildingId(x, y, 0);
        topology.setBuildingKindHint(x, y, null);
        topology.setNatureOverlay(x, y, null);
        // Water is non-walkable but see-through — matches WaterfrontFiller /
        // NatureZoneFiller convention. setWalkable preserves any existing
        // floor flags we don't care about here.
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
    }

    /**
     * Per-coordinate shore-depth array of length {@code len}, centered on
     * {@code nominal} with smoothed jitter. Same noise+rolling-average shape
     * as {@code BiomeMap.noisyBoundary}; duplicated locally rather than pulled
     * into a shared helper because the smoothed-shore use cases are still small
     * and the parameters differ.
     */
    private static int[] noisyShoreDepth(int len, int nominal, int jitter, int smoothRadius, Random rng) {
        float[] raw = new float[len];
        for (int i = 0; i < len; i++) {
            raw[i] = (rng.nextFloat() - 0.5f) * 2f * jitter;
        }
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            int lo = Math.max(0, i - smoothRadius);
            int hi = Math.min(len - 1, i + smoothRadius);
            float sum = 0f;
            for (int j = lo; j <= hi; j++) sum += raw[j];
            out[i] = nominal + Math.round(sum / (hi - lo + 1));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
