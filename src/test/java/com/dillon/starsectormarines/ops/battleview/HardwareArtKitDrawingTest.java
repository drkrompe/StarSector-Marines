package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link HardwareArt#preload} loads a drawing for every kit, so a kit without one crashes the Mech Lab. */
class HardwareArtKitDrawingTest {
    @Test
    void everyKitHasADrawingShippedWithTheMod() {
        for (VehicleKit kit : VehicleKit.values()) {
            Path drawing = Path.of("mod/graphics/ui/mech-hardware", HardwareArt.stem(kit) + ".svg");
            assertTrue(Files.isRegularFile(drawing), kit + " has no drawing at " + drawing);
        }
    }
}
