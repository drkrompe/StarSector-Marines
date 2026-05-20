package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.air.AirBody;

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

    /**
     * Kinematic state. Position is initialized to the spawn cell center;
     * patrol behavior (next commit) drives steering and slews the body around
     * the hub's anchor.
     */
    public final AirBody body = new AirBody();

    /** Hub that launched this drone. Held so the hub's active-drone bookkeeping can drop dead drones; patrol behavior will read it for the patrol-around-this-anchor goal. */
    public final DroneHubUnit homeHub;

    public Drone(String id, Faction faction, int cellX, int cellY, DroneHubUnit homeHub) {
        super(id, faction, UnitType.DRONE, cellX, cellY);
        this.homeHub = homeHub;
        this.maxHp = DRONE_MAX_HP;
        this.hp = DRONE_MAX_HP;
        this.attackDamage = 0f;
        this.attackRange = 0f;
        this.attackCooldown = 1f;
        this.accuracy = 0f;
        this.moveSpeed = 0f;
        this.role = UnitRole.STRUCTURE;
        this.body.teleport(cellX + 0.5f, cellY + 0.5f, 0f);
    }
}
