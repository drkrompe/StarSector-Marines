package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.WeaponDef.Guidance;
import com.dillon.starsectormarines.battle.weapon.AirMissileSpec;

/** A finite-fuel, turn-limited air-target missile. Position is simulation-owned. */
public final class GuidedMissile {
    public float x, y, z, previousX, previousY, previousZ;
    public float heading, pitch, speed, remaining;
    public float stepSeconds;
    public final float launchBearingDegrees;
    private final float maxSpeed;
    private final Guidance guidance;
    private final AirMissileSpec spec;

    public GuidedMissile(float x, float y, float facingDegrees, float maxSpeed, Guidance guidance) {
        this.x = previousX = x;
        this.y = previousY = y;
        launchBearingDegrees = facingDegrees;
        heading = (float) Math.toRadians(facingDegrees + 90f);
        pitch = (float) Math.toRadians(25f);
        this.maxSpeed = maxSpeed;
        this.guidance = guidance;
        this.spec = null;
        remaining = guidance.lifetime();
        speed = Math.min(4f, maxSpeed);
    }

    public GuidedMissile(float x, float y, float facingDegrees, float targetX, float targetY,
                         float targetZ, AirMissileSpec spec) {
        this.x = previousX = x; this.y = previousY = y;
        launchBearingDegrees = facingDegrees;
        heading = (float) Math.toRadians(facingDegrees + 90f);
        pitch = (float) Math.atan2(targetZ, Math.hypot(targetX - x, targetY - y));
        this.spec = spec;
        this.guidance = new Guidance(spec.turnRateDegPerSec(), spec.acceleration(), spec.lifetime());
        maxSpeed = spec.cruiseSpeed();
        speed = spec.launchSpeed(); remaining = spec.lifetime();
    }

    /** Positive interception time for a constant-speed projectile and a moving target. */
    public static float leadSeconds(float dx, float dy, float dz, float vx, float vy, float speed, float limit) {
        double a = vx * vx + vy * vy - speed * speed;
        double b = 2d * (dx * vx + dy * vy);
        double c = dx * dx + dy * dy + dz * dz;
        double t = -1;
        if (Math.abs(a) < .000001) { if (Math.abs(b) > .000001) t = -c / b; }
        else {
            double d = b * b - 4 * a * c;
            if (d >= 0) {
                double t1 = (-b - Math.sqrt(d)) / (2 * a), t2 = (-b + Math.sqrt(d)) / (2 * a);
                t = t1 > 0 && t2 > 0 ? Math.min(t1, t2) : Math.max(t1, t2);
            }
        }
        return t > 0 ? (float) Math.min(t, limit) : 0f;
    }

    /** A lost target leaves the round flying its last bearing until fuel exhaustion. */
    public void advance(float dt, boolean tracking, float tx, float ty, float tz) {
        previousX = x; previousY = y; previousZ = z;
        dt = Math.min(dt, remaining);
        stepSeconds = dt;
        if (tracking && (spec == null || spec.guided())) {
            float dx = tx - x, dy = ty - y;
            float turn = (float) Math.toRadians(guidance.turnRate()) * dt;
            heading = toward(heading, (float) Math.atan2(dy, dx), turn);
            pitch = toward(pitch, (float) Math.atan2(tz - z, Math.hypot(dx, dy)), turn);
        }
        float nextSpeed = speed > maxSpeed && spec != null
                ? Math.max(maxSpeed, speed - spec.deceleration() * dt)
                : Math.min(maxSpeed, speed + guidance.acceleration() * dt);
        float distance = (speed + nextSpeed) * .5f * dt;
        x += (float) (Math.cos(heading) * Math.cos(pitch)) * distance;
        y += (float) (Math.sin(heading) * Math.cos(pitch)) * distance;
        z += (float) Math.sin(pitch) * distance;
        speed = nextSpeed;
        remaining = Math.max(0f, remaining - dt);
    }

    public boolean contacts(float tx, float ty, float tz, float radius) {
        float dx = x - previousX, dy = y - previousY, dz = z - previousZ;
        float lengthSq = dx * dx + dy * dy + dz * dz;
        float t = lengthSq > 0f ? ((tx - previousX) * dx + (ty - previousY) * dy + (tz - previousZ) * dz) / lengthSq : 0f;
        t = Math.max(0f, Math.min(1f, t));
        float ex = previousX + dx * t - tx, ey = previousY + dy * t - ty, ez = previousZ + dz * t - tz;
        return ex * ex + ey * ey + ez * ez <= radius * radius;
    }

    /** Earliest segment/sphere contact, or positive infinity when the travelled segment misses. */
    public float contactFraction(float tx, float ty, float tz, float radius) {
        return contactFractionMoving(tx, ty, tz, tx, ty, tz, radius);
    }

    /** Earliest contact in the common tick, sweeping projectile motion relative to the moving target. */
    public float contactFractionMoving(float fromX, float fromY, float fromZ,
                                       float toX, float toY, float toZ, float radius) {
        float dx = x - previousX - (toX - fromX);
        float dy = y - previousY - (toY - fromY);
        float dz = z - previousZ - (toZ - fromZ);
        float ox = previousX - fromX, oy = previousY - fromY, oz = previousZ - fromZ;
        float c = ox * ox + oy * oy + oz * oz - radius * radius;
        if (c <= 0) return 0f;
        float a = dx * dx + dy * dy + dz * dz;
        if (a == 0) return Float.POSITIVE_INFINITY;
        float b = 2f * (ox * dx + oy * dy + oz * dz);
        float d = b * b - 4f * a * c;
        if (d < 0) return Float.POSITIVE_INFINITY;
        float t = (-b - (float) Math.sqrt(d)) / (2f * a);
        return t >= 0 && t <= 1 ? t : Float.POSITIVE_INFINITY;
    }

    /** Exhaust exists only while a powered motor is active; proximity charges have none. */
    public boolean powered() { return remaining > 0f && (spec == null || spec.guided()); }

    public float visualBearingDegrees() {
        if (x == previousX && y == previousY && z == previousZ) return launchBearingDegrees;
        return (float) Math.toDegrees(Math.atan2(y + z - previousY - previousZ, x - previousX)) - 90f;
    }

    private static float toward(float from, float to, float limit) {
        float delta = (float) Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + Math.max(-limit, Math.min(limit, delta));
    }
}
