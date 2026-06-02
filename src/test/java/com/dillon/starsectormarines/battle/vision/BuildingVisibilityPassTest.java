package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the fog-bitmap reveal rule: a building roof reveals (targetAlpha 0) iff
 * any of its interior cells is currently revealed, and only its <em>own</em>
 * interior cells count. Guards the fix for the under-reveal divergence (closest-
 * unit + 5-sample raycast → per-cell fog coverage).
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

    private static int idx(int x, int y) {
        return y * GW + x;
    }

    @Test
    public void noInteriorCellRevealed_roofStaysOpaque() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        BuildingVisibilityPass.update(buildings, revealed, GW, GH);
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "unseen interior → opaque roof");
    }

    @Test
    public void anyInteriorCellRevealed_roofReveals() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        revealed[idx(6, 5)] = true; // one of the two interior cells
        BuildingVisibilityPass.update(buildings, revealed, GW, GH);
        assertEquals(0f, buildings.get(1).targetAlpha, 0f, "a single visible interior cell reveals the roof");
    }

    @Test
    public void revealedCellOutsideTheBuilding_doesNotReveal() {
        Buildings buildings = oneBuilding();
        boolean[] revealed = new boolean[GW * GH];
        revealed[idx(0, 0)] = true; // not an interior cell of the building
        BuildingVisibilityPass.update(buildings, revealed, GW, GH);
        assertEquals(1f, buildings.get(1).targetAlpha, 0f, "only the building's own interior cells count");
    }
}
