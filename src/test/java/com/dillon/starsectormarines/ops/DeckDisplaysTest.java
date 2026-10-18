package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckDisplaysTest {
    private static final Path MOD = Path.of("mod");

    private static CommandDeckLayout shipped() throws Exception {
        return CommandDeckLayout.parse(Files.readString(MOD.resolve(CommandDeckLayout.PATH)));
    }

    @Test
    void shippedDisplaysLieInsideThePackedPlate() throws Exception {
        CommandDeckLayout layout = shipped();
        assertFalse(layout.displays().isEmpty());
        Set<DeckDisplays.Kind> kinds = EnumSet.noneOf(DeckDisplays.Kind.class);
        for (CommandDeckLayout.Display display : layout.displays()) {
            kinds.add(DeckDisplays.Kind.of(display.kind()));
            for (int i = 0; i < 4; i++) {
                assertTrue(display.x(i) >= 0f && display.x(i) <= layout.width(), display.id() + " x" + i);
                assertTrue(display.y(i) >= 0f && display.y(i) <= layout.height(), display.id() + " y" + i);
            }
            // Corners run clockwise from top-left: the face has positive area in canvas (y-down) terms.
            float area = 0f;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                area += display.x(i) * display.y(j) - display.x(j) * display.y(i);
            }
            assertTrue(area > 0f, display.id() + " winds clockwise");
        }
        assertEquals(EnumSet.allOf(DeckDisplays.Kind.class), kinds, "every content kind is on some screen");
    }

    @Test
    void layoutWithoutDisplaysStillParses() {
        CommandDeckLayout layout = CommandDeckLayout.parse("""
                {"size": [100, 50], "plate": "p.png", "figures": [], "stations": [],
                 "overlays": {"plot": [], "screens": []}}
                """);
        assertTrue(layout.displays().isEmpty());
    }

    @Test
    void unknownKindShowsTelemetry() {
        assertEquals(DeckDisplays.Kind.TELEMETRY, DeckDisplays.Kind.of("holo-vid"));
        assertEquals(DeckDisplays.Kind.SWEEP, DeckDisplays.Kind.of("sweep"));
    }

    @Test
    void unitSquareCornersLandOnTheQuadCorners() {
        CommandDeckLayout.Display display = new CommandDeckLayout.Display("d", "bars",
                new float[]{10f, 20f, 60f, 12f, 62f, 50f, 9f, 58f});
        CommandDeckLayout.Fit fit = new CommandDeckLayout.Fit(1f, 0f, 0f);
        DeckDisplays.Screen screen = new DeckDisplays.Screen(display, fit, 0f, 0f);
        float[][] unit = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};
        for (int i = 0; i < 4; i++) {
            assertEquals(display.x(i), screen.mapX(unit[i][0], unit[i][1]), 1e-3f);
            assertEquals(display.y(i), screen.mapY(unit[i][0], unit[i][1]), 1e-3f);
        }
    }

    @Test
    void drawsEveryScreenAndMovesBetweenTimes() throws Exception {
        CommandDeckLayout layout = shipped();
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(MOD.resolve(path)));
        try {
            DeckDisplays displays = new DeckDisplays();
            Recording early = new Recording();
            Recording late = new Recording();
            CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(1872f, 372f, layout.width(), layout.height());
            displays.draw(early, layout, fit, 1.0f, 0f);
            displays.draw(late, layout, fit, 2.5f, 0.8f);
            assertTrue(early.triangles.size() > 200, "screens carry content");
            assertEquals(layout.displays().size(), early.sprites, "one bloom per screen");
            assertNotEquals(early.triangles, late.triangles, "content moves with time");
            Recording again = new Recording();
            displays.draw(again, layout, fit, 1.0f, 0f);
            assertEquals(early.triangles, again.triangles, "a pure function of time");
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    @Test
    void drawsWithoutSvgAssets() throws Exception {
        CommandDeckLayout layout = shipped();
        SvgAsset.Source previous = SvgAsset.setSource(path -> { throw new IllegalStateException("no assets"); });
        try {
            Recording canvas = new Recording();
            new DeckDisplays().draw(canvas, layout, new CommandDeckLayout.Fit(1f, 0f, 0f), 3f, 0f);
            assertTrue(canvas.triangles.size() > 100);
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    @Test
    void alphaQuantizesToFewBatches() {
        assertEquals(0, DeckDisplays.quantize(2));
        assertEquals(255, DeckDisplays.quantize(300));
        assertEquals(DeckDisplays.quantize(100), DeckDisplays.quantize(101));
    }

    private static final class Recording extends CanvasContext {
        final List<String> triangles = new ArrayList<>();
        int sprites;

        Recording() {
            super(new CanvasMetrics(new Rect(0, 0, 1872, 372), 1872, 372, 1), new Rect(0, 0, 1872, 372), 1);
        }

        @Override protected void drawFillQuad(float x0, float y0, float x1, float y1,
                                              float x2, float y2, float x3, float y3, Color color) {
            triangles.add(Math.round(x0) + "," + Math.round(y0) + " " + Math.round(x1) + "," + Math.round(y1)
                    + " " + Math.round(x2) + "," + Math.round(y2) + " " + color.getRGB());
        }
        @Override protected void drawFillRect(float x, float y, float w, float h, Color color) {}
        @Override protected void drawLine(float x0, float y0, float x1, float y1, Color color, float width) {}
        @Override protected void drawText(BitmapFont font, String value, float x, float y, Color color) {}
        @Override protected void drawSprite(String path, SpriteAPI sprite, float x, float y,
                                            float w, float h, float angle, Color tint,
                                            CanvasSpriteRegion region, CanvasBlend blend) {
            sprites++;
        }
    }
}
