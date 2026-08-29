package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The grid a set of cut cells already lies on, so it can be moved as one.
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
 * <p><b>A patch is a lattice, not a rectangle.</b> The selection does not have
 * to fill the grid it lies on. {@code floors.brick} is five cells in a plus —
 * one, then three, then one — which is a perfectly ordinary variant pool cut
 * from one sheet on one pitch, and moving that pitch is exactly what somebody
 * would want to do to it. Requiring a filled rectangle refused it for a reason
 * that had nothing to do with the art.
 *
 * <p><b>The addressing is measured, not declared.</b> A block's slot names say
 * {@code nw..se} and would give a 3x3 its addresses directly, but only a 3x3,
 * and only a block. So columns and rows come from where the pieces actually sit:
 * edges within a quarter of a cell of each other are one grid line, because a
 * cell somebody has already nudged by a pixel is still in its column. Which
 * lattice index each line holds is then recovered from the spacing, so a
 * selection that skips a column still lands on the right addresses.
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
     * Either the grid a selection lies on, or why it lies on none.
     *
     * <p>Not an exception: selecting two unrelated props is a thing an operator
     * does on the way to selecting the right ones, and the screen has to be able
     * to say so while they are still choosing.
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
     * The grid {@code selected} lies on, or the reason it lies on none.
     *
     * <p>Two refusals, both structural: pieces whose spacing no single origin and
     * pitch can describe, and two pieces claiming one cell. A patch whose cells
     * are slightly irregular is accepted and regularized on save — that is the
     * correction being asked for — and how far adopting the grid would move them
     * is reported rather than hidden; see {@link #drift()}.
     */
    public static Derived of(List<TilesetExport.Entry> selected) {
        if (selected == null || selected.isEmpty()) {
            return new Derived(null, "Pick a piece to adjust.");
        }
        Axis columns = axis(selected, true);
        Axis rows = axis(selected, false);
        if (!columns.regular() || !rows.regular()) {
            return new Derived(null, subject(selected) + " are not spaced evenly enough to be "
                    + "one grid — no single origin and pitch says where they all sit. Pick one "
                    + "cell to move on its own.");
        }
        List<Placed> placed = new ArrayList<>(selected.size());
        Map<Long, TilesetExport.Entry> taken = new HashMap<>();
        for (int i = 0; i < selected.size(); i++) {
            int col = columns.index()[i];
            int row = rows.index()[i];
            TilesetExport.Entry clash =
                    taken.put((long) row * Integer.MAX_VALUE + col, selected.get(i));
            if (clash != null) {
                return new Derived(null, selected.get(i).id + " and " + clash.id
                        + " are both column " + col + ", row " + row + " of the selection, so it "
                        + "is not one grid. Pieces that overlap cannot be re-cut together.");
            }
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

    /** Whether the members leave cells of their grid empty. */
    public boolean isSparse() {
        return cells.size() < cut.cols() * cut.rows();
    }

    /**
     * The union of the members' cells.
     *
     * <p>The members', not the grid's: a sparse patch's empty cells are not part
     * of the selection, and drawing the picture around them would dim art that
     * the operator did not leave out.
     */
    public SheetSlicer.Piece bounds() {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Placed placed : cells) {
            SheetSlicer.Piece cell = cellOf(placed);
            left = Math.min(left, cell.x());
            top = Math.min(top, cell.y());
            right = Math.max(right, cell.x() + cell.width());
            bottom = Math.max(bottom, cell.y() + cell.height());
        }
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

    /** How a refusal names what was selected: by its block when they share one. */
    private static String subject(List<TilesetExport.Entry> selected) {
        String block = selected.get(0).blockId;
        if (block != null && !block.isEmpty()) {
            for (TilesetExport.Entry entry : selected) {
                if (!block.equals(entry.blockId)) return "These " + selected.size() + " pieces";
            }
            return block + "'s " + selected.size() + " cells";
        }
        return "These " + selected.size() + " pieces";
    }

    /**
     * One axis of the arrangement: how many lattice lines it spans, where line
     * zero is, how far apart the lines are, which line each piece sits on, and
     * whether a single origin and pitch actually describe them.
     */
    private record Axis(int count, double origin, double pitch, int[] index, boolean regular) {}

    private static Axis axis(List<TilesetExport.Entry> selected, boolean horizontal) {
        int size = selected.size();
        int[] start = new int[size];
        int[] extent = new int[size];
        for (int i = 0; i < size; i++) {
            SheetSlicer.Piece piece = selected.get(i).piece;
            start[i] = horizontal ? piece.x() : piece.y();
            extent[i] = horizontal ? piece.width() : piece.height();
        }

        int[] band = new int[size];
        List<List<Integer>> bands = cluster(start, extent, band);
        int lines = bands.size();
        double[] bandStart = new double[lines];
        double[] bandEnd = new double[lines];
        for (int b = 0; b < lines; b++) {
            bandStart[b] = mean(bands.get(b), start, null);
            bandEnd[b] = mean(bands.get(b), start, extent);
        }

        double coarse = coarsePitch(bandStart, extent);
        int[] line = lattice(bandStart, coarse);
        double[] fit = fitLine(bandStart, bandEnd, line);
        double origin = fit[0];
        double pitch = fit[1] > 0 ? fit[1] : coarse;

        // A band that does not sit on the line the fit predicts for it means the
        // pieces were never on one grid, and adopting one would move them a long
        // way to pretend otherwise.
        double slack = Math.max(2.0, coarse / 4.0);
        boolean regular = true;
        for (int b = 0; b < lines; b++) {
            regular &= Math.abs(bandStart[b] - (origin + line[b] * pitch)) <= slack
                    && Math.abs(bandEnd[b] - (origin + (line[b] + 1) * pitch)) <= slack;
        }

        int[] index = new int[size];
        for (int i = 0; i < size; i++) index[i] = line[band[i]];
        return new Axis(line[lines - 1] + 1, origin, pitch, index, regular);
    }

    /** Group starts that are the same grid line, filling {@code band} per piece. */
    private static List<List<Integer>> cluster(int[] start, int[] extent, int[] band) {
        int size = start.length;
        double tolerance = tolerance(extent);
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> start[i]));

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
        return bands;
    }

    /**
     * A first estimate of the pitch, good enough to recover lattice indices.
     *
     * <p>The smallest step between adjacent grid lines, because that step is one
     * pitch whenever any two selected cells are neighbours — which is the normal
     * case and is true even when the selection skips a column elsewhere. A cell's
     * own size is the fallback for a lone line, and only that: a plate cut with
     * gutters has cells narrower than its pitch, and estimating from them reads
     * a 40px grid of 32px cells as a 32px one.
     */
    private static double coarsePitch(double[] bandStart, int[] extent) {
        double smallest = Double.MAX_VALUE;
        for (int b = 1; b < bandStart.length; b++) {
            smallest = Math.min(smallest, bandStart[b] - bandStart[b - 1]);
        }
        if (smallest == Double.MAX_VALUE || smallest <= 0) {
            double total = 0;
            for (int one : extent) total += one;
            smallest = Math.max(1.0, total / extent.length);
        }
        return smallest;
    }

    /**
     * Which lattice index each grid line holds.
     *
     * <p>Consecutive indices are the fallback rather than the rule: a selection
     * that skips a whole column has a gap of two pitches in it, and numbering its
     * lines 0, 1, 2 would place the third cell where the second belongs.
     */
    private static int[] lattice(double[] bandStart, double coarse) {
        int[] line = new int[bandStart.length];
        for (int b = 1; b < bandStart.length; b++) {
            line[b] = (int) Math.round((bandStart[b] - bandStart[0]) / coarse);
            if (line[b] <= line[b - 1]) {
                for (int i = 0; i < line.length; i++) line[i] = i;
                return line;
            }
        }
        return line;
    }

    /**
     * Least squares of {@code y = origin + pitch * index} over every band edge.
     *
     * <p>Both edges of every band, so a lone line still determines a pitch — its
     * start is index {@code k} and its end is index {@code k + 1} — and so a wide
     * plate's pitch is measured across its whole span rather than from one cell.
     * That span is what recovers a fraction: sixteen columns place the pitch far
     * more precisely than any single width can.
     */
    private static double[] fitLine(double[] bandStart, double[] bandEnd, int[] line) {
        int count = 2 * bandStart.length;
        double sumK = 0;
        double sumY = 0;
        double sumKK = 0;
        double sumKY = 0;
        for (int b = 0; b < bandStart.length; b++) {
            double first = line[b];
            double second = line[b] + 1;
            sumK += first + second;
            sumY += bandStart[b] + bandEnd[b];
            sumKK += first * first + second * second;
            sumKY += first * bandStart[b] + second * bandEnd[b];
        }
        double denominator = count * sumKK - sumK * sumK;
        if (denominator == 0) return new double[]{bandStart[0], 0};
        double pitch = (count * sumKY - sumK * sumY) / denominator;
        return new double[]{(sumY - pitch * sumK) / count, pitch};
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
