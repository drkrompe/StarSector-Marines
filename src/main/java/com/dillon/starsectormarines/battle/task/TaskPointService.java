package com.dillon.starsectormarines.battle.task;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Battle-owned registry and exclusive reservation service for task points.
 *
 * <p>Claims are actor-owned and deterministic: the nearest free point wins,
 * with registration order breaking equal-distance ties. This is deliberately
 * independent of ambient route authorship so ordinary battle tasks can claim
 * the same fixture, defensive
 * post, workbench, firing lane, or interaction site without inventing their
 * own occupancy convention.</p>
 */
public final class TaskPointService {

    private final NavigationGrid grid;
    private final Map<String, TaskPoint> points = new LinkedHashMap<>();
    private final Map<String, Long> claimantByPoint = new LinkedHashMap<>();
    private final Map<Long, String> pointByActor = new LinkedHashMap<>();

    public TaskPointService(NavigationGrid grid) {
        if (grid == null) throw new IllegalArgumentException("task point grid is required");
        this.grid = grid;
    }

    public void register(TaskPoint point) {
        if (point == null) throw new IllegalArgumentException("task point is required");
        if (!grid.inBounds(point.cellX(), point.cellY())
                || !grid.isWalkable(point.cellX(), point.cellY())) {
            throw new IllegalArgumentException("task point must stand on walkable floor: " + point.id());
        }
        if (points.putIfAbsent(point.id(), point) != null) {
            throw new IllegalArgumentException("duplicate task point id: " + point.id());
        }
    }

    /** Returns the actor's existing claim when it belongs to {@code group}, otherwise claims the nearest free point. */
    public TaskPoint claimNearest(long actorId, String group, float fromX, float fromY) {
        if (actorId == 0L) throw new IllegalArgumentException("task point claimant is required");
        if (group == null || group.isBlank()) throw new IllegalArgumentException("task point group is required");
        String existingId = pointByActor.get(actorId);
        if (existingId != null) {
            TaskPoint existing = points.get(existingId);
            if (existing != null && existing.group().equals(group)) return existing;
        }
        TaskPoint best = null;
        float bestDistanceSq = Float.POSITIVE_INFINITY;
        for (TaskPoint candidate : points.values()) {
            if (!candidate.group().equals(group) || claimantByPoint.containsKey(candidate.id())) continue;
            float dx = candidate.worldX() - fromX;
            float dy = candidate.worldY() - fromY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq < bestDistanceSq) {
                best = candidate;
                bestDistanceSq = distanceSq;
            }
        }
        if (best != null) {
            release(actorId);
            claimantByPoint.put(best.id(), actorId);
            pointByActor.put(actorId, best.id());
        }
        return best;
    }

    public TaskPoint claimedPoint(long actorId) {
        String pointId = pointByActor.get(actorId);
        return pointId != null ? points.get(pointId) : null;
    }

    public long claimant(String pointId) {
        Long claimant = claimantByPoint.get(pointId);
        return claimant != null ? claimant : 0L;
    }

    public int registeredCount() { return points.size(); }

    public int claimCount() { return pointByActor.size(); }

    public void release(long actorId) {
        String pointId = pointByActor.remove(actorId);
        if (pointId != null) claimantByPoint.remove(pointId, actorId);
    }
}
