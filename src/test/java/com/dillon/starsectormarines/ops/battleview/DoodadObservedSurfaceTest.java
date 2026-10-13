package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.vision.Shadowcast;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DoodadObservedSurfaceTest {
    @Test
    void largeRotatedHullIsAdmittedByItsNearSurfaceWithoutRevealingItsShadow() {
        NavigationGrid grid = openGrid(40, 36);
        Doodad hull = new Doodad(10, 8, new DoodadDef("test.hull", "hull.png", 0, 0,
                DoodadCover.HEAVY, 1.5f, 20, 24, null, 64), 3);
        for (int y = hull.cellY; y < hull.cellY + hull.footprintCellsY; y++)
            for (int x = hull.cellX; x < hull.cellX + hull.footprintCellsX; x++)
                grid.setWalkable(x, y, false);

        FogOfWarService fog = observedFrom(grid, 6, 18, 32);
        boolean[] before = fog.cellRevealedArray().clone();
        assertTrue(fog.isCellRevealed(10, 18));
        assertFalse(fog.isCellRevealed(20, 18));
        assertFalse(fog.isCellRevealed(34, 18));
        assertTrue(DoodadRenderSystem.hasObservedSurface(hull, grid, fog));
        assertArrayEquals(before, fog.cellRevealedArray());
        assertFalse(grid.isWalkable(20, 18));
        assertTrue(grid.blocksStructuralLineOfSight(20, 18));
    }

    @Test
    void visibleEmptyCornerOrTransparentCellCannotAdmitUnobservedSolidSurface() {
        NavigationGrid grid = openGrid(8, 8);
        Doodad prop = new Doodad(3, 3, new DoodadDef("test.irregular", "prop.png", 0, 0,
                DoodadCover.HEAVY, 1f, 2, 1, null, 64, List.of(".#")));
        grid.setWalkable(3, 3, false);
        grid.setWalkable(4, 3, false);
        FogOfWarService fog = new FogOfWarService();
        fog.init(grid, 0);
        fog.cellRevealedArray()[grid.index(3, 3)] = true;
        assertFalse(DoodadRenderSystem.hasObservedSurface(prop, grid, fog));
        fog.cellRevealedArray()[grid.index(4, 3)] = true;
        assertTrue(DoodadRenderSystem.hasObservedSurface(prop, grid, fog));
        grid.setSeeThrough(4, 3, true);
        assertFalse(DoodadRenderSystem.hasObservedSurface(prop, grid, fog));
    }

    private static FogOfWarService observedFrom(NavigationGrid grid, int x, int y, int range) {
        FogOfWarService fog = new FogOfWarService();
        fog.init(grid, 0);
        int[] cells = new int[Shadowcast.maxCells(range)];
        int count = Shadowcast.castFrom(grid, x, y, range, 0, cells, 0);
        for (int i = 0; i < count; i++) fog.cellRevealedArray()[cells[i]] = true;
        return fog;
    }

    @Test
    void observationFollowsRotatedAndMirroredSolidSurfaceOnly() {
        DoodadDef def = new DoodadDef("test.asymmetric", "prop.png", 0, 0,
                DoodadCover.HEAVY, 1f, 3, 2, null, 64, List.of("#..", ".#."));
        for (int pose = 0; pose < 8; pose++) {
            NavigationGrid grid = openGrid(8, 8);
            Doodad prop = new Doodad(2, 2, def, pose % 4, pose >= 4);
            FogOfWarService fog = new FogOfWarService();
            fog.init(grid, 0);
            for (int y = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++) {
                grid.setWalkable(2 + x, 2 + y, false);
            }
            for (int y = 0; y < prop.footprintCellsY; y++) for (int x = 0; x < prop.footprintCellsX; x++) {
                boolean[] revealed = fog.cellRevealedArray();
                Arrays.fill(revealed, false);
                revealed[grid.index(2 + x, 2 + y)] = true;
                assertEquals(prop.hasFogSurfaceAt(x, y),
                        DoodadRenderSystem.hasObservedSurface(prop, grid, fog), "pose " + pose);
            }
        }
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            grid.setWalkableFloor(x, y);
        return grid;
    }
}
