package com.dillon.starsectormarines.battle.infantry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The one billet rule the battle and the company card both read, and the kit table. */
public class SquadMedicineTest {

    @Test
    public void theMiddleTeamComesFirstThenRosterOrder() {
        int[] team = {0, 0, 1, 1, 2};
        assertEquals(2, SquadMedicine.medicBillet(5, i -> true, i -> team[i]));
        assertEquals(3, SquadMedicine.medicBillet(5, i -> i != 2, i -> team[i]));
        assertEquals(0, SquadMedicine.medicBillet(5, i -> team[i] != 1, i -> team[i]),
                "with nobody eligible in the middle team, the first eligible anywhere");
        assertEquals(-1, SquadMedicine.medicBillet(5, i -> false, i -> team[i]));
    }

    @Test
    public void kitsAreKeyedByTheirSystemAndAuthoredOnce() throws JSONException {
        JSONObject root = new JSONObject().put("kits", new JSONArray()
                .put(medicine(12).put("system", "system.a"))
                .put(medicine(20).put("system", "system.b")));
        Map<String, SquadMedicine> kits = SquadMedicine.parseKits(root);
        assertEquals(12f, kits.get("system.a").energyMax());
        assertEquals(20f, kits.get("system.b").energyMax());

        root.getJSONArray("kits").put(medicine(30).put("system", "system.a"));
        assertThrows(JSONException.class, () -> SquadMedicine.parseKits(root));
    }

    private static JSONObject medicine(float pool) throws JSONException {
        return new JSONObject().put("energyMax", pool).put("energyRegenPerSecond", 0.1)
                .put("energyPerPoint", 1).put("minStartEnergy", pool / 4f)
                .put("woundFloorFraction", 0.05).put("healthPerSecond", 2)
                .put("armorPerSecond", 0.5).put("reachCells", 1.5).put("approachSeconds", 8)
                .put("retrySeconds", 3).put("treatBelowHealth", 0.6).put("treatBelowArmor", 0.5)
                .put("heavyFirePressure", 2);
    }
}
