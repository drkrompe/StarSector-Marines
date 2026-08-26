package com.dillon.starsectormarines.catalog;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineCatalogManifestTest {

    @Test
    void coreManifestDeclaresTileResourcesInCompatibilityOrder() throws Exception {
        MarineCatalogManifest manifest = MarineCatalogManifest.parse(
                "starsector_marines", new JSONObject(Files.readString(Path.of(
                        "mod/data/marines/starsector-marines.catalog.json"))));

        assertEquals(TileRegistry.BUILTIN_TILESETS, manifest.tilesets().stream()
                .map(file -> file.source().path()).toList());
        assertEquals(GenMappingRegistry.BUILTIN_MAPPINGS, manifest.tileMappings().stream()
                .map(file -> file.source().path()).toList());
    }

    @Test
    void parsesExplicitCatalogsWithProviderProvenance() throws Exception {
        MarineCatalogManifest manifest = MarineCatalogManifest.parse("oc_faction", new JSONObject("""
                {
                  "schemaVersion": 1,
                  "tilesets": ["data/oc/terrain.tileset.json"],
                  "tileMappings": ["data/oc/terrain.mapping.json"],
                  "weapons": ["data/oc/weapons.weapon.json"],
                  "armor": ["data/oc/armor.armor.json"],
                  "groundRosters": ["data/oc/roster.roster.json"],
                  "equipmentTemplates": ["data/oc/templates.template.json"]
                }
                """));

        assertEquals("oc_faction", manifest.tilesets().get(0).source().modId());
        assertEquals("data/oc/terrain.mapping.json",
                manifest.tileMappings().get(0).source().path());
        assertEquals("oc_faction", manifest.weapons().get(0).source().modId());
        assertEquals("data/oc/weapons.weapon.json",
                manifest.weapons().get(0).source().path());
        assertEquals(1, manifest.armor().size());
        assertEquals(0, manifest.specialEquipment().size());
    }

    @Test
    void rejectsTraversalAndUnknownSchemaVersions() {
        assertThrows(JSONException.class, () -> MarineCatalogManifest.parse("bad", new JSONObject("""
                {"schemaVersion": 1, "weapons": ["../other-mod/catalog.json"]}
                """)));
        assertThrows(JSONException.class, () -> MarineCatalogManifest.parse("future", new JSONObject("""
                {"schemaVersion": 2}
                """)));
    }

    @Test
    void discoversPresentManifestsInEnabledModOrderAndSkipsOnlyAbsentOnes() {
        List<String> loads = new ArrayList<>();

        MarineCatalogManifest manifest = MarineCatalogManifest.discoverEnabled(
                List.of("unrelated-api", "unrelated-game", "provider-a", "provider-b"),
                (path, modId) -> {
                    loads.add(modId + ":" + path);
                    if (modId.equals("unrelated-api")) throw new FileNotFoundException(path);
                    if (modId.equals("unrelated-game")) {
                        throw new RuntimeException("Error loading [" + path
                                + "] resource, not found in [unrelated-game]");
                    }
                    return new JSONObject("""
                            {"schemaVersion": 1, "weapons": ["data/%s.weapon.json"]}
                            """.formatted(modId));
                });

        assertEquals(List.of(
                "unrelated-api:" + MarineCatalogManifest.MANIFEST_PATH,
                "unrelated-game:" + MarineCatalogManifest.MANIFEST_PATH,
                "provider-a:" + MarineCatalogManifest.MANIFEST_PATH,
                "provider-b:" + MarineCatalogManifest.MANIFEST_PATH), loads);
        assertEquals(List.of("provider-a", "provider-b"), manifest.weapons().stream()
                .map(file -> file.source().modId())
                .toList());
    }

    @Test
    void failsLoudForUnreadableOrMalformedPresentManifest() {
        IllegalStateException unreadable = assertThrows(IllegalStateException.class,
                () -> MarineCatalogManifest.discoverEnabled(List.of("provider"),
                        (path, modId) -> {
                            throw new IOException("read failed");
                        }));
        assertInstanceOf(IOException.class, unreadable.getCause());
        assertTrue(unreadable.getMessage().contains("provider"));
        assertTrue(unreadable.getMessage().contains(MarineCatalogManifest.MANIFEST_PATH));

        IllegalStateException malformed = assertThrows(IllegalStateException.class,
                () -> MarineCatalogManifest.discoverEnabled(List.of("provider"),
                        (path, modId) -> new JSONObject("{not-json")));
        assertInstanceOf(JSONException.class, malformed.getCause());

        IllegalStateException unrelatedRuntime = assertThrows(IllegalStateException.class,
                () -> MarineCatalogManifest.discoverEnabled(List.of("provider"),
                        (path, modId) -> {
                            throw new RuntimeException("loader bug");
                        }));
        assertInstanceOf(RuntimeException.class, unrelatedRuntime.getCause());
    }
}
