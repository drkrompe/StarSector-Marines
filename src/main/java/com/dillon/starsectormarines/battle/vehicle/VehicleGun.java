package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
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

    /**
     * How big a mech weapon is on a hull: the weapon at the scale where a mech's hull width is the hull's own, so a
     * weapon is as big beside a hull as a mech's own is beside a mech, and a wider hull carries a bigger gun.
     */
    private static final float HULL_WIDTHS_PER_MECH_WIDTH = 1.5f;

    /** How far back from the turret ring a gun's drawing starts, as a share of its length: the ring is the pivot. */
    public static final float RING_FROM_REAR = .4f;

    private VehicleGun() { }

    /** The share of the hull's width its turret mount ring spans: the base every fitted weapon stands on. */
    private static final float MOUNT_SHARE_OF_HULL_WIDTH = .7f;

    /** The diameter of the mount ring a fitted weapon stands on, in cells. */
    public static float mountCells(VehicleType type) {
        return type.visualWidthCells * MOUNT_SHARE_OF_HULL_WIDTH;
    }

    /** The cells one hull width of the mech's drawing spans on {@code type}. */
    public static float cellsPerMechWidth(VehicleType type) {
        return type.visualWidthCells * HULL_WIDTHS_PER_MECH_WIDTH;
    }

    /** {@code weapon}'s length on {@code type}, rear pivot to muzzle, in cells; zero for a weapon with no drawing. */
    public static float lengthCells(VehicleType type, MechWeaponComponent weapon) {
        return MechHardpointGeometry.barrelHullWidths(weapon) * cellsPerMechWidth(type);
    }

    /** How far the drawn weapon kicks back on {@code type} as it fires, in cells. */
    public static float recoilCells(VehicleType type, MechWeaponComponent weapon) {
        return MechHardpointGeometry.recoilHullWidths(weapon) * cellsPerMechWidth(type);
    }

    /**
     * {@code type}'s turret carrying {@code weapon}; the hull's own for a null weapon. The
     * magazine is the weapon's pulls (a gun that never runs dry on a mech runs on the hull's own feed) and a round
     * costs the weapon's flux, in the hull's own bank.
     */
    public static StructureDef fit(VehicleType type, MechWeaponComponent weapon) {
        StructureDef stock = type.turretStructure();
        if (stock == null || weapon == null) return stock;
        TurretMountDef carriage = stock.mount;
        WeaponDef def = weapon.weaponDef();
        int magazine = weapon.ammoCapacity < 0 ? carriage.ammoCapacity
                : weapon.ammoCapacity * Math.max(1, def.burstCount);
        TurretMountDef.Flux bank = carriage.flux;
        TurretMountDef.Flux flux = bank == null ? null : new TurretMountDef.Flux(bank.capacity(),
                perRound(weapon, def), bank.dissipationPerSec(), bank.ventPerSec());
        // The weapon brings its own muzzle, at the tip of its drawing out from the ring, and its own effects: a
        // ballistic weapon on a ballistic carriage keeps the carriage's casings and scorch, any other has none.
        float length = lengthCells(type, weapon);
        float muzzle = length > 0f ? (1f - RING_FROM_REAR) * length : carriage.muzzleOffsetCells;
        boolean ballistic = weapon.hardpointType == MechWeaponComponent.HardpointType.BALLISTIC;
        return stock.withMount(carriage.withGun(def, magazine, flux, weapon.antiArmorOnly(), muzzle,
                ballistic && carriage.ejectsCasings, ballistic ? carriage.scorchCells : 0f));
    }

    /** What one round of {@code weapon} adds to the gun's flux: a mech's whole-pack flux over the pack's rounds. */
    private static float perRound(MechWeaponComponent weapon, WeaponDef def) {
        float perRound = weapon.rotary() != null ? weapon.fluxPerRound()
                : weapon.fluxPerTrigger() / Math.max(1, def.burstCount);
        return Math.max(0.1f, perRound);
    }
}
