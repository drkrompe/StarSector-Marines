package com.dillon.starsectormarines.battle.air;

import java.util.List;

/** A committed approach before a gunship joins its station; ordinary flight and damage still apply. */
public final class GunshipIngress {
    public record Point(float x, float y) {}
    private final List<Point> points;
    private int next;

    public GunshipIngress(List<Point> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("An ingress needs at least one waypoint");
        this.points = List.copyOf(points);
    }

    public int waypoint() { return next; }
    public boolean complete() { return next == points.size(); }

    /** Returns true while the approach owns steering. No teleport or immunity at waypoint handoff. */
    public boolean steer(AirBody body, AirHandling handling, float dt) {
        if (complete()) return false;
        Point goal = points.get(next);
        float gate = Math.max(3f, handling.minTurnRadiusCells());
        if (body.distanceTo(goal.x, goal.y) <= gate) {
            next++;
            if (complete()) return false;
            goal = points.get(next);
        }
        AirSteeringSystem.steer(body, goal.x, goal.y, SteeringMode.CRUISE, handling, dt);
        return true;
    }
}
