package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Helpers for building placeholder battle scenarios. Hard-coded test setup
 * used to verify the simulation runs end-to-end before mission-driven map
 * generation lands. Once the BATTLE screen wires in, the renderer can call
 * {@link #createPlaceholder} to get a sim it can tick + draw.
 */
public final class BattleSetup {

    public static final int GRID_W = 24;
    public static final int GRID_H = 16;

    private BattleSetup() {}

    /**
     * Builds a placeholder simulation: open arena with a small wall strip down
     * the middle, marines lined up on the left edge, defenders on the right.
     * Counts are even (6 vs 6) so the win condition isn't trivial.
     */
    public static BattleSimulation createPlaceholder() {
        NavigationGrid grid = new NavigationGrid(GRID_W, GRID_H);
        for (int y = 0; y < GRID_H; y++) {
            for (int x = 0; x < GRID_W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        // Short central wall — forces pathing around an obstacle so we can see
        // A* actually working in the visual playtest.
        for (int y = 5; y < 11; y++) {
            grid.setWalkable(GRID_W / 2, y, false);
        }

        BattleSimulation sim = new BattleSimulation(grid);

        int rowSpacing = 2;
        int startY = (GRID_H - 5 * rowSpacing) / 2;
        for (int i = 0; i < 6; i++) {
            sim.addUnit(new Unit("m" + i, Faction.MARINE,   2,            startY + i * rowSpacing));
            sim.addUnit(new Unit("d" + i, Faction.DEFENDER, GRID_W - 3,   startY + i * rowSpacing));
        }
        return sim;
    }
}
