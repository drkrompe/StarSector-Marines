package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Render / placement layer of a {@link TileDef}. Unifies the per-tileset
 * {@code Kind} enums the JSON registry replaces: {@link NatureTile.Kind}'s
 * {@code GROUND / PLANT_OVERLAY / ROCK_OVERLAY} map to {@link #GROUND} /
 * {@link #PLANT} / {@link #ROCK}; {@link UrbanTile3.Kind}'s {@code GROUND /
 * OVERLAY} map to {@link #GROUND} / {@link #OVERLAY}.
 *
 * <p>{@link #GROUND} is the base surface drawn under everything. {@link #PLANT}
 * and {@link #ROCK} are the nature doodad-overlays, kept distinct because their
 * placement legality and gen pools differ (plants → grass only, rocks → any
 * non-water). {@link #OVERLAY} is the generic prop layer with no built-in
 * placement constraint.
 */
public enum TileLayer {
    GROUND, PLANT, ROCK, OVERLAY;

    public boolean isGround()  { return this == GROUND; }
    /** True for any overlay layer — anything drawn on top of a ground tile. */
    public boolean isOverlay() { return this != GROUND; }

    /** Case-insensitive parse from the tileset JSON {@code layer} field. */
    public static TileLayer fromJson(String s) {
        return TileLayer.valueOf(s.trim().toUpperCase());
    }
}
