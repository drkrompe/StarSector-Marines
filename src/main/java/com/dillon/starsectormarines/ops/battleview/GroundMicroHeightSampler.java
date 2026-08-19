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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Real {@link HeightSource} — biases a cell's macro height by the average
 * texel value S1's derived {@code <sheet>_height.png} carries for the SAME
 * source rect {@link GroundRenderSystem} draws the color tile from.
 *
 * <p>The generic {@link GridBlockDef} path and the variable-width sliced
 * nature/urban-3 sheets are both supported. Sliced sampling reuses the frame
 * tables already produced while loading the color sheets; a null frame table
 * means the color renderer took its fixed-grid fallback, so this sampler takes
 * the same fallback. Walls and blocks on S1-skipped sheets remain macro-only.
 *
 * <p>A resolved micro <em>offset</em> is cached per cell rather than the final
 * macro+micro value. This keeps a cached wall/rubble or mapping fallback from
 * freezing an old macro height if runtime terrain state changes. The cache also
 * records wall/kind state so a changed ground kind is resolved again.
 */
final class GroundMicroHeightSampler implements HeightSource {

    /** How far the micro sample can push the macro value, each direction. Playtest-tunable. */
    private static final float MICRO_SCALE = 0.25f;

    private final Supplier<SpriteSheetFrames> natureFrames;
    private final Supplier<SpriteSheetFrames> urban3Frames;
    private final Function<String, HeightSheetTexture> textureFactory;
    private final Map<String, HeightSheetTexture> heightSheets = new HashMap<>();
    private final Map<Long, CacheEntry> cache = new HashMap<>();

    GroundMicroHeightSampler(BattleSprites sprites) {
        this(sprites::natureFrames, sprites::urbanTile3Frames, HeightSheetTexture::new);
    }

    /** Package-private injection seam used by asset-backed unit tests. */
    GroundMicroHeightSampler(Supplier<SpriteSheetFrames> natureFrames,
                             Supplier<SpriteSheetFrames> urban3Frames,
                             Function<String, HeightSheetTexture> textureFactory) {
        this.natureFrames = natureFrames;
        this.urban3Frames = urban3Frames;
        this.textureFactory = textureFactory;
    }

    @Override
    public float combine(float macroHeight, NavigationGrid grid, CellTopology topology, int gridX, int gridY) {
        long key = packKey(gridX, gridY);
        CellTopology.GroundKind kind = topology.getGroundKind(gridX, gridY);
        boolean wall = topology.isWall(gridX, gridY);
        int state = terrainState(grid, topology, gridX, gridY, kind, wall);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.terrainState == state) {
            return macroHeight + cached.microOffset;
        }

