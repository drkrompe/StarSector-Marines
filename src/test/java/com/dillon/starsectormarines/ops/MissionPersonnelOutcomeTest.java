package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void deployedFireteamContextIsFrozenInBriefingOrder() {
        Set<String> selected = new LinkedHashSet<>(
                Arrays.asList("fireteam-b", "fireteam-a"));
        MissionOutcome outcome = outcome(true, Set.of(), Set.of(), selected);
        selected.clear();

        assertEquals(Arrays.asList("fireteam-b", "fireteam-a"),
                new ArrayList<>(outcome.deployedFireteamIds));
        assertThrows(UnsupportedOperationException.class,
                () -> outcome.deployedFireteamIds.add("fireteam-c"));
    }

    private static MissionOutcome outcome(boolean victory, Set<String> survivors,
                                          Set<String> fallen) {
        return outcome(victory, survivors, fallen, Collections.emptySet());
    }

    private static MissionOutcome outcome(boolean victory, Set<String> survivors,
                                          Set<String> fallen,
                                          Set<String> fireteams) {
        return MissionOutcome.builder()
                .victory(victory)
                .missionId("personnel-outcome-test")
                .missionName("Personnel Test")
                .missionType(MissionType.ASSAULT)
                .risk(RiskLevel.MEDIUM)
                .missionSource(MissionSource.GENERATED)
                .marinesEngaged(survivors.size() + fallen.size())
                .marinesLost(fallen.size())
                .survivingSoldierIds(survivors)
                .fallenSoldierIds(fallen)
                .deployedFireteamIds(fireteams)
                .build();
    }
}
