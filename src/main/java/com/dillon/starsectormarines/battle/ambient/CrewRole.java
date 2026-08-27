package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What a crew member is aboard to do, as the jobs they will work.
 *
 * <p>A role is the join between a person and a room. A compartment publishes the
 * jobs its fixtures afford; a role says which of those jobs are <em>this</em>
 * person's, and in what order they come round to them. Put a mech technician in
 * a vehicle bay and they weld, fetch, and read off terminals; put the same
 * technician in a barracks and they have nothing to do there, which is the
 * correct answer rather than a gap.
 *
 * <p>Roles are deliberately not derived from what a room contains. A room full
 * of bunks affords rest to everybody and is somebody's <em>job</em> only if that
 * somebody is off watch; work is not the same fact as opportunity, and folding
 * them together is how every actor on a deck ends up doing whatever is nearest.
 *
 * <p>A role's jobs come in two lists, because the same affordance is a different
 * job in a different room. Stowage in a vehicle bay is the parts run and belongs
 * to whoever works the bay; stowage in a berth is somebody's own locker and
 * belongs to whoever sleeps there. A single list cannot tell those apart, and
 * with one the marines were offered a shift running the bay's stores.
 *
 * <p>The order of {@link #jobs()} is the order the shift comes round in, not a
 * priority. A technician welds, then fetches a part, then checks a readout, then
 * welds again — a rotation, because that is what a shift looks like from
 * outside, and because a route that always ran to the nearest free job would
 * bunch every technician at one end of the bay.
 */
public enum CrewRole {

    /**
     * Services the machines a vehicle bay berths. Welding on whatever is parked
     * is the work; the parts run and the readout are what the work needs.
     */
    MECH_TECH(RoomPurpose.CREW_QUARTERS,
            List.of(Affordance.SERVICE, Affordance.STOW, Affordance.READOUT),
            List.of(Affordance.REST)),

    /** Makes and repairs the parts a bay consumes, at the bench rather than the machine. */
    MACHINIST(RoomPurpose.CREW_QUARTERS,
            List.of(Affordance.FABRICATE, Affordance.STOW),
            List.of(Affordance.REST)),

    /**
     * Off watch: eating and keeping their shooting in around the ship, sleeping
     * and squaring their kit away in their own berthing.
     */
    MARINE(RoomPurpose.BARRACKS,
            List.of(Affordance.MESS, Affordance.PRACTICE),
            List.of(Affordance.REST, Affordance.STOW));

    private final RoomPurpose quarters;
    private final List<Affordance> onWatch;
    private final List<Affordance> offWatch;
    private final List<Affordance> jobs;

    CrewRole(RoomPurpose quarters, List<Affordance> onWatch, List<Affordance> offWatch) {
        this.quarters = quarters;
        this.onWatch = List.copyOf(onWatch);
        this.offWatch = List.copyOf(offWatch);
        List<Affordance> all = new ArrayList<>(this.onWatch);
        all.addAll(this.offWatch);
        this.jobs = List.copyOf(all);
    }

    /**
     * The compartment this role's bunk is in.
     *
     * <p>A berth is an assignment rather than an amenity: everyone aboard eats
     * in the same mess and washes in the same heads, and nobody sleeps in
     * somebody else's bunk. Without this a marine would turn in wherever the
     * nearest bunkroom happened to be, which on a ship carrying two populations
     * is the ratings' — and the barracks the player reads a billet count off
     * would be a room the ship's crew were also sleeping in.
     */
    public RoomPurpose quarters() {
        return quarters;
    }

    /** The jobs this role works in a compartment that is somebody's workplace. */
    public List<Affordance> onWatch() {
        return onWatch;
    }

    /** The jobs this role has in its own berthing, and nowhere else. */
    public List<Affordance> offWatch() {
        return offWatch;
    }

    /** Every job this role has anywhere, in the order a shift comes round to them. */
    public List<Affordance> jobs() {
        return jobs;
    }

    /**
     * Whether a compartment of this purpose is somebody's berthing rather than
     * somewhere they work.
     *
     * <p>Read off the roles themselves so one place decides it. A purpose is
     * berthing exactly when some role calls it their quarters, which is what the
     * distinction means.
     */
    public static boolean isBerthing(RoomPurpose purpose) {
        return BERTHING.contains(purpose);
    }

    private static final Set<RoomPurpose> BERTHING = berthing();

    private static Set<RoomPurpose> berthing() {
        EnumSet<RoomPurpose> purposes = EnumSet.noneOf(RoomPurpose.class);
        for (CrewRole role : values()) purposes.add(role.quarters);
        return purposes;
    }

    /**
     * How the actor should present while doing this job.
     *
     * <p>Presentation only — the activity vocabulary tells the ambient service
     * how to stand and what to hold, and resolves nothing. A technician welding
     * and a technician reading a terminal are both working; they do not look
     * like it in the same way.
     */
    public static AmbientActivity activityFor(Affordance affordance) {
        return switch (affordance) {
            case SERVICE, FABRICATE -> AmbientActivity.WORKING;
            case STOW, READOUT -> AmbientActivity.INSPECTING;
            case REST -> AmbientActivity.RESTING;
            case MESS -> AmbientActivity.SOCIALIZING;
            case PRACTICE -> AmbientActivity.PRACTICING_EQUIPMENT;
        };
    }

    /**
     * How long a job of this kind holds somebody, in seconds.
     *
     * <p>Uneven on purpose. Equal dwells put every technician in a compartment
     * on the same cadence, so they arrive and leave together and the room
     * pulses; unequal ones drift apart on their own without anybody authoring a
     * schedule.
     */
    public static float dwellFor(Affordance affordance) {
        return switch (affordance) {
            case SERVICE -> 5.5f;
            case FABRICATE -> 4.4f;
            case STOW -> 3.2f;
            case READOUT -> 2.6f;
            case REST -> 7.0f;
            case MESS -> 6.0f;
            case PRACTICE -> 4.0f;
        };
    }
}
