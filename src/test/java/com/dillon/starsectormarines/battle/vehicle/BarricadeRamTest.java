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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a hull rams, what its way does to a sandbag barricade, and the view it plans over, on a hand-laid grid. */
class BarricadeRamTest {

    private static final int SIZE = 20;
    private static final float DT = 1f / 30f;
    private static TileRegistry previous;
    private static TileRegistry registry;

    @BeforeAll static void sheets() throws Exception {
        previous = TileRegistry.installed();
        registry = new TileRegistry();
        for (String sheet : new String[]{"doodads", "sandbag-damage"})
            registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/" + sheet + ".tileset.json"))));
        TileRegistry.install(registry);
    }

    @AfterAll static void restore() { TileRegistry.install(previous); }

    @Test void ramsLightBarricadesAndNothingElse() {
        Map map = new Map(2, 2, 4, 2);
        map.grid.setTag(4, 2, NavigationGrid.CellTag.WORKS, true);
        map.grid.setWalkable(6, 2, false);
        map.grid.setWallHp(6, 2, 100);
        assertTrue(BarricadeRam.rammable(map.grid, map.topology, map.doodads, 2, 2), "a sandbag piece");
        assertFalse(BarricadeRam.rammable(map.grid, map.topology, map.doodads, 4, 2), "the same piece on works");
        assertFalse(BarricadeRam.rammable(map.grid, map.topology, map.doodads, 6, 2), "a wall, not a barricade");
        assertFalse(BarricadeRam.rammable(map.grid, map.topology, map.doodads, 8, 2), "open floor");
        map.grid.setWallHp(2, 2, BarricadeRam.MAX_STRUCTURE + 1);
        assertFalse(BarricadeRam.rammable(map.grid, map.topology, map.doodads, 2, 2), "a piece too stout to ram");
    }

    @Test void anApcAtFullWayGoesThroughOneSandbagAndKeepsMostOfIt() {
        Map map = new Map(10, 10);
        VehicleType type = VehicleType.HEAVY_APC;
        GroundBody body = noseInRow10(type);
        body.speed = type.maxSpeed;
        try (NavigationService navigation = map.navigation()) {
            BarricadeRam ram = new BarricadeRam(navigation, new MapEditor(navigation), map.doodads);
            assertEquals(1, ram.strike(body, type));
            assertTrue(map.grid.isWalkable(10, 10), "down at the first touch");
            assertTrue(body.speed > type.maxSpeed * .8f && body.speed < type.maxSpeed, "checked, not stopped: " + body.speed);
        }
    }

    @Test void aSlowHullIsBroughtUpAndShovesThePieceDown() {
        Map map = new Map(10, 10);
        VehicleType type = VehicleType.HEAVY_APC;
        GroundBody body = noseInRow10(type);
        body.speed = .5f;
        try (NavigationService navigation = map.navigation()) {
            BarricadeRam ram = new BarricadeRam(navigation, new MapEditor(navigation), map.doodads);
            assertEquals(0, ram.strike(body, type));
            assertEquals(0f, body.speed, "its way went into the piece");
            int left = map.grid.getWallHp(10, 10);
            assertTrue(left > 0 && left < 100, "which stands, worn: " + left);
            VehicleControlComponent control = new VehicleControlComponent();
            int ticks = 0;
            while (!map.grid.isWalkable(10, 10) && ticks++ < 150) assertTrue(ram.shove(body, type, control, DT));
            assertTrue(map.grid.isWalkable(10, 10), "held only by the piece, it shoves it down");
            assertTrue(ticks <= Math.ceil(left / BarricadeRam.SHOVE_PER_SECOND / DT) + 1, "at the shove rate: " + ticks);
        }
    }

    @Test void theBreachViewOpensWhatTheHullCanRamAndCatchesUp() {
        Map map = new Map(10, 10, 12, 10);
        map.grid.setTag(12, 10, NavigationGrid.CellTag.WORKS, true);
        try (NavigationService navigation = map.navigation()) {
            MapEditor editor = new MapEditor(navigation);
            BarricadeRam ram = new BarricadeRam(navigation, editor, map.doodads);
            NavigationGrid view = ram.planningGrid(VehicleType.HEAVY_APC);
            assertTrue(view.isWalkable(10, 10));
            assertFalse(view.isWalkable(12, 10), "works stay shut");
            assertFalse(map.grid.isWalkable(10, 10), "the live grid is untouched");
            map.grid.setWalkable(3, 3, false);
            NavigationGrid again = ram.planningGrid(VehicleType.HEAVY_APC);
            assertTrue(again == view, "kept, not rebuilt");
            assertFalse(again.isWalkable(3, 3), "caught up with the live grid");
        }
    }

    /** A hull facing north whose nose is just inside row 10, over x 10. */
    private static GroundBody noseInRow10(VehicleType type) {
        GroundBody body = type.createBody();
        body.x = 10.5f;
        body.y = 10.1f - type.visualLengthCells * .5f;
        return body;
    }

    /** An open floor with a sandbag piece on each given x/y pair. */
    private static final class Map {
        final NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        final CellTopology topology = new CellTopology(SIZE, SIZE);
        final DoodadService doodads;

        Map(int... sandbags) {
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
            for (int i = 0; i < sandbags.length; i += 2) grid.setWalkable(sandbags[i], sandbags[i + 1], false);
            doodads = new DoodadService(grid);
            for (int i = 0; i < sandbags.length; i += 2) {
                doodads.addDoodad(new Doodad(sandbags[i], sandbags[i + 1], registry.doodad("doodad.sandbag-straight-n"),
                        0, false));
            }
        }

        NavigationService navigation() { return new NavigationService(grid, topology, false); }
    }
}
