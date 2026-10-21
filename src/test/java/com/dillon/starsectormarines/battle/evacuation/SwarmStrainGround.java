package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Open ground with one walled colony building on it, laid the way the
 * generators lay one: an impassable wall ring tagged {@code WALL} with a wall
 * HP, see-through window cells in it, doorway cells, and the interior tagged
 * with the building's id. Shared by the strain scenes.
 */
final class SwarmStrainGround {

    static final int BUILDING_ID = 1;

    final NavigationGrid grid;
    final CellTopology topology;
    final int minX;
    final int minY;
    final int maxX;
    final int maxY;

    /**
     * Interior cells {@code minX..maxX} x {@code minY..maxY}; the wall ring sits one cell
     * outside them at {@code wallHp}.
     */
    SwarmStrainGround(int width, int height, int minX, int minY, int maxX, int maxY,
                      int wallHp) {
        this.grid = new NavigationGrid(width, height);
        this.topology = new CellTopology(width, height);
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = minY - 1; y <= maxY + 1; y++) {
            for (int x = minX - 1; x <= maxX + 1; x++) {
                boolean ring = x < minX || x > maxX || y < minY || y > maxY;
                if (ring) {
                    grid.setWalkable(x, y, false);
                    grid.setWallHp(x, y, wallHp);
                    topology.setWall(x, y, true);
                } else {
                    topology.setBuildingId(x, y, BUILDING_ID);
                }
            }
        }
    }

    /** A window in the wall ring at {@code (x, y)}: still impassable, now see-through. */
    SwarmStrainGround window(int x, int y) {
        grid.setSeeThrough(x, y, true);
        topology.setWall(x, y, false);
        topology.setWindow(x, y, true);
        return this;
    }

    /** A doorway in the wall ring at {@code (x, y)}. */
    SwarmStrainGround door(int x, int y) {
        grid.setWalkableFloor(x, y);
        grid.setDoorway(x, y, true);
        grid.setWallHp(x, y, 0);
        topology.setWall(x, y, false);
        return this;
    }

    boolean inside(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    boolean onRing(int x, int y) {
        return x >= minX - 1 && x <= maxX + 1 && y >= minY - 1 && y <= maxY + 1
                && !inside(x, y);
    }
}
