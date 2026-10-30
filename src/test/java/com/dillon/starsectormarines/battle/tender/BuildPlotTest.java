package com.dillon.starsectormarines.battle.tender;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPlotTest {

    @Test
    void aFireBaseTurnsItsGunRimGapAndStoresToItsFacing() {
        BuildPlot base = BuildPlot.FIRE_BASE;
        assertArrayEquals(new int[]{10, 11}, base.gun(10, 10, 0, 1), "a cell forward, facing north");
        assertArrayEquals(new int[]{11, 10}, base.gun(10, 10, 1, 0), "a cell forward, facing east");

        List<BuildPlot.Piece> north = base.pieces(BuildPlot.Stage.PARAPET, 10, 10, 0, 1);
        assertEquals(16 - 3, north.size(), "the ring less its three-cell rear opening");
        assertFalse(at(north, 10, 8), "open at the rear");
        assertTrue(at(north, 10, 12), "closed at the front");

        List<BuildPlot.Piece> east = base.pieces(BuildPlot.Stage.PARAPET, 10, 10, 1, 0);
        assertFalse(at(east, 8, 10), "facing east, the opening is to the west");
        assertTrue(at(east, 8, 12), "and the rest of the ring stands");

        for (BuildPlot.Piece bag : base.pieces(BuildPlot.Stage.STORES, 10, 10, 1, 0)) {
            if (bag.doodad().contains("sandbag")) assertTrue(bag.doodad().endsWith("-e"), bag.doodad());
        }
        assertEquals(25, base.pieces(BuildPlot.Stage.PAD, 10, 10, 1, 0).size());
    }

    @Test
    void aPlotFacesTheNearerAxisOfItsThreat() {
        assertArrayEquals(new int[]{1, 0}, BuildPlot.facing(5f, -2f));
        assertArrayEquals(new int[]{0, -1}, BuildPlot.facing(2f, -5f));
        assertArrayEquals(new int[]{0, 1}, BuildPlot.facing(0f, 0f));
    }

    @Test
    void stagesFallDueAtTheirSharesAndStayLaidWhenTheOrderIsWithdrawn() {
        ConstructionOrder order = new ConstructionOrder(BuildOption.LOCUST_FIRE_BASE, 10.5f, 10.5f);
        order.begin();
        order.startWork();
        order.work(7f, 3, 3, false);
        assertNull(order.stageDue(), "the pad takes eight seconds");
        order.work(2f, 3, 3, false);
        assertEquals(BuildPlot.Stage.PAD, order.stageDue());
        order.laid();
        assertNull(order.stageDue());
        assertTrue(order.work(30f, 3, 3, false));
        assertEquals(BuildPlot.Stage.PARAPET, order.stageDue(), "a long tick lays every stage it passed, in order");
        order.laid();
        assertEquals(BuildPlot.Stage.STORES, order.stageDue());
        order.laid();
        assertNull(order.stageDue(), "the gun is the completion, not a stage");

        ConstructionOrder nest = new ConstructionOrder(BuildOption.LOCUST_TURRET, 10.5f, 10.5f);
        nest.begin();
        nest.startWork();
        nest.work(11f, 3, 3, false);
        nest.laid();
        nest.laid();
        nest.cancel();
        assertEquals(2, nest.stagesLaid(), "withdrawing an order takes none of its ground back");
        assertNull(nest.stageDue());
    }

    private static boolean at(List<BuildPlot.Piece> pieces, int x, int y) {
        for (BuildPlot.Piece p : pieces) if (p.x() == x && p.y() == y) return true;
        return false;
    }
}
