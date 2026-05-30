package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

import java.util.Random;

/**
 * Spawn-anchor selection — the last RNG consumer in the pipeline. In conquest
 * mode the marine spawn is pinned to the BEACH biome and the defender spawn
 * to the (deep) FORTRESS_DISTRICT biome; in legacy mode the marine spawns in
 * the low-X half and the defender in the high-X half. Binds
 * {@link BspKeys#MARINE_SPAWN} / {@link BspKeys#DEFENDER_SPAWN}.
 */
public final class SpawnAnchorStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        Random rng = ctx.rng;
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        int[] marine;
        int[] defender;
        if (axis != null) {
            marine   = pickBiomeSpawn(grid, biomeMap, BiomeKind.BEACH,             rng, axis, false);
            defender = pickBiomeSpawn(grid, biomeMap, BiomeKind.FORTRESS_DISTRICT, rng, axis, true);
        } else {
            marine   = pickSpawnAnchor(grid, 1, 1, ctx.width / 2, ctx.height - 1, rng);
            defender = pickSpawnAnchor(grid, ctx.width / 2, 1, ctx.width - 1, ctx.height - 1, rng);
        }
        ctx.put(BspKeys.MARINE_SPAWN, marine);
        ctx.put(BspKeys.DEFENDER_SPAWN, defender);
    }

    /**
     * Random walkable cell inside the rect that bounds every cell tagged with
     * {@code biome}. Falls back to a linear scan of every biome cell if 64
     * random tries miss, then to map center if no walkable cell exists inside
     * the biome (shouldn't happen on real-size maps).
     *
     * <p>When {@code deepBias} is true the search rect is truncated to the
     * deepest 60% of the biome along the traversal {@code axis} (high y for
     * SOUTH_TO_NORTH, high x for WEST_TO_EAST). Used for the defender spawn so
     * it lands <em>inside</em> the fortress wall rather than in the kill zone —
     * the wall sits at ~30% inset from the biome's attacker-facing edge, so 60%
     * reliably lands past it.
     */
    private static int[] pickBiomeSpawn(NavigationGrid grid, BiomeMap biomeMap, BiomeKind biome,
                                        Random rng, TraversalAxis axis, boolean deepBias) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, top = Integer.MAX_VALUE, bot = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (biomeMap.biomeAt(x, y) != biome) continue;
                if (x < lo)  lo = x;
                if (x > hi)  hi = x;
                if (y < top) top = y;
                if (y > bot) bot = y;
            }
        }
        if (lo == Integer.MAX_VALUE) {
            return new int[]{ w / 2, h / 2 };
        }
        if (deepBias) {
            if (axis == TraversalAxis.SOUTH_TO_NORTH) {
                top = top + (int) ((bot - top) * 0.4f);
            } else {
                lo = lo + (int) ((hi - lo) * 0.4f);
            }
        }
        int spanX = Math.max(1, hi - lo + 1);
        int spanY = Math.max(1, bot - top + 1);
        for (int i = 0; i < 64; i++) {
            int x = lo + rng.nextInt(spanX);
            int y = top + rng.nextInt(spanY);
            if (biomeMap.biomeAt(x, y) == biome && grid.isWalkable(x, y)) return new int[]{x, y};
        }
        for (int y = top; y <= bot; y++) {
            for (int x = lo; x <= hi; x++) {
                if (biomeMap.biomeAt(x, y) == biome && grid.isWalkable(x, y)) return new int[]{x, y};
            }
        }
        return new int[]{ (lo + hi) / 2, (top + bot) / 2 };
    }

    /**
     * Random walkable cell in the given rect. Linear-scan fallback if 64 random
     * tries miss (very tight maps).
     */
    private static int[] pickSpawnAnchor(NavigationGrid grid, int xMin, int yMin, int xMax, int yMax, Random rng) {
        int spanX = Math.max(1, xMax - xMin);
        int spanY = Math.max(1, yMax - yMin);
        for (int i = 0; i < 64; i++) {
            int x = xMin + rng.nextInt(spanX);
            int y = yMin + rng.nextInt(spanY);
            if (grid.inBounds(x, y) && grid.isWalkable(x, y)) return new int[]{x, y};
        }
        for (int y = yMin; y < yMax; y++) {
            for (int x = xMin; x < xMax; x++) {
                if (grid.isWalkable(x, y)) return new int[]{x, y};
            }
        }
        return new int[]{(xMin + xMax) / 2, (yMin + yMax) / 2};
    }
}
