package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundClaim;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
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

class MedicalCampusFillerTest {

    private static final int W = 48;
    private static final int H = 40;
    private static final BlockLeaf CLINIC = new BlockLeaf(5, 5, 19, 16, false);
    private static final BlockLeaf SUPPORT = new BlockLeaf(23, 5, 36, 18, false);
    private static final BlockLeaf COURT = new BlockLeaf(23, 22, 34, 31, false);

    @Test
    void clinicPlanSupportsEveryFrontageAtInfantryScale() {
        assertTrue(BuildingLayouts.TACTICAL_AISLE_WIDTH >= 4f * UnitType.MARINE.radius);
        for (BuildingPlacement.Side frontage : BuildingPlacement.Side.values()) {
            Fixture fixture = emptyFixture(71);
            PointOfInterest poi = BuildingShellCore.carve(
                    CLINIC, fixture.grid, fixture.topology, fixture.ctx.doodads,
                    fixture.ctx.rng, MedicalCampusFiller.CLINIC_CONFIG,
                    new BuildingPlacement(frontage, true));

            assertEquals(PointOfInterest.Kind.MEDICAL, poi.kind);
            assertTrue(hasDoor(fixture.grid, CLINIC, frontage));
            assertTrue(hasDoor(fixture.grid, CLINIC, frontage.opposite()));
            assertEquals(2, perimeterDoorways(fixture.grid, CLINIC));
            for (RoomPurpose purpose : List.of(
                    RoomPurpose.MEDICAL_RECEPTION,
                    RoomPurpose.MEDICAL_CORRIDOR,
                    RoomPurpose.TRIAGE,
                    RoomPurpose.TREATMENT_ROOM,
                    RoomPurpose.PATIENT_WARD,
                    RoomPurpose.PHARMACY)) {
                assertTrue(countPurpose(fixture.topology, CLINIC, purpose) > 0,
                        "missing room purpose " + purpose + " for " + frontage);
            }
            assertClearCorridor(fixture);
            assertClinicalFixtures(fixture);
            assertMedicalWindows(fixture);
        }
    }

    @Test
    void campusFacesSharedAmbulanceApronAndPreservesVehicleLane() {
        Fixture fixture = generateCampus(83);

        assertTrue(hasDoor(fixture.grid, CLINIC, BuildingPlacement.Side.RIGHT));
        assertTrue(hasDoor(fixture.grid, SUPPORT, BuildingPlacement.Side.LEFT)
                        || hasDoor(fixture.grid, SUPPORT, BuildingPlacement.Side.BOTTOM),
                "support entrance should face one of its shared apron edges");
        assertEquals(CellTopology.GroundKind.STRIPED,
                fixture.topology.getGroundKind(20, 10));
        assertEquals(CellTopology.GroundKind.STREET,
                fixture.topology.getGroundKind(21, 10));
        assertTrue(fixture.grid.isWalkable(21, 10));
        assertFalse(fixture.topology.isFixture(21, 10));

        long medicalPois = fixture.ctx.pois.stream()
                .filter(p -> p.kind == PointOfInterest.Kind.MEDICAL)
                .count();
        assertEquals(2, medicalPois);

        int courtFixtures = 0;
        for (int y = COURT.top; y <= COURT.bottom; y++) {
            for (int x = COURT.left; x <= COURT.right; x++) {
                assertEquals(CellTopology.GroundKind.STRIPED,
                        fixture.topology.getGroundKind(x, y));
                if (fixture.topology.isFixture(x, y)) courtFixtures++;
            }
        }
        assertEquals(3, courtFixtures);
        for (int y = COURT.top + 2; y <= COURT.bottom - 2; y++) {
            for (int x = COURT.left + 3; x <= COURT.right - 3; x++) {
                assertTrue(fixture.grid.isWalkable(x, y),
                        "ambulance maneuver lane blocked at " + x + "," + y);
            }
        }
    }

