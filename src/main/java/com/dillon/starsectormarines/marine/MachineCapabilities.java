package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.vehicle.VehicleGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * What a machine's chassis, as fitted, offers the parts that go in it (mechs-nouns.md, one machine, one part catalog).
 * A mech and a vehicle are the same kind of machine with one part catalog: whether a part goes on one is its own
 * requirements ({@link MechInternal#unmet}) against these capabilities, never whether the machine is a mech or a
 * vehicle.
 *
 * @param armed       it carries a gun, so it has a flux reservoir for a capacitor bank and vent arrays to make
 * @param fedWeapons  a gun it carries draws on ammunition a ready rack deepens: a magazine, a rack's packs or a rotary
 *                    gun's belt (a beam has none)
 */
public record MachineCapabilities(boolean armed, boolean fedWeapons) {

    /** A mech as fitted: every chassis has weapon mounts, and a fitted weapon may be fed. */
    public static MachineCapabilities of(CampaignMech mech) {
        boolean fed = false;
        for (MechMountSlot slot : MechMountSlot.values()) fed |= fed(mech.weaponAt(slot));
        return new MachineCapabilities(true, fed);
    }

    /** A vehicle as fitted: its gun card's weapon, or the hull's own gun. */
    public static MachineCapabilities of(CampaignVehicle vehicle) {
        return of(vehicle.type(), vehicle.gun());
    }

    /** A hull of {@code type} carrying {@code gun} in its gun card, or its own gun for none. */
    public static MachineCapabilities of(VehicleType type, MechWeaponComponent gun) {
        if (type == null || !type.hasTurretWeapon()) return new MachineCapabilities(false, false);
        MechWeaponComponent weapon = gun != null ? gun : type.ownGun();
        StructureDef fitted = VehicleGun.fit(type, weapon);
        boolean fed = fed(weapon) || fitted != null && fitted.mount.ammoCapacity > 0;
        return new MachineCapabilities(true, fed);
    }

    /** Whether {@code weapon} draws on ammunition: a finite magazine or rack, or a rotary gun's belt. */
    private static boolean fed(MechWeaponComponent weapon) {
        if (weapon == null) return false;
        if (weapon.rotary() != null) return !weapon.rotary().beam();
        return weapon.ammoCapacity > 0;
    }
}
