package com.dillon.starsectormarines.battle.warship;

/**
 * The ship-versus-ship rules, as pure functions: which weapons may fire at a hull, where a round thrown off by its
 * cone goes, where a round that missed comes down, and which heading brings a ship's anti-ship guns to bear.
 *
 * <p>The base game decides who shoots at ships by the weapon, as it does for fighters
 * ({@link WarshipAirRules}): point-defence weapons engage missiles and fighters and never a ship; every other gun
 * may. A lance keeps its ground sweep. Missiles wait for a missile path that flies at a hull (story slice 3), so a
 * missile mount does not engage a hull yet.
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

    /** Whether a weapon may fire at a ship: anything but pure point defence, a missile (for now) or a lance. */
    public static boolean mayFireAtHull(VanillaWeapon w) {
        if (w == null || w.pointDefenceOnly() || w.missile()) return false;
        return !(w.beam() && w.damagePerSecond() >= WarshipSystem.LANCE_MIN_DPS);
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

    /** Roughly the damage per second a weapon puts into a hull: a beam's rating, or a gun's bursts over its cycle. */
    static float firepower(VanillaWeapon w) {
        if (w.beam()) return w.damagePerSecond();
        float cycle = w.cooldownSeconds() + Math.max(1, w.burstSize()) * w.burstDelaySeconds() + 0.05f;
        return w.damagePerShot() * Math.max(1, w.burstSize()) / cycle;
    }

    /**
     * The heading that brings the most anti-ship firepower to bear on {@code (tx, ty)}, ignoring reach so a ship
     * still closing turns first: every mount that may fire at a hull counts its firepower when the target is inside
     * its slot's arc at that heading. Ties go to the smaller turn; with nothing able to bear at any heading the ship
     * points its nose at the target.
     */
    public static float bestHeading(Warship ship, float tx, float ty) {
        float best = ship.facingDegrees;
        float bestPower = firepowerAt(ship, ship.facingDegrees, tx, ty);
        float bestTurn = 0f;
        for (float h = -180f; h < 180f; h += HEADING_STEP_DEGREES) {
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
            if (!mayFireAtHull(m.weapon)) continue;
            float mx = Warship.mountX(ship.x, m.slot, heading);
            float my = Warship.mountY(ship.y, m.slot, heading);
            if (Warship.bears(m, heading, mx, my, tx, ty, UNBOUNDED_REACH)) total += firepower(m.weapon);
        }
        return total;
    }

    /** A reach multiplier large enough that only the arc decides whether a mount bears, for choosing a heading. */
    private static final float UNBOUNDED_REACH = 1e6f;
}
