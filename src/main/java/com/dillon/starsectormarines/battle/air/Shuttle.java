package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

/**
 * One troop-drop shuttle. Separate from {@link Unit} because shuttles are
 * cinematic / spawn-source entities — they fly in fractional world space, rotate
 * freely, and don't pathfind through the grid or participate in combat. The
 * sim ticks their {@link AirBody} state while {@link com.dillon.starsectormarines.ops.BattleScreen}
 * reads {@code body.x}/{@code body.y}/{@code body.facingDegrees} for the sprite.
 *
 * <p>Lifecycle: PENDING (waiting on stagger) → INCOMING (steering from off-map
 * entry to LZ under {@code BRAKE_TO_STATION}) → LANDED (deboarding marines on
 * {@code deboardInterval} cadence) → optional HOVER_STATION (loitering above the
 * LZ providing fire support if armed) → DEPARTING (steering to exit under
 * {@code CRUISE}) → GONE. With {@link #totalCycles} > 1 the shuttle re-enters
 * PENDING after DEPARTING and runs another sortie.
 *
 * <p>Coord convention matches {@link Unit#cellX}/{@link Unit#cellY}: cell-units
 * with Y up. Entry/exit points sit outside the grid (negative or > gridH) so
 * the shuttle reads as off-screen before INCOMING and after DEPARTING.
 */
public class Shuttle {

    public enum State { PENDING, INCOMING, LANDED, HOVER_STATION, DEPARTING, GONE }

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

    /**
     * Current HP. Initialized from {@link ShuttleType#maxHp} at construction.
     * Drives the pressure-to-leave exit during HOVER_STATION via
     * {@link #HOVER_HP_THRESHOLD}. No damage source exists yet — anti-air
     * lands as a follow-up — so today this is effectively constant; the field
     * is wired forward so that wiring is already in place when AA arrives.
     */
    public float hp;

    /**
     * Combat role assigned to this shuttle for the current mission. Null on
     * pure transports (and any hardpoint shuttle the player elected not to
     * arm in future briefing UI). Drives both the kit selection at construction
     * and the HOVER_STATION transition after deboard — null role skips the
     * hover entirely and departs immediately.
     */
    public TurretRole assignedRole;

    /**
     * Optional override for the {@link UnitType} stamped on each deboarded
     * marine. {@code null} (default) means {@code AirSystem.tryDeboardMarine}
     * picks {@code FactionUnitRoster.forFaction(faction).infantry()} — the
     * bulk type for this side. Reinforcement {@code ShuttleMeans} sets this
     * to the elite slot so air-drop reinforcement reads as "stiffening
     * delivery"; the player's normal shuttle drops leave it null and get
     * the faction's standard infantry.
     */
    public UnitType deboardUnitType;

    /**
     * Compound this shuttle's deboarded squad should garrison, or {@code null}
     * for assault / reinforcement drops. Non-null only for
     * {@link com.dillon.starsectormarines.battle.command.compound.CompoundGarrisonSystem}
     * holding drops: {@code AirSystem.tryDeboardMarine} stamps the freshly
     * minted squad with a {@code HOLD_NODE} objective for this node, so the
     * garrison is born into the {@code GarrisonCompound} behavior (patrol the
     * compound, re-clear rooms on counter-attack) instead of waiting for — or
     * being pulled off by — a commander assignment.
     */
    public TacticalNode garrisonNode;

    /**
     * Mounted turrets, sized to {@code type.hardpoints} and populated from
     * {@link ShuttleType#kitFor} when {@link #assignedRole} is non-null.
     * Empty (length-0) array on pure transports — the per-tick turret pass
     * is a no-op in that case.
     */
    public MountedTurret[] turrets = new MountedTurret[0];

    /**
     * Sim-seconds of fire-support fuel remaining. Initialized from
     * {@link ShuttleType#fireSupportSec} when transitioning to HOVER_STATION
     * and counted down each tick. Hits zero → DEPARTING.
     */
    public float hoverTimerSec;

