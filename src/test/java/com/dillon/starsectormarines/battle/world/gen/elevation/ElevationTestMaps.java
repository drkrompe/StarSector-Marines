package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.List;

/**
 * Authored elevation test maps, sized to fill one 1920x1080 frame at play zoom
 * (64x36 cells at 30 px).
 */
public final class ElevationTestMaps {

    private ElevationTestMaps() {}

    public static final int WIDTH = 64;
    public static final int HEIGHT = 36;

    /**
     * The overlook: a long plateau across the middle of a meadow, climbed by a
     * narrow infantry ramp from the west and a vehicle-wide ramp from the south,
     * with a second tier on its east end reached by its own ramp.
     */
    public static ElevationPlan overlook(int faceWidth) {
        return new ElevationPlan(WIDTH, HEIGHT, faceWidth, .6, 11,
                List.of(new Plateau(1, 10, List.of(new Point(29, 19), new Point(44, 18))),
                        new Plateau(2, 4.5, List.of(new Point(45, 18.5)))),
                List.of(new Ramp(new Point(15, 19), new Point(23, 19), 3),
                        new Ramp(new Point(35, 3), new Point(35, 12), 5),
                        new Ramp(new Point(35, 18.5), new Point(43, 18.5), 3)));
    }

    /** A flat meadow of the overlook's size, before any elevation. */
    public static MapResult meadow(CellTopology.GroundKind ground) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, ground);
            }
        }
        return new MapResult(grid, topology, 0, 0, 0, 0, new ArrayList<>(), new ArrayList<>());
    }

    /** The overlook stamped onto a meadow. */
    public static MapResult overlookMap(int faceWidth, CellTopology.GroundKind ground) {
        MapResult map = meadow(ground);
        ElevationBrush.stamp(overlook(faceWidth), map.grid, map.topology);
        return map;
    }
}
