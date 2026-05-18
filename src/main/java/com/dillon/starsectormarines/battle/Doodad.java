package com.dillon.starsectormarines.battle;

/**
 * Visual-only prop placed on a walkable cell — chairs, crates, chests, etc.
 * Drawn by {@link com.dillon.starsectormarines.ops.BattleScreen} above the floor
 * pass and below units. Does not affect navigation or line of sight.
 *
 * <p>Recorded by {@link UrbanMapGenerator} when it scatters props through hollow
 * building interiors and threaded to the sim so all rendering reads from one
 * source of truth.
 *
 * <p><b>Cover.</b> Each doodad carries a {@link #cover} quality in
 * {@code [0..3]} matching the cell-grid cover scale ({@link com.dillon.starsectormarines.battle.nav.NavigationGrid#MAX_COVER}).
 * Read by {@link com.dillon.starsectormarines.battle.ai.TacticalScoring} when
 * picking firing positions — a marine prefers cells with high-cover doodads
 * (crates, rubble piles) over plain interior tiles. <em>Not</em> consumed by
 * {@link BattleSimulation#fireShot}: the damage-reduction lookup still reads
 * the cell-grid cover only. Doodad cover is a planner-side hint that augments
 * but doesn't override the existing cell model.
 *
 * <p>Cover defaults are inferred from the source tile via
 * {@link #defaultCoverFor(TileManifest.TileFrame)} — concrete authoring sites
 * (mapgen fillers) don't have to repeat the literal value per tile kind.
 */
public final class Doodad {

    /** Open ground — empty interaction with cover scoring. */
    public static final int COVER_NONE  = 0;
    /** Light cover — bushes, decals, low debris. Cosmetic but not concealing. */
    public static final int COVER_LIGHT = 1;
    /** Medium cover — crates, chests, benches. Worth a sidestep to grab. */
    public static final int COVER_MED   = 2;
    /** Heavy cover — shelves, wall fragments, rubble piles. Best non-wall cover available. */
    public static final int COVER_HEAVY = 3;

    public final int cellX;
    public final int cellY;
    public final TileManifest.TileFrame tile;
    /** When true, {@link #tile} indexes into {@link TileManifest#ROAD_SHEET} instead of the main {@link TileManifest#SHEET}. Lets LZ pads and other road-sheet props live in the same list as interior doodads. */
    public final boolean fromRoadSheet;
    /** Cover quality 0..3. Stored so {@link TacticalScoring}-style queries don't need to re-derive from {@link #tile} per call. */
    public final int cover;

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile) {
        this(cellX, cellY, tile, false, defaultCoverFor(tile));
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile, boolean fromRoadSheet) {
        this(cellX, cellY, tile, fromRoadSheet, defaultCoverFor(tile));
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile, boolean fromRoadSheet, int cover) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.tile = tile;
        this.fromRoadSheet = fromRoadSheet;
        this.cover = clamp(cover);
    }

    private static int clamp(int v) {
        if (v < COVER_NONE)  return COVER_NONE;
        if (v > COVER_HEAVY) return COVER_HEAVY;
        return v;
    }

    /**
     * Maps a doodad source tile to a sensible cover value. Centralized here so
     * every authoring site reads the same defaults; per-site overrides can pass
     * an explicit cover via the 5-arg constructor when the situation demands it
     * (e.g., a "rubble pile" tile reused as a decorative bench in one filler).
     *
     * <p>Categories (sheet positions reference urban-tileset.png):
     * <ul>
     *   <li><b>Heavy (3)</b> — shelves and damaged-shelf doodads (row 7 cols 4-5).
     *       Tall and dense; reads as full-cover terrain piece.</li>
     *   <li><b>Medium (2)</b> — crates, chests, desks, benches, stools, marker
     *       panels — anything a soldier could realistically duck behind.</li>
     *   <li><b>Light (1)</b> — rubble decals (row 7 cols 0-3). Small piles that
     *       break up sightlines slightly without offering real concealment.</li>
     *   <li><b>None (0)</b> — LZ pads, grates, fl-tile markers. Visual paint
     *       only.</li>
     * </ul>
     */
    public static int defaultCoverFor(TileManifest.TileFrame tile) {
        if (tile == null) return COVER_NONE;
        final int c = tile.col;
        final int r = tile.row;

        // Row 7: damaged props + rubble decals.
        if (r == 7) {
            if (c >= 0 && c <= 3) return COVER_LIGHT; // rubble decals
            if (c == 4 || c == 5) return COVER_HEAVY; // damaged shelves
            if (c == 6) return COVER_MED;             // bench / desk
            if (c == 7) return COVER_MED;             // chest / box-dam
            if (c == 8 || c == 9) return COVER_MED;   // chairs / stools
            return COVER_LIGHT;
        }
        // Row 1: tan + amber crate doodads (cols 8-9).
        if (r == 1 && (c == 8 || c == 9)) return COVER_MED;
        // Row 3: gold + green crates (cols 3-4).
        if (r == 3 && (c == 3 || c == 4)) return COVER_MED;
        // Row 2 col 6: closed-door panel reused as decorative prop. Reads as a
        // standing fixture you can duck behind.
        if (r == 2 && c == 6) return COVER_MED;
        // Row 2 col 16: LZ grate / pad — flat decal, no cover.
        if (r == 2 && c == 16) return COVER_NONE;
        return COVER_NONE;
    }
}
