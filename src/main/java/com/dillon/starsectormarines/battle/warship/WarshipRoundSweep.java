package com.dillon.starsectormarines.battle.warship;

import java.util.List;

/**
 * A round between hulls meets the first hull on its way ({@code air-nouns.md}, Overhead ships): each tick the round's
 * travel is a segment, and the first hull footprint or raised shield arc it crosses is where it stops, whichever side
 * that hull is on. The footprint is the true-scale rectangle soft contact keeps hulls apart by
 * ({@link WarshipSpacing}), and the hulls that can be met are the ones in contact ({@link WarshipSpacing#inContact}):
 * fighters, drones and boats, falling wrecks, hulls in the cloud deck, berthed, lifting or in a low hover are flown
 * over, as they are by other hulls. The round's own ship is never met: its rounds leave from inside its footprint.
 *
 * <p>A shield is met first, on the bubble, where the round crosses it from outside inside the arc; a round entering
 * the bubble through the open part of the arc goes on to the plate. Inside the bubble nothing stops a round but
 * the hull. Where a drawn hull reaches past its bubble, a round meeting the plate inside the raised arc is still
 * taken on the shield, as the arc was always asked of the side a round came from.
 *
 * <p>{@code -Dbattle.warship.inFlightCollision=false} is the control: rounds resolve only where they land, on hostile
 * hulls their blast reaches, and fire takes no account of friends in the line.
 */
final class WarshipRoundSweep {