    @Test
    void medicalClaimRequiresExactlyThreeParcelsAndDemotesFailure() {
        BlockLeaf seed = new BlockLeaf(0, 0, 14, 11, false);
        seed.kind = BlockKind.MEDICAL_CAMPUS;
        BlockLeaf first = new BlockLeaf(18, 0, 29, 11, false);
        first.kind = BlockKind.BUILDING_COMMERCIAL;
        BlockLeaf second = new BlockLeaf(18, 15, 29, 26, false);
        second.kind = BlockKind.PLAZA;
        Map<BlockLeaf, List<BlockLeaf>> adjacency = new IdentityHashMap<>();
        adjacency.put(seed, new ArrayList<>(List.of(first)));
        adjacency.put(first, new ArrayList<>(List.of(seed, second)));
        adjacency.put(second, new ArrayList<>(List.of(first)));

        List<Compound> claimed = CompoundClaim.claim(
                List.of(seed, first, second), adjacency,
                CompoundClaim.DEFAULT_SPECS, new Random(4));
        assertEquals(1, claimed.size());
        assertEquals(BlockKind.MEDICAL_CAMPUS, claimed.get(0).kind);
        assertEquals(3, claimed.get(0).members.size());

        BlockLeaf failed = new BlockLeaf(0, 0, 14, 11, false);
        failed.kind = BlockKind.MEDICAL_CAMPUS;
        BlockLeaf onlyNeighbor = new BlockLeaf(18, 0, 29, 11, false);
        onlyNeighbor.kind = BlockKind.BUILDING_COMMERCIAL;
        Map<BlockLeaf, List<BlockLeaf>> shortAdjacency = new IdentityHashMap<>();
        shortAdjacency.put(failed, new ArrayList<>(List.of(onlyNeighbor)));
        shortAdjacency.put(onlyNeighbor, new ArrayList<>(List.of(failed)));
        assertTrue(CompoundClaim.claim(
                List.of(failed, onlyNeighbor), shortAdjacency,
                CompoundClaim.DEFAULT_SPECS, new Random(4)).isEmpty());
        assertEquals(BlockKind.BUILDING_CIVIC, failed.kind);
    }

    @Test
    void representativeCitiesSurfaceMedicalCampuses() {
        BspCityGenerator generator = new BspCityGenerator();
        int campuses = 0;
        for (long seed = 0; seed < 60; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (Compound compound : generator.getLastCompounds()) {
                if (compound.kind != BlockKind.MEDICAL_CAMPUS) continue;
                campuses++;
                assertEquals(3, compound.members.size());
                assertTrue(countPurpose(map.topology, compound.seed,
                        RoomPurpose.MEDICAL_CORRIDOR) > 0);
            }
        }
        assertTrue(campuses >= 2,
                "medical campuses should be visible in representative cities; count=" + campuses);
    }

    private static Fixture generateCampus(long seed) {
        Fixture fixture = emptyFixture(seed);
        for (int y = 3; y <= 20; y++) {
            for (int x = 20; x <= 22; x++) fixture.roads[x][y] = true;
            fixture.reserved[21][y] = true;
        }
        for (int x = 21; x <= 38; x++) {
            for (int y = 19; y <= 21; y++) fixture.roads[x][y] = true;
            fixture.reserved[x][20] = true;
        }
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(CLINIC, Compound.Role.COMMAND);
        roles.put(SUPPORT, Compound.Role.BARRACKS);
        roles.put(COURT, Compound.Role.ARMORY);
        Compound compound = new Compound(BlockKind.MEDICAL_CAMPUS, CLINIC,
                new ArrayList<>(List.of(CLINIC, SUPPORT, COURT)), roles, null);
        new MedicalCampusFiller().fill(compound, fixture.ctx);
        return fixture;
    }

