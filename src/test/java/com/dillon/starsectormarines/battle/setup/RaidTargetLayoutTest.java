package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidTargetLayoutTest {

    private static final int WIDTH = 24;
    private static final int HEIGHT = 12;
    private static final int MARINE_SPAWN_X = 1;
    private static final int MARINE_SPAWN_Y = 1;
    private static final int DEFENDER_SPAWN_X = 22;
    private static final int DEFENDER_SPAWN_Y = 10;

    /**
     * The egress pad sits beside the defender spawn on purpose: it makes the two
     * points of interest that must be refused the two furthest from it, so the
     * max-by ordering would take either of them if the eligibility rule let it.
     */
    private static final LandingPad EGRESS =
            LandingPad.fallback(DEFENDER_SPAWN_X, DEFENDER_SPAWN_Y);

    private static final PointOfInterest DEFENDER_SIDE_COMMS =
            poi(PointOfInterest.Kind.COMMS, 20, 9);
    private static final PointOfInterest MARINE_SIDE_DEPOT =
            poi(PointOfInterest.Kind.DEPOT, 3, 2);
    private static final PointOfInterest DEFENDER_SIDE_HOUSING =
            poi(PointOfInterest.Kind.RESIDENTIAL, 21, 2);

    @Test
    void targetIsTakenFromTheDefenderSideRatherThanTheHighXHalf() {
        MapResult map = map(List.of(
                MARINE_SIDE_DEPOT, DEFENDER_SIDE_HOUSING, DEFENDER_SIDE_COMMS));

        RaidTargetLayout layout = RaidTargetLayout.select(map, List.of(EGRESS));

        assertEquals(DEFENDER_SIDE_COMMS.kind, layout.kind(),
                "the raid target must be the non-residential building on the"
                        + " defender's side of the map");
        assertEquals(DEFENDER_SIDE_COMMS.interiorAnchorX, layout.targetCellX(),
                "the raid target must stand on the chosen building's interior"
                        + " anchor");
        assertEquals(DEFENDER_SIDE_COMMS.interiorAnchorY, layout.targetCellY(),
                "the raid target must stand on the chosen building's interior"
                        + " anchor");
        assertEquals(EGRESS.centerX, layout.egressCellX(),
                "the return corridor must lead back to the first landing pad");
        assertEquals(EGRESS.centerY, layout.egressCellY(),
                "the return corridor must lead back to the first landing pad");
        assertEquals("RAID-01", layout.targetId(),
                "the raid target must keep its stable identity");
        assertEquals("comms", layout.targetName(),
                "the raid target must be labelled from its own kind");
    }

    @Test
    void aBuildingNearerTheMarineSpawnIsNeverTheTargetHoweverFarFromEgress() {
        MapResult map = map(List.of(MARINE_SIDE_DEPOT, DEFENDER_SIDE_COMMS));

        assertTrue(distanceSquaredFromEgress(MARINE_SIDE_DEPOT)
                        > distanceSquaredFromEgress(DEFENDER_SIDE_COMMS),
                "this test only means something while the refused building is"
                        + " the one the distance ordering would otherwise take");

        RaidTargetLayout layout = RaidTargetLayout.select(map, List.of(EGRESS));

        assertEquals(DEFENDER_SIDE_COMMS.interiorAnchorX, layout.targetCellX(),
                "a building nearer the marine spawn than the defender spawn is"
                        + " on the attacker's own side and must not be raided,"
                        + " however far it lies from the egress pad");
    }

    @Test
    void mapWhoseOnlyBuildingsAreOnTheMarineSideFailsClosed() {
        MapResult map = map(List.of(MARINE_SIDE_DEPOT));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RaidTargetLayout.select(map, List.of(EGRESS)));

        assertTrue(failure.getMessage().contains("defender-side"),
                "a map with nothing worth raiding on the defender's side must"
                        + " say so rather than publish an attacker-side target");
    }

    @Test
    void residentialBuildingsAreRefusedEvenOnTheDefenderSide() {
        MapResult map = map(List.of(DEFENDER_SIDE_HOUSING, DEFENDER_SIDE_COMMS));

        assertTrue(distanceSquaredFromEgress(DEFENDER_SIDE_HOUSING)
                        > distanceSquaredFromEgress(DEFENDER_SIDE_COMMS),
                "this test only means something while the refused building is"
                        + " the one the distance ordering would otherwise take");

        RaidTargetLayout layout = RaidTargetLayout.select(map, List.of(EGRESS));

        assertEquals(DEFENDER_SIDE_COMMS.kind, layout.kind(),
                "housing is not a high-value target and must not be raided"
                        + " merely for standing on the defender's side");
    }

    private static MapResult map(List<PointOfInterest> pointsOfInterest) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        return new MapResult(grid, new CellTopology(WIDTH, HEIGHT),
                MARINE_SPAWN_X, MARINE_SPAWN_Y,
                DEFENDER_SPAWN_X, DEFENDER_SPAWN_Y,
                pointsOfInterest, List.<Doodad>of());
    }

    private static int distanceSquaredFromEgress(PointOfInterest poi) {
        int dx = poi.interiorAnchorX - EGRESS.centerX;
        int dy = poi.interiorAnchorY - EGRESS.centerY;
        return dx * dx + dy * dy;
    }

    private static PointOfInterest poi(PointOfInterest.Kind kind, int x, int y) {
        return new PointOfInterest(kind, x - 1, y - 1, x + 1, y + 1, x, y, x, y);
    }
}
