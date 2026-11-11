package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The process area's laws ({@link ProcessArea}): ordered blocks, clear lanes, nothing walled in. */
class ProcessAreaTest {

    private static final int X0 = 10, Y0 = 20;

    private static ProcessArea.Plan plan(boolean spineLeft, int length) {
        return ProcessArea.plan(X0, Y0, X0 + ProcessArea.width() - 1, Y0 + length - 1, spineLeft, 5);
    }

    /** Every block stands the same way: column and stripper by the rack, the heater behind them, one alignment. */
    @Test
    void everyBlockStandsTheSameWayFromTheRack() {
        for (boolean left : new boolean[]{true, false}) {
            ProcessArea.Plan p = plan(left, 80);
            ProcessArea.Block first = p.blocks().get(0);
            for (ProcessArea.Block b : p.blocks()) {
                assertEquals(first.column().x(), b.column().x());
                assertEquals(first.heater().x(), b.heater().x());
                assertEquals(b.column().y() - b.pad().y(), first.column().y() - first.pad().y());
                int rackX = left ? X0 : X0 + ProcessArea.width() - 1;
                assertTrue(Math.abs(b.column().x() - rackX) < Math.abs(b.heater().x() - rackX),
                        "the column stands nearer the rack than the heater");
            }
        }
    }

    /** Nothing solid stands on a lane, or on the rack corridor's road-side column. */
    @Test
    void lanesAndRackStayClear() {
        for (boolean left : new boolean[]{true, false}) {
            ProcessArea.Plan p = plan(left, 80);
            for (ProcessArea.Box lane : p.lanes()) {
                for (int y = lane.y(); y < lane.y() + lane.h(); y++) {
                    for (int x = lane.x(); x < lane.x() + lane.w(); x++) assertFalse(p.blocked(x, y));
                }
            }
            ProcessArea.Box rack = p.rack();
            int edge = left ? rack.x() : rack.x() + rack.w() - 1;
            for (int y = rack.y(); y < rack.y() + rack.h(); y++) assertFalse(p.blocked(edge, y));
        }
    }

    /** Every open cell, every charge among them, is reached from the road along the spine. */
    @Test
    void everyUnitIsReachedFromTheRoad() {
        for (boolean left : new boolean[]{true, false}) {
            ProcessArea.Plan p = plan(left, 80);
            assertTrue(p.allReached(left));
            for (ProcessArea.Block b : p.blocks()) assertFalse(p.blocked(b.chargeX(), b.chargeY()));
        }
    }

    /**
     * Every unit is tied in: its feed spur runs from the rack's line to its heater's face, its transfer line from the
     * heater to the column, its draw from the column to the stripper, and each stays inside its block.
     */
    @Test
    void everyUnitIsPipedToTheRackAndWithin() {
        for (boolean left : new boolean[]{true, false}) {
            ProcessArea.Plan p = plan(left, 80);
            ProcessArea.Box rack = p.rack();
            int rackLine = rack.x() + rack.w() / 2;
            for (ProcessArea.Block b : p.blocks()) {
                ProcessArea.Run feed = b.feed(), transfer = b.transfer(), draw = b.draw();
                assertEquals(rackLine, feed.x0(), "the feed spur leaves the rack");
                assertTrue(beside(b.heater(), feed.x1(), feed.y1()), "and ends at the heater");
                assertTrue(beside(b.heater(), transfer.x1(), transfer.y1()), "the transfer leaves the heater");
                assertTrue(beside(b.column(), transfer.x0(), transfer.y0()) || b.column().contains(transfer.x0(),
                        transfer.y0()), "into the column");
                assertTrue(ProcessArea.disc(b.column(), draw.x0(), draw.y0()), "the draw leaves the column");
                assertTrue(ProcessArea.disc(b.stripper(), draw.x1(), draw.y1()), "into the stripper");
                for (ProcessArea.Run r : b.pipes()) {
                    for (int[] c : new int[][]{{r.x0(), r.y0()}, {r.x1(), r.y1()}}) {
                        assertTrue(b.pad().contains(c[0], c[1]) || rack.contains(c[0], c[1]), "inside the block");
                    }
                }
            }
            assertTrue(p.allReached(left), "and the pipes wall nothing in");
        }
    }

    private static boolean beside(ProcessArea.Box b, int x, int y) {
        return !b.contains(x, y) && (b.contains(x + 1, y) || b.contains(x - 1, y) || b.contains(x, y + 1)
                || b.contains(x, y - 1));
    }

    /** The area is crossed both ways: a lane the full width between every pair of blocks, and one the full length. */
    @Test
    void theAreaIsCrossedBothWays() {
        ProcessArea.Plan p = plan(true, 80);
        long across = p.lanes().stream().filter(l -> l.w() == ProcessArea.width()).count();
        long along = p.lanes().stream().filter(l -> l.h() == 80).count();
        assertEquals(p.blocks().size() + 1, across);
        assertEquals(1, along);
    }

    /** As many blocks as the length takes, up to the most asked; none when the area is too narrow. */
    @Test
    void asManyBlocksAsFit() {
        int pitch = ProcessArea.BLOCK_V + ProcessArea.LANE;
        assertEquals(2, plan(true, ProcessArea.LANE + 2 * pitch).blocks().size());
        assertEquals(5, plan(true, 400).blocks().size());
        assertNull(ProcessArea.plan(0, 0, ProcessArea.width() - 2, 100, true, 5));
        assertNotNull(ProcessArea.plan(0, 0, ProcessArea.width() - 1, 100, true, 5));
    }
}
