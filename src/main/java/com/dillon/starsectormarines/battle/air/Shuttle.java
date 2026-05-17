package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.MarineLoadout;
import com.dillon.starsectormarines.battle.Unit;

/**
 * One troop-drop shuttle. Separate from {@link Unit} because shuttles are
 * cinematic / spawn-source entities — they fly in fractional world space, rotate
 * freely, and don't pathfind through the grid or participate in combat. The
 * sim ticks their {@link AirBody} state while {@link com.dillon.starsectormarines.ops.BattleScreen}
 * reads {@code body.x}/{@code body.y}/{@code body.facingDegrees} for the sprite.
 *
 * <p>Lifecycle: PENDING (waiting on stagger) → INCOMING (steering from off-map
 * entry to LZ under {@code BRAKE_TO_STATION}) → LANDED (deboarding marines on
 * {@code deboardInterval} cadence) → DEPARTING (steering to exit under
 * {@code CRUISE}) → GONE. With {@link #totalCycles} > 1 the shuttle re-enters
 * PENDING after DEPARTING and runs another sortie.
 *
 * <p>Coord convention matches {@link Unit#cellX}/{@link Unit#cellY}: cell-units
 * with Y up. Entry/exit points sit outside the grid (negative or > gridH) so
 * the shuttle reads as off-screen before INCOMING and after DEPARTING.
 */
public class Shuttle {

    public enum State { PENDING, INCOMING, LANDED, DEPARTING, GONE }

    public final ShuttleType type;
    public final Faction faction;

    public final float lzX;
    public final float lzY;
    public final float entryX;
    public final float entryY;
    public final float exitX;
    public final float exitY;

    public State state = State.PENDING;
    public float pendingDelay;
    public float deboardCountdown;
    public int marinesRemaining;

    /** Kinematic state for the current sortie — position, velocity, facing, angular velocity. Driven each tick by the sim under the {@link ShuttleType}'s handling profile. */
    public final AirBody body = new AirBody();

    /**
     * Render scale multiplier. 1.0 on the ground; rises to {@code CRUISE_SCALE}
     * while at altitude so the shuttle reads as "bigger because higher" during
     * cruise. Driven by the sim from {@link #altitudeT}.
     */
    public float scaleMult = 1f;

    /**
     * Cruise altitude scalar in [0, 1]. 0 = on the LZ, 1 = at cruising height.
     * Computed each tick from the body's remaining distance to the current
     * waypoint, normalized by the leg's start distance. Drives both
     * {@link #scaleMult} and {@link #engineIntensity()} — one source of truth
     * for "how high am I right now."
     */
    public float altitudeT = 1f;

    /**
     * Straight-line distance (cells) at the moment the current INCOMING or
     * DEPARTING leg started. Cached so the sim can compute
     * {@code altitudeT = distRemaining / legStartDist} without per-tick
     * re-derivation. Set to a positive number at state entry; 1 is a safe
     * fallback that turns the lerp into a no-op.
     */
    public float legStartDist = 1f;

    /**
     * Accumulated phase (radians) for the in-flight scale wobble. Advances each
     * tick while airborne so the sprite breathes slightly during cruise. Per-shuttle
     * random offset at construction so simultaneous shuttles don't pulse in sync.
     */
    public float flightPhase = 0f;

    /**
     * Per-deboard loadouts for the <em>current</em> sortie. {@code marineLoadout[i]}
     * is the spec for the (i+1)-th marine to leave this shuttle (i.e., index =
     * type.capacity - marinesRemaining). Null entries — and a null array — fall
     * back to a plain {@link MarineLoadout#COMBATANT} marine.
     *
     * <p>When this shuttle is cycling ({@link #totalCycles} > 1), this field is
     * refreshed on each new sortie from {@link #cycleLoadouts} so the per-cycle
     * planter targeting (and any future per-cycle roles) lands correctly.
     */
    public MarineLoadout[] marineLoadout;

    /**
     * Full per-sortie loadout schedule when this shuttle cycles. Length equals
     * {@link #totalCycles}. {@code null} (or a null entry) falls back to plain
     * combatants for that cycle, matching the default for non-SABOTAGE missions.
     */
    public MarineLoadout[][] cycleLoadouts;

    /** Sortie index within {@link #totalCycles}. 0 on first launch; incremented after each successful DEPARTING. */
    public int currentCycle = 0;
    /** Total sorties this shuttle will fly across the battle. 1 = single drop (no cycling); larger = repeat the state machine that many times. */
    public int totalCycles = 1;
    /** Sim-seconds of "offstage rearm" between sorties when cycling. The shuttle drops out of view (state = PENDING) for this long before re-entering INCOMING. */
    public float rearmDelay = 8f;

    /**
     * Squad identity assigned to all marines deboarded from this shuttle.
     * Lazily set to a fresh id on the first successful deboard;
     * {@link Unit#NO_SQUAD} means "no squad has been created for this shuttle
     * yet." Defenders' shuttles (none today, but the field is faction-agnostic)
     * would get their own squad on the same path.
     */
    public int squadId = Unit.NO_SQUAD;

    public Shuttle(ShuttleType type, Faction faction,
                   float lzX, float lzY,
                   float entryX, float entryY,
                   float exitX, float exitY,
                   float pendingDelay) {
        this.type = type;
        this.faction = faction;
        this.lzX = lzX;
        this.lzY = lzY;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        this.pendingDelay = pendingDelay;
        this.marinesRemaining = type.capacity;
        body.teleport(entryX, entryY, AirBody.facingToward(lzX - entryX, lzY - entryY));
    }

    public boolean isVisible() {
        return state == State.INCOMING || state == State.LANDED || state == State.DEPARTING;
    }

    /**
     * Normalized engine loudness/pitch driver for the shuttle engine loop, in [0, 1].
     * Full throttle at cruise, idles on the ground, smoothly blends between via
     * {@link #altitudeT}. PENDING and GONE return 0 so off-screen shuttles don't
     * contribute to the loop.
     *
     * <p>The driving loop in {@code BattleScreen} takes the max across visible shuttles —
     * three simultaneous landings don't triple the volume; the loudest one wins.
     */
    public float engineIntensity() {
        if (state == State.PENDING || state == State.GONE) return 0f;
        return IDLE_INTENSITY + (1f - IDLE_INTENSITY) * altitudeT;
    }

    /** Engine intensity while parked on the ground — quiet hum, not silent. */
    private static final float IDLE_INTENSITY = 0.3f;
}
