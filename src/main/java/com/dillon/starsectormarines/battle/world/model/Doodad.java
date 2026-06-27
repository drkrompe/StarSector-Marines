package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;

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
 * Read by {@link com.dillon.starsectormarines.battle.decision.TacticalScoring} when
 * picking firing positions — a marine prefers cells with high-cover doodads
 * (crates, rubble piles) over plain interior tiles. <em>Not</em> consumed by
 * {@link BattleSimulation#fireShot}: the damage-reduction lookup still reads
 * the cell-grid cover only. Doodad cover is a planner-side hint that augments
 * but doesn't override the existing cell model.
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
    /** When true, {@link #tile} indexes into {@link TileManifest#ROAD_SHEET} instead of the main {@link TileManifest#SHEET}. Lets LZ pads and other road-sheet props live in the same list as interior doodads. */
    public final boolean fromRoadSheet;
    /** Cover quality 0..3. Stored so {@link TacticalScoring}-style queries don't need to re-derive from {@link #tile} per call. */
    public final int cover;

    /**
     * Builds a doodad from its data-driven {@link DoodadDef} (moddable-tilesets
     * Phase 2): frame from the def's source cell, cover from the def's intrinsic
     * {@link DoodadCover}, and {@link #fromRoadSheet} from whether the def lives
     * on {@link TileManifest#ROAD_SHEET}. The registry-fed prop ctor.
     */
    public Doodad(int cellX, int cellY, DoodadDef def) {
        this(cellX, cellY, new TileManifest.TileFrame(def.col, def.row),
                TileManifest.ROAD_SHEET.equals(def.sheetPath), def.cover.level());
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
}
