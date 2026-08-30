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
 * <p>Lifecycle: PENDING (waiting on stagger) → INCOMING (steering from off-map
 * entry to LZ) → LANDED (deboarding marines or awaiting rescue passengers) →
 * optional HOVER_STATION (armed fire-support loiter) → DEPARTING (steering to
 * exit) → GONE. With
 * {@link #totalCycles} &gt; 1 the shuttle re-enters PENDING after DEPARTING and
 * flies another sortie.
 */
public final class ShuttleMission {

    /** Physical cargo recipe. Infantry preserves the historical default. */
    public AirDeliveryPayload payload = InfantryPayload.INSTANCE;

    /** Ownership claimed when this mission first mints its transported squad. */
    public SquadCommandClaim commandClaim;

    /** HP fraction below which the shuttle aborts HOVER_STATION and departs. Default 0.4 = 40%. */
    public static final float HOVER_HP_THRESHOLD = 0.4f;

    /** Sim-seconds the LANDED → HOVER_STATION takeoff takes. */
    public static final float T_TAKEOFF_SEC = 2.0f;

    /** Standoff (cells) the hover point is pulled back from the squad centroid along the LZ→centroid bearing. */
    public static final float HOVER_STANDOFF_CELLS = 5f;

    /** Current state-machine phase. Driven by {@link AirSystem}. */
    public ShuttleState state = ShuttleState.PENDING;

    /** Stagger / re-arm countdown burned down during PENDING before launch. */
    public float pendingDelay;
    /** Countdown to the next marine deboard while LANDED. */
    public float deboardCountdown;
    /** Marines still aboard for the current sortie. */
    public int marinesRemaining;
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
     * Current HP. Seeded from {@link ShuttleType#maxHp}. Drives the
     * pressure-to-leave HOVER_STATION exit via {@link #HOVER_HP_THRESHOLD};
     * no damage source exists yet (anti-air is a follow-up) so it's effectively
     * constant today, wired forward.
     */
    public float hp;

    /**
     * Fire-support role, or {@code null} on a pure transport. Drives turret kit
     * selection at setup and the HOVER_STATION-vs-immediate-DEPARTING choice —
     * a null role departs immediately after deboard.
     */
    public TurretRole assignedRole;

    /** Post-unload behavior, independent of whether the craft is armed. */
    public PostDeliveryDisposition postDeliveryDisposition =
            PostDeliveryDisposition.LOITER_IF_ARMED;

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
     * <p>The threshold is chosen once, here, from where the sortie is going:
     * the far one, so the roll runs toward the destination and the aircraft
     * leaves the strip already pointing at it. Deciding it per tick would let
     * it change while the craft was halfway down its own taxiway.
     */
    public void departFromRunway(Runway strip, float shelterX, float shelterY,
                                 float towardX, float towardY) {
        float[] threshold = strip.departureThreshold(towardX, towardY);
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
        this.holdX = rollout[0];
        this.holdY = rollout[1];
        this.shelterX = shelterX;
        this.shelterY = shelterY;
        this.usesRunway = true;
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

    /** Sim-seconds of fire-support fuel left; seeded on HOVER_STATION entry, counted down each tick, hits zero → DEPARTING. */
    public float hoverTimerSec;

    /**
     * Hover station-keeping point (cells), recomputed each HOVER_STATION tick
     * from the squad's alive centroid pulled back along the LZ→centroid bearing.
     * Holds its last value if the squad is wiped.
     */
    public float hoverPointX, hoverPointY;

    /** Counts down from {@link #T_TAKEOFF_SEC} on HOVER_STATION entry; drives the smoothstep altitude climb. */
    public float takeoffTimer;

    /**
     * True at HOVER_STATION → DEPARTING so the departing altitude lerp holds at
     * cruise (a hovering shuttle flies away high, not descending then re-climbing).
     * Cleared on cycle reset.
     */
    public boolean departingFromHover;

    /**
     * On-map / drawable: the craft is somewhere over the grid (descending,
     * landed, loitering, or egressing) rather than off-map waiting to launch
     * (PENDING) or finished (GONE). The render + audio + vision passes gate on
     * this, and it is the complement of the off-map states the engine-intensity
     * derivation zeroes.
     */
    public boolean isVisible() {
        return state == ShuttleState.INCOMING || state == ShuttleState.LANDED
                || state == ShuttleState.HOVER_STATION || state == ShuttleState.DEPARTING;
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
                          int marinesRemaining, float hp) {
        this.lzX = lzX;
        this.lzY = lzY;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        this.pendingDelay = pendingDelay;
        this.marinesRemaining = marinesRemaining;
        this.seatsPerSortie = marinesRemaining;
        this.hp = hp;
    }
}
