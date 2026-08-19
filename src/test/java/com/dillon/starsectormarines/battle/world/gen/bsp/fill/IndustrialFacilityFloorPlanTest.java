package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustrialFacilityFloorPlanTest {

    private static final int W = 26;
    private static final int H = 24;

    @Test
    void serviceAndProductionLanesFitInfantryFormations() {
        assertTrue(BuildingLayouts.TACTICAL_AISLE_WIDTH >= 4f * UnitType.MARINE.radius,
                "two-cell industrial lanes must fit two marine diameters");
    }

    @Test
    void allFrontagesProduceReachableFactoryRoomsAndOpaqueMachineLanes() {
        BlockLeaf leaf = new BlockLeaf(3, 3, 19, 15, false); // 17x13
        for (BuildingPlacement.Side frontage : BuildingPlacement.Side.values()) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            List<Doodad> doodads = new ArrayList<>();
            PointOfInterest poi = BuildingShellCore.carve(
                    leaf, grid, topology, doodads, new Random(41),
                    BuildingIndustrialFiller.CONFIG, new BuildingPlacement(frontage, true));

            assertNotNull(poi, frontage.toString());
            assertEquals(PointOfInterest.Kind.DEPOT, poi.kind);
            assertEquals(2, perimeterDoorways(grid, leaf), frontage.toString());
            int loadingDoor = doorwayAlong(grid, leaf, frontage);
            int serviceDoor = doorwayAlong(grid, leaf, frontage.opposite());
            assertTrue(loadingDoor >= 0, "loading entrance " + frontage);
            assertEquals(loadingDoor, serviceDoor, "doors align on service spine " + frontage);
            assertEquals(RoomPurpose.INDUSTRIAL_SPINE,
                    inwardPurpose(topology, leaf, frontage, loadingDoor));
            assertEquals(RoomPurpose.INDUSTRIAL_SPINE,
                    inwardPurpose(topology, leaf, frontage.opposite(), serviceDoor));

            for (RoomPurpose purpose : new RoomPurpose[]{RoomPurpose.LOADING_BAY,
                    RoomPurpose.PRODUCTION_FLOOR, RoomPurpose.CONTROL_ROOM,
                    RoomPurpose.PARTS_CAGE}) {
                assertEquals(1, purposeComponents(topology, leaf, purpose),
                        purpose + " component from " + frontage);
            }
            assertTwoCellSpine(grid, topology, leaf, frontage, loadingDoor);
            assertTrue(doodadsInPurpose(doodads, topology, RoomPurpose.PRODUCTION_FLOOR) >= 3);
            assertEquals(1, doodadsInPurpose(doodads, topology, RoomPurpose.CONTROL_ROOM));
            assertEquals(2, doodadsInPurpose(doodads, topology, RoomPurpose.PARTS_CAGE));
            assertEquals(1, doodadsInPurpose(doodads, topology, RoomPurpose.LOADING_BAY));
            assertMachineryLanes(grid, topology, doodads, frontage, loadingDoor);

            for (int y = leaf.top + 1; y < leaf.bottom; y++) {
                for (int x = leaf.left + 1; x < leaf.right; x++) {
                    if (!topology.isFixture(x, y)) continue;
                    assertFalse(grid.isWalkable(x, y), "fixture blocks movement");
                    boolean opaque = topology.getRoomPurpose(x, y) == RoomPurpose.PRODUCTION_FLOOR;
                    assertEquals(!opaque, grid.isSeeThrough(x, y),
                            "only production machinery blocks line of sight");
                }
            }

            boolean[] reached = flood(grid, leaf, frontage, loadingDoor);
            assertAllWalkableInteriorReached(grid, reached, leaf);
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
    void undersizedIndustrialLotsRetainLegacyWarehousePlan() {
        BlockLeaf leaf = new BlockLeaf(3, 3, 15, 11, false); // 13x9
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        List<Doodad> doodads = new ArrayList<>();
        BuildingShellCore.carve(leaf, grid, topology, doodads, new Random(7),
                BuildingIndustrialFiller.CONFIG,
                new BuildingPlacement(BuildingPlacement.Side.LEFT, true));

        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                assertNull(topology.getRoomPurpose(x, y), "legacy warehouse remains unlabeled");
            }
        }
        assertTrue(doodads.stream().noneMatch(d -> topology.isFixture(d.cellX, d.cellY)),
                "legacy warehouse props remain visual-only");
    }

    @Test
    void representativeCitySeedsProduceLargeIndustrialFacilities() {
        BspCityGenerator generator = new BspCityGenerator();
        int controlRooms = 0;
        for (long seed = 0; seed < 30; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (int y = 0; y < map.grid.getHeight(); y++) {
                for (int x = 0; x < map.grid.getWidth(); x++) {
                    if (map.topology.getRoomPurpose(x, y) == RoomPurpose.CONTROL_ROOM) {
                        controlRooms++;
                    }
                }
            }
        }
        assertTrue(controlRooms > 0,
                "large factories should survive representative BSP lot sizing");
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
        int min = vertical ? leaf.top + 1 : leaf.left + 1;
        int max = vertical ? leaf.bottom - 1 : leaf.right - 1;
        for (int along = min; along <= max; along++) {
            for (int across : new int[]{publicDoor, publicDoor + 1}) {
                int x = vertical ? across : along;
                int y = vertical ? along : across;
                assertTrue(grid.isWalkable(x, y), "spine walkable at " + x + "," + y);
                assertEquals(RoomPurpose.INDUSTRIAL_SPINE, topology.getRoomPurpose(x, y));
            }
        }
    }

    private static void assertMachineryLanes(NavigationGrid grid, CellTopology topology,
                                              List<Doodad> doodads,
                                              BuildingPlacement.Side frontage, int spineLow) {
        boolean vertical = frontage == BuildingPlacement.Side.TOP
                || frontage == BuildingPlacement.Side.BOTTOM;
        List<Doodad> machines = new ArrayList<>();
        for (Doodad doodad : doodads) {
            if (topology.getRoomPurpose(doodad.cellX, doodad.cellY)
                    == RoomPurpose.PRODUCTION_FLOOR) machines.add(doodad);
        }
        machines.sort(Comparator.comparingInt(d -> vertical ? d.cellY : d.cellX));
        for (int i = 1; i < machines.size(); i++) {
            int previous = vertical ? machines.get(i - 1).cellY : machines.get(i - 1).cellX;
            int current = vertical ? machines.get(i).cellY : machines.get(i).cellX;
            assertTrue(current - previous >= 3, "machinery leaves two-cell cross gaps");
        }
        for (Doodad machine : machines) {
            int across = vertical ? machine.cellX : machine.cellY;
            int direction = Integer.compare(spineLow, across);
            for (int step = 1; step <= BuildingLayouts.TACTICAL_AISLE_WIDTH; step++) {
                int x = vertical ? across + direction * step : machine.cellX;
                int y = vertical ? machine.cellY : across + direction * step;
                assertTrue(grid.isWalkable(x, y), "clear production lane beside machinery");
            }
        }
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
                                                          BlockLeaf leaf) {
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                if (grid.isWalkable(x, y)) {
                    assertTrue(reached[y * W + x], "unreachable interior at " + x + "," + y);
                }
            }
        }
    }
}
