package com.dillon.starsectormarines.ops.battleview;

/**
 * Rounded wall corners: which corners of a cell are drawn round, and the pieces that draw them.
 *
 * <p>Presentation only. A wall cell stays a whole impassable square; what changes is the art
 * over it. The rule is decided at a corner of the grid, where four cells meet, from what those
 * four cells are:
 * <ul>
 *   <li>An <b>outside corner</b> is one wall cell with open ground on its two sides and on the
 *       diagonal. The wall cell draws that quarter as ground, then its own tile bent round a
 *       quarter-circle, so the two faces the tile already has meet on the arc.</li>
 *   <li>An <b>inside corner</b> is one open cell with walls on its two sides and on the
 *       diagonal. The open cell lays a fillet of wall into its corner: wall cap where the two
 *       straight face trims used to meet, and a band of the face's own trim along the arc. The
 *       band reaches into the two wall cells beside it and ends where their straight trims carry
 *       on, so the trim runs unbroken round the corner.</li>
 * </ul>
 *
 * <p>The arc has the radius of half a cell, centred on the cell's middle, so every piece of a
 * corner stays in the quarter of the cell, and the strip of its neighbours, that touches that
 * corner. Two corners of one cell never share a piece; a lone pillar is a disc and a wall's
 * free end a half-disc.
 *
 * <p>Pieces are one 4-vertex patch each (a triangle repeats its last vertex), positioned in the
 * owning cell's frame and textured through explicit tile coordinates: positions in the first
 * eight floats, tile UVs in the last eight ({@link GroundMesh.CellSink#patch}). The geometry is
 * worked out once, for the north-east quarter, and mirrored into the others.
 */
final class WallCorners {

    /**
     * Experiment switch, on by default; {@code false} draws every wall corner square as before.
     * Remove it with the losing path once the owner has judged rounded corners against square
     * ones in look shots.
     */
    static final String PROPERTY = "battle.render.roundedWallCorners";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    static boolean enabled() {
        return ENABLED;
    }

    /**
     * Sub-layers the corners draw in: one piece per cell per layer. Twenty holds four outside
     * corners (a pillar) or two inside corners (a dead end); a cell with more drops the extra
     * corners and keeps them square.
     */
    static final int LAYERS = 20;

    /** How deep into a wall, from its face, the trim band bends round an inside corner. */
    static final float FACE_DEPTH = 0.19f;

    private static final int SEGMENTS = 4;
    private static final float RADIUS = 0.5f;

    public static final int NE = 0, SE = 1, SW = 2, NW = 3;
    static final int[] SX = {1, 1, -1, -1};
    static final int[] SY = {1, -1, -1, 1};

    /** What the corner rule asks of the grid. */
    interface Cells {
        /** In bounds and drawn as plain wall (not a window). */
        boolean wall(int x, int y);

        /** In bounds and open ground (not a wall, window or doorway). */
        boolean open(int x, int y);
    }

    /** Which art a piece samples. */
    enum Source {
        /** The ground of the open neighbour across the quarter's x side. */
        GROUND,
        /** The cell's own wall tile. */
        OWN,
        /** The plain cap of the wall across the quarter's y side. */
        CAP,
        /** The face of the wall across the quarter's y side that looks at this cell. */
        FACE
    }

    /** One patch: {@code patch} holds four positions then four tile UVs. */
    record Piece(Source source, float[] patch) {}

    private static final Piece[][] OUTSIDE = new Piece[4][];
    private static final Piece[][] INSIDE = new Piece[4][];

    static {
        Piece[] outside = outsideNorthEast();
        Piece[] inside = insideNorthEast();
        for (int q = 0; q < 4; q++) {
            OUTSIDE[q] = mirror(outside, q);
            INSIDE[q] = mirror(inside, q);
        }
    }

    private WallCorners() {}

    /** Whether wall cell {@code (x, y)} rounds its corner in quarter {@code q}. */
    static boolean outside(Cells cells, int x, int y, int q) {
        int sx = SX[q], sy = SY[q];
        return cells.wall(x, y) && cells.open(x + sx, y) && cells.open(x, y + sy)
                && cells.open(x + sx, y + sy);
    }

    /** Whether open cell {@code (x, y)} lays a fillet into its corner in quarter {@code q}. */
    static boolean inside(Cells cells, int x, int y, int q) {
        int sx = SX[q], sy = SY[q];
        return cells.open(x, y) && cells.wall(x + sx, y) && cells.wall(x, y + sy)
                && cells.wall(x + sx, y + sy);
    }

    /**
     * The corners a battle's walls were built with, fixed the first time its ground is drawn.
     *
     * <p>Damage takes corners away but never makes them. A corner draws only while it was
     * there as built and its rule still holds now, so a fallen wall stops its own rounding and
     * any fillet that leaned on it, and the fresh wall ends either side of a breach stay square:
     * a shell hole reads as torn, not as designed.
     */
    static final class AsBuilt {
        private final int width;
        private final int height;
        /** Per cell: bit {@code q} an outside corner in quarter {@code q}, bit {@code 4 + q} an inside one. */
        private final byte[] bits;

        private AsBuilt(int width, int height) {
            this.width = width;
            this.height = height;
            this.bits = new byte[width * height];
        }

        static AsBuilt of(int width, int height, Cells cells) {
            AsBuilt built = new AsBuilt(width, height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int mask = 0;
                    for (int q = 0; q < 4; q++) {
                        if (WallCorners.outside(cells, x, y, q)) mask |= 1 << q;
                        if (WallCorners.inside(cells, x, y, q)) mask |= 1 << (4 + q);
                    }
                    built.bits[y * width + x] = (byte) mask;
                }
            }
            return built;
        }

