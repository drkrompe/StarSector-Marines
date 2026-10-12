package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.RaidBattleFixture;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaidObjectivePresentationTest {
    @Test
    void checklistFollowsServiceResetSeizureAndReturnWithoutDebugCommander() throws Exception {
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor,
                ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of());
        var presentation = model.updateRaid("Dispatch archive", RaidObjective.Phase.APPROACH, 0, 6, () -> { });
        assertTrue(presentation.objectivesVisible());
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            UiDocument document = new UiDocument(instance.root());
            instance.styles().forEach(document::addStyleSheet);
            document.theme(MarineOpsThemes.standard());
            document.layout(1896, BattleHudOverlay.OBJECTIVE_HEIGHT);
            assertEquals("0 / 2", instance.requireElement("battle-objective-score").text());
            assertTrue(instance.requireElement("battle-raid-return").text().contains("NEXT"));
            assertTrue(instance.requireElement("battle-objective-focus-label").text().contains("6s"));
            model.updateRaid("Dispatch archive", RaidObjective.Phase.SERVICE, 3, 6, () -> { });
            instance.flush();
            assertEquals("Securing target / 50%", instance.requireElement("battle-objective-focus-label").text());
            model.updateRaid("Dispatch archive", RaidObjective.Phase.APPROACH, 0, 6, () -> { });
            instance.flush();
            assertEquals("0 / 2", instance.requireElement("battle-objective-score").text());
            model.updateRaid("Dispatch archive", RaidObjective.Phase.EGRESS, 6, 6, () -> { });
            instance.flush();
            assertEquals("1 / 2", instance.requireElement("battle-objective-score").text());
            assertTrue(instance.requireElement("battle-raid-seize").text().contains("DONE"));
            assertTrue(instance.requireElement("battle-raid-return").text().contains("ACTIVE"));
            assertTrue(instance.requireElement("battle-objective-focus-label").text().contains("Return to landing zone"));
            model.updateRaid("Dispatch archive", RaidObjective.Phase.COMPLETE, 6, 6, () -> { });
            instance.flush();
            assertEquals("2 / 2", instance.requireElement("battle-objective-score").text());
            model.updateProjected(1f, List.of());
            instance.flush();
            assertTrue(instance.requireElement("battle-objectives").hasClass("objective-panel-hidden"));
            assertTrue(instance.requireElement("battle-objective-locate").hasClass("objective-locate-hidden"));
            assertFalse(instance.requireElement("battle-objective-locate").focusable());
        }
    }

    @Test
    void archiveNameSurvivesFixtureRoundTripAndOldRaidStillLoads() throws Exception {
        var chapter = CorvusChapter.DUST_LEDGER;
        RaidBattleFixture fixture = (RaidBattleFixture) chapter.fixture(12, List.of(), chapter.profile(chapter.surface));
        assertEquals("Dispatch archive", fixture.targetName());
        var json = BattleFixtureJson.toJson(fixture);
        assertEquals(fixture, BattleFixtureJson.fromJson(json));
        json.remove("targetName");
        json.remove("targetKind");
        assertNull(((RaidBattleFixture) BattleFixtureJson.fromJson(json)).targetName());
        assertNull(((RaidBattleFixture) BattleFixtureJson.fromJson(json)).targetKind());
    }

    @Test
    void markersPromoteReturnOnlyAfterSeizure() {
        assertEquals("AFTER SEIZURE", BattlefieldMarkerPresentation.raid(
                true, RaidObjective.Phase.SERVICE, .5f).status());
        assertEquals("SEIZING / 50%", BattlefieldMarkerPresentation.raid(
                false, RaidObjective.Phase.SERVICE, .5f).status());
        assertEquals("SECURED", BattlefieldMarkerPresentation.raid(
                false, RaidObjective.Phase.EGRESS, 1).status());
        assertEquals("RETURN HERE", BattlefieldMarkerPresentation.raid(
                true, RaidObjective.Phase.EGRESS, 1).status());
    }
}
