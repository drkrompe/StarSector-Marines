package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.unit.UnitType;
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
    MECH_TECH(RoomPurpose.CREW_QUARTERS, UnitType.ENGINEER,
            onWatchWith(Affordance.SERVICE, Affordance.REPAIR,
                    Affordance.STOW, Affordance.READOUT),
            Amenities.AND_A_BUNK),

    /** Makes and repairs the parts a bay consumes, at the bench rather than the machine. */
    MACHINIST(RoomPurpose.CREW_QUARTERS, UnitType.ENGINEER,
            onWatchWith(Affordance.FABRICATE, Affordance.REPAIR, Affordance.STOW),
            Amenities.AND_A_BUNK),

    /**
     * Keeps the ship running: the plant forward of the transom and the drive
     * against it, plus the readings that say whether either is happy.
     */
    ENGINE_WATCH(RoomPurpose.CREW_QUARTERS, UnitType.ENGINEER,
            onWatchWith(Affordance.TEND, Affordance.REPAIR,
                    Affordance.READOUT, Affordance.STOW),
            Amenities.AND_A_BUNK),

    /** Keeps the sick berth ready, whether or not anybody is in it. */
    MEDIC(RoomPurpose.CREW_QUARTERS, UnitType.ENGINEER,
            onWatchWith(Affordance.TREAT, Affordance.STOW),
            Amenities.AND_A_BUNK),

    /** Stands the ship's watch: the bridge, and the consoles that watch the plant. */
    BRIDGE_WATCH(RoomPurpose.CREW_QUARTERS, UnitType.ENGINEER,
            onWatchWith(Affordance.WATCH, Affordance.READOUT),
            Amenities.AND_A_BUNK),

    /** Issues weapons and takes them back in, and keeps the racks straight. */
    ARMOURER(RoomPurpose.CREW_QUARTERS, UnitType.ENGINEER,
            onWatchWith(Affordance.ISSUE, Affordance.ROUNDS, Affordance.STOW),
            Amenities.AND_A_BUNK),

    /** Works the holds and the boat bay: stores in, stores out, and the tally. */
    STOREKEEPER(RoomPurpose.CREW_QUARTERS, UnitType.ENGINEER,
            onWatchWith(Affordance.STOW, Affordance.READOUT),
            Amenities.AND_A_BUNK),

    /**
     * Off watch: eating, washing and keeping their shooting in around the ship,
     * sleeping and squaring their kit away in their own berthing.
     */
    MARINE(RoomPurpose.BARRACKS, UnitType.MARINE,
            onWatchWith(Affordance.MESS, Affordance.PRACTICE),
            List.of(Affordance.REST, Affordance.STOW));

    /**
     * What everybody aboard does when they are not working.
     *
     * <p>Shared because it is not a fact about a trade. A technician and an
     * armourer sleep, wash, sit in the lounge and use the gym in exactly the
     * same rooms and for the same reasons, and writing the list per role only
     * creates the opportunity for one of them to be quietly missing an amenity
     * the ship has.
     *
     * <p>The lounge and the gym are the point of the list rather than trimming
     * on it. A complement whose only off-watch options are a bunk and a mess
     * table can be asleep, eating, or at work; give it somewhere to sit and
     * somewhere to train and the third state stops being "standing in a
     * passage".
     */
    /**
     * A trade's own work, and then the rooms everybody aboard uses.
     *
     * <p>Written once rather than per role, because which amenities a ship has
     * is a fact about the ship and not about the trade. Spelt out per role it is
     * only an opportunity for one of them to be quietly missing a washroom.
     */
    private static List<Affordance> onWatchWith(Affordance... trade) {
        List<Affordance> jobs = new ArrayList<>(List.of(trade));
        jobs.addAll(Amenities.ANY_ROOM);
        return List.copyOf(jobs);
    }

    private static final class Amenities {

        /**
         * The rooms a trade visits that are nobody's workplace: the heads, the
         * lounge, the gymnasium.
         *
         * <p>On watch rather than off, and the distinction is not about when
         * somebody goes. A job is <b>off watch</b> when it can only be done in
         * one's own quarters — a bunk, a locker — and <b>on watch</b> when it is
         * done in a room of its own. Reading "not work" as "off watch" put the
         * ship's washrooms, lounges and gymnasia beyond everybody's reach at
         * once: the claim rule fences an off-watch job to the role's own
         * berthing, so twelve heads and four lounges published work that no
         * member of the crew was allowed to claim, and the rooms generated
         * furnished and stayed empty.
         *
         * <p>None of them are {@link Affordance#duty}, so none of them make a
         * room a posting. Somewhere to wash is not a billet.
         */
        private static final List<Affordance> ANY_ROOM = List.of(
                Affordance.WASH, Affordance.UNWIND, Affordance.EXERCISE);

        /** A bunk, which is the one thing that is genuinely one's own. */
        private static final List<Affordance> AND_A_BUNK = List.of(Affordance.REST);

        private Amenities() { }
    }

    private final RoomPurpose quarters;
    private final UnitType unit;
    private final List<Affordance> onWatch;
    private final List<Affordance> offWatch;
    private final List<Affordance> jobs;

    CrewRole(RoomPurpose quarters, UnitType unit,
             List<Affordance> onWatch, List<Affordance> offWatch) {
        this.quarters = quarters;
        this.unit = unit;
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

    /**
     * Who this role turns up as.
     *
     * <p>Presentation, but not only presentation: a marine on a range is armed
     * and an engineer is not, so the unit a role spawns as decides whether a
     * practice stop can put rounds downrange at all.
     */
    public UnitType unit() {
        return unit;
    }

    /**
     * The job that defines this trade, or null for a role that has no work.
     *
     * <p>The first of the on-watch jobs, because a role's list is written with
     * its own work at the head and what that work needs behind it. A technician
     * welds, and fetches and reads off terminals <em>because</em> they weld; a
     * storekeeper's whole trade is the stores.
     *
     * <p>This is what makes a compartment one posting rather than several. Every
     * secondary job is shared - half the trades aboard handle stores at some
     * point - so a room that published stowage was a billet for all of them, and
     * a hull with nineteen spares pockets crewed a thousand engineers. Where a
     * watch is stationed is settled by whose room it is.
     *
     * <p>A {@linkplain #isCircuit circuit} job is never a trade, however early
     * it appears in the list. Rounds and defects are deliberately everywhere -
     * that is what makes them circuits - so reading either as a station would
     * make every compartment on the ship a posting and put a watch in each.
     */
    public Affordance trade() {
        for (Affordance job : onWatch) {
            if (job.duty() && !isCircuit(job)) return job;
        }
        return null;
    }

    /**
     * Whether this job is done in several places in one turn of the rotation
     * rather than in one.
     *
     * <p>Almost every job aboard is somewhere you go: a bench, a bunk, a lane.
     * Two are not. Rounds are made <em>of</em> the walk — a compartment is
     * looked into and left, and the next one is the point — and a defect list is
     * wherever the defects happen to be. Given one stop each, like every other
     * job, they collapse into their opposite: a master-at-arms who walks to the
     * nearest compartment and stands in it, and a technician who tends the same
     * fault forever.
     *
     * <p>So a circuit job earns a stop at every place the shift reaches that
     * offers it, and members start at different points on the ring so that two
     * patrols are not the same patrol.
     */
    public static boolean isCircuit(Affordance affordance) {
        return affordance == Affordance.ROUNDS || affordance == Affordance.REPAIR;
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
            case SERVICE, FABRICATE, TREAT, ISSUE, TEND, REPAIR -> AmbientActivity.WORKING;
            case STOW, READOUT, WATCH, ROUNDS -> AmbientActivity.INSPECTING;
            case REST, WASH -> AmbientActivity.RESTING;
            case MESS, UNWIND -> AmbientActivity.SOCIALIZING;
            case EXERCISE -> AmbientActivity.EXERCISING;
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
     *
     * <p><b>Measured against the walk, not against a watch.</b> These were
     * seconds long while a route's travel was a fiction the same clock made up,
     * and stayed seconds long when the walking became real - so a hand crossed a
     * three-hundred-cell hull for forty seconds, spent three at the bench, and
     * set off again. Six in ten actor-samples of a manned transport were
     * somebody in a passage. A stint has to be long enough to read as the thing
     * the walk was for, which on a ship this size is tens of seconds rather than
     * a handful. They are still nothing like a watch: this is presentation, and
     * a player looking at a room for half a minute should see it worked rather
     * than see one person arrive.
     */
    public static float dwellFor(Affordance affordance) {
        return switch (affordance) {
            case SERVICE -> 28f;
            case FABRICATE -> 22f;
            case STOW -> 16f;
            case READOUT -> 13f;
            case REST -> 41f;
            case MESS -> 34f;
            case PRACTICE -> 19f;
            case WASH -> 8f;
            case TREAT -> 31f;
            case WATCH -> 46f;
            case ISSUE -> 17f;
            case TEND -> 25f;
            case REPAIR -> 37f;
            case UNWIND -> 44f;
            case EXERCISE -> 27f;
            // A compartment looked into and left. The shortest job aboard,
            // because what rounds are made of is the walk between them.
            case ROUNDS -> 6f;
        };
    }
}
