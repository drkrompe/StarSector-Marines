package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultsScreenPersonnelPresentationTest {

    @Test
    void namedStationingDebriefIdentifiesPersistentDetachment() {
        MissionOutcome outcome = outcome(MissionSource.STATIONING, Set.of("team-1"));

        assertEquals("STATIONED DETACHMENT — RTD / WIA / MIA / KIA",
                ResultsScreen.personnelHeader(outcome));
        assertEquals("No persistent personnel assigned.",
                ResultsScreen.noPersonnelMessage(outcome));
    }

    @Test
    void anonymousLegacyStationingDebriefStaysAggregate() {
        MissionOutcome outcome = outcome(MissionSource.STATIONING, Collections.emptySet());

        assertEquals("STATIONING PERSONNEL — AGGREGATE REPORT",
                ResultsScreen.personnelHeader(outcome));
        assertEquals("Legacy anonymous detachment — aggregate casualties only.",
                ResultsScreen.noPersonnelMessage(outcome));
    }

    @Test
    void ordinaryMissionKeepsGenericPersonnelCopy() {
        MissionOutcome outcome = outcome(MissionSource.GENERATED, Set.of("team-1"));

        assertEquals("PERSONNEL — RTD / WIA / MIA / KIA",
                ResultsScreen.personnelHeader(outcome));
    }

    private static MissionOutcome outcome(MissionSource source, Set<String> fireteams) {
        return new MissionOutcome(true, "results-personnel", "Personnel Test",
                MissionType.ASSAULT, RiskLevel.MEDIUM, source,
                0, 0, 0, 0, null, null, null, null,
                0, 0f, null, null, null, null,
                -1L, -1L, -1, 0, -1,
                -1, -1, 0, 0, 0,
                Collections.emptySet(), Collections.emptySet(), fireteams);
    }
}
