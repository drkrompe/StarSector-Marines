package com.dillon.starsectormarines.tools.roadauthoring;

import org.json.JSONObject;
import org.json.JSONArray;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Sole producer of the pavement atlas and its catalog, from a retained procedural recipe. */
public final class RoadMaterialBaker {
    private RoadMaterialBaker() {}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args.length==0?".":args[0]);
        JSONObject recipe=new JSONObject(Files.readString(root.resolve("art-source/roads/pavement.recipe.json")));
        int size=recipe.getInt("cellPx"),variants=recipe.getInt("variants");
        JSONArray families=recipe.getJSONArray("materials"),blocks=new JSONArray();
        BufferedImage atlas=new BufferedImage(size*variants,size*families.length()*3,BufferedImage.TYPE_INT_ARGB);
        Graphics2D out=atlas.createGraphics();
        for(int f=0;f<families.length();f++) for(int age=0;age<3;age++) {
            JSONObject family=families.getJSONObject(f); JSONArray cells=new JSONArray();
            for(int v=0;v<variants;v++) {
                out.drawImage(tile(size,recipe.getLong("seed")+v*1337L,family,age),v*size,(f*3+age)*size,null);
                cells.put(new JSONArray().put(v).put(f*3+age));
            }
            blocks.put(new JSONObject().put("id","pavement."+family.getString("id")+"."+new String[]{"new","worn","cracked"}[age]).put("cells",cells));
        }
        out.dispose();
        String asset="graphics/tilesets/pavement.png";
        Files.createDirectories(root.resolve("mod").resolve(asset).getParent());
        ImageIO.write(atlas,"png",root.resolve("mod").resolve(asset).toFile());
        Files.writeString(root.resolve("mod/data/tilesets/pavement.tileset.json"),new JSONObject().put("sheet",asset)
                .put("cellPx",size).put("blocks",blocks).toString(2)+"\n");
    }
    private static BufferedImage tile(int size,long seed,JSONObject family,int age) throws Exception {
        BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Color base=Color.decode(family.getString("base")); Random noise=new Random(seed);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) {
            int grain=noise.nextInt(13)-6+age*3;
            int r=Math.max(0,Math.min(255,base.getRed()+grain)),g=Math.max(0,Math.min(255,base.getGreen()+grain)),b=Math.max(0,Math.min(255,base.getBlue()+grain));
            image.setRGB(x,y,0xff000000|r<<16|g<<8|b);
        }
        Graphics2D g=image.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(family.getString("id").equals("concrete")) {
            g.setColor(new Color(45,45,42,45));g.setStroke(new BasicStroke(1.2f));
            g.drawLine(3,3,size-4,3);g.drawLine(3,3,3,size-4);
        }
        // Marks stay inside their tile: seams retain the same aggregate, without a repeated dark rim.
        Random cracks=new Random(seed^0x56AFA39L);
        int count=age==0?0:age==1?(cracks.nextDouble()<.35?1:0):(cracks.nextDouble()<.7?1:0);
        for(int i=0;i<count;i++) {
            float x=12+cracks.nextFloat()*(size-24),y=8;
            Path2D path=new Path2D.Float();path.moveTo(x,y);
            for(int step=0;step<8;step++) {
                x=Math.max(6,Math.min(size-7,x+cracks.nextFloat()*10-5));
                y+=5+cracks.nextFloat();path.lineTo(x,y);
                if(step==3 && age==2) {path.lineTo(x+8,y-9);path.moveTo(x,y);}
            }
            g.setColor(new Color(150,143,128,45));g.setStroke(new BasicStroke(age==2?1.9f:1.1f));g.draw(path);
            g.setColor(new Color(12,14,14,age==2?190:105));g.setStroke(new BasicStroke(age==2?.9f:.6f));g.draw(path);
            if(age==2 && cracks.nextDouble()<.3) {g.setColor(new Color(26,28,27,140));g.fillOval((int)x-3,(int)y-2,6,4);}
        }
        g.dispose();return image;
    }
}
