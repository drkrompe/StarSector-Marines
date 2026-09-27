package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;

import java.util.Arrays;

/** Worker-owned, search-local counts. No entity references survive a search. */
final class LocalFiringSpread {
    static final int MAX_CELLS = 16384;
    final UnitSpatialIndex.PointBuffer points = new UnitSpatialIndex.PointBuffer();
    private int[] counts = new int[0];
    private int firstX, firstY, lastX, lastY, width;
    boolean built;

    boolean reset(int x0, int y0, int x1, int y1) {
        built = false;
        long columns = (long) x1 - x0 + 1, rows = (long) y1 - y0 + 1;
        if (columns <= 0 || rows <= 0 || columns > MAX_CELLS || rows > MAX_CELLS
                || columns * rows > MAX_CELLS) return false;
        firstX = x0; firstY = y0; lastX = x1; lastY = y1;
        width = x1 - x0 + 1;
        return true;
    }

    void beginBuild() {
        int area = width * (lastY - firstY + 1);
        if (counts.length < area) counts = new int[area];
        else Arrays.fill(counts, 0, area, 0);
    }

    float centerX() { return (firstX + lastX) * 0.5f + 0.5f; }
    float centerY() { return (firstY + lastY) * 0.5f + 0.5f; }
    float gatherRadius() {
        double dx = (lastX - firstX) * 0.5;
        double dy = (lastY - firstY) * 0.5;
        // Round outward: boundary points must survive the broad-phase float test.
        return Math.nextUp((float) (Math.sqrt(dx * dx + dy * dy)
                + TacticalScoring.FIRING_AOE_SPREAD_RADIUS));
    }
    int countAt(int x, int y) { return counts[(y - firstY) * width + x - firstX]; }

    void stampCurrent(float x, float y) {
        stamp(x, y, Float.NaN, Float.NaN, firstX, firstY, lastX, lastY);
    }

    void stampDestination(float x, float y, float liveX, float liveY, int bucketX, int bucketY) {
        int radius = TacticalScoring.FIRING_AOE_SPREAD_RADIUS;
        int bucket = UnitSpatialIndex.BUCKET;
        // Destination entries retain their old bucket until the host refreshes
        // the index. Match the small legacy query's bucket gate even if the live
        // endpoint has moved into a different bucket during member execution.
        stamp(x, y, liveX, liveY,
                Math.max(firstX, bucketX * bucket - radius),
                Math.max(firstY, bucketY * bucket - radius),
                Math.min(lastX, (bucketX + 1) * bucket - 1 + radius),
                Math.min(lastY, (bucketY + 1) * bucket - 1 + radius));
    }

    private void stamp(float x, float y, float liveX, float liveY,
                       int x0, int y0, int x1, int y1) {
        int radius = TacticalScoring.FIRING_AOE_SPREAD_RADIUS;
        int r2 = radius * radius;
        x0 = Math.max(x0, (int) Math.ceil(x - 0.5f - radius));
        y0 = Math.max(y0, (int) Math.ceil(y - 0.5f - radius));
        x1 = Math.min(x1, (int) Math.floor(x - 0.5f + radius));
        y1 = Math.min(y1, (int) Math.floor(y - 0.5f + radius));
        for (int cy = y0; cy <= y1; cy++) for (int cx = x0; cx <= x1; cx++) {
            float dx = x - (cx + 0.5f), dy = y - (cy + 0.5f);
            if (dx * dx + dy * dy > r2) continue;
            float ldx = liveX - (cx + 0.5f), ldy = liveY - (cy + 0.5f);
            if (ldx * ldx + ldy * ldy <= r2) continue;
            counts[(cy - firstY) * width + cx - firstX]++;
        }
    }
}
