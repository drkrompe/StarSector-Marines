package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TurretWeaponDefValidationTest {

    @Test
    void contactPayloadRequiresDamageAndPenetrationTogether() throws Exception {
        JSONObject json = weapon();
        json.getJSONObject("sim").put("contact", new JSONObject().put("damage", 10.0));
        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    @Test
    void interceptableProjectileRequiresPositiveVelocity() throws Exception {
        JSONObject json = weapon();
        json.getJSONObject("sim").put("interceptableProjectile", true);
        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    @Test
    void boostRampRequiresAnInterceptableProjectile() throws Exception {
        JSONObject json = weapon();
        json.getJSONObject("sim").put("boostRamp", true);
        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    @Test
    void noLosMultiplierRequiresIndirectFire() throws Exception {
        JSONObject json = weapon();
        json.getJSONObject("sim").put("noLosAccuracyMult", 0.5);
        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    @Test
    void projectileCountMustBePositive() throws Exception {
        JSONObject json = weapon();
        json.getJSONObject("sim").put("projectilesPerShot", 0);
        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    private static JSONObject weapon() throws Exception {
        return new JSONObject("""
                {
                  "id": "weapon.test-turret",
                  "mount": "turret-mount",
                  "catalog": {
                    "displayName": "Test", "modelName": "Test",
                    "designation": "TST", "designationTiered": false
                  },
                  "sim": {
                    "range": 10.0, "damage": 10.0, "accuracy": 0.5,
                    "cooldown": 1.0, "penetration": 1.0
                  }
                }
                """);
    }
}
