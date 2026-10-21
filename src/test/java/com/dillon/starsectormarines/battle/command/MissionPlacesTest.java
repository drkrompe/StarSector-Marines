package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.MissionPlaces.Building;
import com.dillon.starsectormarines.battle.command.MissionPlaces.Site;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Hostile;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The places a procedural mission's defender holds and can lose ({@link MissionPlaces}): which buildings matter beside
 * an objective, and how a place turns and comes whole on belief and own force alone. A barracks (x 40..55, y 20..30)
 * stands next to the objective at (60, 25); a depot far east does not.
 */
class MissionPlacesTest {

    private static final Building BARRACKS = new Building(47, 25, 40, 20, 55, 30, false);
    private static final Building DEPOT = new Building(150, 25, 145, 20, 155, 30, true);
    private static final Building HALL = new Building(64, 26, 58, 22, 70, 30, false);

    private static List<Hostile> holders(int n, int x, int y) {
        List<Hostile> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Hostile(x, y));
        return out;
    }

    private static Status status(List<Place> places, String key) {
        for (Place place : places) if (place.key().equals(key)) return place.status();
        throw new AssertionError("no place " + key);
    }

    @Test
    void anObjectiveTakesTheBuildingThatHoldsItAndOnlyNearbyKeyBuildingsCount() {
        List<Building> buildings = List.of(BARRACKS, DEPOT, HALL);
        Site target = MissionPlaces.objective("target", 60, 25, buildings);
        assertEquals(58, target.left(), "the hall holds the target cell, so its walls are the target's ground");
        assertEquals(MissionPlaces.OBJECTIVE_WORTH, target.worth());

        List<Site> sites = MissionPlaces.around(List.of(target), buildings);
        List<String> keys = sites.stream().map(Site::key).toList();
        assertEquals(List.of("target", MissionPlaces.building(BARRACKS, 0f).key()), keys,
                "the barracks beside it counts, the depot across the map does not, and the hall is the target itself");

        assertEquals(MissionPlaces.BUILDING_WORTH + MissionPlaces.MUST_HOLD_WORTH,
                MissionPlaces.building(DEPOT, MissionPlaces.BUILDING_WORTH).worth(), "a must-hold building is worth more");
    }

    @Test
    void aPlaceIsLostOnlyOnceHoldersAreBelievedOnItForAFewPulsesWithNobodyOfOursThere() {
        MissionPlaces places = new MissionPlaces();
        List<Site> sites = List.of(MissionPlaces.building(BARRACKS, 1f));
        String key = sites.get(0).key();
        List<int[]> none = List.of();

        assertEquals(Status.HELD, status(places.places(sites, holders(1, 47, 25), none), key),
                "a straggler does not hold a building");
        assertEquals(Status.HELD, status(places.places(sites, holders(4, 47, 25), none), key),
                "one pulse of holders is not yet a loss");
        assertEquals(Status.LOST, status(places.places(sites, holders(4, 47, 25), none), key));
        assertTrue(places.changes().get(0).contains("is lost"), "the turn is in the notes");

        MissionPlaces garrisoned = new MissionPlaces();
        List<int[]> ours = List.of(new int[] {50, 26});
        for (int i = 0; i < 4; i++) {
            assertEquals(Status.HELD, status(garrisoned.places(sites, holders(4, 47, 25), ours), key),
                    "a building with our squad still in it is contested, not lost");
        }
    }

    @Test
    void aLostPlaceIsBeingWonBackWithOursOnItAndWholeOnceNobodyHostileIsBelievedThere() {
        MissionPlaces places = new MissionPlaces();
        List<Site> sites = List.of(MissionPlaces.building(BARRACKS, 1f));
        String key = sites.get(0).key();
        places.places(sites, holders(4, 47, 25), List.of());
        places.places(sites, holders(4, 47, 25), List.of());
        List<int[]> ours = List.of(new int[] {50, 26});

        Place contested = places.places(sites, holders(2, 47, 25), ours).get(0);
        assertEquals(Status.LOSING, contested.status());
        assertTrue(contested.capturing(), "our squads on it are retaking it");

        for (int i = 0; i < MissionPlaces.CLEAR_PULSES - 1; i++) {
            assertTrue(places.places(sites, List.of(), ours).get(0).turned(), "quiet for a moment is not yet whole");
        }
        assertEquals(Status.HELD, status(places.places(sites, List.of(), ours), key));
        assertFalse(places.lost(key));
    }

    @Test
    void aLaunchedGroupHoldsThePlaceWithinItsWalls() {
        Place place = new MissionPlaces().places(List.of(MissionPlaces.building(BARRACKS, 1f)), List.of(), List.of())
                .get(0);
        var orders = place.assault().orders(List.of(new PlaceCounterattack.Body(7, 10, 10, 6, 6, false, false, false,
                null)), null);
        ObjectiveAssignment order = orders.get(7);
        assertEquals(AssignmentKind.DEFEND_AREA, order.kind());
        assertEquals(47, order.targetCellX());
        assertTrue(order.targetRadiusCells() <= MissionPlaces.HOLD_RADIUS_CELLS);
    }
}
