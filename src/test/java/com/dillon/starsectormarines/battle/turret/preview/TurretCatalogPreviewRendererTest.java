package com.dillon.starsectormarines.battle.turret.preview;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression and dev-output coverage for the data-authored turret preview. */
class TurretCatalogPreviewRendererTest {

    @Test
    void catalogRendersStableSixStateStrips() throws Exception {
        String install = System.getProperty("starsectorDir");
        Assumptions.assumeTrue(install != null && !install.isBlank(),
                "starsectorDir is not configured");
        Path core = Path.of(install, "starsector-core");
        Assumptions.assumeTrue(Files.isRegularFile(
                        core.resolve("graphics/fx/particlealpha64linear.png")),
                "Starsector core preview assets are unavailable");

        TurretCatalogRegistry registry = loadRegistry();
        HeadlessTurretCatalogPreviewRenderer renderer =
                new HeadlessTurretCatalogPreviewRenderer(
                Path.of("mod"), core);

        for (TurretMountDef mount : registry.mounts()) {
            HeadlessTurretCatalogPreviewRenderer.RenderedPreview first =
                    renderer.render(mount);
            HeadlessTurretCatalogPreviewRenderer.RenderedPreview second =
                    renderer.render(mount);
            assertArrayEquals(png(first.image()), png(second.image()),
                    mount.id + " preview must be byte-stable");
            assertEquals(TurretCatalogPreviewDocument.STRIP_WIDTH, first.image().getWidth());
            assertEquals(TurretCatalogPreviewDocument.STRIP_HEIGHT, first.image().getHeight());
            assertTrue(distinctColorCount(first.image()) > 64,
                    mount.id + " preview should contain body and effect pixels");
            assertEveryAuthoredSlotContributed(mount, first.slotContributions());
        }

        assertTrue(registry.mountCount() > 0, "the catalog must not render an empty set");
    }

    private static void assertEveryAuthoredSlotContributed(
            TurretMountDef mount, Map<FxSlot, Integer> contributions) {
        assertEquals(mount.weapon.fx.slots().keySet(), contributions.keySet(),
                mount.id + " preview must consume every authored FX slot");
        for (FxSlot slot : mount.weapon.fx.slots().keySet()) {
            assertTrue(contributions.getOrDefault(slot, 0) > 0,
                    mount.id + " slot " + slot.key + " contributed no rendered command");
        }
    }

    private static TurretCatalogRegistry loadRegistry() throws Exception {
        WeaponRegistry weapons = new WeaponRegistry();
        for (String path : WeaponRegistry.BUILTIN_CATALOGS) {
            weapons.ingest(new JSONObject(Files.readString(Path.of("mod", path))));
        }
        TurretCatalogRegistry registry = new TurretCatalogRegistry();
        for (String path : TurretCatalogRegistry.BUILTIN_CATALOGS) {
            registry.ingest(new JSONObject(Files.readString(Path.of("mod", path))), weapons);
        }
        return registry;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", bytes));
        return bytes.toByteArray();
    }

    private static long distinctColorCount(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
                null, 0, image.getWidth());
        return Arrays.stream(pixels).distinct().count();
    }
}
