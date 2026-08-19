package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneApartmentFillerTest {

    private static final int W = 24;
    private static final int H = 22;
    private static final BlockLeaf APARTMENT = new BlockLeaf(5, 5, 18, 16, false);

    @Test
    void apartmentHallFitsTwoMarinesAbreast() {
        assertTrue(BuildingLayouts.TACTICAL_AISLE_WIDTH >= 4f * UnitType.MARINE.radius);
    }

    @Test
    void qualifyingLotsFaceTheirStreetAndGainShootThroughWindows() {
        for (BuildingPlacement.Side frontage : BuildingPlacement.Side.values()) {
            Fixture fixture = generate(APARTMENT, frontage);

            assertTrue(hasDoor(fixture.grid, APARTMENT, frontage),
                    "street-facing entrance " + frontage);
            assertTrue(hasDoor(fixture.grid, APARTMENT, frontage.opposite()),
                    "opposed rear exit " + frontage);
            assertEquals(2, perimeterDoorways(fixture.grid, APARTMENT));
            assertTrue(countPurpose(fixture.topology, APARTMENT,
                    RoomPurpose.APARTMENT_LOBBY) > 0);
            assertTrue(countPurpose(fixture.topology, APARTMENT,
                    RoomPurpose.RESIDENTIAL_HALL) > 0);
            assertEquals(2, purposeComponents(fixture.topology, APARTMENT,
                    RoomPurpose.APARTMENT_LIVING));
            assertEquals(2, purposeComponents(fixture.topology, APARTMENT,
                    RoomPurpose.BEDROOM));

            fixture.topology.tagDefaultWalls(fixture.grid);
            recomputeCover(fixture.grid);
            int windows = 0;
            for (int y = APARTMENT.top; y <= APARTMENT.bottom; y++) {
                for (int x = APARTMENT.left; x <= APARTMENT.right; x++) {
                    if (!fixture.topology.isWindow(x, y)) continue;
                    windows++;
                    assertFalse(fixture.grid.isWalkable(x, y));
                    assertTrue(fixture.grid.isSeeThrough(x, y));
                    assertTrue(fixture.topology.isWall(x, y));
                    assertWindowHasFiringLane(fixture, x, y);
                }
            }
            assertTrue(windows >= 4, "apartment should expose several room firing points");
        }
    }

    @Test
    void undersizedResidentialLotsRetainCompactHomePlan() {
        BlockLeaf home = new BlockLeaf(5, 5, 15, 14, false); // 11x10
        Fixture fixture = generate(home, BuildingPlacement.Side.TOP);

        assertFalse(BuildingResidentialFiller.qualifiesForApartment(home));
        for (int y = home.top; y <= home.bottom; y++) {
            for (int x = home.left; x <= home.right; x++) {
                assertFalse(fixture.topology.isWindow(x, y));
                assertEquals(null, fixture.topology.getRoomPurpose(x, y));
            }
        }
    }

    @Test
    void representativeCitiesContainStandaloneApartmentBlocks() {
        BspCityGenerator generator = new BspCityGenerator();
        int standalone = 0;
        for (long seed = 0; seed < 80; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (PointOfInterest poi : map.pointsOfInterest) {
                if (poi.kind != PointOfInterest.Kind.RESIDENTIAL
                        || belongsToGatedHousing(poi, generator.getLastCompounds())) continue;
                BlockLeaf footprint = new BlockLeaf(
                        poi.left, poi.top, poi.right, poi.bottom, false);
                if (countPurpose(map.topology, footprint,
                        RoomPurpose.RESIDENTIAL_HALL) == 0) continue;
                standalone++;
                assertTrue(hasWindow(map.topology, footprint));
            }
        }
        assertTrue(standalone >= 2,
                "large ordinary residential lots should surface as apartments; count=" + standalone);
    }

    private static Fixture generate(BlockLeaf leaf, BuildingPlacement.Side frontage) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }
        boolean[][] roads = new boolean[W][H];
        markRoadEdge(roads, leaf, frontage);
        GenContext ctx = new GenContext(grid, topology, new Random(73), W, H, 73L);
        ctx.put(BspKeys.ROAD_CELLS, roads);
        new BuildingResidentialFiller().fill(leaf, ctx);
        return new Fixture(grid, topology);
    }

    private static void markRoadEdge(boolean[][] roads, BlockLeaf leaf,
                                     BuildingPlacement.Side side) {
        int min = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.left : leaf.top;
        int max = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.right : leaf.bottom;
        for (int along = min; along <= max; along++) {
            int x = side == BuildingPlacement.Side.LEFT ? leaf.left - 1
                    : side == BuildingPlacement.Side.RIGHT ? leaf.right + 1 : along;
            int y = side == BuildingPlacement.Side.TOP ? leaf.top - 1
                    : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom + 1 : along;
            roads[x][y] = true;
        }
    }

    private static boolean belongsToGatedHousing(PointOfInterest poi,
                                                 Iterable<Compound> compounds) {
        for (Compound compound : compounds) {
            if (compound.kind != BlockKind.GATED_HOUSING) continue;
            for (BlockLeaf member : compound.members) {
                if (poi.left == member.left + 1 && poi.top == member.top + 1
                        && poi.right == member.right - 1
                        && poi.bottom == member.bottom - 1) return true;
            }
        }
        return false;
    }

    private static boolean hasWindow(CellTopology topology, BlockLeaf leaf) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.isWindow(x, y)) return true;
            }
        }
        return false;
    }

    private static boolean hasDoor(NavigationGrid grid, BlockLeaf leaf,
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

    private static int countPurpose(CellTopology topology, BlockLeaf leaf,
                                    RoomPurpose purpose) {
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
        boolean[][] seen = new boolean[W][H];
        int components = 0;
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            for (int x = leaf.left + 1; x < leaf.right; x++) {
                if (seen[x][y] || topology.getRoomPurpose(x, y) != purpose) continue;
                components++;
                floodPurpose(topology, leaf, purpose, x, y, seen);
            }
        }
        return components;
    }

    private static void floodPurpose(CellTopology topology, BlockLeaf leaf,
                                     RoomPurpose purpose, int startX, int startY,
                                     boolean[][] seen) {
        int[] queueX = new int[W * H];
        int[] queueY = new int[W * H];
        int head = 0;
        int tail = 1;
        queueX[0] = startX;
        queueY[0] = startY;
        seen[startX][startY] = true;
        while (head < tail) {
            int x = queueX[head];
            int y = queueY[head++];
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + direction[0];
                int ny = y + direction[1];
                if (!leaf.contains(nx, ny) || seen[nx][ny]
                        || topology.getRoomPurpose(nx, ny) != purpose) continue;
                seen[nx][ny] = true;
                queueX[tail] = nx;
                queueY[tail++] = ny;
            }
        }
    }

    private static void recomputeCover(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.recomputeCoverAt(x, y);
        }
    }

    private static void assertWindowHasFiringLane(Fixture fixture, int windowX, int windowY) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int insideX = windowX + direction[0];
            int insideY = windowY + direction[1];
            RoomPurpose purpose = fixture.topology.getRoomPurpose(insideX, insideY);
            if (purpose != RoomPurpose.APARTMENT_LIVING && purpose != RoomPurpose.BEDROOM) continue;
            int outsideX = windowX - direction[0];
            int outsideY = windowY - direction[1];
            assertTrue(fixture.grid.hasLineOfSight(outsideX, outsideY, insideX, insideY),
                    "shots cross window at " + windowX + "," + windowY);
            int facing = NavigationGrid.facingFor(-direction[0], -direction[1]);
            assertEquals(1, fixture.grid.getCoverAtFacing(insideX, insideY, facing),
                    "interior firing cell receives facade cover");
            return;
        }
        throw new AssertionError("window is not aligned with a private room");
    }

    private static final class Fixture {
        final NavigationGrid grid;
        final CellTopology topology;

        Fixture(NavigationGrid grid, CellTopology topology) {
            this.grid = grid;
            this.topology = topology;
        }
    }
}
