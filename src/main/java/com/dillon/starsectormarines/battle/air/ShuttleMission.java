package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.Runway;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The troop-drop <b>mission</b> of an air craft — the delivery state machine
 * and all the per-sortie lifecycle state, the {@code SHUTTLE_MISSION} component
 * of an air entity. The shared air-entity core ({@code entityId}, {@link AirBody}
 * kinematics, {@code APPEARANCE}, engine + turret components) stays
 * mission-agnostic: an air craft composes one of these by id; a fighter (planned)
 * would compose a different mission component over the same core. The behavior
 * that drives this data is {@link AirSystem}'s state-machine tick — this is pure
 * data.
 *
 * <p>Lifecycle: PENDING (waiting on stagger) → optional LOADING → optional
 * PAD_ASCENT (climbing off a hardstand) → INCOMING (steering from the entry
 * point to the LZ) → PAD_DESCENT (settling onto it) → LANDED (deboarding
 * marines or awaiting rescue passengers) → DEPARTING (steering to exit) →
 * GONE. With {@link #totalCycles} &gt; 1 the shuttle re-enters PENDING after
 * DEPARTING and flies another sortie.
 */
public final class ShuttleMission {

    /** Physical cargo recipe. Infantry preserves the historical default. */
    public AirDeliveryPayload payload = InfantryPayload.INSTANCE;

    /** Ownership claimed when this mission first mints its transported squad. */
    public SquadCommandClaim commandClaim;

    /** Current state-machine phase. Driven by {@link AirSystem}. */
    public ShuttleState state = ShuttleState.PENDING;

    /** Stagger / re-arm countdown burned down during PENDING before launch. */
    public float pendingDelay;
    /** Countdown to the next marine deboard while LANDED. */
    public float deboardCountdown;
    /** Marines still aboard for the current sortie. */
    public int marinesRemaining;
    /**
     * Seconds between deboards on this sortie.
     *
     * <p>On the sortie rather than read off the airframe each tick, for the
     * same reason {@link #seatsPerSortie} is: the hull says what it can do and
     * the sortie says what it is doing. It also keeps a transport-shaped
     * number off {@link Airframe}, which a fighter would have to answer
     * meaninglessly.
     */
    public float deboardInterval = 0.6f;

    /**
     * Marines embarked on each infantry sortie. This may be lower than the
     * carrier's physical capacity and is restored unchanged after re-arm.
     */
    public final int seatsPerSortie;
    /**
     * Marines that have already deboarded this sortie — the {@link #marineLoadout} index for the next
     * one to leave. Kept independent of {@link #marinesRemaining} so a <em>partial</em> sortie (fewer
     * than {@link ShuttleType#capacity} aboard, e.g. the last ship of a D5 wave) still indexes loadouts
     * from 0 rather than from {@code capacity − marinesRemaining}. Reset to 0 at each sortie.
     */
    public int deboardedThisSortie;

    /**
     * Rescue pickup craft remain on the ground after arriving with an empty
     * hold. The evacuation system clears this flag after the last active
     * civilian is either aboard or lost, allowing the ordinary departure leg
     * to take over.
     */
    public boolean awaitingEvacuees;
    /** Number of rescued civilians represented aboard this craft. */
    public int evacueesAboard;
    /** Maximum representative civilians this craft was dispatched to collect. */
    public int evacueeCapacity;

    /** Marks a local-militia reinforcement sortie for rescue-mission accounting. */
    public boolean rescueMilitiaTransport;
    /** Marks the one-off mech delivery assigned to the rescue pickup line. */
    public boolean rescuePickupMechTransport;
    /** Optional physical mech variant carried by a mech-support sortie. */
    public MechVariant mechVariant;
    /**
     * Ordered chassis carried by a multi-mech support sortie. The payload uses
     * {@link #deboardedThisSortie} as its index; null/empty falls back to
     * {@link #mechVariant} for ordinary one-mech and rescue deliveries.
     */
    public MechVariant[] mechVariants;
    /**
     * Frozen configured mechs carried by campaign support. When present this
     * owns chassis, doctrine, and installed subsystem for each deboard slot.
     */
    public MechDeploymentSpec[] mechDeployments;
    /** Ordered rescue-perimeter points patrolled by a delivered pickup mech. */
    public int[] rescuePatrolCells;
    /** Fixed perimeter anchor assigned to the militia squad when it deboards. */
    public int rescueGuardX = -1;
    public int rescueGuardY = -1;

    /** LZ touchdown point (cells). */
    public final float lzX, lzY;
    /** Stable source-manifest position, or -1 when the spawning path has no manifest. */
    public int manifestOrdinal = -1;
    /** Mission-authored landing-area identity, or -1 for a legacy point landing. */
    public int landingAreaId = -1;
    /** Mission-authored multi-transport squad-delivery group, or -1 when ungrouped. */
    public int arrivalGroupId = -1;
    /** Ground strength this arrival group is assembling toward; 0 leaves legacy sizing in force. */
    public int expectedArrivalStrength;
    /** Off-map entry point the sortie flies in from (cells). */
    public final float entryX, entryY;
    /**
     * Exit point the sortie departs to (cells). Mutable so the owner can retarget the egress
     * mid-sortie toward a moving point — e.g. a bridge drop-ship flying home to its (orbiting) host
     * carrier, falling back to an off-map egress only once the carrier has left. {@link AirSystem}
     * reads it live each DEPARTING tick, so a retarget just re-steers the egress in flight.
     */
    public float exitX, exitY;

    /**
     * Straight-line distance (cells) at the moment the current INCOMING or
     * DEPARTING leg started. Cached so the altitude lerp is
     * {@code altitudeT = distRemaining / legStartDist} without re-derivation;
     * 1 is a safe no-op fallback.
     */
    public float legStartDist = 1f;

    /**
     * Per-deboard loadouts for the <em>current</em> sortie. {@code marineLoadout[i]}
     * is the spec for the (i+1)-th marine to leave (index = {@link #deboardedThisSortie}).
     * Null entries / a null array fall back to a plain {@link MarineLoadout#COMBATANT}.
     * Refreshed from {@link #cycleLoadouts} on each new sortie when cycling.
     */
    public MarineLoadout[] marineLoadout;

    /**
     * Full per-sortie loadout schedule when cycling. Length equals
     * {@link #totalCycles}; {@code null} (or a null entry) falls back to plain
     * combatants for that cycle.
     */
    public MarineLoadout[][] cycleLoadouts;

    /** Sortie index within {@link #totalCycles}. 0 on first launch; bumped after each DEPARTING. */
    public int currentCycle = 0;
    /** True after this sortie has reserved every persistent squad slot it carries. */
    public boolean fieldPresenceAdmitted;
    /** Total sorties across the battle. 1 = single drop; larger = repeat the state machine that many times. */
    public int totalCycles = 1;
    /** Sim-seconds of offstage re-arm between sorties when cycling. */
    public static final float DEFAULT_REARM_DELAY_SEC = 8f;
    public float rearmDelay = DEFAULT_REARM_DELAY_SEC;

    /**
     * Squad identity stamped on every marine deboarded this sortie. Lazily set
     * to a fresh id on the first deboard; {@link Squad#NO_SQUAD} means "no squad
     * minted yet."
     */
    public int squadId = Squad.NO_SQUAD;

    /**
     * Fire-support role, or {@code null} on a pure transport. Drives turret kit
     * selection at setup — what an armed craft carries on its way in and out,
     * not a reason to stay.
     */
    public TurretRole assignedRole;

    /**
     * Optional override of the {@link UnitType} stamped on each deboarded marine.
     * {@code null} picks the faction's bulk infantry; reinforcement drops set it
     * to the elite slot.
     */
    public UnitType deboardUnitType;

    /**
     * Compound this sortie's squad should garrison, or {@code null} for
     * assault / reinforcement drops. Non-null stamps a {@code HOLD_NODE}
     * objective on the freshly-minted squad so it's born into the garrison
     * behavior. See {@code CompoundGarrisonSystem}.
     */
    public TacticalNode garrisonNode;

    /**
     * Tactical node stamped as the deboarded squad's {@link Squad#assignedNode}
     * at squad mint — the recapture-target objective a progressive-reinforcement
     * drop should advance on and re-man (see
     * {@code reinforcement-nouns.md}, the "assign
     * at deboard, not on arrival" contract). Distinct from {@link #garrisonNode},
     * which stamps a marine {@code HOLD_NODE} <em>objective</em> — this only sets
     * the squad's spawn-time anchor. {@code null} for drops with no objective
     * (assault / overflow).
     */
    public TacticalNode assignNode;

    /**
     * Sim-seconds this craft has been on the ground without managing to set
     * anybody down. Reset by every successful deboard.
     *
     * <p>A delivery that cannot find anywhere to put a passenger is a delivery
     * that fails, not one that waits: without a bound the craft holds its LZ
     * for the rest of the battle with its passengers still aboard, and the
     * reinforcement neither arrives nor is ever reported as lost.
     */
    public float unloadStalledFor;

    /**
     * The berth this sortie flew off, or null for a craft that came from
     * off-map.
     *
     * <p>An aircraft based on a field is borrowed from a hardstand and owes
     * itself back to it. Carrying the berth on the mission is what closes that
     * loop: the craft knows where home is, so a completed sortie parks and a
     * lost one writes the berth off, without the field having to guess which of
     * its aircraft failed to come back.
     */
    public AirfieldService.Berth homeBerth;

    /**
     * Where the taxi out ends and the roll begins — the runway threshold this
     * sortie departs from.
     *
     * <p>Chosen once at dispatch rather than each tick, because the threshold a
     * craft is taxiing to must not change while it is halfway there.
     */
    public float holdX, holdY;
    /** The far threshold: where the takeoff roll ends and the aircraft is flying. */
    public float rollX, rollY;
    /**
     * The shelter this aircraft came out of and taxis back into.
     *
     * <p>Its own, not the nearest. A base where every returning aircraft picked
     * the closest free shed would shuffle its complement around over a battle
     * and lose the one thing a shelter is for: knowing which aircraft is where.
     */
    public float shelterX, shelterY;
    /**
     * Whether this sortie's business at the objective is its guns rather than
     * its ramp.
     *
     * <p>A transport arrives, touches down, sets people on the ground and
     * leaves. A strike aircraft never lands on what it was sent to attack: it
     * arrives on station, works, and turns for home. Without this the fire
     * support was reached by way of a touchdown, so a fighter sent against an
     * objective put its wheels down on it for a tick first — which reads
     * exactly as absurdly as it sounds and also handed the objective a
     * stationary target at zero altitude.
     */
    public boolean strikeSortie;

    /**
     * The line this attack run is flying: in from {@code runFrom}, through the
     * target, out to {@code runTo}.
     *
     * <p>Held for the whole run rather than recomputed per tick, because a run
     * that re-aims while it is being flown is a hover with extra steps. The
     * target moves; the run does not.
     */
    public float runFromX, runFromY, runToX, runToY;

    /** Passes this sortie has left before it turns for home. */
    public int passesLeft;

    /** Counts down to the next round while the aircraft is firing. */
    public float fireCooldown;

    /**
     * Rounds left on this pass for a weapon that carries a finite load, and
     * ignored by one that fires as long as it has a target.
     *
     * <p>Reset when a pass begins rather than when the sortie does, because a
     * bomber that came back round with an empty bay would be flying a circuit
     * for nothing.
     */
    public int roundsLeftThisPass;

    /**
     * What this sortie is carrying, cached off the airframe when it launches.
     *
     * <p>On the sortie rather than read through the airframe on every shot, so
     * a load that is spent, swapped or halved belongs to this trip and not to
     * the type of aircraft.
     */
    public AirOrdnance ordnance;

    /** Bearing the last run came in on, so the next one comes from somewhere else. */
    public float lastRunBearingDeg;

    /**
     * The solved approach this homebound craft is flying, or null before one
     * has been worked out.
     *
     * <p>Cleared rather than edited whenever the approach stops being the one
     * to fly — the craft is down, or it was denied the strip and is going
     * round again. A path is the answer to a question asked from one pose, so
     * a new pose is a new question.
     */
    public RunwayApproach approach;

    /**
     * How many circuits this craft has already flown for want of the strip.
     *
     * <p>A go-around is the right answer to a strip somebody else is on, and an
     * unbounded one is what turns a recoverable minute into a permanent
     * condition: an aircraft that loses the race every time it arrives circles
     * for the rest of the battle. Counted so the approach can stop asking
     * politely — see {@code AirSystem}'s emergency landing.
     */
    public int goAroundsFlown;

    /**
     * Sim-seconds spent so far in the current pad phase — {@link ShuttleState#PAD_ASCENT}
     * or {@link ShuttleState#PAD_DESCENT}. Reset to 0 on entry to either.
     *
     * <p>The ascent's climb is timed directly off this: there is nothing
     * physical to wait for in a vertical climb held over one spot, so its
     * height is simply this elapsed time normalised against how long a climb
     * takes. The descent reads it only as a safety bound — its actual
     * completion is a condition on the body ({@link #padDescentEntrySpeed}
     * and the craft's live position), never this clock, because a clock
     * raced against a bus-tier hull's gentle brakes is exactly the fault this
     * field used to cause: the settle ended on a stated duration regardless of
     * whether the craft had actually killed its speed, so a heavy hull was
     * snapped to a stop still travelling. What is left of the clock here is
     * only the guard against a settle that, for some reason, never converges —
     * see {@code AirSystem#MAX_PAD_SETTLE_SEC}.
     */
    public float padPhaseElapsed;

    /**
     * The craft's speed at the moment it entered {@link ShuttleState#PAD_DESCENT}.
     *
     * <p>The descent's visible height is driven by how much of that speed is
     * still left — 1 at entry, 0 once the drift is killed — rather than by a
     * clock, so what the eye sees sinking is tied to the same quantity the
     * settle is actually waiting on.
     */
    public float padDescentEntrySpeed;

    /** The threshold this approach is aimed at — where the aircraft flies itself onto the strip. */
    public float touchdownX, touchdownY;

    /**
     * The route this aircraft is taxiing, and how far along it is.
     *
     * <p>An aircraft on its wheels goes round the hangar it came out of rather
     * than through it. Steering straight at the threshold crossed whatever was
     * in between — the shed's own back wall included — because a body built for
     * flight has nothing that stops it. The taxi follows walkable ground like
     * anything else on the ground does; the roll afterwards is a straight line
     * down a strip that is clear by construction.
     */
    public int[] taxiPath;
    public int taxiLeg;

    /**
     * Nosewheel deflection this craft is carrying, as a fraction of full lock.
     *
     * <p>The one piece of state {@link GroundDriveSystem} needs and the only
     * reason it is not a pure function of the body: an aircraft's wheel is
     * where the last tick left it, and snapping it to whatever the route asks
     * for is what makes a turn read as a computed heading rather than as
     * something being steered.
     */
    public float groundSteer;

    /** Forgets the current taxi route. */
    public void clearTaxiRoute() {
        taxiPath = null;
        taxiLeg = 0;
        groundSteer = 0f;
    }

    /**
     * Whether this sortie rolls.
     *
     * <p>The discriminator for the whole ground procedure. False for every
     * vertical-lift craft — which is most of them — and the reason the added
     * phases cost a shuttle nothing.
     */
    public boolean usesRunway;

    /**
     * Sends this sortie out of its shelter and down the strip toward
     * {@code (towardX, towardY)}.
     *
     * <p>The threshold is chosen once, here, from where the sortie is starting
     * and where it is going — the end that costs the least turning across the
     * whole procedure, which is normally the far one but is not when the craft
     * is parked beside the near one. Deciding it per tick would let it change
     * while the craft was halfway down its own taxiway.
     */
    public void departFromRunway(Runway strip, float shelterX, float shelterY,
                                 float towardX, float towardY) {
        float[] threshold = strip.departureThreshold(shelterX, shelterY, towardX, towardY);
        float[] far = strip.opposite(threshold);
        this.holdX = threshold[0];
        this.holdY = threshold[1];
        this.rollX = far[0];
        this.rollY = far[1];
        this.shelterX = shelterX;
        this.shelterY = shelterY;
        this.usesRunway = true;
        this.state = ShuttleState.TAXI_OUT;
    }

    /**
     * Brings this sortie home onto the strip from {@code (fromX, fromY)}.
     *
     * <p>An aircraft lands into the end it arrives at rather than flying the
     * length of its own runway first, so the rollout runs toward the far
     * threshold — the opposite choice to a departure, for the same reason. That
     * rollout target becomes the hold point, because it is where the craft
     * leaves the strip and where the taxi in begins.
     *
     * <p>Only the ground half. Where the approach is aimed is an LZ, and an LZ
     * is decided when the sortie is dispatched and never moved afterwards; a
     * caller flying a craft home points it at
     * {@link Runway#touchdownThreshold} and lands there.
     */
    public void landOnRunway(Runway strip, float fromX, float fromY,
                             float shelterX, float shelterY) {
        float[] rollout = strip.departureThreshold(fromX, fromY);
        float[] touchdown = strip.opposite(rollout);
        this.holdX = rollout[0];
        this.holdY = rollout[1];
        // Both ends, together. The rollout target alone is not enough to fly
        // a landing: the approach and the roll both need to know which way down
        // the strip the aircraft is pointing, and deriving that from one end
        // and the craft's position gets it wrong the moment the craft is past
        // the threshold.
        this.touchdownX = touchdown[0];
        this.touchdownY = touchdown[1];
        this.shelterX = shelterX;
        this.shelterY = shelterY;
        this.usesRunway = true;
        // A fresh landing, so a fresh patience. A craft on its second sortie
        // must not inherit the circuits its first one flew.
        this.goAroundsFlown = 0;
    }

    /**
     * Zone fallback for an objective that is not backed by a tactical node.
     *
     * <p>Not every objective a request names sits on an authored place. A lost
     * zone is somewhere the defender used to hold and no longer does, and the
     * nearest tactical node may be well outside the tolerance that would make
     * it the same position. Without this the squad lands owned but with nothing
     * to do, and the objective the sortie was flown for is lost at the ramp.
     * Mirrors {@link com.dillon.starsectormarines.battle.vehicle.VehicleMission#assignZoneId}.
     */
    public int assignZoneId = ObjectiveAssignment.UNSCOPED;

    /**
     * Whether the delivering authority owns {@link #assignNode} as a real
     * objective, rather than merely naming somewhere for the squad to start.
     *
     * <p>Set, the deboarded squad is claimed <em>with</em> its task — a hold on
     * that node, or a clear of {@link #assignZoneId} when the objective has no
     * node — so it lands already doing the thing the sortie was flown for and
     * its owner can retask it once that is done. Unset — a drop with no
     * commanding authority to ask — the squad is claimed bare, exactly as
     * before. Same field and same meaning as the convoy's.
     */
    public boolean commandOwnsObjective;

    /**
     * On the map, and therefore drawn: anywhere between leaving a berth and
     * being finished with one.
     *
     * <p>Stated as what it is <em>not</em> — off-map waiting to launch, or
     * done — rather than as a list of the phases that qualify. It was a list
     * once, and every phase added since was left out of it: a craft loading on
     * its pad, taxiing, holding short, rolling, or taxiing back in was not
     * drawn at all. That is a costly thing to get wrong here, because the whole
     * of what a strip buys over a vertical lift is a minute of ground movement
     * somebody can see and shoot at.
     */
    public boolean isOnMap() {
        return state != ShuttleState.PENDING && state != ShuttleState.GONE;
    }

    /**
     * Out over the battlefield on its delivery legs, rather than off-map or
     * moving about its own field. What the craft's guns and its sensors both
     * key off.
     *
     * <p>Deliberately not {@link #isOnMap()}. An aircraft taxiing to the strip
     * is on the map and is drawn, and it is also nose-to-tail with its own
     * ground crew inside its own perimeter — the last place a hull-mounted
     * autocannon should be hunting for targets, and not somewhere a
     * fifty-cell air search should be sweeping from either.
     *
     * <p>Written as an exhaustive switch rather than the list this used to be.
     * The list once read INCOMING, PAD_DESCENT, LANDED, DEPARTING, RETURNING —
     * every phase that existed when it was written — and {@link ShuttleState#ATTACK_RUN}
     * and {@link ShuttleState#REPOSITION} were added afterwards and simply
     * never got a mention, which made a strike aircraft's whole time on
     * station invisible to fog reveal and mute on its own turrets. A switch
     * with no {@code default} forces every phase this enum ever grows to be
     * placed on one side or the other before the project compiles again; a
     * list just grows a hole.
     */
    public boolean isOverTheBattle() {
        return switch (state) {
            case PENDING, GONE,
                 LOADING, PAD_ASCENT,
                 TAXI_OUT, HOLDING_SHORT, TAKEOFF_ROLL,
                 LANDING_ROLL, TAXI_IN -> false;
            case INCOMING, PAD_DESCENT, ATTACK_RUN, REPOSITION,
                 LANDED, DEPARTING, RETURNING -> true;
        };
    }

    /**
     * Whether the aircraft is out in the open on its wheels under its own
     * power.
     *
     * <p><b>No longer a targeting gate.</b> Whether a given shooter can engage
     * this craft is {@code EngagementService.canEngage}, which asks the carrier
     * for presence and altitude and the shooter for whether it can reach up;
     * this predicate said "anybody with a weapon can shoot at it", which was
     * true of the only shooters that existed and stopped being the shape of the
     * question. What is left for it is the one thing it genuinely decides:
     * whether a kill leaves a hull on the ground or a machine falling out of
     * the sky.
     *
     * <p>This is what a runway is <em>for</em>. A strip buys a minute of
     * movement across open ground in exchange for not lifting vertically off a
     * stand, and the trade is worth nothing while the minute is invulnerable.
     *
     * <p>Asked of the locomotion rather than listed, because "is it on its
     * wheels" is exactly what {@link AirLocomotion#GROUNDED} means and a list
     * is a thing the next phase added gets left out of. Two grounded phases
     * are deliberately excluded, and both are down with the ramp open rather
     * than moving: a loading craft's passengers have already been taken off
     * the roster, so making it shootable would owe them a disposition nothing
     * gives them, and a landed one is the same craft at the other end of the
     * trip.
     */
    public boolean isOnItsWheelsAndExposed() {
        return AirLocomotion.of(state) == AirLocomotion.GROUNDED
                && state != ShuttleState.LOADING
                && state != ShuttleState.LANDED;
    }

    /**
     * Squad walking out to embark, or {@link com.dillon.starsectormarines.battle.squad.Squad#NO_SQUAD}
     * on a sortie that was loaded before it existed.
     *
     * <p>Distinct from {@link #squadId}, which is the squad this sortie
     * <em>delivers</em> and is minted at the far end. One is who gets on; the
     * other is who gets off.
     */
    public int embarkSquadId = Squad.NO_SQUAD;

    /**
     * Seconds a loading craft waits on its pad before going with whoever made
     * it aboard.
     *
     * <p>There has to be a deadline, because the squad walking out to the pad
     * can be shot on the way. Without one a sortie whose squad died in the yard
     * holds a hardstand for the rest of the battle and the air arm silently
     * stops existing.
     */
    public float boardingPatience;

    /**
     * Who takes command of the boarding party when this sortie closes, or null
     * to simply let it go.
     *
     * <p>A sortie borrows people; it should not keep them. While loading, the
     * crew is held at reinforcement authority so nothing outranks the lift and
     * pulls it apart mid-boarding. That is right for the ninety seconds it
     * lasts and wrong forever after: the survivors of a scrubbed sortie, or the
     * ones a full aircraft left behind, would otherwise stand on the pad under
     * an authority the mission commander cannot outbid, for the rest of the
     * battle, while each new sortie marched four more out to join them.
     *
     * <p>Handing them over rather than releasing them is deliberate. An
     * unclaimed squad is not in the commander's pool — the pool is what it
     * owns — so a bare release would leave them exactly as stranded, just
     * without a label. This is the claim the delivery policy already mints for
     * the squads a convoy brings in, asked for the same thing.
     */
    public SquadCommandClaim embarkHandoff;

    public ShuttleMission(float lzX, float lzY, float entryX, float entryY,
                          float exitX, float exitY, float pendingDelay,
                          int marinesRemaining) {
        this.lzX = lzX;
        this.lzY = lzY;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        this.pendingDelay = pendingDelay;
        this.marinesRemaining = marinesRemaining;
        this.seatsPerSortie = marinesRemaining;
    }
}
