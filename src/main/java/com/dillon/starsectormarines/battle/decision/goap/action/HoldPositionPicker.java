package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.Arrays;

/** Primitive, objective-local capture posts, derived from current topology at each plan build. */
final class HoldPositionPicker {
    private HoldPositionPicker() {}

    static int[][] pick(TacticalNode node, int zoneId, int count,
                        NavigationGrid grid, ZoneGraph zones) {
        NavigationZone zone = zones.zoneById(zoneId);
        if (zone == null || zone.getCellCount() == 0) return anchor(node);
        int width = grid.getWidth();
        int left = Math.max(0, node.compoundLeft());
        int top = Math.max(0, node.compoundTop());
        int right = Math.min(width - 1, node.compoundRight());
        int bottom = Math.min(grid.getHeight() - 1, node.compoundBottom());
        int area = Math.max(0, right - left + 1) * Math.max(0, bottom - top + 1);
        int[] candidates = new int[Math.min(area, zone.getCellCount())];
        int size = 0;
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        // Read whichever representation is smaller: the local rectangle, or a small room.
        if (area < zone.getCellCount()) {
            for (int y = top; y <= bottom; y++) {
                for (int x = left; x <= right; x++) {
                    if (profile != null) profile.record(TickInnerProfile.Bucket.HOLD_POSITION_CELL, 0L);
                    if (zones.zoneIdAt(x, y) == zoneId && grid.isWalkable(x, y)) {
                        candidates[size++] = y * width + x;
                    }
                }
            }
        } else {
            for (int cell : zone.getCellIndices()) {
                if (profile != null) profile.record(TickInnerProfile.Bucket.HOLD_POSITION_CELL, 0L);
                int x = cell % width, y = cell / width;
                if (x >= left && x <= right && y >= top && y <= bottom && grid.isWalkableAt(cell)) {
                    candidates[size++] = cell;
                }
            }
        }
        if (size == 0) {
            // Malformed/outdated authored bounds must not station the squad in a different
            // capture zone. A rare linear fallback finds ONE legal post, never a global spread.
            if (profile != null) profile.record(TickInnerProfile.Bucket.HOLD_POSITION_FALLBACK, 0L);
            int nearest = -1;
            float distance = Float.MAX_VALUE;
            for (int cell : zone.getCellIndices()) {
                if (profile != null) profile.record(TickInnerProfile.Bucket.HOLD_POSITION_CELL, 0L);
                if (!grid.isWalkableAt(cell)) continue;
                float d = distanceSquared(cell % width, cell / width, node.anchorX, node.anchorY);
                if (d < distance) { nearest = cell; distance = d; }
            }
            return nearest < 0 ? anchor(node) : new int[][]{{nearest % width}, {nearest / width}};
        }
        return spread(candidates, size, width, node.anchorX, node.anchorY, count);
    }

    /** Same farthest-point rule and first-candidate tie break; O(candidates * posts). */
    static int[][] spread(int[] candidates, int size, int width, int anchorX, int anchorY, int count) {
        int n = Math.max(1, Math.min(count, size));
        int[] xs = new int[n], ys = new int[n];
        float[] nearest = new float[size];
        Arrays.fill(nearest, Float.MAX_VALUE);
        int selected = 0;
        float seedDistance = Float.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            int cell = candidates[i];
            float d = distanceSquared(cell % width, cell / width, anchorX, anchorY);
            if (d < seedDistance) { seedDistance = d; selected = i; }
        }
        int picked = 0;
        while (picked < n) {
            int cell = candidates[selected];
            int x = cell % width, y = cell / width;
            xs[picked] = x;
            ys[picked++] = y;
            if (picked == n) break;
            float farthest = 0f;
            int next = -1;
            for (int i = 0; i < size; i++) {
                int candidate = candidates[i];
                nearest[i] = Math.min(nearest[i], distanceSquared(
                        candidate % width, candidate / width, x, y));
                if (nearest[i] > farthest) { farthest = nearest[i]; next = i; }
            }
            if (next < 0) break;
            selected = next;
        }
        return new int[][]{Arrays.copyOf(xs, picked), Arrays.copyOf(ys, picked)};
    }

    private static int[][] anchor(TacticalNode node) {
        return new int[][]{{node.anchorX}, {node.anchorY}};
    }

    private static float distanceSquared(int ax, int ay, int bx, int by) {
        float dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }
}
