package com.dillon.starsectormarines.battle.air.engine;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EngineFxTextureTest {
    @Test void additiveTexturesHaveTransparentFrameEdgesAndATaperedFlame() throws Exception {
        long totalBytes = 0;
        BufferedImage flame = null;
        for (String kind : new String[]{"glow", "flame"}) {
            Path path = Path.of("mod/graphics/battle/fx/aircraft-engine-" + kind + ".png");
            totalBytes += Files.size(path);
            BufferedImage image = ImageIO.read(path.toFile());
            assertNotNull(image, path.toString());
            assertTrue(image.getColorModel().hasAlpha());
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(0, alpha(image, x, 0), "top frame edge");
                assertEquals(0, alpha(image, x, image.getHeight() - 1), "bottom frame edge");
            }
            for (int y = 0; y < image.getHeight(); y++) {
                assertEquals(0, alpha(image, 0, y), "left frame edge");
                assertEquals(0, alpha(image, image.getWidth() - 1, y), "right frame edge");
            }
            if (kind.equals("flame")) flame = image;
            else assertTrue(alpha(image, image.getWidth() / 2, image.getHeight() / 2) > 128);
        }
        assertTrue(totalBytes <= 32 * 1024, "procedural engine textures remain small");
        assertNotNull(flame);
        int tail = flame.getHeight() / 5, middle = flame.getHeight() / 2;
        int base = Math.round((flame.getHeight() - 1) * .97f);
        assertTrue(rowWidth(flame, tail) < rowWidth(flame, middle));
        assertTrue(rowWidth(flame, middle) < rowWidth(flame, base));
        int center = flame.getWidth() / 2;
        assertTrue(alpha(flame, center, base) > alpha(flame, center, middle));
        assertTrue(alpha(flame, center, middle) > alpha(flame, center, tail));
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static int rowWidth(BufferedImage image, int y) {
        int width = 0;
        for (int x = 0; x < image.getWidth(); x++) if (alpha(image, x, y) >= 16) width++;
        return width;
    }
}
