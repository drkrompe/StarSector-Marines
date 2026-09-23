package com.dillon.starsectormarines.battle.nav.mesh;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tiled mesh's one promise beyond the untiled one: a rebuild that
 * re-covers only the tiles a change touched publishes exactly the snapshot a
 * fresh mesh of the same grid would — same regions, same transitions, same
 * cell-to-region answer — whatever the change did.
 */
class GreedyNavigationMeshTiledTest {

    private static NavigationGrid randomCity(int width, int height, long seed) {
        NavigationGrid grid = new NavigationGrid(width, height);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (random.nextFloat() < 0.78f) grid.setWalkableFloor(x, y);
            }
        }
        for (int i = 0; i < width * height / 40; i++) {
            grid.setSharedEdgePassable(random.nextInt(width), random.nextInt(height),
                    Direction.CARDINALS[random.nextInt(4)], false);
        }
        for (int i = 0; i < width * height / 200; i++) {
            grid.setDoorway(random.nextInt(width), random.nextInt(height), true);
        }
        return grid;
    }

    private static void assertMatchesFreshRebuild(NavigationGrid grid,
                                                  GreedyNavigationMesh.Snapshot actual) {
        GreedyNavigationMesh.Snapshot fresh = new GreedyNavigationMesh(grid).snapshot();
        assertEquals(fresh.regions(), actual.regions());
        assertEquals(fresh.transitions(), actual.transitions());
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                assertEquals(fresh.regionIdAt(x, y), actual.regionIdAt(x, y));
            }
        }
    }

    @Test
    void noRegionCrossesATileBoundary() {
        NavigationGrid grid = new NavigationGrid(100, 70);
        for (int y = 0; y < 70; y++) {
            for (int x = 0; x < 100; x++) grid.setWalkableFloor(x, y);
        }
        GreedyNavigationMesh mesh = new GreedyNavigationMesh(grid);
        assertEquals(12, mesh.tileCount());
        for (GreedyNavigationMesh.Region region : mesh.snapshot().regions()) {
            int tileX0 = region.x() / GreedyNavigationMesh.TILE;
            int tileX1 = (region.maxXExclusive() - 1) / GreedyNavigationMesh.TILE;
            int tileY0 = region.y() / GreedyNavigationMesh.TILE;
            int tileY1 = (region.maxYExclusive() - 1) / GreedyNavigationMesh.TILE;
            assertEquals(tileX0, tileX1, "region " + region + " spans tiles in x");
            assertEquals(tileY0, tileY1, "region " + region + " spans tiles in y");
        }
        // An open 100x70 field is one rectangle per tile and nothing else.
        assertEquals(12, mesh.snapshot().regions().size());
    }

    @Test
    void randomChangesRecoverOnlyTheirTilesAndMatchAFreshMesh() {
        NavigationGrid grid = randomCity(100, 70, 3L);
        GreedyNavigationMesh mesh = new GreedyNavigationMesh(grid);
        assertEquals(12, mesh.lastTilesCovered());
        assertEquals(0, mesh.pendingGridChanges());
        assertMatchesFreshRebuild(grid, mesh.snapshot());
        Random random = new Random(5L);
        long lastChangeCount = grid.changeCount();
        for (int round = 0; round < 40; round++) {
            int changes = 1 + random.nextInt(3);
            for (int i = 0; i < changes; i++) {
                int x = random.nextInt(100);
                int y = random.nextInt(70);
                switch (random.nextInt(3)) {
                    case 0 -> grid.setWalkable(x, y, !grid.isWalkable(x, y));
                    case 1 -> {
                        Direction dir = Direction.CARDINALS[random.nextInt(4)];
                        grid.setSharedEdgePassable(x, y, dir, !grid.isEdgePassable(x, y, dir));
                    }
                    default -> grid.setDoorway(x, y, !grid.isDoorway(x, y));
                }
            }
            long before = mesh.snapshot().revision();
            assertEquals(grid.changeCount() - lastChangeCount,
                    mesh.pendingGridChanges());
            mesh.rebuild();
            assertEquals(before + 1, mesh.snapshot().revision());
            assertEquals(0, mesh.pendingGridChanges());
            assertTrue(mesh.lastCoverNanos() + mesh.lastSeamNanos()
                            + mesh.lastAssemblyNanos() <= mesh.lastRebuildNanos(),
                    "stage times must fit inside the measured rebuild");
            lastChangeCount = grid.changeCount();
            // A shared-edge write names both its cells, so one change can dirty two tiles.
            assertTrue(mesh.lastTilesCovered() <= 2 * changes,
                    "changes " + changes + " re-covered " + mesh.lastTilesCovered());
            assertMatchesFreshRebuild(grid, mesh.snapshot());
        }
    }

    @Test
    void aBreachAcrossASeamJoinsRegionsOnBothSides() {
        NavigationGrid grid = new NavigationGrid(70, 20);
        for (int y = 1; y < 19; y++) {
            for (int x = 1; x < 31; x++) grid.setWalkableFloor(x, y);
            for (int x = 34; x < 69; x++) grid.setWalkableFloor(x, y);
        }
        GreedyNavigationMesh mesh = new GreedyNavigationMesh(grid);
        int westBefore = mesh.regionIdAt(10, 10);
        int eastBefore = mesh.regionIdAt(60, 10);
        assertTrue(!mesh.snapshot().areConnected(westBefore, eastBefore));

        for (int y = 8; y <= 12; y++) {
            for (int x = 31; x <= 33; x++) grid.setWalkableFloor(x, y);
        }
        mesh.rebuild();

        assertEquals(2, mesh.lastTilesCovered());
        assertTrue(mesh.snapshot().areConnected(
                mesh.regionIdAt(10, 10), mesh.regionIdAt(60, 10)));
        assertMatchesFreshRebuild(grid, mesh.snapshot());
    }

    @Test
    void fallingBehindTheLogRecoversEveryTile() {
        NavigationGrid grid = randomCity(64, 64, 9L);
        GreedyNavigationMesh mesh = new GreedyNavigationMesh(grid);
        int capacity = grid.changeLogCapacity();
        for (int i = 0; i <= capacity; i++) {
            grid.setWalkable(5, 5, !grid.isWalkable(5, 5));
        }
        mesh.rebuild();
        assertEquals(mesh.tileCount(), mesh.lastTilesCovered());
        assertMatchesFreshRebuild(grid, mesh.snapshot());
    }
}
