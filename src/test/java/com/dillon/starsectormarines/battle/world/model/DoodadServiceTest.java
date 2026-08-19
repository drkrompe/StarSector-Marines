package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoodadServiceTest {

    private static final float EPS = 1e-6f;

    @Test
    void stackedProfilesChooseTheStrongestLevelThatContainsTheRound() {
        NavigationGrid grid = new NavigationGrid(4, 4);
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
}
