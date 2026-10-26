package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * A hull as something a body on foot reaches to strike ({@code colony-breach-nouns.md}, A standing vehicle): how far
 * a point stands from its side, and the ground beside it a striker closes to. A hull's centre lies inside its
 * footprint, where the hull's shove never lets a body stand, so reach is to the side and a striker heads for a cell
 * beside it. Pure; frame as {@link HullImpact}.
 */
public final class HullReach {

    private HullReach() {}

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
