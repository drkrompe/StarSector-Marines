package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ArmoryWorkshopArtTest {
    @Test void retainingFrameAndLooseLayersKeepTheirTransparentVoids() throws Exception {
        var image = ImageIO.read(Path.of("mod/graphics/tilesets/armory-workshop.png").toFile());
        var frame = TileRegistry.installed().doodad("armory-workshop.rack-frame");
        for (int bay = 0; bay < 3; bay++) {
            int cx = frame.col * 64 + bay * 64 + 32;
            int cy = frame.row * 64 + 32;
            for (int y = cy - 10; y <= cy + 10; y++) for (int x = cx - 12; x <= cx + 12; x++)
                assertTrue((image.getRGB(x, y) >>> 24) <= 2, "rack opening must remain transparent apart from quantization residue");
        }
        for (String id : new String[]{"rifles", "bench-tools", "rack-frame"}) {
            var def = TileRegistry.installed().doodad("armory-workshop." + id);
            assertEquals(0, def.cover.level());
            assertEquals(0f, def.ballisticHalfHeight);
        }
    }
}
