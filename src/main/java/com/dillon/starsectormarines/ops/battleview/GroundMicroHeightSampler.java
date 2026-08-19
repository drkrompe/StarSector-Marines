package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resolves each ground cell to the exact source rectangle the color pass uses,
 * but on the matching S1-derived height and normal sheets. The material passes
 * draw that rectangle as a texture, preserving every derived texel instead of
 * collapsing the rectangle to one average value.
 *
 * <p>The resolver mirrors both variable-width nature/urban-3 selection and
 * fixed-grid fallback selection. Cells on sheets S1 deliberately skipped
 * (walls, indoor floors, rubble) return {@code null} and stay macro-only.
 * Resolution is cached per cell with a terrain fingerprint so wall demolition
 * and ground-kind changes cannot leave stale atlas coordinates behind.
 */
final class GroundMicroHeightSampler {

    private static final Set<String> DERIVED_SHEETS = Set.of(
            TileManifest.ROAD_SHEET,
            TileManifest.FLOORS_SHEET,
            TileManifest.WATER_SHEET,
            TileManifest.STREET3_SHEET,
            TileManifest.NATURE_SHEET);

    private final Supplier<SpriteSheetFrames> natureFrames;
    private final Supplier<SpriteSheetFrames> urban3Frames;
    private final Map<Long, CacheEntry> cache = new HashMap<>();

    GroundMicroHeightSampler(BattleSprites sprites) {
        this(sprites::natureFrames, sprites::urbanTile3Frames);
    }

    /** Package-private injection seam used by asset-backed unit tests. */
    GroundMicroHeightSampler(Supplier<SpriteSheetFrames> natureFrames,
                             Supplier<SpriteSheetFrames> urban3Frames) {
        this.natureFrames = natureFrames;
        this.urban3Frames = urban3Frames;
    }

    Sample resolve(NavigationGrid grid, CellTopology topology, int gridX, int gridY) {
        long key = packKey(gridX, gridY);
        CellTopology.GroundKind kind = topology.getGroundKind(gridX, gridY);
        boolean wall = topology.isWall(gridX, gridY);
        int state = terrainState(grid, topology, gridX, gridY, kind, wall);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.terrainState == state) return cached.sample;

