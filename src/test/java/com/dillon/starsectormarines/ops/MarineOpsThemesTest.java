package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MarineOpsThemesTest {

    @Test
    void selectedFocusAndRootEdgeSurviveThemeReplacementOnTheSameTree() {
        UiElement button = new UiElement("button")
                .tag(UiTag.BUTTON)
                .addClass("selected")
                .addClass("ui-workbench")
                .onClick(() -> { });
        UiElement root = new UiElement("root")
                .addClass("workbench-root")
                .addClass("ui-workbench")
                .child(button);
        UiDocument document = new UiDocument(root)
                .addStyleSheet(MarineOpsThemes.WORKBENCH_COMPONENTS)
                .theme(MarineOpsThemes.standard());
        document.layout(200f, 100f);
        document.requestFocus(button, true);
        document.advance(0f);
        document.advance(0.2f);

        assertEquals(new Color(0x6E, 0xD7, 0xFF), root.borderColor());
        assertEquals(new Color(0xFF, 0xD4, 0x64), button.borderColor());

        document.theme(MarineOpsThemes.highContrast());
        document.advance(0f);
        document.advance(0.2f);

        assertSame(button, root.children().get(0));
        assertEquals(new Color(0x63, 0xE8, 0xFF), root.borderColor());
        assertEquals(new Color(0xFF, 0xE4, 0x5C), button.borderColor());
    }

    @Test
    void proceduralCanvasPaletteChangesWithTheTheme() {
        MarineOpsThemes.CanvasPalette standard = MarineOpsThemes.canvasPalette(false);
        MarineOpsThemes.CanvasPalette contrast = MarineOpsThemes.canvasPalette(true);

        assertNotEquals(standard.button(), contrast.button());
        assertNotEquals(standard.edge(), contrast.edge());
        assertNotEquals(standard.muted(), contrast.muted());
    }
}
