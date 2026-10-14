package com.dillon.starsectormarines.battle.air;

/**
 * A fighter's nose guns against another aircraft: the geometry of the chase and the odds of a round.
 *
 * <p>The guns are bolted to the nose, so aiming them is flying the aircraft, as it is for a gun run on the ground
 * ({@code air-nouns.md}). The pursuer steers at where the prey will be ({@link #leadPoint}); the guns fire only while the
 * prey stands inside a short window ahead of the nose ({@link #inWindow}); and whether a round hits is a roll that
 * falls with range and with how far the prey is off the nose ({@link #hitChance}). Nothing here is a to-hit number for
 * the whole sortie: a pass that is flown badly puts few rounds on the target and a pass flown well puts many.
 *
 * <p>Rounds are real hits: they arrive after a short flight and land through the shared armour and structure law
 * ({@code BattleSimulation.applyAimedDamage}), so an aircraft shot down by a fighter dies the one way an aircraft dies.
 * That is the door a warship's guns and the ground anti-air already use; this is the third customer of it, not a
 * second damage model.
 *
 * <p>UNTUNED: the figures are first-pass. The cadence is set against what a pass can hold the prey under the nose for:
 * a fast crossing is a second or two of window, and the magazine and damage are sized so that two such passes down a
 * light strike aircraft and a heavy fighter survives them.
 */
public final class AirToAirGun {

    /** Furthest the guns reach, in cells. UNTUNED. */
    public static final float RANGE_CELLS = 16f;
    /** Half-angle of the window ahead of the nose inside which the guns fire, in degrees. UNTUNED. */
    public static final float CONE_DEGREES = 14f;
    /** Rounds per second while firing. UNTUNED. */
    public static final float ROUNDS_PER_SECOND = 12f;
    /** Damage of one round. UNTUNED. */
    public static final float DAMAGE = 7f;
    /** Armour penetration of one round: enough to bite an airframe's skin. UNTUNED. */
    public static final float PENETRATION = 8f;
    /** Seconds from the muzzle to the target. */
    public static final float FLIGHT_SECONDS = 0.1f;
    /** Chance of a hit point-blank and dead ahead. UNTUNED. */
    public static final float BEST_HIT_CHANCE = 0.6f;
    /** Share of {@link #BEST_HIT_CHANCE} left at the edge of the range. */
    private static final float RANGE_FLOOR = 0.35f;
    /** Share of {@link #BEST_HIT_CHANCE} left at the edge of the cone. */
    private static final float CONE_FLOOR = 0.5f;
    /** Share of the range inside which range costs nothing. */
    private static final float POINT_BLANK_SHARE = 0.4f;
    /** Longest the pursuer leads the prey, in seconds. */
    private static final float MAX_LEAD_SECONDS = 2.5f;

    private AirToAirGun() {}

    /**
     * The point to steer at to come onto the prey: where it will be after the time it takes to cover the gap.
     *
     * @return {@code {x, y}}
     */
    public static float[] leadPoint(float shooterX, float shooterY, float shooterSpeed,
                                    float preyX, float preyY, float preyVx, float preyVy) {
        float dist = (float) Math.hypot(preyX - shooterX, preyY - shooterY);
        float seconds = Math.min(MAX_LEAD_SECONDS, dist / Math.max(1f, shooterSpeed));
        return new float[]{preyX + preyVx * seconds, preyY + preyVy * seconds};
    }

    /** How far the prey lies off the nose, in degrees from 0 (dead ahead) to 180. */
    public static float offNoseDegrees(AirBody shooter, float targetX, float targetY) {
        float dx = targetX - shooter.x;
        float dy = targetY - shooter.y;
        if (dx * dx + dy * dy < 1e-8f) return 0f;
        float toTarget = (float) Math.toDegrees(Math.atan2(dy, dx));
        float nose = shooter.facingDegrees + 90f;
        return Math.abs(((toTarget - nose + 540f) % 360f) - 180f);
    }

    /** Whether a target at this range and bearing off the nose is inside the window the guns fire in. */
    public static boolean inWindow(float range, float offNoseDegrees) {
        return range <= RANGE_CELLS && offNoseDegrees <= CONE_DEGREES;
    }

    /** The chance one round hits a target at this range and bearing off the nose; zero outside the window. */
    public static float hitChance(float range, float offNoseDegrees) {
        if (!inWindow(range, offNoseDegrees)) return 0f;
        float pointBlank = RANGE_CELLS * POINT_BLANK_SHARE;
        float rangeShare = range <= pointBlank ? 1f
                : 1f - (1f - RANGE_FLOOR) * (range - pointBlank) / (RANGE_CELLS - pointBlank);
        float coneShare = 1f - (1f - CONE_FLOOR) * (offNoseDegrees / CONE_DEGREES);
        return BEST_HIT_CHANCE * rangeShare * coneShare;
    }

    /** Seconds between rounds. */
    public static float fireInterval() {
        return 1f / ROUNDS_PER_SECOND;
    }
}
