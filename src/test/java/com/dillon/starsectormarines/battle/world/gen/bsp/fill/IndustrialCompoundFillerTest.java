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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustrialCompoundFillerTest {

    private static final int W = 40;
    private static final int H = 34;
    private static final BlockLeaf FACTORY = new BlockLeaf(2, 2, 18, 14, false);
    private static final BlockLeaf YARD = new BlockLeaf(22, 2, 34, 14, false);
    private static final BlockLeaf UTILITY = new BlockLeaf(2, 18, 13, 28, false);

    @Test
    void yardGateFitsTwoMarinesAbreast() {
        assertTrue(IndustrialCompoundFiller.GATE_WIDTH >= 4f * UnitType.MARINE.radius);
    }

    @Test
    void factoryYardAndUtilityFaceSharedApronWithoutBlockingVehicleLane() {
        Fixture fixture = generate(83L);

        assertDoorwaysOnOpposedSides(fixture.grid, FACTORY,
                BuildingPlacement.Side.BOTTOM, BuildingPlacement.Side.TOP);
        assertDoorwaysOnOpposedSides(fixture.grid, UTILITY,
                BuildingPlacement.Side.TOP, BuildingPlacement.Side.BOTTOM);
        int factoryDoor = doorwayAlong(fixture.grid, FACTORY, BuildingPlacement.Side.BOTTOM);
        assertEquals(RoomPurpose.INDUSTRIAL_SPINE,
                inwardPurpose(fixture.topology, FACTORY,
                        BuildingPlacement.Side.BOTTOM, factoryDoor));
        assertTrue(countPurpose(fixture.topology, FACTORY, RoomPurpose.PRODUCTION_FLOOR) > 0);
        assertTrue(countPurpose(fixture.topology, FACTORY, RoomPurpose.CONTROL_ROOM) > 0);
        assertTrue(countPurpose(fixture.topology, FACTORY, RoomPurpose.PARTS_CAGE) > 0);

        int yardGates = 0;
        int fenceCells = 0;
        int yardEquipment = 0;
        for (int y = YARD.top; y <= YARD.bottom; y++) {
            for (int x = YARD.left; x <= YARD.right; x++) {
                boolean perimeter = x == YARD.left || x == YARD.right
                        || y == YARD.top || y == YARD.bottom;
                if (perimeter && fixture.grid.isDoorway(x, y)) yardGates++;
                if (perimeter && fixture.topology.isFixture(x, y)) {
                    fenceCells++;
                    assertFalse(fixture.grid.isWalkable(x, y));
                    assertTrue(fixture.grid.isSeeThrough(x, y), "chain link preserves LOS");
                    assertFalse(fixture.topology.isWall(x, y), "fence remains a prop fixture");
                }
                if (!perimeter && fixture.topology.isFixture(x, y)) yardEquipment++;
            }
        }
        assertEquals(2, yardGates, "yard receives a two-cell apron-facing gate");
        assertEquals(46, fenceCells, "yard perimeter is fenced except for its gate");
        assertTrue(yardEquipment >= 5, "yard receives a tactical work-zone grammar");
        for (int y = YARD.top + 1; y < YARD.bottom; y++) {
            if (!fixture.grid.isDoorway(YARD.left, y)) continue;
            for (int depth = 0; depth <= 2; depth++) {
                assertTrue(fixture.grid.isWalkable(YARD.left + depth, y),
                        "yard gate requires a clear marine-width entrance throat");
                assertFalse(fixture.topology.isFixture(YARD.left + depth, y),
                        "yard equipment cannot seal the gate approach");
            }
        }

        for (int y = 2; y <= 14; y++) {
            assertReservedStreet(fixture, 20, y);
            assertEquals(CellTopology.GroundKind.STRIPED,
                    fixture.topology.getGroundKind(19, y));
            assertEquals(CellTopology.GroundKind.STRIPED,
                    fixture.topology.getGroundKind(21, y));
        }
        for (int x = 2; x <= 13; x++) {
            assertReservedStreet(fixture, x, 16);
            assertEquals(CellTopology.GroundKind.STRIPED,
                    fixture.topology.getGroundKind(x, 15));
            assertEquals(CellTopology.GroundKind.STRIPED,
                    fixture.topology.getGroundKind(x, 17));
        }
        for (Doodad doodad : fixture.ctx.doodads) {
            assertFalse(fixture.reserved[doodad.cellX][doodad.cellY],
                    "compound prop cannot occupy reserved vehicle lane");
        }
    }

    @Test
    void compoundLayoutIsDeterministic() {
        assertEquals(digest(generate(991L)), digest(generate(991L)));
    }

    @Test
    void representativeCitiesClaimIndustrialCompoundsWithTacticalFactories() {
        BspCityGenerator generator = new BspCityGenerator();
        int compounds = 0;
        int tacticalFactories = 0;
        for (long seed = 0; seed < 60; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (Compound compound : generator.getLastCompounds()) {
                if (compound.kind != BlockKind.INDUSTRIAL_COMPOUND) continue;
                compounds++;
                if (countPurpose(map.topology, compound.seed,
                        RoomPurpose.PRODUCTION_FLOOR) > 0) tacticalFactories++;
            }
        }
        assertTrue(compounds >= 2, "industrial compounds should remain visible; count=" + compounds);
        assertEquals(compounds, tacticalFactories,
                "every claimed industrial site must retain its qualifying factory seed");
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
        for (int y = 2; y <= 14; y++) {
            for (int x = 19; x <= 21; x++) road[x][y] = true;
            reserved[20][y] = true;
        }
        for (int x = 2; x <= 18; x++) {
            for (int y = 15; y <= 17; y++) road[x][y] = true;
            reserved[x][16] = true;
        }

        List<BlockLeaf> members = new ArrayList<>(List.of(FACTORY, YARD, UTILITY));
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(FACTORY, Compound.Role.COMMAND);
        roles.put(YARD, Compound.Role.BARRACKS);
        roles.put(UTILITY, Compound.Role.ARMORY);
        Compound compound = new Compound(
                BlockKind.INDUSTRIAL_COMPOUND, FACTORY, members, roles, null);
        GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);
        ctx.put(BspKeys.ROAD_CELLS, road);
        ctx.put(BspKeys.ROAD_RESERVATION, reserved);
        new IndustrialCompoundFiller().fill(compound, ctx);
        return new Fixture(grid, topology, road, reserved, ctx);
    }

    private static void assertDoorwaysOnOpposedSides(NavigationGrid grid, BlockLeaf leaf,
                                                      BuildingPlacement.Side first,
                                                      BuildingPlacement.Side second) {
        assertTrue(hasDoorway(grid, leaf, first), "missing doorway on " + first);
        assertTrue(hasDoorway(grid, leaf, second), "missing doorway on " + second);
        assertEquals(2, perimeterDoorwayCount(grid, leaf));
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

    private static int countPurpose(CellTopology topology, BlockLeaf leaf, RoomPurpose purpose) {
        int count = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.getRoomPurpose(x, y) == purpose) count++;
            }
        }
        return count;
    }

    private static void assertReservedStreet(Fixture fixture, int x, int y) {
        assertTrue(fixture.grid.isWalkable(x, y));
        assertFalse(fixture.topology.isFixture(x, y));
        assertEquals(CellTopology.GroundKind.STREET,
                fixture.topology.getGroundKind(x, y));
    }

    private static String digest(Fixture fixture) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                out.append(fixture.grid.isWalkable(x, y) ? '1' : '0');
                out.append(fixture.grid.isSeeThrough(x, y) ? 'T' : 'O');
                out.append(fixture.grid.isDoorway(x, y) ? 'D' : '-');
                out.append(fixture.topology.isFixture(x, y) ? 'F' : '-');
                out.append(fixture.topology.getGroundKind(x, y).ordinal()).append(';');
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
        final boolean[][] road;
        final boolean[][] reserved;
        final GenContext ctx;

        Fixture(NavigationGrid grid, CellTopology topology,
                boolean[][] road, boolean[][] reserved, GenContext ctx) {
            this.grid = grid;
            this.topology = topology;
            this.road = road;
            this.reserved = reserved;
            this.ctx = ctx;
        }
    }
}
