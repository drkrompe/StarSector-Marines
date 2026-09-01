package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionMlxTest {

    @Test
    void debugCatalogBuildsItsThreePaneWorkspace() throws Exception {
        MarkupLoader loader = loader(MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (MarkupInstance instance = loader.build(new Reactor(),
                MissionSelectScreen.ROOT_COMPONENT,
                MissionSelectScreen.previewProps())) {
            instance.requireElement("mission-client-list");
            instance.requireElement("mission-list");
            instance.requireElement("mission-detail-panel");
            instance.requireElement("mission-preview-2");
            assertTrue(instance.requireElement("mission-preview-2").hasClass("selected"));
        }
    }

    @Test
    void conquestBriefingMakesItsLateGameFloorVisible() throws Exception {
        MarkupLoader loader = loader(BriefingScreen.COMPONENT_PATHS);
        loader.reload();
        try (MarkupInstance instance = loader.build(new Reactor(),
                BriefingScreen.ROOT_COMPONENT,
                BriefingScreen.previewProps(true, true))) {
            assertTrue(instance.requireElement("mission-debug-drawer")
                    .hasClass("expanded"));
            assertTrue(instance.requireElement("mission-debug-workspace")
                    .hasClass("panel"));
            assertTrue(instance.requireElement("tier-first").disabled());
            assertTrue(instance.requireElement("tier-established").disabled());
            assertTrue(instance.requireElement("tier-veteran").disabled());
            assertFalse(instance.requireElement("tier-reinforced").disabled());
            assertTrue(instance.requireElement("tier-full").hasClass("selected"));
            instance.requireElement("mission-debug-controls");
            instance.requireElement("mission-debug-air-heading");
            instance.requireElement("mission-loadout-grid");
            instance.requireElement("mission-power-list");
            assertTrue(instance.requireElement("mission-assign")
                    .hasClass("briefing-assign-hidden"));
            assertTrue(instance.requireElement("debug-squads-cycle")
                    .hasClass("debug-cycle-absent"));
        }
    }

    @Test
    void debugDrawerStartsCollapsed() throws Exception {
        MarkupLoader loader = loader(BriefingScreen.COMPONENT_PATHS);
        loader.reload();
        try (MarkupInstance instance = loader.build(new Reactor(),
                BriefingScreen.ROOT_COMPONENT,
                BriefingScreen.previewProps(false))) {
            assertTrue(instance.requireElement("mission-debug-drawer")
                    .hasClass("collapsed"));
            assertTrue(instance.requireElement("mission-debug-workspace")
                    .hasClass("debug-workspace-collapsed"));
            assertFalse(instance.requireElement("mission-debug-toggle").disabled());
        }
    }

    @Test
    void squadDeploymentBuildsA1080pRosterWorkspace() throws Exception {
        MarkupLoader loader = loader(SquadDeploymentScreen.COMPONENT_PATHS);
        loader.reload();
        try (MarkupInstance instance = loader.build(new Reactor(),
                SquadDeploymentScreen.ROOT_COMPONENT,
                SquadDeploymentScreen.previewProps())) {
            instance.requireElement("squad-deployment-summary");
            instance.requireElement("squad-deployment-grid");
            assertTrue(instance.requireElement("deployment-preview-0").hasClass("selected"));
            assertEquals("graphics/portraits/portrait_mercenary01.png",
                    instance.requireElement("deployment-preview-0-commander-portrait")
                            .imageSource());
            assertEquals("Lt. Mira Hale",
                    instance.requireElement("deployment-preview-0-commander-name").text());
            assertEquals("HOME COMMAND · SELECTED",
                    instance.requireElement("deployment-preview-0-commander-status").text());
            assertEquals("HOME COMMAND · AVAILABLE",
                    instance.requireElement("deployment-preview-2-commander-status").text());
            assertEquals("3 WIA · 1 KIA",
                    instance.requireElement("deployment-preview-2-casualties").text());
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                for (int slot = 0; slot < MarineSquad.TEAM_SIZE; slot++) {
                    instance.requireElement("deployment-preview-member-0-" + team + "-" + slot);
                }
            }
            assertEquals("Corporal Hale",
                    instance.requireElement("squad-deployment-inspector-title").text());
            assertTrue(instance.requireElement("squad-deployment-inspector-firepower")
                    .text().contains("FIREPOWER"));
            assertFalse(instance.requireElement("squad-deployment-inspector")
                    .hasClass("empty"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void hoveringADeploymentBilletProjectsThatMarinesLoadout() throws Exception {
        MarkupLoader loader = loader(SquadDeploymentScreen.COMPONENT_PATHS);
        loader.reload();
        Map<String, Object> props = SquadDeploymentScreen.previewProps();
        try (MarkupInstance instance = loader.build(new Reactor(),
                SquadDeploymentScreen.ROOT_COMPONENT, props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard()).layout(1920f, 1080f);
            SquadDeploymentScreen.MemberInspector inspector =
                    SquadDeploymentScreen.MemberInspector.bind(instance,
                            (List<SquadDeploymentScreen.SquadRow>) props.get("squadRows"));

            Rect vega = instance.requireElement("deployment-preview-member-0-0-1")
                    .box().borderBox();
            document.pointerMoved(vega.x() + vega.width() * 0.5f,
                    vega.y() + vega.height() * 0.5f);
            inspector.update();

            assertEquals("Marine Vega",
                    instance.requireElement("squad-deployment-inspector-title").text());
            assertTrue(instance.requireElement("squad-deployment-inspector-primary")
                    .text().contains("FIELD RIFLE"));

            document.pointerMoved(4f, 4f);
            inspector.update();
            assertEquals("HOVER A MARINE",
                    instance.requireElement("squad-deployment-inspector-title").text());
            assertTrue(instance.requireElement("squad-deployment-inspector")
                    .hasClass("empty"));
        }
    }

    @Test
    void stationingBuildsOfferAndPendingResponseModes() throws Exception {
        MarkupLoader loader = loader(StationingScreen.COMPONENT_PATHS);
        loader.reload();
        try (MarkupInstance offer = loader.build(new Reactor(),
                StationingScreen.ROOT_COMPONENT,
                StationingScreen.previewProps(false, false));
             MarkupInstance response = loader.build(new Reactor(),
                     StationingScreen.ROOT_COMPONENT,
                     StationingScreen.previewProps(true, true))) {
            assertFalse(offer.requireElement("stationing-primary").disabled());
            assertTrue(offer.requireElement("stationing-secondary").hasClass("hidden"));
            assertFalse(response.requireElement("stationing-secondary").disabled());
            assertTrue(response.requireElement("stationing-primary").disabled());
            assertTrue(response.requireElement("stationing-notice").hasClass("warning"));
        }
    }

    @Test
    void resultsAndLootBuildTheFrozenSettlementHandoff() throws Exception {
        MarkupLoader resultsLoader = loader(ResultsScreen.COMPONENT_PATHS);
        resultsLoader.reload();
        MarkupLoader lootLoader = loader(LootScreen.COMPONENT_PATHS);
        lootLoader.reload();
        try (MarkupInstance results = resultsLoader.build(new Reactor(),
                ResultsScreen.ROOT_COMPONENT,
                ResultsScreen.previewProps(true, true));
             MarkupInstance loot = lootLoader.build(new Reactor(),
                     LootScreen.ROOT_COMPONENT,
                     LootScreen.previewProps())) {
            assertTrue(results.requireElement("mission-results-outcome").hasClass("victory"));
            assertFalse(results.requireElement("mission-results-secondary").hasClass("hidden"));
            assertTrue(loot.requireElement("loot-preview-0").hasClass("selected"));
            assertTrue(loot.requireElement("loot-preview-4").disabled());
        }
    }

    @Test
    void tierSelectorOwnsTheDebugCompanyStage() {
        assertEquals(DebugCompanyStage.FIRST_CONTRACT,
                BriefingScreen.debugCompanyStageFor(OperationTier.FIRST_CONTRACT));
        assertEquals(DebugCompanyStage.ESTABLISHED,
                BriefingScreen.debugCompanyStageFor(OperationTier.ESTABLISHED));
        assertEquals(DebugCompanyStage.VETERAN_COMPANY,
                BriefingScreen.debugCompanyStageFor(OperationTier.VETERAN));
        assertEquals(DebugCompanyStage.REINFORCED,
                BriefingScreen.debugCompanyStageFor(OperationTier.REINFORCED));
        assertEquals(DebugCompanyStage.FULL_STRENGTH,
                BriefingScreen.debugCompanyStageFor(OperationTier.FULL_STRENGTH));

        MarineOpsContext context = new MarineOpsContext(null);
        context.setDebugSquadCount(9);
        context.setDebugCompanyStage(DebugCompanyStage.REINFORCED);
        assertEquals(DebugCompanyStage.REINFORCED, context.getDebugCompanyStage());
        assertEquals(DebugCompanyStage.REINFORCED.squads,
                context.getDebugSquadCount());
    }

    private static MarkupLoader loader(java.util.List<String> paths) {
        return new MarkupLoader(path -> {
            try {
                return Files.readString(Path.of("mod").resolve(path));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }, paths);
    }
}
