package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.LabelLeavesStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivicHeadquartersFloorPlanTest {

    private static final int W = 24;
    private static final int H = 22;

    @Test
    void civicSpineIsSizedForTwoInfantryAbreast() {
        assertTrue(BuildingLayouts.TACTICAL_AISLE_WIDTH >= 4f * UnitType.MARINE.radius,
                "two-cell civic spine must fit two marine diameters");
    }

    @Test
    void allFrontagesProduceAlignedEntrancesFourRoomsAndDistinctCover() {
        BlockLeaf leaf = new BlockLeaf(3, 3, 17, 15, false); // 15x13
        for (BuildingPlacement.Side frontage : BuildingPlacement.Side.values()) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            List<Doodad> doodads = new ArrayList<>();
            PointOfInterest poi = BuildingShellCore.carve(
                    leaf, grid, topology, doodads, new Random(31),
                    BuildingCivicFiller.CONFIG, new BuildingPlacement(frontage, true));

            assertNotNull(poi, frontage.toString());
            assertEquals(PointOfInterest.Kind.ADMINISTRATIVE, poi.kind);
            assertEquals(2, perimeterDoorways(grid, leaf), frontage.toString());
            int publicDoor = doorwayAlong(grid, leaf, frontage);
            int serviceDoor = doorwayAlong(grid, leaf, frontage.opposite());
            assertTrue(publicDoor >= 0, "public entrance " + frontage);
            assertEquals(publicDoor, serviceDoor, "doors align on the civic spine " + frontage);
            assertEquals(RoomPurpose.OFFICE_CORRIDOR,
                    inwardPurpose(topology, leaf, frontage, publicDoor));
            assertEquals(RoomPurpose.OFFICE_CORRIDOR,
                    inwardPurpose(topology, leaf, frontage.opposite(), serviceDoor));

            assertTrue(countPurpose(topology, leaf, RoomPurpose.CIVIC_RECEPTION) >= 12);
            assertEquals(2, purposeComponents(topology, leaf, RoomPurpose.CIVIC_OFFICE));
            assertEquals(1, purposeComponents(topology, leaf, RoomPurpose.CONFERENCE_ROOM));
            assertEquals(1, purposeComponents(topology, leaf, RoomPurpose.SERVER_ROOM));
            assertTwoCellSpine(grid, topology, leaf, frontage, publicDoor);

            assertEquals(2, doodadsInPurpose(doodads, topology, RoomPurpose.CIVIC_OFFICE));
            assertEquals(1, doodadsInPurpose(doodads, topology, RoomPurpose.CONFERENCE_ROOM));
            assertEquals(1, doodadsInPurpose(doodads, topology, RoomPurpose.SERVER_ROOM));
            assertTrue(doodadsInPurpose(doodads, topology, RoomPurpose.CIVIC_RECEPTION) >= 1);

            for (int y = leaf.top + 1; y < leaf.bottom; y++) {
                for (int x = leaf.left + 1; x < leaf.right; x++) {
                    if (!topology.isFixture(x, y)) continue;
                    assertFalse(grid.isWalkable(x, y), "fixture blocks movement");
                    if (topology.getRoomPurpose(x, y) == RoomPurpose.SERVER_ROOM) {
                        assertFalse(grid.isSeeThrough(x, y), "server rack blocks LOS");
                    } else {
                        assertTrue(grid.isSeeThrough(x, y), "low office fixture preserves LOS");
                    }
                }
            }

            boolean[] reached = flood(grid, leaf, frontage, publicDoor);
            assertAllWalkableInteriorReached(grid, reached, leaf, frontage);
            for (RoomPurpose purpose : new RoomPurpose[]{RoomPurpose.CIVIC_RECEPTION,
                    RoomPurpose.CIVIC_OFFICE, RoomPurpose.CONFERENCE_ROOM,
                    RoomPurpose.SERVER_ROOM}) {
                assertTrue(reachesPurpose(topology, reached, leaf, purpose),
                        purpose + " reachable from " + frontage);
            }

            topology.tagDefaultWalls(grid);
            for (int y = leaf.top + 1; y < leaf.bottom; y++) {
                for (int x = leaf.left + 1; x < leaf.right; x++) {
                    if (topology.isFixture(x, y)) {
                        assertFalse(topology.isWall(x, y), "fixture must not become structure");
                    }
                }
            }
        }
    }

    @Test
    void generatedCivicHeadquartersOnlyUseLargeLots() {
        BspCityGenerator generator = new BspCityGenerator();
        int headquarters = 0;
        for (long seed = 0; seed < 20; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (PointOfInterest poi : map.pointsOfInterest) {
                if (poi.kind != PointOfInterest.Kind.ADMINISTRATIVE) continue;
                headquarters++;
                int width = poi.right - poi.left + 1;
                int height = poi.bottom - poi.top + 1;
                assertTrue(Math.max(width, height) >= LabelLeavesStage.CIVIC_MIN_LONG_DIM);
                assertTrue(Math.min(width, height) >= LabelLeavesStage.CIVIC_MIN_SHORT_DIM);
            }
        }
        assertTrue(headquarters >= 2, "civic headquarters should survive representative size filtering");
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static int perimeterDoorways(NavigationGrid grid, BlockLeaf leaf) {
        int count = 0;
        for (int x = leaf.left; x <= leaf.right; x++) {
            if (grid.isDoorway(x, leaf.top)) count++;
            if (grid.isDoorway(x, leaf.bottom)) count++;
        }
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            if (grid.isDoorway(leaf.left, y)) count++;
            if (grid.isDoorway(leaf.right, y)) count++;
        }
        return count;
    }

    private static int doorwayAlong(NavigationGrid grid, BlockLeaf leaf,
                                    BuildingPlacement.Side side) {
        int min = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.left + 1 : leaf.top + 1;
        int max = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            int x = side == BuildingPlacement.Side.LEFT ? leaf.left
                    : side == BuildingPlacement.Side.RIGHT ? leaf.right : along;
            int y = side == BuildingPlacement.Side.TOP ? leaf.top
                    : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom : along;
            if (grid.isDoorway(x, y)) return along;
        }
        return -1;
    }

    private static RoomPurpose inwardPurpose(CellTopology topology, BlockLeaf leaf,
                                             BuildingPlacement.Side side, int along) {
        int x = side == BuildingPlacement.Side.LEFT ? leaf.left + 1
                : side == BuildingPlacement.Side.RIGHT ? leaf.right - 1 : along;
        int y = side == BuildingPlacement.Side.TOP ? leaf.top + 1
                : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom - 1 : along;
        return topology.getRoomPurpose(x, y);
    }

    private static void assertTwoCellSpine(NavigationGrid grid, CellTopology topology,
                                           BlockLeaf leaf, BuildingPlacement.Side frontage,
                                           int publicDoor) {
        boolean vertical = frontage == BuildingPlacement.Side.TOP
                || frontage == BuildingPlacement.Side.BOTTOM;
        int second = publicDoor + 1;
        int min = vertical ? leaf.top + 1 : leaf.left + 1;
        int max = vertical ? leaf.bottom - 1 : leaf.right - 1;
        for (int along = min; along <= max; along++) {
            for (int across : new int[]{publicDoor, second}) {
                int x = vertical ? across : along;
                int y = vertical ? along : across;
                assertTrue(grid.isWalkable(x, y), "spine walkable at " + x + "," + y);
                assertEquals(RoomPurpose.OFFICE_CORRIDOR, topology.getRoomPurpose(x, y));
            }
        }
    }

    private static int countPurpose(CellTopology topology, BlockLeaf leaf, RoomPurpose purpose) {
        int count = 0;
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                if (topology.getRoomPurpose(x, y) == purpose) count++;
            }
        }
        return count;
    }

    private static int purposeComponents(CellTopology topology, BlockLeaf leaf,
                                         RoomPurpose purpose) {
        boolean[] seen = new boolean[W * H];
        int components = 0;
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                int index = y * W + x;
                if (seen[index] || topology.getRoomPurpose(x, y) != purpose) continue;
                components++;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                seen[index] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = cell[0] + direction[0];
                        int ny = cell[1] + direction[1];
                        if (!leaf.contains(nx, ny)) continue;
                        int next = ny * W + nx;
                        if (seen[next] || topology.getRoomPurpose(nx, ny) != purpose) continue;
                        seen[next] = true;
                        queue.addLast(new int[]{nx, ny});
                    }
                }
            }
        }
        return components;
    }

    private static int doodadsInPurpose(List<Doodad> doodads, CellTopology topology,
                                        RoomPurpose purpose) {
        int count = 0;
        for (Doodad doodad : doodads) {
            if (topology.getRoomPurpose(doodad.cellX, doodad.cellY) == purpose) count++;
        }
        return count;
    }

    private static boolean[] flood(NavigationGrid grid, BlockLeaf leaf,
                                   BuildingPlacement.Side frontage, int along) {
        int startX = frontage == BuildingPlacement.Side.LEFT ? leaf.left
                : frontage == BuildingPlacement.Side.RIGHT ? leaf.right : along;
        int startY = frontage == BuildingPlacement.Side.TOP ? leaf.top
                : frontage == BuildingPlacement.Side.BOTTOM ? leaf.bottom : along;
        boolean[] seen = new boolean[W * H];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        seen[startY * W + startX] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = cell[0] + direction[0];
                int y = cell[1] + direction[1];
                if (!leaf.contains(x, y) || !grid.isWalkable(x, y)) continue;
                int index = y * W + x;
                if (seen[index]) continue;
                seen[index] = true;
                queue.addLast(new int[]{x, y});
            }
        }
        return seen;
    }

    private static void assertAllWalkableInteriorReached(NavigationGrid grid, boolean[] reached,
                                                          BlockLeaf leaf,
                                                          BuildingPlacement.Side frontage) {
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                if (grid.isWalkable(x, y)) {
                    assertTrue(reached[y * W + x],
                            "unreachable interior at " + x + "," + y + " from " + frontage);
                }
            }
        }
    }

    private static boolean reachesPurpose(CellTopology topology, boolean[] reached,
                                          BlockLeaf leaf, RoomPurpose purpose) {
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                if (reached[y * W + x] && topology.getRoomPurpose(x, y) == purpose) return true;
            }
        }
        return false;
    }
}
