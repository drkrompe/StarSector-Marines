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
     * Whether {@code ship}'s strongest anti-ship gun has {@code target} now: -1 when it has no such gun or the target is
     * beyond that gun's hull reach, 0 when in reach but outside the gun's arc at the hull's heading, 1 when it bears.
     * A reading for evidence and for the tactics layer, not a firing rule.
     */
    public static int strongestGunOn(Warship ship, Warship target) {
        Warship.Mount m = strongest(ship);
        if (m == null) return -1;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        float range = (float) Math.hypot(target.centerX() - mx, target.centerY() - my);
        if (range > WarshipScale.hullReach(m.weapon.rangePx())) return -1;
        return Warship.bears(m, ship.facingDegrees, mx, my, target.centerX(), target.centerY(), UNBOUNDED_REACH) ? 1 : 0;
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
     * its slot's arc at that heading. Among the headings that keep at least {@link #BROADSIDE_SHARE} of the best
     * firepower on it, the ship takes the one that lays the target most nearly abeam, less a little for a longer
     * turn: ships of the line, not fighters, so a hull whose battery can fight broadside does, and one whose guns are
     * all in its bow turns its nose in. With nothing able to bear at any heading the ship points its nose at the
     * target.
     */
    public static float bestHeading(Warship ship, float tx, float ty) {
        // The coarse sweep, the ship's own heading, and then each anti-ship mount's own exact heading: a hardpoint's
        // arc is narrower than the sweep's step, so the heading that lays it on the target has to be tried by name.
        int coarse = (int) (360f / HEADING_STEP_DEGREES);
        List<Float> candidates = new ArrayList<>(coarse + ship.mounts().size() + 1);
        candidates.add(ship.facingDegrees);
        for (int i = 0; i < coarse; i++) candidates.add(-180f + i * HEADING_STEP_DEGREES);
        float toTarget = WarshipFacing.bearing(ship.x, ship.y, tx, ty);
        for (Warship.Mount m : ship.mounts()) {
            if (!gunAtHull(m.weapon) || m.knockedOut()) continue;
            // Laid from the mount, not the hull's centre: a hardpoint set off the centreline misses by its offset.
            float h = WarshipFacing.wrap(toTarget - m.slot.angleDegrees());
            for (int k = 0; k < 2; k++) {
                float mx = Warship.mountX(ship.x, m.slot, h);
                float my = Warship.mountY(ship.y, m.slot, h);
                h = WarshipFacing.wrap(WarshipFacing.bearing(mx, my, tx, ty) - m.slot.angleDegrees());
            }
            candidates.add(h);
        }
        // A heading's firepower is what still bears a little either side of it, so a heavy hull that settles a degree
        // or two off its order, or a target that drifts, does not take a gun off the target: the broadside rule below
        // would otherwise pick the very edge of a hardpoint's arc.
        float[] power = new float[candidates.size()];
        float most = 0f;
        for (int i = 0; i < power.length; i++) {
            float h = candidates.get(i);
            power[i] = settledFirepowerAt(ship, h, tx, ty);
            most = Math.max(most, power[i]);
        }
        if (most <= 0f) {
            for (int i = 0; i < power.length; i++) {
                power[i] = firepowerAt(ship, candidates.get(i), tx, ty);
                most = Math.max(most, power[i]);
            }
        }
        if (most <= 0f) return toTarget;
        // Of the headings that keep nearly all of that firepower on the target, the one that lays the target most
        // nearly abeam: a broadside where the guns allow one, and the nose only where they do not. Between two
        // broadsides it prefers the side with more plate left, so a ship whose engaged side is stripped wears round
        // to fight on its fresh one, as a ship of the line does, rather than taking every round on the same plate.
        // A bow ship (WarshipDuelHelm.bowFighter) does not trade guns for a beam: of the headings that keep nearly
        // all of its battery on the target, it takes the one with the most, then the least turn.
        WarshipPlating plating = ship.plating();
        boolean bow = WarshipDuelHelm.bowFighter(ship);
        float band = bow ? BOW_SHARE : BROADSIDE_SHARE;
        float best = ship.facingDegrees;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < power.length; i++) {
            if (power[i] < most * band) continue;
            float h = candidates.get(i);
            float abeam = (float) Math.abs(Math.sin(Math.toRadians(WarshipFacing.wrap(toTarget - h))));
            float turn = Math.abs(WarshipFacing.wrap(h - ship.facingDegrees));
            float score = bow
                    ? power[i] / most - TURN_PENALTY * turn / 180f
                    : abeam - TURN_PENALTY * turn / 180f + POWER_PREFERENCE * power[i] / most
                    + FRESH_SIDE_PREFERENCE * abeam * plateFacing(plating, WarshipFacing.wrap(toTarget - h));
            if (score > bestScore) {
                best = h;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * How much the plate left on the side a heading turns toward the target is worth, scaled by how nearly abeam
     * it lays it. Against {@link #TURN_PENALTY} a half-circle wear is worth it once the engaged side has about half
     * its plate less than the other, and a ship that has worn round does not turn back.
     */
    static final float FRESH_SIDE_PREFERENCE = 0.3f;

    /**
     * The plate left on the side of the hull a target at {@code relative} degrees off the nose would strike: port
     * when it lies counter-clockwise of the nose. One for a hull without a grid, and for a target nearer the nose
     * or stern than the beam, so the preference only ever chooses between broadsides.
     */
    static float plateFacing(WarshipPlating plating, float relative) {
        if (plating == null) return 1f;
        float side = (float) Math.sin(Math.toRadians(relative));
        if (Math.abs(side) < 0.5f) return 1f;
        return plating.plateLeft(side > 0f ? WarshipPlating.Section.PORT : WarshipPlating.Section.STARBOARD);
    }

    /**
     * A heading that brings at least this share of the most anti-ship firepower to bear may be chosen for a
     * broadside. A ship of the line gives up some of its bow guns to lay its side on the target; a ship that cannot
     * keep this much abeam is a bow ship.
     */
    static final float BROADSIDE_SHARE = 0.6f;
    /** A bow ship keeps headings with at least this share of its most firepower on the target. */
    static final float BOW_SHARE = 0.85f;
    /** How much laying the target fully abeam is worth against a half-circle of turning. */
    static final float TURN_PENALTY = 0.15f;
    /**
     * How far either side of a heading its guns must still bear for the heading to count their firepower: this, or
     * a quarter of the mount's own arc where that is narrower, so a hardpoint laid on the target still counts.
     */
    static final float SETTLE_DEGREES = 2f;
    /** How much the last of the firepower inside the broadside band is worth against laying the target abeam. */
    static final float POWER_PREFERENCE = 0.5f;

    /**
     * {@link #firepowerAt}, counting a gun only if it also bears its settle either side of {@code heading}
     * ({@link #SETTLE_DEGREES}, or a quarter of its arc where that is narrower).
     */
    static float settledFirepowerAt(Warship ship, float heading, float tx, float ty) {
        float total = 0f;
        for (Warship.Mount m : ship.mounts()) {
            if (!gunAtHull(m.weapon) || m.knockedOut()) continue;
            float settle = Math.min(SETTLE_DEGREES, 0.25f * m.slot.arcDegrees());
            if (bearsAt(ship, m, heading, tx, ty) && bearsAt(ship, m, heading - settle, tx, ty)
                    && bearsAt(ship, m, heading + settle, tx, ty)) {
                total += firepower(m.weapon);
            }
        }
        return total;
    }

    private static boolean bearsAt(Warship ship, Warship.Mount m, float heading, float tx, float ty) {
        float mx = Warship.mountX(ship.x, m.slot, heading);
        float my = Warship.mountY(ship.y, m.slot, heading);
        return Warship.bears(m, heading, mx, my, tx, ty, UNBOUNDED_REACH);
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
