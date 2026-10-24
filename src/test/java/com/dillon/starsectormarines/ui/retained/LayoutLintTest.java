package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutLintTest {

    @Test
    void wrappedTextTallerThanItsFixedBoxIsClippedText() {
        UiElement body = new UiElement("body").text("ONE TWO THREE")
                .style("width: 50px; height: 20px; white-space: normal");
        List<String> findings = lint(new UiElement("root").style("align-items: start").child(body));
        assertEquals(List.of("text-clip #root > #body 40.0"), findings);
    }

    @Test
    void oneLineAQuarterOfItsHeightTooTallStillReadsAndIsOnlyTight() {
        UiElement label = new UiElement("label").text("ONE").style("width: 50px; height: 15px");
        assertEquals(List.of("text-tight #root > #label 5.0"),
                lint(new UiElement("root").style("align-items: start").child(label)));
    }

    @Test
    void aChildCrossingAClippingParentSpillsOnceForItsWholeSubtree() {
        UiElement inner = new UiElement("inner").style("height: 80px");
        UiElement card = new UiElement("card").style("height: 70px; flex-direction: column").child(inner);
        UiElement panel = new UiElement("panel").style("width: 100px; height: 50px; overflow: hidden; flex-direction: column").child(card);
        assertEquals(List.of("clip-spill #root > #panel > #card 20.0"),
                lint(new UiElement("root").style("align-items: start").child(panel)));
    }

    @Test
    void aZeroSizeHideStillTakesAGapSlot() {
        UiElement column = new UiElement("column").style("flex-direction: column; gap: 6px")
                .child(new UiElement("shown").style("height: 10px"))
                .child(new UiElement("hidden").style("width: 0px; height: 0px; opacity: 0"))
                .child(new UiElement("after").style("height: 10px"));
        assertEquals(List.of("hidden-gap #root > #column > #hidden 6.0"),
                lint(new UiElement("root").style("align-items: start").child(column)));
    }

    @Test
    void repeatedInstancesOfOneMarkupNodeFoldIntoOnePattern() {
        assertEquals("#stock > #transfer-stock:* > #shade-#",
                LayoutLint.pattern("#stock > #transfer-stock:rare_metals > #shade-3"));
    }

    /** Each finding as kind, element and overflow; where it sits is the box the layout gave it. */
    private static List<String> lint(UiElement root) {
        UiDocument document = new UiDocument(root).theme(new UiTheme(
                StyleSheet.parse("test-theme", ":root { font-family: body; }"), Map.of("body", new FixedFont())));
        document.layout(200f, 100f);
        return LayoutLint.inspect(document).stream()
                .map(finding -> finding.kind().label() + " " + finding.element() + " " + finding.overflow())
                .toList();
    }

    /** Ten px per character, twenty px per line, one word per line once a string does not fit. */
    private static final class FixedFont extends BitmapFont {
        private FixedFont() {
            super("unused-test-font.fnt");
        }

        @Override
        public float measureWidth(String value) {
            return value == null ? 0f : value.length() * 10f;
        }

        @Override
        public int getLineHeight() {
            return 20;
        }

        @Override
        public List<String> wrapLines(String value, float maxWidth) {
            if (value == null || value.isEmpty()) return List.of();
            if (measureWidth(value) <= maxWidth) return List.of(value);
            return List.of(value.split(" "));
        }
    }
}
