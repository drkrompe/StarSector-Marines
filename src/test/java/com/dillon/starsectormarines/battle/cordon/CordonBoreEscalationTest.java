package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.cordon.CordonBoreEscalation.Box;
import com.dillon.starsectormarines.battle.cordon.CordonBoreEscalation.Site;
import com.dillon.starsectormarines.battle.cordon.CordonBoreEscalation.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The line bores' rules: when a step is due, what brings the surge, and where a site may not stand. */
class CordonBoreEscalationTest {

    private static final Step STEP = new Step(300, 2, 0.5f);

    @Test void aStepIsDueAtItsTimeOrWhenTheEvacuationRunsAheadOfIt() {
        assertFalse(CordonBoreEscalation.due(STEP, 299, 1, 11, 24));
        assertTrue(CordonBoreEscalation.due(STEP, 300, 0, 0, 24));
        assertTrue(CordonBoreEscalation.due(STEP, 10, 2, 0, 24), "convoys home bring it forward");
        assertTrue(CordonBoreEscalation.due(STEP, 10, 0, 12, 24), "people out bring it forward");
    }

    @Test void theSurgeComesOnThePullOutTheTownFinishedOrTheLatestTime() {
        assertTrue(CordonBoreEscalation.surges(true, false, false, true, 0), "an abandoned town surges at once");
        assertTrue(CordonBoreEscalation.surges(false, true, false, true, 0), "the player's pull-out order surges at once");
        assertFalse(CordonBoreEscalation.surges(false, false, true, true, 600), "the last truck is still at the gate");
        assertTrue(CordonBoreEscalation.surges(false, false, true, false, 600), "the town is empty and its last truck gone");
        assertFalse(CordonBoreEscalation.surges(false, false, false, false, 600), "nothing has ended the town yet");
        assertTrue(CordonBoreEscalation.surges(false, false, false, true, CordonBoreEscalation.SURGE_LATEST_SECONDS),
                "the latest time surges whatever the town does");
    }

    /** A town at 80..100 x 90..110, the front toward low y, the road coming in from the west. */
    @Test void theTownsSitesStandBeyondItAndOnItsFarFlankClearOfItsWallAndTheRoad() {
        Box town = new Box(80, 90, 100, 110);
        List<int[]> road = new ArrayList<>();
        for (int x = 10; x < 80; x++) road.add(new int[]{x, 100});
        assertEquals(1, CordonBoreEscalation.flank(road, 90), "the road comes in from the west: the far flank is east");
        Box kept = new Box(80 - CordonBoreEscalation.OFF_TOWN, 90 - CordonBoreEscalation.OFF_TOWN,
                100 + CordonBoreEscalation.OFF_TOWN, 110 + CordonBoreEscalation.OFF_TOWN);
        List<Site> sites = CordonBoreEscalation.townSites(town, -1, 1, List.of(), road, List.of(kept), List.of(),
                (x, y) -> true, 260, 200, 7, 7L);
        assertEquals(2, sites.size());
        Site beyond = sites.get(0), flank = sites.get(1);
        assertEquals(8, beyond.order());
        assertTrue(beyond.surge() && flank.surge() && beyond.town() && flank.town());
        assertTrue(beyond.y() < 90 - CordonBoreEscalation.OFF_TOWN, "beyond the town, into the front: " + beyond);
        assertTrue(flank.x() > 100 + CordonBoreEscalation.OFF_TOWN, "on the east flank: " + flank);
        for (Site s : sites) {
            for (int[] c : road) assertTrue(Math.max(Math.abs(c[0] - s.x()), Math.abs(c[1] - s.y()))
                    > CordonBoreEscalation.OFF_ROAD, "off the road: " + s);
        }
    }

    /**
     * Two redoubts on a line at rows 40..50, the front toward high y, a road straight down between them at x 100. Every
     * site stands in front of the line, off the road, the redoubts and the kept-out box, and the far ones open first.
     */
    @Test void sitesStandInFrontOfTheLineOffTheRoadTheRedoubtsAndTheCamp() {
        List<Box> redoubts = List.of(new Box(40, 40, 60, 50), new Box(140, 40, 160, 50));
        List<int[]> road = new ArrayList<>();
        for (int y = 0; y < 120; y++) road.add(new int[]{100, y});
        Box camp = new Box(120, 70, 140, 90);
        List<Site> sites = CordonBoreEscalation.plan(redoubts, 1, road, 100, List.of(camp), List.of(),
                (x, y) -> true, 200, 120, 2, 7L);
        assertEquals(3, sites.size(), "both redoubts' fronts and the stretch between have open ground");
        for (Site s : sites) {
            assertTrue(s.y() > 50, "in front of the line: " + s);
            assertTrue(Math.abs(s.x() - 100) > CordonBoreEscalation.OFF_ROAD, "off the road: " + s);
            for (Box r : redoubts) assertTrue(r.distance(s.x(), s.y()) >= CordonBoreEscalation.OFF_REDOUBT, "off a redoubt: " + s);
            assertTrue(camp.distance(s.x(), s.y()) > 0, "out of the camp: " + s);
        }
        assertTrue(Math.abs(sites.get(0).x() - 100) >= Math.abs(sites.get(2).x() - 100), "the far ones first");
        assertFalse(sites.get(1).surge());
        assertTrue(sites.get(2).surge(), "the rest are kept for the surge");
        assertTrue(sites.get(2).between(), "the stretch at the gap is the last");
    }

    @Test void anAnchorWithNoOpenGroundHasNoSite() {
        List<Site> sites = CordonBoreEscalation.plan(List.of(new Box(40, 40, 60, 50)), 1, List.of(), 0, List.of(),
                List.of(), (x, y) -> false, 200, 120, 4, 7L);
        assertTrue(sites.isEmpty());
    }
}
