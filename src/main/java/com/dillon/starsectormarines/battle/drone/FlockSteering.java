package com.dillon.starsectormarines.battle.drone;

/**
 * The flocking rule: a member's next velocity from its own state, its flock-mates' and the way to the anchor.
 *
 * <p>Four terms, each brought to at most unit length and weighted by {@link FlockWeights}:
 * <ul>
 *   <li><b>separation</b>, the shipped {@link DroneSeparation} push away from mates closer than
 *       {@link FlockWeights#separationCells()}, so no two members stack;</li>
 *   <li><b>alignment</b>, the neighbours' mean velocity, so the body turns together;</li>
 *   <li><b>cohesion</b>, toward the neighbours' centre, so a body split round a wall closes again past it;</li>
 *   <li><b>anchor</b>, along the member's own way to the anchor, fading over the last
 *       {@link FlockWeights#arriveCells()} so members settle round it rather than all onto it.</li>
 * </ul>
 * The weighted sum is capped at one and scaled to {@link FlockWeights#maxSpeed()}.
 *
 * <p>Pure and topology-free: the caller supplies the way to the anchor as a direction and a distance, which is
 * where walls come in ({@link FlockField}). Neighbours are only the flock's own members, so the search is bounded by
 * the flock's size, never by the battle's.
 */
public final class FlockSteering {

    private FlockSteering() {}

    /**
     * The velocity member {@code self} asks for, as {@code {vx, vy}} in cells per second.
     *
     * @param self      index of the member in the parallel arrays
     * @param ids       member ids, for the separation push's tie-break when two members share a point
     * @param xs        member positions, cells
     * @param vxs       member velocities, cells per second
     * @param count     members in the arrays
     * @param anchorDx  unit direction of the member's way to the anchor ({@code 0, 0} for none)
     * @param anchorDist how far the member still has to go to the anchor along that way, cells
     */
    public static float[] velocity(int self, long[] ids, float[] xs, float[] ys, float[] vxs, float[] vys, int count,
                                   float anchorDx, float anchorDy, float anchorDist, FlockWeights w) {
        float x = xs[self];
        float y = ys[self];

        float[] push = DroneSeparation.push(ids[self], x, y, ids, xs, ys, count, w.separationCells());
        float sx = push[0] / w.separationCells();
        float sy = push[1] / w.separationCells();

        float sumVx = 0f, sumVy = 0f, sumX = 0f, sumY = 0f;
        int n = 0;
        float r2 = w.neighbourCells() * w.neighbourCells();
        for (int i = 0; i < count; i++) {
            if (i == self) continue;
            float dx = xs[i] - x;
            float dy = ys[i] - y;
            if (dx * dx + dy * dy > r2) continue;
            sumVx += vxs[i];
            sumVy += vys[i];
            sumX += xs[i];
            sumY += ys[i];
            n++;
        }
        float ax = 0f, ay = 0f, cx = 0f, cy = 0f;
        if (n > 0) {
            float[] align = capped(sumVx / n / w.maxSpeed(), sumVy / n / w.maxSpeed());
            ax = align[0];
            ay = align[1];
            float[] close = capped((sumX / n - x) / w.neighbourCells(), (sumY / n - y) / w.neighbourCells());
            cx = close[0];
            cy = close[1];
        }

        float pull = Math.min(1f, Math.max(0f, anchorDist) / w.arriveCells());
        float px = anchorDx * pull;
        float py = anchorDy * pull;

        float[] sum = capped(w.separation() * sx + w.alignment() * ax + w.cohesion() * cx + w.anchor() * px,
                w.separation() * sy + w.alignment() * ay + w.cohesion() * cy + w.anchor() * py);
        return new float[]{sum[0] * w.maxSpeed(), sum[1] * w.maxSpeed()};
    }

    private static float[] capped(float x, float y) {
        float len = (float) Math.sqrt(x * x + y * y);
        if (len <= 1f) return new float[]{x, y};
        return new float[]{x / len, y / len};
    }
}
