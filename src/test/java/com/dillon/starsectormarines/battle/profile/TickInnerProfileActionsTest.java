package com.dillon.starsectormarines.battle.profile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-action histogram behind {@code ACTION_EXECUTE}: it must count
 * into the bucket and under the name at once, survive the per-worker merge
 * a parallel tick does at its end, be frozen by a snapshot, and be emptied
 * by the reset the next tick starts with.
 */
class TickInnerProfileActionsTest {

    @Test
    void anActionIsCountedInTheBucketAndUnderItsOwnName() {
        TickInnerProfile profile = new TickInnerProfile();

        profile.recordAction("EnterZone", 3_000L);
        profile.recordAction("EnterZone", 5_000L);
        profile.recordAction("HoldPosition", 100L);

        assertEquals(8_100L, profile.nanosOf(TickInnerProfile.Bucket.ACTION_EXECUTE));
        assertEquals(3, profile.countOf(TickInnerProfile.Bucket.ACTION_EXECUTE));
        Map<String, long[]> actions = profile.actions();
        assertArrayEquals(new long[] {8_000L, 2L}, actions.get("EnterZone"));
        assertArrayEquals(new long[] {100L, 1L}, actions.get("HoldPosition"));
    }

    @Test
    void workersMergeTheirHistogramsIntoTheCanonicalProfile() {
        TickInnerProfile canonical = new TickInnerProfile();
        TickInnerProfile worker = new TickInnerProfile();
        canonical.recordAction("EnterZone", 10L);
        worker.recordAction("EnterZone", 20L);
        worker.recordAction("Approach", 7L);

        canonical.addFrom(worker);

        Map<String, long[]> actions = canonical.actions();
        assertArrayEquals(new long[] {30L, 2L}, actions.get("EnterZone"));
        assertArrayEquals(new long[] {7L, 1L}, actions.get("Approach"));
        assertEquals(37L, canonical.nanosOf(TickInnerProfile.Bucket.ACTION_EXECUTE));
    }

    @Test
    void aSnapshotIsFrozenAndAResetEmptiesTheLiveHistogram() {
        TickInnerProfile profile = new TickInnerProfile();
        profile.recordAction("EnterZone", 10L);

        TickInnerProfile.Snapshot snapshot = profile.snapshot();
        profile.recordAction("EnterZone", 10L);
        profile.reset();

        assertArrayEquals(new long[] {10L, 1L}, snapshot.actions.get("EnterZone"));
        assertTrue(profile.actions().isEmpty());
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.ACTION_EXECUTE));
    }

    @Test
    void theCopyHandedOutDoesNotAliasTheLiveCounters() {
        TickInnerProfile profile = new TickInnerProfile();
        profile.recordAction("EnterZone", 10L);

        Map<String, long[]> copy = profile.actions();
        copy.get("EnterZone")[0] = 999L;

        assertEquals(10L, profile.actions().get("EnterZone")[0]);
        assertFalse(profile.actions().isEmpty());
    }

    @Test
    void everyInfantryReflexHasABucketAndAStrangerFallsToOther() {
        assertSame(TickInnerProfile.Bucket.REFLEX_LANE_SIDESTEP,
                TickInnerProfile.reflexBucket("LANE_SIDESTEP"));
        assertSame(TickInnerProfile.Bucket.REFLEX_COMMITTED_AIM,
                TickInnerProfile.reflexBucket("COMMITTED_AIM"));
        assertSame(TickInnerProfile.Bucket.REFLEX_OTHER,
                TickInnerProfile.reflexBucket("PLAYER_LOCOMOTION"));
    }
}
