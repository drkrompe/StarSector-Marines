package com.dillon.starsectormarines.battle.world.gen.layout;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A built site opens its ground to the layout's structures; held and open sites keep theirs closed to them. */
class StructureHeldTest {

    @Test
    void onlyABuiltSiteTakesStructures() {
        boolean[][] held = new boolean[10][10];
        for (boolean[] column : held) Arrays.fill(column, true);
        MissionLayout layout = new MissionLayout(List.of(
                new Site("rows", 1, 1, 3, 3, SitePurpose.BUILT, false),
                new Site("lines", 6, 6, 8, 8, SitePurpose.GROUND, false)));
        boolean[][] out = MissionLayoutStage.structureHeld(held, layout, List.of());
        assertFalse(out[2][2], "a built site's ground takes structures");
        assertTrue(out[7][7], "held ground still refuses them");
        assertTrue(held[2][2], "the growth mask is left as it was");
        assertTrue(SitePurpose.BUILT.held() && SitePurpose.GROUND.held() && !SitePurpose.OPEN.held());
    }

    @Test
    void withNoBuiltSiteTheMaskIsUnchanged() {
        boolean[][] held = new boolean[4][4];
        MissionLayout layout = new MissionLayout(List.of(new Site("pit", 0, 0, 1, 1, SitePurpose.GROUND, false)));
        assertSame(held, MissionLayoutStage.structureHeld(held, layout, List.of()));
    }
}
