package com.dillon.starsectormarines.battle;

/**
 * Static drone-launch structure stamped at the center of a
 * {@link com.dillon.starsectormarines.battle.DefensePostKind#DRONE_HUB}
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
 * <p>The {@link UnitRole#STRUCTURE} role keeps the sim's per-tick behavior
 * dispatch a no-op for hubs. A separate {@code DroneSpawner} tick (added in
 * a follow-up commit) will read the hub's position to spawn patrolling
 * drones around it.
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

    public DroneHubUnit(String id, Faction faction, int cellX, int cellY) {
        super(id, faction, UnitType.DRONE_HUB_STRUCTURE, cellX, cellY);
        this.maxHp = HUB_MAX_HP;
        this.hp = HUB_MAX_HP;
        this.attackDamage = 0f;
        this.attackRange = 0f;
        this.attackCooldown = 1f;
        this.accuracy = 0f;
        this.moveSpeed = 0f;
        this.role = UnitRole.STRUCTURE;
    }
}
