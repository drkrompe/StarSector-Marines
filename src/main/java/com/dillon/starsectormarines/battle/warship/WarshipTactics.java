package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.WarshipManoeuvre.Kind;
import com.dillon.starsectormarines.battle.warship.WarshipManoeuvre.Role;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The layer above the duel helm ({@code warship-tactics.md}): ships fighting hulls as a group, flying manoeuvres.
 *
 * <p><b>The group pass</b> ({@link #plan}) runs once a second per side. Its hunters are the ships fighting hulls with
 * their duel helm, free to manoeuvre and not escorting. Where the other side has an escorted hull (a <em>charge</em>)
 * and there are two or more hunters, the one best placed to go round flanks the charge and the rest pin its escorts,
 * each the escort nearest it. Otherwise each hunter takes the hull cheapest for it (range over reach) plus a penalty
 * for every friend already on it, so a second ship goes to another hull while one remains; a hull worn under a third,
 * or the last one left, may be shared on purpose. Hunters that share a hull work it from opposite sides, and broadside
 * ships sharing one form a line: the nearest leads, the rest follow in its wake.
 *
 * <p><b>The manoeuvre machine</b> ({@link #fly}) runs every tick for each hunter. A manoeuvre is held for tens of
 * seconds and ends on its own conditions, never on a timer alone and never re-chosen from scratch each tick:
 * approach out of reach on an oblique course; a bow ship's attack run to its standoff with its guns on; a broadside
 * ship's run parallel to the target with its broadside laid; extend to open the range; come about through a fixed
 * turn back onto the target; flank wide round a charge's escorts; line ahead in a lead's wake. A front-shielded bow
 * ship never turns its back in reach: its run gives way to a <b>standoff</b>, a drift astern and to one side with its
 * nose, guns and shield still on the target, which opens past the target's reach when its own pool has filled
 * (venting) and otherwise ends in another run. A run is held on while the target is near its flux limit. A bow ship
 * with no front to keep (an omni shield or none) comes about instead. Which follows which is read off where the ship's
 * guns bear (a bow or broadside ship), its shield, the range against both reaches, both pools, and the role. The
 * standoff law still sets how close a run goes. Outcomes are the fight's own.
 *
 * <p><b>A duel</b> (one hunter against one hull) is flown for position: a bow ship approaches for its target's quarter,
 * a front-shielded one orbits between runs with its shield and side guns still on the target, a ship ahead on flux and
 * hull turns in first across the target's T, a ship turned in on turns in to answer, and the one whose shield is going
 * breaks away at full way, its quarter crossed by the other. {@code -Dbattle.warship.duelManoeuvres=false} is the
 * control.
 *
 * <p><b>The pack relay</b> ({@link #relay}): two or more bow ships on one hull share it as a tank, which holds the
 * target's attention and fires only as its flux allows, and strikers, which work its quarter at full fire; they swap
 * when the tank runs high, the old tank venting out of reach. {@code -Dbattle.warship.packRelay=false} is the control,
 * and {@code -Dbattle.warship.threatTargeting=false} the control for the target's threat-aware choice that the relay
 * catches and sheds the target's attention by.
 *
 * <p>A ship flying a manoeuvre travels along its nose ({@link #alongNose}): it can drift sideways and astern only at
 * a fraction of its speed, so its motion reads as a course, not a hover. {@code -Dbattle.warship.tactics=false} is the
 * control: every hunter flies the single-ship duel helm, and the pack division is the old one.
 */
public final class WarshipTactics {

    /** {@code -Dbattle.warship.tactics=false} turns the group layer and manoeuvres off. */
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("battle.warship.tactics"));

    /** How often, in seconds, the group pass runs. */
    static final float PLAN_SECONDS = 1f;
    /** Out of reach by this share of its own longest anti-ship reach, a ship approaches; inside it, it runs. */
    static final float IN_REACH_SHARE = 1.05f;
    /** The oblique an approach takes off the straight line to its target, toward its side, in degrees. */
    static final float APPROACH_OBLIQUE_DEGREES = 30f;
    /** An attack run lasts at least this long, in seconds, before anything but flux ends it. */
    static final float RUN_MIN_SECONDS = 10f;
    /** No run lasts longer than this: the range breathes. */
    static final float RUN_MAX_SECONDS = 30f;
    /** A broadside run lasts this long unless it passes or wears its side first. */
    static final float BROADSIDE_RUN_SECONDS = 35f;
    /** A run ends to open the range once the pool is this full, so a ship vents between passes. */
    static final float RUN_FLUX_LIMIT = 0.75f;
    /** How far off the line to its target an extend runs, in degrees: a break across the target's bow or stern. */
    static final float EXTEND_TURN_DEGREES = 110f;
    /** An extend lasts at least this long. */
    static final float EXTEND_MIN_SECONDS = 6f;
    /** And at most this long. */
    static final float EXTEND_MAX_SECONDS = 25f * WarshipScale.HULL_RANGE;
    /** A come-about ends once the nose is this close to where its next run wants it. */
    static final float COME_ABOUT_DONE_DEGREES = 20f;
    /** And never lasts longer than this. */
    static final float COME_ABOUT_MAX_SECONDS = 45f;
    /** How far the helm leads a forced turn, in degrees, so the hull turns the chosen way. */
    static final float TURN_LEAD_DEGREES = 60f;
    /** How far a broadside run bends toward or away from the target to hold its range, at most, in degrees. */
    static final float RANGE_BEND_DEGREES = 30f;
    /** A flank ends when the ship is within this many degrees round the charge of its goal bearing. */
    static final float FLANK_DONE_DEGREES = 35f;
    /** How far round the charge a flank waypoint is taken each step, in degrees. */
    static final float FLANK_STEP_DEGREES = 50f;
    /** And no flank lasts longer than this. */
    static final float FLANK_MAX_SECONDS = 90f;
    /** A flank swings round at this share of the ship's reach from the charge, at the least. */
    static final float FLANK_RADIUS_REACH_SHARE = 1.15f;
    /** How many hull-lengths ahead a fixed-course manoeuvre puts the station, so the hull cruises rather than arrives. */
    static final float CRUISE_LENGTHS = 3f;
    /** The interval between hulls in a line, in screen gaps. */
    static final float LINE_GAP_SHARE = 3f;
    /** A friend already on a hull costs a hunter this much, in reaches of range, before it shares it. */
    static final float SHARE_PENALTY = 1.2f;
    /** Keeping the hull it had is worth this much, in reaches, so a near tie does not flap. */
    static final float KEEP_BONUS = 0.5f;
    /** A hull worn under this share is worth sharing: focus fire to finish it. */
    static final float FOCUS_HULL = 0.33f;
    /** A flanker keeps its role until another hunter is better placed by this many degrees round the charge. */
    static final float FLANK_SWITCH_DEGREES = 30f;
    /** A line's lead keeps this share of its speed once its line is closed up, so the followers can hold station. */
    static final float LEAD_SPEED_SHARE = 0.85f;
    /** And this while a follower is still out of its place. */
    static final float LEAD_WAIT_SPEED_SHARE = 0.6f;
    /** Sideways drift a ship on a manoeuvre may make, as a share of its top speed. */
    static final float LATERAL_SHARE = 0.3f;
    /** Astern way a ship on a manoeuvre may make, as a share of its top speed. */
    static final float ASTERN_SHARE = 0.25f;
    /** A target's pool this full (or overloaded) is pressed: a run is not broken off while the target is near its limit. */
    static final float PRESS_FLUX = 0.6f;
    /** A bow ship's run gives way to a standoff once its own pool is this full. */
    static final float STANDOFF_FLUX = 0.5f;
    /** A standoff lasts at least this long, in seconds. */
    static final float STANDOFF_MIN_SECONDS = 8f;
    /** And at most this long, unless the ship is venting a full pool. */
    static final float STANDOFF_MAX_SECONDS = 30f;
    /** A ship standing off to vent turns back in once its pool is down to this. */
    static final float VENTED_FLUX = 0.3f;
    /** How far round its target, per standoff, a ship drifts as it opens the range, in degrees. */
    static final float STANDOFF_SLIDE_DEGREES = 30f;
    /** A standoff opens the range to this share of the way from the standoff range to the ship's own reach. */
    static final float STANDOFF_OPEN_SHARE = 0.7f;
    /** A venting standoff opens to this share of the target's longest reach: just past it. */
    static final float VENT_CLEAR_SHARE = 1.05f;
    /**
     * How far round the target a clearing drift slides: wider than an ordinary standoff's, since the ship sidesteps
     * faster than it backs and has to get out of reach rather than merely open it.
     */
    static final float VENT_SLIDE_DEGREES = 60f;

    /**
     * {@code -Dbattle.warship.duelManoeuvres=false} is the control: a front-shielded bow ship ends its run in the
     * nose-on standoff drift, and nobody presses a target for having turned its cover away.
     */
    static final boolean DUEL_MANOEUVRES =
            !"false".equalsIgnoreCase(System.getProperty("battle.warship.duelManoeuvres"));
    /** An orbit keeps its target this far inside the edge of the ship's shield arc. */
    static final float ORBIT_ARC_MARGIN_DEGREES = 10f;
    /** An orbit never holds its target nearer the bow than this, so it carries the ship round rather than in. */
    static final float ORBIT_MIN_ANGLE_DEGREES = 45f;
    /** Nor further off it than the beam: past it the orbit opens the range, which is a break's work. */
    static final float ORBIT_MAX_ANGLE_DEGREES = 90f;
    /**
     * An orbit gives up this much of its widest covered angle, at most, to keep part of the battery bearing: a Wolf
     * holds its target where its turret still reaches rather than at its shield's very edge.
     */
    static final float ORBIT_GUN_GIVE_DEGREES = 20f;
    /**
     * The share of its bow firepower a ship must keep bearing to circle at all. A hull whose battery is almost all in
     * its bow (an Eagle) gives up too much by circling, and holds its nose on, sliding sideways, instead: a standoff.
     */
    static final float ORBIT_FIRE_SHARE = 0.3f;
    /** An orbit lasts at least this long before it turns back in for a run. */
    static final float ORBIT_MIN_SECONDS = 10f;
    /** And at most this long. */
    static final float ORBIT_MAX_SECONDS = 25f;
    /** Inside this share of the standoff an orbit turns in to run rather than spiralling closer. */
    static final float ORBIT_CLOSE_SHARE = 0.6f;
    /** How far an orbit bends its angle to hold the range, at most, in degrees (out when short, in when long). */
    static final float ORBIT_BEND_DEGREES = 25f;
    /** An orbit that finds its target's cover turned away turns in on it after at least this long. */
    static final float CROSS_AFTER_SECONDS = 2f;
    /** An orbit turns in first on a target whose battery is off it after at least this long. */
    static final float TURN_IN_AFTER_SECONDS = 4f;
    /** And only with at least this much {@link #advantage} over the target. */
    static final float TURN_IN_FLUX_LEAD = 0.02f;
    /** A hull without a front shield is covered from where it brings at least this share of its bow battery to bear. */
    static final float COVER_FIRE_SHARE = 0.5f;
    /** A break opens on a course at least this far off the bearing to its target, in degrees. */
    static final float BREAK_MIN_DEGREES = 110f;
    /** And at most this far: a front shield wide enough keeps covering the target as the ship opens. */
    static final float BREAK_MAX_DEGREES = 150f;
    /** A break lasts at least this long. */
    static final float BREAK_MIN_SECONDS = 8f;
    /** And at most this long. */
    static final float BREAK_MAX_SECONDS = 40f;
    /**
     * A breaking ship being run down by a target whose guns bear on it comes about early, to face it, once its pool is
     * down to this: the defender's counter-turn.
     */
    static final float COUNTER_FLUX = 0.5f;

    // ------------------------------------------------------------------------------------- the pack relay

    /**
     * {@code -Dbattle.warship.packRelay=false} is the control: two or more bow ships on one hull work it from opposite
     * sides with the ordinary runs and standoffs, nobody holding the target's attention for the other.
     */
    static final boolean PACK_RELAY_DEFAULT =
            !"false".equalsIgnoreCase(System.getProperty("battle.warship.packRelay"));
    /**
     * {@code -Dbattle.warship.threatTargeting=false} is the control: a hunter choosing between hulls reads only the
     * range over its reach, as before, and not which of them has its guns on it or how far it must turn to answer.
     */
    static final boolean THREAT_TARGETING_DEFAULT =
            !"false".equalsIgnoreCase(System.getProperty("battle.warship.threatTargeting"));
    /** A relay role is held at least this long, in seconds, before the pack swaps. */
    static final float RELAY_MIN_SECONDS = 12f;
    /** A tank whose pool is this full hands the target over to a fresh partner. */
    static final float SWAP_FLUX = 0.5f;
    /** A partner whose pool is no fuller than this is fresh enough to take the target over. */
    static final float READY_FLUX = 0.4f;
    /** A striker whose pool is this full breaks off to vent while the tank holds. */
    static final float STRIKER_VENT_FLUX = 0.75f;
    /**
     * A tank fires only while the hard flux its shield holds stays under this share of its pool, so its shield keeps
     * the headroom to hold the target; its own guns' flux is soft and vents behind the shield, so it does not count.
     */
    static final float TANK_FIRE_SHARE = 0.5f;
    /** A tank stands at this share of the standoff: nearer the target than its partners, so it is the hull in front. */
    static final float TANK_RANGE_SHARE = 0.75f;
    /** How much flying round to the target's battery arc costs a tank, per range's worth: it closes where it is. */
    static final float TANK_TRAVEL_WEIGHT = 1.2f;
    /** A striker keeps at least this far round the target from its tank, in degrees: the two work it from two sides. */
    static final float STRIKER_SEPARATION_DEGREES = 90f;
    /** What a striker station inside that separation costs, at the tank's own bearing, against the target's battery. */
    static final float STRIKER_SEPARATION_WEIGHT = 0.6f;
    /** A tank handing over turns away once its partner is in place, or after this long, in seconds, regardless. */
    static final float HANDOVER_MAX_SECONDS = 15f;
    /**
     * Or once its own pool is this full: its shield is going, and a ship whose shield is going turns away and opens at
     * full way rather than sit out an overload in the target's sights, as a duel's break does.
     */
    static final float HANDOVER_DANGER_FLUX = 0.85f;
    /** How far off straight away from the target a venting ship runs, toward the side away from its partner, in degrees. */
    static final float VENT_OFFSET_DEGREES = 30f;
    /** A venting ship holds this far out, as a share of the target's longest reach. */
    static final float VENT_HOLD_SHARE = 1.15f;
    /**
     * A venting ship whose course out runs into its margin short of this share of its hold looks for a clearer one,
     * up to {@link #VENT_COURSE_SWING_DEGREES} either side, in steps of {@link #VENT_COURSE_STEP_DEGREES}, each step
     * off its course costing a little ({@link #VENT_COURSE_SWING_COST} at the widest) against the room it buys.
     */
    static final float VENT_ROOM_SHARE = 0.8f;
    static final float VENT_COURSE_SWING_DEGREES = 90f;
    static final float VENT_COURSE_STEP_DEGREES = 15f;
    static final float VENT_COURSE_SWING_COST = 0.15f;
    /** A venting ship this close to its hold, in cells, is there: it stops running and turns its nose back. */
    static final float VENT_ARRIVE_CELLS = 4f;
    /** In choosing between hulls, what a hull with no gun laid on the chooser costs against one with its battery on it. */
    static final float THREAT_WEIGHT = 0.6f;
    /** And what a half-circle of turning to bring the chooser's own guns to bear costs. */
    static final float TURN_WEIGHT = 0.3f;
    /**
     * How much of the bonus for keeping its hull a chooser gives up when that hull is no threat to it: half, so a
     * hull that has turned away is let go for one that has its guns on, but two hulls trading places round it do not
     * have it swinging between them.
     */
    static final float KEEP_THREAT_SHARE = 0.5f;
    /** A hull closing from short of its reach counts at most this share of the threat it will be in reach. */
    static final float INCOMING_MENACE = 0.5f;
    /** A hull with less than half its battery laid on the chooser has stopped being the threat it was. */
    static final float LET_GO_MENACE = 0.5f;

    private boolean packRelay = PACK_RELAY_DEFAULT;
    private boolean threatTargeting = THREAT_TARGETING_DEFAULT;

    /** Turns the pack relay on or off for this battle (a scene's control loop); the default is the property's. */
    void packRelay(boolean on) {
        this.packRelay = on;
    }

    boolean packRelay() {
        return packRelay;
    }

    /** Turns threat-aware target choice on or off for this battle; the default is the property's. */
    void threatTargeting(boolean on) {
        this.threatTargeting = on;
    }

    private float nextPlan;

    /** Groups and assigns, once a second. */
    void plan(float now, List<Warship> ships, boolean held) {
        if (!ENABLED || now < nextPlan) return;
        nextPlan = now + PLAN_SECONDS;
        for (int side = 0; side < 2; side++) {
            List<Warship> hunters = new ArrayList<>();
            List<Warship> enemies = new ArrayList<>();
            for (Warship s : ships) {
                boolean ours = (side == 0) == Faction.MARINE.friendlyTo(s.faction);
                if (!ours) {
                    if (!s.destroyed() && !s.departed()) enemies.add(s);
                    continue;
                }
                if (hunter(s, held)) hunters.add(s);
                else if (s.manoeuvre().role != Role.NONE) s.manoeuvre().clear();
            }
            enemies.removeIf(e -> hunters.isEmpty() || !WarshipShipFire.hostileHull(hunters.get(0), e));
            if (hunters.isEmpty()) continue;
            if (enemies.isEmpty()) {
                for (Warship h : hunters) h.manoeuvre().clear();
                continue;
            }
            assign(hunters, enemies, ships, now);
            // One ship against one: the duel's own manoeuvres (orbit, cross, break) are flown. A pack, a line or an
            // escorted charge is the group's choreography and keeps its own.
            boolean duel = DUEL_MANOEUVRES && hunters.size() == 1 && enemies.size() == 1;
            for (Warship h : hunters) h.manoeuvre().duel = duel;
        }
    }

    /** Whether a ship is one of its side's hunters: fighting hulls with its duel helm, free, not escorting. */
    static boolean hunter(Warship s, boolean held) {
        return (WarshipShipFire.looksAtHulls(s, held) || overloadedInFight(s)) && s.duelHelm() && !s.departing()
                && free(s) && WarshipEscort.liveLead(s) == null;
    }

    /**
     * Whether {@code s} is overloaded in the middle of its hull fight: its guns hold no target while the overload
     * lasts, but it is still flying the manoeuvre its fight gave it, and a collapsed shield is when a ship breaks away.
     */
    static boolean overloadedInFight(Warship s) {
        Warship t = s.manoeuvre().target();
        return s.manoeuvre().duel && s.overloaded() && t != null && !t.destroyed() && !t.departed();
    }

    /** Whether the ship's helm is its own: not piloted, leaving, on a route, a low pass, a remote order or in the deck. */
    static boolean free(Warship s) {
        return !s.piloted() && !s.departing() && !s.isPassingThrough() && s.lowPass() == null && !s.descending()
                && s.remoteOrder() == Warship.RemoteOrder.NONE && !s.berthed() && !s.liftRunning();
    }

    private void assign(List<Warship> hunters, List<Warship> enemies, List<Warship> ships, float now) {
        Warship charge = null;
        float chargeGap = Float.MAX_VALUE;
        float[] centre = centroid(hunters);
        for (Warship e : enemies) {
            if (WarshipEscort.escortsOf(e, ships).isEmpty()) continue;
            float g = e.distanceTo(centre[0], centre[1]);
            if (g < chargeGap) {
                charge = e;
                chargeGap = g;
            }
        }
        if (charge != null && hunters.size() >= 2) {
            divideOnCharge(hunters, charge, WarshipEscort.escortsOf(charge, ships));
            return;
        }
        Map<Warship, List<Warship>> on = distribute(hunters, enemies, threatTargeting);
        for (Map.Entry<Warship, List<Warship>> entry : on.entrySet()) relay(now, entry.getKey(), entry.getValue());
    }

    /** One hunter flanks the charge; the rest pin its escorts, spread over them. */
    private static void divideOnCharge(List<Warship> hunters, Warship charge, List<Warship> escorts) {
        float[] ec = centroid(escorts);
        // The far side of the charge from its screen: where the flanker is going.
        float away = WarshipFacing.bearing(ec[0], ec[1], charge.centerX(), charge.centerY());
        Warship flanker = null;
        float best = Float.MAX_VALUE;
        Warship current = null;
        float currentScore = Float.MAX_VALUE;
        for (Warship h : hunters) {
            float round = Math.abs(WarshipFacing.wrap(
                    WarshipFacing.bearing(charge.centerX(), charge.centerY(), h.centerX(), h.centerY()) - away));
            if (round < best) {
                best = round;
                flanker = h;
            }
            if (h.manoeuvre().role == Role.FLANK && h.manoeuvre().target == charge) {
                current = h;
                currentScore = round;
            }
        }
        if (current != null && currentScore <= best + FLANK_SWITCH_DEGREES) flanker = current;
        Map<Warship, Integer> pinned = new IdentityHashMap<>();
        for (Warship h : hunters) {
            WarshipManoeuvre m = h.manoeuvre();
            if (h == flanker) {
                setRole(m, Role.FLANK, charge, h.serial() % 2 == 0 ? 1 : -1, null);
                m.flankGoal = away;
                continue;
            }
            Warship pick = null;
            float pickCost = Float.MAX_VALUE;
            for (Warship e : escorts) {
                float cost = e.distanceTo(h.centerX(), h.centerY()) + 1000f * pinned.getOrDefault(e, 0);
                if (cost < pickCost) {
                    pickCost = cost;
                    pick = e;
                }
            }
            pinned.merge(pick, 1, Integer::sum);
            setRole(m, Role.PIN, pick, h.serial() % 2 == 0 ? 1 : -1, null);
        }
    }

    /**
     * Each hunter takes the hull cheapest for it, a friend already on one making it dearer. With threat targeting on,
     * a hunter set upon by two or more hulls whose own hull has stopped being a threat to it (turned its guns away, or opened past its own reach: {@link
     * #menace} under {@link #LET_GO_MENACE}) chooses again with threat weighed: a hull is cheaper the more of its
     * battery it has laid on the hunter from inside its reach and the less the hunter must turn to bring its guns to
     * bear, and the hull it had keeps only half its keeping bonus. A ship answers the hull in front of it with its guns
     * on and lets one that has turned away go. That is what a pack relay's tank draws, and what a venting partner sheds
     * by turning away; a hull still threatening its chooser is chosen by range as before.
     *
     * @return the hunters on each hull, in hunter order
     */
    private static Map<Warship, List<Warship>> distribute(List<Warship> hunters, List<Warship> enemies,
                                                          boolean threatAware) {
        Map<Warship, List<Warship>> on = new IdentityHashMap<>();
        boolean weighThreat = threatAware && enemies.size() > 1;
        for (Warship h : hunters) {
            float reach = Math.max(1f, WarshipShipFire.longestReach(h));
            Warship pick = null;
            float pickCost = Float.MAX_VALUE;
            // Threat is weighed only by a ship set upon by two or more hulls, and only when the one it has is no
            // longer a threat to it: then it looks for the one that is. Otherwise it chooses by range, as it always
            // did, so a ship in an even fight does not drop the hull it is wearing down for another.
            Warship current = h.manoeuvre().target;
            boolean weigh = weighThreat && current != null && enemies.contains(current)
                    && setUpon(h, enemies) >= 2 && menace(current, h) < LET_GO_MENACE;
            for (Warship e : enemies) {
                int friends = on.getOrDefault(e, List.of()).size();
                boolean focus = enemies.size() == 1 || e.hullFraction() < FOCUS_HULL;
                float threat = weigh ? menace(e, h) : 1f;
                float cost = e.distanceTo(h.centerX(), h.centerY()) / reach
                        + (focus ? 0f : SHARE_PENALTY * friends)
                        - (h.manoeuvre().target == e ? KEEP_BONUS * (1f - KEEP_THREAT_SHARE * (1f - threat)) : 0f);
                if (weigh) cost += THREAT_WEIGHT * (1f - threat) + TURN_WEIGHT * turnToBear(h, e) / 180f;
                if (cost < pickCost) {
                    pickCost = cost;
                    pick = e;
                }
            }
            on.computeIfAbsent(pick, k -> new ArrayList<>()).add(h);
        }
        // Hunters sharing a hull work it from opposite sides.
        for (Map.Entry<Warship, List<Warship>> entry : on.entrySet()) {
            Warship target = entry.getKey();
            int next = 1;
            for (Warship h : entry.getValue()) {
                int side = entry.getValue().size() == 1 && h.manoeuvre().target == target ? h.manoeuvre().side
                        : entry.getValue().size() == 1 ? (h.serial() % 2 == 0 ? 1 : -1) : next;
                // A line and a relay keep their roles from pass to pass; they are re-read below.
                Role r = h.manoeuvre().role;
                Role keep = r == Role.LEAD || r == Role.SECOND || r == Role.TANK || r == Role.STRIKER ? r : Role.FREE;
                setRole(h.manoeuvre(), keep, target, side, h.manoeuvre().leader);
                next = -next;
            }
        }
        // Broadside ships of a group sail in line: the one nearest its own hull leads, the rest follow in its wake,
        // each still firing at the hull the group gave it.
        List<Warship> line = new ArrayList<>();
        for (Warship h : hunters) if (!WarshipDuelHelm.bowFighter(h)) line.add(h);
        if (line.size() < 2) {
            for (Warship h : line) setRole(h.manoeuvre(), Role.FREE, h.manoeuvre().target, h.manoeuvre().side, null);
            return on;
        }
        line.sort((a, b) -> Float.compare(a.manoeuvre().target.distanceTo(a.centerX(), a.centerY()),
                b.manoeuvre().target.distanceTo(b.centerX(), b.centerY())));
        Warship lead = null;
        for (Warship h : line) if (h.manoeuvre().role == Role.LEAD) lead = h;
        if (lead == null) lead = line.get(0);
        WarshipManoeuvre lm = lead.manoeuvre();
        setRole(lm, Role.LEAD, lm.target, lm.side, null);
        Warship ahead = lead;
        for (Warship h : line) {
            if (h == lead) continue;
            WarshipManoeuvre m = h.manoeuvre();
            setRole(m, Role.SECOND, m.target, lm.side, ahead);
            ahead = h;
        }
        return on;
    }

    /**
     * How much of a threat {@code e} is to {@code to}: the share of its anti-ship battery it has laid on {@code to} at
     * its present heading, when {@code to} is inside its longest reach. Short of its reach (out to {@link
     * WarshipShipFire#NOTICE_REACH_SHARE} of it) a hull closing with its guns laid is an incoming threat, counted at
     * up to {@link #INCOMING_MENACE} of that share by how much of its top speed it is closing at; a hull opening the
     * range, or further off, is none.
     */
    static float menace(Warship e, Warship to) {
        float reach = WarshipShipFire.longestReach(e);
        float range = e.distanceTo(to.centerX(), to.centerY());
        if (reach <= 0f || range > reach * WarshipShipFire.NOTICE_REACH_SHARE) return 0f;
        float best = Math.max(firepowerOff(e, 0f), firepowerOff(e, 90f));
        if (best <= 0f) return 0f;
        float laid = Math.min(1f, WarshipShipRules.firepowerAt(e, e.facingDegrees, to.centerX(), to.centerY()) / best);
        if (range <= reach * IN_REACH_SHARE) return laid;
        float dx = to.centerX() - e.centerX();
        float dy = to.centerY() - e.centerY();
        float len = Math.max(1e-3f, (float) Math.hypot(dx, dy));
        float closing = ((e.velocityX - to.velocityX) * dx + (e.velocityY - to.velocityY) * dy) / len;
        float share = Math.max(0f, Math.min(1f, closing / Math.max(1e-3f, e.maxSpeed)));
        return INCOMING_MENACE * laid * share;
    }

    /** How many of {@code enemies} have {@code h} as the hull their group gave them to fight. */
    static int setUpon(Warship h, List<Warship> enemies) {
        int n = 0;
        for (Warship e : enemies) if (e.manoeuvre().target == h) n++;
        return n;
    }

    /** How far, in degrees, {@code h} must turn from its heading to the one that brings its guns to bear on {@code e}. */
    static float turnToBear(Warship h, Warship e) {
        float[] aim = WarshipShipRules.aimPoint(h, e);
        return Math.abs(WarshipFacing.wrap(WarshipShipRules.bestHeading(h, aim[0], aim[1]) - h.facingDegrees));
    }

    /**
     * The pack relay, once a second, over the hunters a hull has drawn: two or more bow ships on one hull share it as
     * a <b>tank</b>, which holds the target's attention from nearest and nose-on with its shield on it and fires only
     * as its flux allows, and <b>strikers</b>, which work it from its quarter at full fire. The ship with the most
     * flux to spare holds the target; when the tank's pool runs high and a partner is fresh, they swap: the partner
     * closes to catch the target's eye and the old tank, once the partner is in place, turns away, opens past the
     * target's reach to vent with its shield down, and comes back as a striker. A striker running high breaks off to
     * vent the same way while the tank holds. Broadside ships in a line keep their line. Nothing is read but the hulls'
     * own pools, reach and bearings, and the target's own choice of whom to fight.
     */
    private void relay(float now, Warship target, List<Warship> on) {
        List<Warship> pack = new ArrayList<>();
        for (Warship h : on) {
            if (WarshipDuelHelm.bowFighter(h) && h.manoeuvre().role != Role.LEAD && h.manoeuvre().role != Role.SECOND) {
                pack.add(h);
            }
        }
        if (!packRelay || pack.size() < 2) {
            for (Warship h : on) {
                WarshipManoeuvre m = h.manoeuvre();
                if (m.role == Role.TANK || m.role == Role.STRIKER) relayRole(m, Role.FREE, now);
            }
            return;
        }
        Warship tank = null;
        for (Warship h : pack) if (h.manoeuvre().role == Role.TANK) tank = h;
        if (tank == null) {
            // The first tank is the hull the target already has in its sights, else the one with the most flux to
            // spare, nearer first: whoever is drawing the fire holds it. A ship out venting is never it.
            Warship sighted = target.hullTarget();
            if (sighted != null && pack.contains(sighted) && !sighted.manoeuvre().relayVenting) {
                tank = sighted;
            } else {
                float best = Float.MAX_VALUE;
                for (Warship h : pack) {
                    if (h.manoeuvre().relayVenting) continue;
                    float score = h.fluxFraction() + 0.1f * target.distanceTo(h.centerX(), h.centerY())
                            / Math.max(1f, WarshipShipFire.longestReach(h));
                    if (score < best) {
                        best = score;
                        tank = h;
                    }
                }
            }
        } else if (now - tank.manoeuvre().relaySince >= RELAY_MIN_SECONDS && tank.fluxFraction() >= SWAP_FLUX) {
            // A fresh partner takes over: low in flux and inside its own reach of the target, so it can catch the
            // target's eye at once rather than leave nobody holding it while it closes.
            Warship fresh = null;
            for (Warship h : pack) {
                if (h == tank || h.manoeuvre().relayVenting || h.overloaded() || h.fluxFraction() > READY_FLUX
                        || target.distanceTo(h.centerX(), h.centerY())
                        > WarshipShipFire.longestReach(h) * IN_REACH_SHARE) {
                    continue;
                }
                if (fresh == null || h.fluxFraction() < fresh.fluxFraction()) fresh = h;
            }
            WarshipManoeuvre old = tank.manoeuvre();
            if (fresh != null) {
                relayRole(old, Role.STRIKER, now);
                beginVent(old, now, fresh);
                relayRole(fresh.manoeuvre(), Role.TANK, now);
                fresh.manoeuvre().relaySwaps++;
                tank = fresh;
            } else if (tank.fluxFraction() >= HANDOVER_DANGER_FLUX) {
                // Nobody fresh to take it over and the shield going: it breaks off to vent all the same, and the
                // partner with the most to spare, if any is not venting itself, takes the target as best it can.
                relayRole(old, Role.STRIKER, now);
                beginVent(old, now, null);
                Warship next = null;
                for (Warship h : pack) {
                    if (h.manoeuvre().relayVenting || h.overloaded()) continue;
                    if (next == null || h.fluxFraction() < next.fluxFraction()) next = h;
                }
                old.relayPartner = next;
                if (next != null) {
                    relayRole(next.manoeuvre(), Role.TANK, now);
                    next.manoeuvre().relaySwaps++;
                }
                tank = next;
            }
        }
        for (Warship h : pack) {
            WarshipManoeuvre m = h.manoeuvre();
            if (h == tank) {
                relayRole(m, Role.TANK, now);
                continue;
            }
            relayRole(m, Role.STRIKER, now);
            if (tank != null) m.relayPartner = tank;
            if (!m.relayVenting && h.fluxFraction() >= STRIKER_VENT_FLUX) beginVent(m, now, tank);
        }
    }

    /** Gives {@code m} a relay role (or none), starting its manoeuvre afresh only when the role changes. */
    private static void relayRole(WarshipManoeuvre m, Role role, float now) {
        if (m.role == role) return;
        m.role = role;
        m.kind = null;
        m.relaySince = now;
        m.relayVenting = false;
        m.relayPartner = null;
    }

    /** Sends a relay ship off to vent, its partner left holding the target. */
    private static void beginVent(WarshipManoeuvre m, float now, Warship partner) {
        m.relayVenting = true;
        m.relayVents++;
        m.relayPartner = partner;
        m.begin(Kind.VENT, now, Float.NaN);
    }

    private static void setRole(WarshipManoeuvre m, Role role, Warship target, int side, Warship leader) {
        if (m.target != target || m.role != role) m.kind = null;
        m.flankGoal = Float.NaN;
        m.role = role;
        m.target = target;
        m.side = side;
        m.leader = leader;
    }

    private static float[] centroid(List<Warship> ships) {
        float x = 0f;
        float y = 0f;
        for (Warship s : ships) {
            x += s.centerX();
            y += s.centerY();
        }
        return new float[]{x / Math.max(1, ships.size()), y / Math.max(1, ships.size())};
    }

    // ------------------------------------------------------------------------------------------------ the machine

    /**
     * Flies one tick of {@code ship}'s manoeuvre against {@code target}: ends it on its own conditions and begins the
     * next, and returns the station the helm should fly to (the ship's heading is left in {@link
     * WarshipManoeuvre#heading}). {@code null} when the ship has no manoeuvre, so the single-ship duel helm stands.
     */
    float[] fly(Warship ship, Warship target, float now, WarshipEdgeHelm edges, List<Warship> ships) {
        WarshipManoeuvre m = ship.manoeuvre();
        m.heading = Float.NaN;
        m.speedShare = 1f;
        if (!ENABLED || m.role == Role.NONE || m.target != target) return null;
        // A ship fights where its battery bears, not only its longest gun; it clears the target's longest gun.
        float reach = Math.max(1f, WarshipDuelHelm.batteryReach(ship));
        float theirs = WarshipShipFire.longestReach(target);
        float range = (float) Math.hypot(target.centerX() - ship.centerX(), target.centerY() - ship.centerY());
        float standoff = WarshipDuelHelm.preferredRange(reach, WarshipDuelHelm.batteryReach(target),
                0.5f * ship.lengthCells, 0.5f * target.lengthCells);
        boolean bow = WarshipDuelHelm.bowFighter(ship);
        float toTarget = WarshipFacing.bearing(ship.centerX(), ship.centerY(), target.centerX(), target.centerY());

        if (m.role == Role.SECOND && m.leader != null && !m.leader.destroyed() && !m.leader.departed()) {
            if (m.kind != Kind.LINE_AHEAD) m.begin(Kind.LINE_AHEAD, now, Float.NaN);
            return lineAhead(ship, m);
        }
        if (m.role == Role.TANK || m.role == Role.STRIKER) {
            return flyRelay(ship, target, m, now, toTarget, range, standoff, reach, edges);
        }
        if (m.kind == null || m.kind == Kind.LINE_AHEAD) {
            Kind first = m.role == Role.FLANK ? Kind.FLANK : range > reach * IN_REACH_SHARE ? Kind.APPROACH : run(bow);
            m.begin(first, now, first == Kind.BROADSIDE_RUN ? broadsideCourse(ship, toTarget, m) : Float.NaN);
        }
        float seconds = now - m.startedAt;
        switch (m.kind) {
            case APPROACH -> {
                if (range <= reach * IN_REACH_SHARE) {
                    Kind next = run(bow);
                    m.begin(next, now, next == Kind.BROADSIDE_RUN ? broadsideCourse(ship, toTarget, m) : Float.NaN);
                }
            }
            case FLANK -> {
                float goal = flankGoal(target, m);
                float round = WarshipFacing.bearing(target.centerX(), target.centerY(), ship.centerX(), ship.centerY());
                if (Math.abs(WarshipFacing.wrap(round - goal)) <= FLANK_DONE_DEGREES || seconds >= FLANK_MAX_SECONDS) {
                    Kind next = run(bow);
                    m.begin(next, now, next == Kind.BROADSIDE_RUN ? broadsideCourse(ship, toTarget, m) : Float.NaN);
                }
            }
            case ATTACK_RUN, CROSS -> {
                // A cross is a run begun on a target that has turned its cover or its battery away; it ends as a run
                // does. A front-shielded ship never turns its back in reach while its shield holds: its run gives way to an
                // orbit (or, as the control, a standoff), shield and guns still on. It presses while its target is near
                // its limit or has turned its cover away. A hull with no front to keep (an omni shield, or none) has
                // nothing to lose by coming about, so it turns away across the target's bow, opens the range and comes
                // back for another run.
                boolean front = frontShielded(ship) || !UNSHIELDED_COME_ABOUT;
                boolean full = ship.fluxFraction() >= RUN_FLUX_LIMIT;
                if (front && m.duel) {
                    if (shieldGoing(ship, target)) {
                        // The shield is going or gone: nothing left to keep the bow on for, so it breaks away at
                        // full way, and its quarter is what the other ship crosses.
                        beginBreak(ship, target, m, now, toTarget);
                    } else {
                        // It holds the run on a target whose cover is turned away or whose shield has gone, and on a
                        // target near its limit while it has flux to spend or is ahead in the race. Equally full, it
                        // circles off to vent with its shield still on, rather than parking nose to nose.
                        boolean pressing = target.overloaded() || offArc(target, ship) || (pressed(target)
                                && (!full || target.fluxFraction() >= ship.fluxFraction() + PRESS_LEAD));
                        boolean close = range <= standoff * 1.1f && seconds >= RUN_MIN_SECONDS
                                && ship.fluxFraction() >= STANDOFF_FLUX;
                        if (!pressing && seconds >= RUN_MIN_SECONDS && (full || close || seconds >= RUN_MAX_SECONDS)) {
                            beginOrbit(ship, target, m, now, toTarget);
                        }
                    }
                    break;
                }
                boolean pressing = (pressed(target) || (m.duel && offArc(target, ship))) && !full;
                boolean close = range <= standoff * 1.1f && seconds >= RUN_MIN_SECONDS
                        && (!front || ship.fluxFraction() >= STANDOFF_FLUX);
                if (full || (!pressing && (close || seconds >= RUN_MAX_SECONDS))) {
                    m.side = -m.side;
                    if (front) {
                        m.begin(Kind.STANDOFF, now, Float.NaN);
                        m.venting = full;
                    } else {
                        m.begin(Kind.EXTEND, now, WarshipFacing.wrap(toTarget + m.side * EXTEND_TURN_DEGREES));
                    }
                }
            }
            case ORBIT -> {
                if (shieldGoing(ship, target)) {
                    beginBreak(ship, target, m, now, toTarget);
                } else if (range > reach * IN_REACH_SHARE) {
                    m.begin(Kind.APPROACH, now, Float.NaN);
                } else if (seconds >= CROSS_AFTER_SECONDS && offArc(target, ship)) {
                    // The target has let this ship off its cover: turn in across its T.
                    m.begin(Kind.CROSS, now, Float.NaN);
                } else if (seconds >= CROSS_AFTER_SECONDS && mainBears(target, ship) && !mainBears(ship, target)) {
                    // The target has turned its battery in on this ship: turn in to answer it, the counter.
                    m.begin(Kind.ATTACK_RUN, now, Float.NaN);
                } else if (seconds >= TURN_IN_AFTER_SECONDS && !mainBears(target, ship)
                        && ship.fluxFraction() < RUN_FLUX_LIMIT && advantage(ship, target) >= TURN_IN_FLUX_LEAD) {
                    // The target's battery is off this ship and this ship has the more flux to spend: turn in first,
                    // laying its own battery on the target before the target can lay its own. Crossing the T.
                    m.begin(Kind.CROSS, now, Float.NaN);
                } else if (seconds >= ORBIT_MIN_SECONDS && (ship.fluxFraction() <= VENTED_FLUX
                        || seconds >= ORBIT_MAX_SECONDS || range < standoff * ORBIT_CLOSE_SHARE)) {
                    m.begin(Kind.ATTACK_RUN, now, Float.NaN);
                }
            }
            case BREAK -> {
                boolean vented = ship.fluxFraction() <= VENTED_FLUX;
                boolean clear = range >= theirs * VENT_CLEAR_SHARE;
                // Run down by a target whose guns bear, a ship that has vented enough turns to face it.
                boolean counter = ship.fluxFraction() <= COUNTER_FLUX && offArc(ship, target)
                        && bearsOn(target, ship);
                if (!ship.overloaded()
                        && ((seconds >= BREAK_MIN_SECONDS && (vented || clear || counter)) || seconds >= BREAK_MAX_SECONDS)) {
                    // Back the short way: through the side it broke from.
                    m.side = -m.side;
                    m.begin(Kind.COME_ABOUT, now, Float.NaN);
                }
            }
            case STANDOFF -> {
                if (m.duel && shieldGoing(ship, target)) {
                    beginBreak(ship, target, m, now, toTarget);
                    break;
                }
                if (m.duel && seconds >= CROSS_AFTER_SECONDS && offArc(target, ship)) {
                    m.begin(Kind.CROSS, now, Float.NaN);
                    break;
                }
                float open = standoffRange(standoff, reach);
                // A pool that fills during the drift turns it into a vent: out past the target's reach until it is low.
                if (ship.fluxFraction() >= RUN_FLUX_LIMIT) m.venting = true;
                boolean vented = ship.fluxFraction() <= VENTED_FLUX;
                boolean done = m.venting ? vented
                        : seconds >= STANDOFF_MIN_SECONDS && (range >= open * 0.95f || pressed(target)
                        || seconds >= STANDOFF_MAX_SECONDS);
                if (done) {
                    Kind next = range > reach * IN_REACH_SHARE ? Kind.APPROACH : Kind.ATTACK_RUN;
                    m.begin(next, now, Float.NaN);
                }
            }
            case BROADSIDE_RUN -> {
                float relative = Math.abs(WarshipFacing.wrap(toTarget - ship.facingDegrees));
                boolean passed = seconds >= RUN_MIN_SECONDS && (relative > 140f || relative < 40f);
                boolean flux = ship.fluxFraction() >= RUN_FLUX_LIMIT;
                if (flux) {
                    // A full pool: open the range across the target's bow or stern before coming about to vent.
                    m.begin(Kind.EXTEND, now, WarshipFacing.wrap(toTarget + m.side * EXTEND_TURN_DEGREES));
                } else if (passed || seconds >= BROADSIDE_RUN_SECONDS) {
                    // Wear round: turn away from the target, through the stern, onto the other broadside.
                    m.side = -m.side;
                    m.begin(Kind.COME_ABOUT, now, Float.NaN);
                }
            }
            case EXTEND -> {
                boolean open = range >= Math.max(theirs * IN_REACH_SHARE, reach * 0.9f) && seconds >= EXTEND_MIN_SECONDS;
                // A bow ship in a duel comes back early: to answer a target that has turned its battery in on it (the
                // counter), or first, while the target's battery is off it and it has the advantage (crossing the T).
                boolean early = m.duel && bow && seconds >= EXTEND_MIN_SECONDS
                        && ship.fluxFraction() <= VENTED_FLUX
                        && ((mainBears(target, ship) && !mainBears(ship, target))
                        || (!mainBears(target, ship) && advantage(ship, target) >= TURN_IN_FLUX_LEAD));
                if (open || early || seconds >= EXTEND_MAX_SECONDS) {
                    if (m.duel && bow) m.side = shortSide(ship, toTarget);
                    m.begin(Kind.COME_ABOUT, now, Float.NaN);
                }
            }
            case COME_ABOUT -> {
                float want = bow ? toTarget : broadsideCourse(ship, toTarget, m);
                if (Math.abs(WarshipFacing.wrap(want - ship.facingDegrees)) <= COME_ABOUT_DONE_DEGREES
                        || seconds >= COME_ABOUT_MAX_SECONDS) {
                    Kind next = range > reach * IN_REACH_SHARE * 1.3f ? Kind.APPROACH : run(bow);
                    m.begin(next, now, next == Kind.BROADSIDE_RUN ? want : Float.NaN);
                }
            }
            default -> { }
        }
        if (m.role == Role.LEAD) m.speedShare = lineClosedUp(ship, ships) ? LEAD_SPEED_SHARE : LEAD_WAIT_SPEED_SHARE;
        return execute(ship, target, m, toTarget, range, standoff, reach, bow, edges);
    }

    /**
     * One tick of a pack relay ship's manoeuvre. A tank holds the target's attention: on the bearing round it where
     * its battery bears most, weighed heavily against flying round, at {@link #TANK_RANGE_SHARE} of the standoff, nose
     * and shield on. A striker works the target's quarter at the standoff, round from its tank. A venting ship holds
     * fire: while its partner is still closing to take over it stays nose-on, then turns away and runs to past the
     * target's reach, and holds out there, shield down, until its pool is low; then it comes back as a striker.
     */
    private static float[] flyRelay(Warship ship, Warship target, WarshipManoeuvre m, float now, float toTarget,
                                    float range, float standoff, float reach, WarshipEdgeHelm edges) {
        boolean far = range > reach * IN_REACH_SHARE;
        if (m.role == Role.TANK) {
            if (m.kind != Kind.HOLD_AGGRO) m.begin(Kind.HOLD_AGGRO, now, Float.NaN);
            float clear = 0.5f * ship.lengthCells + 0.5f * target.lengthCells + WarshipDuelHelm.CLEARANCE_CELLS;
            float r = Math.max(clear, TANK_RANGE_SHARE * standoff);
            float[] p = aggroStation(ship, target, r, edges);
            m.heading = far ? WarshipFacing.bearing(ship.centerX(), ship.centerY(), p[0], p[1]) : Float.NaN;
            return cog(ship, p);
        }
        Warship partner = m.relayPartner;
        if (m.relayVenting) {
            if (m.kind != Kind.VENT) m.begin(Kind.VENT, now, Float.NaN);
            if (ship.fluxFraction() <= VENTED_FLUX && !ship.overloaded()) {
                m.relayVenting = false;
            } else {
                return vent(ship, target, m, now, toTarget, range, partner, edges);
            }
        }
        if (m.kind != Kind.STRIKE) m.begin(Kind.STRIKE, now, Float.NaN);
        float avoid = partner == null || partner.destroyed() || partner.departed() ? Float.NaN
                : WarshipFacing.bearing(target.centerX(), target.centerY(), partner.centerX(), partner.centerY());
        float[] p = WarshipDuelHelm.bowStation(ship, target, standoff, edges, avoid, STRIKER_SEPARATION_DEGREES,
                STRIKER_SEPARATION_WEIGHT);
        m.heading = far ? WarshipFacing.bearing(ship.centerX(), ship.centerY(), p[0], p[1]) : Float.NaN;
        return cog(ship, p);
    }

    /** A venting relay ship's station this tick (see {@link #flyRelay}). */
    private static float[] vent(Warship ship, Warship target, WarshipManoeuvre m, float now, float toTarget,
                                float range, Warship partner, WarshipEdgeHelm edges) {
        float out = WarshipShipFire.longestReach(target) * VENT_HOLD_SHARE;
        float round = WarshipFacing.bearing(target.centerX(), target.centerY(), ship.centerX(), ship.centerY());
        boolean partnerLive = partner != null && !partner.destroyed() && !partner.departed();
        if (Float.isNaN(m.course)) {
            // Handing over: the partner closes to take the target's eye while this ship keeps its nose and shield on,
            // holding its fire. Once the partner is in place (in reach, nearer the target, its battery on it), or
            // the handover has run long, this ship turns away.
            boolean inPlace = !partnerLive || (target.distanceTo(partner.centerX(), partner.centerY()) < range
                    && mainBears(partner, target) && target.distanceTo(partner.centerX(), partner.centerY())
                    <= WarshipShipFire.longestReach(partner));
            if (!inPlace && m.seconds(now) < HANDOVER_MAX_SECONDS && range < out
                    && ship.fluxFraction() < HANDOVER_DANGER_FLUX && !ship.overloaded()) {
                // Backing off as a standoff's vent does: astern and to one side, nose, guns and shield on the target.
                float at = round + m.side * VENT_SLIDE_DEGREES;
                m.heading = Float.NaN;
                return cog(ship, edges.settle(ship, target.centerX() + nx(at) * out, target.centerY() + ny(at) * out));
            }
            float away = round;
            if (partnerLive) {
                float pb = WarshipFacing.bearing(target.centerX(), target.centerY(), partner.centerX(), partner.centerY());
                away = WarshipFacing.wrap(round - Math.signum(WarshipFacing.wrap(pb - round)) * VENT_OFFSET_DEGREES);
            }
            m.course = ventCourse(ship, target, away, out, edges);
        } else if (ventRoom(ship, target, m.course, out, edges) < VENT_ROOM_SHARE * out) {
            // The target has moved and the course out now runs into an edge's margin: it takes the clearest again.
            float again = ventCourse(ship, target, m.course, out, edges);
            if (ventRoom(ship, target, again, out, edges) > ventRoom(ship, target, m.course, out, edges)) m.course = again;
        }
        // Where it holds: out along its course past the target's reach, or as far out as the map allows inside its
        // margin. A small map vents short of full reach rather than pinning the ship against its edge.
        float[] hold = edges.settle(ship, target.centerX() + nx(m.course) * Math.max(out, range),
                target.centerY() + ny(m.course) * Math.max(out, range));
        float holdRange = target.distanceTo(hold[0], hold[1]);
        if (range < holdRange - VENT_ARRIVE_CELLS) {
            // Running out at full way toward the hold, along the course it took.
            m.heading = WarshipFacing.bearing(ship.centerX(), ship.centerY(), hold[0], hold[1]);
            return cog(ship, hold);
        }
        // Out at the hold: nose back toward the fight.
        m.heading = Float.NaN;
        return cog(ship, hold);
    }

    /**
     * How far out from {@code target} a venting ship can hold along {@code course} toward {@code out}, inside its
     * margin: the distance of that hold once {@link WarshipEdgeHelm#settle settled}.
     */
    static float ventRoom(Warship ship, Warship target, float course, float out, WarshipEdgeHelm edges) {
        float[] p = edges.settle(ship, target.centerX() + nx(course) * out, target.centerY() + ny(course) * out);
        return target.distanceTo(p[0], p[1]);
    }

    /**
     * The course a venting ship runs out on: {@code away} if the map has room along it to reach {@code out} inside
     * the ship's margin, else the course within {@link #VENT_COURSE_SWING_DEGREES} of it with the most room, the
     * nearer to {@code away} on a tie, so a ship venting toward an edge turns along it rather than into it.
     */
    static float ventCourse(Warship ship, Warship target, float away, float out, WarshipEdgeHelm edges) {
        float best = away;
        float bestScore = -Float.MAX_VALUE;
        int steps = Math.round(VENT_COURSE_SWING_DEGREES / VENT_COURSE_STEP_DEGREES);
        for (int k = -steps; k <= steps; k++) {
            float course = WarshipFacing.wrap(away + k * VENT_COURSE_STEP_DEGREES);
            float room = Math.min(out, ventRoom(ship, target, course, out, edges)) / Math.max(1f, out);
            float score = room - VENT_COURSE_SWING_COST * Math.abs(k) / Math.max(1, steps);
            if (score > bestScore) {
                bestScore = score;
                best = course;
            }
        }
        return best;
    }

    /**
     * A tank's station: of the bearings round the target at {@code range}, the one where the target's anti-ship
     * battery bears most at its present heading, less {@link #TANK_TRAVEL_WEIGHT} for each range's worth of flying to
     * get there, so a tank closes to the front of the target from where it is rather than circling to it.
     */
    static float[] aggroStation(Warship ship, Warship target, float range, WarshipEdgeHelm edges) {
        float tx = target.centerX();
        float ty = target.centerY();
        int n = WarshipDuelHelm.BOW_BEARINGS;
        float[][] at = new float[n][];
        float[] power = new float[n];
        float most = 0f;
        for (int i = 0; i < n; i++) {
            double a = 2.0 * Math.PI * i / n;
            at[i] = edges.settle(ship, tx + (float) Math.cos(a) * range, ty + (float) Math.sin(a) * range);
            power[i] = WarshipShipRules.firepowerAt(target, target.facingDegrees, at[i][0], at[i][1]);
            most = Math.max(most, power[i]);
        }
        float[] best = at[0];
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float travel = (float) Math.hypot(at[i][0] - ship.centerX(), at[i][1] - ship.centerY()) / Math.max(1f, range);
            float score = (most > 0f ? 1f - power[i] / most : 0f) + TANK_TRAVEL_WEIGHT * travel;
            if (score < bestScore) {
                bestScore = score;
                best = at[i];
            }
        }
        return best;
    }

    /** Whether every ship following {@code lead} in its line is within half an interval of its place. */
    private static boolean lineClosedUp(Warship lead, List<Warship> ships) {
        for (Warship s : ships) {
            WarshipManoeuvre m = s.manoeuvre();
            if (m.role != Role.SECOND || s.destroyed() || s.departed()) continue;
            Warship ahead = m.leader;
            if (ahead == null) continue;
            float interval = lineInterval(ahead, s);
            float back = WarshipFacing.wrap(ahead.facingDegrees + 180f);
            float sx = ahead.centerX() + nx(back) * interval;
            float sy = ahead.centerY() + ny(back) * interval;
            if (Math.hypot(sx - s.centerX(), sy - s.centerY()) > 0.5f * interval) return false;
        }
        return true;
    }

    static float lineInterval(Warship ahead, Warship ship) {
        return 0.5f * ahead.lengthCells + 0.5f * ship.lengthCells + LINE_GAP_SHARE * WarshipEscort.GAP_CELLS;
    }

    private static Kind run(boolean bow) {
        return bow ? Kind.ATTACK_RUN : Kind.BROADSIDE_RUN;
    }

    /** The station for this tick of the manoeuvre, with the heading left on {@code m}. */
    private static float[] execute(Warship ship, Warship target, WarshipManoeuvre m, float toTarget, float range,
                                   float standoff, float reach, boolean bow, WarshipEdgeHelm edges) {
        float cruise = CRUISE_LENGTHS * ship.lengthCells;
        switch (m.kind) {
            case APPROACH -> {
                if (bow && m.duel) {
                    // A bow ship does not come in on the target's nose: it steers for the quarter where the target's
                    // guns and shield cover least, along its own course, and turns its guns in once it is in reach.
                    // Two such ships work round one another for the T before either fires.
                    float[] p = WarshipDuelHelm.bowStation(ship, target, standoff, edges);
                    m.heading = WarshipFacing.bearing(ship.centerX(), ship.centerY(), p[0], p[1]);
                    return cog(ship, p);
                }
                // Oblique: toward a point on the target's side the ship works, at its standoff.
                float round = WarshipFacing.bearing(target.centerX(), target.centerY(), ship.centerX(), ship.centerY());
                float at = round + m.side * APPROACH_OBLIQUE_DEGREES;
                float[] p = edges.settle(ship, target.centerX() + nx(at) * standoff, target.centerY() + ny(at) * standoff);
                m.heading = bow ? Float.NaN : WarshipFacing.bearing(ship.centerX(), ship.centerY(), p[0], p[1]);
                return cog(ship, p);
            }
            case FLANK -> {
                float goal = flankGoal(target, m);
                float round = WarshipFacing.bearing(target.centerX(), target.centerY(), ship.centerX(), ship.centerY());
                float step = Math.max(-FLANK_STEP_DEGREES, Math.min(FLANK_STEP_DEGREES, WarshipFacing.wrap(goal - round)));
                float radius = Math.max(reach * FLANK_RADIUS_REACH_SHARE, range);
                float at = round + step;
                float[] p = edges.settle(ship, target.centerX() + nx(at) * radius, target.centerY() + ny(at) * radius);
                m.heading = WarshipFacing.bearing(ship.centerX(), ship.centerY(), p[0], p[1]);
                return cog(ship, p);
            }
            case ATTACK_RUN, CROSS -> {
                // Nose and guns on the target, closing to the standoff on the quiet side it works.
                float[] p = WarshipDuelHelm.bowStation(ship, target, standoff, edges);
                m.heading = Float.NaN;
                return cog(ship, p);
            }
            case ORBIT -> {
                // Round the target with way on: held off the bow at the orbit's angle, bent out when the range is
                // short and in when it is long, never past the ship's own cover.
                float bend = Math.max(-ORBIT_BEND_DEGREES, Math.min(ORBIT_BEND_DEGREES,
                        (standoff - range) / Math.max(1f, standoff) * 60f));
                // Short of the range it bends out only as far as its cover still holds; long, it bends in.
                float angle = Math.max(ORBIT_MIN_ANGLE_DEGREES - ORBIT_BEND_DEGREES,
                        Math.min(Math.max(m.orbitAngle, coverAngle(ship)), m.orbitAngle + bend));
                float c = WarshipFacing.wrap(toTarget + m.side * angle);
                m.heading = c;
                return ahead(ship, c, cruise, edges);
            }
            case BREAK -> {
                m.heading = m.course;
                return ahead(ship, m.course, cruise, edges);
            }
            case BROADSIDE_RUN -> {
                float c = broadsideCourse(ship, toTarget, m);
                // Hold the range: bend in when long, out when short.
                float bend = Math.max(-RANGE_BEND_DEGREES, Math.min(RANGE_BEND_DEGREES,
                        (range - standoff) / Math.max(1f, standoff) * 60f));
                c = WarshipFacing.wrap(c + bendToward(c, toTarget) * bend);
                m.heading = c;
                return ahead(ship, c, cruise, edges);
            }
            case EXTEND -> {
                m.heading = m.course;
                return ahead(ship, m.course, cruise, edges);
            }
            case STANDOFF -> {
                // Astern and to one side, round the target, the nose left to the guns: a drift, not a turn away.
                // Venting, it opens past the target's own reach; otherwise it stays inside its own and keeps firing.
                boolean clear = m.venting;
                float open = clear ? Math.max(standoffRange(standoff, reach),
                        WarshipShipFire.longestReach(target) * VENT_CLEAR_SHARE) : standoffRange(standoff, reach);
                float round = WarshipFacing.bearing(target.centerX(), target.centerY(), ship.centerX(), ship.centerY());
                float at = round + m.side * (clear ? VENT_SLIDE_DEGREES : STANDOFF_SLIDE_DEGREES);
                float[] p = edges.settle(ship, target.centerX() + nx(at) * open, target.centerY() + ny(at) * open);
                m.heading = Float.NaN;
                return cog(ship, p);
            }
            case COME_ABOUT -> {
                float want = bow ? toTarget : broadsideCourse(ship, toTarget, m);
                float left = WarshipFacing.wrap(want - ship.facingDegrees);
                // Through the side the manoeuvre named, however short the other way round is.
                float through = m.side > 0 ? (left < 0f ? left + 360f : left) : (left > 0f ? left - 360f : left);
                float lead = Math.signum(through) * Math.min(TURN_LEAD_DEGREES, Math.abs(through));
                m.heading = WarshipFacing.wrap(ship.facingDegrees + lead);
                return ahead(ship, m.heading, 0.5f * cruise, edges);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * {@code -Dbattle.warship.unshieldedComeAbout=false} is the control: a bow ship with no front shield ends its run in
     * a standoff, as a front-shielded one does, instead of coming about.
     */
    static final boolean UNSHIELDED_COME_ABOUT =
            !"false".equalsIgnoreCase(System.getProperty("battle.warship.unshieldedComeAbout"));

    /** Whether {@code ship} carries a front shield, the one kind of hull that must keep its bow to the enemy. */
    static boolean frontShielded(Warship ship) {
        return "front".equals(ship.shieldType());
    }

    /**
     * Begins an orbit: the angle is the widest that keeps the target inside the ship's cover, given up by as much as
     * {@link #ORBIT_GUN_GIVE_DEGREES} where that keeps part of the battery bearing, and the ship circles the way that
     * carries it further round from the target's nose, toward its quarter. A tie (two ships nose to nose) turns the
     * same hand for both, so a pair circles round one another rather than mirroring.
     */
    private static void beginOrbit(Warship ship, Warship target, WarshipManoeuvre m, float now, float toTarget) {
        float angle = orbitAngle(ship);
        if (Float.isNaN(angle)) {
            // Nothing worth keeping bears off the bow: hold it on, drifting sideways and astern round the target.
            m.side = -m.side;
            m.begin(Kind.STANDOFF, now, Float.NaN);
            return;
        }
        float plus = aspectAfter(ship, target, WarshipFacing.wrap(toTarget + angle));
        float minus = aspectAfter(ship, target, WarshipFacing.wrap(toTarget - angle));
        m.side = minus > plus + 1f ? -1 : 1;
        m.begin(Kind.ORBIT, now, Float.NaN);
        m.orbitAngle = angle;
    }

    /**
     * Begins a break: a fixed course well off the bearing to the target, on the side the ship is already turned
     * toward so the turn is the short one, as far round as its shield arc still covers the target (from {@link
     * #BREAK_MIN_DEGREES} to {@link #BREAK_MAX_DEGREES}).
     */
    private static void beginBreak(Warship ship, Warship target, WarshipManoeuvre m, float now, float toTarget) {
        float angle = Math.max(BREAK_MIN_DEGREES, Math.min(BREAK_MAX_DEGREES,
                frontShielded(ship) ? 0.5f * ship.shieldArcDegrees() : 0f));
        float off = WarshipFacing.wrap(ship.facingDegrees - toTarget);
        if (Math.abs(off) > 2f) m.side = off > 0f ? 1 : -1;
        m.begin(Kind.BREAK, now, WarshipFacing.wrap(toTarget + m.side * angle));
        m.venting = true;
    }

    /** How far from {@code target}'s nose {@code ship} would lie after a few lengths on {@code course}, in degrees. */
    private static float aspectAfter(Warship ship, Warship target, float course) {
        float step = 2f * ship.lengthCells;
        float x = ship.centerX() + nx(course) * step;
        float y = ship.centerY() + ny(course) * step;
        float round = WarshipFacing.bearing(target.centerX(), target.centerY(), x, y);
        return Math.abs(WarshipFacing.wrap(round - target.facingDegrees));
    }

    /**
     * The widest angle off the bow at which {@code ship} keeps a target covered: inside its front shield's arc by
     * {@link #ORBIT_ARC_MARGIN_DEGREES}, or the beam for a hull with none; never under {@link
     * #ORBIT_MIN_ANGLE_DEGREES} nor past {@link #ORBIT_MAX_ANGLE_DEGREES}.
     */
    static float coverAngle(Warship ship) {
        float cover = frontShielded(ship) ? 0.5f * ship.shieldArcDegrees() - ORBIT_ARC_MARGIN_DEGREES
                : ORBIT_MAX_ANGLE_DEGREES;
        return Math.max(ORBIT_MIN_ANGLE_DEGREES, Math.min(ORBIT_MAX_ANGLE_DEGREES, cover));
    }

    /**
     * The angle off the bow an orbit holds its target at: {@link #coverAngle}, or the widest angle up to
     * {@link #ORBIT_GUN_GIVE_DEGREES} inside it at which at least {@link #ORBIT_FIRE_SHARE} of the bow battery still
     * bears on one side or the other. Read off the mounts, so a hull with beam guns circles firing them.
     */
    static float orbitAngle(Warship ship) {
        float cover = coverAngle(ship);
        float bow = firepowerOff(ship, 0f);
        if (bow <= 0f) return Float.NaN;
        for (float a = cover; a >= Math.max(ORBIT_MIN_ANGLE_DEGREES, cover - ORBIT_GUN_GIVE_DEGREES); a -= 5f) {
            if (firepowerOff(ship, a) >= ORBIT_FIRE_SHARE * bow) return a;
        }
        return Float.NaN;
    }

    /** The anti-ship firepower bearing on a distant target {@code degrees} off the bow, the better side of the two. */
    private static float firepowerOff(Warship ship, float degrees) {
        float d = 1000f;
        double r = Math.toRadians(degrees);
        float port = WarshipShipRules.firepowerAt(ship, 0f, ship.x - (float) Math.sin(r) * d, ship.y + (float) Math.cos(r) * d);
        float starboard = WarshipShipRules.firepowerAt(ship, 0f, ship.x + (float) Math.sin(r) * d,
                ship.y + (float) Math.cos(r) * d);
        return Math.max(port, starboard);
    }

    /**
     * Whether {@code from} lies off {@code ship}'s cover: outside its front shield's arc, or, for a hull with no front
     * shield, where it brings under {@link #COVER_FIRE_SHARE} of its bow battery to bear. A front shield overloaded
     * covers nothing. A ship that has turned its cover away from its enemy, or lost it, is open to being crossed.
     */
    static boolean offArc(Warship ship, Warship from) {
        if (frontShielded(ship) && ship.overloaded()) return true;
        if (frontShielded(ship)) {
            float rel = WarshipFacing.wrap(WarshipFacing.bearing(ship.centerX(), ship.centerY(), from.centerX(),
                    from.centerY()) - ship.facingDegrees);
            return Math.abs(rel) > 0.5f * ship.shieldArcDegrees();
        }
        float bow = firepowerOff(ship, 0f);
        if (bow <= 0f) return true;
        return WarshipShipRules.firepowerAt(ship, ship.facingDegrees, from.centerX(), from.centerY())
                < COVER_FIRE_SHARE * bow;
    }

    /**
     * Whether {@code ship}'s main battery bears on {@code target} at its present heading: at least {@link
     * #COVER_FIRE_SHARE} of the firepower it brings to bear dead ahead.
     */
    static boolean mainBears(Warship ship, Warship target) {
        float bow = firepowerOff(ship, 0f);
        return bow > 0f && WarshipShipRules.firepowerAt(ship, ship.facingDegrees, target.centerX(), target.centerY())
                >= COVER_FIRE_SHARE * bow;
    }

    /**
     * How far {@code ship} is ahead of {@code target}: the target's pool less its own, plus its own hull share less the
     * target's. The ship ahead is the one that turns in first; in a mirror the margin is whatever the fight has made.
     */
    static float advantage(Warship ship, Warship target) {
        return target.fluxFraction() - ship.fluxFraction() + ship.hullFraction() - target.hullFraction();
    }

    /** The turn side ({@code +1} or {@code -1}) that brings the nose onto {@code toTarget} the short way round. */
    private static int shortSide(Warship ship, float toTarget) {
        return WarshipFacing.wrap(toTarget - ship.facingDegrees) >= 0f ? 1 : -1;
    }

    /** Whether any of {@code ship}'s anti-ship guns bears on {@code target} at its present heading. */
    static boolean bearsOn(Warship ship, Warship target) {
        return WarshipShipRules.firepowerAt(ship, ship.facingDegrees, target.centerX(), target.centerY()) > 0f;
    }

    /**
     * Whether {@code ship}'s shield is going: overloaded, or {@link #SHIELD_GOING_FLUX} full and losing the flux race.
     */
    static boolean shieldGoing(Warship ship, Warship target) {
        return ship.overloaded() || (ship.fluxFraction() >= SHIELD_GOING_FLUX && losingFlux(ship, target));
    }

    /** A pool this full, deeper than its target's, is a shield about to fail. */
    static final float SHIELD_GOING_FLUX = 0.92f;

    /**
     * Whether {@code ship} is losing the flux race against {@code target}: overloaded, or its pool deeper than the
     * target's by {@link #BREAK_FLUX_LEAD}. Two ships equally full hold their shields on each other; the one further
     * gone breaks away, and its quarter is what the other crosses.
     */
    static boolean losingFlux(Warship ship, Warship target) {
        return ship.overloaded() || (!target.overloaded() && ship.fluxFraction() >= target.fluxFraction() + BREAK_FLUX_LEAD);
    }

    /** How much deeper than its own a full ship needs its target's pool to be to hold its run on it. */
    static final float PRESS_LEAD = 0.05f;

    /** How much deeper than its target's a pool must be for the ship to be losing the flux race. */
    static final float BREAK_FLUX_LEAD = 0.1f;

    /** Whether {@code target} is near its limit: overloaded, or its pool at least {@link #PRESS_FLUX} full. */
    static boolean pressed(Warship target) {
        return target.overloaded() || target.fluxFraction() >= PRESS_FLUX;
    }

    /** The range a standoff opens to: most of the way from the standoff range out to the ship's own reach. */
    static float standoffRange(float standoff, float reach) {
        return standoff + STANDOFF_OPEN_SHARE * Math.max(0f, reach - standoff);
    }

    /** Whether turning {@code c} by a positive bend turns it toward {@code toTarget} (+1) or away (-1). */
    private static float bendToward(float c, float toTarget) {
        return WarshipFacing.wrap(toTarget - c) >= 0f ? 1f : -1f;
    }

    /**
     * A broadside run's course: square to the line to the target, turned by the manoeuvre's side, so a ship keeps the
     * one broadside until it wears round (which flips the side).
     */
    static float broadsideCourse(Warship ship, float toTarget, WarshipManoeuvre m) {
        return WarshipFacing.wrap(toTarget + m.side * 90f);
    }

    /**
     * The bearing round a charge, from its centre, a flank is going to: the side away from its escorts, as the group
     * pass last read it, or the charge's quarter astern on the flanker's side when it has not.
     */
    private static float flankGoal(Warship target, WarshipManoeuvre m) {
        if (!Float.isNaN(m.flankGoal)) return m.flankGoal;
        return WarshipFacing.wrap(target.facingDegrees + 180f - m.side * 45f);
    }

    /**
     * Where an escort fighting a hull stands: interposed in line between its charge and that hull, just clear of the
     * charge's side facing it, the charge's escorts spaced along a line square to the threat. A screen that stands
     * between the threat and the ship it guards, rather than a ship that charges out to meet it.
     *
     * @return {@code [x, y]} for the escort's centre of gravity
     */
    static float[] interpose(Warship escort, Warship lead, Warship threat, List<Warship> escorts) {
        float dx = threat.centerX() - lead.centerX();
        float dy = threat.centerY() - lead.centerY();
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            dx = nx(lead.facingDegrees);
            dy = ny(lead.facingDegrees);
            len = 1f;
        }
        dx /= len;
        dy /= len;
        // How far the lead's own hull reaches toward the threat, at its heading.
        float along = Math.abs(dx * nx(lead.facingDegrees) + dy * ny(lead.facingDegrees));
        float across = (float) Math.sqrt(Math.max(0f, 1f - along * along));
        float leadExtent = 0.5f * lead.lengthCells * along + 0.5f * lead.widthCells * across;
        float out = leadExtent + 0.5f * Math.max(escort.lengthCells, escort.widthCells) + WarshipEscort.GAP_CELLS;
        // Clear of the charge however either turns: a heavy hull swinging its bow sweeps its corners out to its
        // half-diagonal faster than a screen sitting just off its side can give way.
        if (WarshipEscort.CLEARS_CHARGE) out = Math.max(out, WarshipEscort.roundingRadius(lead, escort));
        int slot = Math.max(0, escorts.indexOf(escort));
        int slots = Math.max(1, escorts.size());
        float spread = (slot - (slots - 1) * 0.5f) * (Math.max(escort.lengthCells, escort.widthCells)
                + WarshipEscort.GAP_CELLS);
        float cx = lead.centerX() + dx * out - dy * spread;
        float cy = lead.centerY() + dy * out + dx * spread;
        return cog(escort, new float[]{cx, cy});
    }

    /** In the wake of the line's lead, a fixed interval astern, on its course. */
    private static float[] lineAhead(Warship ship, WarshipManoeuvre m) {
        Warship lead = m.leader;
        float interval = lineInterval(lead, ship);
        float back = WarshipFacing.wrap(lead.facingDegrees + 180f);
        float sx = lead.centerX() + nx(back) * interval;
        float sy = lead.centerY() + ny(back) * interval;
        // Steer for a point on the lead's track half an interval ahead of the slot, so a ship off the track closes
        // onto it at an angle and settles into the wake, rather than paralleling the lead abreast; a ship already
        // past that point holds the lead's course and lets its slot come back to it.
        float fx = nx(lead.facingDegrees);
        float fy = ny(lead.facingDegrees);
        float aimX = sx + fx * 0.5f * interval;
        float aimY = sy + fy * 0.5f * interval;
        boolean pastAim = (ship.centerX() - aimX) * fx + (ship.centerY() - aimY) * fy > 0f;
        m.heading = pastAim ? lead.facingDegrees : WarshipFacing.bearing(ship.centerX(), ship.centerY(), aimX, aimY);
        return cog(ship, new float[]{sx, sy});
    }

    /** A station {@code distance} ahead along {@code course}, kept off the map's edges. */
    private static float[] ahead(Warship ship, float course, float distance, WarshipEdgeHelm edges) {
        float[] p = edges.settle(ship, ship.centerX() + nx(course) * distance, ship.centerY() + ny(course) * distance);
        return cog(ship, p);
    }

    /** A point for the hull's centre turned into one for its centre of gravity, which is what a station is. */
    private static float[] cog(Warship ship, float[] centre) {
        return new float[]{centre[0] - (ship.centerX() - ship.x), centre[1] - (ship.centerY() - ship.y)};
    }

    /** The unit vector of a hull-facing heading: the nose is along {@code (-sin, cos)}. */
    static float nx(float heading) {
        return (float) -Math.sin(Math.toRadians(heading));
    }

    static float ny(float heading) {
        return (float) Math.cos(Math.toRadians(heading));
    }

    /**
     * {@code (wantX, wantY)} held to the ship's nose: ahead at any speed, sideways at most {@link #LATERAL_SHARE} and
     * astern at most {@link #ASTERN_SHARE} of its top speed. A ship flying a manoeuvre travels as a ship does.
     */
    static float[] alongNose(Warship ship, float wantX, float wantY) {
        float fx = nx(ship.facingDegrees);
        float fy = ny(ship.facingDegrees);
        float along = wantX * fx + wantY * fy;
        float across = wantX * fy - wantY * fx;
        along = Math.max(-ASTERN_SHARE * ship.maxSpeed, along);
        across = Math.max(-LATERAL_SHARE * ship.maxSpeed, Math.min(LATERAL_SHARE * ship.maxSpeed, across));
        return new float[]{along * fx + across * fy, along * fy - across * fx};
    }
}
