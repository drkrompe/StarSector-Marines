package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.ElevationMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An elevation plan resolved to cells — each cell's tier and whether it is
 * ground, face or ramp — and the continuous field the cliff art is clipped
 * from. Pure: it reads only the plan, so it can be asked and tested without a
 * map.
 *
 * <p><b>Field.</b> Each tier {@code k} has a signed field at the cell corners:
 * how far inside the nearest plateau of tier {@code k} or higher a point is, in
 * cells, roughened by the plan's noise. A cell whose center is at least the face
 * width inside is the plateau's top; between the outline and that depth it is
 * face. The art clips the same field at the same depths, so the face drawn and
 * the cells that are face agree.
 *
 * <p><b>Ramps.</b> A ramp's corridor is cut out of the field of the tier it
 * climbs, its signed distance from the corridor's side taking the field's place,
 * so the cut's sides are faces by the same rule as any outline. Corridor cells
 * that stood in the plateau become ramp cells at the lower tier; corridor cells
 * outside it stay low ground. For the art, the cut rises along the corridor
 * from low ground at the foot to the top at the head, so no cliff is drawn
 * across a ramp's head.
 *
 * <p><b>Safety.</b> Any top cell beside lower ground that is not a ramp's head
 * becomes face, so a rough outline can never leave two tiers touching.
 */
public final class ElevationLayout {

    private final int width;
    private final int height;
    private final int faceWidth;
    private final byte[] tier;
    private final byte[] kind;
    private final double[][] artFields;
    private final double[] rampRise;
    private final List<RampShape> ramps;
    private final double[][] plateauFields;
    private final int southFace;
    private final boolean[] graded;

    /**
     * A ramp's corridor as resolved: from the foot along unit axis
     * {@code (ux, uy)} for {@code length} cells, {@code half} cells either side.
     */
    public record RampShape(int lowerTier, double footX, double footY, double ux, double uy,
                            double length, double half) {
        /** Signed distance across the corridor's axis at a point: positive to the left. */
        public double across(double x, double y) { return -(x - footX) * uy + (y - footY) * ux; }
        public double along(double x, double y) { return (x - footX) * ux + (y - footY) * uy; }
    }

    private ElevationLayout(int width, int height, int faceWidth, byte[] tier, byte[] kind,
                            double[][] artFields, double[] rampRise, List<RampShape> ramps,
                            double[][] plateauFields, int southFace, boolean[] graded) {
        this.width = width;
        this.height = height;
        this.faceWidth = faceWidth;
        this.tier = tier;
        this.kind = kind;
        this.artFields = artFields;
        this.rampRise = rampRise;
        this.ramps = List.copyOf(ramps);
        this.plateauFields = plateauFields;
        this.southFace = southFace;
        this.graded = graded;
    }

    public int width() { return width; }
    /** How many cells a face looking down the screen is widened by. */
    public int southFace() { return southFace; }
    public int height() { return height; }
    public int faceWidth() { return faceWidth; }
    public int tier(int x, int y) { return tier[y * width + x]; }
    public byte kind(int x, int y) { return kind[y * width + x]; }
    public boolean isFace(int x, int y) { return kind(x, y) == ElevationMap.FACE; }
    public boolean isRamp(int x, int y) { return kind(x, y) == ElevationMap.RAMP; }
    /** {@link #isFace} that answers false off the map. */
    public boolean isFaceInBounds(int x, int y) { return inBounds(x, y) && isFace(x, y); }
    /** A cell elevation touches at all: raised, face or ramp. */
    public boolean touched(int x, int y) { return tier(x, y) > 0 || kind(x, y) != ElevationMap.GROUND; }

    /** The highest tier any plateau stands on. */
    public int highestTier() { return artFields.length; }

    /**
     * Tier {@code k}'s art field over the cell corners, {@code (width+1)*(height+1)}
     * values: below zero is lower ground, zero to the face width is the face that
     * holds tier {@code k} up, above is its top. A copy.
     */
    public double[] artCorners(int k) { return artFields[k - 1].clone(); }

    /**
     * Tier {@code k}'s field over the cell corners before any ramp is cut or
     * any south face widened: the plateau's own shape, for a look that lays
     * its ramps on as slopes and widens its south faces itself
     * ({@link #southFace()}).
     */
    public double[] plateauCorners(int k) { return plateauFields[k - 1].clone(); }

