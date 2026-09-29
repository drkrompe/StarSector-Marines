package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Deterministic terrain guide, never a hit prediction or body query. */
public final class DirectControlAimLane {
    private DirectControlAimLane() {}
    public record Lane(float x, float y, float aimX, float aimY, float stopFraction, float stopX, float stopY) {
        public Lane(float x, float y, float aimX, float aimY, float stopFraction) {
            this(x, y, aimX, aimY, stopFraction, x + (aimX - x) * stopFraction, y + (aimY - y) * stopFraction);
        }
        public boolean blocked() { return Float.isFinite(stopFraction); }
    }
    public record Stroke(float x, float y, float endX, float endY, float width, Color color) {}
    /** An unseen muzzle suppresses the guide consistently, whether its body guard is clear or blocked. */
    public static Lane observed(NavigationGrid grid, DirectControlAimOrigins.Origin origin,
                                float aimX, float aimY, BiPredicate<Integer, Integer> revealed) {
        if (!revealed.test((int) Math.floor(origin.x()), (int) Math.floor(origin.y()))) return null;
        Lane guard = trace(grid, origin.bodyX(), origin.bodyY(), origin.x(), origin.y());
        Lane lane = guard.blocked() ? new Lane(origin.bodyX(), origin.bodyY(), aimX, aimY,
                0f, guard.stopX(), guard.stopY()) : trace(grid, origin.x(), origin.y(), aimX, aimY);
        return visiblePrefix(lane, revealed);
    }

    public static Lane trace(NavigationGrid grid, float x, float y, float aimX, float aimY) {
        float t = Float.NaN;
        long wall = grid.firstWallOnLine(x, y, aimX, aimY);
        int wx = (int) wall, wy = (int) (wall >>> 32);
        if (wx != -1 || wy != -1) t = BallisticResolver.segmentCellEntryFraction(x, y, aimX, aimY, wx, wy);
        var barrier = grid.firstProjectileBlockingEdgeBarrierOnLine(x, y, aimX, aimY);
        if (barrier != null) {
            float crossing = barrier.direction() == Direction.E
                    ? (barrier.cellX() + 1f - x) / (aimX - x)
                    : (barrier.cellY() + 1f - y) / (aimY - y);
            if (!Float.isFinite(t) || crossing < t) t = crossing;
        }
        return new Lane(x, y, aimX, aimY, t);
    }
    /** Cap terrain annotations before the first unrevealed cell; the cursor remains independent. */
    public static Lane visiblePrefix(Lane lane, BiPredicate<Integer, Integer> revealed) {
        if (!lane.blocked()) return straightVisiblePrefix(lane, revealed);
        Lane first = straightVisiblePrefix(new Lane(lane.x, lane.y, lane.stopX, lane.stopY, Float.NaN), revealed);
        if (first == null) return null;
        if (first.aimX != lane.stopX || first.aimY != lane.stopY
                || !revealed.test((int) Math.floor(Math.nextAfter(lane.stopX, lane.stopX + lane.stopX - lane.x)),
                        (int) Math.floor(Math.nextAfter(lane.stopY, lane.stopY + lane.stopY - lane.y)))) return first;
        Lane tail = straightVisiblePrefix(new Lane(lane.stopX, lane.stopY, lane.aimX, lane.aimY, Float.NaN), revealed);
        if (tail == null) return first;
        return new Lane(lane.x, lane.y, tail.aimX, tail.aimY, lane.stopFraction, lane.stopX, lane.stopY);
    }

    private static Lane straightVisiblePrefix(Lane lane, BiPredicate<Integer, Integer> revealed) {
        int x = (int) Math.floor(lane.x), y = (int) Math.floor(lane.y);
        if (!revealed.test(x, y)) return null;
        float dx = lane.aimX - lane.x, dy = lane.aimY - lane.y;
        int endX = (int) Math.floor(lane.aimX), endY = (int) Math.floor(lane.aimY);
        int sx = Float.compare(dx, 0), sy = Float.compare(dy, 0);
        float tx = sx == 0 || x == endX ? Float.POSITIVE_INFINITY : ((sx > 0 ? x + 1f : x) - lane.x) / dx;
        float ty = sy == 0 || y == endY ? Float.POSITIVE_INFINITY : ((sy > 0 ? y + 1f : y) - lane.y) / dy;
        int budget = Math.abs(endX - x) + Math.abs(endY - y);
        while ((x != endX || y != endY) && budget-- > 0) {
            float at = Math.min(tx, ty);
            boolean crossX = tx <= ty, crossY = ty <= tx;
            if (crossX) x += sx;
            if (crossY) y += sy;
            if (!revealed.test(x, y)) {
                float cap = Math.max(0f, Math.nextDown(at));
                float capX = Math.nextAfter(lane.x + dx * cap, lane.x);
                float capY = Math.nextAfter(lane.y + dy * cap, lane.y);

                return new Lane(lane.x, lane.y, capX, capY, Float.NaN,
                        lane.stopX, lane.stopY);
            }
            if (crossX) tx = x == endX ? Float.POSITIVE_INFINITY : tx + Math.abs(1f / dx);
            if (crossY) ty = y == endY ? Float.POSITIVE_INFINITY : ty + Math.abs(1f / dy);
        }
        return lane;
    }

    /** Screen-space strokes consumed unchanged by the live and headless drains. */
    public static List<Stroke> strokes(Lane lane, float scale, float offsetX, float offsetY) {
        List<Stroke> result = new ArrayList<>();
        float x = lane.x * scale + offsetX, y = lane.y * scale + offsetY;
        float dx = (lane.aimX - lane.x) * scale, dy = (lane.aimY - lane.y) * scale;
        float stopX = lane.blocked() ? lane.stopX * scale + offsetX : x + dx;
        float stopY = lane.blocked() ? lane.stopY * scale + offsetY : y + dy;
        Color clear = new Color(110, 215, 255, 72);
        result.add(new Stroke(x, y, stopX, stopY, 1f, clear));
        if (lane.blocked()) {
            dx = x + dx - stopX; dy = y + dy - stopY;
            float length = (float) Math.hypot(dx, dy);
            if (length > 0f) for (float at = 4f; at < length; at += 10f) {
                float end = Math.min(length, at + 4f);
                result.add(new Stroke(stopX + dx * at / length, stopY + dy * at / length,
                        stopX + dx * end / length, stopY + dy * end / length, 1f,
                        new Color(110, 215, 255, 35)));
            }
            float sx = stopX, sy = stopY;
            Color amber = new Color(240, 181, 75, 160);
            result.add(new Stroke(sx - 3f, sy - 3f, sx + 3f, sy + 3f, 1f, amber));
            result.add(new Stroke(sx - 3f, sy + 3f, sx + 3f, sy - 3f, 1f, amber));
        }
        return result;
    }
}
