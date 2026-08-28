package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a plate's stated grid actually sits on its sheet.
 *
 * <p>A cut is the stated {@code cols x rows} together with, per axis, the
 * coordinate its first line falls on and the distance between lines. It is not
 * a division of the canvas. Generated art sits inside a margin and is rarely
 * drawn to a pitch that divides its own pixel size evenly:
 * {@code urban-tileset} is a real, regular ten-column grid whose gutters are
 * 123.25px apart starting 2.31px in, while dividing its 1254px canvas by ten
 * places lines 125.4px apart starting at zero. The two disagree by up to
 * seventeen pixels at the ends, which is a visible sliver of the neighbouring
 * cell in every exported tile.
 *
 * <p><b>Counts stay stated; only the placement is measured.</b> How many cells a
 * sheet holds cannot be read off the pixels — it was tried here and deleted,
 * because seam energy ranked "two cells" above the correct ten and
 * autocorrelation answered with the shortest lag it could find. Fitting an
 * origin and a pitch for a count that is already stated is a different and
 * well-posed problem: two parameters against nine observations. See
 * {@link GridFit}.
 *
 * <p>Origin and pitch rather than the rectangle being divided, although the two
 * descriptions carry the same information. Pitch is a property of the art — one
 * cell's size — while a rectangle's width is that size multiplied by the count,
 * so restating the count on a rectangle silently resizes every cell. The count
 * is exactly the thing an operator restates and the pitch is exactly the thing a
 * fit measures, so they are kept apart and each stays editable without
 * disturbing the other.
 *
 * @param originX where column line 0 falls, in sheet pixels
 * @param pitchX  the distance between column lines; need not equal {@code pitchY}
 */
public record GridCut(int cols, int rows, double originX, double pitchX,
                      double originY, double pitchY) {

    public GridCut {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("a grid needs at least one cell: "
                    + cols + "x" + rows);
        }
        if (!(pitchX > 0) || !(pitchY > 0)) {
            throw new IllegalArgumentException("a grid line spacing must be positive: "
                    + pitchX + " x " + pitchY);
        }
    }

    /**
     * The cut a caller gets when nothing has measured the sheet: the piece
     * divided proportionally, which assumes the grid starts at the piece's own
     * edge and steps by {@code extent / count}.
     *
     * <p>Both assumptions are usually false for generated art, which is the bug
     * this type exists to fix. It stays the default because it is what the
     * operator has stated and nothing more, and because inventing a measured
     * placement for a sheet nobody has measured would be the tool overruling
     * them.
     */
    public static GridCut dividing(SheetSlicer.Piece piece, int cols, int rows) {
        return new GridCut(cols, rows,
                piece.x(), piece.width() / (double) cols,
                piece.y(), piece.height() / (double) rows);
    }

    /** The same proportional division, over a whole sheet of the given size. */
    public static GridCut dividing(int width, int height, int cols, int rows) {
        return dividing(new SheetSlicer.Piece(0, 0, Math.max(1, width), Math.max(1, height)),
                cols, rows);
    }

    public GridCut withColumnAxis(double origin, double pitch) {
        return new GridCut(cols, rows, origin, pitch, originY, pitchY);
    }

    public GridCut withRowAxis(double origin, double pitch) {
        return new GridCut(cols, rows, originX, pitchX, origin, pitch);
    }

    /** The same placement, taking a different number of cells from it. */
    public GridCut withCounts(int cols, int rows) {
        return new GridCut(cols, rows, originX, pitchX, originY, pitchY);
    }

    /** Column line {@code index}, unrounded. {@code index} runs 0..cols inclusive. */
    public double columnLine(int index) {
        return originX + index * pitchX;
    }

    /** Row line {@code index}, unrounded. {@code index} runs 0..rows inclusive. */
    public double rowLine(int index) {
        return originY + index * pitchY;
    }

    /**
     * One cell, in sheet pixels.
     *
     * <p>Each edge is rounded once, so a cell's right edge is its neighbour's
     * left edge and the cells tile the cut area exactly with no gap and no
     * overlap. They do not tile the sheet: the margin outside the cut is not
     * part of the grid, which is the whole point.
     */
    public SheetSlicer.Piece cell(int col, int row) {
        int left = (int) Math.round(columnLine(col));
        int right = (int) Math.round(columnLine(col + 1));
        int top = (int) Math.round(rowLine(row));
        int bottom = (int) Math.round(rowLine(row + 1));
        return new SheetSlicer.Piece(left, top,
                Math.max(1, right - left), Math.max(1, bottom - top));
    }

    /** Every cell in reading order: left to right, then top to bottom. */
    public List<SheetSlicer.Piece> cells() {
        List<SheetSlicer.Piece> parts = new ArrayList<>(cols * rows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) parts.add(cell(col, row));
        }
        return parts;
    }

    /** One cell's width in whole source pixels. Not the same number as {@link #cellPxY}. */
    public int cellPxX() {
        return Math.max(1, (int) Math.round(pitchX));
    }

    public int cellPxY() {
        return Math.max(1, (int) Math.round(pitchY));
    }

    /** Whether this cut starts at the origin and steps by a whole division of {@code sheet}. */
    public boolean isDivisionOf(int width, int height) {
        return equalsWithin(this, dividing(width, height, cols, rows));
    }

    private static boolean equalsWithin(GridCut a, GridCut b) {
        return Math.abs(a.originX - b.originX) < 1e-6 && Math.abs(a.pitchX - b.pitchX) < 1e-6
                && Math.abs(a.originY - b.originY) < 1e-6 && Math.abs(a.pitchY - b.pitchY) < 1e-6;
    }

    public String describe() {
        return String.format("%dx%d, columns from %.2f every %.2f px, rows from %.2f every %.2f px",
                cols, rows, originX, pitchX, originY, pitchY);
    }
}