    /**
     * World coordinates of the hover station-keeping point. Recomputed each
     * tick during HOVER_STATION from the squad's alive centroid (pulled back
     * by {@link #HOVER_STANDOFF_CELLS} along the LZ→centroid bearing), so the
     * shuttle follows its delivered marines as they advance with no maximum
     * distance from the LZ. If the squad is wiped, the field holds at its
     * last value.
     */
    public float hoverPointX, hoverPointY;

    /**
     * Counts down from {@link #T_TAKEOFF_SEC} on entry to HOVER_STATION.
     * While > 0, {@link #altitudeT} is driven by a smoothstep ramp from 0
     * (just landed) to 1 (cruise altitude) — gives the shuttle an
     * acceleration / deceleration takeoff instead of popping into the air.
     * Reset on every cycle's LANDED → HOVER_STATION transition.
     */
    public float takeoffTimer;

    /**
     * Set true at the HOVER_STATION → DEPARTING transition so the departing
     * leg's altitude lerp doesn't pretend the shuttle is taking off from the
     * ground again — a shuttle that was hovering at cruise altitude flies
     * away at cruise altitude rather than descending and re-climbing.
     * Cleared on cycle reset.
     */
    public boolean departingFromHover;

    /** HP fraction below which the shuttle aborts HOVER_STATION and departs. Default 0.4 = 40%. */
    public static final float HOVER_HP_THRESHOLD = 0.4f;

    /** Sim-seconds the LANDED → HOVER_STATION takeoff takes. Long enough for the lift to read as gradual climb, short enough that the shuttle gets to firing position promptly. */
    public static final float T_TAKEOFF_SEC = 2.0f;

    /** Standoff (cells) the hover point is pulled back from the squad centroid along the LZ→centroid bearing. Keeps the shuttle behind the squad as they advance — sprites stay unobstructed and the overwatch read is "covering the rear" rather than "parked on top." When the centroid is within this distance of the LZ, the shuttle just holds over the LZ. */
    public static final float HOVER_STANDOFF_CELLS = 5f;

