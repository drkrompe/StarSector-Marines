package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each cell stands at, in metres — the input the sun's shadow is cast
 * from. {@code GroundSunShadowTest} owns the march; this owns what it marches
 * over.
 */
class MacroReliefFieldTest {

    /** A 2x2 interior with a wall ring around it, in an 8x8 world. */
    private static final int WORLD = 8;

    @Test
    void aRoofedBuildingIsSolidRatherThanAHollowRingOfItsOwnWalls() {
        CellTopology topology = walledBox();
        MacroReliefField relief = field(topology, roofOverInterior(topology));
        GenMappingRegistry mapping = GenMappingRegistry.installed();

        assertEquals(mapping.wallMacroHeightMeters(), relief.metersAt(2, 2), 1e-4f,
                "the shell is a wall");
        assertEquals(mapping.roofMacroHeightMeters(), relief.metersAt(3, 3), 1e-4f,
                "and the interior under an intact roof stands at the roof, not at its floor");
        assertEquals(4, relief.roofedCellCount());
    }

    @Test
    void cavingInARoofDropsThatCellBackToItsFloor() {
        CellTopology topology = walledBox();
        topology.setRoofDestroyed(3, 3, true);
        MacroReliefField relief = field(topology, roofOverInterior(topology));
        GenMappingRegistry mapping = GenMappingRegistry.installed();

        assertEquals(mapping.macroHeightMeters(CellTopology.GroundKind.INDOOR),
                relief.metersAt(3, 3), 1e-4f,
                "a hole in the roof is a hole in the height, or daylight never reaches the floor");
        assertEquals(mapping.roofMacroHeightMeters(), relief.metersAt(4, 4), 1e-4f,
                "the roof beside the hole is untouched");
        assertEquals(3, relief.roofedCellCount());
    }

    /**
     * The distinction the class exists to keep: a roof the player is being
     * shown through is still a roof. Reading the fade would make every
     * building's shadow pulse with what the player happens to know.
     */
    @Test
    void fadingARoofOutForVisibilityDoesNotChangeWhatItCasts() {
        CellTopology topology = walledBox();
        Buildings buildings = roofOverInterior(topology);
        float opaque = field(topology, buildings).metersAt(3, 3);

        for (Building building : buildings.all()) {
            building.currentAlpha = 0f;
            building.targetAlpha = 0f;
        }

        assertEquals(opaque, field(topology, buildings).metersAt(3, 3), 1e-4f,
                "roof visibility is not roof presence");
    }

    @Test
    void aWindowCutIntoAWallStandsAtItsSillWhileTheWallEitherSideDoesNot() {
        CellTopology topology = walledBox();
        topology.setWindow(4, 2, true);
        MacroReliefField relief = field(topology, roofOverInterior(topology));
        GenMappingRegistry mapping = GenMappingRegistry.installed();

        assertEquals(mapping.windowSillMacroHeightMeters(), relief.metersAt(4, 2), 1e-4f);
        assertEquals(mapping.wallMacroHeightMeters(), relief.metersAt(3, 2), 1e-4f);
        assertTrue(relief.metersAt(4, 2) < relief.metersAt(3, 2),
                "a window must be lower than the wall it is cut into, or no light gets through it");
    }

    @Test
    void aWindowStillStandsHighEnoughToBeAWallRatherThanAnOpening() {
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        assertTrue(mapping.windowSillMacroHeightMeters()
                        > mapping.macroHeightMeters(CellTopology.GroundKind.INDOOR),
                "a sill is not a doorway; it must still cast something");
    }

    @Test
    void groundOutsideTheMapIsTheDatumRatherThanACliff() {
        CellTopology topology = walledBox();
        MacroReliefField relief = field(topology, Buildings.EMPTY);
        assertEquals(0f, relief.metersAt(-1, 3), 1e-4f);
        assertEquals(0f, relief.metersAt(WORLD, 3), 1e-4f);
    }

    @Test
    void aMapWithNoBuildingsIsUnchangedByTheRoofRule() {
        CellTopology topology = walledBox();
        MacroReliefField relief = field(topology, Buildings.EMPTY);
        GenMappingRegistry mapping = GenMappingRegistry.installed();

        assertEquals(0, relief.roofedCellCount());
        assertEquals(mapping.macroHeightMeters(CellTopology.GroundKind.INDOOR),
                relief.metersAt(3, 3), 1e-4f,
                "an interior nobody registered as a building keeps its floor height");
    }

    /** The march has to reach the tallest thing that can stand, which is now the roof. */
    @Test
    void theTallestHeightCoversTheRoof() {
        CellTopology topology = walledBox();
        MacroReliefField relief = field(topology, roofOverInterior(topology));
        assertTrue(relief.tallestMeters() >= GenMappingRegistry.installed().roofMacroHeightMeters());
    }

    // ------------------------------------------------------------------------

    private static MacroReliefField field(CellTopology topology, Buildings buildings) {
        return new MacroReliefField(topology, buildings, GenMappingRegistry.installed());
    }

    /** Walls on the ring x,y in [2..5]; interior 3..4 is INDOOR floor. */
    private static CellTopology walledBox() {
        CellTopology topology = new CellTopology(WORLD, WORLD);
        for (int y = 2; y <= 5; y++) {
            for (int x = 2; x <= 5; x++) {
                boolean ring = x == 2 || x == 5 || y == 2 || y == 5;
                if (ring) {
                    topology.setTag(x, y, CellTopology.Tag.WALL, true);
                } else {
                    topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
                }
            }
        }
        return topology;
    }

    private static Buildings roofOverInterior(CellTopology topology) {
        int[] xs = {3, 4, 3, 4};
        int[] ys = {3, 3, 4, 4};
        Buildings buildings = new Buildings();
        buildings.add(new Building(1, BuildingKind.values()[0], 3, 4, 3, 4, xs, ys, 1f, 1f, 1f));
        return buildings;
    }
}
