package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TailSquadRouteWorkTest {
    @Test
    void observesEveryMeasuredTickAndOnlySuccessfulCompletionAges() throws Exception {
        TailSquadRouteWork totals = new TailSquadRouteWork(11, 100, true);
        totals.observe(10, 900, List.of(slice("READY", 999)), 99_000_000, 99_000_000);
        totals.observe(11, 100, List.of(slice("YIELD", 800), slice("READY", 20)), 3_000_000, 1_000_000);
        totals.observe(12, 101, List.of(slice("FAILED", 900), slice("LIMIT", 700)), 2_500_000, 2_000_000);
        totals.observe(13, 99, List.of(slice("READY", 30), slice("STARTS_CHANGED", 600)));
        totals.observe(14, 0, List.of());
        JSONObject json = totals.json();
        assertEquals(4, json.getLong("measuredTicks"));
        assertEquals(300, json.getLong("totalWorkUnits"));
        assertEquals(101, json.getInt("maximumTickWorkUnits"));
        assertEquals(12, json.getInt("maximumTickWorkTick"));
        assertEquals(1, json.getLong("budgetViolationTicks"));
        assertEquals(2, json.getLong("readySlices"));
        assertEquals(30, json.getInt("maximumReadyAgeTicks"));
        assertEquals(3.0, json.getDouble("maximumRoutePreparationMs"));
        assertEquals(11, json.getInt("maximumRoutePreparationTick"));
        assertEquals(2.0, json.getDouble("maximumFieldBuildMs"));
        assertEquals(12, json.getInt("maximumFieldBuildTick"));
    }

    @Test
    void totalUsesTickCounterNotRetainedOrCumulativeSamplesAndControlHasNoViolations() throws Exception {
        TailSquadRouteWork totals = new TailSquadRouteWork(1, 10, false);
        TickInnerProfile profile = new TickInnerProfile();
        profile.recordCount(TickInnerProfile.Bucket.SQUAD_ROUTE_WORK, 77);
        profile.recordSquadRouteWork(1, 1, "EnterZone", 2, 3, "DONE", "READY",
                1, 1, 0, 999999, 4, 1, 5, 6, 7, 8);
        totals.observe(1, profile);
        assertEquals(77, totals.json().getLong("totalWorkUnits"));
        assertEquals(77, totals.json().getInt("maximumTickWorkUnits"));
        assertEquals(0, totals.json().getLong("budgetViolationTicks"));
        assertEquals(1, totals.json().getLong("readySlices"));
    }

    private static TickInnerProfile.SquadRouteWork slice(String status, int age) {
        return new TickInnerProfile.SquadRouteWork(1, 1, "EnterZone", 2, 3, "SEED", status,
                1, 1, 0, 999999, age, 1, 5, 6, 7, 8);
    }
}
