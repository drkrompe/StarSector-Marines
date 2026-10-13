package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleLessonOverlayTest {
    @Test void fieldLessonKeepsTheBattleAndPowerTrayVisibleUnderTheScreenTheme() {
        var tray = new UiElement("lesson-power-tray").style(
                "position: absolute; left: 600px; top: 980px; width: 720px; height: 88px; pointer-events: none;");
        var document = BattleLessonOverlay.document(tray);
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of("data/ui/components/company/preparation-guide.mlx"));
        var guide = new PreparationGuide(document, () -> new PreparationGuide.Lesson(1,
                "MECH SUPPORT", "Choose a landing point.", List.of(tray.id()), null, null), () -> {}, loader);
        guide.combatInput();

        for (var theme : List.of(MarineOpsThemes.standard(), MarineOpsThemes.highContrast())) {
            document.theme(theme);
            document.layout(1920, 1080);
            guide.update();

            assertTrue(guide.visible());
            assertEquals(1920f, document.root().box().borderBox().width());
            assertEquals(1080f, document.root().box().borderBox().height());
            assertEquals(0, document.root().background().getAlpha(),
                    "The full-screen lesson host must not cover the battle beneath its spotlight");
            assertTrue(tray.background() == null || tray.background().getAlpha() == 0,
                    "The spotlight anchor must leave the real command tray visible");
        }
    }
}
