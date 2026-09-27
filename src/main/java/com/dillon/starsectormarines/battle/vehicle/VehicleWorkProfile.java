package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;

/** Tick-owned attribution only; standalone planners never create global profiles. */
final class VehicleWorkProfile {
    private VehicleWorkProfile() { }

    static long start() {
        return TickInnerProfile.currentIfBound() == null ? 0L : System.nanoTime();
    }

    static void finish(Bucket bucket, long started) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (started != 0L && profile != null) profile.record(bucket, System.nanoTime() - started);
    }

    static void count(Bucket bucket, int count) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) profile.recordCount(bucket, count);
    }
}
