package com.dillon.starsectormarines.battle.world.tiles;

import java.util.Locale;

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
 * <p>See {@code moddable-tilesets-nouns.md}.
 */
public final class DoodadDef {

    /**
     * Authored edge that should sit against a wall when a layout can honor it.
     * Coordinates are math-y-up: N is the high-y edge and S the low-y edge.
     */
    public enum WallSide {
        N, S, E, W;

        public static WallSide fromJson(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown doodad preferredWallSide '" + value + "'", e);
            }
        }

        public WallSide opposite() {
            switch (this) {
                case N: return S;
                case S: return N;
                case E: return W;
                case W: return E;
                default: throw new IllegalStateException();
            }
        }
    }

    public final String id;
    public final String sheetPath;
    public final int col;
    public final int row;
    public final DoodadCover cover;
    /** Symmetric target-plane catch band around Z=0, in cells. */
    public final float ballisticHalfHeight;
    public final int footprintCellsX;
    public final int footprintCellsY;
    /** Optional authored edge that naturally backs onto a wall (sofa back, bed head). */
    public final WallSide preferredWallSide;
    /**
     * Pixels per source cell on {@link #sheetPath}, or {@code 0} where the sheet
     * did not say.
     *
     * <p>Art is not all drawn at one resolution, and a sheet detailed enough to
     * be worth having is usually drawn well above the grid the game happens to
     * use. Carrying the sheet's own cell size means a finer sheet is sampled at
     * its own scale instead of being resampled down to the coarsest one in the
     * mod — which is unrecoverable, and throws away exactly the detail that made
     * the sheet worth importing.
     *
     * <p>Zero means unspecified; the consumer substitutes its default rather
     * than this class guessing on the sheet's behalf.
     */
    public final int sourceCellPx;

    public DoodadDef(String id, String sheetPath, int col, int row, DoodadCover cover) {
        this(id, sheetPath, col, row, cover,
                (cover == null ? DoodadCover.NONE : cover).defaultBallisticHalfHeight(),
                1, 1, null);
    }

    public DoodadDef(String id, String sheetPath, int col, int row,
                     DoodadCover cover, float ballisticHalfHeight) {
        this(id, sheetPath, col, row, cover, ballisticHalfHeight, 1, 1, null);
    }

    public DoodadDef(String id, String sheetPath, int col, int row,
                     DoodadCover cover, float ballisticHalfHeight,
                     int footprintCellsX, int footprintCellsY) {
        this(id, sheetPath, col, row, cover, ballisticHalfHeight,
                footprintCellsX, footprintCellsY, null);
    }

    public DoodadDef(String id, String sheetPath, int col, int row,
                     DoodadCover cover, float ballisticHalfHeight,
                     int footprintCellsX, int footprintCellsY,
                     WallSide preferredWallSide) {
        this(id, sheetPath, col, row, cover, ballisticHalfHeight,
                footprintCellsX, footprintCellsY, preferredWallSide, 0);
    }

    public DoodadDef(String id, String sheetPath, int col, int row,
                     DoodadCover cover, float ballisticHalfHeight,
                     int footprintCellsX, int footprintCellsY,
                     WallSide preferredWallSide, int sourceCellPx) {
        if (footprintCellsX <= 0 || footprintCellsY <= 0) {
            throw new IllegalArgumentException("Doodad footprint must be positive");
        }
        this.sourceCellPx = Math.max(0, sourceCellPx);
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
        this.preferredWallSide = preferredWallSide;
    }
}
