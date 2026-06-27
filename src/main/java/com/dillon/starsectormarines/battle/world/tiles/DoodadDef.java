package com.dillon.starsectormarines.battle.world.tiles;

/**
 * One decorative prop's authoritative definition, loaded from a
 * {@code *.tileset.json} {@code "doodads"} array into the {@link TileRegistry}
 * and addressed by its stable string {@link #id}. The data half of the
 * moddable-tilesets Phase 2 doodad migration: it replaces the per-prop
 * {@code (col,row)} frames the {@code TileManifest} doodad pools hardcoded and
 * the cover the {@code Doodad.defaultCoverFor} table derived.
 *
 * <p>A doodad is a single source cell ({@link #col},{@link #row} on
 * {@link #sheetPath}) plus an intrinsic tactical {@link #cover} — the prop's
 * cover is the same wherever a filler scatters it (a crate is medium cover in a
 * shop or a warehouse alike). Gen scatters them by id; which ids go in which
 * pool is the {@code GenMappingRegistry}'s concern, not this def's.
 *
 * <p>See {@code roadmap/moddable-tilesets/stories/phase-2-doodad-pools.md}.
 */
public final class DoodadDef {

    public final String id;
    public final String sheetPath;
    public final int col;
    public final int row;
    public final DoodadCover cover;

    public DoodadDef(String id, String sheetPath, int col, int row, DoodadCover cover) {
        this.id = id;
        this.sheetPath = sheetPath;
        this.col = col;
        this.row = row;
        this.cover = cover == null ? DoodadCover.NONE : cover;
    }
}
