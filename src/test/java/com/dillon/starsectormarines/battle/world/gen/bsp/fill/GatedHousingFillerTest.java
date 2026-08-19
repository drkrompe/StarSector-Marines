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
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatedHousingFillerTest {

    private static final int W = 48;
    private static final int H = 38;
    private static final BlockLeaf MAIN = new BlockLeaf(2, 2, 17, 15, false);
    private static final BlockLeaf SECONDARY = new BlockLeaf(22, 2, 36, 15, false);
    private static final BlockLeaf OUTBUILDING = new BlockLeaf(2, 20, 13, 31, false);

    @Test
    void mainGateFitsTwoMarinesAbreast() {
        assertTrue(GatedHousingFiller.GATE_WIDTH >= 4f * UnitType.MARINE.radius);
    }

    @Test
    void apartmentBlocksFaceSharedCourtyardAndPreserveOpenHallways() {
        Fixture fixture = generate(117L);
        BlockLeaf mainInset = inset(MAIN);
        BlockLeaf secondaryInset = inset(SECONDARY);

        assertTrue(hasDoorway(fixture.grid, mainInset, BuildingPlacement.Side.BOTTOM));
        assertTrue(hasDoorway(fixture.grid, mainInset, BuildingPlacement.Side.TOP));
        assertTrue(hasDoorway(fixture.grid, secondaryInset, BuildingPlacement.Side.LEFT));
        assertTrue(hasDoorway(fixture.grid, secondaryInset, BuildingPlacement.Side.RIGHT));
        assertEquals(2, perimeterDoorwayCount(fixture.grid, mainInset));
        assertEquals(2, perimeterDoorwayCount(fixture.grid, secondaryInset));

        for (BlockLeaf apartment : List.of(mainInset, secondaryInset)) {
            assertTrue(countPurpose(fixture.topology, apartment,
                    RoomPurpose.APARTMENT_LOBBY) > 0);
            assertTrue(countPurpose(fixture.topology, apartment,
                    RoomPurpose.RESIDENTIAL_HALL) > 0);
            assertTrue(countPurpose(fixture.topology, apartment,
                    RoomPurpose.APARTMENT_LIVING) > 0);
            assertTrue(countPurpose(fixture.topology, apartment,
                    RoomPurpose.BEDROOM) > 0);
            assertTrue(countFixtures(fixture.topology, apartment,
                    RoomPurpose.APARTMENT_LIVING) >= 2);
            assertTrue(countFixtures(fixture.topology, apartment,
                    RoomPurpose.BEDROOM) >= 2);
            assertPurposeIsClear(fixture, apartment, RoomPurpose.RESIDENTIAL_HALL);
        }

        int courtyardPlanters = 0;
        int outerGateCells = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (fixture.grid.isDoorway(x, y)
                        && fixture.topology.getGroundKind(x, y) == CellTopology.GroundKind.STONE) {
                    outerGateCells++;
                }
                if (fixture.topology.getGroundKind(x, y) != CellTopology.GroundKind.COURTYARD
                        || !fixture.topology.isFixture(x, y)) continue;
                courtyardPlanters++;
                assertFalse(fixture.grid.isWalkable(x, y));
                assertTrue(fixture.grid.isSeeThrough(x, y));
                assertFalse(fixture.topology.isWall(x, y));
                assertFalse(fixture.reserved[x][y]);
            }
        }
        assertEquals(2, outerGateCells, "compound receives one two-cell public gate");
        assertTrue(courtyardPlanters >= 1, "shared courtyard receives tactical planter cover");
        assertAllWalkableCellsConnected(fixture.grid);

        for (Doodad doodad : fixture.ctx.doodads) {
            assertFalse(fixture.reserved[doodad.cellX][doodad.cellY],
                    "residential prop cannot occupy the vehicle reservation");
        }
    }

    @Test
    void compoundLayoutIsDeterministic() {
        assertEquals(digest(generate(771L)), digest(generate(771L)));
    }

    @Test
    void representativeCitiesClaimRoomedResidentialCompounds() {
        BspCityGenerator generator = new BspCityGenerator();
        int compounds = 0;
        int roomedSeeds = 0;
        for (long seed = 0; seed < 60; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (Compound compound : generator.getLastCompounds()) {
                if (compound.kind != BlockKind.GATED_HOUSING) continue;
                compounds++;
                if (countPurpose(map.topology, inset(compound.seed),
                        RoomPurpose.RESIDENTIAL_HALL) > 0) roomedSeeds++;
            }
        }
        assertTrue(compounds >= 2,
                "residential compounds should remain visible; count=" + compounds);
        assertEquals(compounds, roomedSeeds,
                "every claimed gated-housing seed must retain its qualifying apartment plan");
    }

    private static Fixture generate(long seed) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }

        boolean[][] road = new boolean[W][H];
        boolean[][] reserved = new boolean[W][H];
        for (int y = 2; y <= 15; y++) {
            for (int x = 18; x <= 21; x++) road[x][y] = true;
            reserved[20][y] = true;
        }
        for (int x = 2; x <= 17; x++) {
            for (int y = 16; y <= 19; y++) road[x][y] = true;
            reserved[x][18] = true;
        }
        for (int y = 5; y <= 12; y++) {
            road[0][y] = true;
            road[1][y] = true;
        }

        List<BlockLeaf> members = new ArrayList<>(List.of(MAIN, SECONDARY, OUTBUILDING));
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(MAIN, Compound.Role.COMMAND);
        roles.put(SECONDARY, Compound.Role.BARRACKS);
        roles.put(OUTBUILDING, Compound.Role.ARMORY);
        Compound compound = new Compound(
                BlockKind.GATED_HOUSING, MAIN, members, roles, null);
        GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);
        ctx.put(BspKeys.ROAD_CELLS, road);
        ctx.put(BspKeys.ROAD_RESERVATION, reserved);
        new GatedHousingFiller().fill(compound, ctx);
        return new Fixture(grid, topology, reserved, ctx);
    }

    private static BlockLeaf inset(BlockLeaf leaf) {
        return new BlockLeaf(leaf.left + 1, leaf.top + 1,
                leaf.right - 1, leaf.bottom - 1, false);
    }

    private static boolean hasDoorway(NavigationGrid grid, BlockLeaf leaf,
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

    private static int perimeterDoorwayCount(NavigationGrid grid, BlockLeaf leaf) {
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
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.getRoomPurpose(x, y) == purpose) count++;
            }
        }
        return count;
    }

    private static int countFixtures(CellTopology topology, BlockLeaf leaf,
                                     RoomPurpose purpose) {
        int count = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.getRoomPurpose(x, y) == purpose
                        && topology.isFixture(x, y)) count++;
            }
        }
        return count;
    }

    private static void assertPurposeIsClear(Fixture fixture, BlockLeaf leaf,
                                             RoomPurpose purpose) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (fixture.topology.getRoomPurpose(x, y) != purpose) continue;
                assertTrue(fixture.grid.isWalkable(x, y), "common hall must remain walkable");
                assertFalse(fixture.topology.isFixture(x, y), "common hall cannot contain fixtures");
            }
        }
    }

    private static void assertAllWalkableCellsConnected(NavigationGrid grid) {
        boolean[][] seen = new boolean[grid.getWidth()][grid.getHeight()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        outer:
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                seen[x][y] = true;
                queue.add(new int[]{x, y});
                break outer;
            }
        }
        int reached = 0;
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            reached++;
            int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] direction : directions) {
                int nx = cell[0] + direction[0];
                int ny = cell[1] + direction[1];
                if (!grid.inBounds(nx, ny) || seen[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                seen[nx][ny] = true;
                queue.addLast(new int[]{nx, ny});
            }
        }
        int total = 0;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (grid.isWalkable(x, y)) total++;
            }
        }
        assertEquals(total, reached, "residential compound cannot partition walkable space");
    }

    private static String digest(Fixture fixture) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                out.append(fixture.grid.isWalkable(x, y) ? '1' : '0');
                out.append(fixture.grid.isSeeThrough(x, y) ? 'T' : 'O');
                out.append(fixture.grid.isDoorway(x, y) ? 'D' : '-');
                out.append(fixture.topology.isFixture(x, y) ? 'F' : '-');
                RoomPurpose purpose = fixture.topology.getRoomPurpose(x, y);
                out.append(purpose == null ? -1 : purpose.ordinal()).append(';');
            }
        }
        for (Doodad doodad : fixture.ctx.doodads) {
            out.append(doodad.cellX).append(':').append(doodad.cellY).append(':')
                    .append(doodad.tile.col).append(':').append(doodad.tile.row).append('|');
        }
        return out.toString();
    }

    private static final class Fixture {
        final NavigationGrid grid;
        final CellTopology topology;
        final boolean[][] reserved;
        final GenContext ctx;

        Fixture(NavigationGrid grid, CellTopology topology,
                boolean[][] reserved, GenContext ctx) {
            this.grid = grid;
            this.topology = topology;
            this.reserved = reserved;
            this.ctx = ctx;
        }
    }
}
