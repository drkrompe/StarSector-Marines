package com.dillon.starsectormarines.battle;

/**
 * A bolted-down static defense — a {@link TurretKind} mounted on a single
 * non-walkable map cell. Subclass of {@link Unit} so it slots into existing
 * code paths for free: target acquisition, line-of-sight, the firing pipeline,
 * the deaths-this-frame list, the renderer's unit pass. The differences are
 * narrow — immobile, custom firing arc, separate sprite path — and isolated
 * behind {@link UnitRole#TURRET} dispatch and an {@code instanceof MapTurret}
 * check in the renderer.
 *
 * <p>Stats come from {@link TurretKind} at construction; the mount cell is
 * flagged non-walkable on the {@link com.dillon.starsectormarines.battle.nav.NavigationGrid}
 * by {@link BattleSetup} before the sim is built (same pattern vehicles use).
 * On death, {@link BattleSimulation} flips the cell to walkable + rubble so
 * a destroyed turret stops blocking pathing and LOS.
 */
public class MapTurret extends Unit {

    public final TurretKind kind;
    /** Current barrel facing, degrees. 0° = +Y (north); rotates toward the active target at {@link TurretKind#turnRateDegPerSec}. */
    public float facingDegrees;
    /** True once the sim has converted the cell to walkable rubble. Guards against the renderer keeping a destroyed turret on screen and against double-demolition. */
    public boolean demolished;

    /**
     * Rounds left to fire in the current burst (excluding the first round,
     * fired as the trigger pull). {@code 0} = idle / single-shot kind. Burst
     * kinds latch this from {@link TurretKind#burstCount} when the aim loop
     * triggers; {@link com.dillon.starsectormarines.battle.ai.TurretBehavior}
     * pumps the remaining rounds at {@link TurretKind#burstSpacing}.
     */
    public int burstRemaining;
    /** Sim-seconds until the next burst round fires. Counts down while {@link #burstRemaining} &gt; 0. */
    public float burstTimer;
    /** Target locked when the burst started — held across the salvo so the rounds chase the same victim even if a closer one walks into LOS mid-burst. */
    public Unit burstTarget;

    public MapTurret(String id, Faction faction, TurretKind kind, int cellX, int cellY) {
        super(id, faction, UnitType.TURRET, cellX, cellY);
        this.kind = kind;
        // TurretKind stats override the UnitType.TURRET zero-base. Doing it here
        // (rather than in UnitType) keeps the per-kind balance in one place.
        this.maxHp = kind.maxHp;
        this.hp = kind.maxHp;
        this.attackDamage = kind.damage;
        this.attackRange = kind.range;
        this.attackCooldown = kind.cooldown;
        this.accuracy = kind.accuracy;
        this.moveSpeed = 0f;
        this.role = UnitRole.TURRET;
    }
}
