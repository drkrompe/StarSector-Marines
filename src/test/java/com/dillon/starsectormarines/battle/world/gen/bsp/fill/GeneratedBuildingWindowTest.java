package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

            int windows = 0;
            for (int y = BUILDING.top; y <= BUILDING.bottom; y++) {
                for (int x = BUILDING.left; x <= BUILDING.right; x++) {
                    if (!topology.isWindow(x, y)) continue;
                    windows++;
                    assertFalse(grid.isWalkable(x, y),
                            buildingCase.name + " window remains structural");
                    assertTrue(grid.isSeeThrough(x, y),
                            buildingCase.name + " window passes fire");
                }
            }
            assertTrue(windows >= 2,
                    buildingCase.name + " should expose multiple firing apertures");
        }
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
