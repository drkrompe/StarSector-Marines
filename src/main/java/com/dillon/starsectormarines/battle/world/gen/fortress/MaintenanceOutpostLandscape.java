package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.SummitDressing;
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
        // Overlapping lobes and short spurs form broad, varying-width masses.
        // Their inland toes are authored around the basin; their backs continue off-map.
        var plan = new TerrainPlan(c.width, c.height, finish, 3.5, .9, c.seed, List.of(
                ridge(19, new Point(0, 0), new Point(0, 159)),
                ridge(24, new Point(5, 22), new Point(3, 40)),
                ridge(27, new Point(0, 68)),
                ridge(23, new Point(7, 101)),
                ridge(29, new Point(0, 143)),
                ridge(22, new Point(20, 159), new Point(172, 159)),
                ridge(26, new Point(47, 154)),
                ridge(18, new Point(77, 146), new Point(91, 151)),
                ridge(30, new Point(115, 159)),
                ridge(19, new Point(143, 146)),
                ridge(13, new Point(163, 141), new Point(177, 133)),
                ridge(25, new Point(222, 159)),
                ridge(30, new Point(253, 157)),
                ridge(18, new Point(281, 159)),
                ridge(15, new Point(240, 134), new Point(249, 141)),
                ridge(18, new Point(18, 0), new Point(172, 0)),
                ridge(27, new Point(44, 0)),
                ridge(24, new Point(71, 5)),
                ridge(16, new Point(97, 0)),
                ridge(26, new Point(128, 0)),
                ridge(17, new Point(147, 11)),
                ridge(12, new Point(169, 6), new Point(181, 0)),
                ridge(22, new Point(220, 0)),
                ridge(29, new Point(250, 1)),
                ridge(16, new Point(276, 0)),
                ridge(12, new Point(231, 22)),
                // Open gullies break the rounded lobes without cutting isolated pockets.
                cut(7, new Point(34, 117), new Point(18, 123)),
                cut(8, new Point(101, 127), new Point(106, 144)),
                cut(6, new Point(158, 31), new Point(163, 17)),
                cut(7, new Point(261, 36), new Point(269, 20))));
        boolean[] reserved = new boolean[c.width * c.height];
        var roads = c.get(BspKeys.ROAD_RESERVATION);
        var claim = c.get(BspKeys.PRECINCT_CLAIM);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++)
            reserved[y * c.width + x] = roads[x][y] || claim[x][y] != GrownTrunkPlan.UNOWNED
                    || berthApproach(c, x, y);
        TerrainBrush.stamp(plan, c.grid, c.topology, reserved);
        var summit = new SummitDressing(c);
        summit.relay(57, 147);
        summit.scatter(c.get(BspKeys.MARKET_PROFILE).surface());
    }

    private static Stroke ridge(double radius, Point... points) {
        return new Stroke(TerrainPlan.Mode.RAISE, radius, List.of(points));
    }

    private static Stroke cut(double radius, Point... points) {
        return new Stroke(TerrainPlan.Mode.ERASE, radius, List.of(points));
    }

    static void fields(GenContext c) {
        boolean[] open = new boolean[c.width * c.height];
        var claim = c.get(BspKeys.PRECINCT_CLAIM);
        var roads = c.get(BspKeys.ROAD_RESERVATION);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (claim[x][y] != GrownTrunkPlan.UNOWNED || roads[x][y] || c.isMadeGround(x, y)
                    || !c.grid.isWalkable(x, y) || c.grid.isDoorway(x, y)
                    || c.topology.isWall(x, y) || c.topology.isFixture(x, y)
                    || c.topology.hasBuildingKindHint(x, y) || c.topology.getSurface(x, y) != 0
                    || c.topology.roadSurface().underlay(x, y) != null) continue;
            boolean terrain = false;
            if (c.topology.terrainSurface() != null)
                for (int layer = 0; layer < 8; layer++)
                    if (c.topology.terrainSurface().patch(x, y, layer) != null) terrain = true;
            open[y * c.width + x] = !terrain;
        }
        // Air approaches exclude tall obstructions, not native ground patches or passable stones.
        // The explicit hardstands above are protected by made-ground ownership instead.
        NatureZoneFiller.dressOpenGround(c, open);
    }

    private static boolean berthApproach(GenContext c, int x, int y) {
        return c.landingPads.stream().anyMatch(p -> x >= p.left() - 4
                && y >= p.bottom() - 4 && y <= p.top() + 4);
    }
}
