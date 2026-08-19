package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;

/**
 * Prop placed on a battle-map cell — chairs, crates, chests, shelves, etc.
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
 * picking firing positions and by direct-fire accuracy resolution — a marine
 * prefers cells beside high-cover doodads (crates, rubble piles), and incoming
 * fire from the protected facing is less likely to hit. Doodads remain
 * non-blocking and do not reduce damage after a hit.
 *
 * <p>Cover is intrinsic data on the {@link DoodadDef} (moddable-tilesets Phase 2):
 * the {@link #Doodad(int, int, DoodadDef)} ctor reads it from the def, so every
 * authoring site that scatters a registered prop gets a consistent value without
 * repeating it. Marker/resolver doodads (LZ pads, embankments) that aren't defs
 * pass an explicit cover via the 5-arg ctor.
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

    /**
     * Builds a doodad from its data-driven {@link DoodadDef} (moddable-tilesets
     * Phase 2): frame from the def's source cell, cover from the def's intrinsic
     * {@link DoodadCover}, and {@link #fromRoadSheet} from whether the def lives
     * on {@link TileManifest#ROAD_SHEET}. The registry-fed prop ctor.
     */
    public Doodad(int cellX, int cellY, DoodadDef def) {
        this(cellX, cellY, new TileManifest.TileFrame(def.col, def.row),
                def.sheetPath, def.cover.level());
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile, boolean fromRoadSheet, int cover) {
        this(cellX, cellY, tile,
                fromRoadSheet ? TileManifest.ROAD_SHEET : TileManifest.SHEET,
                cover);
    }

    public Doodad(int cellX, int cellY, TileManifest.TileFrame tile, String sheetPath, int cover) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.tile = tile;
        this.sheetPath = sheetPath == null ? TileManifest.SHEET : sheetPath;
        this.fromRoadSheet = TileManifest.ROAD_SHEET.equals(this.sheetPath);
        this.cover = clamp(cover);
    }

    private static int clamp(int v) {
        if (v < COVER_NONE)  return COVER_NONE;
        if (v > COVER_HEAVY) return COVER_HEAVY;
        return v;
    }
}
