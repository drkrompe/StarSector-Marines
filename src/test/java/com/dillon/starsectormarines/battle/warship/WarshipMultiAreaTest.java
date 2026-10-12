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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The areas a directed ship holds at once: how they are chosen, kept apart and merged with a manual order. */
class WarshipMultiAreaTest {

    private static final String HULL = """
            {"hullId":"test","spriteName":"graphics/ships/test.png","width":100,"height":400,
             "center":[50,200],"weaponSlots":[
              {"id":"WS 001","type":"TURRET","size":"LARGE","mount":"TURRET","angle":0,"arc":360,"locations":[0,100]}]}""";
    private static final String VARIANT = """
            {"hullId":"test","weaponGroups":[{"mode":"LINKED","weapons":{"WS 001":"gun_a"}}]}""";

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static Warship ship() throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject(HULL);
                if (path.equals("data/variants/test/test_Standard.variant")) return new JSONObject(VARIANT);
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                if (path.equals("data/hulls/ship_data.csv")) return List.of(Map.of("id", "test"));
                return List.of(Map.of("id", "gun_a", "damage/shot", "100", "type", "KINETIC", "range", "2000",
                        "burst size", "1", "chargedown", "1", "proj speed", "1000", "turn rate", "20", "hints", ""));
            }
        });
        VanillaWeapons.clearCache();
        return Warship.fit("test", "test_Standard", Faction.MARINE, 0f, 0f, 0f);
    }

    /** Three clusters of 5, 3 and 2 hostiles, each tight, 30 cells apart. */
    private static float[][] clusters() {
        float[] xs = {50, 51, 50, 52, 49, 80, 81, 79, 110, 111};
        float[] ys = {10, 11, 12, 10, 11, 10, 11, 12, 10, 11};
        return new float[][]{xs, ys};
    }

    @Test
    void areasComeDensestFirstAndDoNotOverlap() {
        float[][] c = clusters();
        List<Warship.Held> picked = WarshipDirector.select(c[0], c[1], 10, 0f, 0f, 200f, 8f, true,
                List.of(), List.of(), 3);
        assertEquals(3, picked.size());
        assertEquals(5, picked.get(0).weight);
        assertEquals(3, picked.get(1).weight);
        assertEquals(2, picked.get(2).weight);
        for (int i = 0; i < picked.size(); i++) {
            for (int j = i + 1; j < picked.size(); j++) {
                WarshipOrder.FireOnArea a = picked.get(i).area;
                WarshipOrder.FireOnArea b = picked.get(j).area;
                assertTrue(Math.hypot(a.x() - b.x(), a.y() - b.y()) >= 8f, "centres at least a radius apart");
            }
        }
    }

    @Test
    void oneBigClusterIsNotSplitIntoOverlappingAreas() {
        // A line of hostiles two cells apart: neighbouring candidates would sit inside one another.
        float[] xs = new float[12];
        float[] ys = new float[12];
        for (int i = 0; i < 12; i++) {
            xs[i] = 50 + i * 2;
            ys[i] = 10;
        }
        List<Warship.Held> picked = WarshipDirector.select(xs, ys, 12, 0f, 0f, 200f, 8f, true,
                List.of(), List.of(), 3);
        for (int i = 0; i < picked.size(); i++) {
            for (int j = i + 1; j < picked.size(); j++) {
                WarshipOrder.FireOnArea a = picked.get(i).area;
                WarshipOrder.FireOnArea b = picked.get(j).area;
                assertTrue(Math.hypot(a.x() - b.x(), a.y() - b.y()) >= 8f);
            }
        }
    }

    @Test
    void groundHeldOrCoveredByAnotherShipIsNotChosenAgain() {
        float[][] c = clusters();
        WarshipOrder.FireOnArea held = new WarshipOrder.FireOnArea(50f, 11f, 8f, true);
        WarshipOrder.FireOnArea theirs = new WarshipOrder.FireOnArea(80f, 11f, 8f, true);
        List<Warship.Held> picked = WarshipDirector.select(c[0], c[1], 10, 0f, 0f, 200f, 8f, true,
                List.of(held), List.of(theirs), 3);
        assertEquals(1, picked.size(), "only the third cluster is free");
        assertEquals(110f, picked.get(0).area.x(), 2f);
    }

    @Test
    void groundBeyondEveryGunIsNeverChosen() {
        float[][] c = clusters();
        List<Warship.Held> picked = WarshipDirector.select(c[0], c[1], 10, 0f, 0f, 45f, 8f, true,
                List.of(), List.of(), 3);
        assertEquals(1, picked.size());
        assertEquals(5, picked.get(0).weight);
    }

    @Test
    void aManualOrderReplacesTheDirectorsAreasAndIsTheOnlyOne() throws Exception {
        Warship s = ship();
        WarshipOrder.FireOnArea a = new WarshipOrder.FireOnArea(50f, 10f, 8f, true);
        WarshipOrder.FireOnArea b = new WarshipOrder.FireOnArea(80f, 10f, 8f, true);
        s.direct(8f, true, 3);
        s.setDirectedAreas(List.of(new Warship.Held(a, 5), new Warship.Held(b, 3)));
        assertEquals(2, s.areas().size());
        assertSame(a, s.order());
        assertFalse(s.manualOrder());

        WarshipOrder.FireOnArea mine = new WarshipOrder.FireOnArea(20f, 20f, 10f, false);
        s.order(mine);
        assertEquals(List.of(mine), s.areas());
        assertSame(mine, s.order());
        assertTrue(s.manualOrder());

        s.order(WarshipOrder.HoldFire.INSTANCE);
        assertTrue(s.areas().isEmpty());
        assertEquals(WarshipOrder.HoldFire.INSTANCE, s.order());
    }

    @Test
    void theHeldAreasAreKeptDensestFirst() throws Exception {
        Warship s = ship();
        WarshipOrder.FireOnArea a = new WarshipOrder.FireOnArea(50f, 10f, 8f, true);
        WarshipOrder.FireOnArea b = new WarshipOrder.FireOnArea(80f, 10f, 8f, true);
        s.setDirectedAreas(List.of(new Warship.Held(a, 5), new Warship.Held(b, 3)));
        s.held().get(0).weight = 1;
        s.resortByWeight();
        assertSame(b, s.areas().get(0));
        assertSame(a, s.areas().get(1));
    }

    @Test
    void theHeadingIsChosenOverTheWholeUnion() throws Exception {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                if (path.equals("data/hulls/test.ship")) return new JSONObject("""
                        {"hullId":"test","spriteName":"x.png","weaponSlots":[
                         {"id":"WS 001","type":"BUILT_IN","size":"LARGE","mount":"HARDPOINT","locations":[0,0],"angle":0,"arc":5}]}""");
                if (path.equals("data/variants/test/test_Standard.variant")) return new JSONObject(VARIANT);
                throw new FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                if (path.equals("data/hulls/ship_data.csv")) return List.of(Map.of("id", "test"));
                return List.of(Map.of("id", "gun_a", "damage/shot", "100", "type", "KINETIC", "range", "2000",
                        "burst size", "1", "chargedown", "1", "proj speed", "1000", "hints", ""));
            }
        });
        VanillaWeapons.clearCache();
        Warship s = Warship.fit("test", "test_Standard", Faction.MARINE, 0f, 0f, 0f);
        float d = WarshipScale.cells(2000f) * 0.5f;
        float east = WarshipFacing.bestHeading(s, new float[]{d}, new float[]{0f}, new float[]{1f});
        float north = WarshipFacing.bestHeading(s, new float[]{0f}, new float[]{d}, new float[]{1f});
        assertTrue(Math.abs(WarshipFacing.wrap(east - north)) > 45f, "the two grounds want different headings");
        // A fixed gun serves one ground at a time, so the heavier weight decides.
        float heavyEast = WarshipFacing.bestHeading(s, new float[]{d, 0f}, new float[]{0f, d}, new float[]{5f, 1f});
        float heavyNorth = WarshipFacing.bestHeading(s, new float[]{d, 0f}, new float[]{0f, d}, new float[]{1f, 5f});
        assertEquals(east, heavyEast, 0.01f);
        assertEquals(north, heavyNorth, 0.01f);
    }
}
