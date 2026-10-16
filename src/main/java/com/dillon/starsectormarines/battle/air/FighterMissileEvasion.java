package com.dillon.starsectormarines.battle.air;

/** A bounded reaction and break over the fighter's ordinary steering; never changes collision or durability. */
public final class FighterMissileEvasion {
    public record Threat(float x, float y, float vx, float vy) {}
    public record Goal(float x, float y) {}
    public float reactionLeft, breakLeft, recoveryLeft;
    public int breaks, steeringTicks;
    private boolean warned;
    private float awayX, awayY, sideX, sideY, forwardX, forwardY;
    public static final float BREAK_SECONDS = .85f;
    public static final float RECOVERY_SECONDS = .55f;

    /** Only an actually closing missile inside a short warning horizon warrants a break. */
    public static boolean incoming(float x, float y, float vx, float vy, Threat threat) {
        float dx = threat.x - x, dy = threat.y - y;
        float rvx = threat.vx - vx, rvy = threat.vy - vy;
        float closing = -(dx * rvx + dy * rvy);
        float relativeSpeedSq = rvx * rvx + rvy * rvy;
        if (closing <= 0f || relativeSpeedSq < .0001f) return false;
        float closestTime = closing / relativeSpeedSq;
        float missX = dx + rvx * closestTime, missY = dy + rvy * closestTime;
        return closestTime <= 2.2f && missX * missX + missY * missY <= 12f * 12f;
    }

    /** Returns a steering goal only while the bounded maneuver owns this tick; caller moves the body once. */
    public Goal advance(float dt, long identity, AirBody body, AirHandling handling, Threat threat) {
        if (recoveryLeft > 0f) { recoveryLeft = Math.max(0f, recoveryLeft - dt); return null; }
        if (breakLeft <= 0f) {
            if (threat == null) { warned = false; reactionLeft = 0; return null; }
            if (!warned) {
                warned = true;
                reactionLeft = .18f + Math.floorMod(identity, 5) * .035f;
            }
            reactionLeft = Math.max(0f, reactionLeft - dt);
            if (reactionLeft > .0001f) return null;
            float length = (float) Math.hypot(body.x - threat.x, body.y - threat.y);
            awayX = length > .001f ? (body.x - threat.x) / length : 1f;
            awayY = length > .001f ? (body.y - threat.y) / length : 0f;
            float heading = (float) Math.toRadians(body.facingDegrees + 90f);
            forwardX = (float) Math.cos(heading); forwardY = (float) Math.sin(heading);
            float sign = ((identity + breaks) & 1L) == 0L ? 1f : -1f;
            sideX = -forwardY * sign;
            sideY = forwardX * sign;
            breakLeft = BREAK_SECONDS;
            breaks++;
        }
        // One break, then an opposite lateral correction: a weave with no change to the hull's turn limit.
        float weave = breakLeft > BREAK_SECONDS * .45f ? 1f : -.65f;
        float dx = forwardX * 1.5f + awayX * .25f + sideX * .7f * weave;
        float dy = forwardY * 1.5f + awayY * .25f + sideY * .7f * weave;
        float length = Math.max(.001f, (float) Math.hypot(dx, dy));
        float ahead = Math.max(20f, handling.minTurnRadiusCells() * 2f);
        breakLeft = Math.max(0f, breakLeft - dt);
        steeringTicks++;
        if (breakLeft == 0f) { recoveryLeft = RECOVERY_SECONDS; warned = false; }
        return new Goal(body.x + dx / length * ahead, body.y + dy / length * ahead);
    }

    public void reset() { reactionLeft = breakLeft = recoveryLeft = 0; warned = false; }
}
