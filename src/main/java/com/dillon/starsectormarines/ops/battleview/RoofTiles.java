package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;

/**
 * Which pieces of the roof sheet one cell's roof is drawn from (`battle-render-nouns.md`, Roofs).
 *
 * <p>The sheet ({@link #SHEET}) has a row per {@link RoofStyle} and {@link #COLUMNS} square frames in each, a cell
 * apiece: a two-by-two swatch of field that repeats across a roof without a seam, the eave trims an edge piece lays
 * along its open side, and a frame for each way a roof is broken ({@link RoofDamage}). A cell draws the field (or its
 * damage) cut to the shape {@link RoofCover} gives it, and an intact edge piece its trim over that.
 *
 * <p>One resolver for both paths that draw a roof — {@link RoofMesh}, resident, and the per-cell command stream — so
 * the two cannot disagree about what a roof looks like. GL-free, and a function of the cell, its neighbours and its
 * building's style alone.
 *
 * <p>A frame is placed turned some quarter turns clockwise and cut to a rectangle of its cell. Frame and cell points
 * are both in cell units with {@code +y} north; a frame turned {@code k} times puts its point {@code f} at
 * {@code R^k f}, {@code R(x, y) = (y, 1 - x)}, the turn {@link RoofCover} describes its pieces with.
 */
public final class RoofTiles {

    /** The roof sheet, a row per {@link RoofStyle}. */
    public static final String SHEET = "graphics/tilesets/roofs.png";

    /** Frames in a row of the sheet. */
    public static final int COLUMNS = 12;
    /** The swatch of field: columns {@code FIELD + i + 2j} hold its cell {@code (i, j)}, {@code j} counted north. */
    public static final int FIELD = 0;
    /** The trim along an eave's open edge, north-facing: its edge lies at {@link RoofCover#EAVE_DEPTH} up the cell. */
    public static final int TRIM_EAVE = 4;
    /** The trim round an outside corner's square, north-facing (open north and east). */
    public static final int TRIM_CORNER = 5;
    /** The trim along an inside corner's two edges, north-facing (open across the north-east corner). */
    public static final int TRIM_INNER = 6;
    public static final int HOLED = 7;
    public static final int COLLAPSED = 8;
    public static final int BURNT = 9;
    /** The eave trim at {@link RoofCover#DOOR_EAVE_DEPTH}, over an exterior door. */
    public static final int TRIM_DOOR = 10;
    /** The eave trim at {@link RoofCover#PANE_EAVE_DEPTH}, over a slit window. */
    public static final int TRIM_PANE = 11;

    /** The most quads one cell draws: two rectangles of an inside corner and its trim. */
    public static final int MAX_QUADS = 3;

    /** Where a resolved cell's pieces go. */
    public interface Sink {
        /**
         * Frame {@code column} of sheet row {@code row}, turned {@code turns} quarter turns clockwise to lie over the
         * whole cell, drawn over the cell's rectangle {@code x0..x1, y0..y1} (cell units from its south-west corner)
         * and nowhere else.
         */
        void quad(int column, int row, int turns, float x0, float y0, float x1, float y1);
    }

    private RoofTiles() { }

    /** Resolves the roof over {@code (x, y)} of a building roofed in {@code style} with its grain at {@code grainTurns}. */
    public static void resolve(CellTopology topology, int x, int y, RoofStyle style, int grainTurns, Sink sink) {
        int cover = RoofCover.of(topology, x, y);
        RoofCover.Piece piece = RoofCover.piece(cover);
        if (piece == RoofCover.Piece.NONE) return;
        float[][] rects = RoofCover.rects(cover);
        int row = style.ordinal();
        RoofDamage damage = topology.getRoofDamage(x, y);
        if (damage.isOpen()) {
            resolveBroken(topology, x, y, damage, grainTurns, row, rects, sink);
            return;
        }
        int column = fieldColumn(x, y, grainTurns);
        for (float[] r : rects) sink.quad(column, row, grainTurns & 3, r[0], r[1], r[2], r[3]);
        int trim = trimColumn(piece, RoofCover.edge(cover));
        if (trim >= 0) sink.quad(trim, row, RoofCover.facing(cover), 0f, 0f, 1f, 1f);
    }

