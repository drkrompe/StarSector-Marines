package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MarineWeaponDefValidationTest {

    @Test
    void marinePrimaryRequiresAnExplicitHeldSpriteFamily() throws Exception {
        JSONObject json = primaryWeapon();
        json.getJSONObject("render").remove("heldSpriteFamily");

        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    @Test
    void marinePrimaryRejectsMechBlastFields() throws Exception {
        JSONObject json = primaryWeapon();
        json.getJSONObject("sim").put("aoeRadius", 1.0);

        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    private static JSONObject primaryWeapon() throws Exception {
        return new JSONObject("""
                {
                  "id": "weapon.test-primary",
                  "mount": "marine-primary",
                  "catalog": {
                    "displayName": "Test Rifle", "modelName": "Test",
                    "designation": "TST", "designationTiered": false,
                    "role": "LINE", "description": "A test primary."
                  },
                  "sim": {
                    "range": 10.0, "damage": 10.0, "accuracy": 0.5,
                    "cooldown": 1.0, "penetration": 1.0
                  },
                  "render": {
                    "heldSpriteFamily": "RIFLE"
                  }
                }
                """);
    }
}
