package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MechWeaponDefValidationTest {

    @Test
    void mechMountAcceptsMountedBlastAndIndirectFields() throws Exception {
        assertDoesNotThrow(() -> WeaponDef.parse(mechWeapon()));
    }

    @Test
    void mechMountRejectsMarineAimCommitment() throws Exception {
        JSONObject json = mechWeapon();
        json.getJSONObject("sim").put("aimDuration", 0.5);

        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    @Test
    void mechMountRejectsMarineHeldSpriteFamily() throws Exception {
        JSONObject json = mechWeapon();
        json.getJSONObject("render").put("heldSpriteFamily", "RIFLE");

        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    @Test
    void mechMountRejectsRetiredEngineTrailBoolean() throws Exception {
        JSONObject json = mechWeapon();
        json.getJSONObject("render").put("engineTrail", true);

        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    private static JSONObject mechWeapon() throws Exception {
        return new JSONObject("""
                {
                  "id": "weapon.test-mech",
                  "mount": "mech-mount",
                  "catalog": {
                    "displayName": "Test Mech Gun", "modelName": "Test",
                    "designation": "TMG", "designationTiered": false
                  },
                  "sim": {
                    "range": 20.0, "damage": 10.0, "accuracy": 0.5,
                    "cooldown": 1.0, "penetration": 4.0,
                    "roundVelocity": 20.0, "flightSec": 1.0,
                    "aoeRadius": 1.0, "wallDamage": 3,
                    "wallDamageRadius": 1.0, "arcHeight": 2.0,
                    "interceptableProjectile": true,
                    "indirectFire": true, "noLosAccuracyMult": 0.5
                  },
                  "render": {
                    "projectileSprite": "graphics/test.png",
                    "projectileVisualCells": 0.5
                  },
                  "fx": {
                    "trail": [{"kind":"glow", "radius":0.2, "lifetime":0.2}],
                    "impact": [{"kind":"fire", "radius":0.5, "lifetime":0.3}]
                  }
                }
                """);
    }
}
