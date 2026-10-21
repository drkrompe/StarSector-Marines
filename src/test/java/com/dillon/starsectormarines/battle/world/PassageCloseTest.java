package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearanceCache;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A passage shut again ({@code MapEditor.closePassage}, a sally door barred behind a sortie) is the one runtime write that
 * makes the grid less permissive. These ask that the cell comes back exactly as it was, and that every derivation keyed
 * on the grid sees the cell close rather than keeping the opening: path components, line of sight, the vehicle clearance
 * mask and its labels, and the zone graph, whose incremental update can only merge.
 */
class PassageCloseTest {

    private static final int W = 20, H = 9, WALL_X = 10;
    private static final int[] DOOR_Y = {3, 4, 5};

    private NavigationGrid grid;
    private CellTopology topology;
    private NavigationService navigation;
    private MapEditor editor;

    @BeforeEach
    void ground() {
        grid = new NavigationGrid(W, H);
        topology = new CellTopology(W, H);
        int floor = topology.surfaceIndex("colony.limestone");
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            topology.setSurface(x, y, floor);
        }
        int wall = topology.surfaceIndex("colony.masonry-wall");
        for (int y = 0; y < H; y++) {
            grid.setWalkable(WALL_X, y, false);
            grid.setWallHp(WALL_X, y, 400);
            topology.setWall(WALL_X, y, true);
            topology.setGroundKind(WALL_X, y, CellTopology.GroundKind.STREET);
            topology.setSurface(WALL_X, y, wall);
        }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.recomputeCoverAt(x, y);
        navigation = new NavigationService(grid, topology, false);
        editor = new MapEditor(navigation);
    }

    @AfterEach
    void close() {
        navigation.close();
    }

    private MapEditor.PassageRecord[] open() {
        MapEditor.PassageRecord[] records = new MapEditor.PassageRecord[DOOR_Y.length];
        for (int i = 0; i < DOOR_Y.length; i++) {
            records[i] = editor.recordPassage(WALL_X, DOOR_Y[i]);
            assertTrue(editor.openPassage(WALL_X, DOOR_Y[i], WALL_X - 1, DOOR_Y[i]));
        }
        return records;
    }

    private void shut(MapEditor.PassageRecord[] records) {
        for (MapEditor.PassageRecord record : records) assertTrue(editor.closePassage(record));
    }

    @Test
    void theCellComesBackExactlyAsItWas() {
        grid.setWallHp(WALL_X, 4, 250);
        grid.setCoverAtFacing(WALL_X - 1, 4, NavigationGrid.FACING_E, 2, 0.6f);
        long flags = grid.cellFlags(WALL_X, 4);
        byte edges = grid.getEdgePassabilityArray()[grid.index(WALL_X, 4)];
        int surface = topology.getSurface(WALL_X, 4);
        MapEditor.PassageRecord[] records = open();
        assertTrue(grid.isWalkable(WALL_X, 4));
        assertFalse(topology.isWall(WALL_X, 4));
        assertEquals(CellTopology.GroundKind.INDOOR, topology.getGroundKind(WALL_X, 4), "a passage is the floor beside it");
        assertEquals(0, grid.getCellCoverAtFacing(WALL_X - 1, 4, NavigationGrid.FACING_E), "an open door covers nobody");
        shut(records);
        assertEquals(flags, grid.cellFlags(WALL_X, 4));
        assertEquals(edges, grid.getEdgePassabilityArray()[grid.index(WALL_X, 4)]);
        assertEquals(250, grid.getWallHp(WALL_X, 4), "a shut door keeps the wear the wall had");
        assertTrue(topology.isWall(WALL_X, 4));
        assertEquals(CellTopology.GroundKind.STREET, topology.getGroundKind(WALL_X, 4));
        assertEquals(surface, topology.getSurface(WALL_X, 4));
        assertEquals(2, grid.getCellCoverAtFacing(WALL_X - 1, 4, NavigationGrid.FACING_E));
        assertEquals(0.6f, grid.getCellCoverCatchHalfHeightAtFacing(WALL_X - 1, 4, NavigationGrid.FACING_E));
        assertEquals(1, grid.getCellCoverAtFacing(WALL_X + 1, 4, NavigationGrid.FACING_W));
    }

    @Test
    void onlyAnOpenPassageShuts() {
        MapEditor.PassageRecord record = editor.recordPassage(WALL_X, 4);
        assertFalse(editor.closePassage(record), "a standing wall is not shut again");
        assertFalse(navigation.isNavigationTopologyDirty());
    }

    @Test
    void gridDerivationsSeeTheCellClose() {
        VehicleClearanceCache clearance = new VehicleClearanceCache(1);
        assertFalse(grid.arePathConnected(2, 4, 17, 4, false));
        assertFalse(clearance.components(grid, grid.topologyRevision()).connected(2, 4, 17, 4));
        MapEditor.PassageRecord[] records = open();
        assertTrue(grid.arePathConnected(2, 4, 17, 4, false));
        assertTrue(grid.hasLineOfSight(2, 4, 17, 4));
        assertTrue(clearance.clearance(grid, grid.topologyRevision()).isPassable(WALL_X, 4));
        assertTrue(clearance.components(grid, grid.topologyRevision()).connected(2, 4, 17, 4));
        long revision = grid.topologyRevision();
        long changes = grid.changeCount();
        long opacity = grid.opacityRevision();
        shut(records);
        assertTrue(grid.topologyRevision() > revision);
        assertTrue(grid.changeCount() >= changes + DOOR_Y.length, "each shut cell is in the change log");
        assertTrue(grid.opacityRevision() > opacity);
        assertFalse(grid.arePathConnected(2, 4, 17, 4, false));
        assertFalse(grid.hasLineOfSight(2, 4, 17, 4), "the line-of-sight caches forget the opening");
        assertFalse(clearance.clearance(grid, grid.topologyRevision()).isPassable(WALL_X, 4));
        assertFalse(clearance.components(grid, grid.topologyRevision()).connected(2, 4, 17, 4),
                "a component the door joined splits again");
    }

    @Test
    void shuttingRebuildsTheZoneGraphWhole() {
        ZoneGraph zones = navigation.getZoneGraph();
        MapEditor.PassageRecord[] records = open();
        navigation.flushNavigationTopologyIfDirty();
        assertTrue(zones.areConnected(zones.zoneIdAt(2, 4), zones.zoneIdAt(17, 4)));
        shut(records);
        assertTrue(navigation.isNavigationTopologyDirty());
        navigation.flushNavigationTopologyIfDirty();
        assertFalse(zones.areConnected(zones.zoneIdAt(2, 4), zones.zoneIdAt(17, 4)),
                "a zone the door merged is split again, which the merge-only update cannot do");
        assertEquals(-1, zones.zoneIdAt(WALL_X, 4), "the shut cell belongs to no zone");
    }
}
