package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.tiles.TileCover;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoodadServiceTest {

    private static final float EPS = 1e-6f;

    @Test
    void stackedProfilesChooseTheStrongestLevelThatContainsTheRound() {
        NavigationGrid grid = new NavigationGrid(4, 4);
        grid.setWalkableFloor(2, 2);
        DoodadService service = new DoodadService(grid);
        TileManifest.TileFrame frame = new TileManifest.TileFrame(0, 0);
        service.addDoodad(new Doodad(2, 2, frame, false,
                Doodad.COVER_HEAVY, 0.20f));
        service.addDoodad(new Doodad(2, 2, frame, false,
                Doodad.COVER_LIGHT, 0.80f));

        assertEquals(0.20f, service.getDoodadHalfHeightOnCell(
                2, 2, Doodad.COVER_HEAVY), EPS);
        assertEquals(0.80f, service.getDoodadHalfHeightOnCell(
                2, 2, Doodad.COVER_LIGHT), EPS);
        assertEquals(Doodad.COVER_HEAVY, service.getDoodadLevelOnCell(2, 2, 0.10f));
        assertEquals(Doodad.COVER_LIGHT, service.getDoodadLevelOnCell(2, 2, 0.50f));
        assertEquals(Doodad.COVER_LIGHT, service.getDoodadLevelOnCell(2, 2, -0.50f));
        assertEquals(Doodad.COVER_NONE, service.getDoodadLevelOnCell(2, 2, 0.81f));
    }

    @Test
    void multicellDoodadPublishesCoverAndBallisticsAcrossItsWholeFootprint() {
        NavigationGrid grid = new NavigationGrid(5, 4);
        grid.setWalkableFloor(1, 1);
        grid.setWalkableFloor(2, 1);
        DoodadService service = new DoodadService(grid);
        Doodad sofa = new Doodad(1, 1, new TileManifest.TileFrame(0, 0),
                TileManifest.DOODAD_SHEET, Doodad.COVER_MED, 0.42f, 2, 1);

        service.addDoodad(sofa);

        assertEquals(Doodad.COVER_MED, service.getDoodadLevelOnCell(1, 1, 0.40f));
        assertEquals(Doodad.COVER_MED, service.getDoodadLevelOnCell(2, 1, 0.40f));
        assertEquals(Doodad.COVER_NONE, service.getDoodadLevelOnCell(3, 1, 0.40f));
        assertEquals(Doodad.COVER_NONE, service.getDoodadLevelOnCell(2, 1, 0.43f));
        assertEquals(Doodad.COVER_MED,
                service.getDoodadCoverAtFacing(0, 1, NavigationGrid.FACING_E));
        assertEquals(Doodad.COVER_MED,
                service.getDoodadCoverAtFacing(3, 1, NavigationGrid.FACING_W));
        assertEquals(2, sofa.footprintCellsX);
        assertEquals(1, sofa.footprintCellsY);
        assertEquals(true, sofa.occupiesCell(2, 1));
        assertEquals(false, sofa.occupiesCell(3, 1));
    }

    @Test
    void navigationBlockingDoodadPublishesOnlyDirectionalEdgeCover() {
        NavigationGrid grid = new NavigationGrid(5, 4);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        grid.setWalkable(2, 2, false);
        grid.setSeeThrough(2, 2, true);
        grid.recomputeCoverAt(1, 2);
        DoodadService service = new DoodadService(grid);

        service.addDoodad(new Doodad(2, 2,
                new TileManifest.TileFrame(0, 0), false,
                Doodad.COVER_HEAVY, 0.72f));

        assertEquals(Doodad.COVER_NONE, service.getDoodadLevelOnCell(2, 2, 0f),
                "blocked fixture must not also roll crossed-prop interception");
        assertEquals(Doodad.COVER_NONE,
                service.getDoodadCoverAtFacing(1, 2, NavigationGrid.FACING_E));
        assertEquals(Doodad.COVER_HEAVY,
                grid.getCoverAtFacing(1, 2, NavigationGrid.FACING_E));
        assertEquals(0.72f,
                grid.getCoverCatchHalfHeightAtFacing(1, 2, NavigationGrid.FACING_E), EPS);
    }

    @Test
    void natureOverlaysPublishCoverWithoutBecomingRenderDoodads() {
        NavigationGrid grid = new NavigationGrid(5, 4);
        CellTopology topology = new CellTopology(5, 4);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        TileRegistry registry = TileRegistry.installed();
        TileDef medium = registry.tile("nature.rock-medium-1");
        TileDef large = registry.tile("nature.rock-large-1");
        topology.setNatureOverlayIndex(1, 1, medium.index);
        topology.setNatureOverlayIndex(3, 1, large.index);
        DoodadService service = new DoodadService(grid);

        service.addNatureOverlayCover(topology, registry);

        assertTrue(service.getDoodads().isEmpty());
        assertEquals(TileCover.LIGHT.level(), service.getDoodadLevelOnCell(1, 1, 0.10f));
        assertEquals(Doodad.COVER_NONE, service.getDoodadLevelOnCell(1, 1, 0.19f));
        assertEquals(Doodad.COVER_NONE, service.getDoodadLevelOnCell(3, 1, 0.10f),
                "non-walkable rock must not double-count as crossed doodad cover");
        assertEquals(TileCover.HEAVY.level(),
                grid.getCoverAtFacing(2, 1, NavigationGrid.FACING_E));
        assertEquals(TileCover.HEAVY.defaultBallisticHalfHeight(),
                grid.getCoverCatchHalfHeightAtFacing(2, 1, NavigationGrid.FACING_E), EPS);
    }
}
