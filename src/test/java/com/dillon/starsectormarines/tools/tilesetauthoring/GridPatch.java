package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The grid a set of cut cells already forms, so it can be moved as one.
 *
 * <p>A wall block is nine cells of one plate, and what is wrong with it is
 * almost never wrong with one of them. The grid it was cut on starts two pixels
 * left of where the art does, or its pitch is a third of a pixel short and the
 * error only shows up in the far corner. Correcting that a cell at a time is
 * nine edits that have to agree, and they will not: the middle column ends up a
 * pixel wider than the two beside it and the seam it makes is visible in every
 * map the block paves.
 *
 * <p>So the patch is the thing edited. Its parameters are a {@link GridCut} —
 * where the first line falls and how far apart the lines are — which is the same
 * vocabulary the sheet-wide fit uses, and for the same reason: pitch is one
 * cell's size, and moving the origin must not resize anything.
 *
 * <p><b>The addressing is measured, not declared.</b> A block's slot names say
 * {@code nw..se} and would give a 3x3 its addresses directly, but only a 3x3,
 * and only a block — while the thing an operator selects is a rectangle of
 * cells, which may be a whole plate, a row of four, or one piece. So columns and
 * rows come from where the pieces actually sit. Edges within a quarter of a cell
 * of each other are taken to be the same grid line, because a cell somebody has
 * already nudged by a pixel is still in its column, and a real neighbouring
 * column is a whole cell away.
 *
 * <p>One piece is a 1x1 patch, which is what keeps a single-cell correction and
 * a whole-plate correction the same operation rather than two.
 */
public record GridPatch(GridCut cut, List<Placed> cells) {

    public GridPatch {
        cells = List.copyOf(cells);
    }

    /** One member of the patch, and which cell of the grid it is. */
    public record Placed(TilesetExport.Entry entry, int col, int row) {}

    /**
     * Either the grid a selection forms, or why it does not form one.
     *
     * <p>Not an exception: selecting an L of cells is a thing an operator does
     * on the way to selecting the right ones, and the screen has to be able to
     * say so while they are still choosing.
     */
    public record Derived(GridPatch patch, String refusal) {

        public boolean isGrid() {
            return patch != null;
        }
    }

    /** What moving a patch onto a new cut did. */
    public record Applied(int moved, int shifted, int maxShift) {

        public String summary() {
            if (shifted == 0) return moved + " cells left where they were";
            return shifted + " of " + moved + " cells moved, worst edge by " + maxShift + " px";
        }
    }

    /**
     * The grid {@code selected} forms, or the reason it forms none.
     *
     * <p>The only structural refusal is an arrangement that is not a filled
     * rectangle. A patch whose cells are slightly irregular is accepted and
     * regularized on save — that is the correction being asked for — and how far
     * adopting the grid would move them is reported rather than hidden; see
     * {@link #drift()}.
     */
    public static Derived of(List<TilesetExport.Entry> selected) {
        if (selected == null || selected.isEmpty()) {
            return new Derived(null, "Pick a piece to adjust.");
        }
        Axis columns = axis(selected, true);
        Axis rows = axis(selected, false);
        int wanted = columns.count() * rows.count();
        if (wanted != selected.size()) {
            return new Derived(null, selected.size() + " pieces lie on "
                    + columns.count() + " columns and " + rows.count() + " rows, which is a "
                    + columns.count() + "x" + rows.count() + " grid of " + wanted
                    + " cells. A patch is moved as one grid, so it has to be a filled "
                    + "rectangle of cells — select a whole block, a whole plate, or one piece.");
        }
        List<Placed> placed = new ArrayList<>(wanted);
        TilesetExport.Entry[] taken = new TilesetExport.Entry[wanted];
        for (int i = 0; i < selected.size(); i++) {
            int col = columns.band()[i];
            int row = rows.band()[i];
            int at = row * columns.count() + col;
            if (taken[at] != null) {
                return new Derived(null, selected.get(i).id + " and " + taken[at].id
                        + " are both column " + col + ", row " + row + " of the selection, so it "
                        + "is not one grid. Pieces that overlap cannot be re-cut together.");
            }
            taken[at] = selected.get(i);
            placed.add(new Placed(selected.get(i), col, row));
        }
        placed.sort(Comparator.comparingInt(Placed::row).thenComparingInt(Placed::col));
        GridCut cut = new GridCut(columns.count(), rows.count(),
                columns.origin(), columns.pitch(), rows.origin(), rows.pitch());
        return new Derived(new GridPatch(cut, placed), null);
    }

    /** The same members, on a different placement. */
    public GridPatch withCut(GridCut replacement) {
        return new GridPatch(replacement, cells);
    }

