package com.dillon.starsectormarines.battle.drone;

/**
 * A soft push that keeps drones from stacking on one point.
 *
 * <p>Each neighbour inside {@code radius} pushes the drone directly away from it,
 * harder the closer it is; the sum is capped at {@code radius}. Two drones sitting
 * on the same point have no direction between them, so the push falls back to a
 * fixed bearing taken from the pair of ids, which is stable and gives the two
 * opposite ways out. Pure and allocation-light: the caller adds the result to
 * the goal it is already steering to.
 */
public final class DroneSeparation {

    private DroneSeparation() {}

    /**
     * The offset to add to a goal for the drone at ({@code x, y}) with id {@code self}.
     *
     * @param ids neighbour ids, parallel to {@code xs}/{@code ys}; an entry equal to {@code self} is skipped
     * @return {@code {dx, dy}}, never longer than {@code radius}
     */
    public static float[] push(long self, float x, float y, long[] ids, float[] xs, float[] ys, int count,
                               float radius) {
        float px = 0f;
        float py = 0f;
        for (int i = 0; i < count; i++) {
            if (ids[i] == self) continue;
            float dx = x - xs[i];
            float dy = y - ys[i];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= radius) continue;
            float ux;
            float uy;
            if (d < 1e-3f) {
                // Same point: leave opposite ways, by id, so the two do not choose the same one.
                double a = Math.PI * 2.0 * DroneHash.unit(Math.min(self, ids[i]), 17);
                float sign = self < ids[i] ? 1f : -1f;
                ux = sign * (float) Math.cos(a);
                uy = sign * (float) Math.sin(a);
            } else {
                ux = dx / d;
                uy = dy / d;
            }
            float w = (radius - d) / radius * radius;
            px += ux * w;
            py += uy * w;
        }
        float len = (float) Math.sqrt(px * px + py * py);
        if (len > radius) {
            px *= radius / len;
            py *= radius / len;
        }
        return new float[]{px, py};
    }
}
