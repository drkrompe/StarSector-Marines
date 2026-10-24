package com.dillon.starsectormarines.battle.world.model;

/**
 * How much of a cell a building's roof covers, as seen from above (`battle-render-nouns.md`, Roofs).
 *
 * <p>A roof is laid over its building's floor and over the walls that hold it up. Where nothing outside touches a cell
 * — a room's floor, a partition between two rooms, a wall between two buildings — the roof covers it whole, so from
 * outside an interior reads as one roof. Where a cell faces the outside, the roof stops short of the outside edge by
 * an eave: it reaches {@link #EAVE_DEPTH} of the cell in from the building's side and leaves the rest bare, so an
 * exterior wall shows its material (brick, steel plate, sandbags) and a player can judge it as cover.
 *
 * <p>Windows and doorways are part of the wall they are in ({@link #isOpening}), so they notch no roof. An exterior
 * door the generator leaves out of the building is left bare, the roof running up to its inner edge, and a window
 * stamped as the building's own cell in the wall's line takes the wall's eave, so the eave runs straight along the
 * facade and every exterior door and window stays in view before a building is revealed. A door between rooms is
 * roofed over with them.
 * It is presentation only: a
 * roof over a wall shields nothing, and whether a floor cell's roof shields is {@link CellTopology#isRoofIntact}.
 *
 * <p>Sides are numbered clockwise from north, {@code 0 = N (+y)}, {@code 1 = E (+x)}, {@code 2 = S}, {@code 3 = W},
 * and a piece is described as its north-facing shape turned {@link #facing} quarter turns clockwise.
 */
public final class RoofCover {

    /**
     * How far an eave reaches over a cell that faces the outside, from the building's side, in cells: up to the
     * inner edge of an exterior window's frame, which takes the outer 0.3 of its cell, so the roof just touches every
     * window and leaves the frame and the outer strip of each wall in view.
     */
    public static final float EAVE_DEPTH = 0.7f;

    /** The shape a roof takes over one cell. */
    public enum Piece {
        /** No roof: the cell is outside, or a wall with the outside on two sides of it. */
        NONE,
        /** The whole cell: a floor or an interior wall. */
        FULL,
        /** The strip on the building's side of a cell whose {@link #facing} side is outside. */
        EAVE,
        /**
         * The square in the building's corner of an outside corner, whose {@link #facing} side and the next one
         * clockwise are outside.
         */
        CORNER,
        /**
         * An inside corner: the whole cell less the square at its outside corner, the corner between its
         * {@link #facing} side and the next one clockwise, where the outside touches it only across the diagonal.
         */
        INNER
    }

    private static final Piece[] PIECES = Piece.values();
    private static final int[] SIDE_DX = {0, 1, 0, -1};
    private static final int[] SIDE_DY = {1, 0, -1, 0};

    private RoofCover() { }

    /**
     * The cover over {@code (x, y)}, packed as {@code piece.ordinal() | facing << 4}; read it with {@link #piece}
     * and {@link #facing}.
     */
    public static int of(CellTopology topology, int x, int y) {
        if (owner(topology, x, y) == 0) return pack(Piece.NONE, 0);
        // An exterior door or window is left bare and the roof stops at its inner edge, so it reads before a
        // building is revealed; being wall, it still notches nothing round it. One between rooms is roofed over.
        if ((topology.isWindow(x, y) || isOpening(topology, x, y)) && facesOutside(topology, x, y)) {
            return pack(Piece.NONE, 0);
        }
        boolean[] out = new boolean[4];
        int outside = 0;
        for (int side = 0; side < 4; side++) {
            out[side] = isOutside(topology, x + SIDE_DX[side], y + SIDE_DY[side]);
            if (out[side]) outside++;
        }
        if (outside == 0) {
            // The corner between side s and the next one clockwise.
            int corners = 0;
            int firstCorner = -1;
            boolean[] diagonal = new boolean[4];
            for (int s = 0; s < 4; s++) {
                int n = (s + 1) & 3;
                diagonal[s] = isOutside(topology, x + SIDE_DX[s] + SIDE_DX[n], y + SIDE_DY[s] + SIDE_DY[n]);
                if (diagonal[s]) {
                    corners++;
                    if (firstCorner < 0) firstCorner = s;
                }
            }
            if (corners == 0) return pack(Piece.FULL, 0);
            if (corners == 1) return pack(Piece.INNER, firstCorner);
            if (corners == 2) {
                // Both corners on one side: the outside runs along that side, past the ends of it.
                for (int s = 0; s < 4; s++) {
                    if (diagonal[s] && diagonal[(s + 3) & 3]) return pack(Piece.EAVE, s);
                }
            }
            return pack(Piece.FULL, 0);
        }
        if (outside == 1) {
            for (int s = 0; s < 4; s++) if (out[s]) return pack(Piece.EAVE, s);
        }
        if (outside == 2) {
            for (int s = 0; s < 4; s++) if (out[s] && out[(s + 1) & 3]) return pack(Piece.CORNER, s);
        }
        return pack(Piece.NONE, 0);
    }

