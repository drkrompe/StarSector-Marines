package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialEquipmentRegistryTest {

    private static final float EPS = 1e-6f;

    @Test
    void allBuiltInsResolveThroughTheDataRegistry() {
        assertEquals(7, SpecialEquipmentRegistry.installed().size());
        for (MarineSecondaryHandle handle : MarineSecondaryHandle.values()) {
            SpecialEquipmentDef def = SpecialEquipmentRegistry.require(handle.id);
            assertEquals(handle.id, def.id());
            assertNotNull(def.presentation());
            assertNotNull(def.presentation().layerClips());
        }
    }

    @Test
    void satchelDataOwnsCooldownAiAndEveryPresentationState() {
        SpecialEquipmentDef satchel = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
        assertSame(SpecialActivation.UTILITY_SATCHEL, satchel.activation());
        assertSame(SpecialResourceMode.COOLDOWN, satchel.resourceMode());
        assertSame(SpecialAiPolicy.CONTACT_DEMOLITION, satchel.aiPolicy());
        assertSame(SpecialUsePose.PLANT, satchel.presentation().usePose());
        assertEquals(0, satchel.startingAmmo());
        assertEquals(22f, satchel.satchelChargeSpec().cooldownSeconds(), EPS);
        assertEquals(0.38f, satchel.presentation().carrierLayer().widthShoulders(), EPS);
        assertTrue(satchel.presentation().carrierLayer().visibleWhileCarried());
        assertTrue(satchel.presentation().carrierLayer().replacePrimaryWhileUsing());
        assertEquals("using", satchel.presentation().preview().state());
        assertEquals(0.54f, satchel.presentation().deployed().visualCells(), EPS);
    }

    @Test
    void smokeDataProvidesAnActionLayerForCombinedPoseAuthoring() {
        SpecialEquipmentDef smoke = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.SMOKE_GRENADE_ID);

        assertSame(SpecialUsePose.THROW, smoke.presentation().usePose());
        assertNotNull(smoke.presentation().carrierLayer());
        assertEquals(0.2f, smoke.presentation().carrierLayer().widthShoulders(), EPS);
        assertFalse(smoke.presentation().carrierLayer().visibleWhileCarried());
    }

    @Test
    void fragmentationGrenadeIsAThreeUseArcExplosive() {
        SpecialEquipmentDef frag = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.FRAG_GRENADE_ID);
        assertSame(SpecialActivation.ARC_EXPLOSIVE, frag.activation());
        assertSame(SpecialAiPolicy.SOFT_CLUSTER_INDIRECT, frag.aiPolicy());
        assertSame(SpecialResourceMode.AMMUNITION, frag.resourceMode());
        assertSame(SpecialUsePose.THROW, frag.presentation().usePose());
        assertEquals(3, frag.startingAmmo());
        assertNotNull(frag.presentation().carrierLayer());
        assertNotNull(frag.presentation().thrown());
    }

    @Test
    void everyAuthoredPresentationAssetExists() {
        for (SpecialEquipmentDef def : SpecialEquipmentRegistry.installed().all()) {
            assertAsset(def.id(), def.presentation().armoryIconPath());
            assertAsset(def.id(), def.presentation().aimSpritePath());
            if (def.presentation().carrierLayer() != null) {
                assertAsset(def.id(), def.presentation().carrierLayer().spritePath());
            }
            if (def.presentation().thrown() != null) {
                assertAsset(def.id(), def.presentation().thrown().spritePath());
            }
            assertAsset(def.id(), def.presentation().fieldSpritePath());
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

    private enum MarineSecondaryHandle {
        ROCKET(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
        AMR(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID),
        SMOKE(SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
        SATCHEL(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
        FRAG(SpecialEquipmentRegistry.FRAG_GRENADE_ID);

        final String id;

        MarineSecondaryHandle(String id) {
            this.id = id;
        }
    }
}
