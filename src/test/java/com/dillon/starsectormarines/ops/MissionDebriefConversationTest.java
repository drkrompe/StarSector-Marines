package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;
import com.dillon.starsectormarines.battle.setup.BlindWatchSequence;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Warning;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MissionDebriefConversationTest {
    @Test void firstWaylinePaymentIsOneWrittenCastleLine() throws Exception {
        var catalog = catalog();
        for (int lost : List.of(0, 2, 12)) {
            var outcome = MissionOutcome.builder().missionId(OpeningOperationKind.RELIEF.missionId)
                    .missionSource(MissionSource.STORY).victory(true).marinesEngaged(12).marinesLost(lost).build();
            var lines = MissionDebriefConversation.lines(outcome, catalog);
            assertEquals(1, lines.size(), "One Castle line, whatever the losses");
            assertTrue(lines.get(0).speaker().startsWith("CASTLE"));
            assertTrue(MissionDebriefConversation.recordings(outcome, catalog).isEmpty());
            var props = ResultsScreen.previewProps(true, false);
            ResultsScreen.putStoryDebrief(props, outcome);
            assertEquals("hidden", props.get("salvagePlaybackClasses"));
            assertFalse(props.get("salvageClasses").equals("hidden"));
        }
        assertTrue(MissionDebriefConversation.lines(outcome(OpeningOperationKind.RELIEF.missionId,
                MissionSource.STORY, false), catalog).isEmpty());
        assertTrue(MissionDebriefConversation.lines(outcome(OpeningOperationKind.RELIEF.missionId,
                MissionSource.RECURRING, true), catalog).isEmpty());
    }

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
                assertTrue(lines.get(i).speaker().startsWith(i % 2 == 0 ? "CASTLE" : "ZOEY"));
                assertTrue(lines.get(i).voiced());
                assertFalse(lines.get(i).terminal());
            }
            assertEquals("AFTER ACTION / CASTLE & ZOEY", MissionDebriefConversation.title(outcome));
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

    @Test void blindWatchDebriefFollowsTheWarningTheScheduleTheRiggersAndTheCost() throws Exception {
        var catalog = catalog();
        var quiet = new BlindWatchSequence.Report(false, Warning.STOPPED, .2f, State.DONE, State.OFFERED, State.DONE, false);
        var outcome = MissionOutcome.builder().missionId("wayline_corvus_v1_1_2").missionSource(MissionSource.STORY)
                .victory(true).marinesEngaged(36).marinesLost(0).blindWatch(quiet).build();
        assertEquals(List.of("opening.quiet", "routing.quiet", "initiative", "riggers.freed", "cold_store.logged",
                        "cost.clean", "ready"),
                MissionDebriefConversation.lines(outcome, catalog).stream()
                        .map(l -> l.event().substring("debrief.blind_watch.".length())).toList());
        // The warning outranks a false schedule sent before it; the manifest is said only when it was logged.
        var warned = new BlindWatchSequence.Report(true, Warning.SENT, 1f, State.FAILED, State.DONE, State.LAPSED, true);
        assertEquals(List.of("opening.warned", "routing.warned", "initiative", "riggers.lost", "cost.losses", "ready"),
                MissionDebriefConversation.blindWatchBeats(warned, 3));
        var schedule = new BlindWatchSequence.Report(false, Warning.FALSE_SCHEDULE, .4f, State.WORKING, State.DONE,
                State.OFFERED, false);
        assertEquals(List.of("opening.quiet", "routing.schedule", "initiative", "riggers.left", "cost.clean", "ready"),
                MissionDebriefConversation.blindWatchBeats(schedule, 0));
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

    @Test void onlyTheWonFreightRunClosesJangalaAndPointsAtAsharu() throws Exception {
        record Run(boolean victory, List<Integer> delivered, List<Integer> lost, int marinesLost) { }
        for (var run : List.of(new Run(true, List.of(0, 1, 2), List.of(), 0), new Run(true, List.of(0, 1), List.of(2), 3),
                new Run(false, List.of(), List.of(0, 1, 2), 0), new Run(false, List.of(), List.of(0), 0))) {
            var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
            String junction = route.missionId(); route.begin(junction); route.settle(junction, true, null);
            String convoy = route.missionId(); route.begin(convoy);
            var cargo = new FreightRunProgress.Outcome(route.freight().previewAttempt(), run.delivered(), run.lost(), run.victory());
            route.settle(convoy, run.victory(), null, cargo);
            var outcome = MissionOutcome.builder().missionId(convoy).missionSource(MissionSource.STORY)
                    .victory(run.victory()).marinesLost(run.marinesLost()).freightOutcome(cargo).build();
            assertEquals(run.victory(), MissionDebriefConversation.jangalaClosing(outcome, route),
                    "an exhausted account is a failure, not a goodbye");
            if (!run.victory()) continue;
            var lines = MissionDebriefConversation.jangalaLines(catalog());
            assertEquals(4, lines.size());
            assertTrue(lines.get(0).speaker().startsWith("ZOEY"));
            var next = lines.get(lines.size() - 1);
            assertTrue(next.speaker().startsWith("CASTLE"));
            assertTrue(next.text().contains("Asharu"));
            assertTrue(lines.stream().allMatch(WaylineNarration.Line::voiced));
        }
    }

    @Test void laterRouteSecurityIsNotTheGoodbye() {
        var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String junction = route.missionId(); route.begin(junction); route.settle(junction, true, null);
        String convoy = route.missionId(); route.begin(convoy);
        var cargo = new FreightRunProgress.Outcome(route.freight().previewAttempt(), List.of(0, 1, 2), List.of(), true);
        route.settle(convoy, true, null, cargo);
        String security = route.missionId(); route.begin(security); route.settle(security, true, null);
        var outcome = MissionOutcome.builder().missionId(convoy).missionSource(MissionSource.STORY)
                .victory(true).freightOutcome(cargo).build();
        assertFalse(MissionDebriefConversation.jangalaClosing(outcome, route));
        assertFalse(MissionDebriefConversation.jangalaClosing(outcome, null));
    }

    private static MissionOutcome outcome(String id, MissionSource source, boolean victory) {
        return MissionOutcome.builder().missionId(id).missionSource(source).victory(victory).build();
    }
}
