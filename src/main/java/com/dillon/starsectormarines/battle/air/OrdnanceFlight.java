package com.dillon.starsectormarines.battle.air;

/**
 * How a round gets from the aircraft to the ground.
 *
 * <p>Where a round lands is a consequence of how it travels rather than a
 * hand-set offset. An aircraft is at a height, and every round leaves it with
 * some velocity of its own plus whatever it keeps of the aircraft's; the ground
 * is where it arrives. That single sum is what makes a strafing shell, a
 * missile and a bomb land in three different places without three different
 * pieces of code — and it is why a bomb ends up <em>behind</em> the aircraft
 * that dropped it while a shell is still far in front of one.
 *
 * <p>Two regimes, distinguished by whether the round has anything pushing it:
 *
 * <ul>
 *   <li><b>Powered</b> ({@link #muzzleSpeedCells} above zero) — a gun round, a
 *       beam or a missile. It leaves along the nose, depressed below the
 *       horizontal by {@link #depressionDeg}, and flies that straight line at
 *       its own speed. Gravity is not modelled because over the fraction of a
 *       second a 700-cell/sec shell is airborne it would move the impact by a
 *       few thousandths of a cell. The reach is therefore pure sight geometry —
 *       {@code altitude / tan(depression)} — which is exactly what makes a gun
 *       a laser pointer: it hits where the nose is pointed at the ground, and
 *       the aircraft's own motion is irrelevant because there is no time for it
 *       to matter.</li>
 *   <li><b>Dropped</b> ({@code muzzleSpeedCells == 0}) — a bomb. Nothing pushes
 *       it, so it falls, and everything it has going forward it took from the
 *       aircraft. The fall is long enough that the aircraft's motion is the
 *       whole story.</li>
 * </ul>
 *
 * <p>{@link #momentumFraction} is how much of the aircraft's velocity the round
 * keeps. A shell inherits all of it and does not care; a missile's own motor
 * swamps it; a bomb keeps most but not all, because it is a draggy object
 * falling through air the aircraft is cutting through. That last shortfall is
 * the whole reason bombs trail: the aircraft covers {@code speed × fall} while
 * the bomb covers only {@code fraction × speed × fall}, so by the time it lands
 * the machine that dropped it is already past it.
 */
public final class OrdnanceFlight {

    /**
     * Downward acceleration on an unpowered round, cells/sec².
     *
     * <p>A cell is a metre at the current density
     * ({@link AirScale#METERS_PER_CELL}), so this is simply gravity.
     */
    public static final float GRAVITY_CELLS_PER_SEC2 = 9.8f;

    /** Height above the ground this weapon is delivered from, in cells. */
    public final float releaseAltitudeCells;

    /**
     * Speed the round leaves the aircraft at under its own power, cells/sec.
     * {@code 0} for a round that is merely let go and falls.
     */
    public final float muzzleSpeedCells;

    /**
     * Angle below the horizontal the round is launched at. Only meaningful for
     * a powered round: it is what turns a height into a reach.
     */
    public final float depressionDeg;

    /** Share of the aircraft's velocity the round carries away with it. */
    public final float momentumFraction;

    /** One round's arrival: where it lands and how long it was in the air. */
    public record Impact(float x, float y, float flightTimeSec) {}

    private OrdnanceFlight(float releaseAltitudeCells, float muzzleSpeedCells,
                           float depressionDeg, float momentumFraction) {
        this.releaseAltitudeCells = releaseAltitudeCells;
        this.muzzleSpeedCells = muzzleSpeedCells;
        this.depressionDeg = depressionDeg;
        this.momentumFraction = momentumFraction;
    }

    /**
     * A round launched along a depressed sight line at its own speed: a gun, a
     * beam, or a missile under power.
     */
    public static OrdnanceFlight powered(float releaseAltitudeCells, float depressionDeg,
                                         float muzzleSpeedCells, float momentumFraction) {
        if (muzzleSpeedCells <= 0f) {
            throw new IllegalArgumentException("a powered round needs a muzzle speed");
        }
        if (depressionDeg <= 0f || depressionDeg >= 90f) {
            throw new IllegalArgumentException(
                    "a sight line must point down and not straight down: " + depressionDeg);
        }
        return new OrdnanceFlight(releaseAltitudeCells, muzzleSpeedCells,
                depressionDeg, momentumFraction);
    }

    /** A round that is released and falls: a bomb. */
    public static OrdnanceFlight dropped(float releaseAltitudeCells, float momentumFraction) {
        return new OrdnanceFlight(releaseAltitudeCells, 0f, 0f, momentumFraction);
    }

    /** Whether anything is pushing this round once it leaves. */
    public boolean isPowered() {
        return muzzleSpeedCells > 0f;
    }

    /** How long one round is in the air, in seconds. */
    public float flightTimeSec() {
        if (isPowered()) {
            float descent = muzzleSpeedCells * (float) Math.sin(Math.toRadians(depressionDeg));
            return releaseAltitudeCells / descent;
        }
        return (float) Math.sqrt(2f * releaseAltitudeCells / GRAVITY_CELLS_PER_SEC2);
    }

    /** Ground speed the round's own motor contributes along the nose, cells/sec. */
    private float poweredGroundSpeed() {
        return isPowered()
                ? muzzleSpeedCells * (float) Math.cos(Math.toRadians(depressionDeg))
                : 0f;
    }

    /**
     * Where one round released here, now, arrives.
     *
     * @param x            release point
     * @param y            release point
     * @param facingDegrees the aircraft's compass facing; the nose is
     *                     {@code facingDegrees + 90} in the map's frame
     * @param craftVx      the aircraft's velocity, cells/sec
     * @param craftVy      the aircraft's velocity, cells/sec
     */
    public Impact deliver(float x, float y, float facingDegrees, float craftVx, float craftVy) {
        double nose = Math.toRadians(facingDegrees + 90f);
        float alongNose = poweredGroundSpeed();
        float vx = (float) Math.cos(nose) * alongNose + momentumFraction * craftVx;
        float vy = (float) Math.sin(nose) * alongNose + momentumFraction * craftVy;
        float flight = flightTimeSec();
        return new Impact(x + vx * flight, y + vy * flight, flight);
    }

    /**
     * Ground distance from the release point to the impact for an aircraft
     * flying straight ahead at {@code craftSpeed}. The derived quantity the old
     * hand-set lead used to be, kept as a read rather than as a dial.
     */
    public float leadCells(float craftSpeed) {
        return (poweredGroundSpeed() + momentumFraction * craftSpeed) * flightTimeSec();
    }

    /**
     * How far <em>behind</em> the aircraft the round lands, for one flying
     * straight ahead at {@code craftSpeed}. Negative means it is still out in
     * front when it arrives — every powered round is; a bomb is not.
     */
    public float trailCells(float craftSpeed) {
        return craftSpeed * flightTimeSec() - leadCells(craftSpeed);
    }
}
