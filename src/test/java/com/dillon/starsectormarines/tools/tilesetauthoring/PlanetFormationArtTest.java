package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PlanetFormationArtTest {
    @Test void exportedMaterialStaysInsideItsCollisionCellsAndDebrisLeavesGaps() throws Exception {
        var atlas=ImageIO.read(Path.of("mod/graphics/doodads/planet-formations.png").toFile());
        for(var shape:MineralFormation.values())for(String state:new String[]{"","-damaged","-destroyed"}) {
            var def=TileRegistry.installed().doodad(shape.id()+state);
            int visible=0,w=shape.width()*64,h=shape.height()*64;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int alpha=atlas.getRGB(def.col*64+x,def.row*64+y)>>>24;
                if(alpha>32) {
                    visible++;
                    assertTrue(shape.blocks(x/64,shape.height()-1-y/64,0),shape.id()+state+" overhangs at "+x+","+y);
                }
            }
            double fraction=visible/(double)(w*h);
            assertTrue(fraction>.08,"Art disappeared");
            if(state.equals("-destroyed"))assertTrue(fraction<.45,"Debris must leave exposed ground");
        }
    }
}
