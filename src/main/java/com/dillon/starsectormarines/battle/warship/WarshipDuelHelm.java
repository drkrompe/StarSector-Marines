package com.dillon.starsectormarines.battle.warship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The duel helm: where a ship fighting a hostile hull puts itself, as pure functions of the two hulls' own guns and
 * of where the target is going.
 *
 * <p>Ships fight in atmosphere as ships of the line do, not as fighters, and how a ship fights follows from where
 * its guns bear ({@link #bowFighter}):
 * <ul>
 *   <li>A <b>broadside</b> ship (one that can keep most of its battery on a target laid abeam) takes its station on
 *   the target's <em>beam</em>, square to the target's course on whichever side it already is and a little ahead,
 *   so the two lines converge or run parallel.</li>
 *   <li>A <b>bow</b> ship (one whose battery bears only ahead) hunts: it takes its station on the bearing round the
 *   target where the target's own guns bear least, weighed against how far it has to fly to get there, and holds
 *   its bow on the target from there. A heavy hull cannot pivot, so it plans its approach to that quarter rather
 *   than meeting the target nose-on.</li>
 * </ul>
 *
 * <p>The range is the <b>standoff law</b>, read from both hulls and their guns and never authored per hull. A ship's
 * reach is its strongest anti-ship gun's hull-to-hull reach ({@link WarshipScale#hullReach}). A ship that outranges
 * its target holds off between the two reaches; one that is outranged (or evenly matched) closes to
 * {@link #CLOSE_SHARE} of its own reach. Either way the range is never more than {@link #STANDOFF_LENGTHS} of the two
 * hulls' mean length, so an outranging ship cannot sit untouchable across the map, and never nearer than the two
 * hulls and a clearance, so big hulls do not overlap.
 *
 * <p>The heading is chosen separately ({@link WarshipShipRules#bestHeading}). Speed, acceleration and braking are
 * the hull's own ({@link AtmosphericHandling}): the helm only moves the station the ship is already flying to.
 */
public final class WarshipDuelHelm {

    private WarshipDuelHelm() {}

    /** A ship outranges its target only when its reach is longer by at least this many cells. */
    static final float OUTRANGE_MARGIN_CELLS = 2f;
    /** An outranged or even ship closes to this share of its own reach. */
    static final float CLOSE_SHARE = 0.75f;
    /** Open air, in cells, a duelling ship keeps between its hull and its target's at the least. */
    static final float CLEARANCE_CELLS = 6f;
    /** The farthest a duelling ship stands from its target, in the two hulls' mean lengths, centre to centre. */
    static final float STANDOFF_LENGTHS =
            Float.parseFloat(System.getProperty("battle.warship.standoffLengths",
                    Float.toString(3f * WarshipScale.HULL_RANGE)));
    /** Bearings round the target a bow ship weighs for its station. */
    static final int BOW_BEARINGS = 24;
    /** How much flying the whole range round to a quiet quarter costs against the target's full battery bearing. */
    static final float BOW_TRAVEL_WEIGHT = 0.35f;
    /**
     * How much a point inside the target's front shield arc costs, against the target's full battery bearing: fire
     * from there is absorbed, so a quarter outside the arc (on its beam, crossing its T) is worth flying round to.
     */
    static final float BOW_SHIELD_WEIGHT = 0.5f;
    /** How far ahead of the target's beam the station sits, as a share of the range: the lines converge. */
    static final float CONVERGE_SHARE = 0.25f;
    /** With the edge pressure off ({@link WarshipEdgeHelm}), a station is kept this many cells plus half the hull inside the map edge. */
    static final float EDGE_MARGIN_CELLS = 3f;
    /**
     * How far beyond its charge's hull an escort fighting a hull may stand, as a share of its own reach. A screen
     * fights beside the ship it screens: let out to its whole reach, the Hound nearest the Wolfs over Black Sluice ran
     * out ahead of the Greyhook and was shot down alone, before its own guns bore, while its partner was still a
     * hundred cells behind; held to a quarter, the Greyhook's guns join the fight with it.
     */
    static final float ESCORT_LEASH_REACH_SHARE = 0.25f;

    /** The reach, in cells, of the ship's strongest weapon that may fire at a hull; zero when it has none. */
    public static float reach(Warship ship) {
        Warship.Mount m = WarshipShipRules.strongest(ship);
        return m == null ? 0f : WarshipScale.hullReach(m.weapon.rangePx());
    }

    /**
     * The reach, in cells, inside which most of the ship's anti-ship battery bears: the longest range at which guns
     * holding {@link #BATTERY_SHARE} of its firepower all reach. A ship whose strongest gun outranges the rest of its
     * battery (an Eagle's graviton beams over its heavy needlers) fights where the battery can join in, not where only
     * the one gun can; zero when it has no gun at a hull.
     */
    public static float batteryReach(Warship ship) {
        List<Warship.Mount> guns = new ArrayList<>();
        float total = 0f;
        for (Warship.Mount m : ship.mounts()) {
            if (!WarshipShipRules.gunAtHull(m.weapon) || m.knockedOut()) continue;
            guns.add(m);
            total += WarshipShipRules.firepower(m.weapon);
        }
        if (guns.isEmpty() || total <= 0f) return reach(ship);
        guns.sort(Comparator.comparingDouble((Warship.Mount m) -> -m.weapon.rangePx()));
        float held = 0f;
        for (Warship.Mount m : guns) {
            held += WarshipShipRules.firepower(m.weapon);
            if (held >= BATTERY_SHARE * total) return WarshipScale.hullReach(m.weapon.rangePx());
        }
        return WarshipScale.hullReach(guns.get(guns.size() - 1).weapon.rangePx());
    }

    /** The share of its anti-ship firepower a ship wants bearing at its fighting range. */
    static final float BATTERY_SHARE = 0.6f;

    /** Whether a ship with {@code mine} reach holds off a target with {@code theirs}, rather than closing. */
    public static boolean holdsOff(float mine, float theirs) {
        return mine >= theirs + OUTRANGE_MARGIN_CELLS;
    }

    /**
     * The range, centre to centre, a ship wants from its target: midway between the two reaches when it outranges
     * the target, {@link #CLOSE_SHARE} of its own reach otherwise.
     */
    public static float preferredRange(float mine, float theirs) {
        return holdsOff(mine, theirs) ? 0.5f * (mine + theirs) : CLOSE_SHARE * mine;
    }

    /**
     * {@link #preferredRange}, never farther than {@link #STANDOFF_LENGTHS} of the two hulls' mean length and never
     * nearer than the two hulls' half-lengths and {@link #CLEARANCE_CELLS}.
     */
    public static float preferredRange(float mine, float theirs, float halfLength, float targetHalfLength) {
        float standoff = STANDOFF_LENGTHS * (halfLength + targetHalfLength);
        float clear = halfLength + targetHalfLength + CLEARANCE_CELLS;
        return Math.max(clear, Math.min(standoff, preferredRange(mine, theirs)));
    }

    /**
     * Whether {@code ship} fights bow-on: no heading that lays a target abeam keeps {@link
     * WarshipShipRules#BROADSIDE_SHARE} of the anti-ship firepower that bears dead ahead. Read off the hull's own
     * mounts, so a Wolf (H), whose battery is all forward, hunts bow-on and an Onslaught or a Venture broadsides.
     */
    public static boolean bowFighter(Warship ship) {
        float d = 1000f;
        float ahead = WarshipShipRules.firepowerAt(ship, 0f, ship.x, ship.y + d);
        float beam = Math.max(WarshipShipRules.firepowerAt(ship, 0f, ship.x - d, ship.y),
                WarshipShipRules.firepowerAt(ship, 0f, ship.x + d, ship.y));
        return ahead > 0f && beam < ahead * WarshipShipRules.BROADSIDE_SHARE;
    }

    /**
     * A bow ship's station: of {@link #BOW_BEARINGS} points round the target at {@code range}, the one where the
     * target's own anti-ship guns bear least, less {@link #BOW_TRAVEL_WEIGHT} for each range's worth of flying to get
     * there, and {@link #BOW_SHIELD_WEIGHT} dearer where the target's front shield covers it; kept inside
     * the map's edge margin ({@link WarshipEdgeHelm#settle}).
     */
    public static float[] bowStation(Warship ship, Warship target, float range, WarshipEdgeHelm edges) {
        return bowStation(ship, target, range, edges, Float.NaN, 0f, 0f);
    }

    /**
     * {@link #bowStation}, with a bearing round the target to keep away from: a point within {@code separation}
     * degrees of {@code avoid} (a partner's bearing round the target) costs up to {@code weight} more, falling off to
     * nothing at the separation, so two ships on one target work it from two sides. {@code avoid} NaN is the plain
     * station.
     */
    public static float[] bowStation(Warship ship, Warship target, float range, WarshipEdgeHelm edges, float avoid,
                                     float separation, float weight) {
        float tx = target.centerX();
        float ty = target.centerY();
        float most = 0f;
        float[] threat = new float[BOW_BEARINGS];
        float[][] at = new float[BOW_BEARINGS][];
        for (int i = 0; i < BOW_BEARINGS; i++) {
            double a = 2.0 * Math.PI * i / BOW_BEARINGS;
            at[i] = edges.settle(ship, tx + (float) Math.cos(a) * range, ty + (float) Math.sin(a) * range);
            float x = at[i][0];
            float y = at[i][1];
            threat[i] = WarshipShipRules.firepowerAt(target, target.facingDegrees, x, y);
            most = Math.max(most, threat[i]);
        }
        float[] best = at[0];
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < BOW_BEARINGS; i++) {
            float travel = (float) Math.hypot(at[i][0] - ship.x, at[i][1] - ship.y) / Math.max(1f, range);
            float score = (most > 0f ? threat[i] / most : 0f) + BOW_TRAVEL_WEIGHT * travel
                    + (shieldCovers(target, at[i][0], at[i][1]) ? BOW_SHIELD_WEIGHT : 0f);
            if (!Float.isNaN(avoid) && separation > 0f) {
                float round = Math.abs(WarshipFacing.wrap(WarshipFacing.bearing(tx, ty, at[i][0], at[i][1]) - avoid));
                score += weight * Math.max(0f, 1f - round / separation);
            }
            if (score < bestScore) {
                bestScore = score;
                best = at[i];
            }
        }
        return best;
    }

    /** Whether {@code target}'s front shield faces the point {@code (x, y)}: a shot from there is in its arc. */
    static boolean shieldCovers(Warship target, float x, float y) {
        if (!target.hasShield()) return false;
        WarshipShield shield = target.shieldModel();
        if (shield.type() == WarshipShield.Type.OMNI) return true;
        if (shield.type() != WarshipShield.Type.FRONT) return false;
        float bearing = WarshipFacing.bearing(target.centerX(), target.centerY(), x, y);
        return Math.abs(WarshipFacing.wrap(bearing - target.facingDegrees)) <= 0.5f * shield.arcDegrees();
    }

    /**
     * Where an escort that is fighting a hull may stand: {@code station}, pulled in to within {@code leash} of its
     * lead's centre. It screens by fighting, but never so far out that it leaves its charge.
     */
    public static float[] leashed(float[] station, float leadX, float leadY, float leash) {
        float dx = station[0] - leadX;
        float dy = station[1] - leadY;
        float d = (float) Math.hypot(dx, dy);
        if (d <= leash || d < 1e-3f) return station;
        return new float[]{leadX + dx / d * leash, leadY + dy / d * leash};
    }

    /**
     * Where the ship should be now: {@code range} out on the target's beam, on the side the ship is on, and
     * {@link #CONVERGE_SHARE} of the range ahead along the target's course, kept off the map's edges
     * ({@link WarshipEdgeHelm#settle}). {@code targetNose} is the bearing of the target's nose in the {@code atan2}
     * convention. A ship dead ahead or astern of its target takes the beam {@code preferPort} names.
     *
     * @return {@code [x, y]}
     */
    public static float[] station(float shipX, float shipY, float targetX, float targetY, float targetNose,
                                  float range, boolean preferPort, Warship ship, WarshipEdgeHelm edges) {
        double nose = Math.toRadians(targetNose);
        float ax = (float) Math.cos(nose);
        float ay = (float) Math.sin(nose);
        float bx = -ay;
        float by = ax;
        float side = (shipX - targetX) * bx + (shipY - targetY) * by;
        if (side < -1e-3f || (Math.abs(side) <= 1e-3f && !preferPort)) {
            bx = -bx;
            by = -by;
        }
        float x = targetX + bx * range + ax * range * CONVERGE_SHARE;
        float y = targetY + by * range + ay * range * CONVERGE_SHARE;
        return edges.settle(ship, x, y);
    }
}
