package com.dillon.starsectormarines.battle.warship;

/**
 * A ship's place in its group's hull fight ({@code warship-tactics.md}): the role its group gave it, the hull it is
 * to fight, the side it works, and the <b>manoeuvre</b> it is flying. One per ship, owned by it; written by
 * {@link WarshipTactics} and read by the helm, the HUD and evidence.
 *
 * <p>A manoeuvre is an intent held for tens of seconds: a kind, when it began, and for the kinds that hold a fixed
 * course the course itself. It is not a station; the helm flies it.
 */
public final class WarshipManoeuvre {

    /** What a ship is for in its group this minute. */
    public enum Role {
        /** Not in a group hull fight (no hull, an escort, a ship the mission or a pilot is flying). */
        NONE,
        /** A ship with a hull of its own to fight. */
        FREE,
        /** Engaging the escorts of a charge so another can reach it. */
        PIN,
        /** Going round a charge's escorts to the charge itself. */
        FLANK,
        /** The leading ship of a line of broadside ships on one target. */
        LEAD,
        /** A ship sailing in the wake of its line's lead. */
        SECOND,
        /**
         * A pack relay's aggro holder: the nearest and most threatening hull on the target's battery, drawing its fire
         * with its shield on it, firing only as its flux allows ({@link WarshipTactics}, pack relay).
         */
        TANK,
        /**
         * A pack relay's striker: working the target from its quarter, off its shield arc where it can, at full fire
         * while the tank holds the target's attention; or, venting, out of the fight until its pool is low.
         */
        STRIKER
    }

    /** The manoeuvres a ship flies against a hull. */
    public enum Kind {
        /** Closing on the target out of reach, on an oblique course toward the side the ship works. */
        APPROACH,
        /** A bow ship closing nose-on to its standoff, guns on the target. */
        ATTACK_RUN,
        /** A broadside ship running parallel to the target at its standoff, broadside laid on it. */
        BROADSIDE_RUN,
        /** Holding a course away after a run, opening the range. A broadside ship's way of opening it. */
        EXTEND,
        /**
         * A bow ship drifting off its target after a run, astern and to one side, its nose, its guns and any front
         * shield still on the target: the range opens without the ship ever turning its back.
         */
        STANDOFF,
        /** Turning back onto the target through a fixed direction, unmasking guns as it comes round. */
        COME_ABOUT,
        /** Swinging wide round a charge's escorts to the charge's far side. */
        FLANK,
        /** In the wake of the line's lead, matching its course. */
        LINE_AHEAD,
        /**
         * Circling its target with way on, the target held off the bow at the widest angle that keeps it inside the
         * ship's own cover (its front shield's arc, or the beam), working round toward the target's quarter while what
         * bears off the bow keeps firing: two ships jockeying for position rather than parked nose to nose.
         */
        ORBIT,
        /**
         * Turning in across a target that has turned its cover away (its shield arc or its guns off this ship): crossing
         * its T, nose and guns on its flank or stern for as long as it takes the target to turn back.
         */
        CROSS,
        /**
         * Breaking away on a course well off the target to open the range at full way while venting a full pool,
         * rather than backing off at a crawl: the turn is the cost, paid on a quarter the target may cross.
         */
        BREAK,
        /**
         * A pack relay's tank holding the target's attention: on the bearing round the target where its battery bears
         * most, nearer than its partners, nose and shield on the target.
         */
        HOLD_AGGRO,
        /** A pack relay's striker on the target's quarter, away from the tank, guns on at full fire. */
        STRIKE,
        /**
         * A pack relay ship venting after its turn as tank: backing off nose-on while the target still has it in its
         * sights, then, once the target's attention is on the new tank, turning away at full way to past the target's
         * reach and holding there, shield down, until its pool is low.
         */
        VENT
    }

    Role role = Role.NONE;
    /** The hull the group gave this ship to fight, or {@code null}. */
    Warship target;
    /** Which way this ship works its target: +1 or -1 (a pass side, a turn direction, a broadside). */
    int side = 1;
    /** The lead this ship follows in a line, for {@link Role#SECOND}. */
    Warship leader;
    /** For {@link Role#FLANK}, the bearing round the charge it is going to (away from the escorts); NaN for none. */
    float flankGoal = Float.NaN;

    Kind kind;
    /** The battle clock, in seconds, when the current manoeuvre began. */
    float startedAt;
    /** The course a fixed-course manoeuvre holds, in the hull-facing convention; NaN for none. */
    float course = Float.NaN;
    /** The heading the helm should hold this tick, or NaN to bring the guns to bear as the duel helm does. */
    float heading = Float.NaN;
    /** The share of its top speed the helm should hold this tick (a line's lead easing for its followers). */
    float speedShare = 1f;
    /** For a standoff: the ship is venting a full pool, and opens past its target's reach until it has. */
    boolean venting;
    /**
     * Whether this ship is in a one-on-one hull fight (its side's only hunter against the other side's only hull), and
     * so flies a duel's manoeuvres: orbit, cross and break. Set by the group pass.
     */
    boolean duel;
    /** For an orbit: how far off the bow the target is held, in degrees, chosen when the orbit began. */
    float orbitAngle = Float.NaN;
    /** Manoeuvres begun, for evidence. */
    int begun;
    /** For a pack relay ship: it is out of the fight venting, holding its fire, until its pool is low. */
    boolean relayVenting;
    /** For a relay striker or venting ship: the tank holding the target for it, or {@code null}. */
    Warship relayPartner;
    /** The battle clock, in seconds, when this ship took its current relay role. */
    float relaySince = Float.NaN;
    /** Times this ship has taken over as a relay's tank from a partner, for evidence. */
    int relaySwaps;
    /** Times this ship has broken off to vent in a relay, for evidence. */
    int relayVents;

    /** The role the ship's group gave it. */
    public Role role() {
        return role;
    }

    /** The hull the group gave this ship to fight, or {@code null}. */
    public Warship target() {
        return target;
    }

    /** The manoeuvre being flown, or {@code null} when none. */
    public Kind kind() {
        return kind;
    }

    /** Seconds the current manoeuvre has been flown, as of {@code now}. */
    public float seconds(float now) {
        return kind == null ? 0f : now - startedAt;
    }

    /** How many manoeuvres this ship has begun. */
    public int begun() {
        return begun;
    }

    /** Whether this ship is out of its pack relay venting, holding its fire. */
    public boolean relayVenting() {
        return relayVenting;
    }

    /**
     * Whether this ship is venting but still handing the target over: backing off nose-on, holding its fire, until its
     * partner is in place to take the target's eye. Its vent proper begins when it turns away.
     */
    public boolean relayHandingOver() {
        return relayVenting && kind == Kind.VENT && Float.isNaN(course);
    }

    /** Times this ship has taken over as its relay's tank from a partner. */
    public int relaySwaps() {
        return relaySwaps;
    }

    /** Times this ship has broken off to vent in a relay. */
    public int relayVents() {
        return relayVents;
    }

    /** The line lead this ship follows, or {@code null}. */
    public Warship leader() {
        return leader;
    }

    void begin(Kind next, float now, float fixedCourse) {
        kind = next;
        startedAt = now;
        venting = false;
        course = fixedCourse;
        begun++;
    }

    void clear() {
        role = Role.NONE;
        duel = false;
        target = null;
        leader = null;
        flankGoal = Float.NaN;
        kind = null;
        course = Float.NaN;
        heading = Float.NaN;
        relayVenting = false;
        relaySince = Float.NaN;
        relayPartner = null;
    }
}
