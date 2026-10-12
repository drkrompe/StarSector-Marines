package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MissionDebriefConversationTest {
    private static WaylineNarration catalog() throws Exception {
        return WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH))));
    }

    @Test void successfulAttemptsShareAnOrderedCastleMaraDebrief() throws Exception {
        var catalog = catalog();
        for (String id : List.of("wayline_corvus_v1_2_1", "wayline_corvus_v1_2_12",
                DebugMissionCatalog.Scenario.COLD_ACCOUNT.mission().id)) {
            var source = id.startsWith("wayline_") ? MissionSource.STORY : MissionSource.DEBUG;
            var outcome = outcome(id, source, true);
            var lines = MissionDebriefConversation.lines(outcome, catalog);
            assertEquals(List.of("orders", "consideration", "one_ship", "payroll", "way_out", "debt"),
                    lines.stream().map(l -> l.event().substring("debrief.cold_account.".length())).toList());
            for (int i = 0; i < lines.size(); i++) {
                assertTrue(lines.get(i).speaker().startsWith(i % 2 == 0 ? "CASTLE" : "MARA"));
                assertTrue(lines.get(i).voiced());
                assertFalse(lines.get(i).terminal());
            }
            assertEquals("AFTER ACTION / CASTLE & MARA", MissionDebriefConversation.title(outcome));
        }
    }

    @Test void blindWatchRecognizesSuccessWithoutClaimingACasualtyFreeCompany() throws Exception {
        var catalog = catalog();
        for (int losses : List.of(0, 12)) {
            var outcome = MissionOutcome.builder().missionId("wayline_corvus_v1_1_2")
                    .missionSource(MissionSource.STORY).victory(true).marinesEngaged(36).marinesLost(losses).build();
            var lines = MissionDebriefConversation.lines(outcome, catalog);
            assertEquals(List.of("debrief.blind_watch.initiative", "debrief.blind_watch.ready"),
                    lines.stream().map(WaylineNarration.Line::event).toList());
            assertTrue(lines.stream().allMatch(WaylineNarration.Line::voiced));
            var props = ResultsScreen.previewProps(true, false);
            ResultsScreen.putStoryDebrief(props, outcome);
            assertEquals(losses > 0, props.get("storyMessage").toString().contains("paid for this in lives"));
        }
    }

    @Test void unrelatedOutcomesDoNotInventTheInterventionAndSalvageStillWorks() throws Exception {
        var catalog = catalog();
        assertTrue(MissionDebriefConversation.lines(null, catalog).isEmpty());
        for (var outcome : List.of(
                outcome("wayline_corvus_v1_2_1", MissionSource.STORY, false),
                outcome("wayline_corvus_v1_2_1", MissionSource.RECURRING, true),
                outcome("wayline_corvus_v1_1_1", MissionSource.STORY, false),
                outcome("wayline_corvus_v1_1_1", MissionSource.RECURRING, true),
                outcome("wayline_corvus_v1_2_0", MissionSource.STORY, true))) {
            assertTrue(MissionDebriefConversation.lines(outcome, catalog).isEmpty());
        }
        var salvage = MissionOutcome.builder().missionId(OpeningOperationKind.COUNTERATTACK.missionId)
                .missionSource(MissionSource.STORY).victory(true)
                .waylineHound(new WaylineHoundRecovery.Report(true, false)).build();
        assertEquals(HoundSalvageDebrief.lines(salvage, catalog), MissionDebriefConversation.lines(salvage, catalog));
        assertEquals("RECOVERY AGREEMENT / TRANSCRIPT", MissionDebriefConversation.title(salvage));
    }

    private static MissionOutcome outcome(String id, MissionSource source, boolean victory) {
        return MissionOutcome.builder().missionId(id).missionSource(source).victory(victory).build();
    }
}
