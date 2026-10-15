package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Set;

/** Atmospheric missile parameters in cells and seconds; vanilla reference uses 20 native units per cell. */
public record AirMissileSpec(boolean guided, boolean retargetCloser, float launchSpeed,
        float cruiseSpeed, float acceleration, float deceleration, float turnRateDegPerSec,
        float lifetime, float launchSpreadDeg, float fuseRadius, float blastRadius, float blastCoreRadius, boolean friendlyFire) {
    public AirMissileSpec {
        if (!positive(launchSpeed) || !positive(cruiseSpeed) || !positive(acceleration)
                || !positive(deceleration) || !positive(lifetime)
                || !nonnegative(turnRateDegPerSec) || guided && turnRateDegPerSec == 0f
                || !nonnegative(launchSpreadDeg) || launchSpreadDeg > 360f
                || !nonnegative(fuseRadius) || !nonnegative(blastRadius)
                || !nonnegative(blastCoreRadius) || blastCoreRadius > blastRadius
                || retargetCloser && !guided || (fuseRadius > 0f) != (blastRadius > 0f))
            throw new IllegalArgumentException("Invalid air missile flight or fuse parameters");
    }
    private static boolean positive(float v) { return Float.isFinite(v) && v > 0f; }
    private static boolean nonnegative(float v) { return Float.isFinite(v) && v >= 0f; }
    static AirMissileSpec parse(JSONObject j) throws JSONException {
        if (j == null) return null;
        var keys = j.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!Set.of("guided", "retargetCloser", "launchSpeed", "cruiseSpeed", "acceleration",
                    "deceleration", "turnRateDegPerSec", "lifetime", "launchSpreadDeg", "fuseRadius", "blastRadius",
                    "blastCoreRadius", "friendlyFire").contains(key))
                throw new JSONException("Unknown airMissile field: " + key);
        }
        try {
            return new AirMissileSpec(j.getBoolean("guided"), j.optBoolean("retargetCloser", false),
                    (float) j.getDouble("launchSpeed"), (float) j.getDouble("cruiseSpeed"),
                    (float) j.getDouble("acceleration"), (float) j.getDouble("deceleration"),
                    (float) j.optDouble("turnRateDegPerSec", 0), (float) j.getDouble("lifetime"),
                    (float) j.optDouble("launchSpreadDeg", 0), (float) j.optDouble("fuseRadius", 0), (float) j.optDouble("blastRadius", 0),
                    (float) j.optDouble("blastCoreRadius", 0), j.optBoolean("friendlyFire", false));
        } catch (IllegalArgumentException e) { throw new JSONException(e.getMessage()); }
    }
    /** Full launch cone; the mod interprets the native 20-degree spread as +/-10 degrees. */
    public float launchAngleOffset(float uniformSample) {
        return (uniformSample - .5f) * launchSpreadDeg;
    }
    /** Distance to a grounded body's vertical cylinder; a high burst cannot reach ground by horizontal radius alone. */
    public static float groundSurfaceDistance(float horizontalDistance, float altitude, float radius, float halfHeight) {
        float horizontal = Math.max(0f, horizontalDistance - radius);
        float vertical = Math.max(0f, altitude - 2f * halfHeight);
        return (float) Math.hypot(horizontal, vertical);
    }
    /** Linear blast falloff outside the full-damage core, measured from the closest hull surface. */
    public float damageFraction(float surfaceDistance) {
        if (surfaceDistance <= blastCoreRadius) return 1f;
        if (surfaceDistance >= blastRadius) return 0f;
        return (blastRadius - surfaceDistance) / (blastRadius - blastCoreRadius);
    }
}
