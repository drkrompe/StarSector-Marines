package com.dillon.starsectormarines.battle;

/**
 * Visual-only prop placed on a walkable cell — chairs, crates, chests, etc.
 * Drawn by {@link com.dillon.starsectormarines.ops.BattleScreen} above the floor
 * pass and below units. Does not affect navigation, line of sight, or cover.
 *
 * <p>Recorded by {@link UrbanMapGenerator} when it scatters props through hollow
 * building interiors and threaded to the sim so all rendering reads from one
 * source of truth.
 */
public final class Doodad {

    public final int cellX;
    public final int cellY;
    public final TileManifest.TileFrame tile;
    /** When true, {@link #tile} indexes into {@link TileManifest#ROAD_SHEET} instead of the main {@link TileManifest#SHEET}. Lets LZ pads and other road-sheet props live in the same list as interior doodads. */
    public final boolean fromRoadSheet;

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile) {
        this(cellX, cellY, tile, false);
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile, boolean fromRoadSheet) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.tile = tile;
        this.fromRoadSheet = fromRoadSheet;
    }
}