    /** The piece of a packed cover. */
    public static Piece piece(int cover) {
        return PIECES[cover & 0xF];
    }

    /** The side a packed cover faces: see {@link Piece}. */
    public static int facing(int cover) {
        return (cover >>> 4) & 3;
    }

    /**
     * The building whose roof covers {@code (x, y)}, or zero for none: a floor cell's own, and for a wall or window
     * the first building across one of its sides, else across one of its corners.
     */
    public static int owner(CellTopology topology, int x, int y) {
        if (!topology.inBounds(x, y)) return 0;
        int own = topology.getBuildingId(x, y);
        if (own != 0) return own;
        if (!isStructure(topology, x, y)) return 0;
        for (int s = 0; s < 4; s++) {
            int id = topology.getBuildingId(x + SIDE_DX[s], y + SIDE_DY[s]);
            if (id != 0) return id;
        }
        for (int s = 0; s < 4; s++) {
            int n = (s + 1) & 3;
            int id = topology.getBuildingId(x + SIDE_DX[s] + SIDE_DX[n], y + SIDE_DY[s] + SIDE_DY[n]);
            if (id != 0) return id;
        }
        return 0;
    }

    /** Whether any roof is drawn over {@code (x, y)}: what a blow to the roof there can damage. */
    public static boolean isRoofed(CellTopology topology, int x, int y) {
        return piece(of(topology, x, y)) != Piece.NONE;
    }

    /**
     * The rectangles a piece covers in its cell, {@code {x0, y0, x1, y1}} each in cell units from the cell's
     * south-west corner, with {@code +y} north. One rectangle, or two for an {@link Piece#INNER inside corner};
     * none for {@link Piece#NONE}.
     */
    public static float[][] rects(int cover, float depth) {
        Piece piece = piece(cover);
        int facing = facing(cover);
        switch (piece) {
            case FULL: return new float[][]{{0f, 0f, 1f, 1f}};
            case EAVE: return new float[][]{turn(new float[]{0f, 0f, 1f, depth}, facing)};
            case CORNER: return new float[][]{turn(new float[]{0f, 0f, depth, depth}, facing)};
            case INNER: return new float[][]{
                    turn(new float[]{0f, 0f, 1f, depth}, facing),
                    turn(new float[]{0f, depth, depth, 1f}, facing)};
            default: return new float[0][];
        }
    }

    /**
     * A rectangle of a north-facing piece turned {@code quarterTurns} clockwise about the cell's centre. A point
     * {@code (x, y)} goes to {@code (y, 1 - x)}, which takes the north edge to the east.
     */
    static float[] turn(float[] r, int quarterTurns) {
        float x0 = r[0], y0 = r[1], x1 = r[2], y1 = r[3];
        for (int i = 0; i < (quarterTurns & 3); i++) {
            float nx0 = y0, ny0 = 1f - x1, nx1 = y1, ny1 = 1f - x0;
            x0 = nx0; y0 = ny0; x1 = nx1; y1 = ny1;
        }
        return new float[]{x0, y0, x1, y1};
    }

    /** Off the map, or open ground no building owns: what an eave stops short of. */
    static boolean isOutside(CellTopology topology, int x, int y) {
        if (!topology.inBounds(x, y)) return true;
        return topology.getBuildingId(x, y) == 0 && !isStructure(topology, x, y);
    }

    /** A wall, a window, or an opening in a wall's line: what a roof is carried on rather than laid over. */
    private static boolean isStructure(CellTopology topology, int x, int y) {
        return isWallOrWindow(topology, x, y) || isOpening(topology, x, y);
    }

    /**
     * A doorway or breach in a wall's line: no building's floor and no wall itself, but with wall or window either
     * side of it along one axis and a building's floor across it on the other. The generator leaves a doorway out of
     * the building it opens, so without this a door would read as outside and notch every roof it is cut into: an
     * exterior door takes an eave like the wall it is in, an interior one is covered whole.
     */
    static boolean isOpening(CellTopology topology, int x, int y) {
        if (!topology.inBounds(x, y) || topology.getBuildingId(x, y) != 0 || isWallOrWindow(topology, x, y)) {
            return false;
        }
        boolean northSouth = isWallOrWindow(topology, x, y + 1) && isWallOrWindow(topology, x, y - 1);
        boolean eastWest = isWallOrWindow(topology, x + 1, y) && isWallOrWindow(topology, x - 1, y);
        return northSouth && (topology.getBuildingId(x + 1, y) != 0 || topology.getBuildingId(x - 1, y) != 0)
                || eastWest && (topology.getBuildingId(x, y + 1) != 0 || topology.getBuildingId(x, y - 1) != 0);
    }

    private static boolean facesOutside(CellTopology topology, int x, int y) {
        for (int s = 0; s < 4; s++) if (isOutside(topology, x + SIDE_DX[s], y + SIDE_DY[s])) return true;
        return false;
    }

    private static boolean isWallOrWindow(CellTopology topology, int x, int y) {
        return topology.isWall(x, y) || topology.isWindow(x, y);
    }

    private static int pack(Piece piece, int facing) {
        return piece.ordinal() | (facing << 4);
    }
}
