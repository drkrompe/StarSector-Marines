package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedBuildingWindowTest {

    private static final int WIDTH = 28;
    private static final int HEIGHT = 24;
    private static final BlockLeaf BUILDING = new BlockLeaf(3, 3, 20, 17, false);

    @Test
    void nonResidentialBuildingFamiliesReceiveShootThroughFacades() {
        List<BuildingCase> cases = List.of(
                new BuildingCase("commercial", new BuildingShellCore.BuildingConfig(
                        CellTopology.GroundKind.TILE, "COMMERCIAL",
                        PointOfInterest.Kind.RESIDENTIAL, BuildingLayouts.LayoutRecipe.SHOP,
                        BuildingKind.COMMERCIAL,
                        new RoomPurpose[]{RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM},
                        CommercialPartitionStrategy.DEFAULT)),
                new BuildingCase("warehouse", new BuildingShellCore.BuildingConfig(
                        CellTopology.GroundKind.STRIPED, "WAREHOUSE",
                        PointOfInterest.Kind.DEPOT, BuildingLayouts.LayoutRecipe.WAREHOUSE,
                        BuildingKind.INDUSTRIAL)),
                new BuildingCase("industrial", BuildingIndustrialFiller.CONFIG),
                new BuildingCase("civic", BuildingCivicFiller.CONFIG));

        for (BuildingCase buildingCase : cases) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(WIDTH, HEIGHT);
            BuildingShellCore.carve(BUILDING, grid, topology, new ArrayList<>(),
                    new Random(71L), buildingCase.config,
                    new BuildingPlacement(BuildingPlacement.Side.TOP, true));

            topology.tagDefaultWalls(grid);
            BuildingFloodFill.populate(topology, 71L);
            List<SharedEdgeBarrier> windows =
                    BuildingWindowTestSupport.windowsOwnedBy(grid, BUILDING);
            for (SharedEdgeBarrier window : windows) {
                int ownerX = window.structureCellX();
                int ownerY = window.structureCellY();
                Direction outward = BuildingWindowTestSupport.outwardFromOwner(window);
                int outsideX = ownerX + outward.dx;
                int outsideY = ownerY + outward.dy;
                assertTrue(grid.isWalkable(ownerX, ownerY),
                        buildingCase.name + " window owns a walkable recess");
                assertFalse(topology.isWindow(ownerX, ownerY),
                        buildingCase.name + " no longer consumes a thick wall cell");
                assertFalse(topology.isWall(ownerX, ownerY));
                assertEquals(buildingCase.config.buildingKind,
                        topology.getBuildingKindHint(ownerX, ownerY));
                assertTrue(topology.getBuildingId(ownerX, ownerY) > 0,
                        buildingCase.name + " window recess belongs to retained building");
                assertFalse(grid.isSharedEdgePassable(ownerX, ownerY, outward));
                assertTrue(grid.hasLineOfSight(outsideX, outsideY, ownerX, ownerY),
                        buildingCase.name + " window passes fire");
            }
            assertTrue(windows.size() >= 2,
                    buildingCase.name + " should expose multiple firing apertures");
        }
    }

    @Test
    void destroyingBuildingWindowRebuildsNavigationWithoutLosingBuildingOwnership() {
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        BuildingShellCore.carve(BUILDING, grid, topology, new ArrayList<>(),
                new Random(71L), BuildingIndustrialFiller.CONFIG,
                new BuildingPlacement(BuildingPlacement.Side.TOP, true));
        topology.tagDefaultWalls(grid);
        BuildingFloodFill.populate(topology, 71L);

        SharedEdgeBarrier window = BuildingWindowTestSupport
                .windowsOwnedBy(grid, BUILDING).get(0);
        int ownerX = window.structureCellX();
        int ownerY = window.structureCellY();
        int buildingId = topology.getBuildingId(ownerX, ownerY);
        Direction outward = BuildingWindowTestSupport.outwardFromOwner(window);
        NavigationService navigation = new NavigationService(grid, topology);
        MapEditor editor = new MapEditor(navigation);
        long beforeRevision = navigation.getNavigationMesh().snapshot().revision();

        assertTrue(editor.damageEdgeBarrier(ownerX, ownerY, outward,
                SharedEdgeBarrier.Kind.WINDOW.structure()));
        assertTrue(navigation.isNavigationTopologyDirty());
        assertNull(grid.getEdgeBarrier(ownerX, ownerY, outward));
        assertTrue(grid.isSharedEdgePassable(ownerX, ownerY, outward));
        assertEquals(buildingId, topology.getBuildingId(ownerX, ownerY));

        navigation.flushNavigationTopologyIfDirty();

        assertEquals(beforeRevision + 1,
                navigation.getNavigationMesh().snapshot().revision());
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static final class BuildingCase {
        final String name;
        final BuildingShellCore.BuildingConfig config;

        BuildingCase(String name, BuildingShellCore.BuildingConfig config) {
            this.name = name;
            this.config = config;
        }
    }
}
