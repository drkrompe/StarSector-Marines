package com.dillon.starsectormarines.ops.battleview;

import java.util.List;

/**
 * Which ground bleeds into which, and through which of the baked feather tiles.
 *
 * <p>A boundary between two outdoor grounds is a cell edge, and drawn as one it
 * is a hard square. Feathering lets the ground that wins at a boundary — the
 * earlier in {@link #MATERIALS} — reach a short, noise-edged way into its
 * neighbour, drawn as the winner's own texture through an alpha mask. It is
 * presentation only: nothing gameplay reads is changed, and the cell is still
 * the loser's ground for every purpose but the picture.
 *
 * <p><b>Quadrants make the edge continuous.</b> The bleed is one field over the
 * whole map: alpha falls off with distance from the winner's cells, and that
 * distance is wobbled by a noise anchored in world space. Every bleed reaches
 * less than half a cell, so the field in one quarter of a cell depends only on
 * the three neighbours touching that corner — two cardinals and the diagonal
 * between them — and is one of four {@link Shape shapes}: an edge from either
 * cardinal, an inner corner from both, an outer corner from the diagonal alone.
 * The baked tile for a shape is that field evaluated over the cell, so two
 * quarters on either side of a cell boundary sample the same field at the same
 * points and meet without a step, whatever shapes they are.
 *
 * <p><b>The noise is fixed edge by edge</b>, which is what lets the field be
 * baked at all without repeating. Every cell edge carries one of two hashed
 * {@link #edgeCode colours}, and the noise along an edge is a function of its
 * colour alone; inside the cell it is a function of the four colours. A cell
 * picks the tile of its four edges, sixteen in all, and the cell across an edge
 * reads the same colour there, so the field still meets itself exactly. (A
 * noise that simply repeated every few cells showed as a zigzag along a long
 * straight field edge.) The corners are where four cells meet with no colour
 * of their own, so the noise is zero there.
 *
 * <p>The tiles are baked from the materials' own art by
 * {@code GroundFeatherBaker} into {@link #SHEET}, one {@code feather.<material>}
 * block per material, its cells in {@link #tileIndex} order.
 */
public final class GroundFeather {

    /** Control-run switch; on by default. */
    public static final String PROPERTY = "battle.render.groundFeathering";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    public static final String SHEET = "graphics/tilesets/ground-feather.png";
    public static final String BLOCK_PREFIX = "feather.";

    /**
     * Feathering ground, the one that bleeds over the other first.
     *
     * <p>A key is what the cell's floor draws from: its surface block, or for a
     * bare {@code GRASS}/{@code DIRT} cell drawn from the nature sheet,
     * {@code kind:<KIND>}. Green growth creeps over bare earth, earth spills
     * onto sand and gravel; roads, paving, interiors and walls are not here and
     * never take part.
     */
    public static final List<String> MATERIALS = List.of(
            "floors.grass", "kind:GRASS",
            "floors.dirt", "kind:DIRT", "pavement.dirt.worn",
            "floors.sand", "pavement.gravel.cracked", "floors.stone");

    /** Noise fields a tile can sit in: one per combination of its four edge colours. */
    public static final int EDGE_CODES = 16;

    /** How many winners one quarter-cell can show: lowest priority first, the highest last. */
    public static final int RANKS = 2;

    /** Quarter-cells, in sub-layer order. */
    public static final int NE = 0, SE = 1, SW = 2, NW = 3;

    /**
     * The four quarter-cells as clipped-patch vertices, normalized within the
     * cell, counter-clockwise from the south-west, north up.
     */
    static final float[][] QUADRANT_VERTICES = {
            {.5f, .5f, 1f, .5f, 1f, 1f, .5f, 1f},
            {.5f, 0f, 1f, 0f, 1f, .5f, .5f, .5f},
            {0f, 0f, .5f, 0f, .5f, .5f, 0f, .5f},
            {0f, .5f, .5f, .5f, .5f, 1f, 0f, 1f},
    };

    /**
     * What the winner's cells around a quarter-cell are, by where they stand.
     * The bake evaluates each over the whole cell; a quarter reads only its own
     * part of it.
     */
    public enum Shape {
        EDGE_N(0, 1), EDGE_E(1, 0), EDGE_S(0, -1), EDGE_W(-1, 0),
        OUTER_NE(1, 1), OUTER_SE(1, -1), OUTER_SW(-1, -1), OUTER_NW(-1, 1),
        INNER_NE(1, 1), INNER_SE(1, -1), INNER_SW(-1, -1), INNER_NW(-1, 1);

