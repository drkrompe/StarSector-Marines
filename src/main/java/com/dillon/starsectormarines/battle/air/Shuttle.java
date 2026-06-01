package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One troop-drop shuttle — an air entity: an {@code entityId}, kinematics
 * ({@link AirBody}), render state, and a composed {@link ShuttleMission} (the
 * delivery state machine + per-sortie lifecycle). Engine plumes and turrets are
 * components keyed by {@link #entityId} in {@link AirSystem}'s stores, not fields
 * here. Separate from a grid {@code Unit} because shuttles fly in fractional
 * world space, rotate freely, and don't pathfind or fight on the grid.
 *
 * <p>The shared core (id + body + render) is mission-agnostic so a fighter
 * (planned) can compose the same core with a different mission component; the
 * shuttle-specific lifecycle lives entirely in {@link #mission}.
 *
 * <p>Coord convention is cell-units with Y up (same frame as ground units).
 * Entry/exit points sit outside the grid so the shuttle reads as off-screen
 * before INCOMING and after DEPARTING.
 */
public class Shuttle {

    public enum State { PENDING, INCOMING, LANDED, HOVER_STATION, DEPARTING, GONE }

    /**
     * Air-entity id — a monotonic {@code long}, assigned by {@link AirSystem#add}
     * when this shuttle is registered (0 means "not yet registered"). Keys this
     * craft's components in the air component stores (today:
     * {@link com.dillon.starsectormarines.battle.air.engine.ThrusterFx},
     * {@link AirTurrets}). Disjoint from unit ids; never recycled, so no
     * generation bits. The first step of the air tier's air-entity-composition
     * migration — air craft as real entities.
     */
    public long entityId;

    public final ShuttleType type;
    public final Faction faction;

    /** Kinematic state for the current sortie — position, velocity, facing. Driven each tick by the sim under the {@link ShuttleType}'s handling profile. */
    public final AirBody body = new AirBody();

    /** Delivery mission — the state machine and per-sortie lifecycle. Every shuttle has one; the shared air-entity core above stays mission-agnostic. */
    public final ShuttleMission mission;

    /**
     * Render scale multiplier. 1.0 on the ground; rises to {@code CRUISE_SCALE}
     * while at altitude so the shuttle reads as "bigger because higher." Driven
     * by the sim from {@link #altitudeT}.
     */
    public float scaleMult = 1f;

    /**
     * Cruise altitude scalar in [0, 1]. 0 = on the LZ, 1 = at cruising height.
     * Drives both {@link #scaleMult} and {@link #engineIntensity()} — one source
     * of truth for "how high am I right now."
     */
    public float altitudeT = 1f;

    /** Accumulated phase (radians) for the in-flight scale wobble. Advances while airborne. */
    public float flightPhase = 0f;

    /** HP fraction below which the shuttle aborts HOVER_STATION and departs. Default 0.4 = 40%. */
    public static final float HOVER_HP_THRESHOLD = 0.4f;

    /** Sim-seconds the LANDED → HOVER_STATION takeoff takes. */
    public static final float T_TAKEOFF_SEC = 2.0f;

    /** Standoff (cells) the hover point is pulled back from the squad centroid along the LZ→centroid bearing. */
    public static final float HOVER_STANDOFF_CELLS = 5f;

    /** Peak screen-Y offset (cells) at altitudeT = 1 to sell altitude in the top-down view. Render-only; sim-space position is unchanged. */
    public static final float VISUAL_ALT_PEAK_CELLS = 3.0f;

    public Shuttle(ShuttleType type, Faction faction,
                   float lzX, float lzY,
                   float entryX, float entryY,
                   float exitX, float exitY,
                   float pendingDelay) {
        this.type = type;
        this.faction = faction;
        this.mission = new ShuttleMission(lzX, lzY, entryX, entryY, exitX, exitY,
                pendingDelay, type.capacity, type.maxHp);
        body.teleport(entryX, entryY, AirBody.facingToward(lzX - entryX, lzY - entryY));
    }

    public boolean isVisible() {
        State s = mission.state;
        return s == State.INCOMING || s == State.LANDED
                || s == State.HOVER_STATION || s == State.DEPARTING;
    }

    /**
     * World-frame X of a mount's pivot: its hull-local slot offset (cells, at the
     * global pixel density, scraped from the hull's {@code weaponSlots} by
     * {@link com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver})
     * rotated by the body facing and added to {@code body.x}. {@code extraScale}
     * is the renderer's altitude zoom ({@link #scaleMult}); the sim passes 1.
     * Shared by {@link AirSystem} and the render pass so a round fires from where
     * the turret is drawn.
     *
     * @param facingCos {@code cos(toRadians(body.facingDegrees))}, hoisted by the caller
     * @param facingSin {@code sin(toRadians(body.facingDegrees))}, hoisted by the caller
     */
    public float turretWorldX(TurretMount m, float facingCos, float facingSin, float extraScale) {
        float lx = m.localOffsetX * extraScale;
        float ly = m.localOffsetY * extraScale;
        return body.x + lx * facingCos - ly * facingSin;
    }

    /** World-frame Y counterpart of {@link #turretWorldX}; the renderer adds the altitude Y-offset on top. */
    public float turretWorldY(TurretMount m, float facingCos, float facingSin, float extraScale) {
        float lx = m.localOffsetX * extraScale;
        float ly = m.localOffsetY * extraScale;
        return body.y + lx * facingSin + ly * facingCos;
    }

    /**
     * Normalized engine loudness/pitch driver for the engine loop, in [0, 1].
     * Full throttle at cruise, idles on the ground, blends via {@link #altitudeT}.
     * PENDING and GONE return 0 so off-screen shuttles don't contribute. The
     * {@code BattleScreen} loop takes the max across visible shuttles.
     */
    public float engineIntensity() {
        if (mission.state == State.PENDING || mission.state == State.GONE) return 0f;
        return IDLE_INTENSITY + (1f - IDLE_INTENSITY) * altitudeT;
    }

    /**
     * Master engine-FX throttle for the render pass, in {@code [0, 1]} — how lit
     * the engines are <em>at all</em> (altitude-driven). <em>Which</em> thrusters
     * glow and by how much is
     * {@link com.dillon.starsectormarines.battle.air.engine.ThrusterDemand}'s job,
     * smoothed per slot by {@code ThrusterFxSystem} and applied in the renderer.
     */
    public float engineFxIntensity() {
        return engineIntensity();
    }

    /** Engine intensity while parked on the ground — quiet hum, not silent. */
    private static final float IDLE_INTENSITY = 0.3f;

    /**
     * Render-only Y offset (cells) added to {@code body.y} to sell altitude in
     * the top-down view. Sim-space position is the ground projection; the sprite
     * floats above it scaled by altitudeT. Turrets read the same offset.
     */
    public float visualAltitudeOffsetCells() {
        return altitudeT * VISUAL_ALT_PEAK_CELLS;
    }
}
