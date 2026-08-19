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

    public MechWeaponMount(MechMountSlot slot, MechWeaponComponent component) {
        if (slot == null || component == null || !component.accepts(slot)) {
            throw new IllegalArgumentException("Incompatible mech hardpoint component");
        }
        this.slot = slot;
        this.component = component;
        this.ammo = component.ammoCapacity;
    }

    public MechWeapon weapon() {
        return component.weapon;
    }

    public boolean hasAmmo() {
        return component.ammoCapacity < 0 || ammo > 0;
    }

    public boolean needsSupply() {
        return component.ammoCapacity >= 0 && ammo < component.ammoCapacity;
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
