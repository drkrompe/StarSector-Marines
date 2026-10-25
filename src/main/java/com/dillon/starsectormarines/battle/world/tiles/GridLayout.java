package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Named autotile layout for a fixed-grid {@link GridBlockDef} — the geometry
 * resolver that maps a cell's 4-neighbor "exterior on this side" mask to a
 * {@code (col, row)} offset within the block. This is the code half of the
 * moddable-tilesets data/algorithm seam: the JSON supplies a block's
 * <em>origin</em> + the layout's <em>name</em>; the math stays here (a submod
 * re-points origins, it doesn't author new geometry — see
 * {@code moddable-tilesets-nouns.md}).
 *
 * <p>Each layout is the authoritative autotile geometry for its block —
 * originally ported, behavior-for-behavior, from the now-removed
 * {@code TileManifest.pickXxx} pickers.
 */
public enum GridLayout {

    /** Single fixed cell, neighbor-independent: always the block origin. */
    SINGLE {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            return new int[]{oc, or};
        }
    },

    /**
     * Facing-inward 3×3 — the decorated edge faces the wall it abuts
     * (used by {@code urban.floor} / {@code urban.rubble}). Never null.
     */
    FLOOR_3X3 {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            int col = w ? 0 : (e ? 2 : 1);
            int row = n ? 0 : (s ? 2 : 1);
            return new int[]{oc + col, or + row};
        }
    },

    /**
     * Hollow-perimeter 3×3 wall (used by {@code urban.wall}) — same offsets
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
    },

    /**
     * Sixteen full-cell wall caps, one for every exposed-face combination.
     * Bits are N=1, E=2, S=4, W=8; row-major mask order in a 4x4 patch.
     * Both sides of a thin partition may be exposed. No interior/exterior bias,
     * no floor or apron inside the wall tile, and no priority that drops a face.
     */
    WALL_FACES_4X4 {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            int mask = (n ? 1 : 0) | (e ? 2 : 0) | (s ? 4 : 0) | (w ? 8 : 0);
            return new int[]{oc + mask % 4, or + mask / 4};
        }
    },

    /**
     * Hollow-perimeter 3×3 whose <em>open</em> case is null (used by
     * {@code road.road} / {@code road.courtyard}) — same edge offsets as
     * {@link #FLOOR_3X3}, but a cell with no wall neighbor returns {@code null}
     * so the caller paints the block's {@link GridBlockDef#fillRgb} (the open
     * road/courtyard interior). The inverse null-trigger of {@link #WALL_3X3}.
     */
    PERIMETER_3X3 {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            if (!n && !s && !e && !w) return null;
            int col = w ? 0 : (e ? 2 : 1);
            int row = n ? 0 : (s ? 2 : 1);
            return new int[]{oc + col, or + row};
        }
    },

    /**
     * Sixteen full-cell ground marks addressed like {@link #WALL_FACES_4X4} (N=1, E=2, S=4,
     * W=8, row-major), but for a marked ground rather than a wall: a flag means the ground
     * ends on that side, against a wall or against any other ground. The renderer supplies
     * that boundary mask instead of the wall mask, so a painted apron is outlined wherever
     * it stops. Mask 0 is the open field.
     */
    EDGE_FACES_4X4 {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            return WALL_FACES_4X4.resolve(oc, or, n, s, e, w);
        }
    },

    /** N, E, S, W in row-major order; the opening supplies exactly one facing flag. */
    CARDINAL_2X2 {
        @Override public int[] resolve(int oc, int or, boolean n, boolean s, boolean e, boolean w) {
            int facing = n ? 0 : e ? 1 : s ? 2 : w ? 3 : 0;
            return new int[]{oc + facing % 2, or + facing / 2};
        }
    };

    /**
     * Resolves to a {@code {col, row}} cell given the block origin and the
     * four-neighbor flags (each "the exterior / a wall is on this side"), or
     * {@code null} for the enclosed/fill case.
     */
    public abstract int[] resolve(int originCol, int originRow, boolean n, boolean s, boolean e, boolean w);

    /**
     * Width and height, in cells, of the patch this layout addresses from its
     * origin — 1 for {@link #SINGLE}, 2 for cardinal openings, 4 for the complete face sets,
     * and 3 for the legacy perimeter layouts.
     *
     * <p>Authoring tools need the extent to reserve a contiguous patch in a
     * packed atlas and to know which slots a layout has. That extent is part of
     * the layout's geometry, so it is answered here rather than re-derived from
     * the layout's name somewhere else.
     */
    public int span() {
        if (this == CARDINAL_2X2) return 2;
        if (isFaces()) return 4;
        return this == SINGLE ? 1 : 3;
    }

    public boolean isWall() { return this == WALL_3X3 || this == WALL_FACES_4X4; }

    /** Whether this layout addresses sixteen exposed-face masks. */
    public boolean isFaces() { return this == WALL_FACES_4X4 || this == EDGE_FACES_4X4; }

    /** Whether a ground drawn with this layout reads its mask from where the ground ends, not from walls. */
    public boolean marksGroundEdges() { return this == EDGE_FACES_4X4; }

    /** Parse from the tileset JSON {@code layout} field. */
    public static GridLayout fromJson(String s) {
        switch (s.trim().toLowerCase()) {
            case "cardinal-2x2": return CARDINAL_2X2;
            case "single":        return SINGLE;
            case "floor-3x3":     return FLOOR_3X3;
            case "wall-3x3":      return WALL_3X3;
            case "wall-faces-4x4": return WALL_FACES_4X4;
            case "edge-faces-4x4": return EDGE_FACES_4X4;
            case "perimeter-3x3": return PERIMETER_3X3;
            default: throw new IllegalArgumentException("unknown grid layout '" + s + "'");
        }
    }
}
