package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;

/**
 * Autonomous defensive drone launched from a {@link DroneHubUnit}. Combatant
 * with HP, faction = DEFENDER, targetable by marines like any other unit. The
 * drone composes an {@link AirBody} for future kinematic flight (patrol +
 * intercept come in follow-up commits); for the spawn-cadence-only slice the
 * body holds the spawn position and the drone idles in place.
 *
 * <p>Per-instance vanilla sprite path (the {@link UnitType#DRONE} sheet field
 * is empty) so the renderer hooks the dedicated drone pass instead of the
 * generic unit-sheet path — same convention used by {@link DroneHubUnit} and
 * {@link com.dillon.starsectormarines.battle.turret.MapTurret}.
 *
 * <p>The {@link UnitRole#STRUCTURE} role is a temporary stand-in: it keeps the
 * sim's per-tick behavior dispatch a no-op until {@code DroneBehavior} arrives
 * with patrol + engagement.
 */
public class Drone extends Unit {

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

    /** Turret-style facing slew rate for the drone's body, deg/sec. Fast enough that the drone snaps onto a new target inside a single cooldown window without reading as instantaneous; matches the upper end of {@link com.dillon.starsectormarines.battle.turret.TurretKind#turnRateDegPerSec}. */
    public static final float TURN_RATE_DEG_PER_SEC = 140f;

    /**
     * Drone {@link Unit#airLosRadius}, in cells. Walls within this many cells
     * of the drone are transparent for LoS — both for the drone firing down
     * and for ground combatants firing up. Smaller than the shuttle's 3.5
     * because drones are physically smaller and patrol closer to roof level;
     * 3.0 covers the wall thickness of every building stamper without giving
     * the drone "see through the next building's wall too" reach.
     */
    public static final float DRONE_AIR_LOS_RADIUS = 3.0f;

    /**
     * Kinematic state. Position is initialized to the spawn cell center;
     * patrol behavior (next commit) drives steering and slews the body around
     * the hub's anchor.
     */
    public final AirBody body = new AirBody();

    /** Hub that launched this drone. Held so the hub's active-drone bookkeeping can drop dead drones; patrol behavior reads it for the patrol-around-this-anchor goal. */
    public final DroneHubUnit homeHub;

    /**
     * Radius (cells) around the hub's anchor within which the drone picks
     * patrol waypoints. Sized so the drone covers roughly the city block the
     * hub sits in — wide enough to spread fire coverage, tight enough that
     * the drone doesn't wander into the next defense post's territory.
     */
    public static final float PATROL_RADIUS_CELLS = 8f;

    /**
     * Current patrol waypoint, world cell coords. Picked when spawned; re-
     * rolled when the drone gets close to it. Sentinel value
     * {@link Float#NaN} means "no waypoint yet" — DroneBehavior picks one on
     * first tick using {@code BattleSimulation.getRng()}.
     */
    public float patrolGoalX = Float.NaN;
    public float patrolGoalY = Float.NaN;

    /**
     * Drone flight handling — nimbler than any shuttle profile because the
     * drone is small and patrolling, not hauling marines. The high turn rate
     * lets it pivot onto a new patrol waypoint in roughly a second; modest
     * accel keeps the motion read as a smooth drift rather than a teleport.
     * Lateral damping is high so the drone tracks its waypoint cleanly when
     * cruising and settles tight against drift while station-keeping during
     * an engagement.
     */
    public static final AirHandling HANDLING = new AirHandling() {
        @Override public float maxSpeed()                 { return 2.5f; }
        @Override public float accel()                    { return 4f; }
        @Override public float brakingAccel()             { return 6f; }
        @Override public float maxTurnRateDegPerSec()     { return TURN_RATE_DEG_PER_SEC; }
        @Override public float lateralDriftDamping()      { return 4f; }
        @Override public float stationDamping()           { return 8f; }
    };

    /** Re-roll the patrol waypoint when the drone gets within this many cells. */
    public static final float PATROL_WAYPOINT_ARRIVE_DIST = 1.2f;

    public Drone(String id, Faction faction, int cellX, int cellY, DroneHubUnit homeHub) {
        super(id, faction, UnitType.DRONE, cellX, cellY);
        this.homeHub = homeHub;
        this.maxHp = DRONE_MAX_HP;
        this.hp = DRONE_MAX_HP;
        this.primaryWeapon = MarineWeapon.DRONE_PULSE;
        this.attackRange = MarineWeapon.DRONE_PULSE.range;
        this.attackDamage = MarineWeapon.DRONE_PULSE.damage;
        this.attackCooldown = MarineWeapon.DRONE_PULSE.cooldown;
        this.accuracy = MarineWeapon.DRONE_PULSE.accuracy;
        this.moveSpeed = 0f;
        this.airLosRadius = DRONE_AIR_LOS_RADIUS;
        this.role = UnitRole.DRONE_PATROL;
        this.body.teleport(cellX + 0.5f, cellY + 0.5f, 0f);
    }
}
