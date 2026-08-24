package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.markup.MarkupParser;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MarineOpsThemesTest {

    @Test
    void selectedFocusAndRootEdgeSurviveThemeReplacementOnTheSameTree() throws IOException {
        UiElement button = new UiElement("button")
                .tag(UiTag.BUTTON)
                .addClass("selected")
                .addClass("mlx-ui-workbench")
                .onClick(() -> { });
        UiElement root = new UiElement("root")
                .addClass("workbench-root")
                .addClass("mlx-ui-workbench")
                .child(button);
        UiDocument document = new UiDocument(root)
                .addStyleSheet(workbenchStyle())
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

    @Test
    void bodyHeadingAndTitleRolesResolveToTheRetainedTypographyHierarchy() throws IOException {
        UiElement body = new UiElement("body").addClass("mlx-ui-workbench");
        UiElement heading = new UiElement("heading")
                .addClass("mlx-ui-workbench")
                .addClass("heading");
        UiElement title = new UiElement("title")
                .addClass("mlx-ui-workbench")
                .addClass("title");
        UiElement root = new UiElement("root")
                .addClass("mlx-ui-workbench")
                .child(body)
                .child(heading)
                .child(title);
        UiDocument document = new UiDocument(root)
                .addStyleSheet(workbenchStyle())
                .theme(MarineOpsThemes.standard());

        document.styles().resolve(root);

        assertSame(Fonts.INSIGNIA_15_AA, document.styles().fontFor(body));
        assertSame(Fonts.ORBITRON_12_BOLD, document.styles().fontFor(heading));
        assertSame(Fonts.ORBITRON_16, document.styles().fontFor(title));
    }

    private static StyleSheet workbenchStyle() throws IOException {
        Path path = Path.of("mod/data/ui/components/dev/ui-workbench.mlx");
        return MarkupParser.parse(path.toString(), Files.readString(path)).style();
    }
}
