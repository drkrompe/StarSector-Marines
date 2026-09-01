package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What {@code onApplicationLoad} would have done for the catalogs an armed
 * squad reads: the weapon registry and the special-equipment registry.
 *
 * <p>Both entry points call this before any scene plays. {@code SceneBuilder}
 * arms every squad by default, and both catalogs fail loud rather than
 * degrade, so a scene with a default kit cannot stand its world up until they
 * are in — the first scene on the instrument died twice at startup for exactly
 * this. Idempotent: a registry already installed is left alone, which is what
 * lets the snapshot runner call it for every suite in a row.
 */
public final class SceneRegistries {

    private SceneRegistries() {}

    public static void installArmoury(Path projectRoot) throws Exception {
        DiskRegistries.installMapGeneration(projectRoot);
        if (SpecialEquipmentRegistry.installed() == null) {
            SpecialEquipmentRegistry equipment = new SpecialEquipmentRegistry();
            for (String path : SpecialEquipmentRegistry.BUILTIN_CATALOGS) {
                equipment.ingest(new JSONObject(
                        Files.readString(projectRoot.resolve("mod").resolve(path))));
            }
            equipment.validateReferences();
            SpecialEquipmentRegistry.install(equipment);
        }
    }
}
