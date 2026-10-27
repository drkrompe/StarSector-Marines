package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a weapon throws, read off how it is drawn. */
class OrdnanceTest {

    @Test
    void aShellFromTheBaseGamesMissileFolderIsStillBallistic() throws JSONException {
        assertEquals(Ordnance.BALLISTIC, Ordnance.of(weapon("RIFLE", "graphics/missiles/shell_small_yellow.png", null)));
        assertEquals(Ordnance.BALLISTIC, Ordnance.of(weapon("RIFLE", null, null)));
    }

    @Test
    void aMissileFliesAMissileSpriteOrTrailsAContrail() throws JSONException {
        assertEquals(Ordnance.MISSILE, Ordnance.of(weapon("RIFLE", "graphics/missiles/missile_salamander.png", null)));
        assertEquals(Ordnance.MISSILE, Ordnance.of(weapon("RIFLE", null, "missile-smoke")));
    }

    @Test
    void aLaserIsEnergy() throws JSONException {
        assertEquals(Ordnance.ENERGY, Ordnance.of(weapon("LASER_GUN", null, null)));
    }

    private static WeaponDef weapon(String held, String sprite, String contrail) throws JSONException {
        JSONObject render = new JSONObject();
        render.put("heldSpriteFamily", held);
        if (sprite != null) render.put("projectileSprite", sprite);
        if (contrail != null) render.put("contrail", contrail);
        return WeaponDef.parse(new JSONObject("""
                {
                  "id": "weapon.ordnance-evidence", "mount": "marine-primary",
                  "catalog": { "displayName": "Evidence",
                               "role": "LINE", "description": "An ordnance fixture." },
                  "sim": { "range": 20.0, "damage": 10.0, "accuracy": 1.0, "cooldown": 0.5, "penetration": 1.0 },
                  "fx": { "impact": [ {"kind": "glow", "radius": 0.2, "lifetime": 0.1} ] }
                }
                """).put("render", render));
    }
}
