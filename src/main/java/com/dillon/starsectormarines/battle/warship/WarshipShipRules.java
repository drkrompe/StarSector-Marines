package com.dillon.starsectormarines.battle.warship;

import java.util.ArrayList;
import java.util.List;

/**
 * The ship-versus-ship rules, as pure functions: which weapons may fire at a hull, where a round thrown off by its
 * cone goes, where a round that missed comes down, and which heading brings a ship's anti-ship guns to bear.
 *
 * <p>The base game decides who shoots at ships by the weapon, as it does for fighters
 * ({@link WarshipAirRules}): point-defence weapons engage missiles and fighters and never a ship; every other gun
 * may. A lance keeps its ground sweep. A missile may, except a standoff missile, whose beam is a ground payload; but
 * a magazine is not a sustained gun, so the hull's heading and the duel helm's reach are read from its guns alone
 * ({@link #gunAtHull}), and a missile mount fires whenever its hull comes into its launch cone and reach.
 *
 * <p>Every random-looking quantity is a pure hash of who fired what ({@link WarshipAirRules#unit}), never a draw
 * from the shared random stream, so a duel cannot shift another gun's draws.
 */
public final class WarshipShipRules {

    private WarshipShipRules() {}

    /** A round that misses comes down at least this share of the reach it had left, and at most all of it. */
    static final float MIN_CARRY_SHARE = 0.25f;
    /** A round that misses comes down at least this many cells beyond where it passed the hull. */
    static final float MIN_CARRY_CELLS = 2f;
    /** Headings are searched in steps this wide when bringing guns to bear on a hull. */
    static final float HEADING_STEP_DEGREES = 5f;

    /** How far beyond its slot's arc a guided missile may be launched at a hull: it turns onto the hull after launch. */
    static final float GUIDED_LAUNCH_DEGREES = 20f;

    /** Whether a weapon may fire at a ship: anything but pure point defence, a lance or a standoff missile. */
    public static boolean mayFireAtHull(VanillaWeapon w) {
        if (w == null || w.pointDefenceOnly()) return false;
        if (w.missile()) return w.flight() == null || w.flight().standoff() == null;
        return !(w.beam() && w.damagePerSecond() >= WarshipSystem.LANCE_MIN_DPS);
    }

    /** Whether a weapon is a gun that may fire at a ship: what the hull's heading and the duel helm are read from. */
    public static boolean gunAtHull(VanillaWeapon w) {
        return mayFireAtHull(w) && !w.missile();
    }

    /** Whether a missile is guided onto its target after launch, so its slot's arc does not have to hold the target. */
    static boolean guided(VanillaWeapon w) {
        MissileFlightSpec flight = w.missile() ? w.flight() : null;
        return flight != null && flight.guidance() != MissileFlightSpec.Guidance.UNGUIDED;
    }

    /** The arc, in degrees either side of the slot's facing, inside which a mount may fire at a hull. */
    static float hullArcHalfDegrees(Warship.Mount m) {
        float half = Math.max(m.slot.arcDegrees(), 1f) * 0.5f;
        return guided(m.weapon) ? half + GUIDED_LAUNCH_DEGREES : half;
    }

    /**
     * The lateral miss, in cells, that a weapon's cone gives a round at {@code distance}: up to the half-cone's
     * tangent either side, by {@code unit} in {@code [0, 1)}. A weapon with no spread always flies true.
     */
    public static float coneMiss(VanillaWeapon w, float distance, float unit) {
        return distance * (float) Math.tan(Math.toRadians(w.spreadDegrees() * 0.5f)) * (unit * 2f - 1f);
    }

    /**
     * Where a round that missed comes down: on along its own line from the muzzle {@code (mx, my)} through where it
     * passed the hull {@code (px, py)}, by a share of the reach it had left ({@code unit} chooses the share between
     * {@link #MIN_CARRY_SHARE} and all of it), and never less than {@link #MIN_CARRY_CELLS}. A round fired in
     * atmosphere does not fly on for ever; how far it carries is the only thing about its fall the ground needs.
     *
     * @return {@code [x, y]} of the ground it lands on
     */
    public static float[] strayLanding(float mx, float my, float px, float py, float reach, float unit) {
        float dx = px - mx;
        float dy = py - my;
        float travelled = (float) Math.hypot(dx, dy);
        if (travelled < 1e-3f) return new float[]{px, py};
        float left = Math.max(0f, reach - travelled);
        float carry = Math.max(MIN_CARRY_CELLS, left * (MIN_CARRY_SHARE + (1f - MIN_CARRY_SHARE) * unit));
        return new float[]{px + dx / travelled * carry, py + dy / travelled * carry};
    }

