package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CordonCityPiecesTest {

    /** Redoubts west and east, their front edges at y 100, the front up the map. */
    private static final int[] WEST = {50, 80, 80, 100}, EAST = {180, 80, 210, 100};

    private static Building building(int id, BuildingKind kind, int x0, int y0, int x1, int y1) {
        List<int[]> cells = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) cells.add(new int[]{x, y});
        int[] xs = new int[cells.size()], ys = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            xs[i] = cells.get(i)[0];
            ys[i] = cells.get(i)[1];
        }
        return new Building(id, kind, x0, x1, y0, y1, xs, ys, 1f, 1f, 1f);
    }

    @Test
    void theTenementIsAResidentialHouseInTheNearFrontNearestTheMiddle() {
        Building middle = building(1, BuildingKind.RESIDENTIAL, 125, 125, 135, 135);
        Building aside = building(2, BuildingKind.RESIDENTIAL, 100, 125, 110, 135);
        Building shop = building(3, BuildingKind.COMMERCIAL, 128, 118, 138, 128);
        Building behind = building(4, BuildingKind.RESIDENTIAL, 125, 70, 135, 80);
        Building tiny = building(5, BuildingKind.RESIDENTIAL, 130, 130, 133, 133);
        assertSame(middle, CordonCityPieces.tenement(List.of(aside, shop, behind, tiny, middle), WEST, EAST, 1,
                (x, y) -> true));
    }

    @Test
    void aHouseWithNoLaneBackToTheLineIsNotTheTenement() {
        Building walledIn = building(1, BuildingKind.RESIDENTIAL, 125, 125, 135, 135);
        // Open ground only inside the house and above it: nothing reaches the line's row.
        assertNull(CordonCityPieces.tenement(List.of(walledIn), WEST, EAST, 1, (x, y) -> y >= 125));
    }

    @Test
    void theObservationPostTakesTheBiggestBuildingForwardOfItsRedoubt() {
        Building small = building(1, BuildingKind.RESIDENTIAL, 60, 115, 66, 121);
        Building big = building(2, BuildingKind.COMMERCIAL, 55, 112, 75, 128);
        Building far = building(3, BuildingKind.INDUSTRIAL, 150, 110, 190, 140);
        List<Building> got = CordonCityPieces.lookouts(List.of(small, big, far), WEST, 1, Set.of());
        assertEquals(List.of(big, small), got);
        assertEquals(List.of(small), CordonCityPieces.lookouts(List.of(small, big), WEST, 1, Set.of(2)));
    }

    @Test
    void thePostIsSandbaggedInsideTheFrontWallNearestTheMiddle() {
        Building b = building(1, BuildingKind.COMMERCIAL, 10, 10, 20, 15);
        List<int[]> bags = CordonCityPieces.windows(b, 1, (x, y) -> x != 15);
        assertEquals(CordonCityPieces.OP_BAGS, bags.size());
        for (int[] c : bags) {
            assertEquals(15, c[1]);
            assertEquals(true, Math.abs(c[0] - 15) <= 3 && c[0] != 15);
        }
        for (int[] c : CordonCityPieces.windows(b, -1, (x, y) -> true)) assertEquals(10, c[1]);
    }
}
