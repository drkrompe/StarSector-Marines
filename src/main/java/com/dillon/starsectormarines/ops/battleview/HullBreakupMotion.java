package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirCrashImpact;
import com.dillon.starsectormarines.battle.air.AirCrashMotion;

/** Analytical appearance motion; no accumulated renderer state or gameplay movement. */
public final class HullBreakupMotion {
    public record Pose(float x, float y, float spinDegrees) {}
    private HullBreakupMotion() {}

    public static Pose pose(AirCrashImpact impact, HullBreakup.Piece piece, float age, float hullLength) {
        float t = Math.max(0, age);
        if (t == 0) return new Pose(0, 0, 0);
        float speed = (float) Math.hypot(impact.vx(), impact.vy());
        float deceleration = AirCrashMotion.SKID_DECELERATION;
        float travelTime = Math.min(t, speed / deceleration);
        float distance = speed * travelTime - .5f * deceleration * travelTime * travelTime;
        float forwardX = speed > 0 ? impact.vx() / speed * distance : 0;
        float forwardY = speed > 0 ? impact.vy() / speed * distance : 0;
        // Initial scatter velocity decays to zero exactly at this finite duration.
        float variation = Math.min(1f, Math.abs(piece.spinDegrees()) / 8f);
        float duration = 1.1f + piece.centreAlong() * .8f + variation * .5f
                + Math.min(1.2f, speed * .025f);
        float progress = ease(t / duration);
        float spinProgress = ease(t / (duration * (.7f + variation * .7f)));
        float localX = piece.slideAcross() * hullLength * 4f * progress;
        float localY = -piece.slideAlong() * hullLength * 4f * progress;
        double rad = Math.toRadians(impact.facingDegrees());
        float x = forwardX + (float) (localX * Math.cos(rad) - localY * Math.sin(rad));
        float y = forwardY + (float) (localX * Math.sin(rad) + localY * Math.cos(rad));
        float spin = (piece.spinDegrees() * 5f + impact.angularVelocityDegrees() * .18f) * spinProgress;
        return new Pose(x, y, spin);
    }
    private static float ease(float time) {
        float remaining = 1f - Math.min(1f, Math.max(0f, time));
        return 1f - remaining * remaining * remaining;
    }
}
