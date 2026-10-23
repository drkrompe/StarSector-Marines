package com.dillon.starsectormarines.battle.turret;

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
    void catalogAcceptsOddMultiCellFootprintsAndRejectsEvenOrEmptyOnes() throws Exception {
        WeaponRegistry weapons = loadWeapons();
        TurretCatalogRegistry registry = new TurretCatalogRegistry();
        registry.ingest(catalog("mount.big", "weapon.turret-vulcan", "structure.big", "mount.big", 3, 3), weapons);
        StructureDef big = registry.getStructure("structure.big");
        assertEquals(3, big.footprintCellsX);
        assertEquals(3, big.footprintCellsY);
        assertTrue(big.isMultiCell());
        TurretCatalogRegistry wide = new TurretCatalogRegistry();
        wide.ingest(catalog("mount.w", "weapon.turret-vulcan", "structure.w", "mount.w", 5, 3), weapons);
        assertTrue(wide.getStructure("structure.w").isMultiCell());
        assertFalse(registry.getStructure("structure.big") == null);
        for (int[] bad : new int[][]{{2, 2}, {3, 4}, {0, 1}, {-1, 1}}) {
            assertThrows(JSONException.class, () -> new TurretCatalogRegistry().ingest(
                    catalog("mount.ok", "weapon.turret-vulcan", "structure.bad", "mount.ok", bad[0], bad[1]), weapons));
        }
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
    void guidedMissilesRequireAnAirTargetPlatformAndValidFlightParameters() throws Exception {
        WeaponRegistry weapons = loadWeapons();
        JSONObject host = catalog("mount.test-aa", "weapon.ridge-aa", "structure.test-aa", "mount.test-aa", 3, 3);
        assertThrows(JSONException.class, () -> new TurretCatalogRegistry().ingest(host, weapons));
        host.getJSONArray("structures").getJSONObject(0).put("targetRole", "AA");
        new TurretCatalogRegistry().ingest(host, weapons);

        var catalog = new JSONObject(Files.readString(Path.of("mod/data/marines/turret-weapons.weapon.json")));
        JSONObject guided = null;
        var definitions = catalog.getJSONArray("weapons");
        for (int i = 0; i < definitions.length(); i++) {
            JSONObject weapon = definitions.getJSONObject(i);
            if (weapon.getString("id").equals("weapon.ridge-aa")) guided = weapon;
        }
        if (guided == null) throw new AssertionError("Missing guided AA weapon");
        for (String field : new String[]{"turnRateDegPerSec", "acceleration", "lifetime"}) {
            JSONObject invalid = new JSONObject(guided.toString());
            invalid.getJSONObject("sim").getJSONObject("airGuidance").put(field, 0);
            assertThrows(JSONException.class, () -> WeaponDef.parse(invalid), field);
        }
        JSONObject indirect = new JSONObject(guided.toString());
        indirect.getJSONObject("sim").put("indirectFire", true);
        assertThrows(JSONException.class, () -> WeaponDef.parse(indirect));
    }

    @Test
    void stableIdResolvesTheRegistryOwnedObjectGraph() {
        StructureDef structure = structure(TurretCatalogRegistry.VULCAN_STRUCTURE_ID);
        assertSame(structure.mount, TurretCatalogRegistry.requireMount(structure.mountId));
        assertSame(structure.mount.weapon, WeaponRegistry.require(structure.mount.weaponId));
    }

    private static StructureDef structure(String id) {
        return TurretCatalogRegistry.requireStructure(id);
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
                    "durability": { "structure": 1.0, "armorCapacity": 1.0, "armorRating": 1.0 },
                    "physics": { "radius": 0.5, "hitHalfHeight": 0.5,
                                 "footprintCells": [%d, %d] },
                    "forceScore": 1.0
                  }]
                }
                """.formatted(mountId, weaponId, structureId, structureMountId,
                        footprintX, footprintY));
    }
}
