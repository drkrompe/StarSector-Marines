package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan.Stroke;

import java.util.List;

/** The outpost occupies a basin; its surrounding country remains the planet's ground. */
final class MaintenanceOutpostLandscape {
    private MaintenanceOutpostLandscape() {}

    static void mountains(GenContext c) {
        var finish = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> TerrainPlan.Finish.GRASS_TOP;
            case FROZEN -> TerrainPlan.Finish.ICE;
            default -> TerrainPlan.Finish.ROCK;
        };
        var plan = new TerrainPlan(c.width, c.height, finish, 3.5, .7, c.seed, List.of(
                ridge(11, new Point(20, 37), new Point(20, 75), new Point(22, 116)),
                ridge(11, new Point(54, 137), new Point(102, 138), new Point(148, 135), new Point(174, 126)),
                ridge(9, new Point(52, 20), new Point(101, 18), new Point(147, 22), new Point(172, 28)),
                ridge(9, new Point(206, 137), new Point(229, 143)),
                ridge(9, new Point(216, 21), new Point(240, 26))));
        boolean[] reserved = new boolean[c.width * c.height];
        var roads = c.get(BspKeys.ROAD_RESERVATION);
        var claim = c.get(BspKeys.PRECINCT_CLAIM);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++)
            reserved[y * c.width + x] = roads[x][y] || claim[x][y] != GrownTrunkPlan.UNOWNED
                    || berthApproach(c, x, y);
        TerrainBrush.stamp(plan, c.grid, c.topology, reserved);
    }

    private static Stroke ridge(double radius, Point... points) {
        return new Stroke(TerrainPlan.Mode.RAISE, radius, List.of(points));
    }

    static void fields(GenContext c) {
        boolean[] open = new boolean[c.width * c.height];
        var claim = c.get(BspKeys.PRECINCT_CLAIM);
        var roads = c.get(BspKeys.ROAD_RESERVATION);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (claim[x][y] != GrownTrunkPlan.UNOWNED || roads[x][y] || c.isMadeGround(x, y)
                    || berthApproach(c, x, y) || !c.grid.isWalkable(x, y) || c.grid.isDoorway(x, y)
                    || c.topology.isWall(x, y) || c.topology.isFixture(x, y)
                    || c.topology.hasBuildingKindHint(x, y) || c.topology.getSurface(x, y) != 0
                    || c.topology.roadSurface().underlay(x, y) != null) continue;
            boolean terrain = false;
            if (c.topology.terrainSurface() != null)
                for (int layer = 0; layer < 8; layer++)
                    if (c.topology.terrainSurface().patch(x, y, layer) != null) terrain = true;
            open[y * c.width + x] = !terrain;
        }
        NatureZoneFiller.dressOpenGround(c, open);
    }

    private static boolean berthApproach(GenContext c, int x, int y) {
        return c.landingPads.stream().anyMatch(p -> x >= p.left() - 4
                && y >= p.bottom() - 4 && y <= p.top() + 4);
    }
}
