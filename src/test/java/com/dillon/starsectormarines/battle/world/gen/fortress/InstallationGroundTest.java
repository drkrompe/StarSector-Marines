package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class InstallationGroundTest {
    @Test void ownsOpenYardButPreservesBuildingsOtherClaimsAndNavigation() {
        for (SurfacePalette planet : SurfacePalette.values()) {
            var grid = new NavigationGrid(24, 24);
            var topology = new CellTopology(24, 24);
            var ctx = new GenContext(grid, topology, new Random(42), 24, 24, 42);
            ctx.put(BspKeys.MARKET_PROFILE, new TargetProfile(0, 0, 0, 0, "", Set.of(), planet, null));
            boolean[][] ground = new boolean[24][24], road = new boolean[24][24];
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.GRASS);
                ground[x][y] = x > 0 && y > 0 && x < 23 && y < 23;
                road[x][y] = x == 20 && y > 0 && y < 23;
            }
            var shape = RoomShape.of("######", "##....", "##....", "##....", "##....", "##....");
            var room = new RoomPacker.Placed(shape, RoomPose.CANONICAL, 5, 5, RoomPurpose.BARRACKS, List.of());
            int interior = topology.surfaceIndex("floor.authored");
            for (int[] cell : shape.filled()) {
                topology.setGroundKind(5 + cell[0], 5 + cell[1], GroundKind.INDOOR);
                topology.setSurface(5 + cell[0], 5 + cell[1], interior);
            }
            // A perimeter wall and an already-authored pad cannot acquire pavement art.
            grid.setWalkable(1, 1, false);
            grid.setWallHp(1, 1, 900);
            topology.setWall(1, 1, true);
            topology.setSurface(1, 1, topology.surfaceIndex("wall.authored"));
            ctx.markMadeGround(21, 21);
            topology.setSurface(21, 21, topology.surfaceIndex("pad.authored"));
            grid.setDoorway(11, 5, true);
            topology.setGroundKind(11, 5, GroundKind.STRIPED);
            topology.setSurface(11, 5, topology.surfaceIndex("threshold.authored"));
            InstallationGround.dress(ctx, ground, road, List.of(room));
            assertEquals("wall.authored", topology.getSurfaceId(1, 1));
            assertEquals(900, grid.getWallHp(1, 1));
            assertEquals("pad.authored", topology.getSurfaceId(21, 21));
            assertEquals("threshold.authored", topology.getSurfaceId(11, 5));
            assertTrue(grid.isDoorway(11, 5));
            assertEquals("floor.authored", topology.getSurfaceId(5, 5));
            assertEquals(GroundKind.INDOOR, topology.getGroundKind(5, 5));
            assertEquals(GroundKind.GRASS, topology.getGroundKind(0, 0), "outside the claim");
            assertNull(topology.getSurfaceId(0, 0));
            assertTrue(topology.getSurfaceId(8, 8).startsWith("pavement.concrete."), "L cutout is outdoor apron");
            assertTrue(topology.getSurfaceId(20, 12).startsWith("pavement.asphalt."));
            assertEquals(planet.inlandGround(), topology.getGroundKind(16, 16));
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++)
                assertEquals(x != 1 || y != 1, grid.isWalkable(x, y), "surface painting cannot change access");
            assertEquals(new Random(42).nextLong(), ctx.rng.nextLong(), "no layout RNG consumed");
        }
    }
}
