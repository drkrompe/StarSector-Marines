package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every family that declares lying-body art ships all of it, on the shared pose frame. */
class DownedArtTest {
    private static final String ARMOR = "mod/graphics/battle/marine-modular-topdown/variants/armor/";
    private static final String CREW = "mod/graphics/battle/ship-crew-modular-topdown/";
    private static final Map<LayeredArmorFamily, String> ROOTS = Map.of(
            LayeredArmorFamily.ARMORLESS, ARMOR + "armorless",
            LayeredArmorFamily.CHARCOAL, ARMOR + "charcoal",
            LayeredArmorFamily.BLUE_SCOUT, ARMOR + "blue-scout",
            LayeredArmorFamily.OUTLAW, ARMOR + "outlaw",
            LayeredArmorFamily.MILITIA, ARMOR + "militia",
            LayeredArmorFamily.SHIP_COMMAND, CREW + "command",
            LayeredArmorFamily.SHIP_ENGINEERING, CREW + "engineering",
            LayeredArmorFamily.SHIP_MEDICAL, CREW + "medical",
            LayeredArmorFamily.SHIP_SERVICE, CREW + "service");

    @Test void bloodPoolsShipOnTheirFrame() throws Exception {
        for (int pool = 0; pool < BattleSprites.BLOOD_POOLS; pool++) {
            Path file = Path.of("mod", BattleSprites.BLOOD_POOL_ROOT + pool + ".png");
            BufferedImage image = ImageIO.read(file.toFile());
            assertEquals(240, image.getWidth(), file.toString());
            assertEquals(240, image.getHeight(), file.toString());
            assertEquals(0, image.getRGB(0, 0) >>> 24, file + " corner must be transparent");
            assertTrue((image.getRGB(120, 120) >>> 24) > 200, file + " pool centre must be solid");
        }
    }

    @Test void aPoolSpreadsFromAThirdToFullAndThenStays() {
        assertEquals(UnitRenderService.BLOOD_START_FRACTION, UnitRenderService.bloodSpread(0f), 1e-6f);
        assertTrue(UnitRenderService.bloodSpread(1f) < UnitRenderService.bloodSpread(2f));
        assertEquals(1f, UnitRenderService.bloodSpread(UnitRenderService.BLOOD_SPREAD_SECONDS), 1e-6f);
        assertEquals(1f, UnitRenderService.bloodSpread(600f), 1e-6f);
    }

    @Test void declaredFamiliesShipFourTransparentPosesOnTheSharedFrame() throws Exception {
        for (LayeredArmorFamily family : LayeredArmorFamily.values()) {
            if (!family.hasDownedArt()) continue;
            String root = ROOTS.get(family);
            assertTrue(root != null, family + " declares downed art but this test has no folder for it");
            for (int pose = 0; pose < LayeredUnitAssets.DOWNED_POSES; pose++) {
                Path file = Path.of(root, "downed-" + pose + ".png");
                assertTrue(Files.exists(file), file.toString());
                BufferedImage image = ImageIO.read(file.toFile());
                assertEquals(300, image.getWidth(), file.toString());
                assertEquals(460, image.getHeight(), file.toString());
                assertEquals(0, image.getRGB(0, 0) >>> 24, file + " corner must be transparent");
                assertTrue((image.getRGB(150, 230) >>> 24) > 200, file + " body centre must be solid");
            }
        }
    }
}
