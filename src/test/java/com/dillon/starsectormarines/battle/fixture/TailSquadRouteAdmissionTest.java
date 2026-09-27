package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TailSquadRouteAdmissionTest {
    @Test
    void totalsExcludeWarmupAndRetainWaitAgeWhenPendingQueueEmpties() throws Exception {
        TailSquadRouteAdmission.Totals totals = new TailSquadRouteAdmission.Totals(11);
        totals.observe(10, new TailSquadRouteAdmission(99, 999, 9, 8, 7, 100, 101));
        totals.observe(11, new TailSquadRouteAdmission(2, 12, 4, 1, 3, 2, 1));
        totals.observe(12, new TailSquadRouteAdmission(0, 0, 2, 2, 1, 0, 3));
        JSONObject result = totals.json();
        assertEquals(11, result.getInt("firstMeasuredTick"));
        assertEquals(2, result.getLong("pendingRequestTicks"));
        assertEquals(12, result.getLong("pendingCalls"));
        assertEquals(6, result.getLong("admittedAttempts"));
        assertEquals(3, result.getLong("resumedFields"));
        assertEquals(4, result.getLong("compatibleRefreshDeferredTicks"));
        assertEquals(0, result.getLong("pendingAtEnd"));
        assertEquals(2, result.getLong("maximumPending"));
        assertEquals(2, result.getInt("maximumPendingWaitTicks"));
        assertEquals(3, result.getInt("maximumAdmittedWaitTicks"));
    }

    @Test
    void countOnlyEventsSurviveWorkerMergeAndSnapshotFreezesTickValues() throws Exception {
        TickInnerProfile host = new TickInnerProfile();
        host.recordCount(Bucket.SQUAD_ROUTE_PENDING_REQUEST, 2);
        host.recordCount(Bucket.SQUAD_ROUTE_ADMITTED, 4);
        host.recordCount(Bucket.SQUAD_ROUTE_RESUMED, 1);
        TickInnerProfile worker = new TickInnerProfile();
        worker.recordCount(Bucket.SQUAD_ROUTE_PENDING_CALL, 6);
        host.addFrom(worker);
        TailSquadRouteAdmission sample = TailSquadRouteAdmission.capture(host, 3, 4, 2);
        host.reset();
        JSONObject result = sample.json();
        assertEquals(2, result.getLong("pendingRequests"));
        assertEquals(6, result.getLong("pendingCalls"));
        assertEquals(4, result.getLong("admittedAttempts"));
        assertEquals(1, result.getLong("resumedFields"));
        assertEquals(3, result.getInt("compatibleRefreshDeferred"));
        assertEquals(4, result.getInt("oldestPendingWaitTicks"));
        assertEquals(2, result.getInt("admittedWaitTicks"));
        assertEquals(0, worker.nanosOf(Bucket.SQUAD_ROUTE_PENDING_CALL));
    }
}
