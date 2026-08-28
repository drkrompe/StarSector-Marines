package com.dillon.starsectormarines.battle.nav.mesh;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedyNavigationMeshTest {

    @Test
    void openAreaCollapsesToOneLargestRectangle() {
        GreedyNavigationMesh mesh = new GreedyNavigationMesh(
                walkableGrid(6, 4));

        GreedyNavigationMesh.Snapshot snapshot = mesh.snapshot();

        assertEquals(1, snapshot.regions().size());
        assertEquals(new GreedyNavigationMesh.Region(0, 0, 0, 6, 4, false),
                snapshot.regions().get(0));
        assertTrue(snapshot.transitions().isEmpty());
    }

    @Test
    void closedSharedEdgeRunBecomesAnImpassableMeshSeam() {
        NavigationGrid grid = walkableGrid(4, 3);
        for (int y = 0; y < 3; y++) {
            grid.blockSharedEdge(1, y, Direction.E);
        }

        GreedyNavigationMesh.Snapshot snapshot =
                new GreedyNavigationMesh(grid).snapshot();
        int left = snapshot.regionIdAt(1, 1);
        int right = snapshot.regionIdAt(2, 1);

        assertEquals(2, snapshot.regions().size());
        assertNotEquals(left, right);
        assertFalse(snapshot.areConnected(left, right));
        assertFalse(hasTransitionAt(snapshot, 1, 1, Direction.E));
    }

    @Test
    void partialBarrierSplitsRegionsButRetainsTheRouteAroundItsEndpoint() {
        NavigationGrid grid = walkableGrid(4, 3);
        grid.blockSharedEdge(1, 1, Direction.E);

        GreedyNavigationMesh.Snapshot snapshot =
                new GreedyNavigationMesh(grid).snapshot();
        int left = snapshot.regionIdAt(1, 1);
        int right = snapshot.regionIdAt(2, 1);

        assertNotEquals(left, right);
        assertFalse(hasTransitionAt(snapshot, 1, 1, Direction.E));
        assertTrue(snapshot.areConnected(left, right));
    }

    @Test
    void doorwayRemainsASingletonRegion() {
        NavigationGrid grid = walkableGrid(5, 1);
        grid.setDoorway(2, 0, true);

        GreedyNavigationMesh.Snapshot snapshot =
                new GreedyNavigationMesh(grid).snapshot();
        GreedyNavigationMesh.Region doorway = snapshot.regionAt(2, 0);

        assertTrue(doorway.doorway());
        assertEquals(1, doorway.cellCount());
        assertEquals(3, snapshot.regions().size());
        assertTrue(snapshot.areConnected(snapshot.regionIdAt(0, 0),
                snapshot.regionIdAt(4, 0)));
    }

    @Test
    void cellDestructionPublishesOneNewMeshSnapshotAtFlush() {
        NavigationGrid grid = new NavigationGrid(3, 1);
        grid.setWalkableFloor(0, 0);
        grid.setWalkableFloor(2, 0);
        NavigationService navigation = service(grid);
        GreedyNavigationMesh.Snapshot before =
                navigation.getNavigationMesh().snapshot();

        grid.setWalkableFloor(1, 0);
        navigation.markCellOpened(1, 0);

        assertEquals(-1, before.regionIdAt(1, 0));
        assertEquals(before.revision(),
                navigation.getNavigationMesh().snapshot().revision());
        navigation.flushNavigationTopologyIfDirty();

        GreedyNavigationMesh.Snapshot after =
                navigation.getNavigationMesh().snapshot();
        assertEquals(before.revision() + 1, after.revision());
        assertEquals(1, after.regions().size());
        assertEquals(3, after.regions().get(0).cellCount());
        assertMatchesFreshRebuild(grid, after);
    }

    @Test
    void mapEditorWallDestructionUsesTheReactiveMeshLifecycle() {
        NavigationGrid grid = new NavigationGrid(3, 1);
        grid.setWalkableFloor(0, 0);
        grid.setWalkableFloor(2, 0);
        grid.setWallHp(1, 0, 5);
        CellTopology topology = new CellTopology(3, 1);
        topology.setWall(1, 0, true);
        NavigationService navigation = new NavigationService(grid, topology);
        MapEditor editor = new MapEditor(navigation);
        long beforeRevision = navigation.getNavigationMesh().snapshot().revision();

        assertTrue(editor.damageWall(1, 0, 5));
        assertTrue(navigation.isNavigationTopologyDirty());
        assertEquals(-1, navigation.getNavigationMesh().regionIdAt(1, 0));

        navigation.flushNavigationTopologyIfDirty();

        GreedyNavigationMesh.Snapshot after =
                navigation.getNavigationMesh().snapshot();
        assertEquals(beforeRevision + 1, after.revision());
        assertTrue(after.regionAt(1, 0).doorway());
        assertTrue(after.areConnected(after.regionIdAt(0, 0),
                after.regionIdAt(2, 0)));
        assertMatchesFreshRebuild(grid, after);
    }

    @Test
    void barrierDestructionRemeshesAtTheSameTopologyBoundary() {
        NavigationGrid grid = walkableGrid(2, 1);
        grid.placeEdgeBarrier(0, 0, Direction.E,
                SharedEdgeBarrier.Kind.WINDOW);
        NavigationService navigation = service(grid);
        MapEditor editor = new MapEditor(navigation);
        GreedyNavigationMesh.Snapshot before =
                navigation.getNavigationMesh().snapshot();
        assertEquals(2, before.regions().size());

        assertTrue(editor.damageEdgeBarrier(0, 0, Direction.E,
                SharedEdgeBarrier.Kind.WINDOW.structure()));

        assertEquals(2,
                navigation.getNavigationMesh().snapshot().regions().size());
        navigation.flushNavigationTopologyIfDirty();

        GreedyNavigationMesh.Snapshot after =
                navigation.getNavigationMesh().snapshot();
        assertEquals(before.revision() + 1, after.revision());
        assertEquals(1, after.regions().size());
        assertMatchesFreshRebuild(grid, after);
    }

    @Test
    void fullRebuildAlsoHandlesANewlyClosedCell() {
        NavigationGrid grid = walkableGrid(3, 1);
        NavigationService navigation = service(grid);

        grid.setWalkable(1, 0, false);
        navigation.markNavigationTopologyDirty();
        navigation.flushNavigationTopologyIfDirty();

        GreedyNavigationMesh.Snapshot after =
                navigation.getNavigationMesh().snapshot();
        assertEquals(2, after.regions().size());
        assertFalse(after.areConnected(after.regionIdAt(0, 0),
                after.regionIdAt(2, 0)));
        assertMatchesFreshRebuild(grid, after);
    }

    @Test
    void multipleMutationsInOneBatchPublishOnlyOneRevision() {
        NavigationGrid grid = new NavigationGrid(4, 1);
        grid.setWalkableFloor(0, 0);
        grid.setWalkableFloor(3, 0);
        NavigationService navigation = service(grid);
        long beforeRevision = navigation.getNavigationMesh().snapshot().revision();

        grid.setWalkableFloor(1, 0);
        navigation.markCellOpened(1, 0);
        grid.setWalkableFloor(2, 0);
        navigation.markCellOpened(2, 0);
        navigation.flushNavigationTopologyIfDirty();

        GreedyNavigationMesh.Snapshot after =
                navigation.getNavigationMesh().snapshot();
        assertEquals(beforeRevision + 1, after.revision());
        assertEquals(1, after.regions().size());
        assertMatchesFreshRebuild(grid, after);
    }

    private static boolean hasTransitionAt(
            GreedyNavigationMesh.Snapshot snapshot,
            int x, int y, Direction direction) {
        for (GreedyNavigationMesh.Transition transition
                : snapshot.transitions()) {
            if (transition.direction() != direction) continue;
            if (direction == Direction.E && transition.x() == x
                    && y >= transition.y()
                    && y < transition.y() + transition.length()) {
                return true;
            }
            if (direction == Direction.N && transition.y() == y
                    && x >= transition.x()
                    && x < transition.x() + transition.length()) {
                return true;
            }
        }
        return false;
    }

    private static void assertMatchesFreshRebuild(
            NavigationGrid grid, GreedyNavigationMesh.Snapshot actual) {
        GreedyNavigationMesh.Snapshot fresh =
                new GreedyNavigationMesh(grid).snapshot();
        assertEquals(fresh.regions(), actual.regions());
        assertEquals(fresh.transitions(), actual.transitions());
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                assertEquals(fresh.regionIdAt(x, y), actual.regionIdAt(x, y));
            }
        }
    }

    private static NavigationService service(NavigationGrid grid) {
        return new NavigationService(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static NavigationGrid walkableGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }
}