    /** The rectangle a member would take under this patch's cut. */
    public SheetSlicer.Piece cellOf(Placed placed) {
        return cut.cell(placed.col(), placed.row());
    }

    /** The union of every cell this patch's cut names. */
    public SheetSlicer.Piece bounds() {
        SheetSlicer.Piece first = cut.cell(0, 0);
        SheetSlicer.Piece last = cut.cell(cut.cols() - 1, cut.rows() - 1);
        int left = Math.min(first.x(), last.x());
        int top = Math.min(first.y(), last.y());
        int right = Math.max(first.x() + first.width(), last.x() + last.width());
        int bottom = Math.max(first.y() + first.height(), last.y() + last.height());
        return new SheetSlicer.Piece(left, top, right - left, bottom - top);
    }

    /**
     * The largest distance any member's edge would travel to sit on this cut.
     *
     * <p>Zero for a patch derived from cells that were already regular, which is
     * what says the grid describes them rather than replaces them.
     */
    public int drift() {
        int worst = 0;
        for (Placed placed : cells) {
            worst = Math.max(worst, shift(placed.entry().piece, cellOf(placed)));
        }
        return worst;
    }

    /**
     * Move every member onto the rectangle its address names.
     *
     * <p>All of them or none: a patch half-applied is worse than one not
     * applied, because the half that moved no longer agrees with the half that
     * did not and nothing on the sheet says which is right.
     */
    public Applied applyTo(int sheetWidth, int sheetHeight) throws IOException {
        for (Placed placed : cells) {
            SheetSlicer.Piece now = cellOf(placed);
            if (now.x() < 0 || now.y() < 0
                    || now.x() + now.width() > sheetWidth
                    || now.y() + now.height() > sheetHeight) {
                throw new IOException(placed.entry().id + " would be cut at " + now.x() + ","
                        + now.y() + " of " + now.width() + "x" + now.height() + ", which runs off "
                        + "a " + sheetWidth + "x" + sheetHeight + " sheet");
            }
        }
        int shifted = 0;
        int maxShift = 0;
        for (Placed placed : cells) {
            SheetSlicer.Piece was = placed.entry().piece;
            SheetSlicer.Piece now = cellOf(placed);
            placed.entry().piece = now;
            if (!was.equals(now)) {
                shifted++;
                maxShift = Math.max(maxShift, shift(was, now));
            }
        }
        return new Applied(cells.size(), shifted, maxShift);
    }

    private static int shift(SheetSlicer.Piece was, SheetSlicer.Piece now) {
        return Math.max(
                Math.max(Math.abs(now.x() - was.x()), Math.abs(now.y() - was.y())),
                Math.max(Math.abs((now.x() + now.width()) - (was.x() + was.width())),
                        Math.abs((now.y() + now.height()) - (was.y() + was.height()))));
    }

    /**
     * One axis of the arrangement: how many lines the pieces fall on, where the
     * first is, how far apart they are, and which line each piece sits on.
     */
    private record Axis(int count, double origin, double pitch, int[] band) {}

    private static Axis axis(List<TilesetExport.Entry> selected, boolean horizontal) {
        int size = selected.size();
        int[] start = new int[size];
        int[] extent = new int[size];
        for (int i = 0; i < size; i++) {
            SheetSlicer.Piece piece = selected.get(i).piece;
            start[i] = horizontal ? piece.x() : piece.y();
            extent[i] = horizontal ? piece.width() : piece.height();
        }
        double tolerance = tolerance(extent);

        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> start[i]));

        int[] band = new int[size];
        List<List<Integer>> bands = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int at = order[i];
            if (!current.isEmpty() && start[at] - start[order[i - 1]] > tolerance) {
                bands.add(current);
                current = new ArrayList<>();
            }
            band[at] = bands.size();
            current.add(at);
        }
        bands.add(current);

        double origin = mean(bands.get(0), start, null);
        double last = mean(bands.get(bands.size() - 1), start, extent);
        return new Axis(bands.size(), origin, (last - origin) / bands.size(), band);
    }

    /**
     * How far apart two edges can be and still be the same grid line.
     *
     * <p>A quarter of the smallest cell. Big enough that a cell somebody has
     * already nudged stays in its column; small enough that no real neighbouring
     * column — a whole cell away — is ever swallowed into it.
     */
    private static double tolerance(int[] extent) {
        int smallest = Integer.MAX_VALUE;
        for (int one : extent) smallest = Math.min(smallest, one);
        return Math.max(1.0, smallest / 4.0);
    }

    private static double mean(List<Integer> members, int[] start, int[] extent) {
        double total = 0;
        for (int at : members) total += start[at] + (extent == null ? 0 : extent[at]);
        return total / members.size();
    }
}
