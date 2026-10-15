package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ComposableDamageArtTest {
    @Test void everyRetainedPropHasMatchingPhysicalStatesAndSparseWreckArt() throws Exception {
        var registry=TileRegistry.installed();var sheets=new HashMap<String,BufferedImage>();
        for(String family:new String[]{"console-bank","main-stores","sickbay-clinic"}) {
            var entries=new JSONObject(Files.readString(Path.of("art-source/tilesets/"+family+"-damage.tileset-authoring.json"))).getJSONArray("entries");
            for(int i=0;i<entries.length();i+=2) {
                String damagedId=entries.getJSONObject(i).getString("id");
                String id=damagedId.substring(0,damagedId.length()-"-damaged".length());var intact=registry.doodad(id);
                for(String state:new String[]{"damaged","destroyed"}) {
                    var d=registry.doodad(id+"-"+state);assertNotNull(d,id);
                    assertEquals(intact.footprintCellsX,d.footprintCellsX);assertEquals(intact.footprintCellsY,d.footprintCellsY);
                    boolean broken=state.equals("destroyed");assertEquals(broken?0:intact.cover.level(),d.cover.level());
                    assertEquals(broken?0:intact.ballisticHalfHeight,d.ballisticHalfHeight);
                    BufferedImage sheet=sheets.get(d.sheetPath);
                    if(sheet==null){sheet=ImageIO.read(Path.of("mod").resolve(d.sheetPath).toFile());sheets.put(d.sheetPath,sheet);}
                    int w=d.footprintCellsX*d.sourceCellPx,h=d.footprintCellsY*d.sourceCellPx,opaque=0;
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((sheet.getRGB(d.col*d.sourceCellPx+x,d.row*d.sourceCellPx+y)>>>24)>40)opaque++;
                    assertTrue(opaque>w*h*.015,id+" has no readable art");
                    if(broken)assertTrue(opaque<w*h*.72,id+" wreck closes the ground gaps");
                }
            }
        }
        var frame=registry.doodad("main-stores.frame-damaged");
        BufferedImage sheet=sheets.get(frame.sheetPath);
        for(int slot=0;slot<3;slot++) {
            int x=frame.col*frame.sourceCellPx+frame.sourceCellPx;
            int y=frame.row*frame.sourceCellPx+(slot*2+1)*frame.sourceCellPx;
            assertTrue((sheet.getRGB(x,y)>>>24)<=4,"retaining frame window "+slot+" must remain transparent");
        }
    }
}
