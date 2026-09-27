package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** All measured ticks, independently of which slow ticks the report retains. */
final class TailSquadRouteWork {
    private final int firstMeasuredTick;
    private final int workBudget;
    private final boolean budgetEnabled;
    private long measuredTicks, totalWorkUnits, budgetViolationTicks, readySlices;
    private long maximumRoutePreparationNanos, maximumFieldBuildNanos;
    private int maximumTickWorkUnits, maximumTickWorkTick = -1, maximumReadyAgeTicks;
    private int maximumRoutePreparationTick = -1, maximumFieldBuildTick = -1;

    TailSquadRouteWork(int firstMeasuredTick, int workBudget, boolean budgetEnabled) {
        this.firstMeasuredTick = firstMeasuredTick;
        this.workBudget = workBudget;
        this.budgetEnabled = budgetEnabled;
    }

    void observe(int tick, TickInnerProfile profile) {
        if (tick < firstMeasuredTick) return;
        observe(tick, profile.countOf(TickInnerProfile.Bucket.SQUAD_ROUTE_WORK),
                profile.slowSquadRouteWork(),
                profile.nanosOf(TickInnerProfile.Bucket.GOAP_ROUTE_PREPARATION),
                profile.nanosOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_BUILD));
    }

    void observe(int tick, int workUnits, List<TickInnerProfile.SquadRouteWork> slices) {
        observe(tick, workUnits, slices, 0, 0);
    }

    void observe(int tick, int workUnits, List<TickInnerProfile.SquadRouteWork> slices,
                 long routePreparationNanos, long fieldBuildNanos) {
        if (tick < firstMeasuredTick) return;
        measuredTicks++;
        totalWorkUnits += workUnits;
        if (workUnits > maximumTickWorkUnits) {
            maximumTickWorkUnits = workUnits;
            maximumTickWorkTick = tick;
        }
        if (budgetEnabled && workUnits > workBudget) budgetViolationTicks++;
        if (routePreparationNanos > maximumRoutePreparationNanos) {
            maximumRoutePreparationNanos = routePreparationNanos;
            maximumRoutePreparationTick = tick;
        }
        if (fieldBuildNanos > maximumFieldBuildNanos) {
            maximumFieldBuildNanos = fieldBuildNanos;
            maximumFieldBuildTick = tick;
        }
        for (TickInnerProfile.SquadRouteWork slice : slices) {
            if (!"READY".equals(slice.status())) continue;
            readySlices++;
            maximumReadyAgeTicks = Math.max(maximumReadyAgeTicks, slice.ageTicks());
        }
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("firstMeasuredTick", firstMeasuredTick)
                .put("measuredTicks", measuredTicks).put("budgetEnabled", budgetEnabled)
                .put("workBudgetPerTick", workBudget).put("totalWorkUnits", totalWorkUnits)
                .put("maximumTickWorkUnits", maximumTickWorkUnits)
                .put("maximumTickWorkTick", maximumTickWorkTick)
                .put("budgetViolationTicks", budgetViolationTicks)
                .put("readySlices", readySlices).put("maximumReadyAgeTicks", maximumReadyAgeTicks)
                .put("maximumRoutePreparationMs", maximumRoutePreparationNanos / 1_000_000.0)
                .put("maximumRoutePreparationTick", maximumRoutePreparationTick)
                .put("maximumFieldBuildMs", maximumFieldBuildNanos / 1_000_000.0)
                .put("maximumFieldBuildTick", maximumFieldBuildTick)
                .put("semantics", "All ticks after warmup, not only retained slow ticks. Work totals and maxima use the exact tick counter; lifetime slice work is never summed. Preparation and field-build maxima are independent maxima of each tick's bucket time, not one slice; field building is nested in route preparation and must not be added to it. A violation is a budget-enabled tick strictly exceeding its configured allowance. READY counts completed usable fields, not YIELD, FAILED, STARTS_CHANGED, LIMIT, or admissions; admissions are work slices in budgeted mode. Ready age may originate before warmup. Completion ages are complete while the maximum four slices per tick fit the eight retained work samples; the synchronous control emits no work slices.");
    }
}
