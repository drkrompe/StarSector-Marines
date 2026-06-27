package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Tactical cover bucket of a {@link DoodadDef} — the data-driven successor to
 * {@code Doodad.defaultCoverFor}'s hardcoded {@code (col,row)} table. The
 * {@link #level()} is on the cell-grid cover scale
 * ({@code [0..3]}, matching {@code NavigationGrid.MAX_COVER}) that
 * {@code Doodad.cover} stores and {@code TacticalScoring} reads.
 *
 * <p>Distinct from {@link TileCover} (the 2-bucket nature-overlay concealment):
 * doodad cover is the 4-level tactical scale crates / shelves / rubble sit on.
 */
public enum DoodadCover {
    /** Open — visual paint only (LZ pads, grates, markers). */
    NONE(0),
    /** Light — rubble decals, low debris. Breaks sightlines slightly. */
    LIGHT(1),
    /** Medium — crates, chests, benches, desks. Worth a sidestep to grab. */
    MED(2),
    /** Heavy — shelves, wall fragments, embankments. Best non-wall cover. */
    HEAVY(3);

    private final int level;

    DoodadCover(int level) {
        this.level = level;
    }

    /** Cover quality on the {@code [0..3]} cell-grid scale stored by {@code Doodad.cover}. */
    public int level() {
        return level;
    }

    /** Case-insensitive parse from the tileset JSON {@code cover} field ({@code none|light|med|heavy}). */
    public static DoodadCover fromJson(String s) {
        return DoodadCover.valueOf(s.trim().toUpperCase());
    }
}
