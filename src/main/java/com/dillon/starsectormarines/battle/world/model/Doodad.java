package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef.WallSide;

/**
 * Prop placed on one or more battle-map cells — chairs, crates, beds, etc.
 * Drawn by {@link com.dillon.starsectormarines.ops.BattleScreen} above the floor
 * pass and below units. Does not affect navigation or line of sight.
 *
 * <p>Recorded by {@code UrbanMapGenerator} when it scatters props through hollow
 * building interiors and threaded to the sim so all rendering reads from one
 * source of truth.
 *
 * Most are visual-only and sit on walkable cells; generators may pair a prop
 * with an explicit non-walkable topology footprint (for example a commercial
 * shelf {@link CellTopology.Tag#FIXTURE}) when it must shape navigation.
 *
 * <p><b>Cover.</b> Each doodad carries a {@link #cover} quality in
 * {@code [0..3]} matching the cell-grid cover scale ({@link com.dillon.starsectormarines.battle.nav.NavigationGrid#MAX_COVER}).
 * Read by {@link com.dillon.starsectormarines.battle.decision.TacticalScoring} when
 * picking firing positions and by direct-fire resolution — a marine prefers
 * cells beside high-cover doodads (crates, rubble piles), while a round crossing
 * the prop's own cell may be intercepted when its Z lies within the authored
 * {@link #ballisticHalfHeight}. Doodads do not affect line of sight or reduce
 * damage after a hit.
 *
 * <p>Cover is intrinsic data on the {@link DoodadDef} (moddable-tilesets Phase 2):
 * the {@link #Doodad(int, int, DoodadDef)} ctor reads it from the def, so every
 * authoring site that scatters a registered prop gets a consistent value without
 * repeating it. Marker/resolver doodads (LZ pads, embankments) that aren't defs
 * pass explicit cover and may optionally provide a ballistic half-height.
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
    /** Texture containing {@link #tile}. Registry doodads retain their authored sheet path. */
    public final String sheetPath;
    /** Back-compat view used by older previews/tests; new rendering dispatches by {@link #sheetPath}. */
    public final boolean fromRoadSheet;
    /** Cover quality 0..3. Stored so {@code TacticalScoring}-style queries don't need to re-derive from {@link #tile} per call. */
    public final int cover;
    /** Symmetric target-plane catch band around Z=0, in cells. */
    public final float ballisticHalfHeight;
    /** Rendered, navigational, cover, and ballistic footprint from the anchor cell. */
    public final int footprintCellsX;
    public final int footprintCellsY;
    /** Unrotated source span on the sprite sheet. */
    public final int sourceCellsX;
    public final int sourceCellsY;
    /** Placement rotation in normalized quarter turns. */
    public final int quarterTurns;
    /** Optional authored edge intended to sit against a wall. */
    public final WallSide preferredWallSide;
    /**
     * Pixels per source cell on {@link #sheetPath} — the scale this prop's art
     * is drawn at, which is not necessarily the scale the game's own grid uses.
     * Rendering reads its source rectangle at this size, so a sheet drawn finer
     * than {@link TileManifest#TILE_SIZE} keeps its detail instead of being
     * flattened to the coarsest sheet in the mod.
     */
    public final int sourceCellPx;

    /**
     * Builds a doodad from its data-driven {@link DoodadDef} (moddable-tilesets
     * Phase 2): frame from the def's source cell, cover from the def's intrinsic
     * {@link DoodadCover}, and {@link #fromRoadSheet} from whether the def lives
     * on {@link TileManifest#ROAD_SHEET}. The registry-fed prop ctor.
     */
    public Doodad(int cellX, int cellY, DoodadDef def) {
        this(cellX, cellY, def, 0);
    }

    /** Builds one placed orientation without requiring duplicate asset definitions. */
    public Doodad(int cellX, int cellY, DoodadDef def, int quarterTurns) {
        this(cellX, cellY, new TileManifest.TileFrame(def.col, def.row),
                def.sheetPath, def.cover.level(), def.ballisticHalfHeight,
                def.footprintCellsX, def.footprintCellsY,
                rotatedWidth(def.footprintCellsX, def.footprintCellsY, quarterTurns),
                rotatedHeight(def.footprintCellsX, def.footprintCellsY, quarterTurns),
                rotateSide(def.preferredWallSide, quarterTurns),
                def.sourceCellPx, normalizeTurns(quarterTurns));
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile, boolean fromRoadSheet, int cover) {
        this(cellX, cellY, tile,
                fromRoadSheet ? TileManifest.ROAD_SHEET : TileManifest.SHEET,
                cover);
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile,
                  boolean fromRoadSheet, int cover, float ballisticHalfHeight) {
        this(cellX, cellY, tile,
                fromRoadSheet ? TileManifest.ROAD_SHEET : TileManifest.SHEET,
                cover, ballisticHalfHeight);
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile, String sheetPath, int cover) {
        this(cellX, cellY, tile, sheetPath, cover,
                DoodadCover.fromLevel(cover).defaultBallisticHalfHeight());
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile,
                  String sheetPath, int cover, float ballisticHalfHeight) {
        this(cellX, cellY, tile, sheetPath, cover, ballisticHalfHeight, 1, 1);
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile,
                  String sheetPath, int cover, float ballisticHalfHeight,
                  int footprintCellsX, int footprintCellsY) {
        this(cellX, cellY, tile, sheetPath, cover, ballisticHalfHeight,
                footprintCellsX, footprintCellsY, null);
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile,
                  String sheetPath, int cover, float ballisticHalfHeight,
                  int footprintCellsX, int footprintCellsY,
                  WallSide preferredWallSide) {
        this(cellX, cellY, tile, sheetPath, cover, ballisticHalfHeight,
                footprintCellsX, footprintCellsY, preferredWallSide, 0);
    }

    /**
     * @param sourceCellPx pixels per source cell on {@code sheetPath}, or 0 to
     *     read the sheet at the game's own {@link TileManifest#TILE_SIZE}
     */
    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile,
                  String sheetPath, int cover, float ballisticHalfHeight,
                  int footprintCellsX, int footprintCellsY,
                  WallSide preferredWallSide, int sourceCellPx) {
        this(cellX, cellY, tile, sheetPath, cover, ballisticHalfHeight,
                footprintCellsX, footprintCellsY,
                footprintCellsX, footprintCellsY,
                preferredWallSide, sourceCellPx, 0);
    }

    private Doodad(int cellX, int cellY, TileManifest.TileFrame tile,
                   String sheetPath, int cover, float ballisticHalfHeight,
                   int sourceCellsX, int sourceCellsY,
                   int footprintCellsX, int footprintCellsY,
                   WallSide preferredWallSide, int sourceCellPx,
                   int quarterTurns) {
        if (footprintCellsX <= 0 || footprintCellsY <= 0) {
            throw new IllegalArgumentException("Doodad footprint must be positive");
        }
        this.sourceCellPx = sourceCellPx > 0 ? sourceCellPx : TileManifest.TILE_SIZE;
        this.cellX = cellX;
        this.cellY = cellY;
        this.tile = tile;
        this.sheetPath = sheetPath == null ? TileManifest.SHEET : sheetPath;
        this.fromRoadSheet = TileManifest.ROAD_SHEET.equals(this.sheetPath);
        this.cover = clamp(cover);
        this.ballisticHalfHeight = Float.isFinite(ballisticHalfHeight)
                ? Math.max(0f, ballisticHalfHeight)
                : 0f;
        this.sourceCellsX = sourceCellsX;
        this.sourceCellsY = sourceCellsY;
        this.footprintCellsX = footprintCellsX;
        this.footprintCellsY = footprintCellsY;
        this.preferredWallSide = preferredWallSide;
        this.quarterTurns = quarterTurns;
    }

    public boolean occupiesCell(int x, int y) {
        return x >= cellX && x < cellX + footprintCellsX
                && y >= cellY && y < cellY + footprintCellsY;
    }

    private static int clamp(int v) {
        if (v < COVER_NONE)  return COVER_NONE;
        if (v > COVER_HEAVY) return COVER_HEAVY;
        return v;
    }

    private static int normalizeTurns(int quarterTurns) {
        return Math.floorMod(quarterTurns, 4);
    }

    private static int rotatedWidth(int width, int height, int quarterTurns) {
        return (normalizeTurns(quarterTurns) & 1) == 0 ? width : height;
    }

    private static int rotatedHeight(int width, int height, int quarterTurns) {
        return (normalizeTurns(quarterTurns) & 1) == 0 ? height : width;
    }

    private static WallSide rotateSide(WallSide side, int quarterTurns) {
        if (side == null) return null;
        WallSide rotated = side;
        for (int turn = 0; turn < normalizeTurns(quarterTurns); turn++) {
            switch (rotated) {
                case N: rotated = WallSide.W; break;
                case W: rotated = WallSide.S; break;
                case S: rotated = WallSide.E; break;
                case E: rotated = WallSide.N; break;
                default: throw new IllegalStateException();
            }
        }
        return rotated;
    }
}