        Sample sample = resolveUncached(grid, topology, gridX, gridY, wall, kind);
        cache.put(key, new CacheEntry(state, sample));
        return sample;
    }

    /** Drops per-cell atlas resolution between battles. */
    void invalidate() {
        cache.clear();
    }

    private Sample resolveUncached(NavigationGrid grid, CellTopology topology, int gridX, int gridY,
                                   boolean wall, CellTopology.GroundKind kind) {
        if (wall || kind == CellTopology.GroundKind.SNOW) return null;

        GenMappingRegistry mapping = GenMappingRegistry.installed();
        TileRegistry tileReg = TileRegistry.installed();
        if (mapping == null || tileReg == null) return null;

        if (kind == CellTopology.GroundKind.GRASS || kind == CellTopology.GroundKind.DIRT) {
            SpriteSheetFrames frames = natureFrames.get();
            if (frames != null) {
                return sliced(tileReg.tile(GroundTileSelector.natureTileId(kind, gridX, gridY)), frames);
            }
            return gridBlock(mapping.groundBlockId(kind), tileReg, topology, gridX, gridY);
        }

        if (kind == CellTopology.GroundKind.STREET || kind == CellTopology.GroundKind.SIDEWALK) {
            SpriteSheetFrames frames = urban3Frames.get();
            if (frames != null) {
                String streetTileId = mapping.groundBlockId(CellTopology.GroundKind.STREET);
                return sliced(tileReg.tile(GroundTileSelector.urban3TileId(
                        grid, topology, streetTileId, gridX, gridY)), frames);
            }
            return kind == CellTopology.GroundKind.STREET
                    ? streetFallback(grid, topology, tileReg, gridX, gridY)
                    : null;
        }

        return gridBlock(mapping.groundBlockId(kind), tileReg, topology, gridX, gridY);
    }

    private Sample streetFallback(NavigationGrid grid, CellTopology topology,
                                  TileRegistry tileReg, int gridX, int gridY) {
        if (GroundTileSelector.isSidewalkCell(grid, topology, gridX, gridY)) {
            return resolvedBlock(tileReg.block("road.sidewalk"), false, false, false, false, gridX, gridY);
        }
        return resolvedBlock(tileReg.block("road.road"),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX, gridY + 1),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX, gridY - 1),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX + 1, gridY),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX - 1, gridY),
                gridX, gridY);
    }

    private static Sample sliced(TileDef tile, SpriteSheetFrames frames) {
        if (tile == null || frames == null || tile.frame < 0 || tile.frame >= frames.frames.length) return null;
        SpriteSheetFrames.Frame frame = frames.frames[tile.frame];
        int inset = tile.isGround() ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : 0;
        return sample(tile.sheetPath,
                frame.x + inset, frame.y + inset,
                Math.max(1, frame.w - 2 * inset), Math.max(1, frame.h - 2 * inset));
    }

    private static Sample gridBlock(String blockId, TileRegistry tileReg, CellTopology topology,
                                    int gridX, int gridY) {
        if (blockId == null) return null;
        GridBlockDef block = tileReg.block(blockId);
        if (block == null) return null;
        return resolvedBlock(block,
                isWallAt(topology, gridX, gridY + 1),
                isWallAt(topology, gridX, gridY - 1),
                isWallAt(topology, gridX + 1, gridY),
                isWallAt(topology, gridX - 1, gridY),
                gridX, gridY);
    }

    private static Sample resolvedBlock(GridBlockDef block, boolean n, boolean s, boolean e, boolean w,
                                        int gridX, int gridY) {
        if (block == null) return null;
        int[] cell = block.resolve(n, s, e, w, gridX, gridY);
        if (cell == null) return null;
        int inset = block.cellPx >= TileManifest.TILE_SIZE
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        return sample(block.sheetPath,
                cell[0] * block.cellPx + inset, cell[1] * block.cellPx + inset,
                block.cellPx - 2 * inset, block.cellPx - 2 * inset);
    }

    private static Sample sample(String colorSheetPath, int srcX, int srcY, int srcW, int srcH) {
        if (!DERIVED_SHEETS.contains(colorSheetPath)) return null;
        return new Sample(derivedHeightPath(colorSheetPath), derivedNormalPath(colorSheetPath),
                srcX, srcY, srcW, srcH);
    }

    /** {@code graphics/tilesets/Foo.png} -> {@code graphics/tilesets/Foo_height.png}. */
    static String derivedHeightPath(String colorSheetPath) {
        int dot = colorSheetPath.lastIndexOf('.');
        String base = dot >= 0 ? colorSheetPath.substring(0, dot) : colorSheetPath;
        return base + "_height.png";
    }

    /** {@code graphics/tilesets/Foo.png} -> {@code graphics/tilesets/Foo_normal.png}. */
    static String derivedNormalPath(String colorSheetPath) {
        int dot = colorSheetPath.lastIndexOf('.');
        String base = dot >= 0 ? colorSheetPath.substring(0, dot) : colorSheetPath;
        return base + "_normal.png";
    }

    private static boolean isWallAt(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }

    private static long packKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private int terrainState(NavigationGrid grid, CellTopology topology, int x, int y,
                             CellTopology.GroundKind kind, boolean wall) {
        int state = 31 * kind.ordinal() + (wall ? 1 : 0);
        if (kind == CellTopology.GroundKind.STREET || kind == CellTopology.GroundKind.SIDEWALK) {
            state = 31 * state + (urban3Frames.get() == null ? 0 : 1);
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int sx = x + dx;
                    int sy = y + dy;
                    int cell = topology.inBounds(sx, sy)
                            ? topology.getGroundKind(sx, sy).ordinal() + 1 : 0;
                    cell = 31 * cell + (topology.isWall(sx, sy) ? 1 : 0);
                    cell = 31 * cell + (grid.inBounds(sx, sy) && grid.isWalkable(sx, sy) ? 1 : 0);
                    state = 31 * state + cell;
                }
            }
            return state;
        }
        if (kind == CellTopology.GroundKind.GRASS || kind == CellTopology.GroundKind.DIRT) {
            return 31 * state + (natureFrames.get() == null ? 0 : 1);
        }
        state = 31 * state + (isWallAt(topology, x, y + 1) ? 1 : 0);
        state = 31 * state + (isWallAt(topology, x, y - 1) ? 1 : 0);
        state = 31 * state + (isWallAt(topology, x + 1, y) ? 1 : 0);
        return 31 * state + (isWallAt(topology, x - 1, y) ? 1 : 0);
    }

    static final class Sample {
        final String heightSheetPath;
        final String normalSheetPath;
        final int srcX;
        final int srcY;
        final int srcW;
        final int srcH;

        Sample(String heightSheetPath, int srcX, int srcY, int srcW, int srcH) {
            this(heightSheetPath, normalPathFromHeight(heightSheetPath), srcX, srcY, srcW, srcH);
        }

        Sample(String heightSheetPath, String normalSheetPath,
               int srcX, int srcY, int srcW, int srcH) {
            this.heightSheetPath = heightSheetPath;
            this.normalSheetPath = normalSheetPath;
            this.srcX = srcX;
            this.srcY = srcY;
            this.srcW = srcW;
            this.srcH = srcH;
        }

        private static String normalPathFromHeight(String heightSheetPath) {
            return heightSheetPath.endsWith("_height.png")
                    ? heightSheetPath.substring(0, heightSheetPath.length() - "_height.png".length())
                    + "_normal.png" : heightSheetPath + "_normal.png";
        }
    }

    private static final class CacheEntry {
        final int terrainState;
        final Sample sample;

        CacheEntry(int terrainState, Sample sample) {
            this.terrainState = terrainState;
            this.sample = sample;
        }
    }
}
