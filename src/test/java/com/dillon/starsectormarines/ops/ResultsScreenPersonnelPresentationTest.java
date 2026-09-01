package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /**
     * A boat that burned is reported after the marines who burned in it, and
     * only when there was one — every other debrief keeps the rows it had.
     */
    @Test
    void aLostBoatAddsOneDangerRowAfterTheCasualties() {
        MissionOutcome lost = outcome(MissionSource.GENERATED, Set.of(),
                List.of(new MissionOutcome.BoatLoss("boat_03", "Aeroshuttle 03",
                                ShuttleType.AEROSHUTTLE, 4),
                        new MissionOutcome.BoatLoss("boat_05", "Aeroshuttle 05",
                                ShuttleType.AEROSHUTTLE, 0)));

        List<String> ids = rowIds(lost);

        assertEquals(List.of("result-payout", "result-casualties", "result-boats-lost"), ids);
        assertEquals("label result-value tone-danger",
                ResultsScreen.resultRows(lost, null).get(2).tone());
        assertEquals("Aeroshuttle 03, Aeroshuttle 05",
                ResultsScreen.boatLossNames(lost));
    }

    @Test
    void aMissionThatLostNoBoatSaysNothingAboutBoats() {
        MissionOutcome kept = outcome(MissionSource.GENERATED, Set.of(), List.of());

        assertEquals(List.of("result-payout", "result-casualties"), rowIds(kept));
    }

    private static List<String> rowIds(MissionOutcome outcome) {
        List<String> ids = new ArrayList<>();
        for (ResultsScreen.ResultRow row : ResultsScreen.resultRows(outcome, null)) {
            ids.add(row.id());
        }
        return ids;
    }

    private static MissionOutcome outcome(MissionSource source, Set<String> fireteams,
                                          List<MissionOutcome.BoatLoss> boatsLost) {
        return MissionOutcome.builder()
                .victory(true)
                .missionId("results-personnel")
                .missionName("Personnel Test")
                .missionType(MissionType.ASSAULT)
                .risk(RiskLevel.MEDIUM)
                .missionSource(source)
                .deployedFireteamIds(fireteams)
                .boatsLost(boatsLost)
                .build();
    }

    private static MissionOutcome outcome(MissionSource source, Set<String> fireteams) {
        return MissionOutcome.builder()
                .victory(true)
                .missionId("results-personnel")
                .missionName("Personnel Test")
                .missionType(MissionType.ASSAULT)
                .risk(RiskLevel.MEDIUM)
                .missionSource(source)
                .deployedFireteamIds(fireteams)
                .build();
    }
}
