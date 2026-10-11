package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuildingMaterialRenderTest {
    @Test
    void wallAndFloorUseTheirCatalogSheetAndHaveALiveDrawBatch() throws Exception {
        String path = TileRegistry.installed().block("colony.masonry-wall").sheetPath;
        SpriteAPI urban = sprite();
        SpriteAPI colony = sprite();
        SpriteAPI floors = sprite();
        String floorPath = TileRegistry.installed().block("colony.terracotta").sheetPath;
        BattleSprites sprites = new BattleSprites() {
            @Override public SpriteAPI tileSheet() { return urban; }
            @Override public int tileSheetPxW() { return 512; }
            @Override public int tileSheetPxH() { return 512; }
            @Override public SpriteAPI extraTerrainSheet(String requested) {
                return path.equals(requested) ? colony : floorPath.equals(requested) ? floors : null;
            }
            @Override public Collection<SpriteAPI> extraTerrainSheets() { return List.of(colony, floors); }
        };
        NavigationGrid grid = new NavigationGrid(2, 1);
        grid.setWalkableFloor(1, 0);
        CellTopology topology = new CellTopology(2, 1);
        topology.setWall(0, 0, true);
        topology.setWallDirMask(0, 0, CellTopology.WALL_DIR_N);
        topology.setSurface(0, 0, topology.surfaceIndex("colony.masonry-wall"));
        topology.setGroundKind(1, 0, CellTopology.GroundKind.INDOOR);
        topology.setSurface(1, 0, topology.surfaceIndex("colony.terracotta"));
        BattleSimulation sim = new BattleSimulation(grid, topology);
        BattleCamera camera = new BattleCamera(2, 1);
        camera.setViewport(0, 0, 200, 100, 100);
        DrawList out = new DrawList();
        new GroundRenderSystem(sprites).collect(new RenderContext(sim, camera,
                null, 1, 0, false, new HighlightOverlay(), new Selection()), out);
        int tiles = 0;
        for (int i = 0; i < out.count(RenderLayer.GROUND); i++) {
            DrawCommand command = out.buffer(RenderLayer.GROUND)[i];
            if (command.kind() != DrawCommand.Kind.SHEET_QUAD) continue;
            assertSame(tiles == 0 ? colony : floors, command.sprite(),
                    "each block's coordinates must address its own sheet");
            tiles++;
        }
        assertEquals(2, tiles, "both the wall and floor need a textured draw");

        BattleRenderer renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();
        var field = BattleRenderer.class.getDeclaredField("batchBySheet");
        field.setAccessible(true);
        assertNotNull(((Map<?, ?>) field.get(renderer)).get(colony),
                "the live drain silently drops sheets without a registered batch");
        assertNotNull(((Map<?, ?>) field.get(renderer)).get(floors));
    }

    private static SpriteAPI sprite() {
        return (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getHeight" -> 512f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
