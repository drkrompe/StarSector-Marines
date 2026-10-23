package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LookReviewTest {

    @Test
    void stillsAreTheBusiestFramesKeptApartInTimeOrder() {
        List<Integer> activity = List.of(0, 9, 8, 0, 0, 7, 0, 0, 0, 5);
        // 9 at frame 1 wins; 8 at frame 2 is too close; 7 at frame 5 is next; then 5 at frame 9.
        assertArrayEquals(new int[] {1, 5, 9}, LookReview.busiest(activity, 3, 3));
        assertArrayEquals(new int[] {0, 3}, LookReview.busiest(List.of(0, 0, 0, 0), 2, 3));
    }

    @Test
    void openSiteIsTheWindowWithFewestBlockedCells() {
        int w = 140;
        int h = 100;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
                // Indoor floor everywhere but one open field: a body can stand only on the field.
                topology.setGroundKind(x, y, x >= 50 && x < 70 && y >= 40 && y < 50
                        ? GroundKind.GRASS : GroundKind.INDOOR);
            }
        }
        LookGround.Site site = LookGround.openSite(grid, topology, 20, 10);
        assertEquals(50, site.x0());
        assertEquals(40, site.y0());
        assertEquals(0, site.blockedCells());
        assertEquals(53.5f, site.x(3.5f));
    }
}
