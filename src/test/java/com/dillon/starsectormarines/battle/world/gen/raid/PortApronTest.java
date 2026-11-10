package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lighter apron's layout rules ({@link PortApron}): cargo moves from the terminal's side through staging, past the
 * fuel main, to the pads at the deep edge; the pads keep their approach and a blast wall between each pair; the
 * farm stands outermost; nothing overlaps. Geometry only.
 */
class PortApronTest {

    /** Held ground east of a terminal: 60 deep, 70 across, as a 280x168 map's apron is. */
    private static PortApron east(int wanted) {
        return PortApron.lay(200, 60, 259, 129, true, true, wanted);
    }

    @Test
    void padsFaceTheDeepEdgeWithTheirApproachClear() {
        PortApron apron = east(4);
        assertNotNull(apron);
        for (PortApron.Rect pad : apron.pads()) {
            assertTrue(259 - pad.x1() >= PortApron.APPROACH, "a pad keeps its approach to the deep edge clear");
            assertTrue(apron.paved().x1() < 259, "nothing is laid between the pads and the edge");
            assertEquals(PortApron.PAD, pad.width());
            assertEquals(PortApron.PAD, pad.height());
        }
    }

    @Test
    void stagingHoldsABlockPerPadAsTheGroundAllows() {
        PortApron apron = east(3);
        int perRow = 0;
        for (PortApron.Rect box : apron.containers()) if (box.x0() == 200 + PortApron.YARD_ROAD) perRow++;
        assertEquals(3 * 2 * perRow, apron.containers().size(), "three pads, three blocks of two rows");
        PortApron shallow = PortApron.lay(200, 60, 200 + PortApron.depthWithoutStaging() + PortApron.BLOCK, 129,
                true, true, 3);
        assertEquals(2 * perRow, shallow.containers().size(), "shallow ground holds one block");
    }

    @Test
    void cargoMovesFromTheTerminalThroughStagingPastTheMainToThePads() {
        PortApron apron = east(4);
        int main = apron.fuelMain().x0();
        for (PortApron.Rect box : apron.containers()) {
            assertTrue(box.x0() >= 200 + PortApron.YARD_ROAD, "staging leaves the yard road along the terminal");
            assertTrue(box.x1() < main, "staging stands before the fuel main");
        }
        for (PortApron.Rect pad : apron.pads()) assertTrue(pad.x0() > main + PortApron.SERVICE, "pads beyond the lane");
        assertFalse(apron.containers().isEmpty());
    }

    @Test
    void aBlastWallStandsBetweenEachPairOfPadsAndTouchesNeither() {
        PortApron apron = east(4);
        assertEquals(apron.pads().size() - 1, apron.blastWalls().size());
        for (int i = 0; i < apron.blastWalls().size(); i++) {
            PortApron.Rect wall = apron.blastWalls().get(i), a = apron.pads().get(i), b = apron.pads().get(i + 1);
            int lo = Math.min(a.y1(), b.y1()), hi = Math.max(a.y0(), b.y0());
            assertTrue(wall.y0() > lo && wall.y1() < hi, "the wall stands in the gap between its pads");
            for (PortApron.Rect pad : apron.pads()) assertFalse(wall.overlaps(pad));
        }
    }

    @Test
    void theFarmStandsOutermostAndTheMainServesEveryPad() {
        PortApron apron = east(4);
        PortApron.Rect farm = apron.fuelFarm();
        for (PortApron.Rect pad : apron.pads()) assertTrue(farm.y0() > pad.y1(), "the farm stands past the last pad");
        assertEquals(farm.y0() - 1, apron.fuelMain().y1(), "the main runs from the farm");
        assertEquals(apron.pads().size(), apron.hydrants().size());
        for (int i = 0; i < apron.pads().size(); i++) {
            int[] h = apron.hydrants().get(i);
            assertEquals(apron.fuelMain().x0() + 1, h[0], "the hydrant stands beside the main");
            assertTrue(apron.pads().get(i).contains(apron.pads().get(i).x0(), h[1]), "level with its pad");
        }
    }

    @Test
    void nothingOnTheApronOverlaps() {
        PortApron apron = east(6);
        List<PortApron.Rect> all = new ArrayList<>(apron.pads());
        all.addAll(apron.blastWalls());
        all.addAll(apron.containers());
        all.add(apron.fuelFarm());
        all.add(apron.fuelMain());
        for (int i = 0; i < all.size(); i++) {
            assertTrue(contains(apron.paved(), all.get(i)), "everything stands on the pavement");
            for (int j = i + 1; j < all.size(); j++) assertFalse(all.get(i).overlaps(all.get(j)), all.get(i) + " " + all.get(j));
        }
    }

    @Test
    void theApronMirrorsWithTheSite() {
        PortApron east = PortApron.lay(10, 20, 69, 89, true, true, 4);
        PortApron west = PortApron.lay(10, 20, 69, 89, false, false, 4);
        for (int i = 0; i < east.pads().size(); i++) {
            PortApron.Rect e = east.pads().get(i), w = west.pads().get(i);
            assertEquals(10 + 69, e.x0() + w.x1(), "mirrored across the ground");
            assertEquals(20 + 89, e.y0() + w.y1(), "mirrored along the ground");
        }
        assertEquals(10, west.deepX());
    }

    @Test
    void theMarketSetsThePadsAndTheGroundCapsThem() {
        assertEquals(3, PortApron.padsFor(4));
        assertEquals(6, PortApron.padsFor(7));
        assertEquals(3, east(3).pads().size());
        // 70 across holds four pads beside the farm, whatever the market wants.
        assertEquals(4, east(6).pads().size());
    }

    @Test
    void groundTooSmallForTwoPadsLaysNoApron() {
        assertNull(PortApron.lay(0, 0, 59, 30, true, true, 4), "too narrow");
        assertNull(PortApron.lay(0, 0, PortApron.depthWithoutStaging() - 2, 69, true, true, 4), "too shallow");
    }

    private static boolean contains(PortApron.Rect outer, PortApron.Rect inner) {
        return inner.x0() >= outer.x0() && inner.x1() <= outer.x1() && inner.y0() >= outer.y0()
                && inner.y1() <= outer.y1();
    }
}
