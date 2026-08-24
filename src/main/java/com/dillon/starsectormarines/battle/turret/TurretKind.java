package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.weapon.ContrailProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;

/**
 * Id-only compatibility handle for the shipped turret catalog. Simulation,
 * platform, mount and presentation values are owned by the installed JSON
 * registries; this enum survives temporarily while carrier and save-facing
 * callers migrate to stable ids.
 */
public enum TurretKind {
    VULCAN("structure.turret-vulcan"),
    ARBALEST("structure.turret-arbalest"),
    HEAVY_MORTAR("structure.turret-heavy-mortar"),
    DUAL_FLAK("structure.turret-dual-flak"),
    HEPHAESTUS("structure.turret-hephaestus"),
    GRENADE_LAUNCHER("structure.turret-grenade-launcher"),
    LOCUST("structure.turret-locust"),
    HEAVY_MG("structure.turret-heavy-mg");

    public final String structureId;

    TurretKind(String structureId) {
        this.structureId = structureId;
    }

    /** Compatibility resolution used only where runtime APIs still require this enum. */
    public static TurretKind fromStructureId(String structureId) {
        for (TurretKind kind : values()) {
            if (kind.structureId.equals(structureId)) return kind;
        }
        throw new IllegalStateException("No compatibility turret kind for structure '"
                + structureId + "'");
    }

    public StructureDef structure() { return TurretCatalogRegistry.requireStructure(structureId); }

    public TurretMountDef mount() { return structure().mount; }

    public WeaponDef weapon() { return mount().weapon; }

    public String spritePath() { return mount().spritePath; }

    public String recoilSpritePath() { return mount().recoilSpritePath; }

    public String projectileSpritePath() { return weapon().projectileSpritePath; }

    public String fireSoundId() { return weapon().fireSoundId; }

    public String impactSoundId() { return weapon().impactSoundId; }

    public String displayName() { return weapon().displayName; }

    public float range() { return weapon().range; }

    public float damage() { return weapon().damage; }

    public float accuracy() { return weapon().accuracy; }

    public float cooldown() { return weapon().cooldown; }

    public float maxStructure() { return structure().maxStructure; }

    public float turnRateDegPerSec() { return mount().turnRateDegPerSec; }

    public float visualCells() { return mount().visualCells; }

    public float projectileVisualCells() { return weapon().projectileVisualCells; }

    public int startingAmmo() { return mount().ammoCapacity; }

    public int burstCount() { return weapon().burstCount; }

    public float burstSpacing() { return weapon().burstSpacing; }

    public float aoeRadius() { return weapon().aoeRadius; }

    public int wallDamage() { return weapon().wallDamage; }

    public float wallDamageRadius() { return weapon().wallDamageRadius; }

    public float arcHeight() { return weapon().arcHeight; }

    public float flightSec() { return weapon().flightSec; }

    public float hitSpread() { return weapon().hitSpread; }

    public float minRange() { return weapon().minRange; }

    public boolean smokeTrail() { return weapon().smokeTrail; }

    public boolean indirectFire() { return weapon().indirectFire; }

    public float noLosAccuracyMult() { return weapon().noLosAccuracyMult; }

    /** Speed only for an interceptable projectile entity; direct rounds remain resolver events. */
    public float cellsPerSec() {
        return weapon().interceptableProjectile ? weapon().roundVelocity : 0f;
    }

    /** Modeled ground direct-fire speed; nearer stops arrive sooner. */
    public float directRoundVelocity() {
        float flightSec = flightSec();
        return flightSec > 0f ? range() / flightSec : 60f;
    }

    public float penetration() { return weapon().penetration; }

    public float contactDamage() { return weapon().contactDamage; }

    public float contactPenetration() { return weapon().contactPenetration; }

    public float targetAffinityPenetration() {
        return Math.max(penetration(), contactPenetration());
    }

    public float armorPool() { return structure().armorPool; }

    public float armorRating() { return structure().armorRating; }

    public boolean hasBoostRamp() { return weapon().boostRamp; }

    public boolean hasLaunchBackblast() { return weapon().launchBackblast; }

    public ContrailProfile contrailProfile() { return weapon().contrailProfile; }

    public WeaponFxDef fx() { return weapon().fx; }

    public ImpactProfile impactProfile() { return weapon().impactProfile; }
}
