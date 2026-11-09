package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CorridorStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictWalkerFrameTest {

    /**
     * The walker guards interiors (`derelict-security-network.md`, Interiors): it must stand in the narrowest passage
     * an interior is carved with, a corridor of {@link CorridorStage#CORRIDOR_WIDTH} cells between walls.
     */
    @Test
    void theWalkerStandsInAnInteriorCorridor() {
        int width = CorridorStage.CORRIDOR_WIDTH;
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 5; y < 5 + width; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        assertTrue(ManualTerrainMotion.canStand(grid, 6f, 5f + width / 2f, DerelictWalkerFrame.CHASSIS.radius));
    }
}
