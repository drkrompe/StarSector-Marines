package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookReviewTest {

    @Test
    void stillsAreTheBusiestFramesKeptApartInTimeOrder() {
        List<Integer> activity = List.of(0, 9, 8, 0, 0, 7, 0, 0, 0, 5);
        // 9 at frame 1 wins; 8 at frame 2 is too close; 7 at frame 5 is next; then 5 at frame 9.
        assertArrayEquals(new int[] {1, 5, 9}, LookReview.busiest(activity, 3, 3));
        assertArrayEquals(new int[] {0, 3}, LookReview.busiest(List.of(0, 0, 0, 0), 2, 3));
    }

    @Test
    void stillCandidatesHoldEveryFrameTheBusiestPickCanChooseAndNoMore() {
        Random random = new Random(7);
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        for (int trial = 0; trial < 50; trial++) {
            int stills = 1 + random.nextInt(4);
            int gap = 1 + random.nextInt(6);
            List<Integer> activity = new ArrayList<>();
            LookReview.StillCandidates candidates = new LookReview.StillCandidates(new int[0], stills, gap);
            for (int i = 0; i < 120; i++) {
                // Few distinct levels, so ties are common; a rising run puts the best frames last.
                int level = trial % 2 == 0 ? random.nextInt(5) : i / 10 + random.nextInt(3);
                activity.add(level);
                candidates.offer(i, level, frame);
            }
            assertTrue(candidates.size() <= (stills - 1) * (2 * gap - 1) + 1);
            for (int pick : LookReview.busiest(activity, stills, gap)) candidates.frame(pick);
        }
        LookReview.StillCandidates named = new LookReview.StillCandidates(new int[] {2, 5}, 4, 3);
        for (int i = 0; i < 10; i++) named.offer(i, 0, frame);
        assertEquals(2, named.size());
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
