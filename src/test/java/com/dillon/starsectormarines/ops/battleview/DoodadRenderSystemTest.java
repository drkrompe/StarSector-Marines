package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoodadRenderSystemTest {

    @Test
    void rotatedPlacementSamplesCanonicalArtAndTurnsItsDestination() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(8, 8));
        DoodadDef def = new DoodadDef("test.rotated", TileManifest.DOODAD_SHEET,
                2, 3, DoodadCover.MED, 0.4f, 2, 1, null, 96);
        sim.addDoodad(new Doodad(3, 2, def, 1));
        BattleCamera camera = new BattleCamera(8, 8);
        camera.setViewport(0f, 0f, 800f, 800f, 100f);
        SpriteAPI sheet = (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> null);
        BattleSprites sprites = new BattleSprites() {
            @Override public SpriteAPI tileSheet() { return sheet; }
            @Override public SpriteAPI doodadSheet() { return sheet; }
        };
        DrawList out = new DrawList();

        new DoodadRenderSystem(sprites).collect(
                new RenderContext(sim, camera, null, 1f, 0f, false,
                        new HighlightOverlay(), new Selection()), out);

        assertEquals(1, out.count(RenderLayer.DOODADS));
        DrawCommand command = out.buffer(RenderLayer.DOODADS)[0];
        assertEquals(192, command.sourceWidth());
        assertEquals(96, command.sourceHeight());
        assertEquals(200f, command.width(), 0.001f);
        assertEquals(100f, command.height(), 0.001f);
        assertEquals(90f, command.angleDegrees(), 0.001f);
        assertEquals(camera.cellToScreenX(3.5f), command.centerX(), 0.001f);
        assertEquals(camera.cellToScreenY(3f), command.centerY(), 0.001f);
    }
}