    /**
     * Whether the raised ground nearest the cell corner {@code (x, y)}, {@code 0..width} by {@code 0..height}, is
     * graded: an engineered bank ({@link ElevationPlan.Plateau#graded}) rather than natural ground.
     */
    public boolean gradedCorner(int x, int y) {
        return graded[Math.max(0, Math.min(height, y)) * (width + 1) + Math.max(0, Math.min(width, x))];
    }

    /** The ramps as resolved, in plan order. */
    public List<RampShape> ramps() { return ramps; }

    /** How far up its ramp a ramp cell stands, 0 at the foot to 1 at the head; NaN elsewhere. */
    public double rampRise(int x, int y) { return rampRise[y * width + x]; }

    public ElevationMap toMap(ElevationMap.SightRule rule) {
        return ElevationMap.of(width, height, tier, kind, rampRise, rule);
    }

    public static ElevationLayout resolve(ElevationPlan plan) {
        int w = plan.width(), h = plan.height(), cells = w * h, fw = plan.faceWidth();
        int corners = (w + 1) * (h + 1);
        int maxTier = 0;
        for (ElevationPlan.Plateau p : plan.plateaus()) maxTier = Math.max(maxTier, p.tier());
        double[][] raw = new double[maxTier][];
        // Whether the plateau nearest each corner (the one whose field is highest there) is graded, read on tier 1,
        // which every plateau raises.
        boolean[] graded = new boolean[corners];
        for (int k = 1; k <= maxTier; k++) {
            raw[k - 1] = new double[corners];
            Arrays.fill(raw[k - 1], -128);
            for (ElevationPlan.Plateau p : plan.plateaus()) {
                if (p.tier() >= k) sweep(plan, p, raw[k - 1], k == 1 ? graded : null);
            }
        }
        double[][] untilted = new double[maxTier][];
        for (int k = 0; k < maxTier; k++) untilted[k] = raw[k].clone();
        if (plan.southFace() > 0) {
            for (int k = 0; k < maxTier; k++) raw[k] = tilted(raw[k], w, h, fw, plan.southFace());
        }
        double[][] cut = new double[maxTier][];
        double[][] art = new double[maxTier][];
        for (int k = 0; k < maxTier; k++) {
            cut[k] = raw[k].clone();
            art[k] = raw[k].clone();
        }
        double[] rampRise = new double[cells];
        Arrays.fill(rampRise, Double.NaN);
        int[] rampLower = new int[cells];
        Arrays.fill(rampLower, -1);
        List<RampShape> shapes = new ArrayList<>();
        for (ElevationPlan.Ramp ramp : plan.ramps()) {
            int lower = rawTierAt(raw, w, ramp.foot());
            int upper = rawTierAt(raw, w, ramp.head());
            if (upper != lower + 1) {
                throw new IllegalArgumentException("Ramp must climb exactly one tier: foot " + lower
                        + " at " + ramp.foot() + ", head " + upper + " at " + ramp.head());
            }
            double fx = ramp.foot().x(), fy = ramp.foot().y();
            double ax = ramp.head().x() - fx, ay = ramp.head().y() - fy;
            double length = Math.hypot(ax, ay), ux = ax / length, uy = ay / length;
            double half = ramp.width() / 2.0;
            shapes.add(new RampShape(lower, fx, fy, ux, uy, length, half));
            for (int y = 0; y <= h; y++) {
                for (int x = 0; x <= w; x++) {
                    double px = x - .5 - fx, py = y - .5 - fy;
                    double along = px * ux + py * uy, across = Math.abs(-px * uy + py * ux);
                    if (along < -1 || along > length) continue;
                    int i = y * (w + 1) + x;
                    double side = across - half;
                    cut[upper - 1][i] = Math.min(cut[upper - 1][i], side);
                    double climb = (along / length) * (fw + 1) - 1;
                    art[upper - 1][i] = Math.min(art[upper - 1][i], Math.max(side, climb));
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double px = x - fx, py = y - fy;
                    double along = px * ux + py * uy, across = Math.abs(-px * uy + py * ux);
                    if (along < 0 || along > length || across > half) continue;
                    if (centerTier(raw, w, x, y) <= lower) continue;
                    int i = y * w + x;
                    rampLower[i] = lower;
                    rampRise[i] = along / length;
                }
            }
        }
        byte[] tier = new byte[cells];
        byte[] kind = new byte[cells];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (rampLower[i] >= 0) {
                    kind[i] = ElevationMap.RAMP;
                    tier[i] = (byte) rampLower[i];
                    continue;
                }
                for (int k = maxTier; k >= 1; k--) {
                    double c = center(cut[k - 1], w, x, y);
                    if (c >= fw) { tier[i] = (byte) k; break; }
                    if (c >= 0) { tier[i] = (byte) k; kind[i] = ElevationMap.FACE; break; }
                }
            }
        }
        byte[] faced = kind.clone();
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (kind[i] != ElevationMap.GROUND || tier[i] == 0) continue;
                for (int[] d : around) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (kind[n] == ElevationMap.FACE) continue;
                    boolean lower = kind[n] == ElevationMap.RAMP
                            ? tier[n] + 1 != tier[i] || (rampRise[n] < .75 && d[0] * d[1] == 0)
                            : tier[n] < tier[i];
                    if (lower) {
                        faced[i] = ElevationMap.FACE;
                        break;
                    }
                }
            }
        }
        ElevationLayout layout = new ElevationLayout(w, h, fw, tier, faced, art, rampRise, shapes, untilted,
                plan.southFace(), graded);
        for (ElevationPlan.Ramp ramp : plan.ramps()) layout.requireRampEnds(ramp);
        layout.requireEveryPlateauReachable();
        return layout;
    }

    /**
     * Raises {@code field} to the plateau's signed distance wherever it is higher, and marks the corners it won in
     * {@code won}, when given. A tapered chain interpolates its radius along each capsule; a graded one is not
     * roughened and is cut square across its two ends: every capsule is clipped by a plane through each end point,
     * square to the chain there, so no round joint pokes past a short last capsule.
     */
    private static void sweep(ElevationPlan plan, ElevationPlan.Plateau plateau, double[] field, boolean[] won) {
        int w = plan.width(), h = plan.height(), vw = w + 1;
        double reach = plateau.radius() + 4;
        double phase = (plan.seed() & 65535) * .013;
        double rough = plateau.graded() ? 0 : plan.ruggedness() * 1.4;
        var points = plateau.points();
        // The end planes: a point and the unit direction into the chain, or null for round ends.
        double[] start = plateau.graded() ? endPlane(points, false) : null;
        double[] end = plateau.graded() ? endPlane(points, true) : null;
        for (int s = 0; s < points.size(); s++) {
            var a = points.get(Math.max(0, s - 1));
            var b = points.get(s);
            double ra = plateau.radiusAt(Math.max(0, s - 1)), rb = plateau.radiusAt(s);
            int x0 = Math.max(0, (int) Math.floor(Math.min(a.x(), b.x()) - reach));
            int x1 = Math.min(w, (int) Math.ceil(Math.max(a.x(), b.x()) + reach));
            int y0 = Math.max(0, (int) Math.floor(Math.min(a.y(), b.y()) - reach));
            int y1 = Math.min(h, (int) Math.ceil(Math.max(a.y(), b.y()) + reach));
            double dx = b.x() - a.x(), dy = b.y() - a.y(), den = dx * dx + dy * dy;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x - .5 - a.x(), py = y - .5 - a.y();
                    double t = den == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / den));
                    double value = ra + (rb - ra) * t - Math.hypot(px - t * dx, py - t * dy);
                    if (rough > 0) {
                        double noise = Math.sin(x * .59 + Math.sin(y * .37) + phase) * .65
                                + Math.sin(y * 1.13 - x * .34 + phase) * .25
                                + Math.sin(x * 2.1 + y * 1.8 + phase) * .10;
                        value += rough * noise;
                    }
                    if (start != null) {
                        value = Math.min(value, (x - .5 - start[0]) * start[2] + (y - .5 - start[1]) * start[3]);
                    }
                    if (end != null) {
                        value = Math.min(value, (x - .5 - end[0]) * end[2] + (y - .5 - end[1]) * end[3]);
                    }
                    int i = y * vw + x;
                    if (value > field[i]) {
                        field[i] = value;
                        if (won != null) won[i] = plateau.graded();
                    }
                }
            }
        }
    }

    /**
     * The plane square across a chain at its first point ({@code last} false) or its last: {x, y, ux, uy}, the
     * point and the unit direction pointing into the chain; null for a chain of one point.
     */
    private static double[] endPlane(List<ElevationPlan.Point> points, boolean last) {
        int n = points.size();
        ElevationPlan.Point at = points.get(last ? n - 1 : 0);
        for (int k = 1; k < n; k++) {
            ElevationPlan.Point other = points.get(last ? n - 1 - k : k);
            double dx = other.x() - at.x(), dy = other.y() - at.y(), len = Math.hypot(dx, dy);
            if (len > 1e-9) return new double[]{at.x(), at.y(), dx / len, dy / len};
        }
        return null;
    }

    /**
     * The field with its faces that look down the screen widened: near an
     * outline whose outside lies toward lower y, depth is scaled down so the
     * face band reaches further in. Outlines that look up the screen keep
     * their width.
     */
    private static double[] tilted(double[] field, int w, int h, int fw, int extra) {
        int vw = w + 1;
        double[] out = field.clone();
        for (int y = 0; y <= h; y++) {
            for (int x = 0; x <= w; x++) {
                int i = y * vw + x;
                double f = field[i];
                if (f < 0) continue;
                // Sobel over the 3x3 around the corner. Outside every plateau the
                // field holds a sentinel far below zero; read raw, one such
                // neighbour swamps the difference and snaps the direction to the
                // grid axis, which steps the widened band cell by cell along any
                // diagonal outline. Clamped to what a slope-one field would hold
                // there, the outline's own bearing comes through.
                double gx = 0, gy = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = Math.max(0, Math.min(w, x + dx)), ny = Math.max(0, Math.min(h, y + dy));
                        double n = Math.max(field[ny * vw + nx], f - Math.hypot(dx, dy));
                        double weight = dx == 0 || dy == 0 ? 2 : 1;
                        gx += dx * weight * n;
                        gy += dy * weight * n;
                    }
                }
                double length = Math.hypot(gx, gy);
                if (length < 1e-6) continue;
                double south = Math.max(0, gy / length);
                // Continuous: the face band grows from fw to fw + extra*south,
                // and depth past it carries on at the field's own slope.
                double band = fw + extra * south;
                out[i] = f <= band ? f * fw / band : f - extra * south;
            }
        }
        return out;
    }

    private static double center(double[] field, int w, int x, int y) {
        int vw = w + 1;
        return (field[y * vw + x] + field[(y + 1) * vw + x + 1]
                + field[y * vw + x + 1] + field[(y + 1) * vw + x]) * .25;
    }

    /** The tier a cell's center stands inside before any ramp cut, its face band included. */
    private static int centerTier(double[][] raw, int w, int x, int y) {
        for (int k = raw.length; k >= 1; k--) {
            if (center(raw[k - 1], w, x, y) >= 0) return k;
        }
        return 0;
    }

    private static int rawTierAt(double[][] raw, int w, ElevationPlan.Point p) {
        return centerTier(raw, w, (int) Math.round(p.x()), (int) Math.round(p.y()));
    }

    private void requireRampEnds(ElevationPlan.Ramp ramp) {
        double fx = ramp.foot().x(), fy = ramp.foot().y();
        double ux = ramp.head().x() - fx, uy = ramp.head().y() - fy;
        double length = Math.hypot(ux, uy);
        ux /= length;
        uy /= length;
        int footX = (int) Math.round(fx - ux), footY = (int) Math.round(fy - uy);
        int headX = (int) Math.round(ramp.head().x() + ux), headY = (int) Math.round(ramp.head().y() + uy);
        if (!inBounds(footX, footY) || !inBounds(headX, headY)
                || kind(footX, footY) != ElevationMap.GROUND || kind(headX, headY) != ElevationMap.GROUND
                || tier(headX, headY) != tier(footX, footY) + 1) {
            throw new IllegalArgumentException("Ramp ends must open onto ground a tier apart: foot " + footX + ","
                    + footY + " head " + headX + "," + headY);
        }
    }

    /** Every walkable plateau region must reach a ramp; a hill nobody can climb is a raised mass. */
    private void requireEveryPlateauReachable() {
        int cells = width * height;
        boolean[] seen = new boolean[cells];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int start = 0; start < cells; start++) {
            if (seen[start] || kind[start] != ElevationMap.GROUND || tier[start] == 0) continue;
            boolean ramped = false;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                int x = i % width, y = i / width;
                for (int[] step : steps) {
                    int nx = x + step[0], ny = y + step[1];
                    if (!inBounds(nx, ny)) continue;
                    int n = ny * width + nx;
                    if (kind[n] == ElevationMap.RAMP && tier[n] + 1 == tier[i]) ramped = true;
                    if (seen[n] || kind[n] != ElevationMap.GROUND || tier[n] != tier[i]) continue;
                    seen[n] = true;
                    queue.add(n);
                }
            }
            if (!ramped) {
                throw new IllegalArgumentException("Plateau at " + (start % width) + "," + (start / width)
                        + " (tier " + tier[start] + ") has no ramp");
            }
        }
    }

    private boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
}
