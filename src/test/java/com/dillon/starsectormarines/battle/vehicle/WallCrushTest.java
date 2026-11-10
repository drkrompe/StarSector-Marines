package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ultra-heavy crush rule, its breach view and one hull's push, each on a hand-laid grid. */
class WallCrushTest {

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

        assertTrue(WallCrush.canPush(grid, topology, null, 1, 1), "ordinary wall");
        assertTrue(WallCrush.canPush(grid, topology, null, 2, 1), "gated-housing wall");
        assertFalse(WallCrush.canPush(grid, topology, null, 3, 1), "one point over the threshold");
        assertFalse(WallCrush.canPush(grid, topology, null, 4, 1), "fortified wall");
        assertFalse(WallCrush.canPush(grid, topology, null, 5, 1), "a wall nothing can breach");
        assertFalse(WallCrush.canPush(grid, topology, null, 6, 1), "natural obstruction");
        assertFalse(WallCrush.canPush(grid, topology, null, 7, 1), "entity-owned fixture");
        assertFalse(WallCrush.canPush(grid, topology, null, 8, 1), "blocked but not a wall");
        assertFalse(WallCrush.canPush(grid, topology, null, 9, 1), "open floor");
        assertFalse(WallCrush.canPush(grid, topology, null, -1, 1), "off the map");

