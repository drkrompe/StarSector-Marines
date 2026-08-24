package com.dillon.starsectormarines.ui.retained.headless;

import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessUiRendererTest {

    @Test
    void rendersExactRetainedBoxesAndBitmapTextWithoutTheEngine() {
        UiElement button = new UiElement("action").tag(UiTag.BUTTON).text("OPEN ARMORY");
        UiElement root = new UiElement("root").layout(UiLayout.COLUMN).child(button);
        UiDocument document = new UiDocument(root).theme(new UiTheme(
                StyleSheet.parse("headless-test", """
                        :root { background-color: #080d15; font-family: body; padding: 12px; }
                        button {
                            width: 220px; height: 52px; padding: 8px 12px;
                            color: #e4eefa; background-color: #1e3045;
                            border-color: #6ed7ff; border-width: 2px; text-align: center;
                        }
                        """), Map.of("body", Fonts.INSIGNIA_LARGE)));
        HeadlessUiRenderer renderer = renderer();

        BufferedImage first = renderer.render(document, 320, 120);
        BufferedImage second = renderer.render(document, 320, 120);
        int[] firstPixels = ((DataBufferInt) first.getRaster().getDataBuffer()).getData();
        int[] secondPixels = ((DataBufferInt) second.getRaster().getDataBuffer()).getData();

        assertEquals(320, first.getWidth());
        assertEquals(120, first.getHeight());
        assertTrue(Arrays.equals(firstPixels, secondPixels));
        assertTrue(Arrays.stream(firstPixels).distinct().count() > 80,
                "boxes, borders, and the vanilla bitmap font should all be present");
    }

    private static HeadlessUiRenderer renderer() {
        Path starsectorCore = Path.of(System.getProperty("starsectorDir"))
                .resolve("starsector-core");
        return new HeadlessUiRenderer(Path.of("mod"), starsectorCore);
    }
}
