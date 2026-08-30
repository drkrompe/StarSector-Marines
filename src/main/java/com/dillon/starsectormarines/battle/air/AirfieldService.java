package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.Runway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State owner for a garrison airfield's berths — what stands on each hardstand,
 * what is away flying, and what has burned.
 *
 * <p>The berth is the thing with identity here, not the airframe. A hardstand
 * is authored into the map and stays put; the aircraft on it comes and goes and
 * may never come back. Modelling it the other way round leaves nowhere to
 * record "this pad had an aircraft and now does not", which is the whole state
 * an attacker is trying to create.
 *
 * <p>Naming and split follow the project convention: this owns the state and
 * {@link AirfieldSystem} is the stateless tick consumer that puts airframes on
 * the map, takes them off, and counts down a refit. Reinforcement reads
 * {@link #hasAirworthyAirframe} as a supply question and takes an airframe
 * through {@link #launch}.
 *
 * <p>This is a second and independent way to end an enemy's air. Holding the
 * {@code AIRBASE} compound is the other, and the two ask genuinely different
 * questions: a field held with every aircraft burning supplies nothing, and a
 * field lost with the aircraft intact takes them with it.
 */
public final class AirfieldService {

    /** Where a berth's aircraft is. */
    public enum BerthState {
        /** The airframe is on its pad: a live unit, standable-past, and shootable. */
        PARKED,
        /** The airframe is out on a sortie. Nothing stands on the pad. */
        AWAY,
        /** Home and being turned round. Nothing stands on the pad until it is ready. */
        REFITTING,
        /** Burned on the pad or lost over the objective. Terminal — nothing replaces it. */
        DESTROYED
    }

    /**
     * One hardstand and whatever is on it.
     *
     * <p>Mutable and owned by the service; written only by {@link AirfieldSystem}
     * and by {@link #launch} / {@link #recover}. Public fields kept primitive in
     * the same style as {@code CompoundService.Record}.
     */
    /** What kind of place an aircraft is kept in. */
    public enum Kind {
        /**
         * A marked stand out on the apron. An aircraft here lifts off it
         * vertically and is boarded from outside.
         */
        HARDSTAND,
        /**
         * A bay inside a hangar. An aircraft here is under cover and cannot
         * lift off where it stands — it taxis out and uses the strip, which is
         * why a field with no runway bases nothing in its sheds.
         */
        SHELTER
    }

    public static final class Berth {
        /** The stand this berth is, or null for a shelter — nothing lands in a shed. */
        public final LandingPad pad;
        /** Where the aircraft stands, whichever kind of place this is. */
        public final int centerX;
        public final int centerY;
        public final Kind kind;
        public final ShuttleType type;
        /** Which way the parked hull points — its pad's approach bearing, or out of its shed. */
        public final float facingDegrees;

        public BerthState state = BerthState.PARKED;
        /**
         * Live unit id of the parked airframe while {@link #state} is
         * {@code PARKED}, else {@code 0}. Not an identity — the same aircraft
         * gets a new id every time it comes home, because a unit is what an
         * airframe <em>is</em> while it is standing on the ground.
         */
        public long airframeId;
        /**
         * Structure left on the hull, carried across every handoff so an
         * aircraft that comes home shot up parks shot up and is written off by
         * that much less fire on the ground.
         */
        public float hullHp;
        /** Sim-seconds of turnaround left before an airframe home from a sortie is airworthy again. */
        public float refitRemaining;
        /**
         * Whether the burnt-out airframe is still standing on this hardstand.
         *
         * <p>Its own fact rather than something read off {@link #state}. An
         * aircraft burned on its pad and one shot down over the objective are
         * the same terminal {@code DESTROYED} to everything that asks about
         * supply, and they are nothing alike to look at: one leaves a hulk on
         * the concrete and the other leaves an empty stand.
         */
        public boolean wreckOnPad;

        Berth(LandingPad pad, ShuttleType type, float facingDegrees) {
            this(pad, pad.centerX, pad.centerY, Kind.HARDSTAND, type, facingDegrees);
        }

        Berth(LandingPad pad, int centerX, int centerY, Kind kind,
              ShuttleType type, float facingDegrees) {
            this.pad = pad;
            this.centerX = centerX;
            this.centerY = centerY;
            this.kind = kind;
            this.type = type;
            this.facingDegrees = facingDegrees;
            this.hullHp = type.maxHp;
        }

        /** Whether this berth can put an aircraft in the air right now. */
        public boolean airworthy() {
            return state == BerthState.PARKED;
        }
    }

    /**
     * Sim-seconds a returned airframe spends on the ground before it can fly
     * again.
     *
     * <p>The existing air model already treats a re-arm as a full refit —
     * magazines refilled, hull repaired — and made it free and instantaneous
     * because it happened off-map at a carrier nobody could reach. Based
     * aircraft move that servicing onto a piece of ground the attacker can walk
     * onto, so it has to take long enough to be a window rather than a
     * formality. Long enough that a field cannot answer two requests back to
     * back; short enough that one sortie does not retire the aircraft.
     */
    public static final float REFIT_SECONDS = 45f;

    /** Fraction of the hull a full turnaround puts back. A field patches an airframe; it does not rebuild one. */
    private static final float REFIT_REPAIR_FRACTION = 0.5f;

    private final List<Berth> berths = new ArrayList<>();
    private Runway runway;
    /** The craft currently using the strip, or {@code 0} when it is free. */
    private long runwayOccupant;

    /**
     * Gives this field its strip. Called once at setup; a field without one
     * flies nothing that has to roll.
     */
    public void installRunway(Runway runway) {
        this.runway = runway;
    }

    /** The field's strip, or null when it has none. */
    public Runway runway() {
        return runway;
    }

    /** Whether anything is on the strip right now. */
    public boolean runwayBusy() {
        return runwayOccupant != 0L;
    }

    /** The craft on the strip, or {@code 0}. */
    public long runwayOccupant() {
        return runwayOccupant;
    }

    /**
     * Takes the strip for {@code craft}, if it is free or already theirs.
     *
     * <p>One occupant, because two aircraft rolling down one strip is not a
     * race the simulation should be allowed to lose — and because holding short
     * is the thing that makes a single strip a bottleneck worth attacking. Idempotent
     * for the holder so a state that re-asserts its claim every tick does not
     * have to remember whether it already has it.
     *
     * @return whether {@code craft} now holds the strip
     */
    public boolean claimRunway(long craft) {
        if (craft == 0L) return false;
        if (runwayOccupant == craft) return true;
        if (runwayOccupant != 0L) return false;
        runwayOccupant = craft;
        return true;
    }

    /**
     * Gives the strip back.
     *
     * <p>Ignores a caller that does not hold it, so a craft that is torn down
     * mid-procedure can release unconditionally without first checking whether
     * it got that far. A strip left claimed by a craft that no longer exists
     * would close the field for the rest of the battle.
     */
    public void releaseRunway(long craft) {
        if (runwayOccupant == craft) runwayOccupant = 0L;
    }

    /** Registers one hardstand and the aircraft based on it. Called once at setup. */
    public Berth addBerth(LandingPad pad, ShuttleType type, float facingDegrees) {
        Berth berth = new Berth(pad, type, facingDegrees);
        berths.add(berth);
        return berth;
    }

    /**
     * Registers one hangar bay and the aircraft kept in it.
     *
     * <p>Only worth doing on a field with a strip: an aircraft in a shed
     * reaches the air by taxiing out and rolling, so basing one where there is
     * nothing to roll down strands it in the shed for the battle.
     */
    public Berth addShelterBerth(Gantry shelter, ShuttleType type) {
        Berth berth = new Berth(null, shelter.centerX, shelter.centerY,
                Kind.SHELTER, type, shelter.facing.degrees());
        berths.add(berth);
        return berth;
    }

    /** Every berth on the field, in registration order. */
    public List<Berth> berths() {
        return Collections.unmodifiableList(berths);
    }

    /** Whether this field has an airframe that could fly a sortie now. */
    public boolean hasAirworthyAirframe() {
        for (Berth berth : berths) {
            if (berth.airworthy()) return true;
        }
        return false;
    }

    /**
     * The berth a sortie should fly from — the airworthy one nearest
     * {@code (x, y)}, or null when the field has nothing left to send.
     *
     * <p>Nearest rather than first so a sortie leaves from the stand closest to
     * where it is going, which is also the stand the crew has the shortest walk
     * to.
     */
    public Berth nearestAirworthy(float x, float y) {
        return nearestAirworthy(x, y, Kind.HARDSTAND);
    }

    /**
     * The airworthy berth of {@code kind} nearest {@code (x, y)}, or null.
     *
     * <p>Split by kind because the two are not interchangeable and a caller
     * always knows which it wants: a vertical-lift transport needs a stand it
     * can rise off, and a craft that rolls needs a shed it can taxi out of.
     * Handing a shuttle a shelter would strand it; handing a rolling craft a
     * hardstand would have it take off from the middle of the apron.
     */
    public Berth nearestAirworthy(float x, float y, Kind kind) {
        Berth best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Berth berth : berths) {
            if (!berth.airworthy() || berth.kind != kind) continue;
            float dx = berth.centerX - x;
            float dy = berth.centerY - y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = berth;
            }
        }
        return best;
    }

    /**
     * Sends this berth's airframe up. The unit is not removed here — that is
     * {@link AirfieldSystem}'s to do, because taking something off the map is a
     * tick-side action and this owns state only.
     *
     * @return the hull HP the departing aircraft carries into the air
     */
    public float launch(Berth berth) {
        if (!berth.airworthy()) {
            throw new IllegalStateException("berth is not airworthy: " + berth.state);
        }
        berth.state = BerthState.AWAY;
        return berth.hullHp;
    }

    /**
     * Takes an airframe back, with whatever the sortie left of it, and starts
     * its turnaround. It is not on the pad and cannot be shot until the refit
     * finishes and {@link AirfieldSystem} puts it back.
     */
    public void recover(Berth berth, float hullHp) {
        if (berth.state == BerthState.DESTROYED) return;
        berth.hullHp = Math.max(1f, Math.min(berth.type.maxHp, hullHp));
        berth.state = BerthState.REFITTING;
        berth.refitRemaining = REFIT_SECONDS;
        berth.airframeId = 0L;
    }

    /** How much hull a completed turnaround hands back. */
    float repaired(Berth berth) {
        float repair = berth.type.maxHp * REFIT_REPAIR_FRACTION;
        return Math.min(berth.type.maxHp, berth.hullHp + repair);
    }

    /**
     * Writes this berth off for the rest of the battle, whether the airframe
     * burned on its pad or was shot down over the objective.
     *
     * <p>Terminal on purpose. A field is a finite thing to lose, and an
     * attacker who spends the effort to destroy an aircraft should be able to
     * see that it stays destroyed.
     */
    public void destroyed(Berth berth) {
        berth.state = BerthState.DESTROYED;
        berth.airframeId = 0L;
        berth.hullHp = 0f;
    }

    /**
     * Writes this berth off with its aircraft burning where it was parked.
     *
     * <p>The same terminal state as {@link #destroyed}, plus the hulk left on
     * the concrete. That the wreck is visible is the point: a raider who walks
     * onto an apron to burn the based aircraft can see the result of it
     * standing there for the rest of the battle, rather than having to infer
     * it from sorties that stop arriving.
     */
    public void burnedOnPad(Berth berth) {
        destroyed(berth);
        berth.wreckOnPad = true;
    }

    /** The berth holding a given live parked airframe, or null. */
    public Berth berthOf(long airframeId) {
        if (airframeId == 0L) return null;
        for (Berth berth : berths) {
            if (berth.airframeId == airframeId) return berth;
        }
        return null;
    }
}
