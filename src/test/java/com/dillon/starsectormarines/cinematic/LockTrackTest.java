package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CinematicShot.LockOverlay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockTrackTest {
    private final LockTrack track = new LockTrack(new LockOverlay(5.0, 11.0, 0.7, 0.9, 16, 46, "JANGALA", List.of(),
            new double[][] {{5.0, 1000, 500, 300}, {11.9, 1100, 520, 330}}));

    @Test void offScreenBeforeTheLockAndAfterItClears() {
        assertNull(track.at(4.9));
        assertNull(track.at(12.0));
    }

    @Test void arcsStartOutsideTheLimbAndCloseToItsMargin() {
        var landing = track.at(5.0);
        assertEquals(300 * (LockTrack.RING_MARGIN + LockTrack.CLOSE_TRAVEL), landing.ringRadius(), 1e-6);
        var held = track.at(8.0);
        assertEquals(track.sample(8.0)[3] * LockTrack.RING_MARGIN, held.ringRadius(), 1e-6);
    }

    @Test void theLabelArrivesAfterTheLeaderAndLeavesWithTheRing() {
        assertEquals(0.0, track.at(5.1).textAlpha(), 1e-9);
        assertEquals(1.0, track.at(8.0).textAlpha(), 1e-9);
        assertEquals(1.0, track.at(8.0).run(), 1e-9);
        assertTrue(track.at(11.5).fade() < 1.0);
    }

    @Test void theTrackedCentreIsInterpolatedLinearly() {
        double[] mid = track.sample(8.45);
        assertEquals(1050, mid[1], 1e-6);
        assertEquals(510, mid[2], 1e-6);
    }
}
