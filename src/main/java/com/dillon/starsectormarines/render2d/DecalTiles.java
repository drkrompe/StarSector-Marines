package com.dillon.starsectormarines.render2d;

import com.dillon.starsectormarines.battle.combat.fx.Decal;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;

/**
 * The rules for {@link DecalAccumulator}'s sharp view tiles, kept free of GL so they can be decided and tested
 * where they are owned.
 *
 * <p>The accumulator's map-wide surface holds every mark at a density the whole map can afford (32 px a cell at
 * most, 10-17 on a large map). Zoomed in past that density, the camera shows marks magnified and soft, so the
 * accumulator lays sharp tiles over the view instead: square blocks of {@link #TILE_CELLS} cells at
 * {@link #PX_PER_CELL}, each filled by replaying the retained marks that reach it. A tile is a presentation block
 * of the view, never a simulation grid; residency is bounded by {@link #MAX_RESIDENT}, so its memory is bounded by
 * the view rather than the map.
 */
public final class DecalTiles {

    private DecalTiles() {}

    /** Cells along a tile's side. */
    public static final int TILE_CELLS = 16;
    /** Texels per cell on a tile: sharp at the closer zooms, where a cell is drawn 64 px or more. */
    public static final int PX_PER_CELL = 64;
    /**
     * Texels round a tile's own block that it also holds, the marks of its neighbours' edges, so linear filtering at
     * a tile's border samples the next tile's pixels rather than clamping to its own.
     */
    public static final int PAD_PX = 2;
    /** Side of a tile's texture, in texels. */
    public static final int TEXTURE_PX = TILE_CELLS * PX_PER_CELL + 2 * PAD_PX;
    /**
     * Most tiles held at once: a 1920 x 1080 view where tiles start on a large map covers about 15, and each is a
     * little over 4 MB, so the tiles never hold more than about 85 MB. A view needing more draws from the map-wide
     * surface.
     */
    public static final int MAX_RESIDENT = 20;
    /** Tiles filled in one frame at most, so panning onto new ground spreads its replays over frames. */
    public static final int FILLS_PER_FRAME = 4;
    /** How much sharper than the map-wide surface the view must be drawn before tiles take over. */
    static final float ENGAGE_RATIO = 1.25f;

    /** Whether a view drawing {@code screenPxPerCell} per cell is sharper than the map-wide surface can show. */
    public static boolean engaged(float screenPxPerCell, int mapPxPerCell) {
        return mapPxPerCell > 0 && mapPxPerCell < PX_PER_CELL && screenPxPerCell > mapPxPerCell * ENGAGE_RATIO;
    }

    /** The tile holding cell column or row {@code cell}. */
    public static int tileOf(int cell) {
        return Math.floorDiv(cell, TILE_CELLS);
    }

    /** How many tiles cover the inclusive cell range {@code [min, max]} along one axis. */
    public static int span(int min, int max) {
        return tileOf(max) - tileOf(min) + 1;
    }

    /** One key per tile, for a map of resident tiles. */
    public static long key(int tileX, int tileY) {
        return ((long) tileX << 32) ^ (tileY & 0xffffffffL);
    }

    /**
     * Whether a mark drawn as {@code frame} reaches the tile {@code (tileX, tileY)}, its padding included: its
     * longest reach in any turn, half its diagonal, against the tile's padded block.
     */
    public static boolean reaches(Decal decal, SpriteSheetFrames.Frame frame, int tileX, int tileY) {
        float aspect = frame.w > 0 ? (float) frame.h / frame.w : 1f;
        float reach = 0.5f * decal.scaleCells * (float) Math.sqrt(1f + aspect * aspect);
        float pad = (float) PAD_PX / PX_PER_CELL;
        float x0 = tileX * TILE_CELLS - pad;
        float y0 = tileY * TILE_CELLS - pad;
        float x1 = (tileX + 1) * TILE_CELLS + pad;
        float y1 = (tileY + 1) * TILE_CELLS + pad;
        return decal.x + reach > x0 && decal.x - reach < x1 && decal.y + reach > y0 && decal.y - reach < y1;
    }
}
