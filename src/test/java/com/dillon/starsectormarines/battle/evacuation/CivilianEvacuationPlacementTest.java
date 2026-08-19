package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianEvacuationPlacementTest {

    @Test
    void selectsResidentialShelterAndEightUniqueReachableCells() {
        NavigationGrid grid = openGrid(40, 32);
        PointOfInterest lab = poi(PointOfInterest.Kind.LABORATORY, 5, 5);
        PointOfInterest home = poi(PointOfInterest.Kind.RESIDENTIAL, 10, 8);

        CivilianEvacuationPlacement placement =
                CivilianEvacuationPlacement.find(
                        grid, new CellTopology(40, 32),
                        List.of(lab, home), 77L);

        assertNotNull(placement);
        assertEquals(10, placement.shelterX);
        assertEquals(8, placement.shelterY);
        assertEquals(home.anchorCellX, placement.shelterApproachX);
        assertEquals(home.anchorCellY, placement.shelterApproachY);
        assertEquals(8, placement.spawnCount());
        int edgeDistance = Math.min(Math.min(placement.liftX,
                        grid.getWidth() - 1 - placement.liftX),
                Math.min(placement.liftY,
                        grid.getHeight() - 1 - placement.liftY));
        assertTrue(edgeDistance >= CivilianEvacuationPlacement.PICKUP_EDGE_INSET);
        assertEquals(CivilianEvacuationPlacement.PICKUP_FORMATION_POINTS,
                placement.formationPointCount());
        for (int point = 0; point < placement.formationPointCount(); point++) {
            assertTrue(grid.isWalkable(placement.formationX(point),
                    placement.formationY(point)));
            assertTrue(Math.max(Math.abs(placement.formationX(point) - placement.liftX),
                    Math.abs(placement.formationY(point) - placement.liftY))
                    >= placement.formationRadius() - 2);
        }
        assertEquals(CivilianEvacuationPlacement.PICKUP_FORMATION_RADIUS,
                placement.formationRadius());
        assertTrue(formationSpan(placement, true) >= 25);
        assertTrue(formationSpan(placement, false) >= 25);
        for (int i = 0; i < placement.spawnCount(); i++) {
            assertTrue(grid.isWalkable(
                    placement.spawnX(i), placement.spawnY(i)));
            assertTrue(placement.spawnX(i) > home.left
                    && placement.spawnX(i) < home.right);
            assertTrue(placement.spawnY(i) > home.top
                    && placement.spawnY(i) < home.bottom);
            for (int j = i + 1; j < placement.spawnCount(); j++) {
                assertTrue(placement.spawnX(i) != placement.spawnX(j)
                        || placement.spawnY(i) != placement.spawnY(j));
            }
        }
    }

    private static int formationSpan(CivilianEvacuationPlacement placement,
                                     boolean xAxis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int point = 0; point < placement.formationPointCount(); point++) {
            int value = xAxis ? placement.formationX(point)
                    : placement.formationY(point);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min + 1;
    }

    @Test
    void selectionIsIndependentOfPoiIterationOrder() {
        NavigationGrid grid = openGrid(24, 18);
        PointOfInterest first = poi(
                PointOfInterest.Kind.RESIDENTIAL, 6, 7);
        PointOfInterest second = poi(
                PointOfInterest.Kind.RESIDENTIAL, 16, 10);

        CivilianEvacuationPlacement a =
                CivilianEvacuationPlacement.find(
                        grid, new CellTopology(24, 18),
                        List.of(first, second), 941L);
        CivilianEvacuationPlacement b =
                CivilianEvacuationPlacement.find(
                        grid, new CellTopology(24, 18),
                        List.of(second, first), 941L);

        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.shelterX, b.shelterX);
        assertEquals(a.shelterY, b.shelterY);
        assertEquals(a.liftX, b.liftX);
        assertEquals(a.liftY, b.liftY);
        for (int i = 0; i < a.spawnCount(); i++) {
            assertEquals(a.spawnX(i), b.spawnX(i));
            assertEquals(a.spawnY(i), b.spawnY(i));
        }
    }

    @Test
    void invalidOrIncompleteMapsProduceNoPartialPlacement() {
        NavigationGrid open = openGrid(12, 12);
        assertNull(CivilianEvacuationPlacement.find(
                open, new CellTopology(12, 12),
                List.of(poi(PointOfInterest.Kind.DEPOT, 6, 6)), 1L));

        NavigationGrid tinyPocket = new NavigationGrid(12, 12);
        tinyPocket.setWalkableFloor(6, 6);
        tinyPocket.setWalkableFloor(0, 0);
        assertNull(CivilianEvacuationPlacement.find(tinyPocket,
                new CellTopology(12, 12),
                List.of(poi(PointOfInterest.Kind.RESIDENTIAL, 6, 6)), 1L));
    }

    @Test
    void pickupCenterRequiresAClearOutdoorFiveByFiveFootprint() {
        NavigationGrid grid = openGrid(40, 32);
        CellTopology topology = new CellTopology(40, 32);
        PointOfInterest home = poi(PointOfInterest.Kind.RESIDENTIAL, 10, 8);
        CivilianEvacuationPlacement first = CivilianEvacuationPlacement.find(
                grid, topology, List.of(home), 412L);
        assertNotNull(first);
        for (int y = first.liftY - CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
             y <= first.liftY + CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
             y++) {
            for (int x = first.liftX - CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
                 x <= first.liftX + CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
                 x++) {
                topology.setBuildingId(x, y, 7);
            }
        }

        CivilianEvacuationPlacement replacement =
                CivilianEvacuationPlacement.find(grid, topology,
                        List.of(home), 412L);

        assertNotNull(replacement);
        assertTrue(replacement.liftX != first.liftX
                || replacement.liftY != first.liftY);
        for (int y = replacement.liftY
                     - CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
             y <= replacement.liftY
                     + CivilianEvacuationPlacement.LIFT_ZONE_RADIUS; y++) {
            for (int x = replacement.liftX
                         - CivilianEvacuationPlacement.LIFT_ZONE_RADIUS;
                 x <= replacement.liftX
                         + CivilianEvacuationPlacement.LIFT_ZONE_RADIUS; x++) {
                assertTrue(grid.isWalkable(x, y));
                assertEquals(0, topology.getBuildingId(x, y),
                        "every cell in the 5x5 pickup trigger must be outdoors");
            }
        }
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static PointOfInterest poi(PointOfInterest.Kind kind,
                                       int x, int y) {
        return new PointOfInterest(kind, x - 2, y - 2, x + 2, y + 2,
                x, y, x, y);
    }
}