    /** {@code -Dbattle.warship.inFlightCollision=false} restores landing-only resolution. */
    static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("battle.warship.inFlightCollision"));
    /**
     * {@code -Dbattle.warship.clearLineFire=false} keeps in-flight collision but lets a mount fire through a friend in
     * its line: the control for the clear-line check.
     */
    static final boolean CLEAR_LINE = !"false".equalsIgnoreCase(System.getProperty("battle.warship.clearLineFire"));
    /**
     * How much wider than its footprint a friend counts as being in the line, in cells: a round thrown by its cone
     * strays a little off the line it was laid along.
     */
    static final float CLEAR_LINE_PAD_CELLS = 1f;

    /** Where a round meets a hull: the hull, how far along the segment (0 to 1), the point, and whether on the shield. */
    record Contact(Warship ship, float t, float x, float y, boolean shield) {
    }

    private WarshipRoundSweep() {}

    /** Whether a hull is a body a round can meet: one in contact with other hulls. */
    static boolean meets(Warship ship) {
        return WarshipSpacing.inContact(ship);
    }

    /**
     * The first hull the segment {@code (x0, y0)} to {@code (x1, y1)} meets, or {@code null}: of every hull but
     * {@code shooter} that can be met, the one whose shield or footprint it crosses soonest.
     */
    static Contact first(List<Warship> ships, Warship shooter, float x0, float y0, float x1, float y1) {
        Contact best = null;
        for (Warship ship : ships) {
            if (ship == shooter || !meets(ship)) continue;
            Contact c = contact(ship, x0, y0, x1, y1);
            if (c != null && (best == null || c.t < best.t)) best = c;
        }
        return best;
    }

    /** Where the segment meets {@code ship}, shield first and then its footprint, or {@code null}. */
    static Contact contact(Warship ship, float x0, float y0, float x1, float y1) {
        float hull = footprintEntry(ship, x0, y0, x1, y1, 0f);
        if (ship.hasShield() && ship.shieldAbsorbing()) {
            float s = circleEntry(ship.shieldCentreX(), ship.shieldCentreY(), ship.shieldRadiusCells(), x0, y0, x1, y1);
            if (!Float.isNaN(s) && (Float.isNaN(hull) || s <= hull)) {
                float sx = x0 + (x1 - x0) * s;
                float sy = y0 + (y1 - y0) * s;
                if (ship.arcCovers(sx, sy)) return new Contact(ship, s, sx, sy, true);
            }
        }
        if (Float.isNaN(hull)) return null;
        float hx = x0 + (x1 - x0) * hull;
        float hy = y0 + (y1 - y0) * hull;
        // A drawn hull's rectangle can reach past its bubble (a sprite's margin, a long bow): a raised shield still
        // covers the hull inside its arc, so a round reaching the plate there is taken on the shield where it met it.
        boolean shielded = ship.hasShield() && ship.shieldAbsorbing() && ship.arcCovers(hx, hy);
        return new Contact(ship, hull, hx, hy, shielded);
    }

    /**
     * How far along the segment (0 to 1) it enters {@code ship}'s footprint grown by {@code pad} cells each way; 0 when
     * it starts inside, NaN when it misses.
     */
    static float footprintEntry(Warship ship, float x0, float y0, float x1, float y1, float pad) {
        double r = Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float cx = ship.centerX();
        float cy = ship.centerY();
        // Into the hull's own frame: across the beam (u) and along the keel (v), as Warship.distanceTo reads them.
        float ax = x0 - cx;
        float ay = y0 - cy;
        float u0 = ax * c + ay * s;
        float v0 = -ax * s + ay * c;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float du = dx * c + dy * s;
        float dv = -dx * s + dy * c;
        return boxEntry(u0, v0, du, dv, ship.widthCells * 0.5f + pad, ship.lengthCells * 0.5f + pad);
    }

    /** Entry parameter of {@code p + d t}, {@code t} in [0, 1], into the box {@code |u| <= hu, |v| <= hv}; NaN for none. */
    static float boxEntry(float u0, float v0, float du, float dv, float hu, float hv) {
        float t0 = 0f;
        float t1 = 1f;
        float[][] axes = {{u0, du, hu}, {v0, dv, hv}};
        for (float[] a : axes) {
            if (Math.abs(a[1]) < 1e-7f) {
                if (Math.abs(a[0]) > a[2]) return Float.NaN;
                continue;
            }
            float ta = (-a[2] - a[0]) / a[1];
            float tb = (a[2] - a[0]) / a[1];
            t0 = Math.max(t0, Math.min(ta, tb));
            t1 = Math.min(t1, Math.max(ta, tb));
        }
        return t0 <= t1 ? t0 : Float.NaN;
    }

    /**
     * Entry parameter (0 to 1) of the segment into the circle from outside; NaN when it starts inside or never
     * crosses it. A round already inside a bubble is not stopped by it.
     */
    static float circleEntry(float cx, float cy, float radius, float x0, float y0, float x1, float y1) {
        float fx = x0 - cx;
        float fy = y0 - cy;
        float c = fx * fx + fy * fy - radius * radius;
        if (c <= 0f || radius <= 0f) return Float.NaN;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float a = dx * dx + dy * dy;
        if (a < 1e-9f) return Float.NaN;
        float b = 2f * (fx * dx + fy * dy);
        float disc = b * b - 4f * a * c;
        if (disc < 0f) return Float.NaN;
        float t = (-b - (float) Math.sqrt(disc)) / (2f * a);
        return t >= 0f && t <= 1f ? t : Float.NaN;
    }

    /**
     * Whether a mount at {@code (mx, my)} has a clear line to {@code target} at {@code (ax, ay)}: no friendly hull
     * that can be met lies across the line, its footprint grown by {@link #CLEAR_LINE_PAD_CELLS}, before the line
     * reaches the target's own footprint. A hostile hull in the way does not foul the line: hitting it is no loss.
     */
    static boolean clearLine(Warship ship, Warship target, float mx, float my, float ax, float ay,
                             List<Warship> ships) {
        float reach = footprintEntry(target, mx, my, ax, ay, 0f);
        if (Float.isNaN(reach)) reach = 1f;
        for (Warship other : ships) {
            if (other == ship || other == target || !meets(other) || !ship.faction.friendlyTo(other.faction)) continue;
            float t = footprintEntry(other, mx, my, ax, ay, CLEAR_LINE_PAD_CELLS);
            if (!Float.isNaN(t) && t < reach) return false;
        }
        return true;
    }
}
