package com.dillon.starsectormarines.battle.world.tiles;

import java.util.Locale;

/**
 * What a {@link DoodadDef} contributes to prepared ground, declared in the
 * tileset's {@code "fightingPosition"} property (moddable-tilesets).
 *
 * <p>Classification is by this declared property alone, never by id prefix: a
 * new sheet of earthworks joins the fighting-position model by saying so in its
 * own data. See {@code fighting-positions.md}.
 */
public enum FightingPositionRole {
    /** Not prepared ground. */
    NONE,
    /** A dug channel: its own cell is a place to stand. */
    TRENCH,
    /** A raised line, such as a sandbag parapet: it and the standable ground either side can be manned. */
    PARAPET;

    /** Parses the JSON value; absent or blank is {@link #NONE}, anything unrecognised fails loud. */
    public static FightingPositionRole fromJson(String value) {
        if (value == null || value.isBlank()) return NONE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown doodad fightingPosition '" + value + "'", e);
        }
    }

    /** The JSON spelling, or {@code null} for {@link #NONE} (the property is then omitted). */
    public String toJson() {
        return this == NONE ? null : name().toLowerCase(Locale.ROOT);
    }
}
