package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The shape of a natural drop or rise (`voids.md`, natural styles), worked out once per map from its cells and
 * drawn by {@link NaturalVoidRenderSystem}. GL-free.
 *
 * <p>A drop and a rise are one shape with the sign of its depth turned: a drop's steps go down into the void
 * from its edge, a rise's go up into the high ground from its foot. Depth is measured downward throughout, so
 * a rise's steps stand at negative depths.
 *
 * <p><b>Distance in.</b> Every point of the void has a distance, in cells, to the nearest ground that stands
 * (not a bridge). Each of the {@link NaturalVoid#steps} begins where that distance, roughened by its own slow
 * and fine noise, passes the step's inset, so the edge and each ledge below wander independently of the
 * cells. A point stands at the depth of the deepest step it has passed; the steps' regions are nested by
 * construction ({@link #h}), so a ledge may pinch out where two walls meet but the drop never rises again.
 *
 * <p><b>Pieces.</b> The void is cut into triangles {@link #RES} to a cell's side and each is clipped, its
 * fields taken as linear across it, into the {@link Band} it lies in (a flat piece at one depth) and the
 * {@link Wall} segments where it crosses a step's line (a face between two depths). A band's flat pieces are
 * lit once here: the sun, and how much sky the walls around leave them; a wall's light is worked out by
 * depth along it.
 *
 * <p><b>Depth along the shape.</b> A shape with {@link Shape#marks} deepens and shallows along its length: each
 * step's depth is scaled at every point by a {@link #gain} blended from the marks, so the ledges keep their share
 * of the floor's depth wherever it lies. The marks change depth only: where the lip, the ledges and the floor lie
 * across the drop is the insets' alone. A band's pieces then stand at the depth of their own corners
 * ({@link Band#z}), and a wall's at the depth of its two ends ({@link Wall#g0}). A shape with no marks is as
 * deep everywhere as its steps, worked out exactly as before they were added.
 *
 * <p><b>Bridges.</b> Ground the walkable surface carries across the void too narrow to stand on rock of its
 * own (opened away by {@link #BRIDGE_CELLS}), joining wider ground at two ends, is a built span: the drop
 * runs on under it, and it is a thin
 * slab to the sun.
 */
final class NaturalVoidField {

    /** Triangle corners to a cell's side. */
    static final int RES = 4;
    /** Ground narrower than this, in cells, between two sides of a void is a bridge rather than rock. */
    static final int BRIDGE_CELLS = 7;
    /** How deep a bridge's deck is, in metres: what it shades the drop with, and how far its sides show. */
    static final float DECK_METERS = 1.2f;
    /** How far the edge and each ledge wander about their inset, in cells: slow swings and fine breaks. */
    static final float WANDER_SLOW = 1.1f, WANDER_FINE = .35f;
    /** How far, in cells, the fray fades from full cover into the turf at its outer end. */
    static final float FRAY_FADE = .35f;
    /** How far the edge is smoothed either way, in cells. */
    static final float SMOOTH_CELLS = 1.25f;
    /** How far the lip frays onto the ground, in cells: at least, and how much further it wanders. */
    static final float FRAY_MIN = .55f, FRAY_WANDER = .5f;
    /** How far out the sky is looked for round a point, in cells, and in how many directions. */
    static final int SKY_REACH = 40, SKY_DIRS = 8;
    /** Shade samples to a cell's side on the ground at a rise's foot. */
    static final int SHADE_RES = 4;
    /** Metres of wall lit as one, top to bottom. */
    static final float WALL_LIGHT_METERS = 1.5f;
    /** How far from a depth mark, in cells, its weight starts to fall away: the blend between marks is this soft. */
    static final float MARK_SOFT_CELLS = 6f;

    final int w, h;
    final Shape shape;
    /** A rise: the steps climb from the walkable ground rather than fall from it. */
    final boolean rising;
    /** The highest point of the shape, as a depth: {@code 0} for a drop, the top step's for a rise. */
    final float top;
    /** Depth of each step's top, metres: {@code 0} for the lip, then each step's. */
    final float[] depth;
    /** Cells the drop shows in: void and bridges. */
    final boolean[] open;
    /** Bridge cells: walkable ground over the drop. */
    final boolean[] bridge;
    /** Per corner sample, {@code (w*RES+1) x (h*RES+1)}: distance in, and each step's reach {@code h[k]}. */
    private final int sw, sh;
    final float[] dist;
    /**
     * Signed distance to the drop's edge, positive into the void, smoothed over {@link #SMOOTH_CELLS}: the
     * line the lip, the ledges and the fray are drawn from, so none of them follows the cells' stair steps.
     */
    final float[] edge;
    /**
     * {@code h[k] > 0} where the drop is at least step {@code k} deep ({@code k >= 1}); nested, {@code
     * h[k] >= h[k+1]}. {@code h[0]} is the distance itself: the lip runs from the cells' edge to step 1.
     */
    final float[][] reach;
    /**
     * Per corner sample, the share of the steps' depth the shape stands at there ({@link Shape#marks}); null where
     * it has none and stands at its steps' depth everywhere.
     */
    final float[] gain;
    /** The most of its steps' depth the shape stands at anywhere: 1 without marks. */
    final float mostGain;
    /** How the drop's floor runs along it ({@link ChannelFlow}); null for a rise, whose floor is the battle's ground. */
    final ChannelFlow flow;

    /** Flat pieces of one depth, step {@code k}: the lip ({@code 0}), each ledge, and the floor last. */
    final List<Band> bands = new ArrayList<>();
    /** Faces going down from step {@code k-1} to step {@code k}, at {@code walls.get(k-1)}. */
    final List<List<Wall>> walls = new ArrayList<>();
    /**
     * The lip's fray over the ground beside the drop: earth and rock breaking through the turf a little way
     * onto the walkable cells, so the drop's edge is not the cells' edge. Drawn over the ground tiles; each
     * quad's {@code spare} is its cover, fading out into the turf.
     */
    final Band fray = new Band(0, 0f);
    /**
     * A rise's shadow on the walkable ground at its foot, drawn over the tiles: quads whose {@code spare} is
     * the shade's strength. Empty for a drop, whose ground the sun's own composite shades.
     */
    final Band floorShade = new Band(0, 0f);
    /** Bridge sides along the drop: {@code {x0, y0, x1, y1, normalX, normalY}} in cells. */
    final List<float[]> bridgeSides = new ArrayList<>();

    /** The pieces at one depth: quads {@code {x0, y0, .. x3, y3}} in cells, with their light and look. */
    static final class Band {
        final int step;
        /** The step's depth on the shape's steps, metres; each piece's own where the shape has marks, in {@link #z}. */
        final float depth;
        /** Eight floats a quad (a triangle repeats its last corner). */
        float[] quads = new float[0];
        /** Per quad: {@code {centreX, centreY, light, reachToNext, flowX, flowY, sky, spare}}. */
        float[] attrs = new float[0];
        /**
         * Per quad, the depth of its four corners and its centre, metres, five floats; null where the shape has no
         * marks and every piece stands at {@link #depth}.
         */
        float[] z;
        int count;

        /**
         * The floor's pieces only: per quad, the flow at its centre, {@code {along, across, dirX, dirY, speed}} as
         * {@link ChannelFlow#at} gives it; null on every other band.
         */
        float[] flow;

        /** Corner {@code v} of quad {@code q}'s depth ({@code v == 4} its centre). */
        float z(int q, int v) { return z == null ? depth : z[q * 5 + v]; }

        Band(int step, float depth) { this.step = step; this.depth = depth; }

        void add(float[] poly, int n, float[] attr) {
            // A convex polygon of up to five corners, as quads: (0,1,2,3) and (0,3,4,4).
            emit(poly, 0, 1, 2, n > 3 ? 3 : 2, attr);
            if (n == 5) emit(poly, 0, 3, 4, 4, attr);
        }

        private void emit(float[] p, int a, int b, int c, int d, float[] attr) {
            if (count * 8 + 8 > quads.length) {
                quads = Arrays.copyOf(quads, Math.max(64, quads.length * 2));
                attrs = Arrays.copyOf(attrs, Math.max(64, attrs.length * 2));
            }
            int o = count * 8;
            quads[o] = p[a * 2]; quads[o + 1] = p[a * 2 + 1];
            quads[o + 2] = p[b * 2]; quads[o + 3] = p[b * 2 + 1];
            quads[o + 4] = p[c * 2]; quads[o + 5] = p[c * 2 + 1];
            quads[o + 6] = p[d * 2]; quads[o + 7] = p[d * 2 + 1];
            System.arraycopy(attr, 0, attrs, o, 8);
            count++;
        }
    }

    /**
     * A segment of a step's line, {@code (x0, y0)} to {@code (x1, y1)} in cells, with the face's outward normal
     * (into the drop, or out of a rise toward the lower ground), the depths of its top and foot, and its light
     * down its height, one value each {@link #WALL_LIGHT_METERS} from its top.
     */
    record Wall(float x0, float y0, float x1, float y1, float nx, float ny, float zTop, float zFoot, float[] light,
                float g0, float g1) {
        /** A wall as deep at both ends as its steps. */
        Wall(float x0, float y0, float x1, float y1, float nx, float ny, float zTop, float zFoot, float[] light) {
            this(x0, y0, x1, y1, nx, ny, zTop, zFoot, light, 1f, 1f);
        }
    }

    /**
     * The steps as the field reads them: {@code depths} and {@code insets} for steps {@code 1..n}, depth signed
     * downward; {@code spans} when narrow ground across the shape is a bridge; {@code wander} how far the edge
     * and ledges swing about their insets, as a share of the natural styles' ({@link #WANDER_SLOW}): an open pit's
     * benches are cut, not eroded, so they wander much less; {@code marks} {@code {x, y, gain}} in the field's cells,
     * each a place where the shape stands at {@code gain} of its steps' depth, empty for none.
     */
    record Shape(float[] depths, float[] insets, int seed, boolean spans, float wander, float[] marks) {
        /** How little an open pit's benches wander, as a share of a natural drop's ledges. */
        static final float CUT_WANDER = .22f;

        Shape(float[] depths, float[] insets, int seed, boolean spans, float wander) {
            this(depths, insets, seed, spans, wander, new float[0]);
        }

        /** This shape on a field whose cells are the map's moved by {@code (dx, dy)}. */
        Shape shifted(float dx, float dy) {
            float[] m = marks.clone();
            for (int i = 0; i < m.length; i += 3) { m[i] += dx; m[i + 1] += dy; }
            return new Shape(depths, insets, seed, spans, wander, m);
        }

        static Shape of(NaturalVoid look) {
            int n = look.steps().size();
            float[] d = new float[n], in = new float[n];
            for (int k = 0; k < n; k++) {
                d[k] = look.steps().get(k).depthMeters();
                in[k] = look.steps().get(k).insetCells();
            }
            boolean pit = look.style() == NaturalVoid.Style.OPEN_PIT;
            float wander = pit ? CUT_WANDER : 1f;
            // An open pit's benches are cut level: its haul road is laid from their depths.
            List<NaturalVoid.DepthMark> marks = pit || look.marks() == null ? List.of() : look.marks();
            float[] m = new float[marks.size() * 3];
            for (int i = 0; i < marks.size(); i++) {
                NaturalVoid.DepthMark mark = marks.get(i);
                m[i * 3] = mark.x(); m[i * 3 + 1] = mark.y(); m[i * 3 + 2] = mark.floorMeters() / look.floorMeters();
            }
            return new Shape(d, in, look.seed(), true, wander, m);
        }

        static Shape of(NaturalRise look) {
            int n = look.steps().size();
            float[] d = new float[n], in = new float[n];
            for (int k = 0; k < n; k++) {
                d[k] = -look.steps().get(k).heightMeters();
                in[k] = look.steps().get(k).insetCells();
            }
            List<NaturalRise.HeightMark> marks = look.marks() == null ? List.of() : look.marks();
            float[] m = new float[marks.size() * 3];
            for (int i = 0; i < marks.size(); i++) {
                NaturalRise.HeightMark mark = marks.get(i);
                m[i * 3] = mark.x(); m[i * 3 + 1] = mark.y(); m[i * 3 + 2] = mark.topMeters() / look.topMeters();
            }
            // Narrow ground between two rises is a pass along the ravine floor, not a span over anything.
            return new Shape(d, in, look.seed(), false, 1f, m);
        }
    }

    NaturalVoidField(int w, int h, boolean[] voidCell, NaturalVoid look, float sunX, float sunY, float risePerCell) {
        this(w, h, voidCell, Shape.of(look), sunX, sunY, risePerCell);
    }

    NaturalVoidField(int w, int h, boolean[] voidCell, Shape shape, float sunX, float sunY, float risePerCell) {
        this.w = w;
        this.h = h;
        this.shape = shape;
        int n = shape.depths().length;
        depth = new float[n + 1];
        for (int k = 0; k < n; k++) depth[k + 1] = shape.depths()[k];
        rising = n > 0 && depth[n] < 0f;
        sw = w * RES + 1;
        sh = h * RES + 1;
        gain = shape.marks().length == 0 ? null : gains(shape.marks());
        float most = 1f;
        if (gain != null) { most = 0f; for (float g : gain) most = Math.max(most, g); }
        mostGain = most;
        float highest = 0f;
        for (float d : depth) highest = Math.min(highest, d * most);
        top = highest;
        bridge = shape.spans() ? bridges(w, h, voidCell) : new boolean[w * h];
        open = new boolean[w * h];
        for (int i = 0; i < w * h; i++) open[i] = voidCell[i] || bridge[i];
        dist = distanceIn();
        edge = smoothEdge();
        reach = new float[n + 1][];
        reach[0] = dist;
        for (int k = n; k >= 1; k--) {
            float inset = shape.insets()[k - 1];
            float[] r = new float[sw * sh];
            for (int j = 0; j < sh; j++) {
                for (int i = 0; i < sw; i++) {
                    int s = j * sw + i;
                    float x = i / (float) RES, y = j / (float) RES;
                    float wander = shape.wander() * (WANDER_SLOW * Noise.fbm(x / 9f, y / 9f, shape.seed() * 31 + k)
                            + WANDER_FINE * Noise.fbm(x / 1.6f, y / 1.6f, shape.seed() * 57 + k));
                    float own = edge[s] + wander - inset;
                    r[s] = k == n ? own : Math.max(own, reach[k + 1][s]);
                }
            }
            reach[k] = r;
        }
        for (int k = 0; k <= n; k++) bands.add(new Band(k, depth[k]));
        for (int k = 1; k <= n; k++) walls.add(new ArrayList<>());
        cut(sunX, sunY, risePerCell);
        if (gain != null) for (Band band : bands) cornerDepths(band);
        flow = rising ? null : flow(n);
        fray();
        sides();
        if (rising) floorShade(sunX, sunY, risePerCell);
    }

    int steps() { return depth.length - 1; }

    /**
     * {@link #gain}: at each sample, the marks' gains blended by weights falling away with the square of the
     * distance past {@link #MARK_SOFT_CELLS}, so a place near one mark takes its depth and the drop runs smoothly
     * from one to the next.
     */
    private float[] gains(float[] marks) {
        float[] g = new float[sw * sh];
        float soft = MARK_SOFT_CELLS * MARK_SOFT_CELLS;
        for (int j = 0; j < sh; j++) {
            for (int i = 0; i < sw; i++) {
                float x = i / (float) RES, y = j / (float) RES;
                double sum = 0, weight = 0;
                for (int m = 0; m < marks.length; m += 3) {
                    float dx = x - marks[m], dy = y - marks[m + 1];
                    double d2 = dx * dx + dy * dy + soft;
                    double wgt = 1.0 / (d2 * d2);
                    sum += wgt * marks[m + 2];
                    weight += wgt;
                }
                g[j * sw + i] = (float) (sum / weight);
            }
        }
        return g;
    }

    /** The share of its steps' depth the shape stands at, at a point in cells: exactly 1 without marks. */
    float gainAt(float x, float y) {
        return gain == null ? 1f : sample(gain, x, y);
    }

    /** How deep step {@code k} lies at a point, metres: its depth on the steps, scaled by the {@link #gainAt}. */
    float stepDepth(int k, float x, float y) {
        return gain == null ? depth[k] : depth[k] * sample(gain, x, y);
    }

    /** The floor's flow, worked out over the open cells at the floor's depth there, and laid on its pieces. */
    private ChannelFlow flow(int n) {
        float[] floorDepth = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) floorDepth[y * w + x] = stepDepth(n, x + .5f, y + .5f);
        ChannelFlow f = new ChannelFlow(w, h, open, floorDepth);
        Band floor = bands.get(n);
        float[] at = new float[5];
        floor.flow = new float[floor.count * 5];
        for (int q = 0; q < floor.count; q++) {
            f.at(floor.attrs[q * 8], floor.attrs[q * 8 + 1], at);
            System.arraycopy(at, 0, floor.flow, q * 5, 5);
        }
        return f;
    }

    /** Each piece's corners and centre at their own depth along the shape. */
    private void cornerDepths(Band band) {
        float[] z = new float[band.count * 5];
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            for (int v = 0; v < 4; v++) z[q * 5 + v] = stepDepth(band.step, band.quads[o + v * 2], band.quads[o + v * 2 + 1]);
            z[q * 5 + 4] = stepDepth(band.step, band.attrs[o], band.attrs[o + 1]);
        }
        band.z = z;
    }

    // ----- shape -----

    /** Ground too narrow to stand on rock of its own, between parts of the void. */
    static boolean[] bridges(int w, int h, boolean[] voidCell) {
        boolean[] land = new boolean[w * h];
        for (int i = 0; i < w * h; i++) land[i] = !voidCell[i];
        int r = BRIDGE_CELLS / 2;
        boolean[] opened = grow(shrink(land, w, h, r), w, h, r);
        boolean[] narrow = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!land[i] || opened[i]) continue;
                narrow[i] = (voidWithin(voidCell, w, h, x, y, -1, 0) && voidWithin(voidCell, w, h, x, y, 1, 0))
                        || (voidWithin(voidCell, w, h, x, y, 0, -1) && voidWithin(voidCell, w, h, x, y, 0, 1));
            }
        }
        // A span joins the ground at two ends at least a span's width apart; a spur or a notch of the bank meets
        // it along one side only, and stays ground.
        boolean[] out = new boolean[w * h];
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int start = 0; start < w * h; start++) {
            if (!narrow[start] || seen[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
            while (head < tail) {
                int c = queue[head++];
                int x = c % w, y = c / w;
                int[][] next = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
                for (int[] p : next) {
                    if (p[0] < 0 || p[1] < 0 || p[0] >= w || p[1] >= h) continue;
                    int q = p[1] * w + p[0];
                    if (narrow[q]) {
                        if (!seen[q]) { seen[q] = true; queue[tail++] = q; }
                    } else if (land[q]) {
                        minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                        minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                    }
                }
            }
            boolean spans = maxX >= 0 && Math.max(maxX - minX, maxY - minY) >= BRIDGE_CELLS;
            if (spans) for (int k = 0; k < tail; k++) out[queue[k]] = true;
        }
        return out;
    }

    private static boolean voidWithin(boolean[] voidCell, int w, int h, int x, int y, int dx, int dy) {
        for (int s = 1; s < BRIDGE_CELLS; s++) {
            int px = x + dx * s, py = y + dy * s;
            if (px < 0 || py < 0 || px >= w || py >= h) return false;
            if (voidCell[py * w + px]) return true;
        }
        return false;
    }

    private static boolean[] shrink(boolean[] m, int w, int h, int r) {
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean all = true;
                for (int dy = -r; dy <= r && all; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int px = x + dx, py = y + dy;
                        // Past the map's edge counts as ground, so the edge does not erode.
                        if (px >= 0 && py >= 0 && px < w && py < h && !m[py * w + px]) { all = false; break; }
                    }
                }
                out[y * w + x] = all;
            }
        }
        return out;
    }

    private static boolean[] grow(boolean[] m, int w, int h, int r) {
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y * w + x]) continue;
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int px = x + dx, py = y + dy;
                        if (px >= 0 && py >= 0 && px < w && py < h) out[py * w + px] = true;
                    }
                }
            }
        }
        return out;
    }

    private boolean openCell(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && open[y * w + x];
    }

    /** Distance in cells from each corner sample to the nearest standing ground (exact, squared EDT). */
    private float[] distanceIn() {
        boolean[] land = new boolean[sw * sh];
        for (int j = 0; j < sh; j++) {
            for (int i = 0; i < sw; i++) {
                // A sample on standing ground: any cell touching it is not open.
                int x0 = (i - 1) / RES, x1 = i / RES, y0 = (j - 1) / RES, y1 = j / RES;
                if (i == 0) x0 = -1;
                if (j == 0) y0 = -1;
                land[j * sw + i] = !openCell(x0, y0) || !openCell(x1, y0) || !openCell(x0, y1) || !openCell(x1, y1);
            }
        }
        return distanceTo(land);
    }

    /** {@link #edge}: the distance in less the distance out, box-blurred twice. */
    private float[] smoothEdge() {
        boolean[] in = new boolean[sw * sh];
        for (int s = 0; s < sw * sh; s++) in[s] = dist[s] > 0f;
        float[] out = distanceTo(in);
        float[] e = new float[sw * sh];
        // Ground samples on the drop's edge are a sample's spacing from the first open one.
        for (int s = 0; s < sw * sh; s++) e[s] = dist[s] > 0f ? dist[s] : -Math.max(0f, out[s] - 1f / RES);
        int r = Math.max(1, Math.round(SMOOTH_CELLS * RES));
        for (int pass = 0; pass < 2; pass++) {
            e = blur(e, r, true);
            e = blur(e, r, false);
        }
        return e;
    }

    private float[] blur(float[] f, int r, boolean alongX) {
        float[] o = new float[f.length];
        int n = alongX ? sw : sh, lines = alongX ? sh : sw;
        for (int l = 0; l < lines; l++) {
            float sum = 0f;
            int count = 0;
            for (int t = -r; t <= r; t++) {
                int q = Math.max(0, Math.min(n - 1, t));
                sum += f[alongX ? l * sw + q : q * sw + l];
                count++;
            }
            for (int t = 0; t < n; t++) {
                o[alongX ? l * sw + t : t * sw + l] = sum / count;
                int add = Math.min(n - 1, t + r + 1), drop = Math.max(0, t - r);
                sum += f[alongX ? l * sw + add : add * sw + l] - f[alongX ? l * sw + drop : drop * sw + l];
            }
        }
        return o;
    }

    /** Distance in cells from each corner sample to the nearest {@code source} sample (exact, squared EDT). */
    private float[] distanceTo(boolean[] source) {
        float inf = 1e12f;
        float[] g = new float[sw * sh];
        for (int s = 0; s < sw * sh; s++) g[s] = source[s] ? 0f : inf;
        float[] col = new float[Math.max(sw, sh)];
        float[] res = new float[Math.max(sw, sh)];
        for (int i = 0; i < sw; i++) {
            for (int j = 0; j < sh; j++) col[j] = g[j * sw + i];
            edt(col, sh, res);
            for (int j = 0; j < sh; j++) g[j * sw + i] = res[j];
        }
        for (int j = 0; j < sh; j++) {
            System.arraycopy(g, j * sw, col, 0, sw);
            edt(col, sw, res);
            for (int i = 0; i < sw; i++) g[j * sw + i] = (float) Math.sqrt(res[i]) / RES;
        }
        return g;
    }

    /** Felzenszwalb's one-dimensional squared distance transform of {@code f} into {@code d}. */
    private static void edt(float[] f, int n, float[] d) {
        int[] v = new int[n];
        float[] z = new float[n + 1];
        int k = 0;
        v[0] = 0;
        z[0] = -Float.MAX_VALUE;
        z[1] = Float.MAX_VALUE;
        for (int q = 1; q < n; q++) {
            float s;
            while (true) {
                int p = v[k];
                s = ((f[q] + q * (float) q) - (f[p] + p * (float) p)) / (2f * q - 2f * p);
                if (s <= z[k] && k > 0) { k--; continue; }
                break;
            }
            if (s <= z[k]) { v[0] = q; z[0] = -Float.MAX_VALUE; z[1] = Float.MAX_VALUE; k = 0; continue; }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Float.MAX_VALUE;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            float dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }

    // ----- sampling -----

    /** A field at a point in cells, bilinear between samples. */
    float sample(float[] field, float x, float y) {
        float fx = Math.max(0f, Math.min(sw - 1.001f, x * RES)), fy = Math.max(0f, Math.min(sh - 1.001f, y * RES));
        int i = (int) fx, j = (int) fy;
        float tx = fx - i, ty = fy - j;
        int s = j * sw + i;
        return (field[s] * (1 - tx) + field[s + 1] * tx) * (1 - ty) + (field[s + sw] * (1 - tx) + field[s + sw + 1] * tx) * ty;
    }

    /** How far below the surface the rock stands at a point, metres: {@code 0} on the ground and the lip. */
    float depthAt(float x, float y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return 0f;
        for (int k = steps(); k >= 1; k--) if (sample(reach[k], x, y) > 0f) return stepDepth(k, x, y);
        return 0f;
    }

    private boolean onBridge(float x, float y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        return cx >= 0 && cy >= 0 && cx < w && cy < h && bridge[cy * w + cx];
    }

    /** Whether the sun reaches a point {@code z} metres down, marching toward it over the rock and the bridges. */
    float sunlit(float x, float y, float z, float sunX, float sunY, float rise) {
        float step = .3f;
        for (float t = step; ; t += step) {
            float rayDepth = z - t * rise;
            if (rayDepth <= top - .01f) return 1f;
            float px = x + sunX * t, py = y + sunY * t;
            if (depthAt(px, py) < rayDepth) return 0f;
            if (rayDepth < DECK_METERS && onBridge(px, py)) return 0f;
        }
    }

    /** How much of the sky a point {@code z} metres down sees past the rock round it, 0 to 1. */
    float sky(float x, float y, float z) {
        if (z <= top) return 1f;
        float seen = 0f;
        for (int d = 0; d < SKY_DIRS; d++) {
            double a = Math.PI * 2 * (d + .5) / SKY_DIRS;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            float best = 0f;
            for (float t = .5f; t < SKY_REACH; t *= 1.25f) {
                float rise = (z - depthAt(x + dx * t, y + dy * t)) / t;
                best = Math.max(best, rise);
            }
            seen += 1f - best / (float) Math.sqrt(1f + best * best);
        }
        return seen / SKY_DIRS;
    }

    // ----- cutting -----

    private void cut(float sunX, float sunY, float rise) {
        int n = steps();
        float sinE = rise / (float) Math.sqrt(1f + rise * rise);
        float[] poly = new float[16], tmp = new float[16];
        float[] attr = new float[8];
        float[] tx = new float[3], ty = new float[3];
        int[] ts = new int[3];
        for (int cy = 0; cy < h; cy++) {
            for (int cx = 0; cx < w; cx++) {
                // Ground beside the drop too: the smoothed line may run a little under it, and what lies deeper
                // there shows past the edge once scaled toward the view's centre. Its surface is the tiles'.
                boolean ground = !open[cy * w + cx];
                if (ground && !nearOpen(cx, cy)) continue;
                for (int sj = 0; sj < RES; sj++) {
                    for (int si = 0; si < RES; si++) {
                        int i = cx * RES + si, j = cy * RES + sj;
                        int a = j * sw + i, b = a + 1, c = a + sw + 1, d = a + sw;
                        for (int tri = 0; tri < 2; tri++) {
                            ts[0] = a; ts[1] = tri == 0 ? b : c; ts[2] = tri == 0 ? c : d;
                            for (int v = 0; v < 3; v++) {
                                tx[v] = (ts[v] % sw) / (float) RES;
                                ty[v] = (ts[v] / sw) / (float) RES;
                            }
                            for (int k = ground ? 1 : 0; k <= n; k++) {
                                int m = clip(tx, ty, ts, k, poly, tmp);
                                if (m < 3) continue;
                                float mx = 0f, my = 0f;
                                for (int v = 0; v < m; v++) { mx += poly[v * 2]; my += poly[v * 2 + 1]; }
                                mx /= m; my /= m;
                                float z = stepDepth(k, mx, my);
                                float sky = sky(mx, my, z);
                                float sun = sunlit(mx, my, z, sunX, sunY, rise);
                                attr[0] = mx; attr[1] = my;
                                attr[2] = .42f + .36f * sky + .62f * sun * sinE;
                                attr[3] = k < n ? -sample(reach[k + 1], mx, my) : sample(reach[k], mx, my);
                                float gx = sample(edge, mx + .25f, my) - sample(edge, mx - .25f, my);
                                float gy = sample(edge, mx, my + .25f) - sample(edge, mx, my - .25f);
                                float len = (float) Math.hypot(gx, gy);
                                // The floor's water runs along the drop: across the way the distance in climbs.
                                float fx = len > 1e-4f ? -gy / len : 1f, fy = len > 1e-4f ? gx / len : 0f;
                                if (Math.abs(fx) >= Math.abs(fy) ? fx < 0 : fy < 0) { fx = -fx; fy = -fy; }
                                attr[4] = fx; attr[5] = fy;
                                attr[6] = sky;
                                attr[7] = sun;
                                bands.get(k).add(poly, m, attr);
                            }
                            for (int k = 1; k <= n; k++) wallSegment(tx, ty, ts, k, sunX, sunY, rise);
                        }
                    }
                }
            }
        }
    }

    /**
     * The triangle's piece in band {@code k}: where {@code reach[k] > 0} (all of it for the lip) and {@code
     * reach[k+1] <= 0} (all of it for the floor). Returns the corner count.
     */
    private int clip(float[] tx, float[] ty, int[] ts, int k, float[] poly, float[] tmp) {
        int n = steps();
        int m = 3;
        float[] val = new float[3];
        for (int v = 0; v < 3; v++) { poly[v * 2] = tx[v]; poly[v * 2 + 1] = ty[v]; }
        // Carry each kept corner's field values through the clip by re-sampling: fields are linear here.
        if (k >= 1) {
            for (int v = 0; v < 3; v++) val[v] = reach[k][ts[v]];
            m = clipLinear(poly, m, tmp, tx, ty, val, 1f);
            System.arraycopy(tmp, 0, poly, 0, m * 2);
        }
        if (k < n && m >= 3) {
            for (int v = 0; v < 3; v++) val[v] = reach[k + 1][ts[v]];
            m = clipLinear(poly, m, tmp, tx, ty, val, -1f);
            System.arraycopy(tmp, 0, poly, 0, m * 2);
        }
        return m;
    }

    /**
     * Clips a convex polygon to where {@code sign * f >= 0}, {@code f} the linear field through the triangle's
     * corners with values {@code val}. Writes the result to {@code out} and returns its corner count.
     */
    private static int clipLinear(float[] in, int m, float[] out, float[] tx, float[] ty, float[] val, float sign) {
        int o = 0;
        for (int v = 0; v < m; v++) {
            float ax = in[v * 2], ay = in[v * 2 + 1];
            int u = (v + 1) % m;
            float bx = in[u * 2], by = in[u * 2 + 1];
            float fa = sign * linear(tx, ty, val, ax, ay), fb = sign * linear(tx, ty, val, bx, by);
            if (fa >= 0f) { out[o++] = ax; out[o++] = ay; }
            if ((fa >= 0f) != (fb >= 0f)) {
                float t = fa / (fa - fb);
                out[o++] = ax + (bx - ax) * t;
                out[o++] = ay + (by - ay) * t;
            }
            if (o >= out.length - 2) break;
        }
        return o / 2;
    }

    /** The linear field through a triangle's three corners, at a point. */
    private static float linear(float[] tx, float[] ty, float[] val, float x, float y) {
        float d = (ty[1] - ty[2]) * (tx[0] - tx[2]) + (tx[2] - tx[1]) * (ty[0] - ty[2]);
        if (Math.abs(d) < 1e-9f) return val[0];
        float l0 = ((ty[1] - ty[2]) * (x - tx[2]) + (tx[2] - tx[1]) * (y - ty[2])) / d;
        float l1 = ((ty[2] - ty[0]) * (x - tx[2]) + (tx[0] - tx[2]) * (y - ty[2])) / d;
        return l0 * val[0] + l1 * val[1] + (1 - l0 - l1) * val[2];
    }

    /** Where step {@code k}'s line crosses the triangle: a face from step {@code k-1}'s depth down to its own. */
    private void wallSegment(float[] tx, float[] ty, int[] ts, int k, float sunX, float sunY, float rise) {
        float[] val = {reach[k][ts[0]], reach[k][ts[1]], reach[k][ts[2]]};
        float[] pts = new float[4];
        int found = 0;
        for (int v = 0; v < 3 && found < 2; v++) {
            int u = (v + 1) % 3;
            float fa = val[v], fb = val[u];
            if ((fa > 0f) == (fb > 0f)) continue;
            float t = fa / (fa - fb);
            pts[found * 2] = tx[v] + (tx[u] - tx[v]) * t;
            pts[found * 2 + 1] = ty[v] + (ty[u] - ty[v]) * t;
            found++;
        }
        if (found < 2) return;
        // The face looks into the drop: up the field's slope.
        float d = (ty[1] - ty[2]) * (tx[0] - tx[2]) + (tx[2] - tx[1]) * (ty[0] - ty[2]);
        if (Math.abs(d) < 1e-9f) return;
        float gx = ((ty[1] - ty[2]) * val[0] + (ty[2] - ty[0]) * val[1] + (ty[0] - ty[1]) * val[2]) / d;
        float gy = ((tx[2] - tx[1]) * val[0] + (tx[0] - tx[2]) * val[1] + (tx[1] - tx[0]) * val[2]) / d;
        float len = (float) Math.hypot(gx, gy);
        if (len < 1e-6f) return;
        // A drop's face looks into it, up the field; a rise's looks out of it, down toward the lower ground.
        float nx = (rising ? -gx : gx) / len, ny = (rising ? -gy : gy) / len;
        float g0 = gainAt(pts[0], pts[1]), g1 = gainAt(pts[2], pts[3]);
        float gm = gainAt((pts[0] + pts[2]) / 2f, (pts[1] + pts[3]) / 2f);
        if (rising) {
            riseWall(pts, nx, ny, k, sunX, sunY, rise, g0, g1, gm);
            return;
        }
        // The face's light is worked out at its own depth here; it is drawn between its steps' depths, scaled at
        // each end by the gain there.
        float top = depth[k - 1] * gm, bottom = depth[k] * gm;
        int rows = Math.max(1, (int) Math.ceil((bottom - top) / WALL_LIGHT_METERS));
        float[] light = new float[rows];
        float mx = (pts[0] + pts[2]) / 2f + nx * .05f, my = (pts[1] + pts[3]) / 2f + ny * .05f;
        float cosE = 1f / (float) Math.sqrt(1f + rise * rise);
        float facing = Math.max(0f, nx * sunX + ny * sunY) * cosE;
        // The sky a face sees: half of what the ground at its foot sees, more near the top.
        float skyFoot = sky(mx + nx * .4f, my + ny * .4f, bottom);
        for (int r = 0; r < rows; r++) {
            float z = top + (r + .5f) * (bottom - top) / rows;
            float open = .5f * (skyFoot + (1f - skyFoot) * (1f - (z - top) / Math.max(1f, bottom)));
            float sun = facing > 0f ? sunlit(mx, my, z, sunX, sunY, rise) : 0f;
            light[r] = .4f + .32f * open + .8f * sun * facing;
        }
        walls.get(k - 1).add(new Wall(pts[0], pts[1], pts[2], pts[3], nx, ny, depth[k - 1], depth[k], light, g0, g1));
    }

    /**
     * A rise's face, from step {@code k}'s top down to the step below: lit by the sun where its march clears
     * the rock, and by the sky a point just out from the face at each height sees.
     */
    private void riseWall(float[] pts, float nx, float ny, int k, float sunX, float sunY, float rise,
                          float g0, float g1, float gm) {
        float zTop = depth[k] * gm, zFoot = depth[k - 1] * gm;
        int rows = Math.max(1, (int) Math.ceil((zFoot - zTop) / WALL_LIGHT_METERS));
        float[] light = new float[rows];
        float mx = (pts[0] + pts[2]) / 2f + nx * .05f, my = (pts[1] + pts[3]) / 2f + ny * .05f;
        float cosE = 1f / (float) Math.sqrt(1f + rise * rise);
        float facing = Math.max(0f, nx * sunX + ny * sunY) * cosE;
        for (int r = 0; r < rows; r++) {
            float z = zTop + (r + .5f) * (zFoot - zTop) / rows;
            float open = sky(mx + nx * .6f, my + ny * .6f, z);
            float sun = facing > 0f ? sunlit(mx, my, z, sunX, sunY, rise) : 0f;
            light[r] = .48f + .4f * open + .7f * sun * facing;
        }
        walls.get(k - 1).add(new Wall(pts[0], pts[1], pts[2], pts[3], nx, ny, depth[k], depth[k - 1], light, g0, g1));
    }

    /**
     * A rise's shadow on the walkable ground round its foot, sampled {@link #SHADE_RES} to a cell's side as
     * far out as its height throws it, each sample's shade the mean of four marches.
     */
    private void floorShade(float sunX, float sunY, float rise) {
        int reachCells = (int) Math.ceil(-top / Math.max(.05f, rise)) + 1;
        float[] attr = new float[8];
        float[] poly = new float[8];
        float step = 1f / SHADE_RES;
        for (int cy = 0; cy < h; cy++) {
            for (int cx = 0; cx < w; cx++) {
                if (open[cy * w + cx] || !openWithin(cx, cy, reachCells)) continue;
                for (int j = 0; j < SHADE_RES; j++) {
                    for (int i = 0; i < SHADE_RES; i++) {
                        float x0 = cx + i * step, y0 = cy + j * step;
                        float shade = 0f;
                        for (int q = 0; q < 4; q++) {
                            float qx = x0 + ((q & 1) == 0 ? .25f : .75f) * step;
                            float qy = y0 + ((q & 2) == 0 ? .25f : .75f) * step;
                            shade += 1f - sunlit(qx, qy, 0f, sunX, sunY, rise);
                        }
                        if (shade <= 0f) continue;
                        poly[0] = x0; poly[1] = y0; poly[2] = x0 + step; poly[3] = y0;
                        poly[4] = x0 + step; poly[5] = y0 + step; poly[6] = x0; poly[7] = y0 + step;
                        attr[0] = x0 + step / 2f;
                        attr[1] = y0 + step / 2f;
                        attr[7] = shade / 4f;
                        floorShade.add(poly, 4, attr);
                    }
                }
            }
        }
    }

    private boolean openWithin(int x, int y, int r) {
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) if (openCell(x + dx, y + dy)) return true;
        return false;
    }

    /**
     * The fray: on ground cells beside the drop, where the signed distance to the drop plus a wandering
     * reach is above nothing. Its cover fades from full at the drop's edge to none at its wandering end.
     */
    private void fray() {
        float[] e = new float[sw * sh];
        for (int j = 0; j < sh; j++) {
            for (int i = 0; i < sw; i++) {
                int s = j * sw + i;
                float x = i / (float) RES, y = j / (float) RES;
                float reach = FRAY_MIN + FRAY_WANDER * (.5f + .5f * Noise.fbm(x / 2.3f, y / 2.3f, shape.seed() * 13 + 1));
                e[s] = reach + edge[s];
            }
        }
        float[] poly = new float[16], tmp = new float[16];
        float[] attr = new float[8];
        float[] tx = new float[3], ty = new float[3], val = new float[3];
        for (int cy = 0; cy < h; cy++) {
            for (int cx = 0; cx < w; cx++) {
                if (open[cy * w + cx] || !nearOpen(cx, cy)) continue;
                for (int sj = 0; sj < RES; sj++) {
                    for (int si = 0; si < RES; si++) {
                        int i = cx * RES + si, j = cy * RES + sj;
                        int a = j * sw + i, b = a + 1, c = a + sw + 1, d = a + sw;
                        for (int tri = 0; tri < 2; tri++) {
                            int[] ts = {a, tri == 0 ? b : c, tri == 0 ? c : d};
                            for (int v = 0; v < 3; v++) {
                                tx[v] = (ts[v] % sw) / (float) RES;
                                ty[v] = (ts[v] / sw) / (float) RES;
                                poly[v * 2] = tx[v];
                                poly[v * 2 + 1] = ty[v];
                                val[v] = e[ts[v]];
                            }
                            int m = clipLinear(poly, 3, tmp, tx, ty, val, 1f);
                            if (m < 3) continue;
                            float mx = 0f, my = 0f;
                            for (int v = 0; v < m; v++) { mx += tmp[v * 2]; my += tmp[v * 2 + 1]; }
                            mx /= m; my /= m;
                            // A bank's end where a bridge leaves it is the road onto the deck, not a lip.
                            if (byBridge(mx, my)) continue;
                            // Full at the drop's edge, fading out into the turf, broken into clumps.
                            float e0 = linear(tx, ty, val, mx, my);
                            float cover = e0 >= FRAY_FADE ? 1f : e0 / FRAY_FADE
                                    * (.55f + .45f * Noise.value(mx * 1.7f, my * 1.7f, shape.seed() * 5 + 2));
                            attr[0] = mx; attr[1] = my; attr[2] = 1f;
                            attr[3] = .3f + (1f - cover) * 1.4f;
                            attr[4] = 0f; attr[5] = 0f; attr[6] = 1f; attr[7] = cover;
                            fray.add(tmp, m, attr);
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether the open cell nearest a point of ground is a bridge: the point lies at a bridge's end, where the
     * walkable surface runs on onto the deck, rather than at the drop's edge.
     */
    boolean byBridge(float x, float y) {
        float bestVoid = Float.MAX_VALUE, bestBridge = Float.MAX_VALUE;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int ox = cx + dx, oy = cy + dy;
                if (!openCell(ox, oy)) continue;
                float ex = Math.max(0f, Math.max(ox - x, x - (ox + 1))), ey = Math.max(0f, Math.max(oy - y, y - (oy + 1)));
                float d = ex * ex + ey * ey;
                if (bridge[oy * w + ox]) bestBridge = Math.min(bestBridge, d);
                else bestVoid = Math.min(bestVoid, d);
            }
        }
        return bestBridge < bestVoid;
    }

    /**
     * Whether a bridge's deck lies within {@code radius} cells of a point. A bridge is ground to the air over a drop:
     * a puff of smoke or mist that would spread over its deck is not drawn, so the deck never reads stained.
     */
    boolean nearBridge(float x, float y, float radius) {
        int x0 = (int) Math.floor(x - radius), x1 = (int) Math.floor(x + radius);
        int y0 = (int) Math.floor(y - radius), y1 = (int) Math.floor(y + radius);
        for (int cy = Math.max(0, y0); cy <= Math.min(h - 1, y1); cy++) {
            for (int cx = Math.max(0, x0); cx <= Math.min(w - 1, x1); cx++) {
                if (!bridge[cy * w + cx]) continue;
                float ex = Math.max(0f, Math.max(cx - x, x - (cx + 1))), ey = Math.max(0f, Math.max(cy - y, y - (cy + 1)));
                if (ex * ex + ey * ey < radius * radius) return true;
            }
        }
        return false;
    }

    private boolean nearOpen(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) if (openCell(x + dx, y + dy)) return true;
        return false;
    }

    /** The bridges' sides over the drop. */
    private void sides() {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!bridge[y * w + x]) continue;
                if (voidAt(x, y - 1)) bridgeSides.add(new float[]{x, y, x + 1, y, 0, -1});
                if (voidAt(x, y + 1)) bridgeSides.add(new float[]{x, y + 1, x + 1, y + 1, 0, 1});
                if (voidAt(x - 1, y)) bridgeSides.add(new float[]{x, y, x, y + 1, -1, 0});
                if (voidAt(x + 1, y)) bridgeSides.add(new float[]{x + 1, y, x + 1, y + 1, 1, 0});
            }
        }
    }

    private boolean voidAt(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && open[y * w + x] && !bridge[y * w + x];
    }

    /** Smooth value noise, about {@code -1..1}. */
    static final class Noise {
        private Noise() { }

        static float fbm(float x, float y, int seed) {
            return .65f * value(x, y, seed) + .35f * value(x * 2.1f, y * 2.1f, seed + 101);
        }

        static float value(float x, float y, int seed) {
            int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
            float fx = x - ix, fy = y - iy;
            fx = fx * fx * (3 - 2 * fx);
            fy = fy * fy * (3 - 2 * fy);
            float a = hash(ix, iy, seed), b = hash(ix + 1, iy, seed), c = hash(ix, iy + 1, seed),
                    d = hash(ix + 1, iy + 1, seed);
            return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
        }

        static float hash(int x, int y, int seed) {
            int n = x * 374761393 + y * 668265263 + seed * 1442695041;
            n = (n ^ (n >>> 13)) * 1274126177;
            n ^= n >>> 16;
            return (n & 0xFFFFFF) / (float) 0x7FFFFF - 1f;
        }
    }
}