        float microOffset = resolveOffset(grid, topology, gridX, gridY, wall, kind);
        cache.put(key, new CacheEntry(state, microOffset));
        return macroHeight + microOffset;
    }

    /** Drops the per-cell cache (NOT the loaded sheets). Call between battles — cell coords don't survive a map change. */
    void invalidate() {
        cache.clear();
    }

    private float resolveOffset(NavigationGrid grid, CellTopology topology, int gridX, int gridY,
                                boolean wall, CellTopology.GroundKind kind) {
        if (wall || kind == CellTopology.GroundKind.SNOW) return 0f;

        GenMappingRegistry mapping = GenMappingRegistry.installed();
        TileRegistry tileReg = TileRegistry.installed();
        if (mapping == null || tileReg == null) return 0f;

        if (kind == CellTopology.GroundKind.GRASS || kind == CellTopology.GroundKind.DIRT) {
            SpriteSheetFrames frames = natureFrames.get();
            if (frames != null) {
                Float sliced = sampleSliced(tileReg.tile(GroundTileSelector.natureTileId(kind, gridX, gridY)), frames);
                // The color pass used the sliced sheet. Missing/corrupt derived
                // data must stay macro-only, not sample a different fallback art tile.
                return sliced == null ? 0f : sliced;
            }
            // Color path falls back to the mapped Floors_Tiles block when the
            // sliced nature sheet failed to load.
            return sampleGridBlock(mapping.groundBlockId(kind), tileReg, topology, gridX, gridY);
        }

        if (kind == CellTopology.GroundKind.STREET || kind == CellTopology.GroundKind.SIDEWALK) {
            SpriteSheetFrames frames = urban3Frames.get();
            if (frames != null) {
                String streetTileId = mapping.groundBlockId(CellTopology.GroundKind.STREET);
                Float sliced = sampleSliced(tileReg.tile(GroundTileSelector.urban3TileId(
                        grid, topology, streetTileId, gridX, gridY)), frames);
                return sliced == null ? 0f : sliced;
            }
            // STREET has the road autotile fallback; explicit SIDEWALK has no
            // fixed-grid fallback in GroundRenderSystem and stays macro-only.
            return kind == CellTopology.GroundKind.STREET
                    ? sampleStreetFallback(grid, topology, tileReg, gridX, gridY)
                    : 0f;
        }

        return sampleGridBlock(mapping.groundBlockId(kind), tileReg, topology, gridX, gridY);
    }

    private float sampleStreetFallback(NavigationGrid grid, CellTopology topology,
                                       TileRegistry tileReg, int gridX, int gridY) {
        if (GroundTileSelector.isSidewalkCell(grid, topology, gridX, gridY)) {
            return sampleResolvedBlock(tileReg.block("road.sidewalk"), false, false, false, false, gridX, gridY);
        }
        return sampleResolvedBlock(tileReg.block("road.road"),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX, gridY + 1),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX, gridY - 1),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX + 1, gridY),
                GroundTileSelector.isRoadBoundary(grid, topology, gridX - 1, gridY),
                gridX, gridY);
    }

    private Float sampleSliced(TileDef tile, SpriteSheetFrames frames) {
        if (tile == null || frames == null || tile.frame < 0 || tile.frame >= frames.frames.length) return null;
        SpriteSheetFrames.Frame frame = frames.frames[tile.frame];
        int inset = tile.isGround() ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : 0;
        return sampleOffset(tile.sheetPath,
                frame.x + inset, frame.y + inset,
                Math.max(1, frame.w - 2 * inset), Math.max(1, frame.h - 2 * inset));
    }

    private float sampleGridBlock(String blockId, TileRegistry tileReg, CellTopology topology,
                                  int gridX, int gridY) {
        if (blockId == null) return 0f;
        GridBlockDef block = tileReg.block(blockId);
        if (block == null) return 0f;

        boolean n = isWallAt(topology, gridX, gridY + 1);
        boolean s = isWallAt(topology, gridX, gridY - 1);
        boolean e = isWallAt(topology, gridX + 1, gridY);
        boolean w = isWallAt(topology, gridX - 1, gridY);
        return sampleResolvedBlock(block, n, s, e, w, gridX, gridY);
    }

    private float sampleResolvedBlock(GridBlockDef block, boolean n, boolean s, boolean e, boolean w,
                                      int gridX, int gridY) {
        if (block == null) return 0f;
        int[] c = block.resolve(n, s, e, w, gridX, gridY);
        if (c == null) return 0f;

        int inset = (block.cellPx >= TileManifest.TILE_SIZE)
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        Float offset = sampleOffset(block.sheetPath,
                c[0] * block.cellPx + inset, c[1] * block.cellPx + inset,
                block.cellPx - 2 * inset, block.cellPx - 2 * inset);
        return offset == null ? 0f : offset;
    }

    private Float sampleOffset(String colorSheetPath, int srcX, int srcY, int srcW, int srcH) {
        float micro = heightSheetFor(colorSheetPath).averageHeight(srcX, srcY, srcW, srcH);
        if (micro < 0f) return null;
        return (micro - 0.5f) * MICRO_SCALE;
    }

    private HeightSheetTexture heightSheetFor(String colorSheetPath) {
        return heightSheets.computeIfAbsent(colorSheetPath,
                p -> textureFactory.apply(derivedHeightPath(p)));
    }

    /** {@code graphics/tilesets/Foo.png} -> {@code graphics/tilesets/Foo_height.png} — S1's naming convention. */
    static String derivedHeightPath(String colorSheetPath) {
        int dot = colorSheetPath.lastIndexOf('.');
        String base = dot >= 0 ? colorSheetPath.substring(0, dot) : colorSheetPath;
        return base + "_height.png";
    }

    private static boolean isWallAt(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }

    private static long packKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * Cheap mutation fingerprint for everything that can change the selected
     * source rect. Urban-3 sidewalk corners inspect neighboring sidewalk cells,
     * and those can in turn inspect their cardinal walls, hence the radius-two
     * window for STREET/SIDEWALK. Fixed-grid blocks only read cardinal walls.
     */
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

    private static final class CacheEntry {
        final int terrainState;
        final float microOffset;

        CacheEntry(int terrainState, float microOffset) {
            this.terrainState = terrainState;
            this.microOffset = microOffset;
        }
    }
}
