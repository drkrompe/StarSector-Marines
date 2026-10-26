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

    /** Centre of the arc this mount fires through, degrees off the nose, counter-clockwise positive. */
    public final float arcCenterDeg;
    /** Width of that arc in degrees; {@link #ALL_ROUND} or more fires all round. */
    public final float arcDeg;

    public static final float ALL_ROUND = 360f;

    public TurretMount(String structureId, float localOffsetX, float localOffsetY) {
        this(structureId, localOffsetX, localOffsetY, 0f, ALL_ROUND);
    }

    public TurretMount(String structureId, float localOffsetX, float localOffsetY,
                       float arcCenterDeg, float arcDeg) {
        this.structureId = TurretCatalogRegistry.requireStructure(structureId).id;
        this.localOffsetX = localOffsetX;
        this.localOffsetY = localOffsetY;
        this.arcCenterDeg = arcCenterDeg;
        this.arcDeg = arcDeg;
    }

    /** Whether this mount is held to an arc rather than firing all round. */
    public boolean arced() { return arcDeg < ALL_ROUND; }

    public StructureDef structure() { return TurretCatalogRegistry.requireStructure(structureId); }

    public TurretMountDef mountDef() { return structure().mount; }

    public WeaponDef weaponDef() { return mountDef().weapon; }
}
