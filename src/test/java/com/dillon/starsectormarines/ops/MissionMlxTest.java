package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