    private static Fixture emptyFixture(long seed) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }
        boolean[][] roads = new boolean[W][H];
        boolean[][] reserved = new boolean[W][H];
        GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);
        ctx.put(BspKeys.ROAD_CELLS, roads);
        ctx.put(BspKeys.ROAD_RESERVATION, reserved);
        return new Fixture(grid, topology, roads, reserved, ctx);
    }

    private static void assertClearCorridor(Fixture fixture) {
        int count = 0;
        for (int y = CLINIC.top + 1; y < CLINIC.bottom; y++) {
            for (int x = CLINIC.left + 1; x < CLINIC.right; x++) {
                if (fixture.topology.getRoomPurpose(x, y) != RoomPurpose.MEDICAL_CORRIDOR) continue;
                count++;
                assertTrue(fixture.grid.isWalkable(x, y));
                assertFalse(fixture.topology.isFixture(x, y));
            }
        }
        assertTrue(count >= 2 * (Math.min(CLINIC.width(), CLINIC.height()) - 2));
    }

    private static void assertClinicalFixtures(Fixture fixture) {
        for (RoomPurpose purpose : List.of(
                RoomPurpose.MEDICAL_RECEPTION,
                RoomPurpose.TRIAGE,
                RoomPurpose.TREATMENT_ROOM,
                RoomPurpose.PATIENT_WARD,
                RoomPurpose.PHARMACY)) {
            boolean found = false;
            for (int y = CLINIC.top + 1; y < CLINIC.bottom; y++) {
                for (int x = CLINIC.left + 1; x < CLINIC.right; x++) {
                    if (fixture.topology.getRoomPurpose(x, y) == purpose
                            && fixture.topology.isFixture(x, y)) found = true;
                }
            }
            assertTrue(found, "missing fixture in " + purpose);
        }
    }

    private static void assertMedicalWindows(Fixture fixture) {
        fixture.topology.tagDefaultWalls(fixture.grid);
        recomputeCover(fixture.grid);
        var windows = BuildingWindowTestSupport.windowsOwnedBy(fixture.grid, CLINIC);
        for (SharedEdgeBarrier window : windows) {
            int ownerX = window.structureCellX();
            int ownerY = window.structureCellY();
            assertTrue(fixture.grid.isWalkable(ownerX, ownerY));
            assertFalse(fixture.topology.isWindow(ownerX, ownerY));
            assertWindowHasFiringLane(fixture, window);
        }
        assertTrue(windows.size() >= 2);
    }

    private static void assertWindowHasFiringLane(Fixture fixture,
                                                  SharedEdgeBarrier window) {
        int windowX = window.structureCellX();
        int windowY = window.structureCellY();
        Direction outward = BuildingWindowTestSupport.outwardFromOwner(window);
        int insideX = windowX - outward.dx;
        int insideY = windowY - outward.dy;
        RoomPurpose purpose = fixture.topology.getRoomPurpose(insideX, insideY);
        assertTrue(purpose == RoomPurpose.TREATMENT_ROOM
                || purpose == RoomPurpose.PATIENT_WARD);
        int outsideX = windowX + outward.dx;
        int outsideY = windowY + outward.dy;
        assertTrue(fixture.grid.hasLineOfSight(outsideX, outsideY, insideX, insideY));
        int facing = NavigationGrid.facingFor(outward.dx, outward.dy);
        assertEquals(1, fixture.grid.getCoverAtFacing(windowX, windowY, facing));
    }

    private static void recomputeCover(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.recomputeCoverAt(x, y);
        }
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
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (topology.getRoomPurpose(x, y) == purpose) count++;
            }
        }
        return count;
    }

    private static final class Fixture {
        final NavigationGrid grid;
        final CellTopology topology;
        final boolean[][] roads;
        final boolean[][] reserved;
        final GenContext ctx;

        Fixture(NavigationGrid grid, CellTopology topology,
                boolean[][] roads, boolean[][] reserved, GenContext ctx) {
            this.grid = grid;
            this.topology = topology;
            this.roads = roads;
            this.reserved = reserved;
            this.ctx = ctx;
        }
    }
}
