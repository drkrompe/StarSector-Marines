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
 * at each end of a run long enough to hold two. Pure and deterministic.
 */
public final class LineCourse {

    /** Cells the wire stands in front of the works. */
    public static final int WIRE_OFFSET = 2;
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
     * left open in it, and {@code nests} the run ends.
     */
    public record Course(List<List<int[]>> runs, List<List<int[]>> gaps, List<int[]> wire,
                         List<int[]> funnels, List<int[]> nests) {}

    public static Course plan(List<int[]> path, CellTest isRoad, int roadGap, int[] frontward, int funnelEvery) {
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
                    int[] w = {c[0] + frontward[0] * WIRE_OFFSET, c[1] + frontward[1] * WIRE_OFFSET};
                    int phase = funnelEvery > 0 ? k % funnelEvery : -1;
                    boolean open = funnelEvery > 0 && phase >= funnelEvery / 2
                            && phase < funnelEvery / 2 + FUNNEL_WIDTH;
                    if (open) funnels.add(w);
                    else wire.add(w);
                }
                if (span.size() >= MIN_NESTED_RUN) {
                    nests.add(span.get(0));
                    nests.add(span.get(span.size() - 1));
                    span = new ArrayList<>(span.subList(1, span.size() - 1));
                }
                runs.add(span);
            }
            i = j;
        }
        return new Course(runs, gaps, dedupe(wire), dedupe(funnels), nests);
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
