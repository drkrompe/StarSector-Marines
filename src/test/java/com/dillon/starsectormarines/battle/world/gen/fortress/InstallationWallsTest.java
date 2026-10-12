package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class InstallationWallsTest {
    @Test void rotatedConcaveShellsKeepFloorsAndOpeningsAndCollapseWithTheirOwnMaterial() {
        for (RoomPurpose purpose : List.of(RoomPurpose.BARRACKS, RoomPurpose.CONTROL_ROOM, RoomPurpose.VEHICLE_BAY))
            for (RoomPose pose : RoomPose.all()) {
                var grid = new NavigationGrid(12, 12);
                var topology = new CellTopology(12, 12);
                var ctx = new GenContext(grid, topology, new Random(42), 12, 12, 42);
                var shape = RoomShape.of("####", "##..", "##..").posed(pose);
                var room = new RoomPacker.Placed(shape, pose, 4, 4, purpose, List.of());
                for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
                }
                for (int[] c : shape.wall()) {
                    int x = 4 + c[0], y = 4 + c[1];
                    grid.setWalkable(x, y, false);
                    grid.setWallHp(x, y, 100);
                    topology.setWall(x, y, true);
                }
                int[][] ring = shape.wall();
                int[] door = ring[0], window = ring[1], fixture = ring[2], authored = ring[3], target = ring[4];
                grid.setWalkableFloor(4 + door[0], 4 + door[1]);
                grid.setDoorway(4 + door[0], 4 + door[1], true);
                topology.setWall(4 + door[0], 4 + door[1], false);
                topology.setSurface(4 + door[0], 4 + door[1], topology.surfaceIndex("floor.threshold"));
                grid.setWalkableFloor(4 + window[0], 4 + window[1]);
                topology.setWall(4 + window[0], 4 + window[1], false);
                topology.setFixture(4 + fixture[0], 4 + fixture[1], true);
                topology.setSurface(4 + authored[0], 4 + authored[1], topology.surfaceIndex("wall.specialty"));
                var kinds = topology.copyGroundKinds();
                InstallationWalls.dress(ctx, List.of(room));
                assertArrayEquals(kinds, topology.copyGroundKinds());
                assertEquals("floor.threshold", topology.getSurfaceId(4 + door[0], 4 + door[1]));
                assertNull(topology.getSurfaceId(4 + window[0], 4 + window[1]));
                assertNull(topology.getSurfaceId(4 + fixture[0], 4 + fixture[1]));
                assertEquals("wall.specialty", topology.getSurfaceId(4 + authored[0], 4 + authored[1]));
                for (int[] c : shape.filled()) assertNull(topology.getSurfaceId(4 + c[0], 4 + c[1]));
                assertEquals(new Random(42).nextLong(), ctx.rng.nextLong());
                int x = 4 + target[0], y = 4 + target[1];
                assertEquals(InstallationWalls.BUILDING, topology.getSurfaceId(x, y));
                var editor = new MapEditor(new NavigationService(grid, topology));
                assertFalse(editor.damageWall(x, y, 30));
                assertEquals(70, grid.getWallHp(x, y));
                assertFalse(grid.isWalkable(x, y));
                assertTrue(editor.damageWall(x, y, 70));
                assertEquals(InstallationWalls.BUILDING, topology.getWallRemnant(x, y).materialId());
                assertEquals(InstallationFloors.material(purpose), topology.getWallRemnant(x, y).groundSurfaceId(),
                        "corners and isolated walls retain their floor without inferring a neighbour");
                assertTrue(grid.isWalkable(x, y));
                assertNull(topology.getSurfaceId(x, y));
            }
    }

    @Test void perimeterCoatCannotSealOrPaintItsGate() {
        var grid = new NavigationGrid(5, 3);
        var topology = new CellTopology(5, 3);
        var ctx = new GenContext(grid, topology, new Random(1), 5, 3, 1);
        boolean[][] ring = new boolean[5][3];
        for (int x = 0; x < 5; x++) { ring[x][1] = true; grid.setWallHp(x, 1, 600); }
        grid.setWalkableFloor(2, 1);
        InstallationWalls.perimeter(ctx, ring);
        assertNull(topology.getSurfaceId(2, 1));
        assertTrue(grid.isWalkable(2, 1));
        assertEquals(InstallationWalls.PERIMETER, topology.getSurfaceId(1, 1));
        assertEquals(600, grid.getWallHp(1, 1));
    }
}
