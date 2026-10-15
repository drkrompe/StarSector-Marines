package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.model.PropDamage;
import com.dillon.starsectormarines.battle.combat.EnvironmentMaterial;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FactoryMachineArtTest {
    @Test void conveyorStatesJoinAtTheirRailsAndReleaseCoverWhenWrecked() throws Exception {
        var registry=TileRegistry.installed();
        for(String suffix:new String[]{"","-damaged","-destroyed"}) {
            var d=registry.doodad("factory-machines.conveyor"+suffix);
            assertNotNull(d); assertEquals(1,d.footprintCellsX); assertEquals(1,d.footprintCellsY);
            if(suffix.equals("-destroyed")) { assertEquals(0,d.cover.level()); assertEquals(0f,d.ballisticHalfHeight); continue; }
            var image=ImageIO.read(Path.of("mod").resolve(d.sheetPath).toFile());
            int joined=0;
            for(int y=0;y<d.sourceCellPx;y++) {
                int left=image.getRGB(d.col*d.sourceCellPx,d.row*d.sourceCellPx+y)>>>24;
                int right=image.getRGB((d.col+1)*d.sourceCellPx-1,d.row*d.sourceCellPx+y)>>>24;
                if(left>40 && right>40) joined++;
            }
            assertTrue(joined>d.sourceCellPx*.35,"feed cannot acquire transparent end gaps");
        }
    }
    @Test void substantialMachineArtHasMatchingDamageFramesAndOpenWreckage() throws Exception {
        var registry = TileRegistry.installed();
        for (String name : new String[]{"fabricator", "assembly", "coolant"}) {
            String id = "factory-machines." + name;
            var intact = registry.doodad(id);
            assertNotNull(intact);
            assertTrue(intact.footprintCellsX * intact.footprintCellsY >= 4);
            assertTrue(PropDamage.EQUIPMENT.containsKey(id));
            assertEquals(EnvironmentMaterial.MACHINERY, EnvironmentMaterial.forProp(id));
            for (String suffix : new String[]{"", "-damaged", "-destroyed"}) {
                var def = registry.doodad(id + suffix);
                assertNotNull(def);
                assertEquals(intact.footprintCellsX, def.footprintCellsX);
                assertEquals(intact.footprintCellsY, def.footprintCellsY);
                boolean wreck = suffix.equals("-destroyed");
                assertEquals(wreck ? 0 : intact.cover.level(), def.cover.level());
                assertEquals(wreck ? 0f : intact.ballisticHalfHeight, def.ballisticHalfHeight);
                var atlas = ImageIO.read(Path.of("mod").resolve(def.sheetPath).toFile());
                int width = def.footprintCellsX * def.sourceCellPx;
                int height = def.footprintCellsY * def.sourceCellPx;
                int opaque = 0;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                    if ((atlas.getRGB(def.col * def.sourceCellPx + x, def.row * def.sourceCellPx + y) >>> 24) > 40) opaque++;
                double coverage = (double) opaque / (width * height);
                assertTrue(wreck ? coverage > .1 && coverage < .60 : coverage > .65,
                        id + suffix + " must read at its authored size or expose floor when wrecked: " + coverage);
            }
        }
    }
}
