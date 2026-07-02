package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;

/**
 * Config + factory for the autonomous defensive drone launched from a
 * {@link DroneHub}. Combatant with HP, faction = DEFENDER, targetable by
 * marines like any other unit. Its continuous-flight {@link AirBody} is a
 * world {@code KINEMATICS} component: {@link #create} builds + positions it
 * and hands it to {@link Entity#seedBody}, then {@code UnitRosterService.allocate}
 * adopts it into the world column — read by id via {@code world.kinematics(id)}
 * and steered each tick by {@link DroneSwarmAction} (which then syncs the grid
 * cell + render position from it).
 *
 * <p>Per-instance vanilla sprite path (the {@link UnitType#DRONE} sheet field
 * is empty) so the renderer hooks the dedicated drone pass instead of the
 * generic unit-sheet path — same convention used by {@link DroneHub} and
 * {@link com.dillon.starsectormarines.battle.turret.MapTurret}.
 *
 * <p>The {@link UnitRole#DRONE_PATROL} role dispatches to
 * {@code GoapDroneBehavior}/{@link DroneSwarmAction} for the per-tick
 * patrol/pursue/engage posture.
 *
 * <p>A plain config/factory class, <b>not</b> an {@link Entity} subclass — the
 * drone's live per-instance state ({@code patrolGoalX/Y}/{@code pursuitGoalX/Y}/
 * {@code pursuitTimer}/{@code homeHubId}) lives in the world {@code DRONE_STATE}
 * component (data owner {@code battle.sim.DroneStateService}); {@link #create}
 * seeds {@code homeHubId} via {@link Entity#seedHomeHubId}. See
 * {@code roadmap/ecs-migration/stories/identity-collapse.md} (slice B3).
 */
public final class Drone {

    /**
     * Vanilla Terminator-drone sprite — sleek combat drone launched from
     * high-tech ships as autonomous escorts. Matches the defender hub's role
     * (small autonomous combatants that patrol and engage intruders).
     */
    public static final String SPRITE_PATH = "graphics/ships/drones/drone_terminator.png";

    /** Sprite render size in cells (long axis). Sized so the drone reads as smaller than infantry but not lost against the embankment tiles. */
    public static final float VISUAL_CELLS = 0.9f;

    /** Drone HP. Low enough that two-three rifle bursts drop one — drones are a screen, not a tank. */
    public static final float DRONE_MAX_HP = 18f;

    /** Turret-style facing slew rate for the drone's body, deg/sec. Sized for orbital engagement: the drone faces its motion direction (boat physics — can't fly sideways), and the fire-arc gate in {@code TurretAim} passes whenever the nose sweeps within {@code FIRE_ARC_DEG} of target bearing. At 220°/sec the orbit motion produces a clean rhythm of nose-on firing windows rather than the drone lagging the bearing change. */
    public static final float TURN_RATE_DEG_PER_SEC = 220f;

    /**
     * Drone {@code VISION_AIR_LOS_RADIUS}, in cells. Walls within this many cells
     * of the drone are transparent for LoS — both for the drone firing down
     * and for ground combatants firing up. Smaller than the shuttle's 3.5
     * because drones are physically smaller and patrol closer to roof level;
     * 3.0 covers the wall thickness of every building stamper without giving
     * the drone "see through the next building's wall too" reach.
     */
    public static final float DRONE_AIR_LOS_RADIUS = 3.0f;

    /**
     * Radius (cells) around the hub's anchor within which the drone picks
     * patrol waypoints. Sized so the drone covers roughly the city block the
     * hub sits in — wide enough to put the orbit into rifle-fire-receiving
     * distance of the surrounding street grid (so the drone actually meets
     * marines pushing the hub), tight enough that it doesn't wander into the
     * next defense post's territory.
     */
    public static final float PATROL_RADIUS_CELLS = 14f;

    /**
     * Detection radius (cells) for the wider-than-weapon-range agro scan. The
     * drone's primary acquisition runs through {@code TurretAim} at its
     * weapon range (26 cells); this second scan runs separately at a larger
     * radius so the drone notices marines who are <em>shooting it</em> from
     * just outside its weapon range — rifle range is 24, DMR is 32 — and
     * cruises into firing range instead of patrolling obliviously while
     * absorbing fire. Sized at 32 so even DMR attackers trip the agro band.
     */
    public static final float AGGRO_RANGE_CELLS = 32f;

    /**
     * Maximum distance (cells) from the drone's home hub the drone is willing
     * to stray when pursuing a target. The "follow allowance" cap — drones
     * aren't fighter aircraft, they're a screen anchored to their launching
     * hub. A marine fleeing beyond this radius outruns the drone's leash and
     * the drone hovers at the boundary until the engagement ends (then
     * returns to patrol via the standard waypoint roll). Sized to fit a full
     * orbit radius (base + pulse amplitude ~ 19 cells at the current
     * attackRange) around a target at the boundary — drones encircling a
     * marine at the leash edge still trace a full orbit instead of bunching
     * on the hub side.
     */
    public static final float ENGAGE_LEASH_RADIUS_CELLS = 32f;

    /**
     * Sim-seconds the drone keeps committing toward a last-known enemy
     * position after the active target evaporates (target died, ducked into
     * cover that breaks LoS, or moved out of detection range). Short window
     * so the drone returns to patrol promptly if the engagement is genuinely
     * over, but long enough to cover a marine ducking behind a wall corner
     * for two seconds and popping back out.
     */
    public static final float PURSUIT_LATCH_SECONDS = 3f;

