package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.CellContour;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Where natural water meets natural ground, drawn as a contour instead of the
 * cells' square steps.
 *
 * <p><b>A field over the cells, honest at their centres.</b> Every cell centre
 * carries {@code +1} for water and {@code -1} for land; every cell corner the
 * sum of the four cells' signs over two — {@code +2} among water, {@code -2}
 * among land — nudged, where water and land meet, by a bounded noise anchored
 * on the corner itself. Each cell is drawn as four triangles fanned from its centre to
 * consecutive corners, and each band of the shore is that triangle clipped where
 * the field, linear across it, passes the band's level. A staircase of cells
 * therefore reads as a slope: a corner with one water cell of four sits at
 * {@code -0.5} and the water cell's corner is cut away; a corner with three sits
 * at {@code +0.5} and the land cell's corner floods. A straight bank stays on
 * its cell edge, wandering by the noise.
 *
 * <p><b>What it may not lie about.</b> Passability and sight follow cells, so the
 * picture may only move the line between them near the corners and edges where
 * the two meet: a centre is always exactly {@code ±1}, beyond every band's level,
 * so no centre is ever drawn as the other side, and no band reaches past the
 * cells next to the water. Presentation only: nothing gameplay
 * reads is changed.
 *
 * <p><b>Built edges stay square.</b> A pool, a quay, a road or a bridge is a made
 * thing with a made edge. Only water and the natural grounds take part; a corner
 * any other cell touches takes the value of the cell being drawn, so the water
 * runs square up to whatever was built against it.
 */
public final class WaterShore {

    /**
     * Control-run switch for the owner's comparison: {@code organic} (the
     * default), {@code diagonal} (the same contour without its noise) or
     * {@code square} (the cells' own steps). Temporary: removed with the losing
     * arms once the {@code water-shore} picks question is answered.
     */
    public static final String PROPERTY = "battle.render.shore";

    private static final String MODE = System.getProperty(PROPERTY, "organic");

    private static final boolean ENABLED = !MODE.equals("square");

    /**
     * How far a corner's value may wander, in field units; a straight bank
     * wanders half this in cells. Under one, so a bank never wanders as far
     * as half a cell.
     */
    static final double NOISE = MODE.equals("diagonal") ? 0 : 0.6;

    /**
     * Comparison switch for what a ford shows through its shallows:
     * {@code pebbles} (a stony bed), {@code stones} (a line of stepping stones
     * across it) or {@code both}, the default. Temporary: removed with the
     * losing looks once the {@code ford-look} picks question is answered.
     */
    public static final String FORD_PROPERTY = "battle.render.fordLook";

    private static final String FORD_LOOK = System.getProperty(FORD_PROPERTY, "both");

    /** The block a ford's pebble bed draws from, or null when the look has none. */
    public static final String PEBBLE_BLOCK = FORD_LOOK.equals("stones") ? null : "shore.ford-pebbles";

    /** The block a ford's stepping stones draw from, or null when the look has none. */
    public static final String STONE_BLOCK = FORD_LOOK.equals("pebbles") ? null : "shore.ford-stones";

    /** Triangles a cell is drawn in, fanned from its centre. */
    public static final int TRIANGLES = 4;

    /**
     * The bands of a shore, in paint order: each lies over the one before.
     *
     * <p>Two fields are read. The waterline bands read the <em>wet</em> field,
     * in which shallow water counts as water; the open water reads the
     * <em>deep</em> field, in which it counts as ground. On a bank with no
     * shallows the two are the same field.
     */
    public enum Band {
        /** A translucent dark film over the land just above the waterline. */
        WET(-0.45, "shore.wet", false),
        /** The light shallows between the waterline and the open water. */
        SHALLOW(0, "shore.shallow", false),
        /** The open water itself, drawn from the water's own tiles. */
        WATER(0.5, null, true);

        /** The field value the band starts at; the field changes by two a cell across a bank. */
        public final double level;
        /** The block it draws from, or null for the water's own. */
        public final String block;
        /** Whether the band reads the deep field rather than the wet one. */
        public final boolean deep;

        Band(double level, String block, boolean deep) {
            this.level = level;
            this.block = block;
            this.deep = deep;
        }
    }

