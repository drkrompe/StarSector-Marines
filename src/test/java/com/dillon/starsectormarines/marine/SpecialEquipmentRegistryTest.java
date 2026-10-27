package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialEquipmentRegistryTest {

    /**
     * The code-to-data bridge, derived rather than listed. Every {@code *_ID}
     * constant the registry publishes must resolve to a catalogued definition,
     * and every catalogued definition must be reachable through one — a
     * constant pointing at a deleted entry and an entry no code can name are
     * both real breakages, and neither is what a count would have caught.
     */
    @Test
    void everyPublishedIdResolvesAndEveryEntryIsNameable() throws IllegalAccessException {
        Set<String> published = new TreeSet<>();
        for (Field field : SpecialEquipmentRegistry.class.getDeclaredFields()) {
            if (!field.getName().endsWith("_ID") || field.getType() != String.class) continue;
            if (!Modifier.isStatic(field.getModifiers())
                    || !Modifier.isPublic(field.getModifiers())) continue;
            published.add((String) field.get(null));
        }
        assertFalse(published.isEmpty(), "the registry should publish its ids as constants");

        Set<String> catalogued = new TreeSet<>();
        for (SpecialEquipmentDef def : SpecialEquipmentRegistry.installed().all()) {
            catalogued.add(def.id());
        }
        assertEquals(published, catalogued,
                "every published id must be catalogued and every catalogued entry nameable");
    }

    /**
     * Structural completeness across the whole catalog. Adding equipment widens
     * this test instead of breaking it, which is the point: the previous version
     * walked a hand-maintained list of five while the catalog held seven, so the
     * two newest entries were asserted by nothing at all.
     */
    @Test
    void everyCataloguedSpecialIsStructurallyComplete() {
        Collection<SpecialEquipmentDef> all = SpecialEquipmentRegistry.installed().all();
        assertFalse(all.isEmpty(), "the built-in catalog must load");
        for (SpecialEquipmentDef def : all) {
            assertSame(def, SpecialEquipmentRegistry.require(def.id()), def.id());
            assertNotNull(def.displayName(), def.id());
            assertFalse(def.displayName().isBlank(), def.id());
            assertNotNull(def.activation(), def.id());
            assertNotNull(def.aiPolicy(), def.id());
            assertNotNull(def.resourceMode(), def.id());
            assertNotNull(def.presentation(), def.id());
            // A shoulder pod never takes the pose, so it has no clips to name.
            if (!def.isShoulderPod()) assertNotNull(def.presentation().layerClips(), def.id());
            if (def.resourceMode() == SpecialResourceMode.AMMUNITION) {
                assertTrue(def.startingAmmo() > 0,
                        def.id() + " is ammunition-gated and must start with uses");
            }
        }
    }

    @Test
    void everyAuthoredPresentationAssetExists() {
        for (SpecialEquipmentDef def : SpecialEquipmentRegistry.installed().all()) {
            assertAsset(def.id(), def.presentation().armoryIconPath());
            if (def.presentation().carrierLayer() != null) {
                assertAsset(def.id(), def.presentation().carrierLayer().spritePath());
            }
            if (def.presentation().thrown() != null) {
                assertAsset(def.id(), def.presentation().thrown().spritePath());
            }
            if (def.presentation().field() != null) {
                assertAsset(def.id(), def.presentation().field().sheetPath());
            }
            if (def.presentation().deployed() != null) {
                assertAsset(def.id(), def.presentation().deployed().spritePath());
            }
        }
    }

    @Test
    void invalidPolicyAndResourceCombinationsFailLoud() throws Exception {
        JSONObject satchel = builtInEntry(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
        satchel.getJSONObject("ai").put("policy", "hardened-direct-fire");
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(satchel));

        JSONObject rocket = builtInEntry(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);
        rocket.getJSONObject("resource").put("startingAmmo", 0);
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(rocket));

        JSONObject smoke = builtInEntry(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        smoke.getJSONObject("presentation").getJSONObject("layerClips")
                .remove("using");
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(smoke));

        // A shoulder pod authors its squad spacing, and may never take the primary's place.
        JSONObject unspaced = builtInEntry(SpecialEquipmentRegistry.MISSILE_POD_ID);
        unspaced.getJSONObject("activation").remove("squadSalvoIntervalSeconds");
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(unspaced));
        JSONObject replacing = builtInEntry(SpecialEquipmentRegistry.MISSILE_POD_ID);
        replacing.getJSONObject("presentation").getJSONObject("carrierLayer")
                .put("replacePrimaryWhileUsing", true);
        assertThrows(JSONException.class, () -> SpecialEquipmentDef.parse(replacing));
    }

    @Test
    void duplicateStableIdsAreRejected() throws Exception {
        JSONObject satchel = builtInEntry(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
        SpecialEquipmentRegistry registry = new SpecialEquipmentRegistry();
        JSONObject root = new JSONObject().put("equipment",
                new JSONArray().put(satchel).put(new JSONObject(satchel.toString())));
        assertThrows(JSONException.class, () -> registry.ingest(root));
    }

    private static JSONObject builtInEntry(String id) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Paths.get("mod",
                SpecialEquipmentRegistry.BUILTIN_CATALOGS.get(0))));
        JSONArray equipment = root.getJSONArray("equipment");
        for (int i = 0; i < equipment.length(); i++) {
            JSONObject entry = equipment.getJSONObject(i);
            if (id.equals(entry.getString("id"))) return new JSONObject(entry.toString());
        }
        throw new IllegalArgumentException("No built-in equipment " + id);
    }

    private static void assertAsset(String equipmentId, String relativePath) {
        if (relativePath == null) return;
        Path path = Paths.get("mod").resolve(relativePath);
        assertTrue(Files.isRegularFile(path), equipmentId + " asset does not exist: " + path);
    }
}
