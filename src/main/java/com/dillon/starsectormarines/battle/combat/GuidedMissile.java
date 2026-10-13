package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.WeaponDef.Guidance;

/** A finite-fuel, turn-limited air-target missile. Position is simulation-owned. */
public final class GuidedMissile {
    public float x, y, z, previousX, previousY, previousZ;
    public float heading, pitch, speed, remaining;
    public final float launchBearingDegrees;
    private final float maxSpeed;
    private final Guidance guidance;

    public GuidedMissile(float x, float y, float facingDegrees, float maxSpeed, Guidance guidance) {
        this.x = previousX = x;
        this.y = previousY = y;
        launchBearingDegrees = facingDegrees;
        heading = (float) Math.toRadians(facingDegrees + 90f);
        pitch = (float) Math.toRadians(25f);
        this.maxSpeed = maxSpeed;
        this.guidance = guidance;
        remaining = guidance.lifetime();
        speed = Math.min(4f, maxSpeed);
    }

    /** A lost target leaves the round flying its last bearing until fuel exhaustion. */
    public void advance(float dt, boolean tracking, float tx, float ty, float tz) {
        previousX = x; previousY = y; previousZ = z;
        if (tracking) {
            float dx = tx - x, dy = ty - y;
            float turn = (float) Math.toRadians(guidance.turnRate()) * dt;
            heading = toward(heading, (float) Math.atan2(dy, dx), turn);
            pitch = toward(pitch, (float) Math.atan2(tz - z, Math.hypot(dx, dy)), turn);
        }
        float nextSpeed = Math.min(maxSpeed, speed + guidance.acceleration() * dt);
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

    public float visualBearingDegrees() {
        if (x == previousX && y == previousY && z == previousZ) return launchBearingDegrees;
        return (float) Math.toDegrees(Math.atan2(y + z - previousY - previousZ, x - previousX)) - 90f;
    }

    private static float toward(float from, float to, float limit) {
        float delta = (float) Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + Math.max(-limit, Math.min(limit, delta));
    }
}