    static final int WATER = 1, NATURAL = 0, BUILT = -1, SHALLOW = 2;

    /** The cell's corners counter-clockwise from the south-west, north up. */
    private static final int[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private WaterShore() {}

    /** Whether the organic shore is drawn at all. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * What a cell is to the shore. Past the map's edge the nearest cell
     * continues, so a coast runs off the map instead of closing on it.
     */
    static int kind(CellTopology topology, int x, int y) {
        x = Math.max(0, Math.min(topology.getWidth() - 1, x));
        y = Math.max(0, Math.min(topology.getHeight() - 1, y));
        if (topology.isWall(x, y) || topology.isFixture(x, y)) return BUILT;
        if (topology.isShallowWater(x, y)) return SHALLOW;
        GroundKind ground = topology.getGroundKind(x, y);
        if (ground == GroundKind.WATER) return WATER;
        if (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null) return BUILT;
        return switch (ground) {
            case GRASS, DIRT, STONE, SAND, SNOW -> NATURAL;
            default -> BUILT;
        };
    }

    /**
     * Whether a cell draws any part of a shore: shallow water always, and
     * water or natural ground with another of the three somewhere among the
     * nine cells around it.
     */
    public static boolean takesPart(CellTopology topology, int x, int y) {
        int self = kind(topology, x, y);
        if (self == BUILT) return false;
        if (self == SHALLOW) return true;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int k = kind(topology, x + dx, y + dy);
                if (k != BUILT && k != self) return true;
            }
        }
        return false;
    }

    /**
     * Whether a water cell is drawn as land under its shore bands, so a corner
     * the contour cuts from it shows ground instead of a square of water.
     */
    public static boolean isShoreWater(CellTopology topology, int x, int y) {
        return kind(topology, x, y) == WATER && takesPart(topology, x, y);
    }

    /**
     * The natural cell a shore water cell borrows its ground from: the first of
     * its neighbours, cardinals before diagonals, that is natural ground; null
     * when none is.
     */
    public static int[] groundDonor(CellTopology topology, int x, int y) {
        int[][] order = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        for (int[] d : order) {
            int nx = x + d[0], ny = y + d[1];
            if (topology.inBounds(nx, ny) && kind(topology, nx, ny) == NATURAL) return new int[]{nx, ny};
        }
        return null;
    }

    /** Whether a cell of this kind is water in the deep or the wet field. */
    private static boolean water(int kind, boolean deep) {
        return kind == WATER || (!deep && kind == SHALLOW);
    }

    /** The wet field at a corner; see {@link #corner(CellTopology, int, int, boolean)}. */
    static double corner(CellTopology topology, int cx, int cy) {
        return corner(topology, cx, cy, false);
    }

    /**
     * A field at the corner {@code (cx, cy)} — the south-west corner of cell
     * {@code (cx, cy)} — or NaN when a built cell touches it.
     *
     * @param deep the deep field, in which shallow water is ground, rather than the wet one
     */
    static double corner(CellTopology topology, int cx, int cy, boolean deep) {
        int water = 0;
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 0; dx++) {
                int k = kind(topology, cx + dx, cy + dy);
                if (k == BUILT) return Double.NaN;
                if (water(k, deep)) water++;
            }
        }
        // Twice the mean of the cells' signs: the scale at which a straight bank's
        // field is linear across all four of a cell's triangles, so every band
        // runs parallel to it instead of reaching out to the corners in teeth.
        double value = water - 2;
        if (water == 0 || water == 4) return value;
        return value + NOISE * noise(cx, cy);
    }

    /**
     * The wet field at a point, in cells: inside the fan triangle the point
     * falls in, linear between the cell's centre and that triangle's two
     * corners, so it crosses a waterline band's level exactly where
     * {@link #patch} clips that band. Shallow water is water here. Continuous
     * across the edge between two cells that take part. NaN on a built cell,
     * which has no shore.
     */
    public static double fieldAt(CellTopology topology, double worldX, double worldY) {
        return fieldAt(topology, worldX, worldY, false);
    }

    /**
     * As {@link #fieldAt(CellTopology, double, double)}, in the deep field when
     * {@code deep}: the one the open water is clipped by, in which a ford is ground.
     */
    public static double fieldAt(CellTopology topology, double worldX, double worldY, boolean deep) {
        int x = (int) Math.floor(worldX), y = (int) Math.floor(worldY);
        int self = kind(topology, x, y);
        if (self == BUILT) return Double.NaN;
        double centre = water(self, deep) ? 1 : -1;
        double fx = worldX - x, fy = worldY - y;
        // The triangle is the one on the nearest edge; v is the way in from that
        // edge toward the centre, u the way along it from its first corner.
        int triangle = 0;
        double v = fy, u = fx;
        if (1 - fx < v) { triangle = 1; v = 1 - fx; u = fy; }
        if (1 - fy < v) { triangle = 2; v = 1 - fy; u = 1 - fx; }
        if (fx < v) { triangle = 3; v = fx; u = 1 - fy; }
        int[] a = CORNERS[triangle], b = CORNERS[(triangle + 1) % 4];
        double va = corner(topology, x + a[0], y + a[1], deep);
        double vb = corner(topology, x + b[0], y + b[1], deep);
        if (Double.isNaN(va)) va = centre;
        if (Double.isNaN(vb)) vb = centre;
        // Barycentric: the centre stands half a cell in from the edge.
        double wc = 2 * v, wb = u - v;
        return wc * centre + (1 - wc - wb) * va + wb * vb;
    }

    /**
     * One band's piece of one of a cell's triangles, as four clipped-patch
     * vertices normalized within the cell; null when the band does not reach it.
     *
     * @param triangle which of the {@link #TRIANGLES} fanned from the centre,
     *                 counter-clockwise from the one along the south edge
     */
    public static float[] patch(CellTopology topology, int x, int y, Band band, int triangle) {
        return clip(topology, x, y, band.deep, band.level, triangle, true);
    }

    /**
     * The stony bed of a shallow cell, for one of its triangles: everywhere the
     * open water does not cover, so the stones never show through deep water.
     * Null for a cell that is not shallow water.
     */
    public static float[] bedPatch(CellTopology topology, int x, int y, int triangle) {
        if (kind(topology, x, y) != SHALLOW) return null;
        return clip(topology, x, y, true, Band.WATER.level, triangle, false);
    }

    /**
     * Whether a shallow cell is on its ford's line of stepping stones: the
     * middle row across the ford, at right angles to the longer of the ford's
     * two runs through the cell, which is the way the water flows.
     */
    public static boolean isSteppingStone(CellTopology topology, int x, int y) {
        if (!topology.isShallowWater(x, y)) return false;
        int north = run(topology, x, y, 0, 1), south = run(topology, x, y, 0, -1);
        int east = run(topology, x, y, 1, 0), west = run(topology, x, y, -1, 0);
        return north + south >= east + west
                ? north == south || north == south + 1
                : east == west || east == west + 1;
    }

    /** Shallow cells beyond {@code (x, y)} in one direction, before the first that is not. */
    private static int run(CellTopology topology, int x, int y, int dx, int dy) {
        int n = 0;
        while (topology.isShallowWater(x + dx * (n + 1), y + dy * (n + 1))) n++;
        return n;
    }

    /** One triangle clipped to where a field is at least {@code level}, or below it. */
    private static float[] clip(CellTopology topology, int x, int y, boolean deep, double level,
                                int triangle, boolean above) {
        int self = kind(topology, x, y);
        if (self == BUILT) return null;
        double centre = water(self, deep) ? 1 : -1;
        int[] a = CORNERS[triangle], b = CORNERS[(triangle + 1) % 4];
        double va = corner(topology, x + a[0], y + a[1], deep);
        double vb = corner(topology, x + b[0], y + b[1], deep);
        // Against something built, the cell runs square to its own edge.
        if (Double.isNaN(va)) va = centre;
        if (Double.isNaN(vb)) vb = centre;
        double sign = above ? 1 : -1;
        return CellContour.clip(new float[]{.5f, .5f, a[0], a[1], b[0], b[1]},
                new double[]{sign * (centre - level), sign * (va - level), sign * (vb - level)});
    }

    /** A corner's own noise, in {@code [-1, 1]}. */
    static double noise(int cx, int cy) {
        long h = cx * 0x9E3779B97F4A7C15L ^ cy * 0xC2B2AE3D27D4EB4FL ^ 0x5B0E5B0EL;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
