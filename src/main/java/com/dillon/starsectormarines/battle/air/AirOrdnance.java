package com.dillon.starsectormarines.battle.air;

/**
 * What an aircraft puts on the ground during an attack run.
 *
 * <p>The aircraft <em>is</em> the weapon: there is no turret, no traverse and
 * no independent target. Ordnance leaves the nose and lands on the ground
 * ahead, and where each round lands is a roll — which is the mechanism rather
 * than a concession to it. A pass walks a scattered line of impacts across a
 * piece of ground, so being caught is a question of how much of you is standing
 * in it, and neither outcome was decided by a to-hit number.
 *
 * <p>Every round is delivered as an ordinary detonation, so splash, wall
 * damage, line of sight and roof interception all come from the pipeline that
 * already owns them. An aircraft's rounds are flagged as an aerial delivery, so
 * a squad under an intact roof is not hit.
 *
 * <p><b>Deliberately about delivery rather than about guns.</b> A rotary
 * cannon, a beam, a missile pod and a stick of bombs differ in how fast rounds
 * leave, how many there are, how tightly they land, how big a hole each one
 * makes — and, above all, in {@link #flight}: how the round actually travels
 * from the aircraft to the ground. There is no lead dial. Reach is a
 * consequence of a height, a launch and a fall, which is what makes a shell
 * land far in front of the aircraft and a bomb land behind it without either
 * being told to.
 *
 * <p>{@link #roundsPerPass} is the other thing that lets a bomber exist beside
 * a gun fighter: a gun runs for as long as it holds its target under the nose,
 * and a bomber releases what it loaded and is done, which is why one can make
 * three passes and the other cannot.
 *
 * <p>Presets rather than per-hull numbers, the way {@code ShuttleType.Profiles}
 * does it: the difference between a gun fighter and a bomber is a kind of
 * armament, not a dozen independent dials, and naming the kind keeps the hull
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
     * How this weapon's rounds get to the ground. The single source of truth
     * for reach: nothing else says how far ahead a round lands.
     */
    public final OrdnanceFlight flight;

    /** Standard scatter of an impact from where it was delivered, in cells. */
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

    public AirOrdnance(float roundsPerSecond, int roundsPerPass, OrdnanceFlight flight,
                       float scatterCells, float firingRangeCells, float aoeRadiusCells,
                       float damage, float penetration, int wallDamage) {
        this.roundsPerSecond = roundsPerSecond;
        this.roundsPerPass = roundsPerPass;
        this.flight = flight;
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
     *
     * <p>A laser pointer. The shell is out of the barrel and on the ground
     * inside three hundredths of a second, so the aircraft does not move
     * meaningfully while it is in the air and the impact is simply where the
     * nose was pointed at the ground. That the reach is most of a dozen cells
     * is the sight geometry of a machine at height rather than a chosen offset:
     * fire that lands under the aircraft is fire from something hovering.
     */
    public static final AirOrdnance AUTOCANNON =
            new AirOrdnance(14f, 0,
                    OrdnanceFlight.powered(12f, 42f, 700f, 1.0f),
                    2.2f, 26f, 1.3f, 24f, 8f, 22);

    /**
     * A beam held on the ground and dragged: the same energy delivered as a
     * dense, tight line rather than as scattered craters. Less per hit than the
     * cannon and far more of them in a straight row, so it paints a line across
     * whatever it is dragged over.
     *
     * <p>The same sight geometry as the cannon — it is the other laser pointer,
     * and the only thing that separates them is what they do once they arrive.
     */
    public static final AirOrdnance BEAM =
            new AirOrdnance(24f, 0,
                    OrdnanceFlight.powered(12f, 42f, 2000f, 1.0f),
                    0.7f, 26f, 1.0f, 13f, 13f, 14);

    /**
     * A pod of light guided missiles: the strafe's cousin, further out.
     *
     * <p>It has its own motor, so unlike a bomb it does not simply fall with
     * whatever the aircraft gave it, and unlike a shell it is slow enough that
     * the flight is a real stretch of time — about a third of a second, during
     * which the aircraft closes a few cells of the gap. A shallower launch than
     * the guns buys the reach: a missile boat releases from well outside gun
     * range and turns away without ever coming over the position.
     *
     * <p>Finite, like a bomb bay and unlike a gun. Six is a pod rather than a
     * magazine, and it is what stops a missile fighter simply being a
     * longer-ranged cannon fighter.
     *
     * <p>A finite load makes the cadence part of the aim. A gun keeps firing
     * until the target passes off the nose, so its reach alone decides where
     * the burst ends; a pod is empty when it is empty, and if it empties too
     * early every round lands short however good the reach is. The release has
     * to last long enough for the aircraft to close the gap between its
     * standoff and its reach — at four a second this pod finished eight cells
     * short of the target and its whole stick fell in front of the position.
     */
    public static final AirOrdnance MISSILES =
            new AirOrdnance(3f, 6,
                    OrdnanceFlight.powered(12f, 34f, 65f, 0.35f),
                    1.4f, 34f, 2.2f, 46f, 14f, 70);

    /**
     * A stick of heavy bombs, released close in and gone once they are gone.
     * Enormous per round and few of them, so a bomber that rolls in on a
     * dispersed target has wasted the sortie and one that catches a formation
     * removes it.
     *
     * <p>Nothing pushes a bomb. It keeps most of the aircraft's speed and
     * spends a second and a half falling, and because it keeps <em>most</em>
     * rather than all of it the aircraft is past the impact by the time it
     * happens — which is what a bomb run looks like, and is not something the
     * model was told to do.
     *
     * <p>The cadence is what makes it a stick rather than a trickle. A bomber
     * is only over its target for about a second, so a slow release simply
     * meant it went home with bombs still aboard — measured at three a second
     * it dropped three of five and kept the rest.
     */
    public static final AirOrdnance BOMBS =
            new AirOrdnance(6f, 5,
                    OrdnanceFlight.dropped(10f, 0.72f),
                    2.6f, 20f, 3.6f, 80f, 16f, 160);
}
