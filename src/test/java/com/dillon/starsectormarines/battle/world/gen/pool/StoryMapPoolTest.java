package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoryMapPoolTest {

    private static Precinct place(String name, int x, int y) {
        return new Precinct(name, x, y, GrownTrunkPlan.Profile.hamlet(), null, Precinct.Boundary.OPEN);
    }

    @Test
    void aPinMovesOnlyItsPlace() {
        PrecinctPlan plan = new PrecinctPlan(List.of(place("base", 10, 10), place("redoubt", 50, 20)));
        MapPatch patch = new MapPatch(List.of(new MapPatch.Pin("base", 12, 30)), List.of());
        PrecinctPlan pinned = patch.pinned(plan);
        assertEquals(12, pinned.precincts().get(0).seedX());
        assertEquals(30, pinned.precincts().get(0).seedY());
        assertSame(plan.precincts().get(1), pinned.precincts().get(1));
        assertSame(plan, MapPatch.NONE.pinned(plan));
    }

    @Test
    void aPinNamingNoPlaceIsAnAuthoringError() {
        PrecinctPlan plan = new PrecinctPlan(List.of(place("base", 10, 10)));
        MapPatch patch = new MapPatch(List.of(new MapPatch.Pin("bsae", 1, 1)), List.of());
        assertThrows(IllegalArgumentException.class, () -> patch.pinned(plan));
    }

    @Test
    void aColonyPatchReadsItsLiftBoresAndHoldoutsAndNamesOneMother() throws Exception {
        MapPatch patch = MapPatch.read(new JSONObject("{\"lift\":[164,24],\"bores\":[{\"x\":30,\"y\":40},"
                + "{\"x\":50,\"y\":60,\"mother\":true}],\"holdouts\":[[69,83]]}"));
        assertEquals(new MapPatch.Cell(164, 24), patch.lift());
        assertEquals(List.of(new MapPatch.Bore(30, 40, false), new MapPatch.Bore(50, 60, true)), patch.bores());
        assertEquals(List.of(new MapPatch.Cell(69, 83)), patch.holdouts());
        assertNull(MapPatch.NONE.lift());
        assertThrows(IllegalArgumentException.class, () -> MapPatch.read(new JSONObject(
                "{\"bores\":[{\"x\":30,\"y\":40},{\"x\":50,\"y\":60}]}")));
    }

    private static String pool(String map, String verdant) {
        return "{\"version\":2,\"map\":\"" + map + "\",\"biomes\":{\"VERDANT\":" + verdant
                + ",\"ROCK\":[{\"seed\":1}],\"ARID\":[{\"seed\":1}],\"FROZEN\":[{\"seed\":1}]}}";
    }

    @Test
    void aRollPicksTheSameEntryOfItsBiomeEveryTimeAndNegativeRollsWrap() throws Exception {
        StoryMapPool pool = StoryMapPool.read(StoryMap.HEART_BURN,
                new JSONObject(pool("heart-burn", "[{\"seed\":4},{\"seed\":9},{\"seed\":2}]")));
        assertEquals(9, pool.pick(SurfacePalette.VERDANT, 1).seed());
        assertEquals(9, pool.pick(SurfacePalette.VERDANT, 4).seed());
        assertEquals(2, pool.pick(SurfacePalette.VERDANT, -1).seed());
        assertEquals(1, pool.pick(SurfacePalette.FROZEN, 7).seed());
        assertEquals(MapPatch.NONE, pool.entry(SurfacePalette.VERDANT, 4).patch());
        assertNull(pool.entry(SurfacePalette.ROCK, 4));
    }

    @Test
    void aPoolRefusesADuplicateSeedAMissingBiomeOrAnotherMapsFile() {
        assertThrows(IllegalArgumentException.class, () -> StoryMapPool.read(StoryMap.HEART_BURN,
                new JSONObject(pool("heart-burn", "[{\"seed\":4},{\"seed\":4}]"))));
        assertThrows(IllegalArgumentException.class, () -> StoryMapPool.read(StoryMap.HEART_BURN,
                new JSONObject(pool("heart-burn", "[]"))));
        assertThrows(Exception.class, () -> StoryMapPool.read(StoryMap.HEART_BURN,
                new JSONObject(pool("heart-front", "[{\"seed\":4}]"))));
    }
}
