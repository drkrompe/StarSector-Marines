package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
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

class CommercialFloorPlanTest {

    private static final int W = 24;
    private static final int H = 20;
    private static final BuildingShellCore.BuildingConfig CONFIG =
            new BuildingShellCore.BuildingConfig(
                    CellTopology.GroundKind.TILE, "COMMERCIAL",
                    PointOfInterest.Kind.RESIDENTIAL,
                    BuildingLayouts.LayoutRecipe.SHOP,
                    BuildingKind.COMMERCIAL,
                    new RoomPurpose[]{RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM},
                    CommercialPartitionStrategy.DEFAULT);

    @Test
    void tacticalAislesAreSizedForInfantryBodies() {
        float twoInfantryAbreast = 4f * UnitType.MARINE.radius;
        assertTrue(BuildingLayouts.TACTICAL_AISLE_WIDTH >= twoInfantryAbreast,
                "two-cell aisle must fit two marine diameters: " + twoInfantryAbreast);
    }

    @Test
    void largeStoresGenerateReachableRoomsAndRealShelfFootprintsAcrossSeeds() {
        BlockLeaf leaf = new BlockLeaf(3, 3, 17, 13, false); // 15x11; tactical threshold
        for (long seed = 0; seed < 25; seed++) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            List<Doodad> doodads = new ArrayList<>();

            PointOfInterest poi = BuildingShellCore.carve(
                    leaf, grid, topology, doodads, new Random(seed), CONFIG);
            assertNotNull(poi, "seed " + seed);

            int shopCells = 0;
            int stockCells = 0;
            int fixtures = 0;
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    RoomPurpose purpose = topology.getRoomPurpose(x, y);
                    if (purpose == RoomPurpose.SHOP_FLOOR) shopCells++;
                    if (purpose == RoomPurpose.STOCKROOM) stockCells++;
                    if (!topology.isFixture(x, y)) continue;
                    fixtures++;
                    assertFalse(grid.isWalkable(x, y), "fixture blocks movement at " + x + "," + y);
                    assertTrue(grid.isSeeThrough(x, y), "shelf preserves lines of fire at " + x + "," + y);
                    assertFalse(nearDoorway(grid, x, y), "fixture invades doorway clearance at " + x + "," + y);
                    assertTrue(hasHeavyDoodadAt(doodads, x, y), "fixture carries heavy shelf cover");
                }
            }
            assertTrue(shopCells >= 40, "seed " + seed + " sales floor cells=" + shopCells);
            assertTrue(stockCells >= 20, "seed " + seed + " stockroom cells=" + stockCells);
            assertTrue(fixtures >= 4, "seed " + seed + " shelf fixtures=" + fixtures);
            assertEquals(2, perimeterDoorways(grid, leaf), "public + service entrances, seed " + seed);

            int[] entrance = salesEntrance(grid, topology, leaf);
            assertNotNull(entrance, "sales entrance, seed " + seed);
            boolean[] reached = flood(grid, entrance[0], entrance[1], leaf);
            assertAllWalkableInteriorReached(grid, reached, leaf, seed);
            assertTrue(reachesPurpose(topology, reached, leaf, RoomPurpose.STOCKROOM),
                    "stockroom reachable from sales entrance, seed " + seed);

            assertTrue(hasTwoCellOpenLane(grid, topology, leaf, true),
                    "two-cell longitudinal fire lane, seed " + seed);
            assertTrue(hasTwoCellOpenLane(grid, topology, leaf, false),
                    "two-cell cross aisle/flank route, seed " + seed);

            topology.tagDefaultWalls(grid);
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    if (topology.isFixture(x, y)) {
                        assertFalse(topology.isWall(x, y), "fixture must not become structural wall");
                    }
                }
            }
        }
    }

    @Test
    void neighborhoodStoreBelowThresholdKeepsNonBlockingFurniture() {
        BlockLeaf leaf = new BlockLeaf(3, 3, 11, 10, false); // 9x8
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        BuildingShellCore.carve(leaf, grid, topology, new ArrayList<>(), new Random(4), CONFIG);
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                assertFalse(topology.isFixture(x, y), "small stores should not receive blocking racks");
            }
        }
    }

    @Test
    void compoundFrontageKeepsSalesFloorPublicAndStockroomAtRear() {
        BlockLeaf leaf = new BlockLeaf(3, 2, 17, 16, false); // 15x15 supports either split axis
        for (BuildingPlacement.Side frontage : BuildingPlacement.Side.values()) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            BuildingShellCore.carve(leaf, grid, topology, new ArrayList<>(), new Random(12),
                    CONFIG, new BuildingPlacement(frontage, true));

            assertTrue(hasDoorwayOnSide(grid, leaf, frontage),
                    "public entrance should face " + frontage);
            assertTrue(hasDoorwayOnSide(grid, leaf, frontage.opposite()),
                    "service entrance should oppose " + frontage);
            assertTrue(purposeCountOnInteriorEdge(topology, leaf, frontage, RoomPurpose.SHOP_FLOOR)
                            > purposeCountOnInteriorEdge(topology, leaf, frontage, RoomPurpose.STOCKROOM),
                    "sales floor should line public frontage " + frontage);
            assertTrue(purposeCountOnInteriorEdge(
                            topology, leaf, frontage.opposite(), RoomPurpose.STOCKROOM)
                            > purposeCountOnInteriorEdge(
                            topology, leaf, frontage.opposite(), RoomPurpose.SHOP_FLOOR),
                    "stockroom should line rear facade " + frontage.opposite());
        }
    }

    @Test
    void representativeUrbanBatchActuallyContainsTacticalStores() {
        long[] seeds = {1L, 42L, 100L, 777L, 1234L, 9999L};
        int mapsWithStores = 0;
        int fixtureCells = 0;
        BspCityGenerator generator = new BspCityGenerator();
        for (long seed : seeds) {
            MapResult map = generator.generate(80, 80, seed);
            int mapFixtures = 0;
            for (int y = 0; y < map.grid.getHeight(); y++) {
                for (int x = 0; x < map.grid.getWidth(); x++) {
                    if (map.topology.isFixture(x, y)) mapFixtures++;
                }
            }
            if (mapFixtures > 0) mapsWithStores++;
            fixtureCells += mapFixtures;
        }
        assertTrue(mapsWithStores >= 3,
                "tactical stores should appear in at least half the preview batch; maps=" + mapsWithStores);
        assertTrue(fixtureCells >= 20,
                "batch should contain a meaningful number of shelf footprints; cells=" + fixtureCells);
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static boolean nearDoorway(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.isDoorway(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    private static boolean hasHeavyDoodadAt(List<Doodad> doodads, int x, int y) {
        for (Doodad d : doodads) {
            if (d.cellX == x && d.cellY == y && d.cover == Doodad.COVER_HEAVY) return true;
        }
        return false;
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

    private static boolean hasDoorwayOnSide(NavigationGrid grid, BlockLeaf leaf,
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
            if (grid.isDoorway(x, y)) return true;
        }
        return false;
    }

    private static int purposeCountOnInteriorEdge(CellTopology topology, BlockLeaf leaf,
                                                   BuildingPlacement.Side side,
                                                   RoomPurpose purpose) {
        int count = 0;
        int min = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.left + 1 : leaf.top + 1;
        int max = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            int x = side == BuildingPlacement.Side.LEFT ? leaf.left + 1
                    : side == BuildingPlacement.Side.RIGHT ? leaf.right - 1 : along;
            int y = side == BuildingPlacement.Side.TOP ? leaf.top + 1
                    : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom - 1 : along;
            if (topology.getRoomPurpose(x, y) == purpose) count++;
        }
        return count;
    }

    private static int[] salesEntrance(NavigationGrid grid, CellTopology topology, BlockLeaf leaf) {
        for (int x = leaf.left + 1; x < leaf.right; x++) {
            if (grid.isDoorway(x, leaf.top)
                    && topology.getRoomPurpose(x, leaf.top + 1) == RoomPurpose.SHOP_FLOOR) return new int[]{x, leaf.top};
            if (grid.isDoorway(x, leaf.bottom)
                    && topology.getRoomPurpose(x, leaf.bottom - 1) == RoomPurpose.SHOP_FLOOR) return new int[]{x, leaf.bottom};
        }
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            if (grid.isDoorway(leaf.left, y)
                    && topology.getRoomPurpose(leaf.left + 1, y) == RoomPurpose.SHOP_FLOOR) return new int[]{leaf.left, y};
            if (grid.isDoorway(leaf.right, y)
                    && topology.getRoomPurpose(leaf.right - 1, y) == RoomPurpose.SHOP_FLOOR) return new int[]{leaf.right, y};
        }
        return null;
    }

    private static boolean[] flood(NavigationGrid grid, int startX, int startY, BlockLeaf leaf) {
        boolean[] seen = new boolean[W * H];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        seen[startY * W + startX] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            for (int[] d : dirs) {
                int x = p[0] + d[0];
                int y = p[1] + d[1];
                if (!leaf.contains(x, y) || !grid.isWalkable(x, y)) continue;
                int idx = y * W + x;
                if (seen[idx]) continue;
                seen[idx] = true;
                queue.addLast(new int[]{x, y});
            }
        }
        return seen;
    }

    private static void assertAllWalkableInteriorReached(NavigationGrid grid, boolean[] reached,
                                                          BlockLeaf leaf, long seed) {
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                if (grid.isWalkable(x, y)) {
                    assertTrue(reached[y * W + x], "unreachable interior at " + x + "," + y + ", seed " + seed);
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

    /** Vertical=true scans for two adjacent open rows; false scans for columns. */
    private static boolean hasTwoCellOpenLane(NavigationGrid grid, CellTopology topology,
                                              BlockLeaf leaf, boolean rows) {
        int outerMin = rows ? leaf.top + 1 : leaf.left + 1;
        int outerMax = rows ? leaf.bottom - 1 : leaf.right - 1;
        for (int a = outerMin; a < outerMax; a++) {
            if (openShopLine(grid, topology, leaf, rows, a)
                    && openShopLine(grid, topology, leaf, rows, a + 1)) return true;
        }
        return false;
    }

    private static boolean openShopLine(NavigationGrid grid, CellTopology topology,
                                        BlockLeaf leaf, boolean row, int fixed) {
        boolean sawShop = false;
        int min = row ? leaf.left + 1 : leaf.top + 1;
        int max = row ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            int x = row ? along : fixed;
            int y = row ? fixed : along;
            if (topology.getRoomPurpose(x, y) != RoomPurpose.SHOP_FLOOR) continue;
            sawShop = true;
            if (!grid.isWalkable(x, y)) return false;
        }
        return sawShop;
    }
}
