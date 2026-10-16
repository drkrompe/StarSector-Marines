package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.AirMissileSpec;

/** Launch-only lead for an unsteered charge; the target keeps its observed horizontal velocity. */
public final class UnguidedMissileAim {
    private static final int REFINEMENTS = 28;
    private UnguidedMissileAim() { }

    /** Earliest reachable interception within fuel life, or zero when no interception can be predicted. */
    public static float interceptSeconds(float dx, float dy, float dz, float vx, float vy, AirMissileSpec spec) {
        if (dx * dx + dy * dy + dz * dz <= .000001f) return 0f;
        double end = spec.lifetime();
        if (spec.launchSpeed() < spec.cruiseSpeed()) {
            // Acceleration can make separation non-convex; bracket bounded time samples instead.
            double previous = 0;
            for (int sample = 1; sample <= 128; sample++) {
                double time = end * sample / 128;
                if (separation(dx, dy, dz, vx, vy, spec, time) <= 0)
                    return root(dx, dy, dz, vx, vy, spec, previous, time);
                previous = time;
            }
            return 0f;
        }
        // For a slowing charge, distance-to-target minus traveled distance is convex.
        // Find its minimum first, so even a short interception window cannot fall between time samples.
        if (separationRate(dx, dy, dz, vx, vy, spec, 0) >= 0) return 0f;
        double minimum = end;
        if (separationRate(dx, dy, dz, vx, vy, spec, end) > 0) {
            double low = 0, high = end;
            for (int i = 0; i < REFINEMENTS; i++) {
                double mid = (low + high) * .5;
                if (separationRate(dx, dy, dz, vx, vy, spec, mid) < 0) low = mid;
                else high = mid;
            }
            minimum = (low + high) * .5;
        }
        return separation(dx, dy, dz, vx, vy, spec, minimum) <= 0
                ? root(dx, dy, dz, vx, vy, spec, 0, minimum) : 0f;
    }

    /** Exact distance along the charge's initial ray, including its cruise-speed floor. */
    public static double distanceAfter(AirMissileSpec spec, double seconds) {
        double initial = spec.launchSpeed(), cruise = spec.cruiseSpeed();
        double rate = initial > cruise ? -spec.deceleration() : spec.acceleration();
        double ramp = initial == cruise ? 0 : Math.abs((cruise - initial) / rate);
        double accelerating = Math.min(seconds, ramp);
        return initial * accelerating + .5 * rate * accelerating * accelerating
                + cruise * Math.max(0, seconds - ramp);
    }

    private static double separation(float dx, float dy, float dz, float vx, float vy,
                                     AirMissileSpec spec, double time) {
        double x = dx + vx * time, y = dy + vy * time;
        return Math.sqrt(x * x + y * y + dz * dz) - distanceAfter(spec, time);
    }

    private static double separationRate(float dx, float dy, float dz, float vx, float vy,
                                         AirMissileSpec spec, double time) {
        double x = dx + vx * time, y = dy + vy * time;
        double distance = Math.sqrt(x * x + y * y + dz * dz);
        double targetRate = distance > 1e-12 ? (x * vx + y * vy) / distance : 0;
        return targetRate - Math.max(spec.cruiseSpeed(), spec.launchSpeed() - spec.deceleration() * time);
    }

    private static float root(float dx, float dy, float dz, float vx, float vy,
                              AirMissileSpec spec, double low, double high) {
        for (int i = 0; i < REFINEMENTS; i++) {
            double mid = (low + high) * .5;
            if (separation(dx, dy, dz, vx, vy, spec, mid) > 0) low = mid;
            else high = mid;
        }
        return (float) high;
    }
}
