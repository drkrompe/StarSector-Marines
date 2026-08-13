package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Real {@link HeightSource} — biases a cell's macro height by the average
 * texel value S1's derived {@code <sheet>_height.png} carries for the SAME
 * source rect {@link GroundRenderSystem} would draw the color tile from.
 *
 * <p><strong>Scope: the generic {@code GridBlockDef} path only.</strong>
 * {@link GroundRenderSystem} resolves most {@link CellTopology.GroundKind}s
 * through {@code drawGroundBlock} — a data-driven {@code kind -> blockId ->
 * GridBlockDef.resolve(neighborMask, x, y)} lookup this class replicates
 * (deliberately duplicated in miniature rather than hooking into the color
 * pass's 400-line dispatch, to avoid destabilizing that hot, already-shipped
 * file for a playtest-tunable effect). That covers WATER, COURTYARD, TILE,
 * STRIPED, LZ_MARKER (road sheet) and STONE, SAND, BRICK (floors sheet) — all
 * sheets S1 baked.
 *
 * <p><strong>Excluded (macro-only fallback), and why:</strong>
 * <ul>
 *   <li>Walls — {@code urban-tileset-imagegen.png} has no derived height sheet;
 *       S1 skipped it deliberately (mixed wall+floor content, see its story doc).</li>
 *   <li>{@code INDOOR}/{@code RUBBLE} — map to blocks on that same skipped sheet.</li>
 *   <li>{@code GRASS}/{@code DIRT}/{@code STREET}/{@code SIDEWALK} — special-cased
 *       in {@code GroundRenderSystem} to prefer the SLICED nature/urban3 sheets
 *       (with a floors/road fallback only when those aren't loaded). Replicating
 *       that sliced-sheet frame-pick logic here risks silently sampling the
 *       WRONG sheet for the tile actually drawn; safer to skip. <strong>Follow-up:</strong>
 *       nature-tiles/urban-tileset-3 height sheets exist (S1 baked them) but
 *       aren't wired — worth doing once this generic path proves out visually.</li>
 * </ul>
 *
 * <p><strong>Caching.</strong> A cell's resolved (kind, block, source rect) is
 * static for the life of a battle (wall→rubble demolition doesn't change this:
 * both map to the un-derived sheet, so the result is macro-only either way) —
 * {@link #combine} caches per-cell so the O(cellPx²) pixel average only runs
 * once per cell, not once per frame. First-touch cost only. The per-cell cache
 * is scoped to ONE battle: {@code BattleScreen} persists across battles, so
 * {@link GroundParallaxPipeline#dispose()} calls {@link #invalidate()} on
 * detach — grid coordinates mean different tiles on the next map. Loaded
 * height sheets survive invalidation (they're battle-independent assets).
 */
final class GroundMicroHeightSampler implements HeightSource {

    /** How far the micro sample can push the macro value, each direction. Playtest-tunable. */
    private static final float MICRO_SCALE = 0.25f;

    private static final EnumSet<CellTopology.GroundKind> SLICED_SHEET_KINDS = EnumSet.of(
            CellTopology.GroundKind.GRASS, CellTopology.GroundKind.DIRT,
            CellTopology.GroundKind.STREET, CellTopology.GroundKind.SIDEWALK,
            CellTopology.GroundKind.SNOW);

    private final Map<String, HeightSheetTexture> heightSheets = new HashMap<>();
    private final Map<Long, Float> cache = new HashMap<>();

    @Override
    public float combine(float macroHeight, CellTopology topology, int gridX, int gridY) {
        Long key = packKey(gridX, gridY);
        Float cached = cache.get(key);
        if (cached != null) return cached;

        float value = resolve(macroHeight, topology, gridX, gridY);
        cache.put(key, value);
        return value;
    }

    /** Drops the per-cell cache (NOT the loaded sheets). Call between battles — cell coords don't survive a map change. */
    void invalidate() {
        cache.clear();
    }

    private float resolve(float macroHeight, CellTopology topology, int gridX, int gridY) {
        if (topology.isWall(gridX, gridY)) return macroHeight;

        CellTopology.GroundKind kind = topology.getGroundKind(gridX, gridY);
        if (SLICED_SHEET_KINDS.contains(kind)) return macroHeight;

        GenMappingRegistry mapping = GenMappingRegistry.installed();
        TileRegistry tileReg = TileRegistry.installed();
        if (mapping == null || tileReg == null) return macroHeight;

        String blockId = mapping.groundBlockId(kind);
        if (blockId == null) return macroHeight;
        GridBlockDef block = tileReg.block(blockId);
        if (block == null) return macroHeight; // a sliced-sheet mapping (no GridBlockDef) -- shouldn't hit given the exclusion above, but stay safe

        boolean n = isWallAt(topology, gridX, gridY + 1);
        boolean s = isWallAt(topology, gridX, gridY - 1);
        boolean e = isWallAt(topology, gridX + 1, gridY);
        boolean w = isWallAt(topology, gridX - 1, gridY);
        int[] c = block.resolve(n, s, e, w, gridX, gridY);
        if (c == null) return macroHeight; // open/enclosed fill case -- no source rect to sample

        HeightSheetTexture tex = heightSheetFor(block.sheetPath);
        // Same source-rect inset drawGroundBlock/emitCellPx applies -- average only the texels the color pass actually draws.
        int inset = (block.cellPx >= TileManifest.TILE_SIZE)
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        float micro = tex.averageHeight(c[0] * block.cellPx + inset, c[1] * block.cellPx + inset,
                block.cellPx - 2 * inset, block.cellPx - 2 * inset);
        if (micro < 0f) return macroHeight; // sheet not baked (e.g. skipped by S1) or failed to load

        return macroHeight + (micro - 0.5f) * MICRO_SCALE;
    }

    private HeightSheetTexture heightSheetFor(String colorSheetPath) {
        return heightSheets.computeIfAbsent(colorSheetPath,
                p -> new HeightSheetTexture(derivedHeightPath(p)));
    }

    /** {@code graphics/tilesets/Foo.png} -> {@code graphics/tilesets/Foo_height.png} — S1's naming convention. */
    private static String derivedHeightPath(String colorSheetPath) {
        int dot = colorSheetPath.lastIndexOf('.');
        String base = dot >= 0 ? colorSheetPath.substring(0, dot) : colorSheetPath;
        return base + "_height.png";
    }

    private static boolean isWallAt(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }

    private static Long packKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
