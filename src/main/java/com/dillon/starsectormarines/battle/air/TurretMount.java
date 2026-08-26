package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * Static config for one hardpoint on a {@link ShuttleType} — what kind of
 * turret it carries and where on the shuttle's sprite it sits (in the
 * shuttle's local frame, +Y forward / +X right, cells). The kit selected for
 * a shuttle is an array of these; the runtime {@link MountedTurret} hangs off
 * each at battle time.
 *
 * <p>Pure data: no behavior, immutable after construction.
 */
public final class TurretMount {

    public final String structureId;
    /** Lateral offset from shuttle center, cells. Positive = right side of the hull. */
    public final float localOffsetX;
    /** Longitudinal offset from shuttle center, cells. Positive = toward the nose. */
    public final float localOffsetY;

    public TurretMount(String structureId, float localOffsetX, float localOffsetY) {
        this.structureId = TurretCatalogRegistry.requireStructure(structureId).id;
        this.localOffsetX = localOffsetX;
        this.localOffsetY = localOffsetY;
    }

    public StructureDef structure() { return TurretCatalogRegistry.requireStructure(structureId); }

    public TurretMountDef mountDef() { return structure().mount; }

    public WeaponDef weaponDef() { return mountDef().weapon; }
}
