package com.dillon.starsectormarines.battle.world.tiles;

/**
 * One decorative prop's authoritative definition, loaded from a
 * {@code *.tileset.json} {@code "doodads"} array into the {@link TileRegistry}
 * and addressed by its stable string {@link #id}. The data half of the
 * moddable-tilesets Phase 2 doodad migration: it replaces the per-prop
 * {@code (col,row)} frames the {@code TileManifest} doodad pools hardcoded and
 * the cover the {@code Doodad.defaultCoverFor} table derived.
 *
 * <p>A doodad begins at source cell ({@link #col},{@link #row} on
 * {@link #sheetPath}) and may span {@link #footprintCellsX} by
 * {@link #footprintCellsY} cells. The same span is its rendered and tactical
 * world footprint. Intrinsic {@link #cover} and symmetric
 * {@link #ballisticHalfHeight} apply to every occupied cell.
 * Gen scatters them by id; which ids go in which pool is the
 * {@code GenMappingRegistry}'s concern, not this def's.
 *
 * <p>See {@code roadmap/moddable-tilesets/stories/phase-2-doodad-pools.md}.
 */
public final class DoodadDef {

    public final String id;
    public final String sheetPath;
    public final int col;
    public final int row;
    public final DoodadCover cover;
    /** Symmetric target-plane catch band around Z=0, in cells. */
    public final float ballisticHalfHeight;
    public final int footprintCellsX;
    public final int footprintCellsY;

    public DoodadDef(String id, String sheetPath, int col, int row, DoodadCover cover) {
        this(id, sheetPath, col, row, cover,
                (cover == null ? DoodadCover.NONE : cover).defaultBallisticHalfHeight(), 1, 1);
    }

    public DoodadDef(String id, String sheetPath, int col, int row,
                     DoodadCover cover, float ballisticHalfHeight) {
        this(id, sheetPath, col, row, cover, ballisticHalfHeight, 1, 1);
    }

    public DoodadDef(String id, String sheetPath, int col, int row,
                     DoodadCover cover, float ballisticHalfHeight,
                     int footprintCellsX, int footprintCellsY) {
        if (footprintCellsX <= 0 || footprintCellsY <= 0) {
            throw new IllegalArgumentException("Doodad footprint must be positive");
        }
        this.id = id;
        this.sheetPath = sheetPath;
        this.col = col;
        this.row = row;
        this.cover = cover == null ? DoodadCover.NONE : cover;
        this.ballisticHalfHeight = Float.isFinite(ballisticHalfHeight)
                ? Math.max(0f, ballisticHalfHeight)
                : 0f;
        this.footprintCellsX = footprintCellsX;
        this.footprintCellsY = footprintCellsY;
    }
}