        /** The neighbour the shape leans toward. */
        public final int dx, dy;

        Shape(int dx, int dy) { this.dx = dx; this.dy = dy; }

        /**
         * The winner's cells this shape stands for, as offsets: one cardinal,
         * one diagonal, or two cardinals for an inner corner.
         */
        public int[][] sources() {
            if (ordinal() < 8) return new int[][]{{dx, dy}};
            return new int[][]{{dx, 0}, {0, dy}};
        }
    }

    private GroundFeather() {}

    /** Whether feathering is armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** Priority of a material key, 0 the strongest; -1 for ground that does not feather. */
    public static int priority(String key) {
        return key == null ? -1 : MATERIALS.indexOf(key);
    }

    /**
     * The shape a quarter-cell shows for one winner.
     *
     * @param quadrant {@link #NE}, {@link #SE}, {@link #SW} or {@link #NW}
     * @param vertical whether the cardinal above or below that corner is the winner's
     * @param horizontal whether the cardinal beside that corner is the winner's
     * @param diagonal whether the diagonal through that corner is the winner's
     * @return the shape, or null when the winner does not touch this corner
     */
    static Shape shape(int quadrant, boolean vertical, boolean horizontal, boolean diagonal) {
        int base;
        if (vertical && horizontal) base = 8;
        else if (vertical) return quadrant == NE || quadrant == NW ? Shape.EDGE_N : Shape.EDGE_S;
        else if (horizontal) return quadrant == NE || quadrant == SE ? Shape.EDGE_E : Shape.EDGE_W;
        else if (diagonal) base = 4;
        else return null;
        return Shape.values()[base + quadrant];
    }

    /** Neighbour offsets of a quarter-cell: vertical cardinal, horizontal cardinal, diagonal. */
    static int dx(int quadrant) { return quadrant == NE || quadrant == SE ? 1 : -1; }
    static int dy(int quadrant) { return quadrant == NE || quadrant == NW ? 1 : -1; }

    /**
     * Which materials bleed into a quarter-cell, weakest first.
     *
     * @param self the cell's own priority, -1 when it does not feather
     * @param vertical the priority of the cardinal above or below the corner
     * @param horizontal the priority of the cardinal beside it
     * @param diagonal the priority of the diagonal through it
     * @param out receives the winners' priorities, weakest (largest) first
     * @return how many were written, at most three
     */
    static int winners(int self, int vertical, int horizontal, int diagonal, int[] out) {
        if (self < 0) return 0;
        int n = 0;
        n = add(self, vertical, out, n);
        n = add(self, horizontal, out, n);
        n = add(self, diagonal, out, n);
        // Weakest first, so the strongest paints last and on top.
        for (int i = 1; i < n; i++) {
            int v = out[i], j = i - 1;
            while (j >= 0 && out[j] < v) { out[j + 1] = out[j]; j--; }
            out[j + 1] = v;
        }
        return n;
    }

    private static int add(int self, int candidate, int[] out, int n) {
        if (candidate < 0 || candidate >= self) return n;
        for (int i = 0; i < n; i++) if (out[i] == candidate) return n;
        out[n] = candidate;
        return n + 1;
    }

    /** Where a shape's tile for the cell at {@code (x, y)} sits in its material's block. */
    public static int tileIndex(Shape shape, int x, int y) {
        return shape.ordinal() * EDGE_CODES + edgeCode(x, y);
    }

    /**
     * The colours of a cell's four edges: north in bit 0, east 1, south 2,
     * west 3. An edge's colour belongs to the edge, so the cells on either
     * side of it read the same bit.
     */
    public static int edgeCode(int x, int y) {
        return colour(x, y + 1, 0x5BD1E995L) | colour(x + 1, y, 0x27D4EB2FL) << 1
                | colour(x, y, 0x5BD1E995L) << 2 | colour(x, y, 0x27D4EB2FL) << 3;
    }

    /** The colour of the horizontal edge under cell {@code (x, y)} or the vertical one left of it, by salt. */
    private static int colour(int x, int y, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ salt;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return (int) (h >>> 63);
    }

    /** Tiles in one material's block. */
    public static int tilesPerMaterial() {
        return Shape.values().length * EDGE_CODES;
    }
}
