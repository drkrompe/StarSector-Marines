package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a {@link Line}'s works go, before any ground is touched: the path
 * rastered into a 4-connected course, split wherever a road crosses it (with
 * a margin either side, the gap), the runs between, a wire course stood off
 * in front of each run with a funnel opened in it at a fixed pitch, and a nest
 * at each end of a run long enough to hold two (and, at a pitch, between).
 * A dug line is deeper than its course by rows behind it, and takes a parapet
 * row on its front lip, its wire standing further out. Pure and deterministic.
 */
public final class LineCourse {

    /** Cells the wire stands in front of the works. */
    public static final int WIRE_OFFSET = 2;
    /** Cells the wire stands in front of a course with a parapet: the parapet, then two cells of open ground. */
    public static final int PARAPET_WIRE_OFFSET = 4;
    /** Cells across a funnel opening. */
    public static final int FUNNEL_WIDTH = 2;
    /** Shortest run kept: anything shorter is left unworked beside its gap. */
    public static final int MIN_RUN = 3;
    /** Shortest run that takes a nest at each end. */
    public static final int MIN_NESTED_RUN = 8;

    private LineCourse() {}

    /** Whether a world cell answers yes. */
    @FunctionalInterface
    public interface CellTest {
        boolean test(int x, int y);
    }

    /**
     * The planned course. {@code runs} are the works' cells (nest cells taken
     * out of them), {@code gaps} each road crossing's cells including its
     * margins, {@code wire} the wire cells in front, {@code funnels} the cells
     * left open in it, {@code nests} the run ends (and the pitched nests
     * between), {@code rear} the rows dug behind the runs, and {@code parapet}
     * the row on the runs' front lip. No cell is in two of runs, rear, parapet
     * and wire.
     */
    public record Course(List<List<int[]>> runs, List<List<int[]>> gaps, List<int[]> wire,
                         List<int[]> funnels, List<int[]> nests, List<int[]> rear, List<int[]> parapet) {}

    /** A one-row course, no parapet, nests at run ends only. */
    public static Course plan(List<int[]> path, CellTest isRoad, int roadGap, int[] frontward, int funnelEvery) {
        return plan(path, isRoad, roadGap, frontward, funnelEvery, 1, false, 0);
    }

