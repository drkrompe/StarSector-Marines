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
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DoodadRenderSystemTest {

    @Test
    void catalogFloorAndFixtureRetainPainterOrderWithoutAnUrbanSheet() {
        SpriteAPI floor = token(), fixture = token();
        BattleSprites sprites = new BattleSprites() {
            @Override public SpriteAPI extraTerrainSheet(String path) {
                return path.equals("custom/floor.png") ? floor : fixture;
            }
        };
        DoodadDef floorDef = new DoodadDef("test.floor", "custom/floor.png", 0, 0,
                DoodadCover.NONE, 0f, 1, 1, null, 64);
        DoodadDef fixtureDef = new DoodadDef("test.fixture", "custom/fixture.png", 0, 0,
                DoodadCover.NONE, 0f, 1, 1, null, 64);
        BattleCamera camera = new BattleCamera(4, 4);
        camera.setViewport(0, 0, 128, 128, 32);
        DrawList out = new DrawList();
        new DoodadRenderSystem(sprites).collectPlacements(List.of(
                new Doodad(1, 1, floorDef), new Doodad(1, 1, fixtureDef),
                new Doodad(2, 1, floorDef), new Doodad(99, 99, fixtureDef)), out, camera,
                camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS, 4, 4), 1f);
        assertEquals(3, out.count(RenderLayer.DOODADS));
        DrawCommand[] commands = out.buffer(RenderLayer.DOODADS);
        assertSame(floor, commands[0].sprite());
        assertSame(fixture, commands[1].sprite());
        assertSame(floor, commands[2].sprite());
    }

    private static SpriteAPI token() {
        return (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> null);
    }

    @Test
    void legacySheetsKeepTheirExistingBatchOrder() {
        SpriteAPI urban = token(), road = token();
        BattleSprites sprites = new BattleSprites() {
            @Override public SpriteAPI tileSheet() { return urban; }
            @Override public SpriteAPI roadSheet() { return road; }
        };
        DoodadDef urbanDef = new DoodadDef("test.urban", TileManifest.SHEET, 0, 0,
                DoodadCover.NONE, 0f, 1, 1, null, 64);
        DoodadDef roadDef = new DoodadDef("test.road", TileManifest.ROAD_SHEET, 0, 0,
                DoodadCover.NONE, 0f, 1, 1, null, 64);
        BattleCamera camera = new BattleCamera(4, 4);
        camera.setViewport(0, 0, 128, 128, 32);
        DrawList out = new DrawList();
        new DoodadRenderSystem(sprites).collectPlacements(List.of(
                new Doodad(1, 1, urbanDef), new Doodad(2, 1, roadDef),
                new Doodad(1, 2, urbanDef)), out, camera,
                camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS, 4, 4), 1f);
        assertEquals(3, out.count(RenderLayer.DOODADS));
        assertSame(road, out.buffer(RenderLayer.DOODADS)[0].sprite());
        assertSame(urban, out.buffer(RenderLayer.DOODADS)[1].sprite());
        assertSame(urban, out.buffer(RenderLayer.DOODADS)[2].sprite());
    }

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
