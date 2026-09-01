package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.Runway;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
        /**
         * Which aircraft is kept here. A transport on an apron stand, a
         * fighter in a shed — the berth does not care which, only that it has
         * a sprite, a size and a hull to shoot at.
         */
        public final Airframe airframe;
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
        /**
         * Hull this airframe is being worked back up to, or 0 when it is not
         * being turned round.
         */
        public float refitTarget;
        /**
         * Hand-seconds of turnaround left on this airframe.
         *
         * <p>Work rather than time, which is the whole of what a turnaround now
         * is: a field with nobody on it never finishes one, and a field with a
         * crew finishes it as fast as they get round to it. An aircraft home in
         * one piece owes the base servicing; one home in pieces owes that and
         * the patching, so damage costs a field its next sortie as well as its
         * hull.
         */
        public float refitWork;
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

        Berth(LandingPad pad, Airframe airframe, float facingDegrees) {
            this(pad, pad.centerX, pad.centerY, Kind.HARDSTAND, airframe, facingDegrees);
        }

        Berth(LandingPad pad, int centerX, int centerY, Kind kind,
              Airframe airframe, float facingDegrees) {
            this.pad = pad;
            this.centerX = centerX;
            this.centerY = centerY;
            this.kind = kind;
            this.airframe = airframe;
            this.facingDegrees = facingDegrees;
            this.hullHp = airframe.maxHp();
        }

        /**
         * Where what is kept here goes when it leaves, or {@code null} for a
         * berth whose craft has nowhere to be but this map.
         *
         * <p>A garrison's hardstand has none: an aircraft off it flies to
         * somewhere on the same battle, and the map's own edge is where it
         * eventually leaves. A ship's boat berth has one, because a bay opens
         * onto space and the point beyond its door is the only place a boat can
         * be that is not aboard. Carried on the berth rather than on the field
         * because a hull with two bays has two doors on two sides.
         */
        public float[] offship;

        /** Whether this berth's craft has somewhere off this map to go. */
        public boolean hasOffship() {
            return offship != null;
        }

        /** Whether this berth can put an aircraft in the air right now. */
        public boolean airworthy() {
            return state == BerthState.PARKED;
        }
    }

    /**
     * Hull one technician puts back on an airframe in one second at the stand.
     *
     * <p>A turnaround used to be a countdown, and the countdown was the whole of
     * it: the field answered its next request forty-five seconds later whether
     * anybody was working or not, so an attacker who killed the ground crew
     * denied the field nothing at all. Counted in hands instead, a turnaround is
     * work — done by people who can be shot on the apron, on an aircraft that is
     * standing on it while they do it. See {@code air-nouns.md}; the vehicle
     * bay's stocks are the same law on a different machine.
     *
     * <p>Fast per hand and slow in practice, which is the shape a field has. A
     * technician at a stand puts hull back quickly; there are three of them, six
     * stands, and a rotation that is mostly not at any one of them, so what an
     * aircraft actually waits for is its turn.
     */
    public static final float HULL_PER_HAND_SECOND = 2f;

    /**
     * Hand-seconds every turnaround costs before any damage is counted.
     *
     * <p>A turnaround is not only patching. Magazines are refilled, tanks are
     * topped up, and somebody walks round the aircraft — all of which is work
     * whether it came home shot up or untouched, and none of which shows on the
     * hull. Without it a field whose sorties are never intercepted turns them
     * round instantly, which is what the countdown this replaced was really
     * protecting: a field cannot answer two requests back to back.
     */
    public static final float TURNAROUND_HAND_SECONDS = 25f;

    /** Fraction of the hull a full turnaround puts back. A field patches an airframe; it does not rebuild one. */
    private static final float REFIT_REPAIR_FRACTION = 0.5f;

    private final List<Berth> berths = new ArrayList<>();
    /** Whose field this is; a garrison's unless a host says otherwise. */
    private Faction owner = Faction.DEFENDER;
    /** Standing cell of every servicing point on the apron, to the berth it works. */
    private final Map<Long, Integer> serviceCells = new HashMap<>();
    /**
     * Wrecks left by an airframe destroyed away from any berth — taxiing,
     * holding short, mid-roll. A hardstand kill needs none of this: its wreck
     * is the berth's own {@link Berth#wreckOnPad}, drawn at a position the
     * berth already carries. A wreck out here has no berth under it, so it
     * carries its own position instead. See {@link #addGroundWreck}.
     */
    private final List<GroundWreck> groundWrecks = new ArrayList<>();
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
     * Takes the strip whether or not anybody else has it.
     *
     * <p>Deliberately not how a departure gets the runway, and deliberately
     * available to a landing. A craft holding short can wait; a craft on final
     * that has already flown its circuits cannot, and it is about to be
     * standing on the strip whatever this method says. Recording the seizure is
     * what keeps the rollout consistent with the aircraft actually on the
     * ground — the alternative is a runway attributed to a craft that is not on
     * it while one that is rolls out unrecorded.
     */
    public void takeRunway(long craft) {
        if (craft == 0L) return;
        runwayOccupant = craft;
    }

    /**
     * Gives the strip back.
     *
     * <p>Ignores a caller that does not hold it, so a craft that is torn down
     * mid-procedure can release unconditionally without first checking whether
     * it got that far. A strip left claimed by a craft that no longer exists
     * would close the field for the rest of the battle — and did: a fighter
     * killed by ground fire during its takeoff roll took the runway with it,
     * and every sortie that came home afterwards was refused and flew circuits
     * until the battle ended. Every way a craft ceases to exist runs through
     * one teardown, and that is where the release belongs, so no future ending
     * has to remember it.
     */
    public void releaseRunway(long craft) {
        if (runwayOccupant == craft) runwayOccupant = 0L;
    }

    /** Registers one hardstand and the aircraft based on it. Called once at setup. */
    public Berth addBerth(LandingPad pad, Airframe airframe, float facingDegrees) {
        Berth berth = new Berth(pad, airframe, facingDegrees);
        berths.add(berth);
        return berth;
    }

    /**
     * Registers one boat berth in a ship's bay, and the boat kept in it.
     *
     * <p>A {@link Kind#HARDSTAND} because that is what the kind says — a berth
     * something lifts off rather than rolls out of. A ship's boat leaves through
     * a door in the hull and needs no run, so it is a hardstand that happens to
     * be indoors, and a shelter berth would have it looking for a strip.
     *
     * <p>Registered from a {@link Gantry} rather than a {@link LandingPad}
     * because that is what authored it: a bay's berths are cut with the room the
     * way a mech bay's are, where a field's hardstands are marked out on a lot.
     * Which of those authored a berth says nothing about how it is used, and
     * conflating the two is why this needed a method rather than an argument.
     */
    public Berth addBayBerth(Gantry bay, Airframe airframe) {
        return addBayBerth(bay, airframe, null);
    }

    /**
     * The same, for a bay that has a door: {@code offship} is the point beyond
     * it that a boat leaving here goes to and comes back from.
     *
     * <p>A bay with no door registers without one and simply never launches,
     * which is a bay whose boats are ornaments rather than a generation that
     * stops. The placer is supposed to give every bay a flank, so that is a
     * defect upstream, and one the deck already reports.
     */
    public Berth addBayBerth(Gantry bay, Airframe airframe, float[] offship) {
        Berth berth = new Berth(null, bay.centerX, bay.centerY,
                Kind.HARDSTAND, airframe, bay.facing.degrees());
        berth.offship = offship == null ? null : new float[]{offship[0], offship[1]};
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
    public Berth addShelterBerth(Gantry shelter, Airframe airframe) {
        Berth berth = new Berth(null, shelter.centerX, shelter.centerY,
                Kind.SHELTER, airframe, shelter.facing.degrees());
        berths.add(berth);
        return berth;
    }

    /**
     * Whose field this is, and therefore whose aircraft stand on it.
     *
     * <p>A fact about the field rather than about whatever ticks it, which is
     * why it lives here. It was a constructor argument to {@code AirfieldSystem}
     * while there was only one kind of field — a garrison's, on a battle map —
     * and the moment a second appeared the tick consumer would have had to be
     * built differently for a ship than for a lot, for a reason that has nothing
     * to do with what it does.
     */
    public Faction owner() {
        return owner;
    }

    /** Hand this field to a side. Called at setup, before anything stands on it. */
    public void setOwner(Faction owner) {
        if (owner != null) this.owner = owner;
    }

    /** Every berth on the field, in registration order. */
    public List<Berth> berths() {
        return Collections.unmodifiableList(berths);
    }

    /** Whether this field has any airframe at all that could fly a sortie now. */
    public boolean hasAirworthyAirframe() {
        for (Berth berth : berths) {
            if (berth.airworthy()) return true;
        }
        return false;
    }

    /**
     * Whether this field has an airframe of {@code kind} that could fly now.
     *
     * <p>Kind-aware because the two kinds are not interchangeable, and asking
     * the unqualified question was a real defect: a caller that could only
     * launch from a hardstand asked "is anything airworthy?", got yes because a
     * fighter was sitting in a shed it could never use, and went looking for a
     * stand that was not there. Whoever is going to call
     * {@link #nearestAirworthy(float, float, Kind)} must ask about the same
     * kind, or the supply question and the supply answer disagree.
     */
    public boolean hasAirworthyAirframe(Kind kind) {
        for (Berth berth : berths) {
            if (berth.airworthy() && berth.kind == kind) return true;
        }
        return false;
    }

    /** Whether this field has any berth of {@code kind}, airworthy or not. */
    public boolean hasBerthOfKind(Kind kind) {
        for (Berth berth : berths) {
            if (berth.kind == kind) return true;
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
     * its turnaround.
     *
     * <p><b>It comes back onto its stand to be worked on, not into a hangar
     * nobody can reach.</b> An aircraft under turnaround stands on the concrete
     * with people round it, which is where a turnaround happens and is also the
     * only way the window it opens is worth anything to an attacker — a
     * servicing that took the aircraft off the map for its duration was a
     * promise that the field would answer again shortly and nothing anybody
     * could interrupt. {@link AirfieldSystem} puts it out; the crew works it up.
     */
    public void recover(Berth berth, float hullHp) {
        if (berth.state == BerthState.DESTROYED) return;
        berth.hullHp = Math.max(1f, Math.min(berth.airframe.maxHp(), hullHp));
        berth.state = BerthState.REFITTING;
        berth.refitTarget = repaired(berth);
        berth.refitWork = TURNAROUND_HAND_SECONDS
                + (berth.refitTarget - berth.hullHp) / HULL_PER_HAND_SECOND;
        berth.airframeId = 0L;
    }

    /** How much hull a completed turnaround hands back. */
    float repaired(Berth berth) {
        float repair = berth.airframe.maxHp() * REFIT_REPAIR_FRACTION;
        return Math.min(berth.airframe.maxHp(), berth.hullHp + repair);
    }

    /**
     * The berth serviced from this standing cell, or -1 where none is.
     *
     * <p>The cell rather than the field, because standing on the apron is not
     * working on an aircraft: the same technician on the same rotation is at a
     * board one minute and a hull the next.
     */
    public int berthServicedFrom(int cellX, int cellY) {
        Integer berth = serviceCells.get(key(cellX, cellY));
        return berth == null ? -1 : berth;
    }

    /**
     * Record where this field's servicing is done from, so the work somebody is
     * doing can be matched to the aircraft it is being done to.
     *
     * <p>Handed in rather than derived, because the work is authored beside the
     * berths and this is the same pass telling the field about it. See
     * {@link AirfieldWork}.
     */
    public void installApronWork(List<FixtureTask> work) {
        serviceCells.clear();
        for (FixtureTask task : work) {
            if (task.affordance() != Affordance.SERVICE) continue;
            if (task.berth() == FixtureTask.NO_BERTH) continue;
            serviceCells.put(key(task.cellX(), task.cellY()), task.berth());
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
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

    /**
     * Records a wreck at wherever an airframe died under its own power —
     * taxiing, holding short, mid-roll — with no berth under it to remember
     * it for. {@code AirSystem} calls this directly for a craft it kills in a
     * grounded phase, the same way {@link #burnedOnPad} is this field's record
     * of a hardstand kill.
     */
    public void addGroundWreck(GroundWreck wreck) {
        groundWrecks.add(wreck);
    }

    /** Every wreck this field is carrying that is not sitting on a berth. */
    public List<GroundWreck> groundWrecks() {
        return Collections.unmodifiableList(groundWrecks);
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
