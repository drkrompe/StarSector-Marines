package com.dillon.starsectormarines.battle.air;

/** Unpowered motion: retain horizontal velocity in the air, dissipate it against the ground. */
public final class AirCrashMotion {
    public static final float FALL_SECONDS = 1.6f;
    private static final float SKID_DECELERATION = 8f;

    private AirCrashMotion() {}

    /** Loss of hover stability builds lateral speed; an already fast hull keeps its momentum. */
    public static void beginFall(AirBody body, float instability) {
        float speed = body.speed();
        float acceleration = Math.max(0f, 8f - speed) / FALL_SECONDS;
        double direction = speed > 1f ? Math.atan2(body.vy, body.vx)
                : Math.toRadians(body.facingDegrees + (instability - .5f) * 140f);
        body.ax = (float) Math.cos(direction) * acceleration;
        body.ay = (float) Math.sin(direction) * acceleration;
        if (acceleration > 0f) body.angVelDegPerSec += (instability < .5f ? -1f : 1f) * 35f;
    }

    public static float altitude(float elapsed, float initialAltitude) {
        float t = Math.min(1f, elapsed / FALL_SECONDS);
        return initialAltitude * (1f - t * t);
    }

    /** No flight controller or horizontal brake remains to arrest the fall. */
    public static void fall(AirBody body, float dt) {
        body.x += body.vx * dt + .5f * body.ax * dt * dt;
        body.y += body.vy * dt + .5f * body.ay * dt * dt;
        body.vx += body.ax * dt;
        body.vy += body.ay * dt;
        tumble(body, dt, .5f);
    }

    /** Integrate friction only until rest, so a long step cannot reverse the wreck. */
    public static boolean skid(AirBody body, float dt) {
        float speed = body.speed();
        if (speed <= 0f) return true;
        float movingSeconds = Math.min(dt, speed / SKID_DECELERATION);
        float nextSpeed = Math.max(0f, speed - SKID_DECELERATION * dt);
        float distance = (speed + nextSpeed) * .5f * movingSeconds;
        body.x += body.vx / speed * distance;
        body.y += body.vy / speed * distance;
        body.ax = -body.vx / speed * SKID_DECELERATION;
        body.ay = -body.vy / speed * SKID_DECELERATION;
        body.vx *= nextSpeed / speed;
        body.vy *= nextSpeed / speed;
        tumble(body, movingSeconds, 3f);
        return nextSpeed == 0f;
    }

    private static void tumble(AirBody body, float dt, float damping) {
        float decay = (float) Math.exp(-damping * dt);
        body.facingDegrees += body.angVelDegPerSec * (1f - decay) / damping;
        body.angVelDegPerSec *= decay;
    }
}
