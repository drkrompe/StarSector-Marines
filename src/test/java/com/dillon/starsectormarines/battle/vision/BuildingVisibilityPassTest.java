package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the open rule: a building opens (targetAlpha 0) only while one of the player's units stands on its own floor;
 * seeing in does not open it, and a peek opens only the building under the pointer. The fog rule still reads what the
 * player sees.
 */
public class BuildingVisibilityPassTest {

    private static final int GW = 10, GH = 10;

    /** Building with interior cells (5,5) and (6,5). */
    private static Buildings oneBuilding() {
        Buildings buildings = new Buildings();
        buildings.add(new Building(1, BuildingKind.values()[0],
                5, 6, 5, 5,
                new int[]{5, 6}, new int[]{5, 5},
                1f, 1f, 1f));
        return buildings;
    }

    private static final OccupantCells NOBODY = new OccupantCells(1);

    private static OccupantCells standing(int... cells) {
        OccupantCells occupants = new OccupantCells(1);
        for (int cell : cells) occupants.add(cell);
        return occupants;
    }

    private static int idx(int x, int y) {
        return y * GW + x;
    }

    @Test
    public void noInteriorCellRevealed_roofStaysOpaque() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, NOBODY);
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "unseen interior → opaque roof");
    }

    @Test
    public void seeingInsideLiftsTheFogButDoesNotOpenIt() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        revealed[idx(6, 5)] = true; // seen through a door or window
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, NOBODY);
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "seeing in keeps the building standing");
        assertEquals(0f, buildings.get(1).targetFog, 0f, "but out of the fog");
    }

    @Test
    public void aUnitOnItsFloorOpensItAndOneOutsideDoesNot() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, standing(idx(7, 5), idx(0, 0)));
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "a unit at its wall or across the map does not open it");
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, standing(idx(0, 0), idx(6, 5)));
        assertEquals(0f, buildings.get(1).targetAlpha, 0f, "a unit on its floor opens it");
        OccupantCells left = standing(idx(6, 5));
        left.clear();
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, left);
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "only the filled occupants count");
    }

    @Test
    public void moreOccupantsThanTheFirstCapacityStillCount() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        OccupantCells occupants = new OccupantCells(64);
        for (int i = 0; i < 64; i++) occupants.add(idx(0, 0));
        occupants.add(idx(6, 5)); // the 65th unit, the only one on the floor
        assertEquals(65, occupants.size());
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, occupants);
        assertEquals(0f, buildings.get(1).targetAlpha, 0f, "the 65th occupant opens the building");
    }

    @Test
    public void thePointerPeeksIntoOneBuildingAndLeavesTheRest() {
        Buildings buildings = oneBuilding();
        buildings.add(new Building(2, BuildingKind.values()[0], 1, 1, 1, 1, new int[]{1}, new int[]{1}, 1f, 1f, 1f));
        BuildingVisibilityPass.advanceAlpha(buildings, 10f, 1);
        assertEquals(0f, buildings.get(1).currentAlpha, 1e-6f, "the building under the pointer opens");
        assertEquals(1f, buildings.get(2).currentAlpha, 1e-6f, "its neighbour stays closed");
        BuildingVisibilityPass.advanceAlpha(buildings, 10f, 0);
        assertEquals(1f, buildings.get(1).currentAlpha, 1e-6f, "and closes again when the pointer leaves");
    }

    @Test
    public void revealedCellOutsideTheBuilding_doesNotReveal() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        revealed[idx(0, 0)] = true; // not an interior cell of the building
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, NOBODY);
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "only the building's own interior cells count");
    }

    @Test
    public void wallCellRevealed_roofLeavesTheFogButStaysClosed() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        revealed[idx(7, 5)] = true; // the wall beside the floor, seen from outside
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, NOBODY);
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "a seen wall does not open the roof");
        assertEquals(0f, buildings.get(1).targetFog, 0f, "a seen wall lifts the roof out of fog");
    }

    @Test
    public void nothingUnderTheRoofRevealed_roofLiesInFog() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        revealed[idx(8, 5)] = true; // two cells off the floor: outside the roof
        BuildingVisibilityPass.update(buildings, revealed, GW, GH, NOBODY);
        assertEquals(1f, buildings.get(1).targetFog, 0f, "a roof nobody can see lies in fog");
    }
}
