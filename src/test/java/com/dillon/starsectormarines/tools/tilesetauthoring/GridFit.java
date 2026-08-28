package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where a sheet's stated grid really sits, measured from the art.
 *
 * <p><b>A plate that separates its cells with a dark gutter has its boundary in
 * the middle of that gap, so its grid is fitted to the gutters; a plate drawn
 * cell against cell has no gap, so its grid is fitted to where the art
 * changes.</b> Which one a sheet is, is a fact about the art and is measured
 * rather than configured: see {@link Boundary} for the two features and
 * {@link #fitAxis} for the choice between them.
 *
 * <p>Either feature reduces to one profile along the axis whose peaks are the
 * boundaries, so both are fitted the same way. Given a <em>stated</em> cell
 * count, an origin and a pitch can be fitted to those peaks: two parameters
 * against {@code count - 1} observations.
 *
 * <p><b>The cell count stays stated and this must never learn to guess it.</b>
 * Detecting the count was tried in this project and deleted: seam energy ranked
 * "two cells" above the correct ten, and autocorrelation was dominated by short
 * lags. That failure does not carry over to fitting a placement for a count
 * somebody has already given, which is over-determined and well conditioned.
 * The distinction is the whole licence for this class to exist.
 *
 * <p><b>A fit is a measurement offered to an operator, never a decision taken
 * for them.</b> Real sheets fail this in several different ways — a plate whose
 * lower rows are empty gives no seams to fit there, and a strip of props whose
 * frames vary in width has no single pitch at all — so every fit reports how
 * many real seams it found and how far they sit from the straight line through
 * them, and {@link Axis#trustworthy()} answers honestly. A caller that applies
 * an untrustworthy axis anyway must be doing so because it was told to.
 */
public final class GridFit {

    private GridFit() {}

    /** Search bounds on the pitch, as a fraction of {@code extent / count}. */
    private static final double MIN_PITCH_FRACTION = 0.90;
    private static final double MAX_PITCH_FRACTION = 1.02;
    private static final double PITCH_STEP_PX = 0.05;
    private static final double ORIGIN_STEP_PX = 0.5;
    /** How far outside the sheet a grid line may start, as a fraction of the pitch. */
    private static final double ORIGIN_SLACK_FRACTION = 0.15;
    /** Snap windows, as a fraction of the pitch, narrowing over the refinement rounds. */
    private static final double[] SNAP_WINDOWS = {0.10, 0.06, 0.05};
    /** How far above the sheet's own background gradient a peak must be to count as a seam. */
    private static final double STRONG_SEAM_FACTOR = 2.0;
    /** How many of the stated boundaries must land on a real seam for the fit to be usable. */
    private static final double MIN_STRONG_FRACTION = 0.8;
    /** How far a seam may sit from the fitted line, in pixels and as a fraction of the pitch. */
    private static final double MIN_RESIDUAL_TOLERANCE_PX = 2.0;
    private static final double RESIDUAL_TOLERANCE_FRACTION = 0.04;

    /**
     * The feature in the art a fit put the grid lines on.
     *
     * <p>The two are not variants of one objective, they are two different
     * things a plate can do at a cell boundary, and confusing them is the bug
     * this distinction exists to fix. A gutter has a bright-to-dark edge on each
     * side of it, so a fit that chases change lands on one of those edges — a
     * few pixels <em>inside</em> the neighbouring cell's art, which is a visible
     * sliver in every exported tile.
     */
    public enum Boundary {

        /** The dark gap a plate leaves between its cells; the line goes in the gap. */
        GUTTER("the gutters between cells"),

        /** Where the art changes, for a plate drawn cell against cell with no gap. */
        CHANGE("where the art changes");

        private final String description;

        Boundary(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * One measured boundary.
     *
     * @param index    which grid line this is, 1 .. count-1
     * @param position where the {@link Boundary} the axis was fitted to actually
     *                 is, in sheet pixels — the middle of a gutter, or the line
     *                 the art changes across
     * @param strong   whether that feature is clearly above the sheet's own
     *                 background level rather than a peak in noise
     */
    public record Seam(int index, int position, double energy, boolean strong) {}

    /**
     * One axis's fitted placement and how well it is supported.
     *
     * <p>The residuals are what says whether "a regular grid" describes the sheet
     * at all. A plate whose frames vary in width — a strip of props, say — fits a
     * straight line badly no matter where it is placed, and that shows up here
     * rather than in a silently wrong cut.
     */
    public record Axis(int count, Boundary onto, double origin, double pitch, List<Seam> seams,
                       double maxResidual, double rmsResidual) {

        /** How many of the measured boundaries sit on a real seam rather than on noise. */
        public int strongSeams() {
            int strong = 0;
            for (Seam seam : seams) {
                if (seam.strong()) strong++;
            }
            return strong;
        }

        /** How far a seam may sit from the fitted line before the fit stops meaning anything. */
        public double residualTolerance() {
            return Math.max(MIN_RESIDUAL_TOLERANCE_PX, RESIDUAL_TOLERANCE_FRACTION * pitch);
        }

        /**
         * Whether this fit may be applied without someone looking at it first.
         *
         * <p>Both halves are needed and each catches a different failure. Too few
         * strong seams means most of the boundaries were fitted to nothing — an
         * empty region of the plate, or a sheet whose cells simply do not meet.
         * A large residual means the seams that were found do not lie on a line,
         * so the sheet is not a regular grid whatever its stated layout says.
         */
        public boolean trustworthy() {
            if (count < 2) return true;
            return strongSeams() >= MIN_STRONG_FRACTION * (count - 1)
                    && maxResidual <= residualTolerance();
        }

        /**
         * The worst distance from a candidate placement's lines to the seams
         * measured here.
         *
         * <p>This is how one cut is compared with another: both are measured
         * against the same observed boundaries, so the comparison says which
         * placement lands on the art rather than which fits its own peaks best.
         */
        public double worstOffsetFrom(double origin, double pitch) {
            double worst = 0;
            for (Seam seam : seams) {
                worst = Math.max(worst, Math.abs(origin + seam.index() * pitch - seam.position()));
            }
            return worst;
        }

        public String describe(String axisName) {
            if (count < 2) return axisName + ": 1 cell, nothing to fit";
            return String.format(
                    "%s: %d cells from %.2f every %.2f px, fitted to %s; %d of %d boundaries on a "
                            + "real seam, worst %.1f px off the line, rms %.1f (tolerance %.1f) "
                            + "— %s",
                    axisName, count, origin, pitch, onto, strongSeams(), count - 1,
                    maxResidual, rmsResidual, residualTolerance(),
                    trustworthy() ? "usable" : "NOT usable, look before applying it");
        }
    }

    /** Both axes of a sheet measured against one stated layout. */
    public record Measured(Axis columns, Axis rows) {

        /**
         * The stated cut moved onto the art, for whichever axes actually fitted.
         *
         * <p>An axis that did not fit keeps the placement it came in with, so a
         * caller that applies this never silently accepts a measurement the
         * measurement itself disowns.
         */
        public GridCut appliedTo(GridCut stated) {
            GridCut cut = stated;
            if (columns.trustworthy()) cut = cut.withColumnAxis(columns.origin(), columns.pitch());
            if (rows.trustworthy()) cut = cut.withRowAxis(rows.origin(), rows.pitch());
            return cut;
        }

        public String describe() {
            return columns.describe("columns") + "\n" + rows.describe("rows");
        }
    }

    /** Fit both axes of {@code sheet} to the layout {@code stated} says it has. */
    public static Measured measure(BufferedImage sheet, GridCut stated) {
        double[][] luminance = luminance(sheet);
        return new Measured(
                fitAxis(columnEnergy(luminance), columnProfile(luminance), stated.cols()),
                fitAxis(rowEnergy(luminance), rowProfile(luminance), stated.rows()));
    }

    /**
     * One axis fitted to whichever boundary the plate actually draws.
     *
     * <p>The gutter fit is tried first and kept when the plate turns out to have
     * gutters — when most of the stated boundaries land in a real trough, which
     * is the same evidence {@link Axis#trustworthy()} wants and is measured by
     * {@link #gutterEnergy}. A plate drawn edge to edge has no trough to find,
     * so that test fails on it and the fit falls back to where the art changes.
     *
     * <p>Note what this is <em>not</em>: it is not a race between two objectives
     * settled by whichever residual came out smaller. Both fit their own feature
     * well on a sheet that has both, and the smaller number would then be an
     * accident of which feature is sharper rather than a statement about where
     * the cells meet. The question asked here is about the art — does this plate
     * leave a gap between its cells? — and only its answer selects the fit.
     */
    static Axis fitAxis(double[] change, double[] profile, int count) {
        if (count < 2) return fit(change, count, Boundary.CHANGE);
        Axis gutters = fit(gutterEnergy(profile, profile.length / (double) count), count,
                Boundary.GUTTER);
        if (gutters.strongSeams() >= MIN_STRONG_FRACTION * (count - 1)) return gutters;
        return fit(change, count, Boundary.CHANGE);
    }

    /**
     * How much the art changes across each column line.
     *
     * <p>Index {@code x} holds the change between columns {@code x-1} and
     * {@code x}, so a peak at {@code x} means a boundary at {@code x}. Index 0
     * is zero because there is nothing to the left of it.
     */
    public static double[] columnEnergy(double[][] luminance) {
        int height = luminance.length;
        int width = height == 0 ? 0 : luminance[0].length;
        double[] energy = new double[width];
        for (int y = 0; y < height; y++) {
            double[] row = luminance[y];
            for (int x = 1; x < width; x++) energy[x] += Math.abs(row[x] - row[x - 1]);
        }
        return energy;
    }

    public static double[] rowEnergy(double[][] luminance) {
        int height = luminance.length;
        int width = height == 0 ? 0 : luminance[0].length;
        double[] energy = new double[height];
        for (int y = 1; y < height; y++) {
            double[] row = luminance[y];
            double[] above = luminance[y - 1];
            for (int x = 0; x < width; x++) energy[y] += Math.abs(row[x] - above[x]);
        }
        return energy;
    }

    /** How bright each column of the sheet is on average. */
    public static double[] columnProfile(double[][] luminance) {
        int height = luminance.length;
        int width = height == 0 ? 0 : luminance[0].length;
        double[] profile = new double[width];
        for (double[] row : luminance) {
            for (int x = 0; x < width; x++) profile[x] += row[x];
        }
        if (height > 0) {
            for (int x = 0; x < width; x++) profile[x] /= height;
        }
        return profile;
    }

    /** How bright each row of the sheet is on average. */
    public static double[] rowProfile(double[][] luminance) {
        int height = luminance.length;
        int width = height == 0 ? 0 : luminance[0].length;
        double[] profile = new double[height];
        for (int y = 0; y < height; y++) {
            double[] row = luminance[y];
            double sum = 0;
            for (int x = 0; x < width; x++) sum += row[x];
            profile[y] = width == 0 ? 0 : sum / width;
        }
        return profile;
    }

    /**
     * How deep a dark gap runs at each line, with lit art on both sides of it.
     *
     * <p>Depth is measured against the brightest art within half a cell either
     * side rather than against the sheet as a whole, and both sides have to
     * supply it. That is what a gutter <em>is</em> — a dip between two lit cells
     * — and stating it that way makes this zero in two places where a plain
     * "how dark is this line" would be maximal and wrong: across an empty region
     * of a plate, which is uniformly dark and has no gap in it however dark it
     * is, and outside the art in the sheet's own margin.
     *
     * @param nominal the stated cell size on this axis, which sets how far away
     *                the art bounding a gutter is allowed to be
     */
    public static double[] gutterEnergy(double[] profile, double nominal) {
        int extent = profile.length;
        int half = Math.max(2, (int) Math.round(nominal / 2));
        double[] energy = new double[extent];
        for (int at = 0; at < extent; at++) {
            double left = 0;
            for (int x = Math.max(0, at - half); x < at; x++) left = Math.max(left, profile[x]);
            double right = 0;
            for (int x = at + 1; x < Math.min(extent, at + half + 1); x++) {
                right = Math.max(right, profile[x]);
            }
            energy[at] = Math.max(0, Math.min(left, right) - profile[at]);
        }
        return energy;
    }

    /**
     * The sheet as brightness, with transparency composited onto black.
     *
     * <p>Compositing matters for a keyed sheet: an unweighted read of the colour
     * channels finds edges inside fully transparent pixels, where whatever the
     * generator left behind the key is invisible but still varies.
     */
    public static double[][] luminance(BufferedImage sheet) {
        int width = sheet.getWidth();
        int height = sheet.getHeight();
        double[][] out = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = sheet.getRGB(x, y);
                double alpha = (argb >>> 24) / 255.0;
                double r = (argb >> 16) & 0xFF;
                double g = (argb >> 8) & 0xFF;
                double b = argb & 0xFF;
                out[y][x] = alpha * (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return out;
    }

    /**
     * Place {@code count} cells on one axis of measured boundary energy.
     *
     * <p>Objective-agnostic: {@code energy} peaks wherever the boundary that
     * {@code onto} names is, and everything below is about placing a regular
     * line through peaks. Which energy to hand it is {@link #fitAxis}'s
     * question.
     *
     * <p>Three steps, each answering a failure of the one before it. A coarse
     * sweep over origin and pitch finds the phase, scored against a capped and
     * blurred copy of the energy — capped because one enormous seam elsewhere on
     * the sheet would otherwise decide the phase by itself, blurred because the
     * art's own boundaries are not perfectly regular and a sharp objective scores
     * a nearly-right line at zero. The lines are then snapped to the strongest
     * change near each of them, in narrowing windows, and a least-squares line is
     * refitted through the snapped positions. What that line does not explain is
     * reported rather than smoothed away.
     */
    public static Axis fit(double[] energy, int count, Boundary onto) {
        int extent = energy.length;
        if (count < 2 || extent < 2) {
            return new Axis(count, onto, 0, Math.max(1, extent), List.of(), 0, 0);
        }
        double nominal = extent / (double) count;
        double[] scored = prepared(energy, count, nominal);

        double[] placement = sweep(scored, count, extent, nominal);
        double origin = placement[0];
        double pitch = placement[1];

        List<int[]> snapped = List.of();
        for (double window : SNAP_WINDOWS) {
            List<int[]> found = snap(energy, origin, pitch, count,
                    Math.max(MIN_RESIDUAL_TOLERANCE_PX, window * pitch));
            if (found.size() < 2) continue;
            double[] line = leastSquares(found);
            origin = line[0];
            pitch = line[1];
            snapped = found;
        }
        if (snapped.isEmpty()) return new Axis(count, onto, origin, pitch, List.of(), 0, 0);

        double background = medianOfPositive(energy);
        List<Seam> seams = new ArrayList<>(snapped.size());
        double worst = 0;
        double squares = 0;
        for (int[] observation : snapped) {
            int index = observation[0];
            int position = observation[1];
            double residual = position - (origin + index * pitch);
            worst = Math.max(worst, Math.abs(residual));
            squares += residual * residual;
            seams.add(new Seam(index, position, energy[position],
                    energy[position] >= STRONG_SEAM_FACTOR * background));
        }
        return new Axis(count, onto, origin, pitch, List.copyOf(seams),
                worst, Math.sqrt(squares / snapped.size()));
    }

    /**
     * The energy as the phase sweep should see it: no single seam allowed to
     * outvote the rest, and spread to the tolerance the art itself has.
     */
    private static double[] prepared(double[] energy, int count, double nominal) {
        int[] strongest = strongestSeparated(energy, Math.max(2, (int) (nominal * 0.5)), count - 1);
        double cap = strongest.length == 0 ? max(energy) : medianOf(energy, strongest);
        double[] capped = new double[energy.length];
        for (int i = 0; i < energy.length; i++) capped[i] = Math.min(energy[i], Math.max(cap, 1e-9));
        return triangleBlur(capped, Math.max(2, (int) Math.round(nominal * 0.05)));
    }

    /** Best origin and pitch by summed energy under the interior grid lines. */
    private static double[] sweep(double[] scored, int count, int extent, double nominal) {
        double best = -1;
        double bestOrigin = 0;
        double bestPitch = nominal;
        int pitchSteps = (int) Math.floor(
                nominal * (MAX_PITCH_FRACTION - MIN_PITCH_FRACTION) / PITCH_STEP_PX);
        for (int p = 0; p <= pitchSteps; p++) {
            double pitch = nominal * MIN_PITCH_FRACTION + p * PITCH_STEP_PX;
            double slack = pitch * ORIGIN_SLACK_FRACTION;
            double lowest = -slack;
            double highest = Math.max(lowest, extent - count * pitch + slack);
            int originSteps = (int) Math.floor((highest - lowest) / ORIGIN_STEP_PX);
            for (int o = 0; o <= originSteps; o++) {
                double origin = lowest + o * ORIGIN_STEP_PX;
                double score = 0;
                for (int k = 1; k < count; k++) score += sample(scored, origin + k * pitch);
                if (score > best) {
                    best = score;
                    bestOrigin = origin;
                    bestPitch = pitch;
                }
            }
        }
        return new double[] {bestOrigin, bestPitch};
    }

    /** Each interior line moved onto the strongest change within {@code window} of it. */
    private static List<int[]> snap(double[] energy, double origin, double pitch, int count,
                                    double window) {
        List<int[]> found = new ArrayList<>(count - 1);
        for (int k = 1; k < count; k++) {
            double line = origin + k * pitch;
            int from = Math.max(1, (int) Math.ceil(line - window));
            int to = Math.min(energy.length - 1, (int) Math.floor(line + window));
            int at = -1;
            for (int x = from; x <= to; x++) {
                if (at < 0 || energy[x] > energy[at]) at = x;
            }
            if (at >= 0 && energy[at] > 0) found.add(new int[] {k, at});
        }
        return found;
    }

    /** The line {@code position = origin + index * pitch} closest to the observations. */
    private static double[] leastSquares(List<int[]> observations) {
        double meanIndex = 0;
        double meanPosition = 0;
        for (int[] observation : observations) {
            meanIndex += observation[0];
            meanPosition += observation[1];
        }
        meanIndex /= observations.size();
        meanPosition /= observations.size();
        double covariance = 0;
        double variance = 0;
        for (int[] observation : observations) {
            double dx = observation[0] - meanIndex;
            covariance += dx * (observation[1] - meanPosition);
            variance += dx * dx;
        }
        double pitch = variance == 0 ? 0 : covariance / variance;
        return new double[] {meanPosition - pitch * meanIndex, pitch};
    }

    /** Linear interpolation, so the sweep can score a line between two pixels. */
    private static double sample(double[] values, double at) {
        if (at < 0 || at > values.length - 1) return 0;
        int floor = (int) Math.floor(at);
        if (floor >= values.length - 1) return values[values.length - 1];
        double fraction = at - floor;
        return values[floor] * (1 - fraction) + values[floor + 1] * fraction;
    }

    /** The strongest positions no two of which are within {@code radius}. */
    private static int[] strongestSeparated(double[] energy, int radius, int limit) {
        Integer[] order = new Integer[energy.length];
        for (int i = 0; i < energy.length; i++) order[i] = i;
        // Ties broken by position so the result does not depend on the sort.
        Arrays.sort(order, (a, b) -> energy[a] == energy[b]
                ? Integer.compare(a, b) : Double.compare(energy[b], energy[a]));
        List<Integer> taken = new ArrayList<>(limit);
        for (int candidate : order) {
            if (candidate < 1 || taken.size() >= limit) continue;
            boolean clear = true;
            for (int held : taken) {
                if (Math.abs(candidate - held) <= radius) {
                    clear = false;
                    break;
                }
            }
            if (clear) taken.add(candidate);
        }
        int[] out = new int[taken.size()];
        for (int i = 0; i < out.length; i++) out[i] = taken.get(i);
        return out;
    }

    private static double[] triangleBlur(double[] values, int halfWidth) {
        double[] kernel = new double[2 * halfWidth + 1];
        double total = 0;
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] = halfWidth + 1 - Math.abs(i - halfWidth);
            total += kernel[i];
        }
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            double sum = 0;
            for (int k = 0; k < kernel.length; k++) {
                int at = i + k - halfWidth;
                if (at >= 0 && at < values.length) sum += values[at] * kernel[k];
            }
            out[i] = sum / total;
        }
        return out;
    }

    private static double max(double[] values) {
        double best = 0;
        for (double value : values) best = Math.max(best, value);
        return best;
    }

    private static double medianOf(double[] values, int[] at) {
        double[] picked = new double[at.length];
        for (int i = 0; i < at.length; i++) picked[i] = values[at[i]];
        return median(picked);
    }

    private static double medianOfPositive(double[] values) {
        int kept = 0;
        for (double value : values) {
            if (value > 0) kept++;
        }
        if (kept == 0) return 0;
        double[] positive = new double[kept];
        int at = 0;
        for (double value : values) {
            if (value > 0) positive[at++] = value;
        }
        return median(positive);
    }

    private static double median(double[] values) {
        if (values.length == 0) return 0;
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }
}