    /**
     * A broken cell, drawn by where it lies in the patch of broken roof round it, so a blast reads as one ragged
     * opening rather than a hole in every cell it reached. A cell broken alone draws its state's whole frame: a shell
     * hole, a cave-in, a burnt patch. In a patch, a fire's cells all draw their rafters, which run on from cell to
     * cell; a shell's or a fall's cells draw the torn roof hanging from an intact neighbour where they border one, and
     * nothing at all in the middle, where the room is open to the sky.
     */
    private static void resolveBroken(CellTopology topology, int x, int y, RoofDamage damage, int grainTurns,
                                      int row, float[][] rects, Sink sink) {
        int broken = 0;
        int intactSide = -1;
        for (int side = 0; side < 4; side++) {
            int nx = x + SIDE_DX[side];
            int ny = y + SIDE_DY[side];
            if (!RoofCover.isRoofed(topology, nx, ny)) continue;
            if (topology.getRoofDamage(nx, ny).isOpen()) broken++;
            else if (intactSide < 0 || (hash(x, y) & 2) != 0) intactSide = side;
        }
        int column;
        int turns;
        if (broken == 0 || damage == RoofDamage.BURNT) {
            // A broken roof keeps its grain, so a remnant's courses still run with the roof's, either way round.
            column = damageColumn(damage);
            turns = (grainTurns + 2 * (hash(x, y) & 1)) & 3;
        } else if (intactSide >= 0) {
            // The fall's frame hangs its remnant from its north edge; turned to the intact side.
            column = COLLAPSED;
            turns = intactSide;
        } else {
            return;
        }
        for (float[] r : rects) sink.quad(column, row, turns, r[0], r[1], r[2], r[3]);
    }

    private static final int[] SIDE_DX = {0, 1, 0, -1};
    private static final int[] SIDE_DY = {1, 0, -1, 0};

    /**
     * The swatch cell that lies over {@code (x, y)}. The swatch is laid down whole and turned with the grain, so a
     * cell takes the swatch cell its own square falls in once the turn is undone: for a quarter turn that is
     * {@code (-y - 1, x)}.
     */
    static int fieldColumn(int x, int y, int grainTurns) {
        int i;
        int j;
        if ((grainTurns & 1) == 0) {
            i = x & 1;
            j = y & 1;
        } else {
            i = (-y - 1) & 1;
            j = x & 1;
        }
        return FIELD + i + 2 * j;
    }

    static int damageColumn(RoofDamage damage) {
        switch (damage) {
            case HOLED: return HOLED;
            case BURNT: return BURNT;
            default: return COLLAPSED;
        }
    }

    static int trimColumn(RoofCover.Piece piece, RoofCover.Edge edge) {
        switch (piece) {
            case EAVE: return edge == RoofCover.Edge.DOOR ? TRIM_DOOR : edge == RoofCover.Edge.PANE ? TRIM_PANE : TRIM_EAVE;
            case CORNER: return TRIM_CORNER;
            case INNER: return TRIM_INNER;
            default: return -1;
        }
    }

    /** The frame point a frame turned {@code turns} times puts at cell point {@code (px, py)}: {@code u}. */
    public static float frameU(int turns, float px, float py) {
        switch (turns & 3) {
            case 1: return 1f - py;
            case 2: return 1f - px;
            case 3: return py;
            default: return px;
        }
    }

    /** As {@link #frameU}, its {@code v} (north). */
    public static float frameV(int turns, float px, float py) {
        switch (turns & 3) {
            case 1: return px;
            case 2: return 1f - py;
            case 3: return 1f - px;
            default: return py;
        }
    }

    /**
     * How much of a building's tint a roof takes. The tints were chosen to darken one paving tile into many roofs; a
     * roof drawn in its own material needs only a touch of them to tell neighbours apart.
     */
    static final float TINT_SHARE = 0.35f;

    /** One channel of a building's tint as its roof wears it. */
    public static float tint(float channel) {
        return 1f - (1f - channel) * TINT_SHARE;
    }

    /** A frame coordinate held half a texel inside a frame {@code framePx} across, out of the next frame's reach. */
    public static float insideFrame(float t, float framePx) {
        float inset = 0.5f / Math.max(1f, framePx);
        return Math.max(inset, Math.min(1f - inset, t));
    }

    private static int hash(int x, int y) {
        int h = x * 0x27D4EB2D ^ y * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        return h ^ (h >>> 12);
    }
}
