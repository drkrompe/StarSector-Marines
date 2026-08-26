package com.dillon.starsectormarines.battle.mech;

/** Mutable firing state for one installed {@link MechWeaponComponent}. */
public final class MechWeaponMount {

    public final MechMountSlot slot;
    public final MechWeaponComponent component;
    public int ammo;
    public float cooldown;
    public int burstRemaining;
    public float burstTimer;
    public long burstTargetId;
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

    public MechWeapon weapon() {
        return component.weapon();
    }

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
        float cadence = replenisher.replenishmentSeconds(weapon());
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

    public void consumeTrigger() {
        if (component.ammoCapacity >= 0) ammo--;
    }
}
