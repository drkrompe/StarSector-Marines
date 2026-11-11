package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A works' defences by the raided market's security, and a hall's parapet ({@code WorksDefences}). */
class WorksDefencesTest {

    @Test
    void theDefencesFollowTheRaidedMarketsSecurity() {
        assertEquals(WorksDefences.Layer.OPEN, WorksDefences.layer(RaidSiteTemplate.Security.OPEN));
        assertEquals(WorksDefences.Layer.GUARDED, WorksDefences.layer(RaidSiteTemplate.Security.GUARDED));
        assertEquals(WorksDefences.Layer.FORTIFIED, WorksDefences.layer(RaidSiteTemplate.Security.FORTIFIED));
        assertEquals(WorksDefences.Layer.OPEN, WorksDefences.layer(null), "a plan with no security lays nothing");
    }

    @Test
    void aBuildingFacesTheSideOfTheWorksItLiesFurthestOutPast() {
        int[] works = {10, 10, 30, 30};
        assertEquals(0, WorksDefences.heldSide(works, 2, 12, 6, 18), "west");
        assertEquals(1, WorksDefences.heldSide(works, 34, 12, 38, 18), "east");
        assertEquals(2, WorksDefences.heldSide(works, 12, 1, 18, 5), "low y");
        assertEquals(3, WorksDefences.heldSide(works, 12, 36, 18, 40), "high y");
        assertEquals(-1, WorksDefences.heldSide(works, 25, 25, 35, 35), "overlapping the works faces no side");
        assertEquals(4, WorksDefences.gap(works, 2, 12, 6, 18));
    }

    @Test
    void theOuterBeltIsKeptOffEachGatesApproachOutwardOfItsMouth() {
        List<int[]> lanes = List.of(new int[]{10, 10, 1, 0});
        assertTrue(WorksDefences.inLane(lanes, 20, 10 + WorksDefences.LANE_CLEAR));
        assertFalse(WorksDefences.inLane(lanes, 20, 10 + WorksDefences.LANE_CLEAR + 1));
        assertFalse(WorksDefences.inLane(lanes, 5, 10), "behind the mouth is the works, not its approach");
    }

    @Test
    void aHallsParapetStandsOffEverySideFacingOutWithWaysThrough() {
        // A 10x4 hall at (10..19, 20..23): the parapet runs three cells out, at x 7 and 22, y 17 and 26.
        List<int[]> parapet = WorksDefences.parapet(10, 20, 19, 23);
        assertTrue(parapet.stream().allMatch(p -> p[0] == 7 || p[0] == 22 || p[1] == 17 || p[1] == 26));
        assertTrue(parapet.stream().filter(p -> p[1] == 26).allMatch(p -> p[2] == 0), "the north side faces north");
        assertTrue(parapet.stream().filter(p -> p[0] == 7).allMatch(p -> p[2] == 3), "the west side faces west");
        assertTrue(parapet.stream().noneMatch(p -> p[1] == 26 && p[0] == 13), "a way through every sixth cell");
        assertTrue(parapet.stream().noneMatch(p -> (p[0] == 7 || p[0] == 22) && (p[1] == 17 || p[1] == 26)),
                "the corners stay open");
    }
}
