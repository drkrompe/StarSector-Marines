package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.ElevationMap;

import java.util.ArrayDeque;

/**
 * An elevation plan resolved to cells: each cell's tier and whether it is
 * ground, face or ramp. Pure: it reads only the plan, so it can be asked and
 * tested without a map.
 *
 * <p>Order of resolution. A cell takes the highest tier whose plateau covers
 * its center. Ramp corridors then claim their cells at the ramp's lower tier,
 * and a wall of face cells closes each corridor's sides so a ramp is entered
 * only at its foot and its head. Finally every top cell within the face width
 * of lower ground becomes face, which is what makes the cliff a band of
 * unwalkable cells every route authority already refuses.
 */
public final class ElevationLayout {

    private final int width;
    private final int height;
    private final byte[] tier;
    private final byte[] kind;

    private ElevationLayout(int width, int height, byte[] tier, byte[] kind) {
        this.width = width;
        this.height = height;
        this.tier = tier;
        this.kind = kind;
    }

    public int width() { return width; }
    public int height() { return height; }
    public int tier(int x, int y) { return tier[y * width + x]; }
    public byte kind(int x, int y) { return kind[y * width + x]; }
    public boolean isFace(int x, int y) { return kind(x, y) == ElevationMap.FACE; }
    public boolean isRamp(int x, int y) { return kind(x, y) == ElevationMap.RAMP; }
    /** {@link #isFace} that answers false off the map. */
    public boolean isFaceInBounds(int x, int y) { return inBounds(x, y) && isFace(x, y); }
    /** A cell elevation touches at all: raised, face or ramp. */
    public boolean touched(int x, int y) { return tier(x, y) > 0 || kind(x, y) != ElevationMap.GROUND; }

    public ElevationMap toMap(ElevationMap.SightRule rule) {
        return ElevationMap.of(width, height, tier, kind, rule);
    }

