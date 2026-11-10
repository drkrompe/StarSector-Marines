package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How what lies on a natural drop's floor runs along it (`voids.md`, natural styles): water down a gorge, melt down
 * a lava rift, the frozen flow of a crevasse. Worked out once per map from the drop's open cells and the depth of its
 * floor, and read by every floor's drawing. GL-free.
 *
 * <p><b>Ends.</b> A floor flows only where it leaves the field: each run of open cells along the field's border is
 * an end. Of a channel's ends the shallowest is its source and every other an outlet, so a channel runs toward its
 * deeper end; ends as deep run toward the east and south. A channel with fewer than two ends is still: a pool, a
 * frozen crevasse, a lava lake.
 *
 * <p><b>Potential.</b> Between its source ({@code 0}) and its outlets ({@code 1}) a channel's potential is the
 * steady flow of a sheet as deep as its floor: {@code div(depth * grad(phi)) = 0}, nothing crossing the walls. So it
 * follows every bend, parts round a pier and runs faster where the channel narrows or shallows, the same water
 * squeezed through less. It is solved once, by over-relaxation from a guess of each cell's share of the way along.
 *
 * <p><b>Coordinates.</b> {@link #along} is the potential scaled to cells, so it runs about one a cell down a
 * channel of even width and depth; {@link #across} is the stream function, the flow passed on one side, scaled to
 * cells by the channel's mean depth. Together they lay a pattern along the channel, never across a bend's chord, and
 * {@link #speed} (the gradient of {@code along}) is how fast the flow runs, about 1 on an even reach.
 */
final class ChannelFlow {

    /** Over-relaxation: its factor, the most sweeps, and the change in a sweep below which the solve has settled. */
    static final float OMEGA = 1.85f;
    static final int MAX_SWEEPS = 3000;
    static final float SETTLED = 1e-6f;
    /** The least depth a cell carries flow through, metres, so a shallow fringe never dams the channel. */
    static final float MIN_DEPTH = .5f;

    final int w, h;
    /** Per cell: carries flow (open, and part of a channel with a source and an outlet). */
    final boolean[] flowing;
    /** Per cell centre: the potential in cells along the channel; 0 where still or closed. */
    final float[] along;
    /** Per cell corner, {@code (w+1) x (h+1)}: the stream function in cells across the channel. */
    final float[] across;
    /** Whether any of the field flows at all. */
    final boolean any;
    /**
     * Per cell corner: the flow's velocity, direction times its speed, the mean of the flowing cells round the corner;
     * 0 where none flows. Read through {@link #velocity}, for whatever is carried down the flow as a sheet.
     */
    private final float[] vx, vy;

    /**
     * @param open  cells the floor lies under: the drop's void and bridges
     * @param depth each cell's floor depth, metres
     */
    ChannelFlow(int w, int h, boolean[] open, float[] depth) {
        this.w = w;
        this.h = h;
        flowing = new boolean[w * h];
        along = new float[w * h];
        across = new float[(w + 1) * (h + 1)];
        boolean anyFlow = false;
        int[] component = components(open);
        int count = 0;
        for (int c : component) count = Math.max(count, c + 1);
        for (int c = 0; c < count; c++) anyFlow |= solve(open, depth, component, c);
        any = anyFlow;
        vx = new float[(w + 1) * (h + 1)];
        vy = new float[(w + 1) * (h + 1)];
        if (any) corners();
    }

    private void corners() {
        float[] cvx = new float[w * h], cvy = new float[w * h];
        float[] f = new float[5];
        for (int s = 0; s < w * h; s++) {
            if (!flowing[s]) continue;
            at(s % w + .5f, s / w + .5f, f);
            cvx[s] = f[2] * f[4];
            cvy[s] = f[3] * f[4];
        }
        int cw = w + 1;
        for (int j = 0; j <= h; j++) {
            for (int i = 0; i <= w; i++) {
                float sx = 0f, sy = 0f;
                int n = 0;
                for (int q = 0; q < 4; q++) {
                    int px = i - 1 + (q & 1), py = j - 1 + (q >> 1);
                    if (px < 0 || py < 0 || px >= w || py >= h || !flowing[py * w + px]) continue;
                    sx += cvx[py * w + px];
                    sy += cvy[py * w + px];
                    n++;
                }
                if (n > 0) { vx[j * cw + i] = sx / n; vy[j * cw + i] = sy / n; }
            }
        }
    }

    /** The flow's velocity at a point in cells into {@code out} ({@code {x, y}}, cells per cell along), bilinear. */
    void velocity(float x, float y, float[] out) {
        int cw = w + 1;
        float fx = Math.max(0f, Math.min(w - .001f, x)), fy = Math.max(0f, Math.min(h - .001f, y));
        int i = (int) fx, j = (int) fy;
        float tx = fx - i, ty = fy - j;
        int s = j * cw + i;
        out[0] = (vx[s] * (1 - tx) + vx[s + 1] * tx) * (1 - ty) + (vx[s + cw] * (1 - tx) + vx[s + cw + 1] * tx) * ty;
        out[1] = (vy[s] * (1 - tx) + vy[s + 1] * tx) * (1 - ty) + (vy[s + cw] * (1 - tx) + vy[s + cw + 1] * tx) * ty;
    }

    // ----- ends and potential -----

    private int[] components(boolean[] open) {
        int[] comp = new int[w * h];
        Arrays.fill(comp, -1);
        int[] queue = new int[w * h];
        int next = 0;
        for (int s = 0; s < w * h; s++) {
            if (!open[s] || comp[s] >= 0) continue;
            int head = 0, tail = 0;
            queue[tail++] = s;
            comp[s] = next;
            while (head < tail) {
                int c = queue[head++];
                int x = c % w, y = c / w;
                for (int d = 0; d < 4; d++) {
                    int nx = x + DX[d], ny = y + DY[d];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (open[n] && comp[n] < 0) { comp[n] = next; queue[tail++] = n; }
                }
            }
            next++;
        }
        return comp;
    }

    private static final int[] DX = {1, -1, 0, 0}, DY = {0, 0, 1, -1};

    /** The ends of one channel: runs of its cells along the field's border, in order round it. */
    private List<int[]> ends(int[] component, int c) {
        int perimeter = 2 * (w + h) - 4;
        int[] ring = new int[perimeter];
        int k = 0;
        for (int x = 0; x < w; x++) ring[k++] = x;
        for (int y = 1; y < h; y++) ring[k++] = y * w + w - 1;
        for (int x = w - 2; x >= 0; x--) ring[k++] = (h - 1) * w + x;
        for (int y = h - 2; y >= 1; y--) ring[k++] = y * w;
        List<int[]> ends = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < perimeter; i++) if (component[ring[i]] != c) { start = i; break; }
        if (start < 0) return ends;
        List<Integer> run = new ArrayList<>();
        for (int j = 1; j <= perimeter; j++) {
            int cell = ring[(start + j) % perimeter];
            if (component[cell] == c) {
                run.add(cell);
            } else if (!run.isEmpty()) {
                ends.add(run.stream().mapToInt(Integer::intValue).toArray());
                run.clear();
            }
        }
        if (!run.isEmpty()) ends.add(run.stream().mapToInt(Integer::intValue).toArray());
        return ends;
    }

    private boolean solve(boolean[] open, float[] depth, int[] component, int c) {
        List<int[]> ends = ends(component, c);
        if (ends.size() < 2) return false;
        // The source: the shallowest end; ends as deep give the one farthest north and west.
        int source = 0;
        float best = Float.MAX_VALUE;
        for (int e = 0; e < ends.size(); e++) {
            float d = 0f, place = 0f;
            for (int cell : ends.get(e)) { d += depth[cell]; place += cell % w + cell / w; }
            d /= ends.get(e).length;
            place /= ends.get(e).length;
            float key = Math.round(d) * 1e5f + place;
            if (key < best) { best = key; source = e; }
        }
        // 0 at the source, 1 at every outlet, fixed; the rest free.
        byte[] fixed = new byte[w * h];
        for (int e = 0; e < ends.size(); e++) for (int cell : ends.get(e)) fixed[cell] = (byte) (e == source ? 1 : 2);
        int[] fromSource = steps(component, c, fixed, (byte) 1);
        int[] toOutlet = steps(component, c, fixed, (byte) 2);
        float[] phi = new float[w * h];
        float length = 0f;
        int outlets = 0;
        for (int s = 0; s < w * h; s++) {
            if (component[s] != c) continue;
            flowing[s] = true;
            int a = fromSource[s], b = toOutlet[s];
            phi[s] = a + b == 0 ? 0f : a / (float) (a + b);
            if (fixed[s] == 2) { length += a; outlets++; }
        }
        length = Math.max(1f, length / Math.max(1, outlets));
        float[] k = new float[w * h];
        for (int s = 0; s < w * h; s++) if (component[s] == c) k[s] = Math.max(MIN_DEPTH, depth[s]);
        for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {
            float most = 0f;
            for (int s = 0; s < w * h; s++) {
                if (component[s] != c || fixed[s] != 0) continue;
                int x = s % w, y = s / w;
                float sum = 0f, weight = 0f;
                for (int d = 0; d < 4; d++) {
                    int nx = x + DX[d], ny = y + DY[d];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (component[n] != c) continue;
                    float g = 2f * k[s] * k[n] / (k[s] + k[n]);
                    sum += g * phi[n];
                    weight += g;
                }
                if (weight <= 0f) continue;
                float change = OMEGA * (sum / weight - phi[s]);
                phi[s] += change;
                most = Math.max(most, Math.abs(change));
            }
            if (most < SETTLED) break;
        }
        for (int s = 0; s < w * h; s++) if (component[s] == c) along[s] = phi[s] * length;
        streams(component, c, k, phi, length);
        return true;
    }

    /** Steps through the channel from the cells marked {@code mark}, 4-connected. */
    private int[] steps(int[] component, int c, byte[] fixed, byte mark) {
        int[] dist = new int[w * h];
        Arrays.fill(dist, Integer.MAX_VALUE);
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        for (int s = 0; s < w * h; s++) if (component[s] == c && fixed[s] == mark) { dist[s] = 0; queue[tail++] = s; }
        while (head < tail) {
            int cur = queue[head++];
            int x = cur % w, y = cur / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                if (component[n] != c || dist[n] != Integer.MAX_VALUE) continue;
                dist[n] = dist[cur] + 1;
                queue[tail++] = n;
            }
        }
        for (int s = 0; s < w * h; s++) if (dist[s] == Integer.MAX_VALUE) dist[s] = 0;
        return dist;
    }

    /**
     * {@link #across}: from one corner of the channel, the flow passed between each corner and the next along the
     * face between them, so it is the same on a whole streamline. The channel's mean depth turns it to cells.
     */
    private void streams(int[] component, int c, float[] k, float[] phi, float length) {
        int cw = w + 1, ch = h + 1;
        float meanDepth = 0f;
        int cells = 0;
        for (int s = 0; s < w * h; s++) if (component[s] == c) { meanDepth += k[s]; cells++; }
        meanDepth /= Math.max(1, cells);
        float scale = length / meanDepth;
        boolean[] seen = new boolean[cw * ch];
        int[] queue = new int[cw * ch];
        int start = -1;
        for (int s = 0; s < w * h && start < 0; s++) if (component[s] == c) start = (s / w) * cw + s % w;
        int head = 0, tail = 0;
        queue[tail++] = start;
        seen[start] = true;
        across[start] = 0f;
        while (head < tail) {
            int cur = queue[head++];
            int x = cur % cw, y = cur / cw;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx < 0 || ny < 0 || nx >= cw || ny >= ch) continue;
                int n = ny * cw + nx;
                if (seen[n]) continue;
                // The face from corner (x, y) to the next lies between two cells; the flow through it, if it is the
                // channel's on both sides, is the potential's drop across it. A face along a wall passes none.
                int ax, ay, bx, by;
                if (DY[d] == 0) { int fx = Math.min(x, nx); ax = fx; ay = y - 1; bx = fx; by = y; }
                else { int fy = Math.min(y, ny); ax = x - 1; ay = fy; bx = x; by = fy; }
                boolean aIn = inside(component, c, ax, ay), bIn = inside(component, c, bx, by);
                if (!aIn && !bIn) continue;
                float flux = 0f;
                if (aIn && bIn) {
                    int a = ay * w + ax, b = by * w + bx;
                    float g = 2f * k[a] * k[b] / (k[a] + k[b]);
                    flux = g * (phi[a] - phi[b]) * scale;
                }
                // Turning a step along x or y into the flow across it, with one sense for both.
                across[n] = across[cur] + (DY[d] == 0 ? -flux * DX[d] : flux * DY[d]);
                seen[n] = true;
                queue[tail++] = n;
            }
        }
    }

    private boolean inside(int[] component, int c, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && component[y * w + x] == c;
    }

    // ----- sampling -----

    /**
     * The flow at a point in cells into {@code out}: {@code {along, across, dirX, dirY, speed}}. Still or off the
     * channel: {@code {x, y, 1, 0, 0}}, so a still floor's pattern lies on the map's own axes.
     */
    void at(float x, float y, float[] out) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (cx < 0 || cy < 0 || cx >= w || cy >= h || !flowing[cy * w + cx]) {
            out[0] = x; out[1] = y; out[2] = 1f; out[3] = 0f; out[4] = 0f;
            return;
        }
        out[0] = alongAt(x, y);
        out[1] = acrossAt(x, y);
        float gx = (alongAt(x + .5f, y) - alongAt(x - .5f, y));
        float gy = (alongAt(x, y + .5f) - alongAt(x, y - .5f));
        float len = (float) Math.hypot(gx, gy);
        out[2] = len > 1e-5f ? gx / len : 1f;
        out[3] = len > 1e-5f ? gy / len : 0f;
        out[4] = len;
    }

    /** The potential between cell centres, from flowing cells only, so a wall never drags it. */
    float alongAt(float x, float y) {
        float fx = x - .5f, fy = y - .5f;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fy);
        float tx = fx - i, ty = fy - j;
        float sum = 0f, weight = 0f;
        for (int q = 0; q < 4; q++) {
            int px = i + (q & 1), py = j + (q >> 1);
            if (px < 0 || py < 0 || px >= w || py >= h || !flowing[py * w + px]) continue;
            float wq = ((q & 1) == 0 ? 1f - tx : tx) * ((q >> 1) == 0 ? 1f - ty : ty);
            sum += wq * along[py * w + px];
            weight += wq;
        }
        if (weight > 1e-6f) return sum / weight;
        int cx = Math.max(0, Math.min(w - 1, (int) Math.floor(x))), cy = Math.max(0, Math.min(h - 1, (int) Math.floor(y)));
        return along[cy * w + cx];
    }

    /** The stream function between cell corners. */
    float acrossAt(float x, float y) {
        int cw = w + 1;
        float fx = Math.max(0f, Math.min(w - .001f, x)), fy = Math.max(0f, Math.min(h - .001f, y));
        int i = (int) fx, j = (int) fy;
        float tx = fx - i, ty = fy - j;
        int s = j * cw + i;
        return (across[s] * (1 - tx) + across[s + 1] * tx) * (1 - ty) + (across[s + cw] * (1 - tx) + across[s + cw + 1] * tx) * ty;
    }
}
