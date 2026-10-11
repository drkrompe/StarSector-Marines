package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONObject;

/** Four columns of formations; rows are intact, fractured, then low scattered debris. */
public final class PlanetFormationTemplate {
    public static final int PITCH=384, FRAME=352;
    public static BufferedImage render() {
        var image=new BufferedImage(PITCH*4,PITCH*3,BufferedImage.TYPE_INT_ARGB);
        for(var shape:MineralFormation.values()) for(int state=0;state<3;state++) {
            double scale=FRAME/(double)Math.max(shape.width(),shape.height());
            var g=image.createGraphics();
            g.translate(shape.ordinal()*PITCH+(PITCH-shape.width()*scale)/2,
                    state*PITCH+(PITCH-shape.height()*scale)/2);
            g.scale(scale,scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int n=0;
            for(var facet:shape.facets()) {
                double[] p=facet.points(); double cx=0,cy=0;
                for(int i=0;i<p.length;i+=2) {cx+=p[i];cy+=p[i+1];}
                cx/=p.length/2;cy/=p.length/2;
                Color base=shape.ice()?new Color(155,189,202):new Color(112,108,99);
                if(state<2) {
                    var body=new Path2D.Double();body.moveTo(p[0],shape.height()-p[1]);
                    for(int i=2;i<p.length;i+=2)body.lineTo(p[i],shape.height()-p[i+1]);
                    body.closePath();g.setColor(n++%2==0?base:base.darker());g.fill(body);
                }
                for(int i=0;i<p.length;i+=2) {
                    int j=(i+2)%p.length;
                    double shrink=state==2?.65:1;
                    double tx=(cx+p[i]+p[j])/3,ty=(cy+p[i+1]+p[j+1])/3;
                    var tri=new Path2D.Double();
                    tri.moveTo(tx+(cx-tx)*shrink,shape.height()-(ty+(cy-ty)*shrink));
                    tri.lineTo(tx+(p[i]-tx)*shrink,shape.height()-(ty+(p[i+1]-ty)*shrink));
                    tri.lineTo(tx+(p[j]-tx)*shrink,shape.height()-(ty+(p[j+1]-ty)*shrink));tri.closePath();
                    g.setColor(i%4==0?base.brighter():base);g.fill(tri);
                    if(state==1) {g.setColor(base.darker().darker());g.setStroke(new BasicStroke(.035f));g.draw(tri);}
                }
            }
            g.dispose();
        }
        return image;
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        ImageIO.write(render(),"png",root.resolve("art-source/templates/planet-formations.png").toFile());
        // Accepted source cuts include measured ImageGen registration. Never overwrite them.
        if (Files.exists(root.resolve("art-source/tilesets/planet-formations.tileset-authoring.json"))) return;
        var entries=new JSONArray();
        for(var shape:MineralFormation.values()) for(int state=0;state<3;state++) {
            double scale=FRAME/(double)Math.max(shape.width(),shape.height());
            int w=(int)Math.round(shape.width()*scale),h=(int)Math.round(shape.height()*scale);
            var mask=new JSONArray();
            if(state<2)for(int y=0;y<shape.height();y++) {
                StringBuilder row=new StringBuilder();
                for(int x=0;x<shape.width();x++)row.append(shape.blocks(x,y,0)?'#':'.');mask.put(row.toString());
            }
            entries.put(new JSONObject().put("id",shape.id()+(state==0?"":state==1?"-damaged":"-destroyed"))
                    .put("included",true).put("cover","none").put("ballisticHalfHeight",0)
                    .put("footprintCells",new JSONArray().put(shape.width()).put(shape.height()))
                    .put("fogSurface",mask).put("rect",new JSONArray().put(shape.ordinal()*PITCH+(PITCH-w)/2)
                            .put(state*PITCH+(PITCH-h)/2).put(w).put(h)));
        }
        var doc=new JSONObject().put("sheetName","planet-formations").put("sheet","art-source/tilesets/planet-formations.raw.png")
                .put("cellPx",64).put("gridCols",4).put("gridRows",3).put("idPrefix","formation").put("alphaMin",40)
                .put("note","PlanetFormationTemplate: full footprint cuts, four columns, intact/damaged/destroyed rows. MineralFormation polygons own collision and fog masks. No ground backing.")
                .put("entries",entries);
        Files.writeString(root.resolve("art-source/tilesets/planet-formations.tileset-authoring.json"),doc.toString(2)+"\n");
    }
}
