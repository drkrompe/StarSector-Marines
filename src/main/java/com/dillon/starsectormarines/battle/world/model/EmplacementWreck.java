package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

/**
 * Swaps the art of a concrete gun emplacement ({@link TileManifest#EMPLACEMENT_BLOCK}) to a damage state. The damaged
 * and destroyed patches are blocks named for the intact one, cut on the same
 * wall-3x3 slots, so a parapet piece becomes the same slot of the other patch in place. Only the picture changes: the
 * replacement keeps the original's cover level, ballistic height and footprint.
 */
public final class EmplacementWreck {

    /** The damage states are blocks named for the intact one: no data field pairs wall-3x3 blocks, so the name does. */
    public static final String DAMAGED_SUFFIX = "-damaged";
    public static final String DESTROYED_SUFFIX = "-destroyed";

    private EmplacementWreck() {}

    /**
     * The {@code piece} as it looks in {@code state}, or {@code null} when it is not a piece of {@code intact}.
     *
     * @param intact the intact block the piece was stamped from
     * @param state  the damaged or destroyed block of the same layout
     */
    public static Doodad inState(Doodad piece, GridBlockDef intact, GridBlockDef state) {
        if (intact == null || state == null || !intact.sheetPath.equals(piece.sheetPath)) return null;
        int dc = piece.tile.col - intact.originCol;
        int dr = piece.tile.row - intact.originRow;
        if (dc < 0 || dc >= intact.layout.span() || dr < 0 || dr >= intact.layout.span()) return null;
        return new Doodad(piece.cellX, piece.cellY,
                new TileManifest.TileFrame(state.originCol + dc, state.originRow + dr), state.sheetPath,
                piece.cover, piece.ballisticHalfHeight, piece.footprintCellsX, piece.footprintCellsY,
                null, state.cellPx);
    }

    /** Shows the intact pieces within {@code radius} cells (Chebyshev) of {@code (cx, cy)} as destroyed, in place. */
    public static int destroyAround(List<Doodad> doodads, int cx, int cy, int radius) {
        return showAround(doodads, cx, cy, radius, DESTROYED_SUFFIX);
    }

    /** As {@link #destroyAround}, for the chipped and cracked, still-standing state. */
    public static int damageAround(List<Doodad> doodads, int cx, int cy, int radius) {
        return showAround(doodads, cx, cy, radius, DAMAGED_SUFFIX);
    }

    private static int showAround(List<Doodad> doodads, int cx, int cy, int radius, String suffix) {
        TileRegistry registry = TileRegistry.installed();
        GridBlockDef intact = registry == null ? null : registry.block(TileManifest.EMPLACEMENT_BLOCK);
        if (intact == null) return 0;
        GridBlockDef state = registry.block(TileManifest.EMPLACEMENT_BLOCK + suffix);
        int swapped = 0;
        for (int i = 0; i < doodads.size(); i++) {
            Doodad d = doodads.get(i);
            if (Math.abs(d.cellX - cx) > radius || Math.abs(d.cellY - cy) > radius) continue;
            Doodad shown = inState(d, intact, state);
            if (shown == null) continue;
            doodads.set(i, shown);
            swapped++;
        }
        return swapped;
    }
}
