package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

/**
 * A weapon mount on a hull beside its main turret ({@link VehicleType#secondaryMounts()}): a coax gun, a launcher
 * on the deck, a defensive mount. It is a turret of its own, with its own catalog structure, aim, magazine and
 * cadence, standing at {@code mountX, mountY} cells from the hull's centre (+X right, +Y forward, as
 * {@link VehicleType#turretMountX}). Under direct control it is on the right button ({@code FireGroups}); a
 * {@code crewed} mount is a defence with its own gunner, which never joins a button and keeps firing on its own.
 *
 * <p>A mount with a {@code part} carries a fitted mech weapon ({@link #withPart}, the coax card's): its structure is
 * its catalog carriage carrying that part ({@code fitted},
 * {@link VehicleGun#fit(VehicleType, VehicleMount, MechWeaponComponent)}). Without one it is the catalog structure
 * itself. A fitted mount rides the main turret ({@link VehicleType#coaxMount}): its {@code mountX, mountY} are then
 * cells from the turret ring in the turret's own frame, not from the hull's centre.
 */
public record VehicleMount(String structureId, float mountX, float mountY, boolean crewed,
                           MechWeaponComponent part, StructureDef fitted) {

    /** A mount whose gun is its catalog structure's own. */
    public VehicleMount(String structureId, float mountX, float mountY, boolean crewed) {
        this(structureId, mountX, mountY, crewed, null, null);
    }

    /** This mount on {@code type} with {@code part} fitted on its carriage; this mount itself for a null part. */
    public VehicleMount withPart(VehicleType type, MechWeaponComponent part) {
        if (part == null) return this;
        return new VehicleMount(structureId, mountX, mountY, crewed, part, VehicleGun.fit(type, this, part));
    }

    /** Whether the gun rides the main turret, turning with it beside its barrel: a coax card's fitted part. */
    public boolean ridesTurret() {
        return part != null;
    }

    /** The structure the mount fights as: the carriage with its fitted part, or the catalog structure. */
    public StructureDef structure() {
        return fitted != null ? fitted : carriage();
    }

    /** The catalog structure the mount stands on, whatever is fitted to it. */
    public StructureDef carriage() {
        return TurretCatalogRegistry.requireStructure(structureId);
    }
}
