package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BuildingStampTest {
    static GenContext open(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
        }
        return new GenContext(grid, topology, new Random(42), width, height, 42);
    }

    @Test
    void everyPlanKeepsItsRoomsAndRecessesReachableInEveryPose() {
        for (BuildingStamp stamp : BuildingStamp.values()) for (int turn = 0; turn < 4; turn++) {
            GenContext ctx = open(stamp.width(turn) + 4, stamp.height(turn) + 4);
            stamp.place(ctx, 2, 2, turn);
            boolean[][] reached = new boolean[ctx.width][ctx.height];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{0, 0});
            reached[0][0] = true;
            while (!queue.isEmpty()) {
                int[] c = queue.removeFirst();
                for (Direction d : Direction.CARDINALS) {
                    int x = c[0] + d.dx, y = c[1] + d.dy;
                    if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height
                            || reached[x][y] || !ctx.grid.isWalkable(x, y)
                            || !ctx.grid.isSharedEdgePassable(c[0], c[1], d)) continue;
                    reached[x][y] = true;
                    queue.add(new int[]{x, y});
                }
            }
            for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
                if (ctx.grid.isWalkable(x, y)) assertTrue(reached[x][y],
                        stamp + " turn " + turn + " stranded " + x + "," + y);
            }
            var poi = ctx.pois.get(0);
            assertTrue(ctx.grid.isWalkable(poi.interiorAnchorX, poi.interiorAnchorY));
            assertEquals(stamp.kind, ctx.topology.getBuildingKindHint(poi.interiorAnchorX, poi.interiorAnchorY));
            assertTrue(ctx.grid.isWalkable(poi.anchorCellX, poi.anchorCellY));
            assertNull(ctx.topology.getBuildingKindHint(poi.anchorCellX, poi.anchorCellY));
            assertTrue(ctx.grid.getEdgeBarriers().size() >= 2);
            assertEquals(1, BuildingFloodFill.populate(ctx.topology, 42).size(),
                    "partition doorways must join each plan into one roof");
            for (int y = 0; y < stamp.height; y++) for (int x = 0; x < stamp.width; x++) {
                if (stamp.cell(x, y) != ' ') continue;
                int[] p = stamp.posed(x, y, turn);
                assertEquals(0, ctx.topology.getBuildingId(p[0] + 2, p[1] + 2),
                        "concave cuts must not be roofed over");
            }
        }
    }

    @Test
    void rotationMovesSurfacesDoorwaysFixturesAndWindowOwnersTogether() {
        for (BuildingStamp stamp : BuildingStamp.values()) {
            GenContext original = open(stamp.width + 4, stamp.height + 4);
            stamp.place(original, 2, 2, 0);
            for (int turn = 1; turn < 4; turn++) {
                GenContext rotated = open(stamp.width(turn) + 4, stamp.height(turn) + 4);
                stamp.place(rotated, 2, 2, turn);
                for (int y = 0; y < stamp.height; y++) for (int x = 0; x < stamp.width; x++) {
                    int[] p = stamp.posed(x, y, turn);
                    int px = p[0] + 2, py = p[1] + 2;
                    assertEquals(original.grid.isWalkable(x + 2, y + 2), rotated.grid.isWalkable(px, py));
                    assertEquals(original.grid.isDoorway(x + 2, y + 2), rotated.grid.isDoorway(px, py));
                    if (original.grid.isDoorway(x + 2, y + 2)) {
                        int[] bits = {CellTopology.WALL_DIR_N, CellTopology.WALL_DIR_E,
                                CellTopology.WALL_DIR_S, CellTopology.WALL_DIR_W};
                        int facing = original.topology.getWallDirMask(x + 2, y + 2);
                        assertEquals(1, Integer.bitCount(facing), "each door needs one retained facing");
                        for (int d = 0; d < 4; d++) if (facing == bits[d]) {
                            assertEquals(bits[Math.floorMod(d - turn, 4)], rotated.topology.getWallDirMask(px, py));
                        }
                    }
                    assertEquals(original.topology.getSurfaceId(x + 2, y + 2), rotated.topology.getSurfaceId(px, py));
                    assertEquals(original.topology.getBuildingKindHint(x + 2, y + 2), rotated.topology.getBuildingKindHint(px, py));
                    if (stamp.cell(x, y) == ' ') {
                        assertNull(rotated.topology.getSurfaceId(px, py), "courtyard must stay outdoors");
                    }
                }
                for (Doodad d : original.doodads) {
                    int[] p = stamp.posed(d.cellX - 2, d.cellY - 2, turn);
                    final int facing = turn;
                    assertTrue(rotated.doodads.stream().anyMatch(r -> r.cellX == p[0] + 2
                            && r.cellY == p[1] + 2 && r.quarterTurns == facing));
                }
                for (var window : original.grid.getEdgeBarriers()) {
                    int[] p = stamp.posed(window.structureCellX() - 2, window.structureCellY() - 2, turn);
                    assertTrue(rotated.grid.getEdgeBarriers().stream().anyMatch(w ->
                            w.structureCellX() == p[0] + 2 && w.structureCellY() == p[1] + 2));
                }
            }
        }
    }

    @Test
    void materialsResolveToShippedBlocks() {
        for (BuildingStyle style : BuildingStyle.values()) {
            assertNotNull(TileRegistry.installed().block(style.wall), style.wall);
            assertNotNull(TileRegistry.installed().block(style.floor), style.floor);
            assertNotNull(TileRegistry.installed().block(style.accent), style.accent);
        }
    }
}

