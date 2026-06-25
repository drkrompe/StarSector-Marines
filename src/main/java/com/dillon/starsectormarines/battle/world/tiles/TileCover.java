package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Gameplay cover bucket of a {@link TileDef} — the registry counterpart to
 * the former {@code NatureTile.Cover}. {@link #LIGHT} reads as partial cover (fire-through
 * with an accuracy penalty, when wired); {@link #NONE} is fully open.
 */
public enum TileCover {
    NONE, LIGHT;

    /** Case-insensitive parse from the tileset JSON {@code cover} field. */
    public static TileCover fromJson(String s) {
        return TileCover.valueOf(s.trim().toUpperCase());
    }
}
