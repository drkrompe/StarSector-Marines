package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.turret.TurretKind;

/**
 * Runtime state for one hardpoint on an air craft. Pairs with the
 * static {@link TurretMount} config — the mount describes "where + what",
 * this carries "what it's currently doing": facing, cooldown, ammo, locked
 * target.
 *
 * <p>Pure data, ticked by {@link AirSystem} using the shared
 * {@link com.dillon.starsectormarines.battle.turret.TurretAim} loop. Not a
 * {@link Entity} — shuttle turrets aren't on the unit list, don't pathfind,
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
     * Currently locked enemy as a {@link Entity#entityId} into the registry, or
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
     * Target locked when the burst started, as a {@link Entity#entityId} —
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

    /** Null-safe write into {@link #targetId} — same {@code null}→{@code 0L} convention as {@link Entity#idOf}. */
    public void setTarget(Entity t) {
        this.targetId = (t == null) ? 0L : t.entityId;
    }

    /** Null-safe write into {@link #burstTargetId} — same {@code null}→{@code 0L} convention as {@link Entity#idOf}. */
    public void setBurstTarget(Entity t) {
        this.burstTargetId = (t == null) ? 0L : t.entityId;
    }

    /** True once every mount on the shuttle has fired its magazine dry. */
    public boolean ammoDry() {
        return ammo <= 0;
    }

    /**
     * World-frame X of this mount's pivot: its hull-local slot offset
     * ({@link TurretMount#localOffsetX}/{@code Y}, scraped from the hull's
     * {@code weaponSlots}) scaled by {@code extraScale}, rotated by the body facing,
     * and added to {@code body.x}. {@code extraScale} is the renderer's altitude
     * zoom ({@link AirAppearance#scaleMult(float, float)}); the sim passes 1. Shared
     * by {@link AirSystem} and the render pass so a round fires from where the
     * turret is drawn.
     *
     * @param facingCos {@code cos(toRadians(body.facingDegrees))}, hoisted by the caller
     * @param facingSin {@code sin(toRadians(body.facingDegrees))}, hoisted by the caller
     */
    public float worldX(AirBody body, float facingCos, float facingSin, float extraScale) {
        float lx = mount.localOffsetX * extraScale;
        float ly = mount.localOffsetY * extraScale;
        return body.x + lx * facingCos - ly * facingSin;
    }

    /** World-frame Y counterpart of {@link #worldX}; the renderer adds the altitude Y-offset on top. */
    public float worldY(AirBody body, float facingCos, float facingSin, float extraScale) {
        float lx = mount.localOffsetX * extraScale;
        float ly = mount.localOffsetY * extraScale;
        return body.y + lx * facingSin + ly * facingCos;
    }
}
