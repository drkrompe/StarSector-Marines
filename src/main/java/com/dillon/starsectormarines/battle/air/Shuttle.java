package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One troop-drop shuttle — an air entity: an {@code entityId}, kinematics
 * ({@link AirBody}), and a composed {@link ShuttleMission} (the delivery state
 * machine + per-sortie lifecycle). Render state ({@code altitudeT}/
 * {@code flightPhase}), engine plumes, and turrets are all components keyed by
 * {@link #entityId} in the world's columns ({@code APPEARANCE} / {@code THRUSTER_FX}
 * / {@code AIR_TURRETS}), not fields here. Separate from a grid {@code Entity}
 * because shuttles fly in fractional world space, rotate freely, and don't
 * pathfind or fight on the grid.
 *
 * <p>The shared core (id + body) is mission-agnostic so a fighter (planned) can
 * compose the same core with a different mission component; the shuttle-specific
 * lifecycle lives entirely in {@link #mission}.
 *
 * <p>Coord convention is cell-units with Y up (same frame as ground units).
 * Entry/exit points sit outside the grid so the shuttle reads as off-screen
 * before INCOMING and after DEPARTING.
 */
public class Shuttle {

    public enum State { PENDING, INCOMING, LANDED, HOVER_STATION, DEPARTING, GONE }

    /**
     * World entity id — a monotonic {@code long}, assigned by {@link AirSystem#add}
     * when this shuttle is registered (0 means "not yet registered"). Minted from
     * the single {@code UnitRosterService} id authority (shared with ground units,
     * so a shuttle id can never collide with a ground id) and adopted into the one
     * entity world as the air archetype {@code {AIR_IDENTITY, KINEMATICS,
     * SHUTTLE_MISSION}} — world-resident but never in the dense ground roster.
     * Keys this craft's components (incl. the optional
     * {@link com.dillon.starsectormarines.battle.air.engine.ThrusterFx} /
     * {@link AirTurrets}); never recycled, so no generation bits. This handle's
     * {@code body}/{@code mission} refs alias the world's KINEMATICS/SHUTTLE_MISSION
     * columns during the air-into-world migration (the handle dissolves in its
     * final phase).
     */
    public long entityId;

    public final ShuttleType type;
    public final Faction faction;

    /** Kinematic state for the current sortie — position, velocity, facing. Driven each tick by the sim under the {@link ShuttleType}'s handling profile. */
    public final AirBody body = new AirBody();

    /** Delivery mission — the state machine and per-sortie lifecycle. Every shuttle has one; the shared air-entity core above stays mission-agnostic. */
    public final ShuttleMission mission;

    /** HP fraction below which the shuttle aborts HOVER_STATION and departs. Default 0.4 = 40%. */
    public static final float HOVER_HP_THRESHOLD = 0.4f;

    /** Sim-seconds the LANDED → HOVER_STATION takeoff takes. */
    public static final float T_TAKEOFF_SEC = 2.0f;

    /** Standoff (cells) the hover point is pulled back from the squad centroid along the LZ→centroid bearing. */
    public static final float HOVER_STANDOFF_CELLS = 5f;

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
}
