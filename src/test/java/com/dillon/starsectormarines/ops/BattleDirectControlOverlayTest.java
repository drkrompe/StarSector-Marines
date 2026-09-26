package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BattleDirectControlOverlayTest {
    @Test
    void retainedButtonFitsItsViewportAndOnlyEligibleActionFires() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            AtomicInteger actions = new AtomicInteger();
            try (MarkupInstance markup = fixture(Path.of("mod"), false, enabled,
                    actions::incrementAndGet)) {
                UiDocument document = document(markup);
                var button = markup.requireElement("battle-direct-control-toggle");
                var box = button.box().borderBox();
                assertEquals(!enabled, button.disabled());
                assertTrue(box.x() >= 0 && box.right() <= 440f);
                assertTrue(box.y() >= 0 && box.bottom() <= 64f);
                assertTrue(markup.requireElement("battle-direct-control-hint")
                        .box().borderBox().bottom() <= 64f);
                document.pointerDown(box.x() + 20, box.y() + 12);
                document.pointerUp(box.x() + 20, box.y() + 12);
                assertEquals(enabled ? 1 : 0, actions.get());
            }
        }
    }

    static MarkupInstance fixture(Path modRoot, boolean active, boolean enabled,
                                  Runnable action) throws Exception {
        return fixture(modRoot, active, enabled, false, action);
    }

    static MarkupInstance fixture(Path modRoot, boolean active, boolean enabled,
                                  boolean mech, Runnable action) throws Exception {
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(modRoot.resolve(path)),
                List.of(BattleDirectControlOverlay.COMPONENT_PATH));
        loader.reload();
        return loader.build(new Reactor(), BattleDirectControlOverlay.COMPONENT, Map.of(
                "label", active ? "Return to command [C / Esc]" : "Control selected unit [C]",
                "hint", active ? mech ? "Bulwark 1  |  WASD pivot/walk | Mouse aim | LMB direct mounts"
                        : "Rhea Voss  |  WASD move | Mouse aim | Hold LMB fire"
                        : enabled ? "Direct control uses pause or 1x speed"
                        : "Select a ready Marine or combat Mech",
                "disabled", !enabled, "toggle", action));
    }

    static UiDocument document(MarkupInstance markup) {
        UiDocument document = new UiDocument(markup.root());
        for (var style : markup.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(440f, 64f);
        return document;
    }
}
