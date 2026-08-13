package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;

/**
 * Emits the ground layer's MACRO height field as flat per-cell grayscale quads
 * into a {@link SolidQuadBatch} — the height-target sibling pass to
 * {@link GroundRenderSystem}'s color emission, run only while
 * {@link GroundParallaxPipeline} is rendering into the height FBO.
 *
 * <p>One quad per cell, no autotile/atlas resolution: macro height is a
 * per-tile-<em>kind</em> scalar (walls high, buildings raised, ground mid,
 * rubble low, water lowest — see {@link GenMappingRegistry#macroHeight}), not
 * a per-texel one, so unlike {@link GroundRenderSystem} this pass never needs
 * {@code TileRegistry} block/frame lookups — just {@code CellTopology}.
 */
final class GroundHeightPass {

    private GroundHeightPass() {}

    static void emit(BattleCamera cam, NavigationGrid grid, CellTopology topology,
                     GenMappingRegistry mapping, HeightSource micro, SolidQuadBatch out) {
        float cellPx = cam.cellPxSize();
        float wallHeight = (mapping != null) ? mapping.wallMacroHeight() : GenMappingRegistry.DEFAULT_WALL_MACRO_HEIGHT;

        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                float macro = topology.isWall(x, y) ? wallHeight
                        : (mapping != null ? mapping.macroHeight(topology.getGroundKind(x, y)) : 0.5f);
                float h = clamp01(micro.combine(macro, topology, x, y));

                float x0 = cam.cellToScreenX(x);
                float y0 = cam.cellToScreenY(y);
                out.appendRect(x0, y0, x0 + cellPx, y0 + cellPx, h, h, h, 1f);
            }
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
