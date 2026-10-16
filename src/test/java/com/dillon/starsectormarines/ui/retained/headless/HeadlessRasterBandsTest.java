package com.dillon.starsectormarines.ui.retained.headless;

import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The banded drain must be the serial drain split by rows, and the packed additive blend its general one. */
class HeadlessRasterBandsTest {

    @Test
    void unpremultiplyIsTheColourModelsOwn() {
        ColorModel premultiplied = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE).getColorModel();
        for (int alpha = 0; alpha < 256; alpha++) {
            for (int channel = 0; channel <= alpha; channel++) {
                int pixel = alpha << 24 | channel << 16 | channel << 8 | channel;
                assertEquals(premultiplied.getRed(pixel), HeadlessUiRenderer.unpremultiply(channel, alpha),
                        "channel " + channel + " at alpha " + alpha);
            }
        }
    }

    @Test
    void bandsDrawTheSamePixelsAsTheWholeCanvas(@TempDir Path root) throws Exception {
        BufferedImage noise = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 40; x++) noise.setRGB(x, y, random.nextInt());
        ImageIO.write(noise, "png", root.resolve("noise.png").toFile());

        Consumer<CanvasContext> scene = context -> {
            Random draws = new Random(11);
            for (int i = 0; i < 24; i++) {
                float x = draws.nextFloat() * 97f, y = draws.nextFloat() * 83f;
                float width = 5f + draws.nextFloat() * 60f, height = 5f + draws.nextFloat() * 50f;
                float angle = i % 3 == 0 ? 0f : draws.nextFloat() * 360f;
                Color tint = new Color(draws.nextInt(256), draws.nextInt(256), draws.nextInt(256), 64 + draws.nextInt(192));
                CanvasSpriteRegion region = i % 2 == 0 ? CanvasSpriteRegion.FULL
                        : new CanvasSpriteRegion(0.25f, 0.2f, 0.5f, 0.6f);
                context.sprite("noise.png", null, x, y, width, height, angle, tint, region,
                        i % 4 == 1 ? CanvasBlend.ADDITIVE : CanvasBlend.NORMAL);
            }
            context.fillQuad(3.3f, 4.1f, 80.7f, 20.2f, 60.5f, 77.9f, 10.1f, 50.6f, new Color(30, 200, 90, 120));
            context.line(2.5f, 80.2f, 95.1f, 1.7f, new Color(250, 250, 20, 200), 2.3f);
            context.fillRect(10.4f, 30.6f, 50.2f, 9.9f, new Color(10, 20, 250, 90));
        };

        int[] whole = render(root, context -> scene.accept(context));
        Set<Integer> tops = ConcurrentHashMap.newKeySet();
        int[] banded = render(root, context -> assertTrue(HeadlessUiRenderer.drawInBands(context, 12,
                (band, top, bottom) -> {
                    tops.add(top);
                    assertTrue(bottom - top <= 12, "a band is at most twelve rows");
                    scene.accept(band);
                })));
        assertEquals(7, tops.size(), "seven bands drew (83 rows in twelves)");
        // Identical, except that a band's first row may sample a scaled or turned sprite a level or two differently.
        for (int i = 0; i < whole.length; i++) {
            if (whole[i] == banded[i]) continue;
            int row = i / 97;
            assertEquals(0, row % 12, "pixel " + i % 97 + "," + row + " differs off a band's first row");
            for (int shift = 0; shift <= 24; shift += 8) {
                assertTrue(Math.abs((whole[i] >>> shift & 255) - (banded[i] >>> shift & 255)) <= 2,
                        "pixel " + i % 97 + "," + row + " differs by more than two levels");
            }
        }
    }

    private static int[] render(Path root, Consumer<CanvasContext> draw) {
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(List.of(root), (pass, context, viewport, alpha) -> {
            draw.accept(context);
            return true;
        });
        BufferedImage image = renderer.renderHostPass(new Pass(), 97, 83);
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private record Pass() implements CanvasHostPass {
        @Override public void draw(CanvasHostViewport viewport, float alphaMult) { }
    }
}
