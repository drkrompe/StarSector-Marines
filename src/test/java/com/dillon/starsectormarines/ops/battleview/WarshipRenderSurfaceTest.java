package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.warship.WarshipAtmosphere;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** The warship renderer tints its atmosphere by the surface the battle was set up with. */
class WarshipRenderSurfaceTest {

    private static WarshipAtmosphere.Tint tintAfterCollecting(SurfacePalette surface) {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(20, 20));
        sim.setSurface(surface);
        BattleCamera camera = new BattleCamera(20, 20);
        camera.setViewport(0f, 0f, 400f, 400f, 20f);
        RenderContext ctx = new RenderContext(sim, camera, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        WarshipRenderSystem system = new WarshipRenderSystem(new BattleSprites() {
            @Override public void ensureEngineFxSprites() { }
        });
        system.collect(ctx, new DrawList());
        return system.tint();
    }

    @Test
    void theRendererReadsTheBattlesOwnSurface() {
        assertArrayEquals(WarshipAtmosphere.Tint.forPalette(SurfacePalette.FROZEN).glow(),
                tintAfterCollecting(SurfacePalette.FROZEN).glow());
        assertArrayEquals(WarshipAtmosphere.Tint.forPalette(SurfacePalette.ARID).dust(),
                tintAfterCollecting(SurfacePalette.ARID).dust());
    }

    @Test
    void aBattleWithNoPlanetBehindItIsBareRock() {
        assertArrayEquals(WarshipAtmosphere.Tint.forPalette(null).vapour(), tintAfterCollecting(null).vapour());
    }
}
