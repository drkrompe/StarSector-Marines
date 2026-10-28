package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * A mech weapon in a vehicle's gun card (mechs-nouns.md, a vehicle is a mech for parts): the hull's own turret with the
 * weapon's ballistics in it. The carriage stays the hull's (its traverse, loader, muzzles, flux bank and hit shares) and the
 * gun is the weapon's, so the weapon fires with the range, damage and cadence it has on a mech; only the magazine and
 * the flux a round costs are translated, here and nowhere else.
 */
public final class VehicleGun {

    private VehicleGun() { }

    /**
     * {@code stock} carrying {@code weapon}; {@code stock} itself for a null weapon, the hull's own gun. The
     * magazine is the weapon's pulls (a gun that never runs dry on a mech runs on the hull's own feed) and a round
     * costs the weapon's flux, in the hull's own bank.
     */
    public static StructureDef fit(StructureDef stock, MechWeaponComponent weapon) {
        if (stock == null || weapon == null) return stock;
        TurretMountDef carriage = stock.mount;
        WeaponDef def = weapon.weaponDef();
        int magazine = weapon.ammoCapacity < 0 ? carriage.ammoCapacity
                : weapon.ammoCapacity * Math.max(1, def.burstCount);
        TurretMountDef.Flux bank = carriage.flux;
        TurretMountDef.Flux flux = bank == null ? null : new TurretMountDef.Flux(bank.capacity(),
                perRound(weapon, def), bank.dissipationPerSec(), bank.ventPerSec());
        return stock.withMount(carriage.withGun(def, magazine, flux, weapon.antiArmorOnly()));
    }

    /** What one round of {@code weapon} adds to the gun's flux: a mech's whole-pack flux over the pack's rounds. */
    private static float perRound(MechWeaponComponent weapon, WeaponDef def) {
        float perRound = weapon.rotary() != null ? weapon.fluxPerRound()
                : weapon.fluxPerTrigger() / Math.max(1, def.burstCount);
        return Math.max(0.1f, perRound);
    }
}
