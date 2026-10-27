package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The blade's clearing rule, its breach view and one hull's push, each on a hand-laid grid. */
class DozerBladeTest {

    private static final int SIZE = 20;

    @Test void pushesOrdinaryWallsAndRefusesEverythingElse() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 1, 1, 100);
        wall(grid, topology, 2, 1, 80);
        wall(grid, topology, 3, 1, 101);
        wall(grid, topology, 4, 1, 150);
        wall(grid, topology, 5, 1, 0);
        wall(grid, topology, 6, 1, 100);
        grid.setTag(6, 1, NavigationGrid.CellTag.NATURAL_SOLID, true);
        wall(grid, topology, 7, 1, 100);
        topology.setTag(7, 1, CellTopology.Tag.FIXTURE, true);
        // Unwalkable and carrying structure, but not a wall: water, a hull's footprint.
        grid.setWalkable(8, 1, false);
        grid.setWallHp(8, 1, 50);

        assertTrue(DozerBlade.canPush(grid, topology, null, 1, 1), "ordinary wall");
        assertTrue(DozerBlade.canPush(grid, topology, null, 2, 1), "gated-housing wall");
        assertFalse(DozerBlade.canPush(grid, topology, null, 3, 1), "one point over the threshold");
        assertFalse(DozerBlade.canPush(grid, topology, null, 4, 1), "fortified wall");
        assertFalse(DozerBlade.canPush(grid, topology, null, 5, 1), "a wall nothing can breach");
        assertFalse(DozerBlade.canPush(grid, topology, null, 6, 1), "natural obstruction");
        assertFalse(DozerBlade.canPush(grid, topology, null, 7, 1), "entity-owned fixture");
        assertFalse(DozerBlade.canPush(grid, topology, null, 8, 1), "blocked but not a wall");
        assertFalse(DozerBlade.canPush(grid, topology, null, 9, 1), "open floor");
        assertFalse(DozerBlade.canPush(grid, topology, null, -1, 1), "off the map");

        grid.setWallHp(4, 1, 90);
        assertTrue(DozerBlade.canPush(grid, topology, null, 4, 1), "a fortified wall worn under the threshold");
    }

    @Test void pushesSandbagBarricades() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry registry = new TileRegistry();
        for (String sheet : new String[]{"doodads", "sandbag-damage"})
            registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/" + sheet + ".tileset.json"))));
        TileRegistry.install(registry);
        try {
            NavigationGrid grid = floor();
            CellTopology topology = new CellTopology(SIZE, SIZE);
            grid.setWalkable(2, 2, false);
            DoodadService doodads = new DoodadService(grid);
            doodads.addDoodad(new Doodad(2, 2, registry.doodad("doodad.sandbag-straight-n"), 0, false));
            assertTrue(DozerBlade.canPush(grid, topology, doodads, 2, 2));
            assertFalse(DozerBlade.canPush(grid, topology, doodads, 3, 2));
        } finally {
            TileRegistry.install(previous);
        }
    }

    @Test void breachViewOpensExactlyThePushableCellsAndLeavesTheLiveGrid() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 4, 4, 100);
        wall(grid, topology, 5, 4, 150);
        NavigationGrid view = DozerBlade.breachView(grid, topology, null);
        assertTrue(view.isWalkable(4, 4));
        assertFalse(view.isWalkable(5, 4));
        assertFalse(grid.isWalkable(4, 4), "the live grid is untouched");
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (x == 4 && y == 4) continue;
            assertEquals(grid.isWalkable(x, y), view.isWalkable(x, y), "cell " + x + "," + y);
        }
    }

    @Test void gradesCrashDebrisOnOpenGroundOnly() {
        NavigationGrid grid = floor();
        grid.setTag(3, 3, NavigationGrid.CellTag.CRASH_DEBRIS, true);
        assertTrue(DozerBlade.grades(grid, 3, 3));
        assertFalse(DozerBlade.grades(grid, 4, 3));
    }

    /**
     * A dozer facing north with its nose a cell short of a wall row. Its blade bears on the four cells across its
     * width in the next row and nothing further: those under the threshold come down, the fortified one stands, and
     * the cells beside the blade and the row behind are untouched.
     */
    @Test void pushOpensOnlyTheCellsTheBladeBearsOn() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        for (int x = 5; x <= 15; x++) {
            wall(grid, topology, x, 10, x == 9 ? 150 : 100);
            wall(grid, topology, x, 11, 100);
        }
        VehicleType type = VehicleType.DOZER_BREACHER;
        GroundBody body = type.createBody();
        body.x = 10f;
        body.y = 10.1f - type.visualLengthCells * .5f;
        body.facingDegrees = 0f;
        VehicleControlComponent control = new VehicleControlComponent();
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            DozerBlade blade = new DozerBlade(navigation, new MapEditor(navigation), null);
            assertTrue(blade.push(body, type, control, 1f / 30f), "the blade bears on pushable cells");
            for (int tick = 1; tick < 60; tick++) blade.push(body, type, control, 1f / 30f);
            assertFalse(blade.push(body, type, control, 1f / 30f),
                    "against what is left, a fortified cell, the blade does no work: the hull is stuck, not pushing");
            for (int x : new int[]{8, 10, 11}) {
                assertTrue(grid.isWalkable(x, 10), "pushed " + x);
                assertEquals(CellTopology.GroundKind.RUBBLE, topology.getGroundKind(x, 10));
            }
            assertFalse(grid.isWalkable(9, 10), "fortified cell stands");
            for (int x : new int[]{5, 6, 7, 12, 13, 14, 15}) assertFalse(grid.isWalkable(x, 10), "beside the blade " + x);
            for (int x = 5; x <= 15; x++) assertFalse(grid.isWalkable(x, 11), "row behind " + x);
        }
    }

    @Test void pushSpendsStructureAtTheBladeRate() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 10, 10, 100);
        VehicleType type = VehicleType.DOZER_BREACHER;
        GroundBody body = type.createBody();
        body.x = 10.5f;
        body.y = 10.1f - type.visualLengthCells * .5f;
        VehicleControlComponent control = new VehicleControlComponent();
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            DozerBlade blade = new DozerBlade(navigation, new MapEditor(navigation), null);
            for (int tick = 0; tick < 30; tick++) blade.push(body, type, control, 1f / 30f);
            assertEquals(100 - (int) DozerBlade.PUSH_PER_SECOND, grid.getWallHp(10, 10), 1);
            assertFalse(blade.push(new BicycleBody(2.8f, 30f, 80f, 1f, 1f, 1f), type, control, 1f / 30f),
                    "a blade bearing on open floor does no work");
            assertEquals(0f, control.bladeCarry);
        }
    }

    @Test void aDozerDrivingOntoOneBarricadeCellGoesThroughItAndKeepsMostOfItsWay() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 10, 10, 100);
        VehicleType type = VehicleType.DOZER_BREACHER;
        GroundBody body = dozerAtRow10(type);
        body.speed = type.maxSpeed;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            DozerBlade blade = new DozerBlade(navigation, new MapEditor(navigation), null);
            assertTrue(blade.push(body, type, new VehicleControlComponent(), 1f / 30f));
            assertTrue(grid.isWalkable(10, 10), "down at the first touch, not pushed over seconds");
            assertTrue(body.speed > type.maxSpeed * .8f && body.speed < type.maxSpeed, "checked, not stopped: " + body.speed);
        }
    }

    @Test void aDozerDrivingOntoAWallAcrossItsBladeIsBroughtUpAndTheWallTakesItsWay() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        for (int x = 8; x <= 12; x++) wall(grid, topology, x, 10, 100);
        VehicleType type = VehicleType.DOZER_BREACHER;
        GroundBody body = dozerAtRow10(type);
        // Short of full speed: at full speed its blade's three cells of city wall are less than its way, and it goes through.
        body.speed = type.maxSpeed * .8f;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            DozerBlade blade = new DozerBlade(navigation, new MapEditor(navigation), null);
            assertTrue(blade.push(body, type, new VehicleControlComponent(), 1f / 30f));
            assertEquals(0f, body.speed, "its whole way went into the wall");
            assertFalse(grid.isWalkable(10, 10), "a city wall across the blade stands the hit");
            int left = grid.getWallHp(10, 10);
            assertTrue(left > 0 && left < 100 - (int) (DozerBlade.PUSH_PER_SECOND / 30f), "and is worn by it: " + left);
        }
    }

    /** A dozer driving north with its blade bearing on row 10 from x 9 to 12. */
    private static GroundBody dozerAtRow10(VehicleType type) {
        GroundBody body = type.createBody();
        body.x = 10.5f;
        body.y = 10.1f - type.visualLengthCells * .5f;
        return body;
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y, int structure) {
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, structure);
        topology.setTag(x, y, CellTopology.Tag.WALL, true);
    }
}
