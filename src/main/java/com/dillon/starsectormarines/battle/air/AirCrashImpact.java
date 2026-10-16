package com.dillon.starsectormarines.battle.air;

/** Immutable first ground-contact pose, shared by a sliding craft and its settled wreck. */
public record AirCrashImpact(float x, float y, float facingDegrees, float vx, float vy,
                             float angularVelocityDegrees, float atSeconds, long seed) {
    public float age(float nowSeconds) { return Math.max(0f, nowSeconds - atSeconds); }

    /** Called after airborne integration and before skid; repeat calls preserve the original snapshot. */
    static AirCrashImpact captureContact(AirCrashImpact existing, AirBody body, long entityId,
                                         float before, float after, float contactSeconds) {
        if (existing != null || before >= AirCrashMotion.FALL_SECONDS || after < AirCrashMotion.FALL_SECONDS)
            return existing;
        long seed = entityId ^ ((long) Float.floatToIntBits(body.x) << 32)
                ^ Integer.toUnsignedLong(Float.floatToIntBits(body.y));
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        seed ^= seed >>> 31;
        return new AirCrashImpact(body.x, body.y, body.facingDegrees, body.vx, body.vy,
                body.angVelDegPerSec, contactSeconds, seed);
    }
}
