package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import org.json.JSONException;
import org.json.JSONObject;

/** Post-preparation queue gauges and tick-local events, frozen after member join. */
record TailSquadRouteAdmission(long pendingRequests, long pendingCalls,
                               long admittedAttempts, long resumedFields,
                               int compatibleRefreshDeferred,
                               int oldestPendingWaitTicks, int admittedWaitTicks) {
    static TailSquadRouteAdmission capture(TickInnerProfile profile,
                                           int compatibleRefreshDeferred,
                                           int oldestPendingWaitTicks, int admittedWaitTicks) {
        return new TailSquadRouteAdmission(
                profile.countOf(Bucket.SQUAD_ROUTE_PENDING_REQUEST),
                profile.countOf(Bucket.SQUAD_ROUTE_PENDING_CALL),
                profile.countOf(Bucket.SQUAD_ROUTE_ADMITTED),
                profile.countOf(Bucket.SQUAD_ROUTE_RESUMED),
                compatibleRefreshDeferred, oldestPendingWaitTicks, admittedWaitTicks);
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("pendingRequests", pendingRequests)
                .put("pendingCalls", pendingCalls).put("admittedAttempts", admittedAttempts)
                .put("resumedFields", resumedFields)
                .put("compatibleRefreshDeferred", compatibleRefreshDeferred)
                .put("oldestPendingWaitTicks", oldestPendingWaitTicks)
                .put("admittedWaitTicks", admittedWaitTicks);
    }

    static final class Totals {
        private final int firstMeasuredTick;
        private long pendingRequestTicks, pendingCalls, admittedAttempts, resumedFields;
        private long compatibleRefreshDeferredTicks;
        private long pendingAtEnd, maximumPending;
        private int maximumPendingWaitTicks, maximumAdmittedWaitTicks;

        Totals(int firstMeasuredTick) { this.firstMeasuredTick = firstMeasuredTick; }

        void observe(int tick, TailSquadRouteAdmission sample) {
            if (tick < firstMeasuredTick) return;
            pendingRequestTicks += sample.pendingRequests;
            pendingCalls += sample.pendingCalls;
            admittedAttempts += sample.admittedAttempts;
            resumedFields += sample.resumedFields;
            compatibleRefreshDeferredTicks += sample.compatibleRefreshDeferred;
            pendingAtEnd = sample.pendingRequests;
            maximumPending = Math.max(maximumPending, pendingAtEnd);
            maximumPendingWaitTicks = Math.max(maximumPendingWaitTicks, sample.oldestPendingWaitTicks);
            maximumAdmittedWaitTicks = Math.max(maximumAdmittedWaitTicks, sample.admittedWaitTicks);
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("firstMeasuredTick", firstMeasuredTick)
                    .put("pendingRequestTicks", pendingRequestTicks).put("pendingCalls", pendingCalls)
                    .put("admittedAttempts", admittedAttempts).put("resumedFields", resumedFields)
                    .put("compatibleRefreshDeferredTicks", compatibleRefreshDeferredTicks)
                    .put("pendingAtEnd", pendingAtEnd).put("maximumPending", maximumPending)
                    .put("maximumPendingWaitTicks", maximumPendingWaitTicks)
                    .put("maximumAdmittedWaitTicks", maximumAdmittedWaitTicks)
                    .put("semantics", "Events and queue observations exclude warmup. Pending request-ticks are repeated observations, not unique squads. Admissions count work slices in budgeted mode and whole build attempts in the control, including failures; resumedFields counts successful fields for previously pending exact intents, not physical movement. Compatible refresh deferrals retain a usable field and are not no-field pending. Wait ages are observed in measured ticks and can originate before warmup ended.");
        }
    }
}
