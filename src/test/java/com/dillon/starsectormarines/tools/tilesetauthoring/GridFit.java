package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where a sheet's stated grid really sits, measured from the art.
 *
 * <p>A tileable plate is drawn cell against cell, so the boundary between two
 * cells is a line the art changes across. Summing that change down the whole
 * sheet — {@code sum_y |I[y][x] - I[y][x-1]|} for a column line — makes the real
 * boundaries stand out as peaks. Given a <em>stated</em> cell count, an origin
 * and a pitch can be fitted to those peaks: two parameters against
 * {@code count - 1} observations.
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
 * lower rows are empty gives no seams to fit there, and a strip of props with
 * gutters between them has its boundaries in the gaps rather than on any edge —
 * so every fit reports how many real seams it found and how far they sit from
 * the straight line through them, and {@link Axis#trustworthy()} answers
 * honestly. A caller that applies an untrustworthy axis anyway must be doing so
 * because it was told to.
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
     * One measured boundary.
     *
     * @param index    which grid line this is, 1 .. count-1
     * @param position where the art actually changes, in sheet pixels
     * @param strong   whether the change is clearly above the sheet's background
     *                 gradient rather than a peak in noise
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
    public record Axis(int count, double origin, double pitch, List<Seam> seams,
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
                    "%s: %d cells from %.2f every %.2f px; %d of %d boundaries on a real seam, "
                            + "worst %.1f px off the line, rms %.1f (tolerance %.1f) — %s",
                    axisName, count, origin, pitch, strongSeams(), count - 1,
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
                fit(columnEnergy(luminance), stated.cols()),
                fit(rowEnergy(luminance), stated.rows()));
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
     * Place {@code count} cells on one axis of measured seam energy.
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
    public static Axis fit(double[] energy, int count) {
        int extent = energy.length;
        if (count < 2 || extent < 2) {
            return new Axis(count, 0, Math.max(1, extent), List.of(), 0, 0);
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
        if (snapped.isEmpty()) return new Axis(count, origin, pitch, List.of(), 0, 0);

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
        return new Axis(count, origin, pitch, List.copyOf(seams),
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
