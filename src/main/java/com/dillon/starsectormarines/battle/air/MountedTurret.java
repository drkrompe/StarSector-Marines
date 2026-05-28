package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.turret.TurretKind;

/**
 * Runtime state for one hardpoint on a {@link Shuttle}. Pairs with the
 * static {@link TurretMount} config — the mount describes "where + what",
 * this carries "what it's currently doing": facing, cooldown, ammo, locked
 * target.
 *
 * <p>Pure data, ticked by {@link AirSystem} using the shared
 * {@link com.dillon.starsectormarines.battle.turret.TurretAim} loop. Not a
 * {@link Unit} — shuttle turrets aren't on the unit list, don't pathfind,
 * and can't be targeted independently of their parent shuttle.
 */
public final class MountedTurret {

    /** Slot config — kind and local-frame offset. Immutable; the runtime never reassigns. */
    public final TurretMount mount;

    /** Barrel facing in world frame (Starsector sprite-angle convention; 0° = +Y, positive CCW). Updated each tick by the aim loop. */
    public float facingDegrees;
    /** Sim-seconds until this mount can fire again. Decrements every tick. */
    public float cooldownTimer;
    /** Rounds remaining in this mount's magazine. Initialized from {@link TurretKind#startingAmmo} at construction; the hover-loiter exits when every mount on the shuttle hits zero. */
    public int ammo;
    /**
     * Currently locked enemy as a {@link Unit#entityId} into the registry, or
     * {@code 0L} when nothing's in range/LOS. Persisted across ticks so the aim
     * loop doesn't re-acquire every frame; resolve via
     * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#resolveUnit}
     * — released entities surface as {@code null} without an explicit liveness
     * check by the holder.
     */
    public long targetId = 0L;

    /**
     * Rounds left to fire in the current burst (excluding the first round,
     * which the aim loop fires as the trigger pull). {@code 0} = idle / single-
     * shot kind. Non-zero entries get pumped each tick by {@link AirSystem}
     * regardless of acquisition — a burst commits to its locked target until
     * exhausted, like LRM/SRM salvos.
     */
    public int burstRemaining;
    /** Sim-seconds until the next burst round fires. Counts down while {@link #burstRemaining} &gt; 0. */
    public float burstTimer;
    /**
     * Target locked when the burst started, as a {@link Unit#entityId} —
     * held across the salvo so the rounds chase the same victim even if a
     * closer one walks into LOS mid-burst. {@code 0L} when no burst is active.
     */
    public long burstTargetId = 0L;
    /**
     * Sim-seconds since the last fired round. Reset to {@code 0} on every shot
     * (trigger pull AND each burst continuation), ticked every sim frame by
     * {@link AirSystem}. Lets the renderer drive the barrel-recoil slide per
     * round during a burst, instead of only the first round of the salvo.
     * Initialized to {@code 1f} — well past the renderer's recoil window —
     * so unfired mounts don't read as mid-recoil at sim start.
     */
    public float recoilTimer = 1f;

    public MountedTurret(TurretMount mount) {
        this.mount = mount;
        this.ammo = mount.kind.startingAmmo;
    }

    /** Null-safe write into {@link #targetId} — same shape as {@link Unit#setTarget}. */
    public void setTarget(Unit t) {
        this.targetId = (t == null) ? 0L : t.entityId;
    }

    /** Null-safe write into {@link #burstTargetId} — same shape as {@link Unit#setBurstTarget}. */
    public void setBurstTarget(Unit t) {
        this.burstTargetId = (t == null) ? 0L : t.entityId;
    }

    /** True once every mount on the shuttle has fired its magazine dry. */
    public boolean ammoDry() {
        return ammo <= 0;
    }
}
