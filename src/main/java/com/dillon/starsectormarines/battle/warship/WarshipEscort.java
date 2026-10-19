package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.ShipClass.HullSize;
import com.dillon.starsectormarines.battle.warship.ShipClass.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * The escort relation, as pure geometry and selection: which ship a smaller combat hull may follow, and where it
 * sits beside it. The relation itself is a field on {@link Warship} ({@link Warship#escort}); the per-second
 * upkeep (keeping station, dropping a lost lead) is {@link WarshipSystem}'s.
 *
 * <p>An escort is a posture-layer role, not a body rule: it changes where the ship sits and what its threat
 * picture and anti-air fire favour ({@link AirThreatPicture}, {@link WarshipAirFire}), and nothing about how hard
 * anything hits. The lead needs no ship-as-target machinery: what threatens a lead is the strike craft aimed at it,
 * which the air layer already knows ({@code HullStrike.aimedAt}).
 */
public final class WarshipEscort {

    /**
     * {@code -Dbattle.warship.escortClearance=false} is the control that restores an escort keeping only the screen's
     * narrower touch gap from its own charge and steering straight across it to change sides.
     */
    public static final boolean CLEARS_CHARGE =
            !"false".equalsIgnoreCase(System.getProperty("battle.warship.escortClearance"));

    /** Cells of open air between the lead's hull and the escort's, on the escort's ring. */
    public static final float GAP_CELLS = 4f;

    private WarshipEscort() {}

    /** The ship {@code escort} is escorting if that ship is still a friendly hull over the battle, else {@code null}. */
    static Warship liveLead(Warship escort) {
        Warship lead = escort.escortLead();
        if (lead == null || lead == escort) return null;
        // A lead climbing out or escaping across its edge is still being escorted out; any other departure ends it.
        if (lead.destroyed() || lead.departed()
                || (lead.departing() && !lead.climbingOut() && !lead.escapingAcrossEdge())) return null;
        return escort.faction.friendlyTo(lead.faction) ? lead : null;
    }

    /** Whether a hull of class {@code escort} may escort one of class {@code lead}: a combat frigate, destroyer or cruiser following a larger hull. */
    public static boolean mayEscort(ShipClass escort, ShipClass lead) {
        return escort.role() == Role.COMBAT && escort.size() != HullSize.CAPITAL
                && lead.size().ordinal() > escort.size().ordinal();
    }

    /**
     * The lead an automatic assignment gives {@code ship}: the flagship of its side, which is the single largest
     * live friendly hull over the battle (largest size, then most hit points, then added first), provided
     * {@link #mayEscort} lets this hull follow it. A side's second-largest hull is never a lead, so escorts never
     * chain; {@code null} when the ship is not a candidate or no larger friendly hull is over the battle.
     */
    public static Warship autoLead(Warship ship, List<Warship> ships) {
        if (ship.destroyed() || ship.departing()) return null;
        Warship flagship = null;
        for (Warship other : ships) {
            if (other.destroyed() || other.departing() || !ship.faction.friendlyTo(other.faction)) continue;
            if (flagship == null || larger(other, flagship)) flagship = other;
        }
        if (flagship == null || flagship == ship) return null;
        return mayEscort(ship.shipClass(), flagship.shipClass()) ? flagship : null;
    }

    private static boolean larger(Warship a, Warship b) {
        int sa = a.shipClass().size().ordinal();
        int sb = b.shipClass().size().ordinal();
        if (sa != sb) return sa > sb;
        return a.maxHitPoints > b.maxHitPoints;
    }

    /** The escorts of {@code lead}, in the order the ships were added. */
    static List<Warship> escortsOf(Warship lead, List<Warship> ships) {
        List<Warship> out = new ArrayList<>();
        for (Warship s : ships) {
            if (s != lead && s.escortLead() == lead && liveLead(s) == lead) out.add(s);
        }
        return out;
    }

    /** Radius of the ring an escort sits on around its lead's centre: both half-lengths and the gap. */
    public static float ringRadius(Warship lead, Warship escort) {
        return 0.5f * lead.lengthCells + 0.5f * escort.lengthCells + GAP_CELLS;
    }

    /** The bearing of a vector in degrees, {@code atan2(dy, dx)}. */
    static float bearingDegrees(float dx, float dy) {
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    /** The bearing of a ship's nose, in the same convention the departure heading uses. */
    static float noseBearingDegrees(Warship ship) {
        return ship.facingDegrees + 90f;
    }

    /**
     * Where the escort in slot {@code slot} of {@code slots} stands: in line ahead beside its lead, as a screen does.
     * The line runs parallel to the lead's course ({@code leadNoseDegrees}, a bearing in the {@code atan2}
     * convention), {@code radius} out on whichever beam faces the threat ({@code threatDegrees}); the escorts are
     * spaced {@code spacing} apart along it and centred on the lead, so the screen keeps pace with its charge on a
     * parallel line between it and the threat rather than circling it.
     */
    public static float[] station(float leadX, float leadY, float leadNoseDegrees, float radius, float threatDegrees,
                                  int slot, int slots, float spacing) {
        double nose = Math.toRadians(leadNoseDegrees);
        float ax = (float) Math.cos(nose);
        float ay = (float) Math.sin(nose);
        // The lead's port beam; the starboard is its opposite. The screen takes the beam the threat is on.
        float bx = -ay;
        float by = ax;
        double threat = Math.toRadians(threatDegrees);
        if (bx * Math.cos(threat) + by * Math.sin(threat) < 0.0) {
            bx = -bx;
            by = -by;
        }
        float along = (slot - (slots - 1) * 0.5f) * spacing;
        return new float[]{leadX + bx * radius + ax * along, leadY + by * radius + ay * along};
    }

    /** The most of the way round its charge, in degrees, an escort changing sides steers for at once. */
    static final float ROUND_STEP_DEGREES = 45f;

    /**
     * Radius about the lead's centre that clears both hulls at any headings by {@link #GAP_CELLS}: both hulls'
     * half-diagonals and the gap. A lead turning in place sweeps no corner past it.
     */
    public static float roundingRadius(Warship lead, Warship escort) {
        return halfDiagonal(lead) + halfDiagonal(escort) + GAP_CELLS;
    }

    private static float halfDiagonal(Warship ship) {
        return 0.5f * (float) Math.hypot(ship.lengthCells, ship.widthCells);
    }

    /**
     * Where an escort steers for its station {@code (sx, sy)} (a centre-of-gravity point): straight there, unless the
     * straight way passes inside {@link #roundingRadius} of its charge, when it comes round the charge instead,
     * steering for a point on that radius at most {@link #ROUND_STEP_DEGREES} further round, the short way, toward the
     * station's side. A screen changing beams goes round its charge, not across its bow or along its side.
     *
     * @return {@code [x, y]}, a centre-of-gravity point
     */
    public static float[] roundCharge(Warship escort, Warship lead, float sx, float sy) {
        float ox = escort.centerX() - escort.x;
        float oy = escort.centerY() - escort.y;
        float lx = lead.centerX();
        float ly = lead.centerY();
        float r = roundingRadius(lead, escort);
        float[] waypoint = rounding(escort.centerX() - lx, escort.centerY() - ly, sx + ox - lx, sy + oy - ly, r,
                escort.serial() % 2 == 0);
        if (waypoint == null) return new float[]{sx, sy};
        return new float[]{lx + waypoint[0] - ox, ly + waypoint[1] - oy};
    }

    /**
     * The geometry of {@link #roundCharge}, about the charge's centre: from {@code (ex, ey)} to {@code (tx, ty)}, the
     * waypoint on radius {@code r}, or {@code null} when the straight way stays outside {@code r} short of its end.
     * {@code portOnTie} picks the way round when the station is dead opposite.
     */
    static float[] rounding(float ex, float ey, float tx, float ty, float r, boolean portOnTie) {
        float dx = tx - ex;
        float dy = ty - ey;
        float dd = dx * dx + dy * dy;
        if (dd < 1e-6f) return null;
        // The closest the straight way comes to the charge, short of the station itself.
        float t = -(ex * dx + ey * dy) / dd;
        if (t <= 0f || t >= 1f) return null;
        float cx = ex + dx * t;
        float cy = ey + dy * t;
        if (cx * cx + cy * cy >= r * r) return null;
        double from = Math.atan2(ey, ex);
        double to = Math.atan2(ty, tx);
        double delta = Math.IEEEremainder(to - from, 2.0 * Math.PI);
        if (Math.abs(Math.abs(delta) - Math.PI) < 1e-3) delta = portOnTie ? Math.PI : -Math.PI;
        double step = Math.toRadians(ROUND_STEP_DEGREES);
        double at = from + Math.signum(delta) * Math.min(Math.abs(delta), step);
        return new float[]{(float) (r * Math.cos(at)), (float) (r * Math.sin(at))};
    }

    /** Distance between neighbouring escorts in a screen's line: an escort's length and the gap. */
    public static float lineSpacing(Warship escort) {
        return escort.lengthCells + GAP_CELLS;
    }
}
