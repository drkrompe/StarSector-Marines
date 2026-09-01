package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
                BriefingScreen.previewProps(true))) {
            assertTrue(instance.requireElement("tier-first").disabled());
            assertTrue(instance.requireElement("tier-established").disabled());
            assertTrue(instance.requireElement("tier-veteran").disabled());
            assertFalse(instance.requireElement("tier-reinforced").disabled());
            assertTrue(instance.requireElement("tier-full").hasClass("selected"));
            instance.requireElement("mission-debug-controls");
            instance.requireElement("mission-commitment-scroll");
        }
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