    /**
     * The planned course, {@code depth} rows deep (the course and the rows
     * behind it), with a parapet row in front when asked for, and a nest every
     * {@code nestEvery} cells along a nested run between its ends (none at 0).
     */
    public static Course plan(List<int[]> path, CellTest isRoad, int roadGap, int[] frontward, int funnelEvery,
                              int depth, boolean parapet, int nestEvery) {
        int wireOffset = parapet ? PARAPET_WIRE_OFFSET : WIRE_OFFSET;
        List<int[]> cells = raster(path);
        int n = cells.size();
        boolean[] road = new boolean[n];
        for (int i = 0; i < n; i++) road[i] = isRoad.test(cells.get(i)[0], cells.get(i)[1]);
        boolean[] blocked = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!road[i]) continue;
            for (int j = Math.max(0, i - roadGap); j <= Math.min(n - 1, i + roadGap); j++) blocked[j] = true;
        }
        List<List<int[]>> runs = new ArrayList<>();
        List<List<int[]>> gaps = new ArrayList<>();
        List<int[]> wire = new ArrayList<>();
        List<int[]> funnels = new ArrayList<>();
        List<int[]> nests = new ArrayList<>();
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n && blocked[j] == blocked[i]) j++;
            List<int[]> span = new ArrayList<>(cells.subList(i, j));
            if (blocked[i]) {
                gaps.add(span);
            } else if (span.size() >= MIN_RUN) {
                for (int k = 0; k < span.size(); k++) {
                    int[] c = span.get(k);
                    int[] w = {c[0] + frontward[0] * wireOffset, c[1] + frontward[1] * wireOffset};
                    int phase = funnelEvery > 0 ? k % funnelEvery : -1;
                    boolean open = funnelEvery > 0 && phase >= funnelEvery / 2
                            && phase < funnelEvery / 2 + FUNNEL_WIDTH;
                    if (open) funnels.add(w);
                    else wire.add(w);
                }
                if (span.size() >= MIN_NESTED_RUN) {
                    List<Integer> at = new ArrayList<>();
                    at.add(0);
                    // Between the ends, one at each pitch that leaves at least half a pitch to the far end.
                    if (nestEvery > 0) for (int k = nestEvery; k <= span.size() - 1 - nestEvery / 2; k += nestEvery) at.add(k);
                    at.add(span.size() - 1);
                    List<int[]> kept = new ArrayList<>(span.size());
                    for (int k = 0; k < span.size(); k++) {
                        if (at.contains(k)) nests.add(span.get(k));
                        else kept.add(span.get(k));
                    }
                    span = kept;
                }
                runs.add(span);
            }
            i = j;
        }
        // The rows behind, then the parapet: each only where no earlier row of the works already stands, since a
        // course that steps toward the front puts one cell's row on its neighbour.
        Set<Long> taken = new HashSet<>();
        for (List<int[]> run : runs) for (int[] c : run) taken.add(key(c));
        for (int[] c : nests) taken.add(key(c));
        List<int[]> rear = new ArrayList<>();
        List<int[]> lip = new ArrayList<>();
        List<int[]> course = new ArrayList<>();
        for (List<int[]> run : runs) course.addAll(run);
        course.addAll(nests);
        for (int row = 1; row < depth; row++) {
            for (int[] c : course) {
                int[] r = {c[0] - frontward[0] * row, c[1] - frontward[1] * row};
                if (taken.add(key(r))) rear.add(r);
            }
        }
        if (parapet) {
            for (int[] c : course) {
                int[] p = {c[0] + frontward[0], c[1] + frontward[1]};
                if (taken.add(key(p))) lip.add(p);
            }
        }
        List<int[]> openWire = new ArrayList<>(), openFunnels = new ArrayList<>();
        for (int[] w : dedupe(wire)) if (!taken.contains(key(w))) openWire.add(w);
        for (int[] f : dedupe(funnels)) if (!taken.contains(key(f))) openFunnels.add(f);
        return new Course(runs, gaps, openWire, openFunnels, nests, rear, lip);
    }

    private static long key(int[] c) {
        return (long) c[0] << 32 | (c[1] & 0xffffffffL);
    }

    /** The polyline through {@code path}'s vertices as 4-connected cells, each once, in order. */
    public static List<int[]> raster(List<int[]> path) {
        List<int[]> out = new ArrayList<>();
        for (int v = 0; v + 1 < path.size(); v++) {
            int x0 = path.get(v)[0], y0 = path.get(v)[1], x1 = path.get(v + 1)[0], y1 = path.get(v + 1)[1];
            int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
            int sx = Integer.signum(x1 - x0), sy = Integer.signum(y1 - y0);
            int x = x0, y = y0, ix = 0, iy = 0;
            if (out.isEmpty()) out.add(new int[]{x, y});
            while (ix < dx || iy < dy) {
                double tx = dx == 0 ? Double.POSITIVE_INFINITY : (0.5 + ix) / dx;
                double ty = dy == 0 ? Double.POSITIVE_INFINITY : (0.5 + iy) / dy;
                if (tx < ty) {
                    x += sx;
                    ix++;
                } else {
                    y += sy;
                    iy++;
                }
                out.add(new int[]{x, y});
            }
        }
        return dedupe(out);
    }

    private static List<int[]> dedupe(List<int[]> cells) {
        Set<Long> seen = new HashSet<>();
        List<int[]> out = new ArrayList<>(cells.size());
        for (int[] c : cells) if (seen.add((long) c[0] << 32 | (c[1] & 0xffffffffL))) out.add(c);
        return out;
    }
}
