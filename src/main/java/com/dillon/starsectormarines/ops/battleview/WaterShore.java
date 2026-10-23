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

    /** Triangles a cell is drawn in, fanned from its centre. */
    public static final int TRIANGLES = 4;

    /** The bands of a shore, in paint order: each lies over the one before. */
    public enum Band {
        /** A translucent dark film over the land just above the waterline. */
        WET(-0.45, "shore.wet"),
        /** The light shallows between the waterline and the open water. */
        SHALLOW(0, "shore.shallow"),
        /** The open water itself, drawn from the water's own tiles. */
        WATER(0.5, null);

        /** The field value the band starts at; the field changes by two a cell across a bank. */
        public final double level;
        /** The block it draws from, or null for the water's own. */
        public final String block;

        Band(double level, String block) {
            this.level = level;
            this.block = block;
        }
    }

    static final int WATER = 1, NATURAL = 0, BUILT = -1;

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
        GroundKind ground = topology.getGroundKind(x, y);
        if (ground == GroundKind.WATER) return WATER;
        if (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null) return BUILT;
        return switch (ground) {
            case GRASS, DIRT, STONE, SAND, SNOW -> NATURAL;
            default -> BUILT;
        };
    }

    /**
     * Whether a cell draws any part of a shore: water or natural ground with
     * both somewhere among the nine cells around it.
     */
    public static boolean takesPart(CellTopology topology, int x, int y) {
        int self = kind(topology, x, y);
        if (self == BUILT) return false;
        boolean water = false, land = false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int k = kind(topology, x + dx, y + dy);
                if (k == WATER) water = true;
                else if (k == NATURAL) land = true;
            }
        }
        return water && land;
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

    /**
     * The field at the corner {@code (cx, cy)} — the south-west corner of cell
     * {@code (cx, cy)} — or NaN when a built cell touches it.
     */
    static double corner(CellTopology topology, int cx, int cy) {
        int water = 0;
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 0; dx++) {
                int k = kind(topology, cx + dx, cy + dy);
                if (k == BUILT) return Double.NaN;
                if (k == WATER) water++;
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
     * One band's piece of one of a cell's triangles, as four clipped-patch
     * vertices normalized within the cell; null when the band does not reach it.
     *
     * @param triangle which of the {@link #TRIANGLES} fanned from the centre,
     *                 counter-clockwise from the one along the south edge
     */
    public static float[] patch(CellTopology topology, int x, int y, Band band, int triangle) {
        int self = kind(topology, x, y);
        if (self == BUILT) return null;
        double centre = self == WATER ? 1 : -1;
        int[] a = CORNERS[triangle], b = CORNERS[(triangle + 1) % 4];
        double va = corner(topology, x + a[0], y + a[1]);
        double vb = corner(topology, x + b[0], y + b[1]);
        // Against something built, the cell runs square to its own edge.
        if (Double.isNaN(va)) va = centre;
        if (Double.isNaN(vb)) vb = centre;
        return CellContour.clip(new float[]{.5f, .5f, a[0], a[1], b[0], b[1]},
                new double[]{centre - band.level, va - band.level, vb - band.level});
    }

    /** A corner's own noise, in {@code [-1, 1]}. */
    static double noise(int cx, int cy) {
        long h = cx * 0x9E3779B97F4A7C15L ^ cy * 0xC2B2AE3D27D4EB4FL ^ 0x5B0E5B0EL;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