    /**
     * Drone flight handling — tuned for a combat-helicopter feel: noticeably
     * faster than walking infantry (2 cells/sec) so the drone reads as a
     * zipping autonomous vehicle, with brisk accel/brake so orbit motion
     * stays crisp. The high turn rate is what lets the orbital fire-arc gate
     * pass cleanly during the nose-sweep — see {@link #TURN_RATE_DEG_PER_SEC}.
     * High lateral damping keeps the cruise tight against the orbit goal
     * (which is itself moving) instead of letting the drone drift outward
     * from sideways momentum.
     */
    public static final AirHandling HANDLING = new AirHandling() {
        @Override public float maxSpeed()                 { return 3.5f; }
        @Override public float accel()                    { return 6f; }
        @Override public float brakingAccel()             { return 9f; }
        @Override public float maxTurnRateDegPerSec()     { return TURN_RATE_DEG_PER_SEC; }
        @Override public float lateralDriftDamping()      { return 5f; }
        @Override public float stationDamping()           { return 8f; }
    };

    /** Re-roll the patrol waypoint when the drone gets within this many cells. */
    public static final float PATROL_WAYPOINT_ARRIVE_DIST = 1.2f;

    /**
     * Orbit base radius as a fraction of {@code attackRange}. The drone's
     * engagement target sits at the orbit center; the drone circles at this
     * radius, modulated by the pulse term below. 0.55 places the orbit
     * comfortably inside firing range (radius ~14 cells at attackRange 26)
     * so the drone can fire throughout the orbit.
     */
    public static final float ENGAGE_ORBIT_BASE_FRACTION = 0.55f;

    /**
     * Orbit radius pulse amplitude as a fraction of {@code attackRange}.
     * Each drone's orbit radius oscillates sinusoidally between
     * {@code base ± amplitude}, giving the "varying radius" lap-the-target
     * read familiar from vanilla Starsector Terminator drones. 0.18 keeps
     * the entire orbit (base ± amplitude ~ 9-19 cells at attackRange 26)
     * inside firing range.
     */
    public static final float ENGAGE_ORBIT_PULSE_FRACTION = 0.18f;

    /**
     * Orbit angular velocity, deg/sec CCW. Sized so the tangential speed
     * required to keep up with the orbit point at base radius matches the
     * drone's {@link AirHandling#maxSpeed()}: at base radius ~14 cells,
     * tangential speed = radius × ω ≈ 4.4 cells/sec, a slight overshoot of
     * the drone's 3.5 cells/sec — drones trail the ideal orbit point by a
     * fraction of a second, producing a swooping motion rather than rigid
     * tracking. A full orbit takes ~20 sim-seconds.
     */
    public static final float ENGAGE_ORBIT_ANGULAR_DEG_PER_SEC = 18f;

    /**
     * Orbit radius pulse frequency, Hz. Each pulse cycle takes 1/freq seconds
     * (~2.9s at 0.35 Hz). Per-slot phase offsets make the drones pulse out
     * of sync — one drone diving in while another swings out — so the swarm
     * reads as three independent strafing helicopters rather than a single
     * pulsating ring.
     */
    public static final float ENGAGE_ORBIT_PULSE_HZ = 0.35f;

    /**
     * Sim-seconds the crash animation runs from kill to ground impact. Short
     * enough that the player feels the kill snap to a wreck; long enough that
     * the spin + fade reads as a deliberate "out-of-control fall" rather than
     * a glitch.
     */
    public static final float CRASH_DURATION_SEC = 0.7f;

    /**
     * Spin rate applied to the drone's body facing during the crash, deg/sec.
     * Fast enough to read as a tumble — about two full revolutions over the
     * crash window. Combined with the alpha fade, the drone "spirals down"
     * before exploding into the SmokingWreck FX at impact.
     */
    public static final float CRASH_SPIN_DEG_PER_SEC = 720f;

    private Drone() {}

    /**
     * Builds a fresh drone {@link Entity} at {@code (cellX, cellY)}, homed to
     * the hub with entity id {@code homeHubId} ({@code 0L} = none — test
     * fixtures that never register a hub). Seeds the config stats above plus
     * {@link Entity#seedHomeHubId} (consumed by {@code UnitRosterService.allocate}
     * into the {@code DRONE_STATE} component iff {@code type.isDrone()}); the
     * caller still owns handing the result to {@code sim.addUnit}/{@code queueSpawn}.
     *
     * <p><b>Captured eagerly</b> — pass {@code hub.entityId} only <em>after</em>
     * the hub is registered ({@code addUnit}/{@code allocate}). An unregistered
     * hub still has {@code entityId == 0}, and {@code 0L} is the legit "no hub"
     * sentinel (not fail-loud), so passing it early silently makes the drone
     * hub-less — it patrols around its own body and is excluded from the hub's
     * death cascade.
     */
    public static EntitySpec create(String id, Faction faction, int cellX, int cellY, long homeHubId) {
        // Build + position the kinematic body and hand it to the world-adoption seam.
        // allocate() reads spec.body → adds the KINEMATICS component, keyed by entity
        // id; the body lives in that column thereafter (this same instance, aliased).
        AirBody body = new AirBody();
        body.teleport(cellX + 0.5f, cellY + 0.5f, 0f);
        return new EntitySpec(id, faction, UnitType.DRONE, cellX, cellY)
                .health(DRONE_MAX_HP)
                .primaryWeapon(MarineWeapon.DRONE_PULSE)   // sets range / damage / accuracy / cooldown
                .visionRange(44f)
                .moveSpeed(0f)
                .airLosRadius(DRONE_AIR_LOS_RADIUS)
                .role(UnitRole.DRONE_PATROL)
                .homeHubId(homeHubId)
                .body(body);
    }
}
