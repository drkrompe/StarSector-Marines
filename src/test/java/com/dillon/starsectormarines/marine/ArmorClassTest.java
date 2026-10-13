package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArmorClassTest {
    @Test
    void missingContributedClassDefaultsToRegularWithoutInferringFromRoleOrTier() throws JSONException {
        assertEquals(ArmorClass.STANDARD, MarineArmorCatalogDef.parse(pattern()).armorClass());
    }

    @Test
    void explicitPowerClassParsesAndInvalidProvidedClassIsRefused() throws JSONException {
        JSONObject pattern = pattern();
        pattern.getJSONObject("catalog").put("armorClass", "power-suit");
        assertEquals(ArmorClass.POWER_SUIT, MarineArmorCatalogDef.parse(pattern).armorClass());
        for (String invalid : new String[] { "", " ", "heavy", "powered" }) {
            pattern.getJSONObject("catalog").put("armorClass", invalid);
            assertThrows(JSONException.class, () -> MarineArmorCatalogDef.parse(pattern));
        }
        pattern.getJSONObject("catalog").put("armorClass", JSONObject.NULL);
        assertThrows(JSONException.class, () -> MarineArmorCatalogDef.parse(pattern));
    }

    @Test
    void oldPlanConstructorAndMissingSerializedComponentRetainRegularIntent() {
        assertEquals(ArmorClass.STANDARD, new SquadArmorPlan("old", "Old", "",
                ArmorTradition.HEGEMONY, SquadRoleMix.BALANCED).armorClass());
        assertEquals(ArmorClass.STANDARD, new SquadArmorPlan("missing", "Missing", "",
                ArmorTradition.HEGEMONY, SquadRoleMix.BALANCED, null).armorClass());
    }

    private static JSONObject pattern() throws JSONException {
        return new JSONObject("""
                {"id":"armor.test","catalog":{"displayName":"Test","role":"assault",
                "tradition":"hegemony","description":"Test","tier":4,"iconPath":"test.png"},
                "battle":{"appearanceFamily":"RED_ELITE","armorCapacity":20,"armorRating":12,
                "moveSpeedMult":1,"incomingAccuracyMult":1}}
                """);
    }
}
