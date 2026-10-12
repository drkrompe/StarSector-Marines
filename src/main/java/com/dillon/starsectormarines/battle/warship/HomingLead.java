package com.dillon.starsectormarines.battle.warship;

/**
 * How far ahead of a moving unit a homing missile aims: a guided missile "leads the target", so a
 * running squad is still hit, while an unguided rocket and a poorly guided one go where the unit was.
 *
 * <p>The lead is the unit's velocity over the missile's expected flight time, scaled by how good the
 * seeker is (its turn rate) and capped, so a fast target cannot drag the aim point across the map.
 */
final class HomingLead {

    /** The turn rate, in degrees per second, at which a seeker leads perfectly. */
    private static final float FULL_LEAD_TURN_RATE = 240f;
    /** The most a seeker ever leads, as a share of the flight time it would need to. */
    private static final float MAX_LEAD_SECONDS = 6f;
    /** A missile spends part of its flight accelerating, so its mean speed is a share of its top speed. */
    private static final float MEAN_SPEED_SHARE = 0.75f;

    private HomingLead() {
    }

    /** Seconds of the target's motion to aim ahead by; zero for a missile that does not lead. */
    static float seconds(MissileFlightSpec flight, float distanceCells) {
        if (flight == null || flight.guidance() != MissileFlightSpec.Guidance.HOMING) return 0f;
        float speed = WarshipScale.cells(flight.maxSpeedPx()) * MEAN_SPEED_SHARE;
        if (speed <= 0f) return 0f;
        float skill = Math.min(1f, flight.turnRateDegPerSec() / FULL_LEAD_TURN_RATE);
        return Math.min(MAX_LEAD_SECONDS, distanceCells / speed) * skill;
    }

    /** The aim point moved along the target's velocity by the lead. */
    static float[] apply(float x, float y, float vx, float vy, float leadSeconds) {
        return new float[] {x + vx * leadSeconds, y + vy * leadSeconds};
    }
}
