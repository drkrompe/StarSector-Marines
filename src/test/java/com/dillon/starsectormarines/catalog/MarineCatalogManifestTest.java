package com.dillon.starsectormarines.catalog;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarineCatalogManifestTest {

    @Test
    void parsesExplicitCatalogsWithProviderProvenance() throws Exception {
        MarineCatalogManifest manifest = MarineCatalogManifest.parse("oc_faction", new JSONObject("""
                {
                  "schemaVersion": 1,
                  "weapons": ["data/oc/weapons.weapon.json"],
                  "armor": ["data/oc/armor.armor.json"],
                  "groundRosters": ["data/oc/roster.roster.json"],
                  "equipmentTemplates": ["data/oc/templates.template.json"]
                }
                """));

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
}
