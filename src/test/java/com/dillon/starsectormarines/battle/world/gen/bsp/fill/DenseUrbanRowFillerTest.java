package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenseUrbanRowFillerTest {

    private static final int W = 24;
    private static final int H = 24;

    @Test
    void tacticalAlleyRetainsTwoMarineClearance() {
        assertTrue(DenseBlockFiller.TACTICAL_CLEAR_WIDTH
                >= 4f * UnitType.MARINE.radius);
    }

    @Test
    void qualifyingLotsProduceMixedUseRowsInBothOrientations() {
        verifyTacticalRows(new BlockLeaf(3, 4, 15, 13, false), true);
        verifyTacticalRows(new BlockLeaf(4, 3, 13, 15, false), false);
    }

    @Test
    void tacticalRowsAreSeedDeterministic() {
        BlockLeaf leaf = new BlockLeaf(3, 4, 15, 13, false);
        assertEquals(digest(generate(leaf, 91)), digest(generate(leaf, 91)));
    }

    @Test
    void smallerDenseLotsRetainCompactFourShellPattern() {
        BlockLeaf leaf = new BlockLeaf(3, 4, 14, 12, false); // 12x9
        Fixture fixture = generate(leaf, 31);

        assertFalse(DenseBlockFiller.qualifiesForTacticalRows(leaf));
        assertEquals(1, fixture.ctx.pois.size());
        assertEquals(0, countPurpose(fixture.topology, leaf, RoomPurpose.APARTMENT_LIVING));
        assertEquals(0, countPurpose(fixture.topology, leaf, RoomPurpose.SHOP_FLOOR));
        assertEquals(0, countWindows(fixture.topology, leaf));
        int midX = leaf.left + leaf.width() / 2;
        int midY = leaf.top + leaf.height() / 2;
        for (int y = leaf.top; y <= leaf.bottom; y++) assertTrue(fixture.grid.isWalkable(midX, y));
        for (int x = leaf.left; x <= leaf.right; x++) assertTrue(fixture.grid.isWalkable(x, midY));
    }

    @Test
    void representativeCitiesSurfaceTacticalDenseRows() {
        BspCityGenerator generator = new BspCityGenerator();
        int tenementRows = 0;
        for (long seed = 0; seed < 80; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (PointOfInterest poi : map.pointsOfInterest) {
                BlockLeaf bounds = new BlockLeaf(
                        poi.left, poi.top, poi.right, poi.bottom, false);
                if (countPurpose(map.topology, bounds, RoomPurpose.APARTMENT_LIVING) == 0
                        || countPurpose(map.topology, bounds, RoomPurpose.BEDROOM) == 0
                        || countPurpose(map.topology, bounds, RoomPurpose.RESIDENTIAL_HALL) > 0) {
                    continue;
                }
                tenementRows++;
            }
        }
        assertTrue(tenementRows >= 2,
                "large dense lots should surface tactical tenement rows; count=" + tenementRows);
    }

    private static void verifyTacticalRows(BlockLeaf leaf, boolean verticalAlley) {
        Fixture fixture = generate(leaf, 91);
        assertTrue(DenseBlockFiller.qualifiesForTacticalRows(leaf));
        assertEquals(2, fixture.ctx.pois.size());

        for (RoomPurpose purpose : new RoomPurpose[]{
                RoomPurpose.APARTMENT_LIVING, RoomPurpose.BEDROOM,
                RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM}) {
            assertTrue(countPurpose(fixture.topology, leaf, purpose) > 0,
                    "missing row room " + purpose);
            assertTrue(hasFixture(fixture.topology, leaf, purpose),
                    "missing row fixture " + purpose);
        }

        int alleyStart = verticalAlley
                ? leaf.left + (leaf.width() - DenseBlockFiller.TACTICAL_ALLEY_WIDTH) / 2
                : leaf.top + (leaf.height() - DenseBlockFiller.TACTICAL_ALLEY_WIDTH) / 2;
        int alleyEnd = alleyStart + DenseBlockFiller.TACTICAL_ALLEY_WIDTH - 1;
        if (verticalAlley) {
            BlockLeaf first = new BlockLeaf(leaf.left, leaf.top, alleyStart - 1, leaf.bottom, false);
            BlockLeaf second = new BlockLeaf(alleyEnd + 1, leaf.top, leaf.right, leaf.bottom, false);
            assertOpposedDoors(fixture.grid, first,
                    BuildingPlacement.Side.RIGHT, BuildingPlacement.Side.LEFT);
            assertOpposedDoors(fixture.grid, second,
                    BuildingPlacement.Side.LEFT, BuildingPlacement.Side.RIGHT);
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                int walkable = 0;
                for (int x = alleyStart; x <= alleyEnd; x++) {
                    if (fixture.grid.isWalkable(x, y)) walkable++;
                    assertEquals(CellTopology.GroundKind.STREET,
                            fixture.topology.getGroundKind(x, y));
                }
                assertTrue(walkable >= DenseBlockFiller.TACTICAL_CLEAR_WIDTH,
                        "alley pinched at y=" + y + ": "
                                + alleyWalkability(fixture.grid, alleyStart, alleyEnd, y, true));
            }
        } else {
            BlockLeaf first = new BlockLeaf(leaf.left, leaf.top, leaf.right, alleyStart - 1, false);
            BlockLeaf second = new BlockLeaf(leaf.left, alleyEnd + 1, leaf.right, leaf.bottom, false);
            assertOpposedDoors(fixture.grid, first,
                    BuildingPlacement.Side.BOTTOM, BuildingPlacement.Side.TOP);
            assertOpposedDoors(fixture.grid, second,
                    BuildingPlacement.Side.TOP, BuildingPlacement.Side.BOTTOM);
            for (int x = leaf.left; x <= leaf.right; x++) {
                int walkable = 0;
                for (int y = alleyStart; y <= alleyEnd; y++) {
                    if (fixture.grid.isWalkable(x, y)) walkable++;
                    assertEquals(CellTopology.GroundKind.STREET,
                            fixture.topology.getGroundKind(x, y));
                }
                assertTrue(walkable >= DenseBlockFiller.TACTICAL_CLEAR_WIDTH,
                        "alley pinched at x=" + x + ": "
                                + alleyWalkability(fixture.grid, alleyStart, alleyEnd, x, false));
            }
        }

        fixture.topology.tagDefaultWalls(fixture.grid);
        recomputeCover(fixture.grid);
        int windows = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (!fixture.topology.isWindow(x, y)) continue;
                windows++;
                assertFalse(fixture.grid.isWalkable(x, y));
                assertTrue(fixture.grid.isSeeThrough(x, y));
                assertWindowPurposeAndCover(fixture, x, y);
            }
        }
        assertTrue(windows >= 2);
    }

    private static String alleyWalkability(NavigationGrid grid,
                                             int crossMin, int crossMax,
                                             int along, boolean vertical) {
        StringBuilder result = new StringBuilder();
        for (int cross = crossMin; cross <= crossMax; cross++) {
            int x = vertical ? cross : along;
            int y = vertical ? along : cross;
            result.append(grid.isWalkable(x, y) ? '.' : '#');
        }
        return result.toString();
    }

    private static Fixture generate(BlockLeaf leaf, long seed) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);
        new DenseBlockFiller().fill(leaf, ctx);
        return new Fixture(grid, topology, ctx);
    }

    private static void assertOpposedDoors(NavigationGrid grid, BlockLeaf row,
                                           BuildingPlacement.Side frontage,
                                           BuildingPlacement.Side rear) {
        assertTrue(hasDoor(grid, row, frontage));
        assertTrue(hasDoor(grid, row, rear));
        assertEquals(2, perimeterDoorways(grid, row));
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

    private static boolean hasFixture(CellTopology topology, BlockLeaf leaf,
                                      RoomPurpose purpose) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.getRoomPurpose(x, y) == purpose && topology.isFixture(x, y)) {
                    return true;
                }
            }
        }
        return false;
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

    private static int countWindows(CellTopology topology, BlockLeaf leaf) {
        int count = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.isWindow(x, y)) count++;
            }
        }
        return count;
    }

    private static void recomputeCover(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.recomputeCoverAt(x, y);
        }
    }

    private static void assertWindowPurposeAndCover(Fixture fixture, int windowX, int windowY) {
        for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int insideX = windowX + direction[0];
            int insideY = windowY + direction[1];
            RoomPurpose purpose = fixture.topology.getRoomPurpose(insideX, insideY);
            if (purpose != RoomPurpose.BEDROOM && purpose != RoomPurpose.SHOP_FLOOR) continue;
            int outsideX = windowX - direction[0];
            int outsideY = windowY - direction[1];
            assertTrue(fixture.grid.hasLineOfSight(outsideX, outsideY, insideX, insideY));
            int facing = NavigationGrid.facingFor(-direction[0], -direction[1]);
            assertEquals(1, fixture.grid.getCoverAtFacing(insideX, insideY, facing));
            return;
        }
        throw new AssertionError("window does not face an eligible dense-row room");
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
        final GenContext ctx;

        Fixture(NavigationGrid grid, CellTopology topology, GenContext ctx) {
            this.grid = grid;
            this.topology = topology;
            this.ctx = ctx;
        }
    }
}
