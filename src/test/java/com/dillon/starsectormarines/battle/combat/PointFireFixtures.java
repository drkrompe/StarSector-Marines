package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONObject;

/** A slow, precise round makes flight and contact observable without a catalog roll. */
final class PointFireFixtures {
    private PointFireFixtures() { }

    static WeaponDef rifle(int burstCount) throws Exception {
        JSONObject definition = new JSONObject("""
                {
                  "id": "weapon.point-fire-evidence", "mount": "marine-primary",
                  "catalog": {
                    "displayName": "Evidence Rifle", "modelName": "Evidence",
                    "designation": "EV", "role": "LINE",
                    "description": "A controlled flight fixture."
                  },
                  "sim": {
                    "range": 20.0, "damage": 10.0, "accuracy": 1.0,
                    "cooldown": 1.0, "penetration": 1.0,
                    "burstSpacing": 0.1, "roundVelocity": 10.0,
                    "accuracyFalloff": 0.0, "hitSpread": 0.0
                  },
                  "render": { "heldSpriteFamily": "RIFLE" },
                  "fx": { "impact": [
                    {"kind": "glow", "radius": 0.2, "lifetime": 0.1}
                  ] }
                }
                """);
        definition.getJSONObject("sim").put("burstCount", burstCount);
        definition.getJSONObject("sim").put("burstSpacing", burstCount > 1 ? 0.1 : 0.0);
        return WeaponDef.parse(definition);
    }
}
