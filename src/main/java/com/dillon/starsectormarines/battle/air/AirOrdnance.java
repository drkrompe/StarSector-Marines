package com.dillon.starsectormarines.battle.air;

/**
 * What an aircraft puts on the ground during an attack run.
 *
 * <p>The aircraft <em>is</em> the weapon: there is no turret, no traverse and
 * no independent target. Ordnance leaves the nose forward and lands on the
 * ground ahead, and where each round lands is a roll — which is the mechanism
 * rather than a concession to it. A pass walks a scattered line of impacts
 * across a piece of ground, so being caught is a question of how much of you is
 * standing in it, and neither outcome was decided by a to-hit number.
 *
 * <p>Every round is delivered as an ordinary detonation, so splash, wall
 * damage, line of sight and roof interception all come from the pipeline that
 * already owns them. An aircraft's rounds are flagged as an aerial delivery, so
 * a squad under an intact roof is not hit.
 *
 * <p><b>Deliberately about delivery rather than about guns.</b> A rotary
 * cannon, a beam and a stick of bombs differ in how fast rounds leave, how many
 * there are, how tightly they land and how big a hole each one makes — not in
 * needing separate code. {@link #roundsPerPass} is what lets a bomber exist
 * beside a gun fighter: a gun runs for as long as it holds its target under the
 * nose, and a bomber releases what it loaded and is done, which is why one can
 * make three passes and the other cannot.
 *
 * <p>Presets rather than per-hull numbers, the way {@code ShuttleType.Profiles}
 * does it: the difference between a gun fighter and a bomber is a kind of
 * armament, not seven independent dials, and naming the kind keeps the hull
 * list readable.
 */
public final class AirOrdnance {

    /** Rounds released per second while the aircraft is firing. */
    public final float roundsPerSecond;

    /**
     * Rounds this aircraft has to give on one pass, or {@code 0} for a weapon
     * that fires as long as it has a target in the window.
     *
     * <p>The difference between a gun and a bomb bay. A gun fighter is limited
     * by how long it can hold its target under the nose; a bomber is limited by
     * what it loaded, and its pass ends when the last one is gone whether or
     * not the target is still there.
     */
    public final int roundsPerPass;

    /**
     * How far ahead of the nose rounds land, in cells. Ordnance arrives in
     * front of the aircraft rather than under it, so a run walks its fire onto
     * a target the craft is still approaching.
     */
    public final float leadCells;

    /** Standard scatter of an impact from where it was aimed, in cells. */
    public final float scatterCells;

    /** How near the target the aircraft has to be before it will release. */
    public final float firingRangeCells;

    /** Splash radius of one round. */
    public final float aoeRadiusCells;
    /** Splash damage of one round at the impact. */
    public final float damage;
    /** Armour penetration of one round. */
    public final float penetration;
    /** Structure one round takes off a wall it lands on. */
    public final int wallDamage;

    public AirOrdnance(float roundsPerSecond, int roundsPerPass, float leadCells,
                       float scatterCells, float firingRangeCells, float aoeRadiusCells,
                       float damage, float penetration, int wallDamage) {
        this.roundsPerSecond = roundsPerSecond;
        this.roundsPerPass = roundsPerPass;
        this.leadCells = leadCells;
        this.scatterCells = scatterCells;
        this.firingRangeCells = firingRangeCells;
        this.aoeRadiusCells = aoeRadiusCells;
        this.damage = damage;
        this.penetration = penetration;
        this.wallDamage = wallDamage;
    }

    /** Seconds between rounds. */
    public float fireInterval() {
        return 1f / Math.max(0.01f, roundsPerSecond);
    }

    /** Whether this weapon fires for as long as the target is in the window. */
    public boolean firesContinuously() {
        return roundsPerPass <= 0;
    }

    /**
     * A heavy rotary cannon: high volume, wide scatter, a real crater per
     * round, firing for as long as the target is under the nose. What a gun
     * fighter walks across a position — the fire arrives as a spray of dirt
     * rather than as aimed shots, and a squad caught under it loses people.
     */
    public static final AirOrdnance AUTOCANNON =
            new AirOrdnance(14f, 0, 4f, 2.2f, 22f, 1.3f, 24f, 8f, 22);

    /**
     * A beam held on the ground and dragged: the same energy delivered as a
     * dense, tight line rather than as scattered craters. Less per hit than the
     * cannon and far more of them in a straight row, so it paints a line across
     * whatever it is dragged over.
     */
    public static final AirOrdnance BEAM =
            new AirOrdnance(24f, 0, 4f, 0.7f, 22f, 1.0f, 13f, 13f, 14);

    /**
     * A stick of heavy bombs, released close in and gone once they are gone.
     * Enormous per round and few of them, so a bomber that rolls in on a
     * dispersed target has wasted the sortie and one that catches a formation
     * removes it.
     *
     * <p>The cadence is what makes it a stick rather than a trickle. A bomber
     * is only over its target for about a second, so a slow release simply
     * meant it went home with bombs still aboard — measured at three a second
     * it dropped three of five and kept the rest.
     */
    public static final AirOrdnance BOMBS =
            new AirOrdnance(6f, 5, 5f, 2.6f, 14f, 3.6f, 80f, 16f, 160);
}
