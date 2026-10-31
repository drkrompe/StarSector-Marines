package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
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
 * @param droneHost   it can tether a drone wing and flies no bay of its own (a sensor pair)
 * @param pointDefenceHost it has the flanks for active protection's launchers and sensor panels
 */
public record MachineCapabilities(boolean armed, boolean fedWeapons, boolean droneHost, boolean pointDefenceHost) {

    /** A machine that is only armed or fed: no drone wing or point defence to host. */
    public MachineCapabilities(boolean armed, boolean fedWeapons) {
        this(armed, fedWeapons, false, false);
    }

    /** A mech as fitted: every chassis has weapon mounts, and a fitted weapon may be fed; its chassis says what it hosts. */
    public static MachineCapabilities of(CampaignMech mech) {
        boolean fed = false;
        for (MechMountSlot slot : MechMountSlot.values()) fed |= fed(mech.weaponAt(slot));
        MechVariant variant = mech.variant();
        return new MachineCapabilities(true, fed, variant.hostsDrones(), variant.hostsPointDefence());
    }

    /** A vehicle as fitted: its gun card's weapon, or the hull's own gun. */
    public static MachineCapabilities of(CampaignVehicle vehicle) {
        return of(vehicle.type(), vehicle.gun());
    }

    /** A hull of {@code type} carrying {@code gun} in its gun card, or its own gun for none. */
    public static MachineCapabilities of(VehicleType type, MechWeaponComponent gun) {
        if (type == null) return new MachineCapabilities(false, false);
        // Any hull tethers a wing off its deck unless it flies a bay of its own.
        boolean droneHost = type.droneBay().isEmpty();
        if (!type.hasTurretWeapon()) return new MachineCapabilities(false, false, droneHost, false);
        MechWeaponComponent weapon = gun != null ? gun : type.ownGun();
        StructureDef fitted = VehicleGun.fit(type, weapon);
        boolean fed = fed(weapon) || fitted != null && fitted.mount.ammoCapacity > 0;
        // The gun tanks' rear flanks take the launchers (the Onager and the laser tank, not the dozer).
        boolean pointDefenceHost = type.isTank() && type.weight() == VehicleType.Weight.HEAVY;
        return new MachineCapabilities(true, fed, droneHost, pointDefenceHost);
    }

    /** Whether {@code weapon} draws on ammunition: a finite magazine or rack, or a rotary gun's belt. */
    private static boolean fed(MechWeaponComponent weapon) {
        if (weapon == null) return false;
        if (weapon.rotary() != null) return !weapon.rotary().beam();
        return weapon.ammoCapacity > 0;
    }
}
