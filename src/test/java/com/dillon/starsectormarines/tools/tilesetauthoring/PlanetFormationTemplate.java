package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Columns of formations; rows are intact, fractured, then low scattered debris. The planet sheet draws the rock and
 * ice formations in four columns; {@link HuskFormationTemplate} draws the husks on their own sheet.
 */
public final class PlanetFormationTemplate {
    public static final int PITCH=384, FRAME=352;

    /** The formations one sheet draws, one column each, in enum order. */
    public static List<MineralFormation> sheet(MineralFormation.Material... materials) {
        var wanted=List.of(materials);
        return Arrays.stream(MineralFormation.values()).filter(f->wanted.contains(f.material())).toList();
    }
    /** The planet sheet's four columns; the basalt formations are its first two regraded (volcanic-formations). */
    public static List<MineralFormation> planet() {
        return List.of(MineralFormation.BOULDERS,MineralFormation.SPIRES,MineralFormation.ICE_BLOCKS,MineralFormation.ICE_PINNACLES);
    }

    public static BufferedImage render() { return render(planet()); }
    public static BufferedImage render(List<MineralFormation> shapes) { return render(shapes,false); }
    /**
     * With {@code flat}, standing bodies are one tone each, with no facet triangles: the facet fan otherwise comes
     * back from ImageGen as a literal star-shaped crystal, which suits rock and not a hollow husk.
     */
    public static BufferedImage render(List<MineralFormation> shapes,boolean flat) {
        var image=new BufferedImage(PITCH*shapes.size(),PITCH*3,BufferedImage.TYPE_INT_ARGB);
        for(int col=0;col<shapes.size();col++) for(int state=0;state<3;state++) {
            var shape=shapes.get(col);
            double scale=FRAME/(double)Math.max(shape.width(),shape.height());
            var g=image.createGraphics();
            g.translate(col*PITCH+(PITCH-shape.width()*scale)/2,
                    state*PITCH+(PITCH-shape.height()*scale)/2);
            g.scale(scale,scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int n=0;
            for(var facet:shape.facets()) {
                double[] p=facet.points(); double cx=0,cy=0;
                for(int i=0;i<p.length;i+=2) {cx+=p[i];cy+=p[i+1];}
                cx/=p.length/2;cy/=p.length/2;
                Color base=switch(shape.material()) {
                    case ICE -> new Color(155,189,202);
                    case HUSK -> new Color(140,136,126);
                    default -> new Color(112,108,99);
                };
                if(state<2) {
                    var body=new Path2D.Double();body.moveTo(p[0],shape.height()-p[1]);
                    for(int i=2;i<p.length;i+=2)body.lineTo(p[i],shape.height()-p[i+1]);
                    body.closePath();g.setColor(n++%2==0?base:base.darker());g.fill(body);
                }
                if(flat && state<2) {
                    if(state==1) {
                        var outline=new Path2D.Double();outline.moveTo(p[0],shape.height()-p[1]);
                        for(int i=2;i<p.length;i+=2)outline.lineTo(p[i],shape.height()-p[i+1]);
                        outline.closePath();g.setColor(base.darker().darker());g.setStroke(new BasicStroke(.035f));g.draw(outline);
                    }
                    continue;
                }
                for(int i=0;i<p.length;i+=2) {
                    int j=(i+2)%p.length;
                    double shrink=state==2?.65:1;
                    double tx=(cx+p[i]+p[j])/3,ty=(cy+p[i+1]+p[j+1])/3;
                    var tri=new Path2D.Double();
                    tri.moveTo(tx+(cx-tx)*shrink,shape.height()-(ty+(cy-ty)*shrink));
                    tri.lineTo(tx+(p[i]-tx)*shrink,shape.height()-(ty+(p[i+1]-ty)*shrink));
                    tri.lineTo(tx+(p[j]-tx)*shrink,shape.height()-(ty+(p[j+1]-ty)*shrink));tri.closePath();
                    g.setColor(i%4==0&&!flat?base.brighter():base);g.fill(tri);
                    if(state==1) {g.setColor(base.darker().darker());g.setStroke(new BasicStroke(.035f));g.draw(tri);}
                }
            }
            g.dispose();
        }
        return image;
    }

    /** Writes a sheet's guide, and its authoring seed unless measured source cuts already exist. */
    static void write(Path root,String sheetName,List<MineralFormation> shapes,boolean flat,String note) throws Exception {
        ImageIO.write(render(shapes,flat),"png",root.resolve("art-source/templates/"+sheetName+".png").toFile());
        // Accepted source cuts include measured ImageGen registration. Never overwrite them.
        var docPath=root.resolve("art-source/tilesets/"+sheetName+".tileset-authoring.json");
        if (Files.exists(docPath)) return;
        var entries=new JSONArray();
        for(int col=0;col<shapes.size();col++) for(int state=0;state<3;state++) {
            var shape=shapes.get(col);
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
                    .put("fogSurface",mask).put("rect",new JSONArray().put(col*PITCH+(PITCH-w)/2)
                            .put(state*PITCH+(PITCH-h)/2).put(w).put(h)));
        }
        var doc=new JSONObject().put("sheetName",sheetName).put("sheet","art-source/tilesets/"+sheetName+".raw.png")
                .put("cellPx",64).put("gridCols",shapes.size()).put("gridRows",3).put("idPrefix","formation").put("alphaMin",40)
                .put("note",note).put("entries",entries);
        Files.writeString(docPath,doc.toString(2)+"\n");
    }

    public static void main(String[] args) throws Exception {
        write(Path.of(args[0]),"planet-formations",planet(),false,
                "PlanetFormationTemplate: full footprint cuts, four columns, intact/damaged/destroyed rows. MineralFormation polygons own collision and fog masks. No ground backing.");
    }
}
