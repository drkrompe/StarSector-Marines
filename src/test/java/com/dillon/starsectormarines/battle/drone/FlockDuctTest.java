package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockDuctTest {

    @Test
    void membersGoInAtTheEntryAndTheAnchorMovesToTheExitOnceTheyAreIn() {
        FlockDuct duct = new FlockDuct(0f, 0f, 40f, 0f, 2f);
        assertEquals(0f, duct.anchorX());
        assertFalse(duct.enters(FlockDuct.ENTER_CELLS + 0.1f));
        assertTrue(duct.enters(1f));
        duct.entered();
        duct.entered();
        assertEquals(0, duct.tick(0.1f, 0), "nobody is left outside: the flock is in");
        assertEquals(FlockDuct.Phase.DUCT, duct.phase());
        assertEquals(40f, duct.anchorX());
        assertFalse(duct.enters(0f), "nothing goes in once the flock is in");
    }

    @Test
    void afterTheTransitTheyComeUpOneAtATimeTheFirstAtOnce() {
        FlockDuct duct = new FlockDuct(0f, 0f, 40f, 0f, 2f);
        duct.entered();
        duct.entered();
        duct.tick(0.1f, 0);
        assertEquals(0, duct.tick(1.9f, 0), "still in the duct");
        assertEquals(1, duct.tick(0.2f, 0), "out: the first comes up at once");
        assertEquals(FlockDuct.Phase.OUT, duct.phase());
        assertEquals(1, duct.tick(FlockDock.LAUNCH_SECONDS, 1));
        assertEquals(FlockDuct.Phase.DONE, duct.phase());
    }

    @Test
    void stragglersAreLeftAfterTheWaitAndAFlockWithNoneInIsDone() {
        FlockDuct waited = new FlockDuct(0f, 0f, 40f, 0f, 2f);
        waited.entered();
        waited.tick(FlockDuct.IN_SECONDS, 3);
        assertEquals(FlockDuct.Phase.DUCT, waited.phase(), "three stragglers fly on; one went in");
        FlockDuct none = new FlockDuct(0f, 0f, 40f, 0f, 2f);
        none.tick(FlockDuct.IN_SECONDS, 5);
        assertEquals(FlockDuct.Phase.DONE, none.phase());
    }

    @Test
    void aDuctUpInAShutCourtKeepsTheFlockToThatCourtAndUpOutsideToNone() {
        // A 9x9 open field with a shut 3x3 court walled in at its middle (cells 3..5 open, ring 2 and 6 walled).
        FlockField.Open open = (x, y) -> x >= 0 && y >= 0 && x < 9 && y < 9
                && !(Math.max(Math.abs(x - 4), Math.abs(y - 4)) == 2);
        boolean[] outsideMask = FlockSystem.outside(9, 9, open);
        FlockField.Open outside = (x, y) -> open.open(x, y) && outsideMask[y * 9 + x];
        boolean[] court = FlockSystem.wellAt(9, 9, open, outside, 4, 4);
        assertTrue(court[4 * 9 + 4] && court[3 * 9 + 5], "the court's own cells");
        assertFalse(court[0], "not the outside");
        assertNull(FlockSystem.wellAt(9, 9, open, outside, 0, 0), "up outside: kept to nothing");
    }
}
