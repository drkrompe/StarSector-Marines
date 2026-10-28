package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.CellContour;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * A basin's edge drawn as the line {@link SurfaceField#edge} rather than as the cells it was cut into.
 *
 * <p>Where the floor's open ground (grass on Asharu) meets the rim's (sand) cell to cell, each cell that takes part
 * is drawn as both: the floor's ground clipped to where the edge is at or above zero over the cell, the rim's to where
 * it is below, two triangles each, marched as {@link CellContour} does for roads and raised ground. The cells on
 * either side of a boundary read the same corners, so the line runs on without a step, and the feather between two
 * such cells is left out ({@link GroundFeather}), since it would draw the steps back.
 *
 * <p>Only the wild ground takes part: a cell of its own palette's open ground with no surface of its own, so a field,
 * a yard, a road or a dirt patch keeps its edges and its feathers. Presentation only: the cell is still its own ground
 * for every purpose but the picture.
 */
final class BasinEdge {

    /**
     * The drawn line's wobble: how far the edge is pushed either way, and the noise's scale in cells. The basin's
     * patches are nine cells across and smooth, so the line alone reads as drawn with a pen; a wobble of about half a
     * cell over a few cells reads as ground.
     */
    static final float WOBBLE = 0.05f, WOBBLE_CELLS = 2.5f;
    private static final long WOBBLE_SALT = 0x0B0B1E5L;

    private static final float[] LOWER = {0, 0, 1, 0, 1, 1}, UPPER = {0, 0, 1, 1, 0, 1};

    private final CellTopology topology;
    private final GroundKind floor, rim;
    /** Whether each cell is rim ({@link SurfaceField#rim}), by {@code y * width + x}. */
    private final boolean[] rimCells;
    private final int width;
    /** The edge at every cell corner, {@code (width + 1)} to a row; corner (x, y) is cell (x, y)'s lower left. */
    private final float[] corners;

    private BasinEdge(CellTopology topology, SurfaceField field) {
        this.topology = topology;
        this.floor = field.floor().inlandGround();
        this.rim = field.world().inlandGround();
        this.width = topology.getWidth();
        int height = topology.getHeight();
        rimCells = new boolean[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) rimCells[y * width + x] = field.rim(x, y);
        corners = new float[(width + 1) * (height + 1)];
        long seed = field.seed() ^ WOBBLE_SALT;
        for (int y = 0; y <= height; y++) {
            for (int x = 0; x <= width; x++) {
                float wobble = WOBBLE * (2f * SurfaceField.noise(x / WOBBLE_CELLS, y / WOBBLE_CELLS, seed) - 1f);
                corners[y * (width + 1) + x] = field.edge(x, y) + wobble;
            }
        }
    }

    /** The edge of this map's basin, or null where it has none or its two grounds are the same. */
    static BasinEdge of(CellTopology topology) {
        SurfaceField field = topology.surfaceField();
        if (field == null || field.uniform() || field.floor().inlandGround() == field.world().inlandGround()) return null;
        return new BasinEdge(topology, field);
    }

    /** The floor's open ground, drawn where the edge is at or above zero. */
    GroundKind floor() {
        return floor;
    }

    /** The rim's open ground, drawn where it is below. */
    GroundKind rim() {
        return rim;
    }

    /**
     * Whether this cell is drawn along the edge: wild ground that meets the other side's wild ground, or that the
     * edge crosses or leaves on the other side.
     */
    boolean blends(int x, int y) {
        GroundKind own = wild(x, y);
        if (own == null) return false;
        boolean onFloor = own == floor;
        for (int i = 0; i < 4; i++) {
            if (corner(x + (i & 1), y + (i >> 1)) >= 0f != onFloor) return true;
        }
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            GroundKind other = wild(x + dx, y + dy);
            if (other != null && other != own) return true;
        }
        return false;
    }

    /**
     * One side's piece of one of a cell's two triangles, as a clipped patch's four vertices normalized within the
     * cell, or null where that side has none of it.
     */
    float[] piece(int x, int y, boolean onFloor, int triangle) {
        double s = onFloor ? 1 : -1;
        double a = s * corner(x, y), b = s * corner(x + 1, y), c = s * corner(x + 1, y + 1), d = s * corner(x, y + 1);
        return triangle == 0 ? CellContour.clip(LOWER, new double[]{a, b, c}) : CellContour.clip(UPPER, new double[]{a, c, d});
    }

    /** The open ground this cell is as wild ground of its side, or null where it is anything else. */
    private GroundKind wild(int x, int y) {
        if (!topology.inBounds(x, y) || topology.isWall(x, y) || topology.getSurface(x, y) != 0) return null;
        GroundKind kind = topology.getGroundKind(x, y);
        return kind == (rimCells[y * width + x] ? rim : floor) ? kind : null;
    }

    private float corner(int x, int y) {
        return corners[y * (width + 1) + x];
    }
}
