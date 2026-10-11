package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipArmourTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"weaponSlots":[]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static Warship ship() throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) {
                    return new JSONObject("{\"hullId\":\"test\",\"weaponGroups\":[]}");
                }
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(Map.of("id", "test", "hitpoints", "20000", "armor rating", "1750"));
            }
        });
        VanillaWeapons.clearCache();
        return Warship.fit("test", "test_Standard", Faction.MARINE, 50f, 50f, 0f);
    }

    @Test
    void hitPointsAndArmourComeFromTheHullTable() throws Exception {
        Warship s = ship();
        assertEquals(20000f, s.maxHitPoints);
        assertEquals(1750f, s.armour);
        assertEquals(1f, s.hullFraction());
        assertFalse(s.destroyed());
    }

    @Test
    void smallArmsGlanceOff() {
        assertEquals(0f, Warship.effectiveDamage(18f, 5f, 1750f), "a rifle round does nothing");
        assertEquals(0f, Warship.effectiveDamage(22.5f, 4f, 1750f), "nor a heavy machine gun");
    }

    @Test
    void groundOrdnanceBitesAndAntiShipOrdnanceBitesHard() {
        float orbital = Warship.effectiveDamage(80f, 24f, 1750f);
        assertTrue(orbital > 30f && orbital < 80f, "orbital barrage takes about half: " + orbital);
        float antiShip = Warship.effectiveDamage(600f, 60f, 1750f);
        assertTrue(antiShip > 0.9f * 600f, "an anti-ship round nearly all gets through: " + antiShip);
        assertTrue(Warship.effectiveDamage(81f, 10f, 1750f) < Warship.effectiveDamage(81f, 40f, 1750f),
                "more penetration, more damage");
    }

    @Test
    void aVolleyOfRiflesLeavesTheHullAloneAndHeavyHitsDestroyIt() throws Exception {
        Warship s = ship();
        for (int i = 0; i < 1000; i++) s.takeDamage(18f, 5f);
        assertEquals(1f, s.hullFraction(), "a thousand rifle rounds");
        int hits = 0;
        while (!s.destroyed() && hits < 1000) {
            s.takeDamage(600f, 60f);
            hits++;
        }
        assertTrue(s.destroyed());
        assertTrue(hits >= 30 && hits <= 40, "about twenty thousand points of anti-ship hits: " + hits);
        assertEquals(0f, s.takeDamage(600f, 60f), "a wreck takes nothing more");
        assertEquals(0f, s.hullFraction());
    }

    @Test
    void theFootprintIsTheHullRectangleAboutItsCentre() throws Exception {
        Warship s = ship();
        // Centre of gravity equals sprite centre here: 100 px wide, 400 px long, scaled to cells.
        float halfLength = s.lengthCells / 2f;
        float halfWidth = s.widthCells / 2f;
        assertEquals(0f, s.distanceTo(s.x, s.y + halfLength * 0.9f), 1e-4);
        assertEquals(2f, s.distanceTo(s.x, s.y + halfLength + 2f), 1e-3, "off the bow");
        assertEquals(3f, s.distanceTo(s.x + halfWidth + 3f, s.y), 1e-3, "off the beam");
        s.facingDegrees = 90f;
        // Turned a quarter, the long axis now runs along world -X (forward is (-sin h, cos h)).
        assertEquals(0f, s.distanceTo(s.x - halfLength * 0.9f, s.y), 1e-3);
        assertEquals(2f, s.distanceTo(s.x - halfLength - 2f, s.y), 1e-3);
    }
}
