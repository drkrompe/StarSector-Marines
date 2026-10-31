package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonTownHoldTest {

    @Test
    void theHeldBoxIsTheWarrensBuildingsAndTheCompoundWithAMargin() {
        LaidLayout layout = new LaidLayout(List.of(
                structure(CordonTownWarren.NAME + "1", 40, 30, 44, 33),
                structure(CordonTownWarren.NAME + "2", 70, 50, 75, 56),
                structure("farmstead", 0, 0, 200, 200)));
        int m = CordonTownHold.MARGIN;
        assertArrayEquals(new int[]{30 - m, 30 - m, 75 + m, 56 + m}, CordonTownHold.extent(layout, 30, 40),
                "the compound's middle and every warren building, and nothing else laid");
        assertNull(CordonTownHold.extent(new LaidLayout(List.of(structure("farmstead", 0, 0, 9, 9))), 30, 40),
                "no warren, nothing held");
    }

    @Test
    void heldGroundHasARaggedEdgeAndLeavesTheBoresTheirCreep() {
        int[] box = {0, 0, 199, 199};
        int band = CordonTownHold.EDGE_BAND;
        List<int[]> bores = List.of(new int[]{100, 100});
        assertFalse(CordonTownHold.held(-1, 50, box, bores, 7L), "nothing outside the box");
        assertTrue(CordonTownHold.held(band, 40, box, bores, 7L), "everything deeper than the edge band");
        assertFalse(CordonTownHold.held(104, 100, box, bores, 7L), "but a bore keeps the ground round its mouth");
        int heldAtEdge = 0;
        for (int x = band; x < 200 - band; x++) if (CordonTownHold.held(x, band / 2, box, List.of(), 7L)) heldAtEdge++;
        int along = 200 - 2 * band;
        assertTrue(heldAtEdge > 0 && heldAtEdge < along,
                "half the band in, the edge is ragged: some ground held, some not (" + heldAtEdge + " of " + along + ")");
    }

    private static Laid structure(String name, int x0, int y0, int x1, int y1) {
        return new Laid(name, Laid.Kind.STRUCTURE, Laid.Status.LAID, "", false,
                List.of(new int[]{x0, y0}, new int[]{x1, y1}), List.of(), List.of());
    }
}
