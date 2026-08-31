package com.dillon.starsectormarines.battle.air;

/**
 * A burnt airframe lying exactly where it stopped: a position, a bearing, and
 * which hull it is.
 *
 * <p>The berth's own pad wreck is the special case of this whose position
 * never moves. {@link AirfieldService.Berth#wreckOnPad} keeps its own boolean
 * rather than holding one of these, because a parked airframe never leaves its
 * stand before it burns — the berth's {@code centerX}/{@code centerY}/
 * {@code facingDegrees} already say where the hull is, and giving it a second
 * position to agree with would be a second thing to keep in step. A craft
 * killed under its own power on a taxiway, holding short, or partway down a
 * takeoff roll or a landing rollout has no berth under it — it stopped
 * wherever the fire caught it — so its wreck has to carry that position
 * itself. See {@link AirfieldService#addGroundWreck}.
 *
 * <p>Immutable and kept for the rest of the battle: once a hull comes down it
 * does not move again, so nothing here is ever updated in place.
 */
public final class GroundWreck {

    /** Where the hull lies, cells. */
    public final float x;
    public final float y;

    /** Which way the hull is pointed. */
    public final float facingDegrees;

    /** Which hull this is — its sprite, its size, and the tear pattern it comes apart along. */
    public final Airframe airframe;

    public GroundWreck(float x, float y, float facingDegrees, Airframe airframe) {
        this.x = x;
        this.y = y;
        this.facingDegrees = facingDegrees;
        this.airframe = airframe;
    }

    /** Floor of {@link #x} — the logical cell for grid mutations, matching {@code DeathEvent}'s convention. */
    public int cellX() {
        return (int) Math.floor(x);
    }

    /** Floor of {@link #y} — the logical cell for grid mutations, matching {@code DeathEvent}'s convention. */
    public int cellY() {
        return (int) Math.floor(y);
    }
}
