package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void tracerTailRequiresANonNegativeLengthAndProjectileSprite() throws Exception {
        JSONObject negative = primaryWeapon();
        negative.getJSONObject("render").put("tracerTailCells", -0.1);
        assertThrows(JSONException.class, () -> WeaponDef.parse(negative));

        JSONObject noSprite = primaryWeapon();
        noSprite.getJSONObject("render").put("tracerTailCells", 0.5);
        assertThrows(JSONException.class, () -> WeaponDef.parse(noSprite));
    }

    @Test
    void tracerRoundCadenceMarksEveryNthCountedRound() {
        WeaponDef.TracerRound everyFourth = new WeaponDef.TracerRound(4, Color.RED, 1.5f);
        boolean[] marked = new boolean[9];
        for (int i = 0; i < marked.length; i++) marked[i] = everyFourth.marks(i);
        assertArrayEquals(new boolean[] {false, false, false, true, false, false, false, true, false}, marked);
        assertFalse(everyFourth.marks(-1), "an uncounted round is never a tracer");
        assertTrue(new WeaponDef.TracerRound(1, Color.RED, 1f).marks(0), "every 1 marks every round");
    }

    @Test
    void tracerRoundParsesAndDefaultsItsTailToTheWeaponsTail() throws Exception {
        assertNull(WeaponDef.parse(tracedWeapon()).tracerRound(), "no tracerEvery, no tracer rounds");

        JSONObject json = tracedWeapon();
        json.getJSONObject("render").put("tracerEvery", 4).put("tracerRoundColor", "#FF4A2A");
        WeaponDef.TracerRound round = WeaponDef.parse(json).tracerRound();
        assertEquals(4, round.every());
        assertEquals(new Color(0xFF, 0x4A, 0x2A), round.color());
        assertEquals(0.5f, round.tailCells(), 0f);
    }

    @Test
    void tracerRoundRejectsBadCadenceColorAndOrphanKeys() throws Exception {
        for (Object every : new Object[] {0, -4, 2.5, "four"}) {
            JSONObject json = tracedWeapon();
            json.getJSONObject("render").put("tracerEvery", every).put("tracerRoundColor", "FF4A2A");
            assertThrows(JSONException.class, () -> WeaponDef.parse(json), "tracerEvery " + every);
        }
        for (String color : new String[] {"FF4A2", "GG4A2A"}) {
            JSONObject json = tracedWeapon();
            json.getJSONObject("render").put("tracerEvery", 4).put("tracerRoundColor", color);
            assertThrows(JSONException.class, () -> WeaponDef.parse(json), "tracerRoundColor " + color);
        }
        JSONObject noColor = tracedWeapon();
        noColor.getJSONObject("render").put("tracerEvery", 4);
        assertThrows(JSONException.class, () -> WeaponDef.parse(noColor));

        JSONObject orphan = tracedWeapon();
        orphan.getJSONObject("render").put("tracerRoundColor", "FF4A2A");
        assertThrows(JSONException.class, () -> WeaponDef.parse(orphan));

        JSONObject badTail = tracedWeapon();
        badTail.getJSONObject("render").put("tracerEvery", 4).put("tracerRoundColor", "FF4A2A")
                .put("tracerRoundTailCells", 0);
        assertThrows(JSONException.class, () -> WeaponDef.parse(badTail));

        JSONObject noTail = tracedWeapon();
        noTail.getJSONObject("render").remove("tracerTailCells");
        noTail.getJSONObject("render").put("tracerEvery", 4).put("tracerRoundColor", "FF4A2A");
        assertThrows(JSONException.class, () -> WeaponDef.parse(noTail), "a tracer round needs a tail to draw");
    }

    /** A primary with a projectile sprite and a half-cell tail: the minimum a tracer round can ride. */
    private static JSONObject tracedWeapon() throws Exception {
        JSONObject json = primaryWeapon();
        json.getJSONObject("render")
                .put("projectileSprite", "graphics/missiles/shell_small_yellow.png")
                .put("projectileVisualCells", 0.18)
                .put("tracerTailCells", 0.5);
        return json;
    }

    private static JSONObject primaryWeapon() throws Exception {
        return new JSONObject("""
                {
                  "id": "weapon.test-primary",
                  "mount": "marine-primary",
                  "catalog": {
                    "displayName": "Test Rifle",
                    "designationTiered": false,
                    "role": "LINE", "description": "A test primary."
                  },
                  "sim": {
                    "range": 10.0, "damage": 10.0, "accuracy": 0.5,
                    "cooldown": 1.0, "penetration": 1.0
                  },
                  "render": {
                    "heldSpriteFamily": "RIFLE"
                  },
                  "fx": {
                    "impact": [{"kind":"glow", "radius":0.2, "lifetime":0.1}]
                  }
                }
                """);
    }
}
