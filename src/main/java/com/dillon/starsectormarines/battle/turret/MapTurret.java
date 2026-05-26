package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.sim.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

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
    /**
     * Entity id of the target locked when the burst started — held across the
     * salvo so the rounds chase the same victim even if a closer one walks
     * into LOS mid-burst. {@code 0L} when idle. Shadows the inherited
     * {@link Unit#burstTargetId} on purpose: turret burst-tick lives in
     * {@link com.dillon.starsectormarines.battle.ai.TurretBehavior}, while the
     * inherited field serves marine-style {@link Unit#beginBurst(Unit)}
     * callsites the turret never invokes.
     */
    public long burstTargetId = 0L;
    /**
     * Sim-seconds since the last fired round. Reset to {@code 0} on every shot
     * (trigger pull AND each burst continuation), ticked every sim frame by
     * {@link com.dillon.starsectormarines.battle.ai.TurretBehavior}. Lets the
     * renderer drive the barrel-recoil slide per round during a burst, instead
     * of only the first round of the salvo. Initialized to {@code 1f} — well
     * past the renderer's recoil window — so unfired turrets don't read as
     * mid-recoil at sim start.
     */
    public float recoilTimer = 1f;

    public MapTurret(String id, Faction faction, TurretKind kind, int cellX, int cellY) {
        super(id, faction, UnitType.TURRET, cellX, cellY);
        this.kind = kind;
        // TurretKind stats override the UnitType.TURRET zero-base. Doing it here
        // (rather than in UnitType) keeps the per-kind balance in one place.
        // Pre-allocate seed; setMaxHp/setHp route to the unit's local fields
        // here because registry is still null. UnitRegistry.allocate later
        // copies these into the SoA arrays.
        setMaxHp(kind.maxHp);
        setHp(kind.maxHp);
        this.attackDamage = kind.damage;
        this.attackRange = kind.range;
        this.attackCooldown = kind.cooldown;
        this.accuracy = kind.accuracy;
        this.moveSpeed = 0f;
        this.role = UnitRole.TURRET;
    }
}
