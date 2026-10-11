package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONObject;

/** Material samples clipped by the terrain contour; no sprite footprint or elevated tile border. */
public final class IceTerrainTemplate {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        var image=new BufferedImage(1536,1536,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(new Color(30,33,36));g.fillRect(0,0,1536,1536);
        var blocks=new JSONArray();var entries=new JSONArray();
        for(int i=0;i<9;i++) {
            int x=i%3*512+16,y=i/3*512+16;
            int light=i<2?175:82+(i-2)*17;
            g.setColor(new Color(light,Math.min(255,light+20),Math.min(255,light+30)));g.fillRect(x,y,480,480);
            g.setColor(new Color(Math.max(0,light-20),light,Math.min(255,light+14)));
            for(int n=0;n<8;n++)g.drawPolyline(new int[]{x,x+120,x+235,x+370,x+480},
                    new int[]{y+25+n*58,y+12+n*58,y+30+n*58,y+22+n*58,y+25+n*58},5);
            String id="terrain.ice."+(i==0?"rock":i==1?"talus":"face."+(i-2));
            blocks.put(new JSONObject().put("id",id).put("layout","variants"));
            entries.put(new JSONObject().put("id",id+".sample").put("block",id).put("slot","v1")
                    .put("included",true).put("cover","none").put("footprintCells",new JSONArray().put(1).put(1))
                    .put("rect",new JSONArray().put(x+8).put(y+8).put(464).put(464)));
        }
        g.dispose();ImageIO.write(image,"png",root.resolve("art-source/templates/ice-terrain.png").toFile());
        // Keep the inspected cuts for the accepted raw sheet when regenerating only the guide.
        if (Files.exists(root.resolve("art-source/tilesets/ice-terrain.tileset-authoring.json"))) return;
        var doc=new JSONObject().put("sheetName","ice-terrain").put("sheet","art-source/tilesets/ice-terrain.raw.png")
                .put("cellPx",64).put("gridCols",3).put("gridRows",3).put("idPrefix","terrain.ice").put("alphaMin",40)
                .put("note","IceTerrainTemplate: summit, crushed-ice talus, then seven compressed ice face values dark to light. Inset material cuts; runtime contour owns all geometry.")
                .put("blocks",blocks).put("entries",entries);
        Files.writeString(root.resolve("art-source/tilesets/ice-terrain.tileset-authoring.json"),doc.toString(2)+"\n");
    }
}
