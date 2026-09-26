package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.PointFireAim;

/**
 * Mutable per-vehicle turret state — the ground twin of the air
 * {@link com.dillon.starsectormarines.battle.air.AirTurrets} bag. Held in the
 * {@code GROUND_TURRET} OBJECT component (presence == "armed"): only a vehicle whose
 * {@link VehicleType#hasTurretWeapon()} carries one; an unarmed truck has no
 * {@code GROUND_TURRET}. The immutable weapon config (turn rate, range, cooldown,
 * burst) stays on the structure definition resolved by
 * {@link VehicleType#turretStructureId}; this bag is the live aim/fire state
 * {@link GroundVehicleTurretSystem} drives each tick.
 *
 * <p>Extracted from {@code Vehicle}'s former inline {@code turret*} fields in the
 * convoy-{@code Vehicle}-into-world epic
 * ({@code ecs-nouns.md}). During the aliasing
 * phase the same instance is held by both the {@code Vehicle} handle and the
 * {@code GROUND_TURRET} column.
 */
public final class GroundTurret {

    /** Barrel facing in world frame (0° = +Y, positive CCW). */
    public float facingDeg;
    /** Sim-seconds until the turret can fire again. */
    public float cooldownTimer;
    /** Entity id of the currently locked target, or {@code 0L} when idle. */
    public long targetId;
    /** Rounds remaining in the magazine. Seeded from the authored mount capacity. */
    public int ammo;
    /** Rounds left in the current burst (excluding the trigger-pull round). */
    public int burstRemaining;
    /** Sim-seconds until the next burst round fires. */
    public float burstTimer;
    /** Entity id of the target locked when the current burst started. */
    public long burstTargetId;
    /** World point committed by a manual trigger; null for autonomous bursts. */
    public PointFireAim burstPointAim;

    public GroundTurret(int startingAmmo) {
        this.ammo = startingAmmo;
    }

    /** Releases queued work without refunding ammunition or resetting the fire clock. */
    public void clearQueuedFire() {
        targetId = 0L;
        burstTargetId = 0L;
        burstPointAim = null;
        burstRemaining = 0;
        burstTimer = 0f;
    }
}
