package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

/**
 * A weapon mount on a hull beside its main turret ({@link VehicleType#secondaryMounts()}): a coax gun, a launcher
 * on the deck, a defensive mount. It is a turret of its own, with its own catalog structure, aim, magazine and
 * cadence, standing at {@code mountX, mountY} cells from the hull's centre (+X right, +Y forward, as
 * {@link VehicleType#turretMountX}). Under direct control it is on the right button ({@code FireGroups}); a
 * {@code crewed} mount is a defence with its own gunner, which never joins a button and keeps firing on its own.
 */
public record VehicleMount(String structureId, float mountX, float mountY, boolean crewed) {
    public StructureDef structure() {
        return TurretCatalogRegistry.requireStructure(structureId);
    }
}
