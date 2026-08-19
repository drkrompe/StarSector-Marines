package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredMechAssetTest {

    private static final Path ROOT = Path.of(
            "mod", "graphics", "battle", "mech-modular-topdown");

    @Test
    void familyChassisSpritesAreNormalizedTransparentAndDistinct() throws IOException {
        BufferedImage bulwark = load("chassis.png");
        BufferedImage hound = load("chassis-hound.png");
        BufferedImage sirocco = load("chassis-sirocco.png");

        assertNormalizedTransparent(bulwark);
        assertNormalizedTransparent(hound);
        assertNormalizedTransparent(sirocco);
        assertNotEquals(pixelHash(bulwark), pixelHash(hound));
        assertNotEquals(pixelHash(bulwark), pixelHash(sirocco));
        assertNotEquals(pixelHash(hound), pixelHash(sirocco));
    }

    @Test
    void heavyCannonIsAProductionReadyTransparentModule() throws IOException {
        BufferedImage cannon = load("heavy-cannon.png");
        assertTransparentSprite(cannon, 64, 128, 500);
    }

    @Test
    void thighBoneIsAProductionReadyTransparentModule() throws IOException {
        BufferedImage thighBone = load("thigh-bone.png");
        assertTransparentSprite(thighBone, 40, 112, 300);
    }

    private static BufferedImage load(String filename) throws IOException {
        BufferedImage image = ImageIO.read(ROOT.resolve(filename).toFile());
        assertNotNull(image, filename);
        return image;
    }

    private static void assertNormalizedTransparent(BufferedImage image) {
        assertTransparentSprite(image, 208, 208, 5_000);
    }

    private static void assertTransparentSprite(BufferedImage image, int width,
                                                int height, int minimumVisiblePixels) {
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());

        int transparent = 0;
        int visible = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha == 0) transparent++;
                if (alpha >= 24) visible++;
            }
        }
        assertTrue(transparent > 500, "sprite must retain transparent padding");
        assertTrue(visible > minimumVisiblePixels, "sprite must contain substantial visible art");
    }

    private static int pixelHash(BufferedImage image) {
        int hash = 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                hash = 31 * hash + image.getRGB(x, y);
            }
        }
        return hash;
    }
}
