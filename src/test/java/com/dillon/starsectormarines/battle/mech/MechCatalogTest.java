package com.dillon.starsectormarines.battle.mech;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every chassis and every fitted component must be describable, and the shipped
 * document is what has to describe them — so this reads the file off disk rather
 * than a fixture. {@code mod/data} is declared as a test input in
 * {@code build.gradle}, so editing the catalog re-runs this.
 */
class MechCatalogTest {

    @Test
    void everyChassisAndComponentHasAnEntryInTheShippedCatalog() throws Exception {
        MechCatalog catalog = MechCatalog.parse(new JSONObject(Files.readString(shipped())));

        for (MechVariant variant : MechVariant.values()) {
            MechCatalog.ChassisEntry entry = catalog.chassis(variant);
            assertNotNull(entry, variant + " has no catalog entry");
            assertFalse(entry.designation().isBlank(), variant + " needs a designation");
            assertFalse(entry.role().isBlank(), variant + " needs a chassis role");
            assertTrue(entry.description().length() > 80,
                    variant + " needs real provenance, not a label: " + entry.description());
        }
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            MechCatalog.ComponentEntry entry = catalog.component(component);
            assertNotNull(entry, component + " has no catalog entry");
            assertFalse(entry.designation().isBlank(), component + " needs a designation");
            assertTrue(entry.description().length() > 80,
                    component + " needs real provenance: " + entry.description());
        }
    }

    @Test
    void theInstalledCatalogIsTheShippedOne() {
        assertNotNull(MechCatalog.installed(), "the test bootstrap installs the mech catalog");
        for (MechVariant variant : MechVariant.values()) {
            assertNotNull(MechCatalog.require(variant));
        }
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            assertNotNull(MechCatalog.require(component));
        }
    }

    /**
     * A missing entry stops load rather than producing a chassis a screen can
     * say nothing about, which is the whole reason the catalog exists.
     */
    @Test
    void anIncompleteCatalogIsRefused() {
        String json = """
                {
                  "schemaVersion": 1,
                  "chassis": [
                    { "variant": "hound", "designation": "HND-3", "role": "Light",
                      "description": "A light frame." }
                  ],
                  "components": []
                }
                """;
        assertThrows(IllegalStateException.class,
                () -> MechCatalog.parse(new JSONObject(json)));
    }

    @Test
    void anUnknownIdIsRefused() {
        String json = """
                {
                  "schemaVersion": 1,
                  "chassis": [
                    { "variant": "goshawk", "designation": "GSH-1", "role": "Light",
                      "description": "A chassis that does not exist." }
                  ],
                  "components": []
                }
                """;
        assertThrows(JSONException.class, () -> MechCatalog.parse(new JSONObject(json)));
    }

    @Test
    void anEntryMissingItsProseIsRefused() {
        String json = """
                {
                  "schemaVersion": 1,
                  "chassis": [
                    { "variant": "hound", "designation": "HND-3", "role": "Light" }
                  ],
                  "components": []
                }
                """;
        assertThrows(JSONException.class, () -> MechCatalog.parse(new JSONObject(json)));
    }

    private static Path shipped() {
        return Path.of("mod", "data", "mechs", "mech-catalog.mech.json");
    }
}
