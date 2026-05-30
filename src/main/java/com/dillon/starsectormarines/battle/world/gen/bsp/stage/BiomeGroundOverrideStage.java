package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Step 3b — biome ground overrides. In conquest mode the BEACH biome repaints
 * its walkable non-building, non-water ground as {@link GroundKind#SAND} so the
 * beach reads as a continuous sandy landing zone rather than the generator's
 * default STREET grey. Runs after fill so we know which cells are INDOOR (skip)
 * vs outdoors (paint). Conquest-recipe-only — requires {@link BspKeys#BIOME_MAP}
 * and throws if invoked without it (recipe membership, not a self-gate, keeps it
 * off the legacy path).
 *
 * <p>Skips {@link GroundKind#INDOOR} (preserve building floors) and
 * {@link GroundKind#WATER} (preserve waterfront water bands), but rewrites
 * STREET / COURTYARD / GRASS / DIRT / STONE / TILE / etc. The visual effect:
 * the entire beach reads as continuous sand even though the BSP infill still
 * produced varied block kinds (parks become sand "scrub," roads become "beach
 * paths").
 */
public final class BiomeGroundOverrideStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        if (biomeMap == null) {
            throw new IllegalStateException(
                    "BiomeGroundOverrideStage requires BIOME_MAP — conquest recipe only");
        }
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (biomeMap.biomeAt(x, y) != BiomeKind.BEACH) continue;
                GroundKind g = topology.getGroundKind(x, y);
                if (g == GroundKind.INDOOR || g == GroundKind.WATER) continue;
                topology.setGroundKind(x, y, GroundKind.SAND);
            }
        }
    }
}
