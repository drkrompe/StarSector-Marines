package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiKey;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DebriefRewardPromptTest {
    @Test void acknowledgedOutcomeDoesNotNagButAnUnfinishedRewardReturnsOnTheNextDebrief() {
        var roster = new MarineRoster();
        var prompt = new DebriefRewardPrompt(new Reactor(), () -> {}, () -> {});
        var outcome = outcome();
        prompt.present(outcome, roster); assertFalse(prompt.visible());
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        prompt.present(outcome, roster); assertTrue(prompt.visible());
        prompt.dismiss();
        prompt.present(outcome, roster); assertFalse(prompt.visible());
        prompt.present(outcome(), roster); assertTrue(prompt.visible());
        assertFalse(roster.firstRefit().claimed(), "Showing or deferring the prompt never claims technology");
        roster.firstRefit().claim(roster, FirstRefit.Path.STEADY_LINE);
        prompt.present(outcome(), roster);
        assertEquals("FIT FREE EQUIPMENT", prompt.row().get().get("actionLabel"));
        roster.firstRefit().issue(roster, roster.createSquad().id(), FirstRefit.Path.STEADY_LINE, EquipmentIssueResources.UNLIMITED);
        prompt.present(outcome(), roster); assertFalse(prompt.visible());
        prompt.present(MissionOutcome.builder().missionSource(MissionSource.DEBUG).build(), new MarineRoster());
        assertFalse(prompt.visible());
    }

    @Test void modalStopsDebriefClickThroughAndEscapeOnlyDefersTheOffer() throws Exception {
        var roster = new MarineRoster();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var reactor = new Reactor();
        int[] chosen = {0}, later = {0}, departed = {0};
        var prompt = new DebriefRewardPrompt(reactor, () -> chosen[0]++, () -> later[0]++);
        prompt.present(outcome(), roster);
        var props = ResultsScreen.previewProps(true, true);
        props.put("rewardPrompt", prompt.row());
        props.put("primaryAction", (Runnable) () -> departed[0]++);
        var loader = new MarkupLoader(file -> Files.readString(Path.of("mod").resolve(file)), ResultsScreen.COMPONENT_PATHS);
        try (var markup = loader.reloadAndBuild(reactor, ResultsScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            prompt.bind(document, markup, () -> departed[0]++);
            document.layout(1920, 1080);
            var behind = markup.requireElement("mission-results-primary").box().borderBox();
            document.pointerDown(behind.x()+behind.width()/2, behind.y()+behind.height()/2);
            document.pointerUp(behind.x()+behind.width()/2, behind.y()+behind.height()/2);
            assertEquals(0, departed[0]); assertTrue(prompt.visible());
            document.keyPressed(UiKey.ESCAPE, Set.of(), false);
            markup.flush();
            assertFalse(prompt.visible()); assertEquals(1, later[0]); assertEquals(0, departed[0]);
            assertFalse(roster.firstRefit().claimed());
            prompt.present(outcome(), roster); markup.flush(); document.layout(1920, 1080);
            var action = markup.requireElement("reward-open").box().borderBox();
            assertTrue(action.width() <= 300 && action.height() <= 48);
            document.pointerDown(action.x()+action.width()/2, action.y()+action.height()/2);
            document.pointerUp(action.x()+action.width()/2, action.y()+action.height()/2);
            assertEquals(1, chosen[0]); assertFalse(prompt.visible()); assertEquals(0, departed[0]);
        }
    }
    private static MissionOutcome outcome() {
        return MissionOutcome.builder().victory(true).missionSource(MissionSource.STORY)
                .missionId(OpeningOperationKind.RELIEF.missionId).build();
    }
}

