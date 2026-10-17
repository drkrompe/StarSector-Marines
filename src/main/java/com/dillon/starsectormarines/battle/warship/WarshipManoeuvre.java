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
        SECOND
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
        LINE_AHEAD
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
    /** Manoeuvres begun, for evidence. */
    int begun;

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
        target = null;
        leader = null;
        flankGoal = Float.NaN;
        kind = null;
        course = Float.NaN;
        heading = Float.NaN;
    }
}
