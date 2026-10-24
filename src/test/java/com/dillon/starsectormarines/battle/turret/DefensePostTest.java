package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefensePostTest {

    /** A gun that a composite builder puts up as a laser, and the laser. */
    private static final String CATALOG = """
            {
              "mounts": [{ "id": "mount.t", "weapon": "weapon.turret-vulcan",
                           "sim": { "ammoCapacity": 1, "turnRateDegPerSec": 1.0 }, "render": { "visualCells": 1.0 } }],
              "structures": [
                { "id": "structure.gun", "mount": "mount.t", "builtIn": { "composite": "structure.laser" },
                  "catalog": { "displayName": "Gun" },
                  "durability": { "structure": 1.0, "armorCapacity": 1.0, "armorRating": 1.0 },
                  "physics": { "radius": 0.5, "hitHalfHeight": 0.5, "footprintCells": [1, 1] }, "forceScore": 1.0 },
                { "id": "structure.laser", "mount": "mount.t",
                  "catalog": { "displayName": "Laser" },
                  "durability": { "structure": 1.0, "armorCapacity": 1.0, "armorRating": 1.0 },
                  "physics": { "radius": 0.5, "hitHalfHeight": 0.5, "footprintCells": [1, 1] }, "forceScore": 1.0 }
              ]
            }
            """;

    @Test
    void aPostBuiltInAStyleFitsWhatThatStyleBuildsAndRestylingStartsFromThePlan() throws Exception {
        WeaponRegistry weapons = new WeaponRegistry();
        for (String path : WeaponRegistry.BUILTIN_CATALOGS) weapons.ingest(new JSONObject(Files.readString(Path.of("mod", path))));
        TurretCatalogRegistry registry = new TurretCatalogRegistry();
        registry.ingest(new JSONObject(CATALOG), weapons);
        TurretCatalogRegistry previous = TurretCatalogRegistry.installed();
        TurretCatalogRegistry.install(registry);
        try {
            DefensePost post = new DefensePost(DefensePostKind.LIGHT, 0, 0,
                    List.of(new DefensePost.TurretSpec("structure.gun", 1, 2)));
            DefensePost composite = post.withStyle(EmplacementStyle.COMPOSITE);
            assertEquals("structure.laser", composite.turrets.get(0).structureId);
            assertEquals(1, composite.turrets.get(0).cellX);
            assertEquals("structure.gun", composite.withStyle(EmplacementStyle.DERELICT).turrets.get(0).structureId);
            assertEquals("structure.gun", post.withStyle(EmplacementStyle.MODULAR).turrets.get(0).structureId);
        } finally {
            TurretCatalogRegistry.install(previous);
        }
    }
}
