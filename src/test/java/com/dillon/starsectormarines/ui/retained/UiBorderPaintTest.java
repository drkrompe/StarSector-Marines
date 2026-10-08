package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.style.UiBorderStyle;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiBorderPaintTest {
    @Test
    void pressedBevelReversesLightingAndPreservesOpacity() {
        RecordingCanvas raised = new RecordingCanvas();
        RecordingCanvas pressed = new RecordingCanvas();
        Color steel = new Color(90, 110, 100, 130);
        UiBorderPaint.bevel(raised, 80, 30, 2, 2, steel, UiBorderStyle.OUTSET);
        UiBorderPaint.bevel(pressed, 80, 30, 2, 2, steel, UiBorderStyle.INSET);
        assertEquals(4, raised.colors.size());
        assertEquals(raised.colors.get(0), pressed.colors.get(2));
        assertEquals(raised.colors.get(2), pressed.colors.get(0));
        assertTrue(raised.colors.get(0).getRed() > steel.getRed());
        assertTrue(raised.colors.get(2).getRed() < steel.getRed());
        assertEquals(130, raised.colors.get(0).getAlpha());
        assertArrayEquals(new float[]{0, 0, 80, 0, 78, 2, 2, 2}, raised.quads.get(0));
    }

    @Test
    void narrowBoxesClampMitresWithoutCrossedOrOutsideVertices() {
        RecordingCanvas canvas = new RecordingCanvas();
        UiBorderPaint.bevel(canvas, 3, 2, 10, 20, Color.GRAY, UiBorderStyle.OUTSET);
        for (float[] quad : canvas.quads) {
            for (int i = 0; i < quad.length; i += 2) {
                assertTrue(quad[i] >= 0 && quad[i] <= 3);
                assertTrue(quad[i + 1] >= 0 && quad[i + 1] <= 2);
            }
        }
        assertArrayEquals(new float[]{0, 0, 3, 0, 1.5f, 1, 1.5f, 1}, canvas.quads.get(0));
    }

    private static final class RecordingCanvas extends CanvasContext {
        final List<float[]> quads = new ArrayList<>();
        final List<Color> colors = new ArrayList<>();
        RecordingCanvas() {
            super(new CanvasMetrics(new Rect(0, 0, 80, 30), 80, 30, 1),
                    new Rect(0, 0, 80, 30), 1);
        }
        @Override protected void drawFillQuad(float x0, float y0, float x1, float y1,
                                              float x2, float y2, float x3, float y3, Color color) {
            quads.add(new float[]{x0, y0, x1, y1, x2, y2, x3, y3});
            colors.add(color);
        }
        @Override protected void drawFillRect(float x, float y, float w, float h, Color color) {}
        @Override protected void drawLine(float x0, float y0, float x1, float y1, Color color, float width) {}
        @Override protected void drawText(BitmapFont font, String value, float x, float y, Color color) {}
        @Override protected void drawSprite(String path, SpriteAPI sprite, float x, float y,
                                            float w, float h, float angle, Color tint,
                                            CanvasSpriteRegion region, CanvasBlend blend) {}
    }
}
