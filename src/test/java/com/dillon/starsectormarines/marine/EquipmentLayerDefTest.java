package com.dillon.starsectormarines.marine;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void aPackedLayerHasOneAlwaysVisiblePlacementOnTheBody() throws Exception {
        EquipmentLayerDef packed = EquipmentLayerDef.parsePacked(new JSONObject("""
                {"sprite":"packed.png","widthShoulders":0.3,"heightShoulders":0.96,
                 "pivot":[0.5,0.5],"offsetShoulders":[0,-0.03],"angleDegrees":-35,
                 "occlusion":"over-body"}
                """), "launcher");
        assertEquals(EquipmentLayerDef.Attachment.BODY, packed.attachment());
        assertEquals(packed.carried(), packed.using());
        assertEquals(-35f, packed.carried().angleDegrees());
        assertEquals(EquipmentLayerDef.Occlusion.OVER_BODY, packed.carried().occlusion());
        assertTrue(packed.visibleWhileCarried());
        assertFalse(packed.replacePrimaryWhileUsing());
    }
}
