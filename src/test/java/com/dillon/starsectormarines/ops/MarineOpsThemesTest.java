package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.UiTextAlign;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MarineOpsThemesTest {

    @Test
    void aCommandKeyCentresItsLabelInEveryTheme() {
        for (var theme : new UiTheme[]{MarineOpsThemes.standard(), MarineOpsThemes.highContrast()}) {
            UiElement key = new UiElement("key").tag(UiTag.BUTTON).addClass("console-action").text("BACK TO DECK");
            UiElement plain = new UiElement("plain").tag(UiTag.BUTTON).text("Row");
            UiDocument document = new UiDocument(new UiElement("root").child(key).child(plain)).theme(theme);
            document.layout(200f, 100f);
            document.advance(0f);
            assertEquals(UiTextAlign.CENTER, key.textAlign(), "no screen should have to centre its own command keys");
            assertEquals(UiTextAlign.START, plain.textAlign(), "list-row buttons keep their labels where they put them");
        }
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
