package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ShipCrewArtTest {
    @Test void layersMatchTheCompositorsAnatomicalSizesAndKeepTheNeckOpen() throws Exception {
        for (String uniform : new String[]{"command", "engineering", "medical", "service"}) {
            Path root = Path.of("mod/graphics/battle/ship-crew-modular-topdown", uniform);
            String[] names = {"body", "head", "foot"};
            int[][] sizes = {{150, 120}, {72, 88}, {38, 52}};
            for (int part = 0; part < names.length; part++) {
                var image = ImageIO.read(root.resolve(names[part] + ".png").toFile());
                assertEquals(sizes[part][0], image.getWidth(), uniform + "/" + names[part]);
                assertEquals(sizes[part][1], image.getHeight());
                assertTrue(image.getColorModel().hasAlpha());
                assertEquals(0, image.getRGB(0, 0) >>> 24);
                if (part == 0) {
                    for (int y = 10; y < 32; y++) for (int x = 65; x < 85; x++)
                        assertTrue((image.getRGB(x, y) >>> 24) < 10, uniform + " neck filled");
                    assertTrue((image.getRGB(75, 95) >>> 24) > 200, uniform + " body empty");
                }
            }
        }
        // Surface-map engineers still use their own appearance; ship clothing is a spawn override.
        assertEquals(LayeredArmorFamily.ENGINEER, LayeredArmorFamily.spawnDefault(UnitType.ENGINEER));
    }
}