    /** Peak screen-Y offset (cells) applied at altitudeT = 1 to sell "the shuttle is up in the air" in the top-down view. Sim-space {@code body.x}/{@code body.y} are unchanged — this is render-only. */
    public static final float VISUAL_ALT_PEAK_CELLS = 3.0f;

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
        this.hp = type.maxHp;
        body.teleport(entryX, entryY, AirBody.facingToward(lzX - entryX, lzY - entryY));
    }

    public boolean isVisible() {
        return state == State.INCOMING || state == State.LANDED
                || state == State.HOVER_STATION || state == State.DEPARTING;
    }

    /**
     * True when this shuttle is armed and assigned a fire-support role —
     * after LANDED → marinesRemaining==0, this gates the HOVER_STATION
     * transition vs. the immediate DEPARTING path.
     */
    public boolean shouldHoverLoiter() {
        return assignedRole != null && turrets.length > 0;
    }

    /**
     * Lazily-resolved turret <b>position</b> spread factor: the hull's derived
     * render length ({@link HullFootprintResolver}) over
     * {@link AirScale#TURRET_AUTHORING_HULL_CELLS}, the reference hull the mount
     * offsets were authored against. Scaling a mount's offset by this places it
     * correctly on a hull of any size — and, crucially, <b>spreads the sim mount
     * origins across a large hull</b> so each turret's LoS is computed from its
     * own position rather than the shared body cell. Drives turret placement
     * only; turret <em>size</em> is fixed per kind ({@code TurretKind.visualCells},
     * like a ground {@code MapTurret}). {@code -1} = not yet resolved.
     */
    private float cachedTurretSpread = -1f;

    /** @see #cachedTurretSpread */
    public float turretSpread() {
        if (cachedTurretSpread < 0f) {
            float hullLenCells = HullFootprintResolver.visualLengthCells(type.renderHullId());
            cachedTurretSpread = hullLenCells / AirScale.TURRET_AUTHORING_HULL_CELLS;
        }
        return cachedTurretSpread;
    }

    /**
     * World-frame X of a mount's pivot: its hull-local offset scaled by
     * {@link #turretSpread()} and rotated by the body facing, added to
     * {@code body.x}. {@code extraScale} is an extra multiplier the
     * <em>renderer</em> applies for the altitude visual zoom ({@link #scaleMult});
     * the sim passes {@code 1} — a turret's position is a ground-projected,
     * sim-real quantity. Shared by {@link AirSystem} (sim) and the shuttle render
     * pass so the two can't drift ({@code rounds-fire-from-where-it's-drawn}).
     *
     * @param facingCos {@code cos(toRadians(body.facingDegrees))}, hoisted by the caller
     * @param facingSin {@code sin(toRadians(body.facingDegrees))}, hoisted by the caller
     */
    public float turretWorldX(TurretMount m, float facingCos, float facingSin, float extraScale) {
        float spread = turretSpread() * extraScale;
        float lx = m.localOffsetX * spread;
        float ly = m.localOffsetY * spread;
        return body.x + lx * facingCos - ly * facingSin;
    }

    /** World-frame Y counterpart of {@link #turretWorldX}; the renderer adds the altitude Y-offset on top. */
    public float turretWorldY(TurretMount m, float facingCos, float facingSin, float extraScale) {
        float spread = turretSpread() * extraScale;
        float lx = m.localOffsetX * spread;
        float ly = m.localOffsetY * spread;
        return body.y + lx * facingSin + ly * facingCos;
    }

    /** True when every mounted turret has fired its magazine dry. Drives one of the HOVER_STATION exit triggers. */
    public boolean allTurretsDry() {
        if (turrets.length == 0) return true;
        for (MountedTurret t : turrets) {
            if (!t.ammoDry()) return false;
        }
        return true;
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

    /**
     * Engine intensity for the engine-FX render pass, in {@code [0, 1]}.
     * Layered on top of {@link #engineIntensity()}: the audio loop wants
     * full loudness during HOVER_STATION (hovering vehicles ARE noisy —
     * they're holding lift), but the visible plume should drop because
     * the engine is vectoring for stationary lift rather than forward
     * thrust. We modulate by translational speed: stationary shuttles
     * read at {@link #HOVER_FX_FLOOR}, full forward velocity reads at the
     * full {@link #engineIntensity()}.
     */
    public float engineFxIntensity() {
        float throttle = engineIntensity();
        if (throttle <= 0f) return 0f;
        float speed = (float) Math.sqrt(body.vx * body.vx + body.vy * body.vy);
        float speedT = Math.min(1f, speed / Math.max(0.001f, type.maxSpeed));
        // Floor at HOVER_FX_FLOOR when speedT = 0; linearly climb to 1.0
        // as the shuttle approaches its type's max speed.
        float fxFactor = HOVER_FX_FLOOR + (1f - HOVER_FX_FLOOR) * speedT;
        return throttle * fxFactor;
    }

    /** Engine intensity while parked on the ground — quiet hum, not silent. */
    private static final float IDLE_INTENSITY = 0.3f;
    /** FX-only floor for stationary (HOVER_STATION) shuttles. Audio is unaffected — only the plume scales down. */
    private static final float HOVER_FX_FLOOR = 0.45f;

    /**
     * Render-only Y offset (cells) the renderer should add to {@code body.y}
     * to sell altitude in the top-down view. Sim-space position is the
     * ground projection; the sprite floats above it scaled by altitudeT.
     * Mounted turrets read the same offset so they ride with the shuttle.
     */
    public float visualAltitudeOffsetCells() {
        return altitudeT * VISUAL_ALT_PEAK_CELLS;
    }
}
