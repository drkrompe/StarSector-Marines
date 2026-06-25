package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Named autotile layout for a fixed-grid {@link GridBlockDef} — the geometry
 * resolver that maps a cell's 4-neighbor "exterior on this side" mask to a
 * {@code (col, row)} offset within the block. This is the code half of the
 * moddable-tilesets data/algorithm seam: the JSON supplies a block's
 * <em>origin</em> + the layout's <em>name</em>; the math stays here (a submod
 * re-points origins, it doesn't author new geometry — see
 * {@code roadmap/moddable-tilesets/overview.md}).
 *
 * <p>Faithful ports of the corresponding {@code TileManifest.pickXxxTile}
 * resolvers; {@code GridBlockParityTest} pins each to its origin so the
 * migration off the hardcoded pickers is behavior-preserving.
 */
public enum GridLayout {

    /** Single fixed cell, neighbor-independent — always the block origin. */
    SINGLE {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            return new int[]{oc, or};
        }
    },

    /**
     * Facing-inward 3×3 — the decorated edge faces the wall it abuts
     * (port of {@code pickFloorTile} / {@code pickRubbleTile}). Never null.
     */
    FLOOR_3X3 {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            int col = w ? 0 : (e ? 2 : 1);
            int row = n ? 0 : (s ? 2 : 1);
            return new int[]{oc + col, or + row};
        }
    },

    /**
     * Hollow-perimeter 3×3 wall (port of {@code pickWallTile}) — same offsets
     * as {@link #FLOOR_3X3} but the fully-enclosed case (all four neighbors are
     * walls) returns {@code null}, so the caller paints the block's
     * {@link GridBlockDef#fillRgb} (the source center cell is transparent).
     */
    WALL_3X3 {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            if (n && s && e && w) return null;
            int col = w ? 0 : (e ? 2 : 1);
            int row = n ? 0 : (s ? 2 : 1);
            return new int[]{oc + col, or + row};
        }
    };

    /**
     * Resolves to a {@code {col, row}} cell given the block origin and the
     * four-neighbor flags (each "the exterior / a wall is on this side"), or
     * {@code null} for the enclosed/fill case.
     */
    public abstract int[] resolve(int originCol, int originRow, boolean n, boolean s, boolean e, boolean w);

    /** Parse from the tileset JSON {@code layout} field. */
    public static GridLayout fromJson(String s) {
        switch (s.trim().toLowerCase()) {
            case "single":    return SINGLE;
            case "floor-3x3": return FLOOR_3X3;
            case "wall-3x3":  return WALL_3X3;
            default: throw new IllegalArgumentException("unknown grid layout '" + s + "'");
        }
    }
}