    /** The ship's strongest gun that may fire at a hull, by firepower; {@code null} when it has none. */
    public static Warship.Mount strongest(Warship ship) {
        Warship.Mount best = null;
        float power = 0f;
        for (Warship.Mount m : ship.mounts()) {
            if (!gunAtHull(m.weapon) || m.knockedOut()) continue;
            float p = firepower(m.weapon);
            if (p > power) {
                power = p;
                best = m;
            }
        }
        return best;
    }

    /**
     * Where the ship's strongest anti-ship gun must point to hit {@code target}: the hull's centre led by its
     * velocity over that gun's flight time. A hardpoint's arc is a few degrees, so a hull that points at a moving
     * target's centre rather than its lead point never brings the hardpoint to bear.
     *
     * @return {@code [x, y]}
     */
    public static float[] aimPoint(Warship ship, Warship target) {
        Warship.Mount m = strongest(ship);
        if (m == null) return new float[]{target.centerX(), target.centerY()};
        return WarshipAirRules.leadPoint(ship.mountX(m), ship.mountY(m), target.centerX(), target.centerY(),
                target.velocityX, target.velocityY, m.weapon);
    }

    /** Roughly the damage per second a weapon puts into a hull: a beam's rating, or a gun's bursts over its cycle. */
    static float firepower(VanillaWeapon w) {
        if (w.beam()) return w.damagePerSecond();
        float cycle = w.cooldownSeconds() + Math.max(1, w.burstSize()) * w.burstDelaySeconds() + 0.05f;
        return w.damagePerShot() * Math.max(1, w.burstSize()) / cycle;
    }

    /**
     * The heading that brings the most anti-ship firepower to bear on {@code (tx, ty)}, ignoring reach so a ship
     * still closing turns first: every gun that may fire at a hull counts its firepower when the target is inside
     * its slot's arc at that heading. Ties go to the smaller turn; with nothing able to bear at any heading the ship
     * points its nose at the target.
     */
    public static float bestHeading(Warship ship, float tx, float ty) {
        float best = ship.facingDegrees;
        float bestPower = firepowerAt(ship, ship.facingDegrees, tx, ty);
        float bestTurn = 0f;
        // The coarse sweep, and then each anti-ship mount's own exact heading: a hardpoint's arc is narrower than the
        // sweep's step, so the heading that lays it on the target has to be tried by name.
        int coarse = (int) (360f / HEADING_STEP_DEGREES);
        List<Float> candidates = new ArrayList<>(coarse + ship.mounts().size());
        for (int i = 0; i < coarse; i++) candidates.add(-180f + i * HEADING_STEP_DEGREES);
        float toTarget = WarshipFacing.bearing(ship.x, ship.y, tx, ty);
        for (Warship.Mount m : ship.mounts()) {
            if (gunAtHull(m.weapon) && !m.knockedOut()) candidates.add(WarshipFacing.wrap(toTarget - m.slot.angleDegrees()));
        }
        for (float h : candidates) {
            float power = firepowerAt(ship, h, tx, ty);
            float turn = Math.abs(WarshipFacing.wrap(h - ship.facingDegrees));
            if (power > bestPower * 1.0001f || (power > 0f && power >= bestPower * 0.9999f && turn < bestTurn)) {
                best = h;
                bestPower = power;
                bestTurn = turn;
            }
        }
        if (bestPower <= 0f) return WarshipFacing.bearing(ship.x, ship.y, tx, ty);
        return best;
    }

    static float firepowerAt(Warship ship, float heading, float tx, float ty) {
        float total = 0f;
        for (Warship.Mount m : ship.mounts()) {
            // A gun lost with its plating brings nothing to bear, so the hull turns what it has left.
            if (!gunAtHull(m.weapon) || m.knockedOut()) continue;
            float mx = Warship.mountX(ship.x, m.slot, heading);
            float my = Warship.mountY(ship.y, m.slot, heading);
            if (Warship.bears(m, heading, mx, my, tx, ty, UNBOUNDED_REACH)) total += firepower(m.weapon);
        }
        return total;
    }

    /** A reach multiplier large enough that only the arc decides whether a mount bears, for choosing a heading. */
    private static final float UNBOUNDED_REACH = 1e6f;
}
