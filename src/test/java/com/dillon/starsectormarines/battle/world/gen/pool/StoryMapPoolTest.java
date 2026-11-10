package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSitePlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
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

    @Test
    void aHullPatchReadsItsHeadingAndStakesAndRidesThePlansLayout() throws Exception {
        MapPatch patch = MapPatch.read(new JSONObject("{\"hull\":{\"x\":80,\"y\":60,\"heading\":\"west\",\"trench\":40,"
                + "\"stakes\":[{\"x\":40,\"y\":60,\"stage\":\"planted\"}]}}"));
        HullSitePlan hull = patch.hull();
        assertEquals(Direction.W, hull.heading());
        assertEquals(List.of(new HullSitePlan.StakeAt(40, 60, HullSite.StakeStage.PLANTED)), hull.stakes());
        PrecinctPlan plan = new PrecinctPlan(List.of(place("base", 10, 10)));
        assertSame(hull, patch.pinned(plan).layout().crash());
        assertThrows(Exception.class, () -> MapPatch.read(new JSONObject(
                "{\"hull\":{\"x\":80,\"y\":60,\"heading\":\"up\"}}")));
    }

    private static String pool(String map, String home, String entry) {
        return "{\"version\":3,\"map\":\"" + map + "\",\"home\":\"" + home + "\",\"entry\":" + entry + "}";
    }

    @Test
    void everyWorldPlaysTheOneOwnedMapLaidOutOnItsHome() throws Exception {
        StoryMapPool pool = StoryMapPool.read(StoryMap.HEART_BURN,
                new JSONObject(pool("heart-burn", "ARID", "{\"seed\":2,\"note\":\"the Burn\"}")));
        for (SurfacePalette world : SurfacePalette.values()) {
            assertEquals(2, pool.pick(world, 7).seed(), world.name());
            assertEquals(2, pool.story(world).seed(), world.name());
            assertEquals(SurfacePalette.ARID, pool.layoutFor(world), world.name());
            assertEquals(List.of(pool.owned()), pool.entries(world));
        }
        assertEquals(MapPatch.NONE, pool.entry(SurfacePalette.TOXIC, 2).patch());
        assertNull(pool.entry(SurfacePalette.TOXIC, 3));
    }

    @Test
    void aPoolRefusesAnOldVersionAHomeLaidOutAsAnotherOrAnotherMapsFile() {
        assertThrows(Exception.class, () -> StoryMapPool.read(StoryMap.HEART_BURN, new JSONObject(
                pool("heart-burn", "ARID", "{\"seed\":2}").replace("\"version\":3", "\"version\":2"))));
        assertThrows(IllegalArgumentException.class, () -> StoryMapPool.read(StoryMap.HEART_BURN,
                new JSONObject(pool("heart-burn", "VOLCANIC", "{\"seed\":2}"))));
        assertThrows(Exception.class, () -> StoryMapPool.read(StoryMap.HEART_BURN,
                new JSONObject(pool("heart-front", "ARID", "{\"seed\":2}"))));
    }

    @Test
    void aPlacedStructureReachesThePlansLayoutByName() throws Exception {
        MapPatch patch = MapPatch.read(new JSONObject("{\"place\":[{\"name\":\"airbase-1\",\"kind\":\"AIRFIELD\","
                + "\"x\":89,\"y\":8,\"turns\":2}]}"));
        PrecinctPlan plan = patch.pinned(new PrecinctPlan(List.of(place("base", 10, 10))));
        Structure placed = plan.layout().placed("airbase-1");
        assertEquals(new Structure("airbase-1", StructureKind.AIRFIELD, 89, 8, 2, true), placed);
        assertNull(plan.layout().placed("airbase-2"));
    }
}
