package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.combat.PointFireAim;

/** Mutable firing state for one installed {@link MechWeaponComponent}. */
public final class MechWeaponMount {

    public final MechMountSlot slot;
    public final MechWeaponComponent component;
    public int ammo;
    public float cooldown;
    public int burstRemaining;
    public float burstTimer;
    public long burstTargetId;
    /** A point burst keeps the accepted world point for every remaining round. */
    public PointFireAim burstPointAim;
    /** Accumulated onboard replenishment work toward the next trigger pack. */
    public float replenishmentProgressSeconds;

    public MechWeaponMount(MechMountSlot slot, MechWeaponComponent component) {
        if (slot == null || component == null || !component.accepts(slot)) {
            throw new IllegalArgumentException("Incompatible mech hardpoint component");
        }
        this.slot = slot;
        this.component = component;
        this.ammo = component.ammoCapacity;
    }

    public String weaponId() { return component.weaponId; }

    public WeaponDef weaponDef() { return component.weaponDef(); }

    public boolean hasAmmo() {
        return component.ammoCapacity < 0 || ammo > 0;
    }

    public boolean needsSupply() {
        return component.ammoCapacity >= 0 && ammo < component.ammoCapacity;
    }

    /** True when every authored trigger slot on this mount is filled. */
    public boolean full() {
        return component.ammoCapacity < 0 || ammo >= component.ammoCapacity;
    }

    /**
     * Advances unlimited onboard replenishment at the installed subsystem's
     * cadence. Capacity limits ready ammunition, not lifetime reload count.
     */
    public void advanceReplenishment(float dt,
                                     MissileReplenisherComponent replenisher) {
        if (full()) {
            replenishmentProgressSeconds = 0f;
            return;
        }
        float cadence = replenisher.replenishmentSeconds(weaponId());
        if (!Float.isFinite(cadence)) {
            replenishmentProgressSeconds = 0f;
            return;
        }
        replenishmentProgressSeconds += Math.max(0f, dt);
        while (replenishmentProgressSeconds >= cadence && !full()) {
            ammo++;
            replenishmentProgressSeconds -= cadence;
        }
        if (full()) replenishmentProgressSeconds = 0f;
    }

    public boolean resupplyOne() {
        if (!needsSupply()) return false;
        ammo++;
        return true;
    }

    /** Commits one emitted trigger; ammunition counts trigger packs, not individual rounds. */
    public void commitTrigger(long target, PointFireAim point) {
        consumeTrigger();
        cooldown = weaponDef().cooldown;
        burstRemaining = Math.max(0, component.projectilesPerTrigger - 1);
        burstTimer = burstRemaining > 0 ? weaponDef().burstSpacing : 0f;
        burstTargetId = burstRemaining > 0 && point == null ? target : 0L;
        burstPointAim = burstRemaining > 0 ? point : null;
    }

    /** Cancels queued rounds without refunding ammunition or resetting clocks. */
    public void clearBurst() {
        burstRemaining = 0;
        burstTimer = 0f;
        burstTargetId = 0L;
        burstPointAim = null;
    }

    public void consumeTrigger() {
        if (component.ammoCapacity >= 0) ammo--;
    }
}
