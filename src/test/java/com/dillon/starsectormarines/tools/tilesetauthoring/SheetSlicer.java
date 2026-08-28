package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Finds the individual pieces on a raw art sheet.
 *
 * <p>Works from the alpha channel rather than from a declared grid, because
 * bought and commissioned sheets are laid out for a human to look at: rows of
 * differing height, pieces of differing size, gutters that vary. Keying on
 * transparency finds what is actually there.
 *
 * <p><b>Alpha is thresholded, not tested against zero.</b> Sheets keyed from a
 * dark background carry a faint residue — a few percent alpha across what looks
 * like empty space — and treating any non-zero pixel as content merges the
 * entire sheet into one blob. Thresholding well above that residue and well
 * below the art separates the pieces cleanly.
 *
 * <p>The alternative, keying on luminance, does not work on this kind of art at
 * all: a piece with a dark screen, vent or recess breaks into several fragments
 * because its own interior reads as background.
 */
public final class SheetSlicer {

    private SheetSlicer() {}

    /** Alpha at or above which a pixel counts as art. Above background residue, below anti-aliasing. */
    public static final int DEFAULT_ALPHA_MIN = 40;
    /** Pixels below which a component is speckle rather than a piece. */
    public static final int DEFAULT_MIN_AREA = 200;

    /** One detected piece, in source-sheet pixels. */
    public record Piece(int x, int y, int width, int height) {

        public int right() {
            return x + width - 1;
        }

        public int bottom() {
            return y + height - 1;
        }
    }

    /**
     * Every piece on the sheet, in reading order: grouped into visual rows by
     * vertical overlap, then left to right within each row. Reading order is
     * what makes a generated id list correspond to what a human sees.
     */
    public static List<Piece> slice(BufferedImage sheet, int alphaMin, int minArea) {
        List<Piece> pieces = components(sheet, alphaMin, minArea);
        List<List<Piece>> rows = new ArrayList<>();
        pieces.sort(Comparator.comparingInt(Piece::y).thenComparingInt(Piece::x));
        for (Piece piece : pieces) {
            List<Piece> found = null;
            for (List<Piece> row : rows) {
                int top = row.stream().mapToInt(Piece::y).min().orElse(0);
                int bottom = row.stream().mapToInt(Piece::bottom).max().orElse(0);
                if (piece.y() <= bottom - ROW_OVERLAP && piece.bottom() >= top + ROW_OVERLAP) {
                    found = row;
                    break;
                }
            }
            if (found == null) {
                found = new ArrayList<>();
                rows.add(found);
            }
            found.add(piece);
        }
        List<Piece> ordered = new ArrayList<>();
        for (List<Piece> row : rows) {
            row.sort(Comparator.comparingInt(Piece::x));
            ordered.addAll(row);
        }
        return ordered;
    }

    /** Rows are a human grouping, so pieces need real overlap to share one, not a shared edge. */
    private static final int ROW_OVERLAP = 10;

    /**
     * Split a piece into an exact {@code cols} x {@code rows} grid.
     *
     * <p>Tileable plates are drawn edge to edge with no gutter between them, so
     * they arrive from {@link #slice} fused into one block. They cannot be told
     * apart by looking at pixels — the whole point of a tiling plate is that its
     * edges match its neighbour — so splitting them is an authoring decision
     * made on the pieces that are known to be plates.
     *
     * <p><b>The grid is stated, not measured, and its cells need not be square.</b>
     * Generated art is laid out to whatever the prompt asked for: a 20-frame
     * strip has cells four times wider than they are tall, and a plate's cells
     * rarely divide its pixel size evenly. Taking a cell size in pixels meant
     * recovering the layout by rounding, which quietly produced the wrong number
     * of columns whenever a sheet was not square. The layout is the thing the
     * operator actually knows, so it is the thing this takes.
     *
     * <p><b>A grid needs an origin and a pitch, not a division of the canvas.</b>
     * Dividing the piece proportionally assumes the grid starts at the piece's
     * own edge and steps by {@code extent / count}, and generated art satisfies
     * neither: it sits inside a margin and is rarely drawn to a pitch that
     * divides its own pixel size evenly. This overload is the stated-only case,
     * where nothing has measured the sheet; pass a {@link GridCut} once
     * {@link GridFit} has. See {@link GridCut#dividing}.
     */
    public static List<Piece> splitOnGrid(Piece piece, int cols, int rows) {
        return splitOnGrid(GridCut.dividing(piece, cols, rows));
    }

    /**
     * The cells of a placed cut, in reading order.
     *
     * <p>The cut says where its lines fall, so the parts tile the cut area
     * exactly with no gap and no overlap while leaving the sheet's margin
     * outside the grid, which is what a margin is.
     */
    public static List<Piece> splitOnGrid(GridCut cut) {
        return cut.cells();
    }

    /** Eight-connected components of the thresholded alpha mask, as tight bounding boxes. */
    private static List<Piece> components(BufferedImage sheet, int alphaMin, int minArea) {
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        boolean[] on = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                on[y * w + x] = (sheet.getRGB(x, y) >>> 24) >= alphaMin;
            }
        }
        boolean[] seen = new boolean[w * h];
        List<Piece> found = new ArrayList<>();
        Deque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < on.length; start++) {
            if (!on[start] || seen[start]) continue;
            seen[start] = true;
            queue.add(start);
            int x0 = start % w;
            int x1 = x0;
            int y0 = start / w;
            int y1 = y0;
            int area = 0;
            while (!queue.isEmpty()) {
                int index = queue.poll();
                area++;
                int cx = index % w;
                int cy = index / w;
                if (cx < x0) x0 = cx;
                if (cx > x1) x1 = cx;
                if (cy < y0) y0 = cy;
                if (cy > y1) y1 = cy;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = cx + dx;
                        int ny = cy + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!on[next] || seen[next]) continue;
                        seen[next] = true;
                        queue.add(next);
                    }
                }
            }
            if (area >= minArea) {
                found.add(new Piece(x0, y0, x1 - x0 + 1, y1 - y0 + 1));
            }
        }
        return found;
    }
}
