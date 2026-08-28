package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilitaryCompoundLayoutTest {

    private static final int W = 36;
    private static final int H = 36;

    private static final BlockLeaf COMMAND = new BlockLeaf(3, 3, 14, 14, false);
    private static final BlockLeaf BARRACKS = new BlockLeaf(20, 3, 31, 14, false);
    private static final BlockLeaf ARMORY = new BlockLeaf(3, 20, 14, 31, false);
    private static final BlockLeaf VEHICLE_BAY = new BlockLeaf(20, 20, 31, 31, false);
    private static final List<String> EXTERIOR_SERVICE_IDS = List.of(
            "doodad.industrial-generator", "doodad.industrial-cable-reel",
            "doodad.industrial-pallet-stack", "doodad.industrial-drum-cluster",
            "doodad.industrial-crate-stack", "doodad.industrial-pipe-bundle");

    @Test
    void roleBuildingsUseDistinctFixturesAndFaceTheSharedParadeGround() {
        GenContext ctx = filled(17L);

        assertTrue(count(ctx.doodads, "doodad.military-command-console") >= 4,
                "command and operations chambers need console banks, not token props");
        assertEquals(1, count(ctx.doodads, "doodad.military-tactical-table"),
                "the command chamber centers on one planning table");
        assertTrue(count(ctx.doodads, "doodad.military-bunk") >= 4);
        assertTrue(count(ctx.doodads, "doodad.industrial-crate-stack") >= 1);
        assertTrue(count(ctx.doodads, "doodad.industrial-generator") >= 1);
        assertTrue(count(ctx.doodads, "doodad.military-radar-dish") == 1);

        assertRolePurpose(ctx.topology, COMMAND, RoomPurpose.KEEP_THRONE);
        assertRolePurpose(ctx.topology, BARRACKS, RoomPurpose.BARRACKS);
        assertRolePurpose(ctx.topology, ARMORY, RoomPurpose.ARMORY);
        assertRolePurpose(ctx.topology, VEHICLE_BAY, RoomPurpose.VEHICLE_BAY);

        assertDoorPair(ctx.grid, inset(COMMAND), BuildingPlacement.Side.BOTTOM);
        assertDoorPair(ctx.grid, inset(BARRACKS), BuildingPlacement.Side.BOTTOM);
        assertDoorPair(ctx.grid, inset(ARMORY), BuildingPlacement.Side.TOP);
        assertDoorPair(ctx.grid, inset(VEHICLE_BAY), BuildingPlacement.Side.TOP);
    }

    @Test
    void commandBanksAddVisualDensityWithoutBarricadingTheOperationsFloor() {
        GenContext ctx = filled(17L);
        DoodadDef console = TileRegistry.installed().doodad("doodad.military-command-console");
        int banks = 0;
        for (Doodad doodad : ctx.doodads) {
            if (!matches(doodad, console) || !inset(COMMAND).contains(doodad.cellX, doodad.cellY)) {
                continue;
            }
            banks++;
            assertTrue(ctx.grid.isWalkable(doodad.cellX, doodad.cellY));
            assertFalse(ctx.topology.isFixture(doodad.cellX, doodad.cellY));
        }
        assertTrue(banks >= 4, "command post should read as a staffed operations floor");

        Doodad table = only(ctx.doodads, "doodad.military-tactical-table");
        assertTrue(ctx.topology.isFixture(table.cellX, table.cellY));
        assertFalse(ctx.grid.isWalkable(table.cellX, table.cellY));
    }

    @Test
    void radarUsesBroadCourtyardWithoutBlockingReservedRoadsOrDoorways() {
        GenContext ctx = filled(23L);
        Doodad radar = only(ctx.doodads, "doodad.military-radar-dish");
        boolean[][] reservation = ctx.get(BspKeys.ROAD_RESERVATION);

        assertFalse(reservation[radar.cellX][radar.cellY], "radar must stay off convoy reservation");
        assertFalse(inAnyBuilding(radar.cellX, radar.cellY, ctx.pois), "radar belongs outdoors");
        assertEquals(CellTopology.GroundKind.STONE,
                ctx.topology.getGroundKind(radar.cellX, radar.cellY));
        assertTrue(ctx.topology.isFixture(radar.cellX, radar.cellY));
        assertFalse(ctx.grid.isWalkable(radar.cellX, radar.cellY));
        assertTrue(ctx.grid.isSeeThrough(radar.cellX, radar.cellY));
        assertEquals(Doodad.COVER_HEAVY, radar.cover);
        assertFalse(nearDoorway(ctx.grid, radar.cellX, radar.cellY, 2));

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!reservation[x][y]) continue;
                assertTrue(ctx.grid.isWalkable(x, y), "reserved road blocked at " + x + "," + y);
                assertFalse(ctx.topology.isFixture(x, y), "fixture on reserved road at " + x + "," + y);
            }
        }
    }

    @Test
    void roleApronsReceiveReadableServiceClustersWithoutBecomingObstacles() {
        GenContext ctx = filled(23L);
        boolean[][] reservation = ctx.get(BspKeys.ROAD_RESERVATION);
        int exteriorProps = 0;

        for (Doodad doodad : ctx.doodads) {
            if (!matchesAny(doodad, EXTERIOR_SERVICE_IDS)
                    || inAnyBuilding(doodad.cellX, doodad.cellY, ctx.pois)) continue;
            exteriorProps++;
            assertEquals(CellTopology.GroundKind.STONE,
                    ctx.topology.getGroundKind(doodad.cellX, doodad.cellY));
            assertTrue(ctx.grid.isWalkable(doodad.cellX, doodad.cellY),
                    "service clutter remains visual so both apron lanes stay open");
            assertFalse(ctx.topology.isFixture(doodad.cellX, doodad.cellY));
            assertFalse(ctx.grid.isDoorway(doodad.cellX, doodad.cellY));
            assertFalse(reservation[doodad.cellX][doodad.cellY]);
            assertFalse(nearDoorway(ctx.grid, doodad.cellX, doodad.cellY, 2));
        }

        assertEquals(10, exteriorProps,
                "command, barracks, armory, and vehicle bay each receive a role-sized cluster");
    }

    @Test
    void fixturesRemainSeeThroughAndLeaveMarineSizedLanes() {
        GenContext ctx = filled(31L);
        int fixtures = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!ctx.topology.isFixture(x, y)) continue;
                fixtures++;
                assertFalse(ctx.grid.isWalkable(x, y));
                assertTrue(ctx.grid.isSeeThrough(x, y));
                assertFalse(ctx.topology.isWall(x, y));
            }
        }
        assertTrue(fixtures >= 12, "compound should contain meaningful tactical cover");
        assertTrue(hasTwoCellVerticalLane(ctx.grid, inset(BARRACKS)),
                "paired bunk rows should preserve a two-cell central lane");
        assertTrue(hasTwoCellLane(ctx.grid, inset(ARMORY)),
                "armory racks should preserve a two-cell fire lane");
        assertTrue(hasThreeByThreeOpenArea(ctx.grid, inset(VEHICLE_BAY)),
                "vehicle bay should retain an open service floor");
    }

    @Test
    void buildingsRetainTwoCellParadeGroundAprons() {
        GenContext ctx = filled(31L);
        for (BlockLeaf member : List.of(COMMAND, BARRACKS, ARMORY, VEHICLE_BAY)) {
            assertApron(ctx, member, CellTopology.GroundKind.STONE);
        }
    }

    @Test
    void fortifiedPerimeterReceivesPairedShootThroughApertures() {
        GenContext ctx = filled(31L);
        int windows = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!ctx.topology.isWindow(x, y) || ctx.grid.getWallHp(x, y) <= 0) continue;
                windows++;
                assertFalse(ctx.grid.isWalkable(x, y));
                assertTrue(ctx.grid.isSeeThrough(x, y));
                assertFalse(ctx.grid.isDoorway(x, y));
                assertFalse(ctx.topology.isVehicle(x, y),
                        "corner gun emplacements remain opaque hardpoints");
                assertTrue(hasAdjacentCompoundWindow(ctx.grid, ctx.topology, x, y),
                        "compound aperture must have an adjacent firing cell");
            }
        }
        assertTrue(windows >= 4,
                "fortified perimeter should offer distributed firing pairs");
        assertEquals(0, windows % 2, "compound apertures are emitted as pairs");
    }

    @Test
    void onlyGeneratedCommandPostIsMustHold() {
        GenContext ctx = filled(37L);
        TacticalNode command = ctx.tactical.stream()
                .filter(n -> n.kind == TacticalNode.Kind.COMMAND_POST)
                .findFirst()
                .orElseThrow();

        assertTrue(command.mustHold);
        assertTrue(ctx.tactical.stream()
                        .filter(n -> n != command)
                        .noneMatch(n -> n.mustHold),
                "barracks, armory, and vehicle-bay nodes remain ordinary");
    }

    private static GenContext filled(long seed) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        boolean[][] road = new boolean[W][H];
        boolean[][] reservation = new boolean[W][H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
                road[x][y] = true;
            }
        }
        for (int i = 0; i < W; i++) {
            reservation[17][i] = true;
            reservation[i][17] = true;
        }

        GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);
        ctx.put(BspKeys.ROAD_CELLS, road);
        ctx.put(BspKeys.ROAD_RESERVATION, reservation);
        new MilitaryBaseFiller().fill(compound(), ctx);
        return ctx;
    }

    private static Compound compound() {
        List<BlockLeaf> members = new ArrayList<>(List.of(COMMAND, BARRACKS, ARMORY, VEHICLE_BAY));
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(COMMAND, Compound.Role.COMMAND);
        roles.put(BARRACKS, Compound.Role.BARRACKS);
        roles.put(ARMORY, Compound.Role.ARMORY);
        roles.put(VEHICLE_BAY, Compound.Role.VEHICLE_BAY);
        return new Compound(BlockKind.MILITARY_BASE, COMMAND, members, roles, null);
    }

    private static BlockLeaf inset(BlockLeaf leaf) {
        return new BlockLeaf(leaf.left + MilitaryBaseFiller.BUILDING_SETBACK,
                leaf.top + MilitaryBaseFiller.BUILDING_SETBACK,
                leaf.right - MilitaryBaseFiller.BUILDING_SETBACK,
                leaf.bottom - MilitaryBaseFiller.BUILDING_SETBACK, false);
    }

    private static void assertApron(GenContext ctx, BlockLeaf member,
                                    CellTopology.GroundKind ground) {
        BlockLeaf building = inset(member);
        for (int y = member.top; y <= member.bottom; y++) {
            for (int x = member.left; x <= member.right; x++) {
                if (building.contains(x, y)) continue;
                assertEquals(ground, ctx.topology.getGroundKind(x, y),
                        "compound apron ground at " + x + "," + y);
                assertFalse(ctx.topology.isWall(x, y),
                        "compound apron cannot contain a structural wall at " + x + "," + y);
            }
        }
    }

    private static boolean hasAdjacentCompoundWindow(NavigationGrid grid,
                                                       CellTopology topology,
                                                       int x, int y) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int nx = x + direction[0];
            int ny = y + direction[1];
            if (grid.inBounds(nx, ny) && grid.getWallHp(nx, ny) > 0
                    && topology.isWindow(nx, ny)) return true;
        }
        return false;
    }

    private static int count(List<Doodad> doodads, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        int count = 0;
        for (Doodad doodad : doodads) if (matches(doodad, def)) count++;
        return count;
    }

    private static Doodad only(List<Doodad> doodads, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        Doodad found = null;
        for (Doodad doodad : doodads) {
            if (!matches(doodad, def)) continue;
            assertTrue(found == null, "more than one " + id);
            found = doodad;
        }
        assertTrue(found != null, "missing " + id);
        return found;
    }

    private static boolean matches(Doodad doodad, DoodadDef def) {
        return doodad.sheetPath.equals(def.sheetPath)
                && doodad.tile.col == def.col && doodad.tile.row == def.row;
    }

    private static boolean matchesAny(Doodad doodad, List<String> ids) {
        for (String id : ids) {
            if (matches(doodad, TileRegistry.installed().doodad(id))) return true;
        }
        return false;
    }

    private static void assertRolePurpose(CellTopology topology, BlockLeaf leaf, RoomPurpose purpose) {
        int count = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.getRoomPurpose(x, y) == purpose) count++;
            }
        }
        assertTrue(count >= 10, purpose + " should label its role building; cells=" + count);
    }

    private static void assertDoorPair(NavigationGrid grid, BlockLeaf leaf,
                                       BuildingPlacement.Side frontage) {
        assertTrue(hasDoor(grid, leaf, frontage), "missing frontage door on " + frontage);
        assertTrue(hasDoor(grid, leaf, frontage.opposite()), "missing opposed door");
    }

    private static boolean hasDoor(NavigationGrid grid, BlockLeaf leaf, BuildingPlacement.Side side) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (!grid.isDoorway(x, y)) continue;
                if (side == BuildingPlacement.Side.TOP && y == leaf.top) return true;
                if (side == BuildingPlacement.Side.BOTTOM && y == leaf.bottom) return true;
                if (side == BuildingPlacement.Side.LEFT && x == leaf.left) return true;
                if (side == BuildingPlacement.Side.RIGHT && x == leaf.right) return true;
            }
        }
        return false;
    }

    private static boolean inAnyBuilding(int x, int y, List<PointOfInterest> pois) {
        for (PointOfInterest poi : pois) {
            if (x >= poi.left && x <= poi.right && y >= poi.top && y <= poi.bottom) return true;
        }
        return false;
    }

    private static boolean nearDoorway(NavigationGrid grid, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (grid.isDoorway(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    private static boolean hasTwoCellVerticalLane(NavigationGrid grid, BlockLeaf leaf) {
        for (int x = leaf.left + 1; x < leaf.right - 1; x++) {
            boolean open = true;
            for (int y = leaf.top + 2; y <= leaf.bottom - 2; y++) {
                if (!grid.isWalkable(x, y) || !grid.isWalkable(x + 1, y)) open = false;
            }
            if (open) return true;
        }
        return false;
    }

    private static boolean hasTwoCellLane(NavigationGrid grid, BlockLeaf leaf) {
        if (hasTwoCellVerticalLane(grid, leaf)) return true;
        for (int y = leaf.top + 1; y < leaf.bottom - 1; y++) {
            boolean open = true;
            for (int x = leaf.left + 2; x <= leaf.right - 2; x++) {
                if (!grid.isWalkable(x, y) || !grid.isWalkable(x, y + 1)) open = false;
            }
            if (open) return true;
        }
        return false;
    }

    private static boolean hasThreeByThreeOpenArea(NavigationGrid grid, BlockLeaf leaf) {
        for (int y = leaf.top + 1; y <= leaf.bottom - 3; y++) {
            for (int x = leaf.left + 1; x <= leaf.right - 3; x++) {
                boolean open = true;
                for (int dy = 0; dy < 3; dy++) {
                    for (int dx = 0; dx < 3; dx++) {
                        if (!grid.isWalkable(x + dx, y + dy)) open = false;
                    }
                }
                if (open) return true;
            }
        }
        return false;
    }
}
