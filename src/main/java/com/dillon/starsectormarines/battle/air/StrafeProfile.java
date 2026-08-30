package com.dillon.starsectormarines.battle.air;

/**
 * How an aircraft's own guns behave on a run.
 *
 * <p>The aircraft <em>is</em> the weapon: there is no turret, no traverse and
 * no independent target. Rounds go out forward of the nose and land on the
 * ground ahead, and where each one lands is a roll — which is the whole point.
 * A burst walks a scattered line of impacts across the ground rather than
 * resolving against a chosen victim, so massed infantry catches several and is
 * in real trouble while spread infantry is mostly missed, and neither outcome
 * was decided by a to-hit number.
 *
 * <p>Each round applies its damage as an ordinary detonation, so a strafe uses
 * the same splash, wall damage, line-of-sight gating and roof interception as
 * every other explosion in the battle. An aircraft's rounds are flagged as an
 * aerial delivery, so a squad under an intact roof is safe from them.
 *
 * <p>Presets rather than per-hull numbers, the way {@code ShuttleType.Profiles}
 * does it: the difference between a gun fighter and a bomber is a kind of
 * weapon, not five independent dials, and naming the kind keeps the hull list
 * readable.
 */
public final class StrafeProfile {

    /** Rounds put down per second while the guns are firing. */
    public final float roundsPerSecond;
    /**
     * How far ahead of the nose rounds land, in cells. Rounds arrive in front
     * of the aircraft rather than under it, so a run walks its fire onto a
     * target the craft is still approaching.
     */
    public final float leadCells;
    /** Standard scatter of an impact from where it was aimed, in cells. */
    public final float scatterCells;
    /** Splash radius of one round. */
    public final float aoeRadiusCells;
    /** Splash damage of one round at the impact. */
    public final float damage;
    /** Armour penetration of one round. */
    public final float penetration;
    /** Structure one round takes off a wall it lands on. */
    public final int wallDamage;

    public StrafeProfile(float roundsPerSecond, float leadCells, float scatterCells,
                         float aoeRadiusCells, float damage, float penetration,
                         int wallDamage) {
        this.roundsPerSecond = roundsPerSecond;
        this.leadCells = leadCells;
        this.scatterCells = scatterCells;
        this.aoeRadiusCells = aoeRadiusCells;
        this.damage = damage;
        this.penetration = penetration;
        this.wallDamage = wallDamage;
    }

    /** Seconds between rounds. */
    public float fireInterval() {
        return 1f / Math.max(0.01f, roundsPerSecond);
    }

    /**
     * A heavy rotary cannon: high volume, wide scatter, a real crater per
     * round. What a gun fighter walks across a position — the fire arrives as
     * a spray of dirt rather than as aimed shots, and a squad caught under it
     * loses people.
     */
    public static final StrafeProfile CANNON =
            new StrafeProfile(14f, 4f, 2.2f, 1.3f, 24f, 8f, 22);

    /**
     * A beam held on the ground and dragged: the same energy delivered as a
     * dense, tight line rather than as scattered craters. Less per hit than the
     * cannon and far more of them in a straight row, so it paints a line across
     * whatever it is dragged over.
     */
    public static final StrafeProfile LASER =
            new StrafeProfile(24f, 4f, 0.7f, 1.0f, 13f, 13f, 14);

    /** A torpedo bomber: a handful of very heavy drops, and a miss is a miss. */
    public static final StrafeProfile TORPEDO =
            new StrafeProfile(1.2f, 5f, 2.6f, 3.6f, 80f, 16f, 160);
}
