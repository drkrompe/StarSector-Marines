package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredMechAssetTest {

    private static final Path ROOT = Path.of(
            "mod", "graphics", "battle", "mech-modular-topdown");
    private static final String[] FACTION_SKINS = {
            "hegemony", "tri-tachyon", "persean-league", "luddic-church",
            "knights-of-ludd", "luddic-path", "sindrian-diktat", "lions-guard",
            "pirates", "independent"
    };
    private static final SpriteSpec[] WEAPONS = {
            new SpriteSpec("chaingun-arm.png", 62, 112, 2_500),
            new SpriteSpec("linear-cannon-variant.png", 58, 138, 2_500),
            new SpriteSpec("heavy-cannon.png", 64, 128, 2_500),
            new SpriteSpec("srm-pod.png", 62, 88, 2_500),
            new SpriteSpec("lrm-pod.png", 76, 96, 2_500),
            new SpriteSpec("shoulder-laser-cannon.png", 76, 128, 2_500),
            new SpriteSpec("pulse-laser-arm.png", 62, 112, 2_500),
            new SpriteSpec("hegemony-bastion-autocannon.png", 64, 132, 1_600),
            new SpriteSpec("pather-demolition-cannon.png", 72, 124, 1_800),
            new SpriteSpec("lions-guard-thermal-lance.png", 76, 132, 1_800)
    };

    private record SpriteSpec(String filename, int width, int height, int minimumVisiblePixels) {
    }

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
    void factionSkinsRetainExactChassisMasksAndDistinctPaint() throws IOException {
        String[] chassis = {"chassis.png", "chassis-hound.png", "chassis-sirocco.png"};
        for (String filename : chassis) {
            BufferedImage base = load(filename);
            Set<Integer> paintHashes = new HashSet<>();
            for (String faction : FACTION_SKINS) {
                BufferedImage skin = load("factions/" + faction + "/" + filename);
                assertNormalizedTransparent(skin);
                assertAlphaMaskEquals(base, skin);
                assertNotEquals(pixelHash(base), pixelHash(skin), faction + "/" + filename);
                assertTrue(paintHashes.add(pixelHash(skin)),
                        "duplicate faction paint for " + filename + ": " + faction);
            }
        }
    }

    @Test
    void factionWeaponSkinsRetainExactMasksSharedHardwareAndDistinctPaint() throws IOException {
        for (SpriteSpec weapon : WEAPONS) {
            BufferedImage base = load(weapon.filename());
            Set<Integer> paintHashes = new HashSet<>();
            for (String faction : FACTION_SKINS) {
                BufferedImage skin = load("factions/" + faction + "/" + weapon.filename());
                assertTransparentSprite(skin, weapon.width(), weapon.height(),
                        weapon.minimumVisiblePixels());
                assertAlphaMaskEquals(base, skin);
                assertNotEquals(pixelHash(base), pixelHash(skin),
                        faction + "/" + weapon.filename());
                assertTrue(paintHashes.add(pixelHash(skin)),
                        "duplicate faction paint for " + weapon.filename() + ": " + faction);
                assertSharedHardwareRemains(base, skin);
            }
        }
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

    private static void assertAlphaMaskEquals(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y) >>> 24, actual.getRGB(x, y) >>> 24,
                        "alpha mismatch at " + x + "," + y);
            }
        }
    }

    private static void assertSharedHardwareRemains(BufferedImage base, BufferedImage skin) {
        int visible = 0;
        int unchanged = 0;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                if ((base.getRGB(x, y) >>> 24) < 24) continue;
                visible++;
                if (base.getRGB(x, y) == skin.getRGB(x, y)) unchanged++;
            }
        }
        assertTrue(unchanged > visible / 6,
                "at least one sixth of visible weapon hardware must remain byte-identical");
    }
}
