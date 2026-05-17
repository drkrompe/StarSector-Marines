package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.Unit;

/**
 * Runtime state for one hardpoint on a {@link Shuttle}. Pairs with the
 * static {@link TurretMount} config — the mount describes "where + what",
 * this carries "what it's currently doing": facing, cooldown, ammo, locked
 * target.
 *
 * <p>Pure data, ticked by {@link AirSystem} using the shared
 * {@link com.dillon.starsectormarines.battle.ai.TurretAim} loop. Not a
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
    /** Rounds remaining in this mount's magazine. Initialized from {@link com.dillon.starsectormarines.battle.TurretKind#startingAmmo} at construction; the hover-loiter exits when every mount on the shuttle hits zero. */
    public int ammo;
    /** Currently locked enemy, or null when nothing's in range/LOS. Persisted across ticks so the aim loop doesn't re-acquire every frame. */
    public Unit target;

    public MountedTurret(TurretMount mount) {
        this.mount = mount;
        this.ammo = mount.kind.startingAmmo;
    }

    /** True once every mount on the shuttle has fired its magazine dry. */
    public boolean ammoDry() {
        return ammo <= 0;
    }
}
