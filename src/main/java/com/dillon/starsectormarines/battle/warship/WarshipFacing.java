package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * Which way a ship should point to bring its guns to bear on a piece of ground.
 *
 * <p>A turret traverses within its slot's arc and a hardpoint fires where the
 * nose points, so which guns reach a target is a fact about the ship's heading.
 * This asks the question for every heading and answers with the one that puts the
 * most firepower on the target, breaking ties toward the smaller turn: a capital
 * turns slowly, so a heading that is nearly as good and much nearer is the better
 * order.
 */
public final class WarshipFacing {

    /** Candidate headings are tried this many degrees apart. */
    private static final float STEP_DEGREES = 5f;
    /** A heading must beat the nearest one by this much firepower to be worth a longer turn. */
    private static final float WORTH_TURNING_FOR = 0.02f;

    private WarshipFacing() {}

    /** The heading, in degrees, that brings the most firepower onto {@code (tx, ty)}; the ship's own if nothing bears at any. */
    public static float bestHeading(Warship ship, float tx, float ty) {
        float best = ship.facingDegrees;
        float bestPower = firepowerAt(ship, ship.facingDegrees, tx, ty);
        float bestTurn = 0f;
        for (float h = -180f; h < 180f; h += STEP_DEGREES) {
            float power = firepowerAt(ship, h, tx, ty);
            float turn = Math.abs(wrap(h - ship.facingDegrees));
            boolean clearlyBetter = power > bestPower * (1f + WORTH_TURNING_FOR);
            boolean asGoodAndNearer = power >= bestPower * (1f - WORTH_TURNING_FOR) && turn < bestTurn;
            if (clearlyBetter || asGoodAndNearer) {
                best = h;
                bestPower = Math.max(power, bestPower);
                bestTurn = turn;
            }
        }
        return best;
    }

    /** Damage per second, roughly, of every mount that could reach {@code (tx, ty)} with the hull at {@code heading}. */
    static float firepowerAt(Warship ship, float heading, float tx, float ty) {
        float total = 0f;
        for (Warship.Mount m : ship.mounts()) {
            float mx = Warship.mountX(ship.x, m.slot, heading);
            float my = Warship.mountY(ship.y, m.slot, heading);
            if (Warship.bears(m, heading, mx, my, tx, ty)) total += firepower(m.weapon);
        }
        return total;
    }

    private static float firepower(VanillaWeapon w) {
        float cycle = w.cooldownSeconds() + w.burstSize() * w.burstDelaySeconds() + 0.05f;
        return w.damagePerShot() * w.burstSize() / cycle;
    }

    static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** The heading from a point to another, in the hull-facing convention. */
    static float bearing(float fromX, float fromY, float toX, float toY) {
        return AirBody.facingToward(toX - fromX, toY - fromY);
    }
}
