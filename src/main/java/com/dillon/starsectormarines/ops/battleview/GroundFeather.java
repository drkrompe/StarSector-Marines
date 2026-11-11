package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
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
     * never take part. Snow is last: lying snow on a tundra is a patch the ground around it frays into, as
     * earth's is ({@code LivingWorld#TUNDRA}), and on a frozen world the bare ground it surrounds frays out over it.
     */
    public static final List<String> MATERIALS = List.of(
            // A fallen hull's trench, freshly ploughed: its earth frays out over the lip and whatever it cut through.
            "fallen-hull.trench",
            "floors.grass", "kind:GRASS",
            "floors.dirt", "kind:DIRT", "pavement.dirt.worn",
            "floors.sand", "pavement.gravel.cracked", "floors.stone", "floors.snow",
            // A toxic world's own ground (SAND and STONE there): its sulphur flats spill onto the stained rock.
            "toxic-ground.sulphur", "toxic-ground.crust",
            // A volcanic world's own ground: its ash spills onto the basalt.
            "volcanic.basalt", "volcanic.ash",
            // A ruins hall's coverings (COVERINGS), the wet floor weakest: growth and ice lie over it.
            "ruins-ground.biofilm", "ruins-ground.ice-sheet", "ruins-ground.rime", "ruins-ground.wet-floor");

    /**
     * Coverings over an indoor floor: ice, rime, biofilm and water lying on a hall's own plate and concrete
     * ({@code ruins-expedition.md}, Themed halls are environments). Besides feathering into other materials, a
     * covering bleeds onto an indoor floor that is not a material at all ({@link #FLOOR}), so it ends in a soft edge
     * rather than the cell's step. The world's ground a dead site's weather blows in through a ruin's mouths and banks
     * along its corridors ({@code RuinsWeather}: sand, snow, basalt, ash, sulphur and crust) covers its floor the same
     * way. Nothing else bleeds onto such a floor, so paving and interiors elsewhere keep their edges (but for a
     * quarter-cell of a world's drift across a doorway).
     */
    public static final List<String> COVERINGS = List.of(
            "ruins-ground.biofilm", "ruins-ground.ice-sheet", "ruins-ground.rime", "ruins-ground.wet-floor",
            "floors.sand", "floors.snow", "volcanic.basalt", "volcanic.ash", "toxic-ground.sulphur", "toxic-ground.crust");

    /** The priority of an indoor floor that is no material: weaker than every one, and taking only coverings. */
    public static final int FLOOR = MATERIALS.size();

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
     * @param self the cell's own priority, -1 when it does not feather, {@link #FLOOR} for an indoor floor
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
        if (self == FLOOR && !COVERINGS.contains(MATERIALS.get(candidate))) return n;
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

    /**
     * Materials whose outline is smoothed, strongest first: the cells along it are cut to a contour through the share
     * of the ground around each cell corner that is this material or stronger ({@link #smoothCorners},
     * {@link #contour}), so a staircase of whole-cell steps, and the square corners of a run laid as rectangles, read
     * as a curve. The fallen hull's trench is laid cell by cell along the hull's slide, and its trodden lip with it;
     * ploughed earth has no steps. Only a map the first lies on is smoothed, so trodden earth elsewhere keeps its cells.
     */
    public static final List<String> SMOOTHED = List.of("fallen-hull.trench", "pavement.dirt.worn");

    /**
     * Cells either side of a corner each smoothed material's share is taken over: three rounds the trench's corners
     * over about three cells; one keeps a trodden path a cell wide.
     */
    private static final int[] SMOOTH_RADII = {3, 1};

    /** The radius {@link #SMOOTHED}'s {@code index}th material is smoothed over. */
    public static int smoothRadius(int index) {
        return SMOOTH_RADII[index];
    }

    /** A cell of the outlined material, for {@link #smoothCorners}. */
    public static final byte INSIDE = 1;
    /** A cell of other ground the outline may take or give back. */
    public static final byte OUTSIDE = 0;
    /** A cell that takes no part (a wall, a hull, paving): counted neither way. */
    public static final byte NEUTRAL = -1;

    /**
     * Each cell corner's share of the outlined material among the cells that take part within {@code radius} cells of
     * it, {@code (w + 1) * (h + 1)} values row by row from the south-west; zero where no cell takes part. A straight
     * edge's corners hold exactly one half, so the contour lies on it and only steps and corners move.
     */
    public static float[] smoothCorners(byte[] cells, int w, int h, int radius) {
        int[] inside = new int[(w + 1) * (h + 1)], counted = new int[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            int rowIn = 0, rowCounted = 0;
            for (int x = 0; x < w; x++) {
                byte c = cells[y * w + x];
                if (c == INSIDE) rowIn++;
                if (c != NEUTRAL) rowCounted++;
                int at = (y + 1) * (w + 1) + x + 1, below = y * (w + 1) + x + 1;
                inside[at] = inside[below] + rowIn;
                counted[at] = counted[below] + rowCounted;
            }
        }
        float[] out = new float[(w + 1) * (h + 1)];
        for (int cy = 0; cy <= h; cy++) {
            int y0 = Math.max(0, cy - radius), y1 = Math.min(h, cy + radius);
            for (int cx = 0; cx <= w; cx++) {
                int x0 = Math.max(0, cx - radius), x1 = Math.min(w, cx + radius);
                int n = box(counted, w, x0, y0, x1, y1);
                out[cy * (w + 1) + cx] = n == 0 ? 0f : box(inside, w, x0, y0, x1, y1) / (float) n;
            }
        }
        return out;
    }

    /** The sum over cells {@code [x0, x1) x [y0, y1)} of a summed-area table {@code w + 1} wide. */
    private static int box(int[] table, int w, int x0, int y0, int x1, int y1) {
        int stride = w + 1;
        return table[y1 * stride + x1] - table[y0 * stride + x1] - table[y1 * stride + x0] + table[y0 * stride + x0];
    }

    /**
     * The part of a cell on one side of the contour through its corners' shares at one half, as cell-space quads (a
     * triangle repeats its last vertex), the corners given from the south-west counter-clockwise: the side holding
     * the material when {@code inside}, the other otherwise. Empty when that side has no area.
     */
    public static List<float[]> contour(float sw, float se, float ne, float nw, boolean inside) {
        float[] value = {sw, se, ne, nw};
        float[][] corner = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};
        float[] poly = new float[16];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            boolean in = value[i] >= .5f == inside, nextIn = value[j] >= .5f == inside;
            if (in) n = add(poly, n, corner[i][0], corner[i][1]);
            if (in != nextIn) {
                float t = (.5f - value[i]) / (value[j] - value[i]);
                n = add(poly, n, corner[i][0] + t * (corner[j][0] - corner[i][0]),
                        corner[i][1] + t * (corner[j][1] - corner[i][1]));
            }
        }
        if (n > 1 && poly[0] == poly[2 * (n - 1)] && poly[1] == poly[2 * (n - 1) + 1]) n--;
        float area = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += poly[2 * i] * poly[2 * j + 1] - poly[2 * j] * poly[2 * i + 1];
        }
        List<float[]> out = new ArrayList<>(2);
        if (n < 3 || area < 1e-3f) return out;
        // A fan from the first vertex, four at a time: the side of one straight cut is convex.
        for (int start = 1; start < n - 1; start += 2) {
            float[] quad = new float[8];
            for (int k = 0; k < 4; k++) {
                int v = k == 0 ? 0 : Math.min(start + k - 1, n - 1);
                quad[2 * k] = poly[2 * v];
                quad[2 * k + 1] = poly[2 * v + 1];
            }
            out.add(quad);
        }
        return out;
    }

    /** Appends a vertex unless it repeats the last. */
    private static int add(float[] poly, int n, float x, float y) {
        if (n > 0 && poly[2 * n - 2] == x && poly[2 * n - 1] == y) return n;
        poly[2 * n] = x;
        poly[2 * n + 1] = y;
        return n + 1;
    }
}