        grid.setWallHp(4, 1, 90);
        assertTrue(WallCrush.canPush(grid, topology, null, 4, 1), "a fortified wall worn under the threshold");
    }

    @Test void ceilingAndRateGoByWeight() {
        assertEquals(WallCrush.ULTRA_HEAVY_CEILING, WallCrush.ceiling(VehicleType.Weight.ULTRA_HEAVY));
        assertEquals(WallCrush.ULTRA_HEAVY_PER_SECOND, WallCrush.rate(VehicleType.Weight.ULTRA_HEAVY));
        assertEquals(WallCrush.HEAVY_CEILING, WallCrush.ceiling(VehicleType.Weight.HEAVY));
        assertEquals(WallCrush.HEAVY_PER_SECOND, WallCrush.rate(VehicleType.Weight.HEAVY));
        assertTrue(WallCrush.HEAVY_PER_SECOND < WallCrush.ULTRA_HEAVY_PER_SECOND, "a heavy hull grinds slower");
        for (VehicleType.Weight weight : new VehicleType.Weight[]{VehicleType.Weight.LIGHT, VehicleType.Weight.MEDIUM}) {
            assertEquals(0, WallCrush.ceiling(weight), "no wall falls to a " + weight + " hull");
            assertEquals(0f, WallCrush.rate(weight), weight.name());
        }
        for (int ceiling : new int[]{WallCrush.HEAVY_CEILING, WallCrush.ULTRA_HEAVY_CEILING}) {
            assertTrue(ceiling >= 100, "an ordinary city wall falls to weight");
            assertTrue(ceiling < 150, "a fortified wall holds against weight alone");
        }
    }

    @Test void aFortsWorksAreNeverPushed() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 1, 1, 80);
        grid.markWorks(1, 1);
        assertFalse(WallCrush.canPush(grid, topology, null, 1, 1, 100));
    }

    /** A crushing hull's breach view keeps its identity as the map moves, and catches up with what changed. */
    @Test void aCrushingHullsBreachViewIsKeptAndCaughtUp() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 4, 4, 100);
        VehicleType tank = VehicleType.MEDIUM_TANK;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            NavigationGrid view = crush.planningGrid(tank, grid);
            assertTrue(view.isWalkable(4, 4), "an ordinary wall is open to a heavy hull's plan");
            assertTrue(crush.isKeptView(view));
            wall(grid, topology, 6, 6, 80);
            wall(grid, topology, 7, 6, 150);
            assertSame(view, crush.planningGrid(tank, grid), "kept, not rebuilt");
            assertTrue(view.isWalkable(6, 6), "a new housing wall is caught up open");
            assertFalse(view.isWalkable(7, 6), "a new fortified wall is caught up closed");
        }
    }

    @Test void aCrushingHullsRoutePaysForWhatItBreaks() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        topology.setGroundKind(4, 4, CellTopology.GroundKind.STREET);
        wall(grid, topology, 4, 4, 100);
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            TerrainCostField cost = crush.costField(VehicleType.BREAKTHROUGH_TANK, TerrainCostField.from(topology));
            assertEquals(WallCrush.ROUTE_COST, cost.costAt(4, 4), 1e-6f);
        }
    }

    @Test void aHullBearsOnItsOwnWidth() {
        VehicleType onager = VehicleType.BREAKTHROUGH_TANK;
        assertEquals(onager.visualWidthCells, WallCrush.bearingWidth(onager));
    }

    @Test void theCeilingDecidesWhatIsPushable() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 1, 1, 80);
        assertTrue(WallCrush.canPush(grid, topology, null, 1, 1, 80));
        assertFalse(WallCrush.canPush(grid, topology, null, 1, 1, 79));
        assertFalse(WallCrush.canPush(grid, topology, null, 1, 1, 0), "a hull with no ceiling pushes nothing");
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
            assertTrue(WallCrush.canPush(grid, topology, doodads, 2, 2));
            assertFalse(WallCrush.canPush(grid, topology, doodads, 3, 2));
        } finally {
            TileRegistry.install(previous);
        }
    }

    /**
     * A building's window bay is a floor cell in its wall line whose outward edge carries a window: it is pushable
     * where the walls beside it are, by the same ceiling, and never on works ground. The breach view opens it and
     * leaves the live grid's edge closed.
     */
    @Test void aWindowBayIsPushableWhereverTheWallsItSitsInAre() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        windowBay(grid, topology);
        assertFalse(grid.isSharedEdgePassable(10, 9, Direction.N), "the bay holds a hull on the live grid");
        assertTrue(WallCrush.canPush(grid, topology, null, 9, 10), "the wall beside it");
        assertTrue(WallCrush.canPushEdge(grid, 10, 9, Direction.N, WallCrush.ULTRA_HEAVY_CEILING), "the bay");
        assertTrue(WallCrush.canPushEdge(grid, 10, 10, Direction.S, WallCrush.HEAVY_CEILING), "from either side");
        assertFalse(WallCrush.canPushEdge(grid, 10, 9, Direction.N, 0), "a hull with no ceiling pushes nothing");
        assertFalse(WallCrush.canPushEdge(grid, 11, 9, Direction.N, WallCrush.ULTRA_HEAVY_CEILING), "an open edge");

        NavigationGrid view = WallCrush.breachView(grid, topology, null);
        assertTrue(view.isSharedEdgePassable(10, 9, Direction.N), "open to the hull's plan");
        assertFalse(grid.isSharedEdgePassable(10, 9, Direction.N), "the live grid is untouched");

        grid.markWorks(10, 10);
        assertFalse(WallCrush.canPushEdge(grid, 10, 9, Direction.N, WallCrush.ULTRA_HEAVY_CEILING), "a fort's window");
    }

    /** Pushing from a standstill into a wall row with a window bay in it: the bay goes down with the walls. */
    @Test void pushBreaksTheWindowBayWithTheWallsBesideItAndCountsEachPiece() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        windowBay(grid, topology);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        VehicleControlComponent control = new VehicleControlComponent();
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            for (int tick = 0; tick < 60; tick++) crush.push(body, type, control, 1f / 30f);
            assertNull(grid.getEdgeBarrier(10, 9, Direction.N), "the window is down");
            assertTrue(grid.isSharedEdgePassable(10, 9, Direction.N), "and its edge open");
            assertTrue(grid.isWalkable(9, 10) && grid.isWalkable(11, 10), "with the walls beside it");
            assertEquals(3, body.crushes, "two walls and a window fell");
        }
    }

    /** At its top speed the hull's way carries it through a bay and its walls at the first touch. */
    @Test void atSpeedItGoesThroughAWindowBayAtOnce() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        windowBay(grid, topology);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        body.speed = type.maxSpeed;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            assertTrue(crush.push(body, type, new VehicleControlComponent(), 1f / 30f));
            assertTrue(grid.isSharedEdgePassable(10, 9, Direction.N));
            assertTrue(body.speed > 0f, "checked, not stopped");
        }
    }

    /**
     * Registered equipment (a pallet stack, with its damaged and destroyed art) is built, so it is pushable as a
     * barricade is, by its own structure against the ceiling.
     */
    @Test void pushesRegisteredEquipment() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry registry = new TileRegistry();
        for (String sheet : new String[]{"doodads", "compound-yard-damage"})
            registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/" + sheet + ".tileset.json"))));
        TileRegistry.install(registry);
        try {
            NavigationGrid grid = floor();
            CellTopology topology = new CellTopology(SIZE, SIZE);
            Doodad pallets = new Doodad(2, 2, registry.doodad("doodad.industrial-pallet-stack"), 0, false);
            for (int y = 0; y < pallets.footprintCellsY; y++)
                for (int x = 0; x < pallets.footprintCellsX; x++) grid.setWalkable(2 + x, 2 + y, false);
            DoodadService doodads = new DoodadService(grid);
            doodads.addDoodad(pallets);
            assertTrue(WallCrush.canPush(grid, topology, doodads, 2, 2), "equipment under the ceiling");
            assertFalse(WallCrush.canPush(grid, topology, doodads, 2, 2, 99), "and not over it");
            assertFalse(WallCrush.canPush(grid, topology, doodads, 2 + pallets.footprintCellsX, 2), "beside it");
        } finally {
            TileRegistry.install(previous);
        }
    }

    @Test void breachViewOpensExactlyThePushableCellsAndLeavesTheLiveGrid() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 4, 4, 100);
        wall(grid, topology, 5, 4, 150);
        NavigationGrid view = WallCrush.breachView(grid, topology, null);
        assertTrue(view.isWalkable(4, 4));
        assertFalse(view.isWalkable(5, 4));
        assertFalse(grid.isWalkable(4, 4), "the live grid is untouched");
        assertTrue(view.hasTag(4, 4, NavigationGrid.CellTag.BREACH), "an opened cell is marked for the route's price");
        assertFalse(view.hasTag(5, 4, NavigationGrid.CellTag.BREACH), "a wall it cannot push is not");
        assertFalse(view.hasTag(3, 4, NavigationGrid.CellTag.BREACH), "nor is open ground");
        assertFalse(grid.hasTag(4, 4, NavigationGrid.CellTag.BREACH), "nor anything on the live grid");
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (x == 4 && y == 4) continue;
            assertEquals(grid.isWalkable(x, y), view.isWalkable(x, y), "cell " + x + "," + y);
        }
    }

    @Test void gradesCrashDebrisOnOpenGroundOnly() {
        NavigationGrid grid = floor();
        grid.setTag(3, 3, NavigationGrid.CellTag.CRASH_DEBRIS, true);
        assertTrue(WallCrush.grades(grid, 3, 3));
        assertFalse(WallCrush.grades(grid, 4, 3));
    }

    /**
     * An ultra-heavy hull facing north with its nose a cell short of a wall row. Its nose bears on the four cells
     * across its width in the next row and nothing further: those under the threshold come down, the fortified one
     * stands, and the cells beside it and the row behind are untouched.
     */
    @Test void pushOpensOnlyTheCellsTheNoseBearsOn() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        for (int x = 5; x <= 15; x++) {
            wall(grid, topology, x, 10, x == 9 ? 150 : 100);
            wall(grid, topology, x, 11, 100);
        }
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = type.createBody();
        body.x = 10f;
        body.y = 10.1f - type.visualLengthCells * .5f;
        body.facingDegrees = 0f;
        VehicleControlComponent control = new VehicleControlComponent();
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            assertTrue(crush.push(body, type, control, 1f / 30f), "the nose bears on pushable cells");
            for (int tick = 1; tick < 60; tick++) crush.push(body, type, control, 1f / 30f);
            assertFalse(crush.push(body, type, control, 1f / 30f),
                    "against what is left, a fortified cell, it does no work: the hull is stuck, not pushing");
            for (int x : new int[]{8, 10, 11}) {
                assertTrue(grid.isWalkable(x, 10), "pushed " + x);
                assertEquals(CellTopology.GroundKind.RUBBLE, topology.getGroundKind(x, 10));
            }
            assertFalse(grid.isWalkable(9, 10), "fortified cell stands");
            for (int x : new int[]{5, 6, 7, 12, 13, 14, 15}) assertFalse(grid.isWalkable(x, 10), "beside the nose " + x);
            for (int x = 5; x <= 15; x++) assertFalse(grid.isWalkable(x, 11), "row behind " + x);
        }
    }

    @Test void pushSpendsStructureAtTheWeightsRate() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 10, 10, 100);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = type.createBody();
        body.x = 10.5f;
        body.y = 10.1f - type.visualLengthCells * .5f;
        VehicleControlComponent control = new VehicleControlComponent();
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            for (int tick = 0; tick < 30; tick++) crush.push(body, type, control, 1f / 30f);
            assertEquals(100 - (int) WallCrush.ULTRA_HEAVY_PER_SECOND, grid.getWallHp(10, 10), 1);
            assertFalse(crush.push(new BicycleBody(2.8f, 30f, 80f, 1f, 1f, 1f), type, control, 1f / 30f),
                    "a nose bearing on open floor does no work");
            assertEquals(0f, control.bladeCarry);
        }
    }

    @Test void anUltraHeavyHullDrivingOntoOneBarricadeCellGoesThroughItAndKeepsMostOfItsWay() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 10, 10, 100);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        body.speed = type.maxSpeed;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            assertTrue(crush.push(body, type, new VehicleControlComponent(), 1f / 30f));
            assertTrue(grid.isWalkable(10, 10), "down at the first touch, not pushed over seconds");
            assertTrue(body.speed > type.maxSpeed * .8f && body.speed < type.maxSpeed, "checked, not stopped: " + body.speed);
        }
    }

    @Test void atSpeedItsMassCarriesItThroughAWallRow() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        for (int x = 8; x <= 12; x++) wall(grid, topology, x, 10, 100);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        body.speed = type.maxSpeed;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            assertTrue(crush.push(body, type, new VehicleControlComponent(), 1f / 30f));
            for (int x = 9; x <= 11; x++) assertTrue(grid.isWalkable(x, 10), "down at the first touch " + x);
            assertTrue(body.speed > type.maxSpeed * .8f, "eighty tonnes are checked, not stopped: " + body.speed);
        }
    }

    @Test void atACrawlItIsBroughtUpAndTheWallTakesItsWay() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        for (int x = 8; x <= 12; x++) wall(grid, topology, x, 10, 100);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        body.speed = .25f;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            assertTrue(crush.push(body, type, new VehicleControlComponent(), 1f / 30f));
            assertEquals(0f, body.speed, "its whole way went into the wall");
            assertFalse(grid.isWalkable(10, 10), "a city wall across its nose stands the hit");
            int left = grid.getWallHp(10, 10);
            assertTrue(left > 0 && left < 100 - (int) (WallCrush.ULTRA_HEAVY_PER_SECOND / 30f), "and is worn by it: " + left);
        }
    }

    /**
     * Reversing, the hull bears on the strip past its tail, not its nose: the wall row it backs into comes down and
     * the wall ahead of its nose is untouched.
     */
    @Test void reversingItBearsOnTheStripPastItsTail() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        for (int x = 9; x <= 11; x++) wall(grid, topology, x, 5, 100);
        wall(grid, topology, 10, 10, 100);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        // Its tail a little into row 5, as the nose fixture's nose is into row 10, whatever the hull's length.
        body.y = 5.9f + type.visualLengthCells * .5f;
        VehicleControlComponent control = new VehicleControlComponent();
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            assertTrue(crush.push(body, type, control, 1f / 30f, -1), "the tail bears on the row behind");
            for (int tick = 1; tick < 45; tick++) crush.push(body, type, control, 1f / 30f, -1);
            for (int x = 9; x <= 11; x++) assertTrue(grid.isWalkable(x, 5), "backed through " + x);
            assertEquals(100, grid.getWallHp(10, 10), "the wall ahead of the nose is not borne on");
        }
    }

    /** Backing at speed into a wall row, its way carries it through astern as it does ahead. */
    @Test void reversingAtSpeedItsMassCarriesItThroughAstern() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        // The tail stands at y 6.5, touching row 6.
        for (int x = 9; x <= 11; x++) wall(grid, topology, x, 6, 100);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        body.speed = -type.maxSpeed;
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            assertTrue(crush.push(body, type, new VehicleControlComponent(), 1f / 30f, -1));
            assertTrue(grid.isWalkable(10, 6), "down at the first touch");
            assertTrue(body.speed < 0f && body.speed > -type.maxSpeed, "checked, still backing: " + body.speed);
        }
    }

    /**
     * Turning, its whole footprint crushes: a wall its flank overlaps comes down, and it still does where something
     * it cannot push holds the hull as well, though that is no progress.
     */
    @Test void itsWholeFootprintCrushesWhereverItTurns() {
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        for (boolean alsoHeld : new boolean[]{false, true}) {
            NavigationGrid grid = floor();
            CellTopology topology = new CellTopology(SIZE, SIZE);
            wall(grid, topology, 11, 8, 100);
            if (alsoHeld) {
                grid.setWalkable(9, 7, false);
                grid.setTag(9, 7, NavigationGrid.CellTag.NATURAL_SOLID, true);
            }
            GroundBody body = noseAtRow10(type);
            VehicleControlComponent control = new VehicleControlComponent();
            try (NavigationService navigation = new NavigationService(grid, topology, false)) {
                WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
                assertEquals(!alsoHeld, crush.grind(body, type, control, 1f / 30f), "progress only when free");
                for (int tick = 1; tick < 45; tick++) crush.grind(body, type, control, 1f / 30f);
                assertTrue(grid.isWalkable(11, 8), "the flank's wall is down, held " + alsoHeld);
                if (alsoHeld) assertFalse(grid.isWalkable(9, 7), "the rock stands");
            }
        }
    }

    /** A cell both its leading strip and its footprint cover takes one rate a tick, not two. */
    @Test void aPushAndAGrindTheSameTickSpendOneRate() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        wall(grid, topology, 10, 10, 100);
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        GroundBody body = noseAtRow10(type);
        VehicleControlComponent control = new VehicleControlComponent();
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            WallCrush crush = new WallCrush(navigation, new MapEditor(navigation), null);
            for (int tick = 0; tick < 15; tick++) {
                crush.push(body, type, control, 1f / 30f, 1);
                crush.grind(body, type, control, 1f / 30f);
            }
            assertEquals(100 - (int) (WallCrush.ULTRA_HEAVY_PER_SECOND * .5f), grid.getWallHp(10, 10), 1);
        }
    }

    /** Row 10 from x 9 to 11: a city wall, a window bay looking south out of floor cell (10, 10), a city wall. */
    private static void windowBay(NavigationGrid grid, CellTopology topology) {
        wall(grid, topology, 9, 10, 100);
        wall(grid, topology, 11, 10, 100);
        grid.placeEdgeBarrier(10, 10, Direction.S, SharedEdgeBarrier.Kind.WINDOW);
    }

    /** An ultra-heavy hull driving north with its nose bearing on row 10 from x 9 to 11. */
    private static GroundBody noseAtRow10(VehicleType type) {
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