        boolean outside(Cells now, int x, int y, int q) {
            return was(x, y, q) && WallCorners.outside(now, x, y, q);
        }

        boolean inside(Cells now, int x, int y, int q) {
            return was(x, y, 4 + q) && WallCorners.inside(now, x, y, q);
        }

        private boolean was(int x, int y, int bit) {
            if (x < 0 || y < 0 || x >= width || y >= height) return false;
            return (bits[y * width + x] & (1 << bit)) != 0;
        }
    }

    static Piece[] outsidePieces(int q) {
        return OUTSIDE[q];
    }

    static Piece[] insidePieces(int q) {
        return INSIDE[q];
    }

    // ---- the north-east quarter ---------------------------------------------

    /**
     * Ground under the quarter, then the tile in a fan round the cell's middle. Each fan triangle
     * maps the arc onto the quarter's two outer edges along rays from the middle, so a face trim
     * on either edge lands on the arc at its own thickness, and the 45-degree ray carries the
     * tile's corner.
     */
    private static Piece[] outsideNorthEast() {
        Piece[] pieces = new Piece[1 + SEGMENTS];
        pieces[0] = new Piece(Source.GROUND, same(.5f, .5f, 1f, .5f, 1f, 1f, .5f, 1f));
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = angle(i), a1 = angle(i + 1);
            float[] p0 = onArc(a0, RADIUS), p1 = onArc(a1, RADIUS);
            float[] s0 = onQuarterEdge(a0), s1 = onQuarterEdge(a1);
            pieces[1 + i] = new Piece(Source.OWN, new float[]{
                    .5f, .5f, p0[0], p0[1], p1[0], p1[1], p1[0], p1[1],
                    .5f, .5f, s0[0], s0[1], s1[0], s1[1], s1[0], s1[1]});
        }
        return pieces;
    }

    /**
     * Cap over the two straight trims where they meet, cap in the corner of the open cell, then
     * the face trim bent round the arc on top. The band's ends sit where the neighbours' straight
     * trims carry on: at the north end on the north wall's south face, at the east end on the east
     * wall's west face.
     */
    private static Piece[] insideNorthEast() {
        float t = FACE_DEPTH;
        Piece[] pieces = new Piece[4 + SEGMENTS];
        // The north wall's trim under the fillet, and the east wall's; textured in their own cells.
        pieces[0] = new Piece(Source.CAP, new float[]{
                .5f, 1f, 1f, 1f, 1f, 1f + t, .5f, 1f + t,
                .5f, 0f, 1f, 0f, 1f, t, .5f, t});
        pieces[1] = new Piece(Source.CAP, new float[]{
                1f, .5f, 1f + t, .5f, 1f + t, 1f, 1f, 1f,
                0f, .5f, t, .5f, t, 1f, 0f, 1f});
        // The open cell's corner, out to the band's outer edge at 45 degrees.
        float[] mid = onArc(Math.PI / 4, RADIUS + t);
        pieces[2] = new Piece(Source.CAP, same(1f, 1f, 1f, .5f, mid[0], mid[1], mid[0], mid[1]));
        pieces[3] = new Piece(Source.CAP, same(1f, 1f, mid[0], mid[1], .5f, 1f, .5f, 1f));
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = angle(i), a1 = angle(i + 1);
            float[] in0 = onArc(a0, RADIUS), out0 = onArc(a0, RADIUS + t);
            float[] in1 = onArc(a1, RADIUS), out1 = onArc(a1, RADIUS + t);
            float u0 = alongFace(a0), u1 = alongFace(a1);
            pieces[4 + i] = new Piece(Source.FACE, new float[]{
                    in0[0], in0[1], out0[0], out0[1], out1[0], out1[1], in1[0], in1[1],
                    u0, 0f, u0, t, u1, t, u1, 0f});
        }
        return pieces;
    }

    private static double angle(int segment) {
        return segment * (Math.PI / 2) / SEGMENTS;
    }

    private static float[] onArc(double angle, float radius) {
        return new float[]{(float) (.5 + radius * Math.cos(angle)), (float) (.5 + radius * Math.sin(angle))};
    }

    /** Where a ray from the cell's middle at {@code angle} leaves the north-east quarter. */
    private static float[] onQuarterEdge(double angle) {
        if (angle <= Math.PI / 4) return new float[]{1f, (float) (.5 + .5 * Math.tan(angle))};
        return new float[]{(float) (.5 + .5 / Math.tan(angle)), 1f};
    }

    /**
     * Position along the south face tile for a point of the band. The north end continues the
     * north wall's own trim at its x; the rest runs on along the face.
     */
    private static float alongFace(double angle) {
        return (float) (.5 + .4 * (1 - angle / (Math.PI / 2)));
    }

    /** A patch whose tile UVs are its positions. */
    private static float[] same(float... xy) {
        float[] patch = new float[16];
        System.arraycopy(xy, 0, patch, 0, 8);
        System.arraycopy(xy, 0, patch, 8, 8);
        return patch;
    }

    /**
     * The north-east pieces in quarter {@code q}: positions and UVs both mirrored about the cell's
     * middle line. A UV mirrors with its position because each piece samples the tile of the cell
     * it lies in, and a south-facing trim mirrored north is the north-facing one.
     */
    private static Piece[] mirror(Piece[] pieces, int q) {
        Piece[] out = new Piece[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            float[] p = pieces[i].patch().clone();
            for (int j = 0; j < 16; j += 2) {
                if (SX[q] < 0) p[j] = 1f - p[j];
                if (SY[q] < 0) p[j + 1] = 1f - p[j + 1];
            }
            out[i] = new Piece(pieces[i].source(), p);
        }
        return out;
    }
}
