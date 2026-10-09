package com.dillon.starsectormarines.battle.air;

/**
 * Managed hover through a narrow ship corridor. Each straight segment has an
 * acceleration-limited trapezoidal velocity profile and ends stopped. Turns
 * happen at rest, so neither momentum nor a look-ahead cuts a bank's corner.
 * This drives the real AirBody; it is not a renderer path or ground taxi.
 */
public final class ShipBayTransit {
    public final ShipBayRoute route;
    public final boolean returning;
    public int segment;
    private float elapsed;
    private float age;

    public ShipBayTransit(ShipBayRoute route, boolean returning) {
        this.route = route;
        this.returning = returning;
        segment = returning ? route.points().size() - 1 : 0;
    }

    /** Advances one bounded physical sample, returning true at rest at the end. */
    public boolean advance(AirBody body, AirHandling hull, float dt) {
        if (dt <= 0f) return false;
        age += dt;
        int next = segment + (returning ? -1 : 1);
        if (next < 0 || next >= route.points().size()) {
            return !returning || turn(body, route.homeFacing(), hull, dt);
        }
        ShipBayRoute.Point from = route.points().get(segment), to = route.points().get(next);
        float dx = to.x() - from.x(), dy = to.y() - from.y();
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-5f) { segment = next; return false; }
        if (elapsed == 0f && !turn(body, AirBody.facingToward(dx, dy), hull, dt)) return false;
        float accel = Math.max(0.1f, Math.min(hull.accel(), hull.brakingAccel()));
        float speed = Math.min(2f, Math.max(0.1f, hull.maxSpeed()));
        float ramp = Math.min(speed / accel, (float) Math.sqrt(length / accel));
        float peak = accel * ramp;
        float cruise = Math.max(0f, (length - accel * ramp * ramp) / peak);
        float duration = ramp * 2f + cruise;
        elapsed = Math.min(duration, elapsed + dt);
        float distance, velocity;
        if (elapsed < ramp) {
            distance = 0.5f * accel * elapsed * elapsed;
            velocity = accel * elapsed;
        } else if (elapsed < ramp + cruise) {
            distance = 0.5f * accel * ramp * ramp + peak * (elapsed - ramp);
            velocity = peak;
        } else {
            float remaining = duration - elapsed;
            distance = length - 0.5f * accel * remaining * remaining;
            velocity = accel * remaining;
        }
        float oldVx = body.vx, oldVy = body.vy;
        body.x = from.x() + dx * distance / length;
        body.y = from.y() + dy * distance / length;
        body.vx = dx * velocity / length;
        body.vy = dy * velocity / length;
        body.ax = (body.vx - oldVx) / dt;
        body.ay = (body.vy - oldVy) / dt;
        body.angVelDegPerSec = 0f;
        if (elapsed >= duration) { segment = next; elapsed = 0f; }
        return false;
    }

    /** Low hover inside; climbs only beyond the aperture and settles at home. */
    public float altitude(AirBody body) {
        float outside = (body.x - route.aperture().centerX()) * route.aperture().outDx()
                + (body.y - route.aperture().centerY()) * route.aperture().outDy();
        float height = 0.12f + 0.88f * Math.max(0f, Math.min(1f, outside / 12f));
        float lift = returning ? 1f : Math.min(1f, age / 0.9f);
        float settle = returning ? Math.min(1f, body.distanceTo(route.home().x(), route.home().y()) / 2f) : 1f;
        return height * lift * settle;
    }

    private static boolean turn(AirBody body, float heading, AirHandling hull, float dt) {
        float delta = (heading - body.facingDegrees) % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        float limit = Math.max(1f, hull.maxTurnRateDegPerSec()) * dt;
        float step = Math.max(-limit, Math.min(limit, delta));
        body.facingDegrees += step;
        body.angVelDegPerSec = step / dt;
        body.ax = body.ay = 0f;
        return Math.abs(delta) <= limit;
    }
}