    public static ElevationLayout resolve(ElevationPlan plan) {
        int w = plan.width(), h = plan.height(), cells = w * h;
        byte[] raw = new byte[cells];
        for (ElevationPlan.Plateau plateau : plan.plateaus()) {
            stampPlateau(plan, plateau, raw);
        }
        byte[] tier = raw.clone();
        byte[] kind = new byte[cells];
        // Ramp corridors and their side walls.
        boolean[] corridor = new boolean[cells];
        for (ElevationPlan.Ramp ramp : plan.ramps()) {
            int lower = rampLowerTier(plan, ramp, raw);
            double fx = ramp.foot().x(), fy = ramp.foot().y();
            double ax = ramp.head().x() - fx, ay = ramp.head().y() - fy;
            double length = Math.hypot(ax, ay);
            double ux = ax / length, uy = ay / length;
            double half = ramp.width() / 2.0;
            double reach = half + plan.faceWidth() + 1;
            int x0 = clamp((int) Math.floor(Math.min(fx, fx + ax) - reach), w);
            int x1 = clamp((int) Math.ceil(Math.max(fx, fx + ax) + reach), w);
            int y0 = clamp((int) Math.floor(Math.min(fy, fy + ay) - reach), h);
            int y1 = clamp((int) Math.ceil(Math.max(fy, fy + ay) + reach), h);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x - fx, py = y - fy;
                    double along = px * ux + py * uy;
                    double across = Math.abs(-px * uy + py * ux);
                    if (along < 0 || along > length) continue;
                    int i = y * w + x;
                    if (across <= half) {
                        corridor[i] = true;
                        kind[i] = ElevationMap.RAMP;
                        tier[i] = (byte) lower;
                    } else if (across <= half + plan.faceWidth() && !corridor[i]) {
                        kind[i] = ElevationMap.FACE;
                        tier[i] = (byte) Math.max(raw[i], lower + 1);
                    }
                }
            }
        }
        // Cliff faces: top cells within the face width of lower ground. A ramp
        // counts at its upper tier here, so the plateau's edge at a ramp's head
        // stays open ground.
        byte[] reachTier = new byte[cells];
        for (int i = 0; i < cells; i++) {
            reachTier[i] = kind[i] == ElevationMap.RAMP ? (byte) (tier[i] + 1) : tier[i];
        }
        int fw = plan.faceWidth();
        byte[] faced = kind.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (kind[i] != ElevationMap.GROUND || tier[i] == 0) continue;
                boolean lowerNear = false;
                for (int dy = -fw; dy <= fw && !lowerNear; dy++) {
                    for (int dx = -fw; dx <= fw; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int n = ny * w + nx;
                        if (kind[n] == ElevationMap.FACE) continue;
                        if (reachTier[n] < tier[i]) { lowerNear = true; break; }
                    }
                }
                if (lowerNear) faced[i] = ElevationMap.FACE;
            }
        }
        ElevationLayout layout = new ElevationLayout(w, h, tier, faced);
        for (ElevationPlan.Ramp ramp : plan.ramps()) layout.requireRampEnds(ramp);
        layout.requireEveryPlateauReachable();
        return layout;
    }

    private static void stampPlateau(ElevationPlan plan, ElevationPlan.Plateau plateau, byte[] raw) {
        int w = plan.width(), h = plan.height();
        double reach = plateau.radius() + 2;
        double phase = (plan.seed() & 65535) * .013;
        var points = plateau.points();
        for (int s = 0; s < points.size(); s++) {
            var a = points.get(Math.max(0, s - 1));
            var b = points.get(s);
            int x0 = clamp((int) Math.floor(Math.min(a.x(), b.x()) - reach), w);
            int x1 = clamp((int) Math.ceil(Math.max(a.x(), b.x()) + reach), w);
            int y0 = clamp((int) Math.floor(Math.min(a.y(), b.y()) - reach), h);
            int y1 = clamp((int) Math.ceil(Math.max(a.y(), b.y()) + reach), h);
            double dx = b.x() - a.x(), dy = b.y() - a.y(), den = dx * dx + dy * dy;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x - a.x(), py = y - a.y();
                    double t = den == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / den));
                    double noise = Math.sin(x * .59 + Math.sin(y * .37) + phase) * .65
                            + Math.sin(y * 1.13 - x * .34 + phase) * .25
                            + Math.sin(x * 2.1 + y * 1.8 + phase) * .10;
                    double value = plateau.radius() - Math.hypot(px - t * dx, py - t * dy)
                            + plan.ruggedness() * 1.4 * noise;
                    int i = y * w + x;
                    if (value >= 0 && raw[i] < plateau.tier()) raw[i] = (byte) plateau.tier();
                }
            }
        }
    }

    private static int rampLowerTier(ElevationPlan plan, ElevationPlan.Ramp ramp, byte[] raw) {
        int w = plan.width();
        int foot = raw[(int) Math.round(ramp.foot().y()) * w + (int) Math.round(ramp.foot().x())];
        int head = raw[(int) Math.round(ramp.head().y()) * w + (int) Math.round(ramp.head().x())];
        if (head != foot + 1) {
            throw new IllegalArgumentException("Ramp must climb exactly one tier: foot " + foot
                    + " at " + ramp.foot() + ", head " + head + " at " + ramp.head());
        }
        return foot;
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
                || kind(footX, footY) != ElevationMap.GROUND || kind(headX, headY) != ElevationMap.GROUND) {
            throw new IllegalArgumentException("Ramp ends must open onto ground: foot " + footX + "," + footY
                    + " head " + headX + "," + headY);
        }
    }

    /** Every walkable plateau region must reach a ramp; a hill nobody can climb is a raised mass. */
    private void requireEveryPlateauReachable() {
        int cells = width * height;
        boolean[] seen = new boolean[cells];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < cells; start++) {
            if (seen[start] || kind[start] != ElevationMap.GROUND || tier[start] == 0) continue;
            boolean ramped = false;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                int x = i % width, y = i / width;
                int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
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

    private static int clamp(int v, int size) { return Math.max(0, Math.min(size - 1, v)); }
}
