package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CoverPropDamageTest {
    @Test void structuralHitsDamageThenBreachEachFamilyAndRetainGroundAndRotation() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry registry = new TileRegistry();
        for (String sheet : new String[]{"doodads", "fence-damage", "sandbag-damage"})
            registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/" + sheet + ".tileset.json"))));
        TileRegistry.install(registry);
        try {
            for (String shape : new String[]{"straight-h", "straight-v", "corner-nw", "corner-ne", "corner-sw", "corner-se"})
                for (int turn = 0; turn < 4; turn++) {
                    verify(registry, "doodad.industrial-fence-", shape, turn);
                    verifyWalkable(registry, "doodad.industrial-fence-", shape, turn);
                }
            for (String shape : new String[]{"straight-n", "straight-e", "straight-s", "straight-w",
                    "corner-nw", "corner-ne", "corner-sw", "corner-se"})
                for (int turn = 0; turn < 4; turn++) {
                    verify(registry, "doodad.sandbag-", shape, turn);
                    verifyWalkable(registry, "doodad.sandbag-", shape, turn);
                }
        } finally { TileRegistry.install(previous); }
    }

    private void verifyWalkable(TileRegistry registry, String prefix, String shape, int turn) {
        NavigationGrid grid = new NavigationGrid(5, 5);
        CellTopology topology = new CellTopology(5, 5);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) grid.setWalkableFloor(x, y);
        topology.setGroundKind(2, 2, CellTopology.GroundKind.INDOOR);
        DoodadService service = new DoodadService(grid);
        service.addDoodad(new Doodad(2, 2, registry.doodad(prefix + shape), turn, true));
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation);
            editor.setDoodads(service);
            assertTrue(service.getDoodadLevelOnCell(2, 2) > 0);
            assertFalse(editor.damageWall(2, 2, 25));
            assertEquals(prefix + "damaged-" + shape, service.getDoodads().get(0).definitionId());
            assertTrue(editor.damageWall(2, 2, 75));
            Doodad debris = service.getDoodads().get(0);
            assertEquals(prefix + "destroyed-" + shape, debris.definitionId());
            assertEquals(turn, debris.quarterTurns);
            assertTrue(debris.mirrored);
            assertEquals(0, service.getDoodadLevelOnCell(2, 2));
            assertTrue(grid.isWalkable(2, 2));
            assertFalse(grid.isDoorway(2, 2));
            assertEquals(CellTopology.GroundKind.INDOOR, topology.getGroundKind(2, 2));
            assertFalse(navigation.isNavigationTopologyDirty(), "A gantry rail on walkable floor is not a wall breach");
            assertFalse(editor.damageWall(2, 2, 100));
            assertSame(debris, service.getDoodads().get(0));
        }
    }

    private void verify(TileRegistry registry, String prefix, String shape, int turn) {
        NavigationGrid grid = new NavigationGrid(5, 5);
        CellTopology topology = new CellTopology(5, 5);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) grid.setWalkableFloor(x, y);
        grid.setWalkable(2, 2, false);
        grid.setSeeThrough(2, 2, true);
        topology.setFixture(2, 2, true);
        topology.setGroundKind(2, 2, CellTopology.GroundKind.GRASS);
        topology.setSurface(2, 2, topology.surfaceIndex("floors.stone"));
        DoodadService service = new DoodadService(grid);
        service.addDoodad(new Doodad(2, 2, registry.doodad(prefix + shape), turn, true));
        // A neighboring surviving fence must retain its own directional cover after the local rebake.
        grid.setWalkable(3, 2, false);
        grid.setSeeThrough(3, 2, true);
        service.addDoodad(new Doodad(3, 2, registry.doodad("doodad.industrial-fence-straight-h")));
        try (NavigationService navigation = new NavigationService(grid, topology, false)) {
            MapEditor editor = new MapEditor(navigation);
            editor.setDoodads(service);
            assertFalse(editor.damageWall(2, 2, 0));
            assertFalse(editor.damageWall(2, 2, -5));
            assertEquals(100, grid.getWallHp(2, 2));
            assertFalse(editor.damageWall(2, 2, 25));
            assertEquals(75, grid.getWallHp(2, 2));
            assertFalse(grid.isWalkable(2, 2));
            assertFalse(navigation.isNavigationTopologyDirty());
            assertEquals(prefix + "damaged-" + shape, service.getDoodads().get(0).definitionId());
            assertEquals(registry.doodad(prefix + shape).cover.level(), service.getDoodads().get(0).cover);
            assertEquals(registry.doodad(prefix + shape).cover.level(), grid.getCellCoverAtFacing(1, 2, NavigationGrid.FACING_E));
            assertTrue(editor.damageWall(2, 2, 75));
            assertTrue(grid.isWalkable(2, 2));
            assertFalse(grid.isDoorway(2, 2));
            assertFalse(topology.isFixture(2, 2));
            assertEquals(CellTopology.GroundKind.GRASS, topology.getGroundKind(2, 2));
            assertEquals("floors.stone", topology.getSurfaceId(2, 2));
            Doodad debris = service.getDoodads().get(0);
            assertEquals(prefix + "destroyed-" + shape, debris.definitionId());
            assertEquals(turn, debris.quarterTurns);
            assertTrue(debris.mirrored);
            assertEquals(0, debris.cover);
            assertEquals(0, grid.getCellCoverAtFacing(1, 2, NavigationGrid.FACING_E));
            assertEquals(1, grid.getCellCoverAtFacing(2, 2, NavigationGrid.FACING_E));
            assertFalse(editor.damageWall(2, 2, 100));
            assertSame(debris, service.getDoodads().get(0));
            assertEquals(100, grid.getWallHp(3, 2));
            assertTrue(navigation.isNavigationTopologyDirty());
            navigation.flushNavigationTopologyIfDirty();
            assertFalse(navigation.isNavigationTopologyDirty());
        }
    }
}
