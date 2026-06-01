package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The troop-drop <b>mission</b> of a {@link Shuttle} — the delivery state machine
 * and all the per-sortie lifecycle state, peeled off the shuttle so the
 * shared air-entity core ({@code entityId}, {@link AirBody}, engine + turret
 * components, render state) stays mission-agnostic. A {@link Shuttle} composes
 * one of these; a fighter (planned) would compose a different mission component
 * over the same core. The behavior that drives this data is
 * {@link AirSystem}'s state-machine tick — this is pure data.
 *
 * <p>Lifecycle: PENDING (waiting on stagger) → INCOMING (steering from off-map
 * entry to LZ) → LANDED (deboarding marines) → optional HOVER_STATION (armed
 * fire-support loiter) → DEPARTING (steering to exit) → GONE. With
 * {@link #totalCycles} &gt; 1 the shuttle re-enters PENDING after DEPARTING and
 * flies another sortie.
 */
public final class ShuttleMission {

    /** Current state-machine phase. Driven by {@link AirSystem}. */
    public Shuttle.State state = Shuttle.State.PENDING;

    /** Stagger / re-arm countdown burned down during PENDING before launch. */
    public float pendingDelay;
    /** Countdown to the next marine deboard while LANDED. */
    public float deboardCountdown;
    /** Marines still aboard for the current sortie. */
    public int marinesRemaining;

    /** LZ touchdown point (cells). */
    public final float lzX, lzY;
    /** Off-map entry point the sortie flies in from (cells). */
    public final float entryX, entryY;
    /** Off-map exit point the sortie departs to (cells). */
    public final float exitX, exitY;

    /**
     * Straight-line distance (cells) at the moment the current INCOMING or
     * DEPARTING leg started. Cached so the altitude lerp is
     * {@code altitudeT = distRemaining / legStartDist} without re-derivation;
     * 1 is a safe no-op fallback.
     */
    public float legStartDist = 1f;

    /**
     * Per-deboard loadouts for the <em>current</em> sortie. {@code marineLoadout[i]}
     * is the spec for the (i+1)-th marine to leave (index =
     * {@code type.capacity - marinesRemaining}). Null entries / a null array fall
     * back to a plain {@link MarineLoadout#COMBATANT}. Refreshed from
     * {@link #cycleLoadouts} on each new sortie when cycling.
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
    public float rearmDelay = 8f;

    /**
     * Squad identity stamped on every marine deboarded this sortie. Lazily set
     * to a fresh id on the first deboard; {@link Unit#NO_SQUAD} means "no squad
     * minted yet."
     */
    public int squadId = Unit.NO_SQUAD;

    /**
     * Current HP. Seeded from {@link ShuttleType#maxHp}. Drives the
     * pressure-to-leave HOVER_STATION exit via {@link Shuttle#HOVER_HP_THRESHOLD};
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

    /** Sim-seconds of fire-support fuel left; seeded on HOVER_STATION entry, counted down each tick, hits zero → DEPARTING. */
    public float hoverTimerSec;

    /**
     * Hover station-keeping point (cells), recomputed each HOVER_STATION tick
     * from the squad's alive centroid pulled back along the LZ→centroid bearing.
     * Holds its last value if the squad is wiped.
     */
    public float hoverPointX, hoverPointY;

    /** Counts down from {@link Shuttle#T_TAKEOFF_SEC} on HOVER_STATION entry; drives the smoothstep altitude climb. */
    public float takeoffTimer;

    /**
     * True at HOVER_STATION → DEPARTING so the departing altitude lerp holds at
     * cruise (a hovering shuttle flies away high, not descending then re-climbing).
     * Cleared on cycle reset.
     */
    public boolean departingFromHover;

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
        this.hp = hp;
    }
}
