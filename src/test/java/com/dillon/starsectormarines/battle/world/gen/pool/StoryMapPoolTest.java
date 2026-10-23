package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void aRollPicksTheSameEntryEveryTimeAndNegativeRollsWrap() throws Exception {
        StoryMapPool pool = StoryMapPool.read(StoryMap.HEART_BURN, new JSONObject(
                "{\"version\":1,\"map\":\"heart-burn\",\"seeds\":[{\"seed\":4},{\"seed\":9},{\"seed\":2}]}"));
        assertEquals(9, pool.pick(1).seed());
        assertEquals(9, pool.pick(4).seed());
        assertEquals(2, pool.pick(-1).seed());
        assertEquals(MapPatch.NONE, pool.entry(4).patch());
    }

    @Test
    void aPoolRefusesADuplicateSeedOrAnotherMapsFile() {
        assertThrows(IllegalArgumentException.class, () -> StoryMapPool.read(StoryMap.HEART_BURN, new JSONObject(
                "{\"version\":1,\"map\":\"heart-burn\",\"seeds\":[{\"seed\":4},{\"seed\":4}]}")));
        assertThrows(Exception.class, () -> StoryMapPool.read(StoryMap.HEART_BURN, new JSONObject(
                "{\"version\":1,\"map\":\"heart-front\",\"seeds\":[{\"seed\":4}]}")));
    }
}
