package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * A hull as something a body on foot reaches to strike ({@code colony-breach-nouns.md}, A standing vehicle): how far
 * a point stands from its side, and the ground beside it a striker closes to. A hull's centre lies inside its
 * footprint, where the hull's shove never lets a body stand, so reach is to the side and a striker heads for a cell
 * beside it, then closes the rest of the way until it {@link #touches touches} the side, and strikes. Pure; frame as
 * {@link HullImpact}.
 */
public final class HullReach {

    private HullReach() {}

    /**
     * How far past its own radius a body may stand off a hull's side and still touch it, cells: past where the hull's
     * shove holds a body (its radius and 0.12), so a body held there touches.
     */
    public static final float TOUCH = .2f;

    /** Whether a body of {@code radius} at {@code (px, py)} touches the side of a hull: close enough to strike it. */
    public static boolean touches(float hx, float hy, float facing, float halfLength, float halfWidth,
                                  float px, float py, float radius) {
        return gap(hx, hy, facing, halfLength, halfWidth, px, py) <= radius + TOUCH;
    }

    /**
     * The way from {@code (px, py)} to the nearest point of a hull's side, {unit x, unit y, distance}, or null from
     * inside it: where a body beside the hull steps to close on it.
     */
    public static float[] toward(float hx, float hy, float facing, float halfLength, float halfWidth,
                                 float px, float py) {
        double rad = Math.toRadians(facing);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float dx = px - hx, dy = py - hy;
        float along = Math.max(-halfLength, Math.min(halfLength, dx * fx + dy * fy));
        float across = Math.max(-halfWidth, Math.min(halfWidth, dx * fy - dy * fx));
        // Back to the world: forward is (fx, fy), the hull's right (fy, -fx).
        float vx = hx + fx * along + fy * across - px, vy = hy + fy * along - fx * across - py;
        float distance = (float) Math.hypot(vx, vy);
        return distance < 1e-4f ? null : new float[]{vx / distance, vy / distance, distance};
    }

    /** How far {@code (px, py)} stands from the side of a hull at {@code (hx, hy)}; zero inside it. */
    public static float gap(float hx, float hy, float facing, float halfLength, float halfWidth, float px, float py) {
        return HullImpact.surfaceDistance(hx, hy, facing, halfLength, halfWidth, px, py);
    }

    /**
     * The walkable cell nearest {@code (px, py)} whose centre stands between {@code clearance} and {@code reach} off
     * the hull's side, or null when there is none: where a body that means to strike the hull goes.
     */
    public static int[] beside(NavigationGrid grid, float hx, float hy, float facing, float halfLength,
                               float halfWidth, float px, float py, float reach, float clearance) {
        if (!(reach > clearance)) return null;
        int span = (int) Math.ceil(Math.hypot(halfLength, halfWidth) + reach);
        int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy);
        int[] best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int y = cy - span; y <= cy + span; y++) {
            for (int x = cx - span; x <= cx + span; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                float side = gap(hx, hy, facing, halfLength, halfWidth, x + 0.5f, y + 0.5f);
                if (side < clearance || side > reach) continue;
                float dx = x + 0.5f - px, dy = y + 0.5f - py, d = dx * dx + dy * dy;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }
}
