package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MissionPersonnelOutcomeTest {

    @Test
    void casualtyDispositionIsStableForAResolvedMission() {
        Set<String> fallen = new HashSet<>(Arrays.asList(
                "marine-fallen-a", "marine-fallen-b", "marine-fallen-c"));
        MissionOutcome outcome = outcome(true,
                Collections.singleton("marine-survivor"), fallen);

        Map<String, MarineSoldierStatus> first =
                MissionResolver.resolvePersonnelOutcomes(outcome);
        Map<String, MarineSoldierStatus> replay =
                MissionResolver.resolvePersonnelOutcomes(outcome);

        assertEquals(first, replay);
        assertEquals(MarineSoldierStatus.ACTIVE, first.get("marine-survivor"));
        for (String id : fallen) {
            assertNotEquals(MarineSoldierStatus.ACTIVE, first.get(id));
        }
    }

    private static MissionOutcome outcome(boolean victory, Set<String> survivors,
                                          Set<String> fallen) {
        return new MissionOutcome(victory, "personnel-outcome-test", "Personnel Test",
                MissionType.ASSAULT, RiskLevel.MEDIUM, MissionSource.GENERATED,
                0, 0, survivors.size() + fallen.size(), fallen.size(),
                null, null, null, null,
                0, 0f, null,
                null, null, null,
                -1L, -1L, -1, 0, -1,
                0, 0, 0, survivors, fallen);
    }
}
