package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every tank hull has its own registered sheet: four full square slots, none of them empty. */
class TankVariantSheetsTest {

    @Test
    void eachTankVariantDrawsFromItsOwnFourSlotSheet() throws Exception {
        for (VehicleType type : VehicleType.values()) {
            if (!type.isTank()) continue;
            if (type != VehicleType.BREAKTHROUGH_TANK) {
                assertNotEquals(VehicleType.BREAKTHROUGH_TANK.spritePath, type.spritePath, type.name());
            }
            Path path = Path.of("mod", type.spritePath);
            assertTrue(Files.isRegularFile(path), path.toString());
            BufferedImage sheet = ImageIO.read(path.toFile());
            assertEquals(4, type.frameCount, type.name());
            assertEquals(sheet.getHeight() * type.frameCount, sheet.getWidth(), type.name() + " slots are square");
            int slot = sheet.getHeight();
            for (int frame : new int[]{type.spriteFrame, type.turretFrame, type.wreckHullFrame(), type.wreckTurretFrame()}) {
                assertTrue(opaquePixels(sheet, frame * slot, slot) > slot * slot / 200,
                        type.name() + " frame " + frame + " is drawn");
            }
        }
    }

    private static int opaquePixels(BufferedImage sheet, int x0, int size) {
        int count = 0;
        for (int y = 0; y < size; y += 2) {
            for (int x = x0; x < x0 + size; x += 2) {
                if ((sheet.getRGB(x, y) >>> 24) > 128) count += 4;
            }
        }
        return count;
    }
}
