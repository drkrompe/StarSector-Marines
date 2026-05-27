package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Static drone-launch structure stamped at the center of a
 * {@link com.dillon.starsectormarines.battle.turret.DefensePostKind#DRONE_HUB}
 * defense post. Targetable, has HP, dies when destroyed — but does no firing
 * itself; the drones it spawns are what defends. Per-instance sprite path
 * follows the {@link com.dillon.starsectormarines.battle.turret.MapTurret}
 * convention (the parent {@link UnitType} carries an empty sprite path so
 * the renderer knows to consult the instance instead).
 *
 * <p>Hub HP sits between {@code MEDIUM} turret HP (65) and {@code LARGE}
 * (85) — substantial enough to take a few mag dumps but not a tank; losing
 * the hub silences the drone screen, so the player's expected to push it
 * down if the drones become a problem.
 *
 * <p>{@link UnitRole#DRONE_HUB} dispatches to {@link com.dillon.starsectormarines.battle.ai.DroneHubBehavior},
 * which counts down {@link #spawnCooldown} and asks {@link DroneSpawner} to
 * launch a new drone whenever the hub is below {@link #MAX_ACTIVE_DRONES}.
 */
public class DroneHubUnit extends Unit {

    /**
     * Vanilla weapon sprite reused as the hub structure — the large Pilum
     * launcher reads as a multi-tube drone bay (Pilums are autonomous
     * self-guided munitions, the closest base-game analogue to drones).
     * Swappable later if we add a dedicated hub sprite.
     */
    public static final String SPRITE_PATH = "graphics/weapons/pilum_launcher_large_turret.png";

    /** Sprite render size in cells (long axis). Sized to fill the sealed 1×1 launch pad with a touch of overhang into the surrounding embankment, reading as a substantial emplacement. */
    public static final float VISUAL_CELLS = 1.6f;

    /** Hub HP — between MEDIUM (65) and LARGE (85) turret HP. Substantial enough to outlive a few mag dumps but pushable down with focus fire. */
    public static final float HUB_MAX_HP = 80f;

    /** True once the sim has converted the hub cell to walkable rubble. Guards against the renderer keeping a destroyed hub on screen and against double-demolition. */
    public boolean demolished;

    /** Cap on simultaneously-airborne drones from a single hub. Three is the sweet spot for a screen that justifies the stamper: enough that the swarm can fan out around a target on different bearings (vs. a duo always stacking), small enough that two hubs on the same map don't carpet the area with drones. */
    public static final int MAX_ACTIVE_DRONES = 3;
    /** Sim-seconds the hub waits before its first drone launch. Short delay so a marine pushing the hub still meets a drone screen during the opening engagement. */
    public static final float INITIAL_SPAWN_DELAY_SEC = 4f;
    /** Sim-seconds between successive launches when the hub is below the active-drone cap. Long enough that a steady DPS push thins the screen faster than the hub can replace it; short enough that ignoring the hub means dealing with drones forever. */
    public static final float SPAWN_INTERVAL_SEC = 18f;

    /** Sim-seconds until the next spawn attempt. Ticked down by {@link com.dillon.starsectormarines.battle.ai.DroneHubBehavior}. */
    public float spawnCooldown = INITIAL_SPAWN_DELAY_SEC;
    /** Lifetime count of drones the hub has launched. Used as part of each drone's id so they're greppable in logs across the battle. */
    public int dronesLaunched;

    /**
     * Squad the hub's drones join when they launch. Minted lazily on the first
     * successful launch by {@link com.dillon.starsectormarines.battle.drone.DroneSpawner}
     * — every subsequent drone from this hub joins the same squad so the
     * GOAP planner can coordinate their bearings (encircle-on-engage,
     * sector-on-patrol). Null until the first launch succeeds; never
     * re-minted (leader promotion handles death of the first leader).
     */
    public Squad droneSquad;

    public DroneHubUnit(String id, Faction faction, int cellX, int cellY) {
        super(id, faction, UnitType.DRONE_HUB_STRUCTURE, cellX, cellY);
        setMaxHp(HUB_MAX_HP);
        setHp(HUB_MAX_HP);
        this.localAttackDamage = 0f;
        this.localAttackRange = 0f;
        this.attackCooldown = 1f;
        this.localAccuracy = 0f;
        this.moveSpeed = 0f;
        this.role = UnitRole.DRONE_HUB;
    }
}
