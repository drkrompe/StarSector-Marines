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
import static org.junit.jupiter.api.Assertions.assertNull;

class GroundMicroHeightSamplerTest {

    private static SpriteSheetFrames natureFrames;
    private static SpriteSheetFrames urban3Frames;
    private static final Map<String, BufferedImage> images = new HashMap<>();

    @BeforeAll
    static void loadAssets() throws Exception {
        natureFrames = frames(TileManifest.NATURE_SHEET);
        urban3Frames = frames(TileManifest.STREET3_SHEET);
    }

    @Test
    void resolvesExactHashedNatureFrameWithoutAveragingIt() {
        NavigationGrid grid = walkableGrid(4, 3);
        CellTopology topology = new CellTopology(4, 3);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.GRASS);
        topology.setGroundKind(2, 1, CellTopology.GroundKind.DIRT);

        GroundMicroHeightSampler resolver = resolver(natureFrames, urban3Frames);
        TileRegistry reg = TileRegistry.installed();
        String grassId = GroundTileSelector.natureTileId(CellTopology.GroundKind.GRASS, 1, 1);
        String dirtId = GroundTileSelector.natureTileId(CellTopology.GroundKind.DIRT, 2, 1);
        assertEquals("nature.grass-1", grassId);
        assertEquals("nature.dirt-2", dirtId);
        assertSample(expectedSliced(reg.tile(grassId), natureFrames), resolver.resolve(grid, topology, 1, 1));
        assertSample(expectedSliced(reg.tile(dirtId), natureFrames), resolver.resolve(grid, topology, 2, 1));
    }

    @Test
    void resolvesStreetAndImplicitOrExplicitSidewalkFrames() {
        NavigationGrid grid = walkableGrid(5, 5);
        CellTopology topology = new CellTopology(5, 5);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.STREET);
        topology.setGroundKind(2, 2, CellTopology.GroundKind.STREET);
        topology.setWall(2, 3, true);
        topology.setGroundKind(4, 4, CellTopology.GroundKind.SIDEWALK);

        GroundMicroHeightSampler resolver = resolver(natureFrames, urban3Frames);
        TileRegistry reg = TileRegistry.installed();
        assertSample(expectedSliced(reg.tile("urban3.street-square"), urban3Frames),
                resolver.resolve(grid, topology, 1, 1));
        assertSample(expectedSliced(reg.tile("urban3.sidewalk-corner"), urban3Frames),
                resolver.resolve(grid, topology, 2, 2));
        assertSample(expectedSliced(reg.tile("urban3.sidewalk-corner"), urban3Frames),
                resolver.resolve(grid, topology, 4, 4));
    }

    @Test
    void mirrorsFixedGridFallbacksAndMacroOnlyCases() {
        NavigationGrid grid = walkableGrid(4, 3);
        CellTopology topology = new CellTopology(4, 3);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.GRASS);
        topology.setGroundKind(2, 1, CellTopology.GroundKind.STREET);
        topology.setGroundKind(3, 1, CellTopology.GroundKind.SIDEWALK);
        topology.setWall(2, 2, true);

        GroundMicroHeightSampler resolver = resolver(null, null);
        TileRegistry reg = TileRegistry.installed();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        assertSample(expectedGrid(reg.block(mapping.groundBlockId(CellTopology.GroundKind.GRASS)),
                        topology, 1, 1),
                resolver.resolve(grid, topology, 1, 1));
        assertSample(expectedGrid(reg.block("road.sidewalk"), topology, 2, 1),
                resolver.resolve(grid, topology, 2, 1));
        assertNull(resolver.resolve(grid, topology, 3, 1),
                "explicit SIDEWALK has no fixed-grid color fallback");

        topology.setGroundKind(0, 0, CellTopology.GroundKind.INDOOR);
        assertNull(resolver.resolve(grid, topology, 0, 0),
                "S1 skipped the mixed urban wall/floor sheet");
    }

    @Test
    void cacheReResolvesChangedTerrainAndNeighborWalls() {
        NavigationGrid grid = walkableGrid(3, 3);
        CellTopology topology = new CellTopology(3, 3);
        GroundMicroHeightSampler resolver = resolver(natureFrames, urban3Frames);

        topology.setGroundKind(1, 1, CellTopology.GroundKind.GRASS);
        GroundMicroHeightSampler.Sample grass = resolver.resolve(grid, topology, 1, 1);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.DIRT);
        GroundMicroHeightSampler.Sample dirt = resolver.resolve(grid, topology, 1, 1);
        assertEquals(expectedSliced(TileRegistry.installed().tile("nature.grass-1"), natureFrames).srcX, grass.srcX);
        assertEquals(expectedSliced(TileRegistry.installed().tile("nature.dirt-1"), natureFrames).srcX, dirt.srcX);

        topology.setGroundKind(1, 1, CellTopology.GroundKind.STREET);
        topology.setWall(1, 2, true);
        assertSample(expectedSliced(TileRegistry.installed().tile("urban3.sidewalk-corner"), urban3Frames),
                resolver.resolve(grid, topology, 1, 1));
        topology.setWall(1, 2, false);
        assertSample(expectedSliced(TileRegistry.installed().tile("urban3.street-square"), urban3Frames),
                resolver.resolve(grid, topology, 1, 1));
    }

    @Test
    void macroAndMicroCompositionMatchesShaderFormulaAndClamps() {
        assertEquals(0f, GroundHeightPass.microRelief(0.5f), 1e-6f);
        assertEquals(0.125f, GroundHeightPass.microRelief(1f), 1e-6f);
        assertEquals(-0.125f, GroundHeightPass.microRelief(0f), 1e-6f);
    }

    private static GroundMicroHeightSampler resolver(SpriteSheetFrames nature, SpriteSheetFrames urban3) {
        return new GroundMicroHeightSampler(() -> nature, () -> urban3);
    }

    private static GroundMicroHeightSampler.Sample expectedSliced(TileDef tile, SpriteSheetFrames frames) {
        SpriteSheetFrames.Frame frame = frames.frames[tile.frame];
        int inset = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;
        return new GroundMicroHeightSampler.Sample(
                GroundMicroHeightSampler.derivedHeightPath(tile.sheetPath),
                frame.x + inset, frame.y + inset,
                Math.max(1, frame.w - 2 * inset), Math.max(1, frame.h - 2 * inset));
    }

    private static GroundMicroHeightSampler.Sample expectedGrid(
            GridBlockDef block, CellTopology topology, int x, int y) {
        int[] cell = block.resolve(wall(topology, x, y + 1), wall(topology, x, y - 1),
                wall(topology, x + 1, y), wall(topology, x - 1, y), x, y);
        int inset = block.cellPx >= TileManifest.TILE_SIZE
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        return new GroundMicroHeightSampler.Sample(
                GroundMicroHeightSampler.derivedHeightPath(block.sheetPath),
                cell[0] * block.cellPx + inset, cell[1] * block.cellPx + inset,
                block.cellPx - 2 * inset, block.cellPx - 2 * inset);
    }

    private static void assertSample(GroundMicroHeightSampler.Sample expected,
                                     GroundMicroHeightSampler.Sample actual) {
        assertEquals(expected.heightSheetPath, actual.heightSheetPath);
        assertEquals(expected.normalSheetPath, actual.normalSheetPath);
        assertEquals(expected.srcX, actual.srcX);
        assertEquals(expected.srcY, actual.srcY);
        assertEquals(expected.srcW, actual.srcW);
        assertEquals(expected.srcH, actual.srcH);
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
