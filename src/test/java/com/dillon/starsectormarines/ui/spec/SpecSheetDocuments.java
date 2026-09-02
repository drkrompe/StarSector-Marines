package com.dillon.starsectormarines.ui.spec;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One small authored screen the spec-sheet tests hover: four anchors placed at
 * known corners of an 800x600 document, so where the overlay lands is a fact
 * about placement rather than about a real screen's layout.
 *
 * <p>The anchors are themselves {@code position: absolute}, which is the same
 * seam the overlay uses — a placement test that could not place its own subject
 * would be measuring the fixture.
 */
final class SpecSheetDocuments {

    static final float WIDTH = 800f;
    static final float HEIGHT = 600f;

    /** Deterministic metrics so measured heights are arithmetic rather than art. */
    static final BitmapFont FONT = new FixedFont();

    private static final String SOURCE = """
            <template>
              <div id="screen" class="screen">
                <div id="near-left" class="anchor" style="left: 40px; top: 40px">A</div>
                <div id="beneath" class="anchor" style="left: 240px; top: 40px">D</div>
                <div id="near-right" class="anchor" style="left: 700px; top: 40px">B</div>
                <div id="near-bottom" class="anchor" style="left: 40px; top: 560px">C</div>
                <div id="wide" class="wide-anchor" style="left: 50px; top: 300px">E</div>
              </div>
            </template>

            <style>
              .screen { flex-direction: column; }
              .anchor { position: absolute; width: 60px; height: 20px; }
              .wide-anchor { position: absolute; width: 700px; height: 20px; }
            </style>
            """;

    private SpecSheetDocuments() {
    }

    static MarkupInstance instance() {
        MarkupLoader loader = new MarkupLoader(ignored -> SOURCE, List.of("spec-sheet-fixture.mlx"));
        loader.reload();
        return loader.build("spec-sheet-fixture", Map.of());
    }

    static UiDocument laidOut(MarkupInstance instance) {
        UiDocument document = new UiDocument(instance.root());
        for (StyleSheet sheet : instance.styles()) document.addStyleSheet(sheet);
        document.theme(theme());
        document.layout(WIDTH, HEIGHT);
        return document;
    }

    static UiTheme theme() {
        return new UiTheme(StyleSheet.parse("marine-ops-theme", ":root { font-family: body; }"),
                Map.of("body", FONT, "heading", FONT, "compact", FONT, "title", FONT));
    }

    static SpecSheet sheet(String accent, String... notes) {
        return new SpecSheet("Aegis Composite", "Line pattern · Tier 2", null, accent,
                List.of(new SpecSheet.Stat("Armour", "180", 0.6f),
                        SpecSheet.Stat.of("Tradition", "Hegemony")),
                List.of(notes));
    }

    /** Ten pixels an advance and twenty a line, so a wrap is countable. */
    private static final class FixedFont extends BitmapFont {

        private FixedFont() {
            super("spec-sheet-test-font.fnt");
        }

        @Override
        public float measureWidth(String value) {
            return value == null ? 0f : value.length() * 10f;
        }

        @Override
        public int getLineHeight() {
            return 20;
        }

        /** Greedy word wrap, so a measured line count is a real one. */
        @Override
        public List<String> wrapLines(String value, float maxWidth) {
            if (value == null || value.isEmpty()) return List.of();
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : value.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (line.length() == 0 || measureWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
            return List.copyOf(lines);
        }
    }
}
