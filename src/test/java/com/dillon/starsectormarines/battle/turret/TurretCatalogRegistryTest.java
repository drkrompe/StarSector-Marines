package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretCatalogRegistryTest {

    private static final float EPS = 0.0001f;

    @Test
    void builtinsExactlyPreserveTheEightShippedTurretDefinitions() {
        TurretCatalogRegistry registry = TurretCatalogRegistry.installed();
        assertEquals(8, registry.mountCount());
        assertEquals(8, registry.structureCount());

        assertKind(TurretKind.VULCAN, "Vulcan Cannon", 22f, 10.8f, 3f, .45f, 1.4f,
                60f, 80f, 8f, 120f, 1.6f, .22f, 120,
                6, .08f, .6f, 3, 0f, 0f, 0f, 1.3f, 0f,
                false, false, 1f, 0f, 0f, 0f, false, false, ImpactProfile.RIFLE,
                "graphics/weapons/vulcan_cannon_turret_base.png",
                "graphics/weapons/vulcan_cannon_turret_recoil.png",
                "graphics/missiles/shell_small_yellow.png", "vulcan_cannon_fire");
        assertKind(TurretKind.ARBALEST, "Arbalest Autocannon", 30f, 45f, 8f, .5f, 1.5f,
                70f, 110f, 10f, 90f, 1.8f, .3f, 60,
                1, 0f, 0f, 0, 0f, 0f, 0f, 0f, 0f,
                false, false, 1f, 0f, 0f, 0f, false, false, ImpactProfile.KINETIC,
                "graphics/weapons/arbalest_turret_base.png",
                "graphics/weapons/arbalest_turret_recoil.png",
                "graphics/missiles/shell_large_green.png", "autocannon_fire");
        assertKind(TurretKind.HEAVY_MORTAR, "Heavy Mortar", 36f, 81f, 10f, .55f, 2.5f,
                80f, 130f, 12f, 60f, 1.8f, .32f, 15,
                1, 0f, 1.35f, 24, 1.15f, 0f, .6f, .18f, 0f,
                false, false, 1f, 0f, 0f, 0f, false, false, ImpactProfile.CANNON_HE,
                "graphics/weapons/heavy_mortar_turret.png",
                "graphics/weapons/heavy_mortar_turret_recoil.png",
                "graphics/missiles/shell_round_lrg.png", "heavy_mortar_fire");
        assertKind(TurretKind.DUAL_FLAK, "Dual Flak Cannon", 26f, 36f, 5f, .4f, .8f,
                75f, 120f, 10f, 100f, 2f, .28f, 80,
                1, 0f, 0f, 0, 0f, 0f, 0f, 0f, 0f,
                false, false, 1f, 0f, 0f, 0f, false, false, ImpactProfile.KINETIC,
                "graphics/weapons/double_flak_cannon_turret_base.png",
                "graphics/weapons/double_flak_cannon_turret_recoil.png",
                "graphics/missiles/shell_large_blue.png", "flak_fire");
        assertKind(TurretKind.HEPHAESTUS, "Hephaestus Heavy Cannon", 32f, 45f, 4f, .65f, 4.5f,
                90f, 145f, 14f, 75f, 2.2f, .35f, 50,
                1, 0f, 1.6f, 30, 1.25f, 0f, .55f, .14f, 0f,
                false, false, 1f, 0f, 117f, 24f, false, false, ImpactProfile.CANNON_HE,
                "graphics/weapons/hephaestus_turret_base.png",
                "graphics/weapons/hephaestus_turret_recoil.png",
                "graphics/missiles/shell_hephag.png", "hephaestus_fire");
        assertKind(TurretKind.GRENADE_LAUNCHER, "Grenade Launcher", 28f, 36f, 6f, .55f, 4f,
                75f, 120f, 10f, 80f, 1.7f, .55f, 60,
                4, .18f, 1.5f, 30, 1.5f, 2.5f, .65f, .6f, 5f,
                true, false, 1f, 45f, 0f, 0f, false, false, ImpactProfile.HE,
                "graphics/weapons/light_mortar_turret_base.png",
                "graphics/weapons/light_mortar_turret_recoil.png",
                "graphics/missiles/mortar_round.png", "light_mortar_fire");
        assertKind(TurretKind.LOCUST, "Locust Rocket Battery", 100f, 45f, 14f, .25f, 10f,
                85f, 135f, 12f, 50f, 2f, .45f, 30,
                8, .08f, 1.4f, 20, 1.4f, 3.5f, 1.5f, 8f, 30f,
                true, true, .55f, 70f, 0f, 0f, true, true, ImpactProfile.HE,
                "graphics/weapons/locust_turret.png", "graphics/weapons/locust_turret.png",
                "graphics/missiles/missile_locust.png", "swarmer_fire");
        assertKind(TurretKind.HEAVY_MG, "Heavy MG", 24f, 22.5f, 4f, .5f, 2.2f,
                70f, 110f, 10f, 110f, 1.6f, .22f, 200,
                10, .07f, .8f, 5, 0f, 0f, .18f, 2f, 3f,
                false, false, 1f, 0f, 0f, 0f, false, false, ImpactProfile.KINETIC,
                "graphics/weapons/vulcan_cannon_turret_base.png",
                "graphics/weapons/vulcan_cannon_turret_recoil.png",
                "graphics/missiles/shell_small_yellow.png", "autocannon_fire");
    }

    @Test
    void contactPayloadAndAreaPayloadRemainDistinct() {
        WeaponDef hephaestus = TurretKind.HEPHAESTUS.weapon();
        assertEquals(45f, hephaestus.damage, EPS);
        assertEquals(4f, hephaestus.penetration, EPS);
        assertEquals(1.6f, hephaestus.aoeRadius, EPS);
        assertEquals(117f, hephaestus.contactDamage, EPS);
        assertEquals(24f, hephaestus.contactPenetration, EPS);
    }

    @Test
    void onlyAuthoredTravelingRoundsBecomeInterceptableProjectiles() {
        assertTrue(TurretKind.LOCUST.weapon().interceptableProjectile);
        assertEquals(70f, TurretKind.LOCUST.cellsPerSec(), EPS);
        assertTrue(TurretKind.GRENADE_LAUNCHER.weapon().interceptableProjectile);
        assertEquals(45f, TurretKind.GRENADE_LAUNCHER.cellsPerSec(), EPS);

        assertFalse(TurretKind.HEAVY_MORTAR.weapon().interceptableProjectile);
        assertEquals(0f, TurretKind.HEAVY_MORTAR.cellsPerSec(), EPS,
                "authored flight timing must not turn a resolved shell into a projectile entity");
        assertEquals(60f, TurretKind.HEAVY_MORTAR.directRoundVelocity(), EPS);
    }

    @Test
    void catalogRejectsUnknownAndWrongMountClassWeaponReferences() throws Exception {
        WeaponRegistry weapons = loadWeapons();
        assertThrows(JSONException.class, () -> new TurretCatalogRegistry().ingest(
                catalog("mount.bad", "weapon.missing", "structure.bad", "mount.bad", 1, 1), weapons));
        assertThrows(JSONException.class, () -> new TurretCatalogRegistry().ingest(
                catalog("mount.bad", "weapon.field-rifle", "structure.bad", "mount.bad", 1, 1), weapons));
    }

    @Test
    void catalogRejectsUnknownMountAndUnsupportedFootprint() throws Exception {
        WeaponRegistry weapons = loadWeapons();
        assertThrows(JSONException.class, () -> new TurretCatalogRegistry().ingest(
                catalog("mount.ok", "weapon.turret-vulcan", "structure.bad", "mount.missing", 1, 1), weapons));
        assertThrows(JSONException.class, () -> new TurretCatalogRegistry().ingest(
                catalog("mount.ok", "weapon.turret-vulcan", "structure.bad", "mount.ok", 2, 1), weapons));
    }

    @Test
    void catalogRejectsDuplicateMountAndStructureIds() throws Exception {
        WeaponRegistry weapons = loadWeapons();
        JSONObject duplicateMount = catalog(
                "mount.duplicate", "weapon.turret-vulcan",
                "structure.one", "mount.duplicate", 1, 1);
        duplicateMount.getJSONArray("mounts").put(
                duplicateMount.getJSONArray("mounts").getJSONObject(0));
        assertThrows(JSONException.class,
                () -> new TurretCatalogRegistry().ingest(duplicateMount, weapons));

        JSONObject duplicateStructure = catalog(
                "mount.ok", "weapon.turret-vulcan",
                "structure.duplicate", "mount.ok", 1, 1);
        duplicateStructure.getJSONArray("structures").put(
                duplicateStructure.getJSONArray("structures").getJSONObject(0));
        assertThrows(JSONException.class,
                () -> new TurretCatalogRegistry().ingest(duplicateStructure, weapons));
    }

    @Test
    void compatibilityHandleResolvesTheRegistryOwnedObjects() {
        StructureDef structure = TurretCatalogRegistry.requireStructure(TurretKind.VULCAN.structureId);
        assertSame(structure, TurretKind.VULCAN.structure());
        assertSame(structure.mount, TurretKind.VULCAN.mount());
        assertSame(structure.mount.weapon, TurretKind.VULCAN.weapon());
    }

    private static void assertKind(
            TurretKind kind, String displayName,
            float range, float damage, float penetration, float accuracy, float cooldown,
            float maxStructure, float armorPool, float armorRating,
            float turnRate, float visualCells, float projectileVisualCells, int ammo,
            int burstCount, float burstSpacing, float aoeRadius, int wallDamage,
            float wallDamageRadius, float arcHeight, float flightSec, float hitSpread,
            float minRange, boolean smokeTrail, boolean indirectFire, float noLosAccuracyMult,
            float cellsPerSec, float contactDamage, float contactPenetration,
            boolean boostRamp, boolean launchBackblast, ImpactProfile impact,
            String sprite, String recoilSprite, String projectileSprite, String fireSound) {
        assertEquals(displayName, kind.displayName());
        assertEquals(range, kind.range(), EPS);
        assertEquals(damage, kind.damage(), EPS);
        assertEquals(penetration, kind.penetration(), EPS);
        assertEquals(accuracy, kind.accuracy(), EPS);
        assertEquals(cooldown, kind.cooldown(), EPS);
        assertEquals(maxStructure, kind.maxStructure(), EPS);
        assertEquals(armorPool, kind.armorPool(), EPS);
        assertEquals(armorRating, kind.armorRating(), EPS);
        assertEquals(turnRate, kind.turnRateDegPerSec(), EPS);
        assertEquals(visualCells, kind.visualCells(), EPS);
        assertEquals(projectileVisualCells, kind.projectileVisualCells(), EPS);
        assertEquals(ammo, kind.startingAmmo());
        assertEquals(burstCount, kind.burstCount());
        assertEquals(burstSpacing, kind.burstSpacing(), EPS);
        assertEquals(aoeRadius, kind.aoeRadius(), EPS);
        assertEquals(wallDamage, kind.wallDamage());
        assertEquals(wallDamageRadius, kind.wallDamageRadius(), EPS);
        assertEquals(arcHeight, kind.arcHeight(), EPS);
        assertEquals(flightSec, kind.flightSec(), EPS);
        assertEquals(hitSpread, kind.hitSpread(), EPS);
        assertEquals(minRange, kind.minRange(), EPS);
        assertEquals(smokeTrail, kind.smokeTrail());
        assertEquals(indirectFire, kind.indirectFire());
        assertEquals(noLosAccuracyMult, kind.noLosAccuracyMult(), EPS);
        assertEquals(cellsPerSec, kind.cellsPerSec(), EPS);
        assertEquals(contactDamage, kind.contactDamage(), EPS);
        assertEquals(contactPenetration, kind.contactPenetration(), EPS);
        assertEquals(boostRamp, kind.hasBoostRamp());
        assertEquals(launchBackblast, kind.hasLaunchBackblast());
        assertSame(impact, kind.impactProfile());
        assertEquals(sprite, kind.spritePath());
        assertEquals(recoilSprite, kind.recoilSpritePath());
        assertEquals(projectileSprite, kind.projectileSpritePath());
        assertEquals(fireSound, kind.fireSoundId());
    }

    private static WeaponRegistry loadWeapons() throws Exception {
        WeaponRegistry weapons = new WeaponRegistry();
        for (String path : WeaponRegistry.BUILTIN_CATALOGS) {
            weapons.ingest(new JSONObject(Files.readString(Path.of("mod", path))));
        }
        return weapons;
    }

    private static JSONObject catalog(String mountId, String weaponId,
                                      String structureId, String structureMountId,
                                      int footprintX, int footprintY) throws JSONException {
        return new JSONObject("""
                {
                  "mounts": [{
                    "id": "%s", "weapon": "%s",
                    "sim": { "ammoCapacity": 1, "turnRateDegPerSec": 1.0 },
                    "render": { "visualCells": 1.0 }
                  }],
                  "structures": [{
                    "id": "%s", "mount": "%s",
                    "catalog": { "displayName": "Test" },
                    "durability": { "structure": 1.0, "armorPool": 1.0, "armorRating": 1.0 },
                    "physics": { "radius": 0.5, "hitHalfHeight": 0.5,
                                 "footprintCells": [%d, %d] },
                    "forceScore": 1.0
                  }]
                }
                """.formatted(mountId, weaponId, structureId, structureMountId,
                        footprintX, footprintY));
    }
}
