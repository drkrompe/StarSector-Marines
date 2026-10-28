package com.dillon.starsectormarines.battle.air;


/**
 * Runtime state for one hardpoint on an air craft. Pairs with the
 * static {@link TurretMount} config — the mount describes "where + what",
 * this carries "what it's currently doing": facing, cooldown, ammo, locked
 * target.
 *
 * <p>Pure data, ticked by {@link AirSystem} using the shared
 * {@link com.dillon.starsectormarines.battle.turret.TurretAim} loop. Not a
 * {@code Entity} — shuttle turrets aren't on the unit list, don't pathfind,
 * and can't be targeted independently of their parent shuttle.
 */
public final class MountedTurret {

    /** Slot config — kind and local-frame offset. Immutable; the runtime never reassigns. */
    public final TurretMount mount;

    /** Barrel facing in world frame (Starsector sprite-angle convention; 0° = +Y, positive CCW). Updated each tick by the aim loop. */
    public float facingDegrees;
    /** Sim-seconds until this mount can fire again. Decrements every tick. */
    public float cooldownTimer;
    /** Rounds remaining in this mount's magazine. Initialized from the authored mount capacity. */
    public int ammo;
    /**
     * Currently locked enemy as a {@code entityId} into the registry, or
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
     * Target locked when the burst started, as a {@code entityId} —
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

    /**
     * The second gun sharing this one's turret as a pod, or null. A company boat's hardpoint takes
     * two 2x1 guns (owner pick, 2026-10-27): both sit on the one slot with its one arc, the pod
     * turns as one on whichever of them has a target, lead first, and each fires on its own
     * cadence. Set only by {@link AirArmament}.
     */
    public MountedTurret podPartner;
    /** The pod's lead gun when this is its second gun, or null. */
    public MountedTurret podLead;

    /**
     * How far each of a pod's barrels sits off its slot, in cells across the pod's facing: the
     * lead to the left, the second gun to the right.
     */
    public static final float POD_BARREL_OFFSET_CELLS = 0.24f;

    public MountedTurret(TurretMount mount) {
        this.mount = mount;
        this.ammo = mount.mountDef().ammoCapacity;
    }

    /** Which side of its pod this gun's barrel is on: -1 left for the lead, +1 right for the second, 0 alone. */
    public int podSide() {
        return podPartner != null ? -1 : podLead != null ? 1 : 0;
    }

    /** The facing the pod's housing turns to: its lead's, or this gun's own when it has no pod. */
    public float podFacingDegrees() {
        return podLead != null ? podLead.facingDegrees : facingDegrees;
    }

    /**
     * The world-frame offset of this gun's barrel from its slot, {x, y} in cells, scaled by
     * {@code extraScale}: across the pod's facing by {@link #POD_BARREL_OFFSET_CELLS} on its
     * {@linkplain #podSide side}, nothing for a gun alone. Shared by the sim's shot origin and
     * the renderer.
     */
    public float[] barrelOffset(float extraScale) {
        int side = podSide();
        if (side == 0) return new float[]{0f, 0f};
        double facing = Math.toRadians(podFacingDegrees());
        // Facing 0 is +Y, counter-clockwise positive, so the pod's right is (cos, sin).
        float reach = side * POD_BARREL_OFFSET_CELLS * extraScale;
        return new float[]{reach * (float) Math.cos(facing), reach * (float) Math.sin(facing)};
    }

    /** Write the locked target id ({@code 0L} = none) into {@link #targetId}. */
    public void setTarget(long t) {
        this.targetId = t;
    }

    /** Write the burst-locked target id ({@code 0L} = none) into {@link #burstTargetId}. */
    public void setBurstTarget(long t) {
        this.burstTargetId = t;
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
