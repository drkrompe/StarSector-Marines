package com.dillon.starsectormarines.marine;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquipmentLayerDefTest {
    private JSONObject carrier() throws JSONException {
        return new JSONObject("""
                {"sprite":"pod.png","widthShoulders":0.32,"heightShoulders":0.48,
                 "pivot":[0.5,0.5],
                 "carried":{"offsetShoulders":[0,0],"occlusion":"over-body"},
                 "using":{"offsetShoulders":[0,0],"occlusion":"over-body"}}
                """);
    }

    @Test
    void muzzleDefaultsToNorthCenterAndAcceptsAuthoredInteriorPoint() throws Exception {
        EquipmentLayerDef legacy = EquipmentLayerDef.parse(carrier(), "pod");
        assertEquals(.5f, legacy.muzzleX());
        assertEquals(0f, legacy.muzzleY());
        EquipmentLayerDef authored = EquipmentLayerDef.parse(
                carrier().put("muzzle", new JSONArray("[0.4,0.10416667]")), "pod");
        assertEquals(.4f, authored.muzzleX());
        assertEquals(.10416667f, authored.muzzleY());
    }

    @Test
    void rejectsMalformedMuzzleCoordinates() {
        assertThrows(JSONException.class, () -> EquipmentLayerDef.parse(
                carrier().put("muzzle", "not-an-array"), "pod"));
        assertThrows(JSONException.class, () -> EquipmentLayerDef.parse(
                carrier().put("muzzle", new JSONArray("[0.5]")), "pod"));
        assertThrows(JSONException.class, () -> EquipmentLayerDef.parse(
                carrier().put("muzzle", new JSONArray("[1.1,0]")), "pod"));
    }
}
