package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
        NavigationGrid grid = openGrid(20, 16);
        PointOfInterest lab = poi(PointOfInterest.Kind.LABORATORY, 5, 5);
        PointOfInterest home = poi(PointOfInterest.Kind.RESIDENTIAL, 10, 8);

        CivilianEvacuationPlacement placement =
                CivilianEvacuationPlacement.find(
                        grid, List.of(lab, home), 77L);

        assertNotNull(placement);
        assertEquals(10, placement.shelterX);
        assertEquals(8, placement.shelterY);
        assertEquals(8, placement.spawnCount());
        assertTrue(placement.liftX < 2 || placement.liftY < 2
                || placement.liftX >= 18 || placement.liftY >= 14);
        for (int i = 0; i < placement.spawnCount(); i++) {
            assertTrue(grid.isWalkable(
                    placement.spawnX(i), placement.spawnY(i)));
            for (int j = i + 1; j < placement.spawnCount(); j++) {
                assertTrue(placement.spawnX(i) != placement.spawnX(j)
                        || placement.spawnY(i) != placement.spawnY(j));
            }
        }
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
                        grid, List.of(first, second), 941L);
        CivilianEvacuationPlacement b =
                CivilianEvacuationPlacement.find(
                        grid, List.of(second, first), 941L);

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
                open, List.of(poi(PointOfInterest.Kind.DEPOT, 6, 6)), 1L));

        NavigationGrid tinyPocket = new NavigationGrid(12, 12);
        tinyPocket.setWalkableFloor(6, 6);
        tinyPocket.setWalkableFloor(0, 0);
        assertNull(CivilianEvacuationPlacement.find(tinyPocket,
                List.of(poi(PointOfInterest.Kind.RESIDENTIAL, 6, 6)), 1L));
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
