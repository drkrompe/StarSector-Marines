package com.dillon.starsectormarines.battle.world.gen.ruins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BayJobTest {

    @Test
    void aBayBesideAStoreIsItsMarshallingFloorBeforeAnythingElse() {
        assertEquals(BayJob.MARSHALLING, BayJob.of("habitat", "raw-stock", true));
        // Beside a space that is no store, the bay is not marshalling.
        assertEquals(BayJob.GALLERY, BayJob.of("habitat", "habitat", true));
    }

    @Test
    void aHullBayIsAGalleryElseItServesTheSpaceNearest() {
        assertEquals(BayJob.GALLERY, BayJob.of("services", null, true));
        assertEquals(BayJob.TANK_FARM, BayJob.of("services", null, false));
        assertEquals(BayJob.LOCKERS, BayJob.of("habitat", null, false));
        assertEquals(BayJob.WORKSHOP, BayJob.of("works-hall", null, false));
        assertEquals(BayJob.CRATING, BayJob.of("finished-goods", null, false));
        assertEquals(BayJob.LINE, BayJob.of("line-two", null, false));
        assertEquals(BayJob.SPARES, BayJob.of("raw-stock", null, false));
    }

    @Test
    void neighboursOfOneJobFuseUpToTheJobsSize() {
        int g = BayJob.GALLERY.ordinal(), wk = BayJob.WORKSHOP.ordinal();
        // A row of seven gallery cells fuses six (a gallery's size) and leaves one; two workshops fuse; an empty
        // cell joins nothing.
        int[] jobs = {g, g, g, g, g, g, g, wk, wk, -1};
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 1, 2, 2, -1}, BayJob.fuse(jobs, 10, 1));
    }

    @Test
    void cellsOfDifferentJobsNeverFuse() {
        int t = BayJob.TANK_FARM.ordinal(), c = BayJob.CRATING.ordinal();
        // Diagonal cells of one job are not neighbours.
        int[] jobs = {t, c, c, t};
        assertArrayEquals(new int[]{0, 1, 2, 3}, BayJob.fuse(jobs, 2, 2));
    }
}
