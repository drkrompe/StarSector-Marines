package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundMicroHeightSamplerTest {

    private static final float MACRO = 0.5f;
    private static final float MICRO_SCALE = 0.25f;

    private static SpriteSheetFrames natureFrames;
    private static SpriteSheetFrames urban3Frames;
    private static final Map<String, BufferedImage> images = new HashMap<>();

    @BeforeAll
    static void loadAssets() throws Exception {
        natureFrames = frames(TileManifest.NATURE_SHEET);
        urban3Frames = frames(TileManifest.STREET3_SHEET);
    }

    @Test
    void samplesTheExactHashedNatureFrameUsedByTheColorPass() {
        NavigationGrid grid = walkableGrid(4, 3);
        CellTopology topology = new CellTopology(4, 3);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.GRASS);
        topology.setGroundKind(2, 1, CellTopology.GroundKind.DIRT);

        GroundMicroHeightSampler sampler = sampler(natureFrames, urban3Frames);
        TileRegistry reg = TileRegistry.installed();

        String grassId = GroundTileSelector.natureTileId(CellTopology.GroundKind.GRASS, 1, 1);
        String dirtId = GroundTileSelector.natureTileId(CellTopology.GroundKind.DIRT, 2, 1);
        assertEquals("nature.grass-1", grassId);
        assertEquals("nature.dirt-2", dirtId);
        assertEquals(expectedSliced(MACRO, reg.tile(grassId), natureFrames),
                sampler.combine(MACRO, grid, topology, 1, 1), 1e-6f);
        assertEquals(expectedSliced(MACRO, reg.tile(dirtId), natureFrames),
                sampler.combine(MACRO, grid, topology, 2, 1), 1e-6f);
    }

    @Test
    void samplesStreetAndImplicitOrExplicitSidewalkFrames() {
        NavigationGrid grid = walkableGrid(5, 5);
        CellTopology topology = new CellTopology(5, 5);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.STREET);
        topology.setGroundKind(2, 2, CellTopology.GroundKind.STREET);
        topology.setWall(2, 3, true); // makes (2,2) an implicit sidewalk
        topology.setGroundKind(4, 4, CellTopology.GroundKind.SIDEWALK);

        GenMappingRegistry mapping = GenMappingRegistry.installed();
        String streetId = mapping.groundBlockId(CellTopology.GroundKind.STREET);
        assertEquals("urban3.street-square",
                GroundTileSelector.urban3TileId(grid, topology, streetId, 1, 1));
        assertEquals("urban3.sidewalk-corner",
                GroundTileSelector.urban3TileId(grid, topology, streetId, 2, 2));
        assertEquals("urban3.sidewalk-corner",
                GroundTileSelector.urban3TileId(grid, topology, streetId, 4, 4));

        GroundMicroHeightSampler sampler = sampler(natureFrames, urban3Frames);
        TileRegistry reg = TileRegistry.installed();
        assertEquals(expectedSliced(MACRO, reg.tile("urban3.street-square"), urban3Frames),
                sampler.combine(MACRO, grid, topology, 1, 1), 1e-6f);
        assertEquals(expectedSliced(MACRO, reg.tile("urban3.sidewalk-corner"), urban3Frames),
                sampler.combine(MACRO, grid, topology, 2, 2), 1e-6f);
        assertEquals(expectedSliced(MACRO, reg.tile("urban3.sidewalk-corner"), urban3Frames),
                sampler.combine(MACRO, grid, topology, 4, 4), 1e-6f);
    }

    @Test
    void mirrorsFixedGridFallbacksWhenSlicedColorSheetsAreUnavailable() {
        NavigationGrid grid = walkableGrid(4, 3);
        CellTopology topology = new CellTopology(4, 3);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.GRASS);
        topology.setGroundKind(2, 1, CellTopology.GroundKind.STREET);
        topology.setGroundKind(3, 1, CellTopology.GroundKind.SIDEWALK);
        topology.setWall(2, 2, true); // STREET color fallback chooses road.sidewalk

        GroundMicroHeightSampler sampler = sampler(null, null);
        TileRegistry reg = TileRegistry.installed();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        assertEquals(expectedGrid(MACRO, reg.block(mapping.groundBlockId(CellTopology.GroundKind.GRASS)),
                        topology, 1, 1),
                sampler.combine(MACRO, grid, topology, 1, 1), 1e-6f);
        assertEquals(expectedGrid(MACRO, reg.block("road.sidewalk"), topology, 2, 1),
                sampler.combine(MACRO, grid, topology, 2, 1), 1e-6f);
        assertEquals(MACRO, sampler.combine(MACRO, grid, topology, 3, 1), 1e-6f,
                "explicit SIDEWALK has no color fallback and must stay macro-only");
    }

    @Test
    void cacheKeepsOnlyTheMicroOffsetAndReResolvesChangedTerrainKind() {
        NavigationGrid grid = walkableGrid(3, 3);
        CellTopology topology = new CellTopology(3, 3);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.GRASS);
        GroundMicroHeightSampler sampler = sampler(natureFrames, urban3Frames);

        float atLowMacro = sampler.combine(0.35f, grid, topology, 1, 1);
        float atHighMacro = sampler.combine(0.75f, grid, topology, 1, 1);
        assertEquals(0.40f, atHighMacro - atLowMacro, 1e-6f,
                "cached samples must not freeze the first macro height");

        topology.setGroundKind(1, 1, CellTopology.GroundKind.DIRT);
        TileDef dirt = TileRegistry.installed().tile(
                GroundTileSelector.natureTileId(CellTopology.GroundKind.DIRT, 1, 1));
        assertEquals(expectedSliced(0.75f, dirt, natureFrames),
                sampler.combine(0.75f, grid, topology, 1, 1), 1e-6f,
                "a changed kind at the same coordinate must select a new frame");

        topology.setGroundKind(1, 1, CellTopology.GroundKind.STREET);
        topology.setWall(1, 2, true);
        float sidewalk = sampler.combine(MACRO, grid, topology, 1, 1);
        assertEquals(expectedSliced(MACRO, TileRegistry.installed().tile("urban3.sidewalk-corner"), urban3Frames),
                sidewalk, 1e-6f);
        topology.setWall(1, 2, false);
        assertEquals(expectedSliced(MACRO, TileRegistry.installed().tile("urban3.street-square"), urban3Frames),
                sampler.combine(MACRO, grid, topology, 1, 1), 1e-6f,
                "destroying an adjacent wall must invalidate implicit-sidewalk selection");
    }

    private static GroundMicroHeightSampler sampler(SpriteSheetFrames nature, SpriteSheetFrames urban3) {
        return new GroundMicroHeightSampler(() -> nature, () -> urban3,
                path -> new HeightSheetTexture(image(path)));
    }

    private static float expectedSliced(float macro, TileDef tile, SpriteSheetFrames frames) {
        SpriteSheetFrames.Frame f = frames.frames[tile.frame];
        int inset = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;
        float average = new HeightSheetTexture(image(GroundMicroHeightSampler.derivedHeightPath(tile.sheetPath)))
                .averageHeight(f.x + inset, f.y + inset,
                        Math.max(1, f.w - 2 * inset), Math.max(1, f.h - 2 * inset));
        return macro + (average - 0.5f) * MICRO_SCALE;
    }

    private static float expectedGrid(float macro, GridBlockDef block, CellTopology topology, int x, int y) {
        int[] cell = block.resolve(wall(topology, x, y + 1), wall(topology, x, y - 1),
                wall(topology, x + 1, y), wall(topology, x - 1, y), x, y);
        int inset = block.cellPx >= TileManifest.TILE_SIZE
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        float average = new HeightSheetTexture(image(GroundMicroHeightSampler.derivedHeightPath(block.sheetPath)))
                .averageHeight(cell[0] * block.cellPx + inset, cell[1] * block.cellPx + inset,
                        block.cellPx - 2 * inset, block.cellPx - 2 * inset);
        return macro + (average - 0.5f) * MICRO_SCALE;
    }

    private static boolean wall(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }

    private static NavigationGrid walkableGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static SpriteSheetFrames frames(String path) throws IOException {
        return SpriteSheetSlicer.slice(image(path));
    }

    private static BufferedImage image(String path) {
        return images.computeIfAbsent(path, p -> {
            Path file = Paths.get("mod", p.replace('/', java.io.File.separatorChar));
            try {
                BufferedImage img = ImageIO.read(file.toFile());
                if (img == null) throw new IOException("ImageIO returned null for " + file);
                return img;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read test asset " + file, e);
            }
        });
    }
}
