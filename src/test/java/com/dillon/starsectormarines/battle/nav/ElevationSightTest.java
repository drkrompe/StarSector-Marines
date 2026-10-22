package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.vision.Shadowcast;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One rule for sight, fire and the fog, on a round plateau whose row y=15 reads
 * west to east: ramp 13-19, rim 20, top 21-29, rim 30, face 31-32, low ground 33 on.
 */
class ElevationSightTest {

    static final ElevationPlan HILL = new ElevationPlan(40, 30, 1, 0, 7,
            List.of(new Plateau(1, 8, List.of(new Point(24, 15)))),
            List.of(new Ramp(new Point(13, 15), new Point(19, 15), 3)));

    static NavigationGrid hill(ElevationMap.SightRule rule) {
        CellTopology topology = new CellTopology(40, 30);
        NavigationGrid grid = new NavigationGrid(40, 30);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        ElevationBrush.stamp(HILL, grid, topology);
        if (rule != ElevationMap.SightRule.SKYLINE) {
            NavigationGrid copy = grid.copyNavigationTopology();
            copy.setElevation(null);
            copy.setElevation(ElevationLayout.resolve(HILL).toMap(rule));
            return copy;
        }
        return grid;
    }

    @Test void theRowReadsAsTheFixtureSays() {
        ElevationMap map = hill(ElevationMap.SightRule.SKYLINE).elevation();
        assertTrue(map.isRamp(16, 15));
        assertTrue(map.isRim(30, 15));
        assertEquals(1, map.tier(25, 15));
        assertFalse(map.isRim(25, 15));
        assertTrue(map.isFace(31, 15));
        assertEquals(0, map.tier(33, 15));
    }

    @Test void highGroundSeesDownAndLowGroundSeesOnlyTheRim() {
        NavigationGrid grid = hill(ElevationMap.SightRule.SKYLINE);
        assertTrue(grid.hasLineOfSight(25, 15, 36, 15), "top sees the low ground over its own face");
        assertTrue(grid.hasLineOfSight(30, 15, 36, 15));
        assertTrue(grid.hasLineOfSight(36, 15, 30, 15), "a body on the lip is seen from below");
        assertFalse(grid.hasLineOfSight(36, 15, 25, 15), "a body back from the rim is not");
        assertFalse(grid.hasLineOfSight(36, 15, 2, 15), "the plateau stands between two low cells");
    }

    @Test void fireFollowsSight() {
        NavigationGrid grid = hill(ElevationMap.SightRule.SKYLINE);
        assertTrue(grid.hasLineOfFire(25.5f, 15.5f, 36.5f, 15.5f));
        assertTrue(grid.hasLineOfFire(36.5f, 15.5f, 30.5f, 15.5f));
        assertFalse(grid.hasLineOfFire(36.5f, 15.5f, 25.5f, 15.5f));
        assertFalse(grid.hasLineOfFire(36.5f, 15.5f, 29.5f, 15.5f), "one cell behind the rim is behind the lip");
    }

    @Test void theFogCastsTheSameRule() {
        NavigationGrid grid = hill(ElevationMap.SightRule.SKYLINE);
        assertTrue(casts(grid, 36, 15, grid.index(30, 15)));
        assertTrue(casts(grid, 36, 15, grid.index(31, 15)), "the face itself is seen");
        assertFalse(casts(grid, 36, 15, grid.index(26, 15)));
        assertTrue(casts(grid, 25, 15, grid.index(36, 15)));
    }

    @Test void strictSeesNothingUpAndFlatSeesEverything() {
        NavigationGrid strict = hill(ElevationMap.SightRule.STRICT);
        assertFalse(strict.hasLineOfSight(36, 15, 30, 15));
        assertTrue(strict.hasLineOfSight(30, 15, 36, 15));
        assertFalse(casts(strict, 36, 15, strict.index(30, 15)));
        NavigationGrid flat = hill(ElevationMap.SightRule.FLAT);
        assertTrue(flat.hasLineOfSight(36, 15, 25, 15));
        assertTrue(casts(flat, 36, 15, flat.index(25, 15)));
    }

    @Test void theFaceCoversTheRimFromBelowAndNotTheFootFromAbove() {
        NavigationGrid grid = hill(ElevationMap.SightRule.SKYLINE);
        assertEquals(1, grid.getCoverAgainst(30, 15, 36, 15), "the lip covers a rim holder from below");
        assertEquals(0, grid.getCoverAgainst(33, 15, 25, 15), "the face is under a shooter on top");
        assertEquals(1, grid.getCoverAgainst(33, 15, 10, 2), "a low shooter beyond the hill still meets the face");
    }

    private static boolean casts(NavigationGrid grid, int sx, int sy, int target) {
        int[] out = new int[Shadowcast.maxCells(30)];
        int n = Shadowcast.castFrom(grid, sx, sy, 30, 0f, out, 0);
        for (int i = 0; i < n; i++) if (out[i] == target) return true;
        return false;
    }
}
