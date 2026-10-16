package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Packed replacement art must fit the native canvas that owns physical size and hardpoint registration. */
class FighterBattleArtContractTest {
    @Test void shippedFighterArtPreservesNativeAspectTransparencyAndBoundedPayload() throws Exception {
        String install = System.getProperty("starsectorDir");
        assertNotNull(install, "the configured game install supplies authoritative hull canvases");
        Path hulls = Path.of(install, "starsector-core", "data", "hulls");
        long total = 0;
        for (FighterProfile fighter : FighterProfile.values()) {
            Path packed = Path.of("mod").resolve(fighter.spritePath());
            assertTrue(Files.isRegularFile(packed), fighter + " shipped battle texture is missing");
            var image = ImageIO.read(packed.toFile());
            assertNotNull(image, fighter + " texture must decode");
            var hull = new JSONObject(Files.readString(hulls.resolve(fighter.renderHullId() + ".ship")));
            int nativeWidth = hull.getInt("width"), nativeHeight = hull.getInt("height");
            assertEquals((long) nativeWidth * image.getHeight(), (long) nativeHeight * image.getWidth(),
                    fighter + " packed canvas must match the authoritative hull aspect");
            assertTrue(image.getColorModel().hasAlpha(), fighter + " texture must retain alpha");
            boolean transparent = false, visible = false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                transparent |= alpha == 0;
                visible |= alpha > 0;
            }
            assertTrue(transparent && visible, fighter + " must contain aircraft pixels and clear canvas");
            total += Files.size(packed);
        }
        assertTrue(total <= 1024 * 1024, "all fighter battle textures must fit within 1 MiB; actual bytes=" + total);
    }
}
