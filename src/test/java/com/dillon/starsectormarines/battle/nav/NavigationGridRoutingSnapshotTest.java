package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.command.CommandTopology;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationGridRoutingSnapshotTest {
    private final String previousProperty =
            System.getProperty(NavigationGrid.COMPACT_PUBLIC_TOPOLOGY_PROPERTY);

    @AfterEach
    void restoreProperty() {
        if (previousProperty == null) System.clearProperty(NavigationGrid.COMPACT_PUBLIC_TOPOLOGY_PROPERTY);
        else System.setProperty(NavigationGrid.COMPACT_PUBLIC_TOPOLOGY_PROPERTY, previousProperty);
    }

    @Test
    void compactAndFullSnapshotsPreserveExactRoutingInBothMovementModes() {
        NavigationGrid live = fixture();
        NavigationGrid full = live.copyNavigationTopology();
        NavigationGrid compact = live.copyRoutingTopology();
        NavigationGrid vehicle = live.copyVehicleRoutingTopology();
        assertArrayEquals(full.getCellFlagsArray(), compact.getCellFlagsArray());
        assertArrayEquals(full.getEdgePassabilityArray(), compact.getEdgePassabilityArray());
        assertArrayEquals(compact.getCellFlagsArray(), vehicle.getCellFlagsArray());
        assertArrayEquals(compact.getEdgePassabilityArray(), vehicle.getEdgePassabilityArray());
        for (int y = 0; y < live.getHeight(); y++) {
            for (int x = 0; x < live.getWidth(); x++) {
                assertEquals(full.isWalkable(x, y), compact.isWalkable(x, y));
                assertEquals(full.isDoorwayAt(full.index(x, y)), compact.isDoorwayAt(compact.index(x, y)));
                for (boolean cardinal : new boolean[]{true, false}) {
                    assertArrayEquals(GridPathfinder.findPath(full, 1, 1, x, y, cardinal, null),
                            GridPathfinder.findPath(compact, 1, 1, x, y, cardinal, null));
                    assertEquals(full.arePathConnected(1, 1, x, y, cardinal),
                            compact.arePathConnected(1, 1, x, y, cardinal));
                }
            }
        }
    }

    @Test
    void snapshotsOwnFlagsAndEdgesAcrossLiveAndSiblingMutation() {
        NavigationGrid live = fixture();
        NavigationGrid full = live.copyNavigationTopology();
        NavigationGrid compact = live.copyRoutingTopology();
        assertNotSame(live.getCellFlagsArray(), compact.getCellFlagsArray());
        assertNotSame(live.getEdgePassabilityArray(), compact.getEdgePassabilityArray());
        live.setWalkable(1, 1, false);
        live.setDoorway(3, 2, false);
        live.openSharedEdge(1, 1, Direction.E);
        assertTrue(compact.isWalkable(1, 1));
        assertTrue(compact.isDoorwayAt(compact.index(3, 2)));
        assertFalse(compact.isEdgePassable(1, 1, Direction.E));
        assertArrayEquals(full.getCellFlagsArray(), compact.getCellFlagsArray());
        assertArrayEquals(full.getEdgePassabilityArray(), compact.getEdgePassabilityArray());
        compact.setWalkable(2, 1, false);
        compact.blockSharedEdge(2, 2, Direction.S);
        assertTrue(live.isWalkable(2, 1));
        assertTrue(full.isWalkable(2, 1));
        assertTrue(live.isEdgePassable(2, 2, Direction.S));
        assertTrue(full.isEdgePassable(2, 2, Direction.S));
    }

    @Test
    void compactStorageOmitsAncillaryArraysAndFullCopyKeepsThem() throws Exception {
        NavigationGrid live = fixture();
        NavigationGrid full = live.copyNavigationTopology();
        NavigationGrid compact = live.copyRoutingTopology();
        NavigationGrid vehicle = live.copyVehicleRoutingTopology();
        int cells = live.getWidth() * live.getHeight();
        for (String field : new String[]{"coverByFacing", "coverCatchHalfHeightByFacing",
                "edgeBarrierCoverByFacing", "edgeBarrierCoverCatchHalfHeightByFacing"}) {
            assertEquals(cells * NavigationGrid.FACING_COUNT, arrayLength(full, field), field);
            assertEquals(0, arrayLength(compact, field), field);
            assertEquals(0, arrayLength(vehicle, field), field);
        }
        for (String field : new String[]{"eastEdgeBarriers", "northEdgeBarriers", "wallHp", "transientOpacity"}) {
            assertEquals(cells, arrayLength(full, field), field);
            assertEquals(0, arrayLength(compact, field), field);
            assertEquals(0, arrayLength(vehicle, field), field);
        }
    }

    @Test
    void commanderAndAsyncConsumersUseCompactDefaultAndHonorFullStorageControl() throws Exception {
        for (boolean compact : new boolean[]{true, false}) {
            if (compact) System.clearProperty(NavigationGrid.COMPACT_PUBLIC_TOPOLOGY_PROPERTY);
            else System.setProperty(NavigationGrid.COMPACT_PUBLIC_TOPOLOGY_PROPERTY, "false");
            CountingGrid grid = new CountingGrid();
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 6; x++) grid.setWalkableFloor(x, y);
            }
            ZoneGraph graph = new ZoneGraph(grid);
            graph.rebuild();
            BattleView sim = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> graph;
                        default -> throw new AssertionError("Unexpected battle read: " + method.getName());
                    });
            assertTrue(CommandTopology.freeze(sim).reachable(0, 0, 5, 3));
            CountDownLatch searched = new CountDownLatch(1);
            try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(1, 2,
                    (snapshot, occupancy, request) -> {
                        searched.countDown();
                        return new int[]{0, 0, 1, 0};
                    }, false)) {
                routes.pollOrSubmit(new AsyncDefendTrackRoutes.Request(1L, 1, 1L,
                                new Object(), 1, 0, 0, 0, 1, 0, 1, 0, true),
                        1, grid, new byte[24]);
                assertTrue(searched.await(5, TimeUnit.SECONDS), "async snapshot was submitted");
            }
            assertEquals(compact ? 2 : 0, grid.compactCopies);
            assertEquals(compact ? 0 : 2, grid.fullCopies);
        }
    }

    private static int arrayLength(NavigationGrid grid, String name) throws Exception {
        Field field = NavigationGrid.class.getDeclaredField(name);
        field.setAccessible(true);
        return Array.getLength(field.get(grid));
    }

    private static NavigationGrid fixture() {
        NavigationGrid grid = new NavigationGrid(7, 5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) grid.setWalkableFloor(x, y);
            grid.setWalkable(3, y, false);
        }
        grid.setWalkableFloor(3, 2);
        grid.setDoorway(3, 2, true);
        grid.setSeeThrough(3, 1, true);
        grid.blockSharedEdge(1, 1, Direction.E);
        return grid;
    }

    private static final class CountingGrid extends NavigationGrid {
        int compactCopies;
        int fullCopies;

        CountingGrid() { super(6, 4); }

        @Override public NavigationGrid copyRoutingTopology() {
            compactCopies++;
            return super.copyRoutingTopology();
        }

        @Override public NavigationGrid copyNavigationTopology() {
            fullCopies++;
            return super.copyNavigationTopology();
        }
    }
}
