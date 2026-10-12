package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CompoundLayoutTest {
    @Test void cardinalPosesKeepBuildingsCourtsAndVehicleRoadsSeparate() {
        for (boolean store : new boolean[]{false, true}) for (int turn = 0; turn < 4; turn++) {
            var layout = MaintenanceOutpost.layout(store);
            var site = layout.at(12, 12, turn);
            var road = RoadRasterizer.rasterize(new RoadPlan(100, 100, site.roads(17)));
            Set<Long> occupied = new HashSet<>();
            for (var room : site.buildings()) {
                Set<Long> doors = new HashSet<>();
                room.doors().forEach(d -> doors.add(key(d.x(), d.y())));
                Set<Long> ring = new HashSet<>();
                for (int[] p : room.shape().wall()) ring.add(key(room.originX() + p[0], room.originY() + p[1]));
                assertTrue(ring.containsAll(doors), "every rotated door must belong to its own shell");
                for (int[][] cells : new int[][][]{room.shape().filled(), room.shape().wall()})
                    for (int[] p : cells) {
                        int x = room.originX() + p[0], y = room.originY() + p[1];
                        assertTrue(site.contains(x, y), "building ring must stay inside the compound");
                        assertTrue(occupied.add(key(x, y)), "buildings must not overlap");
                        if (!doors.contains(key(x, y))) assertFalse(road.road(x, y),
                                "vehicle road crosses " + room.purpose() + " at " + x + "," + y);
                    }
            }
            for (var yard : layout.yards()) for (int x = yard.x(); x < yard.x() + yard.width(); x++)
                for (int y = yard.y(); y < yard.y() + yard.height(); y++) {
                    int[] at = site.cell(x, y);
                    assertTrue(site.contains(at[0], at[1]));
                    assertFalse(occupied.contains(key(at[0], at[1])), "shared yard cannot become building floor");
                }
        }
    }

    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
}
