package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Measures generated slot bounds and restores the intact frame's padding; never edits raster pixels. */
public final class ComposableDamageRegistration {
    public static void main(String[] args) throws Exception {
        String family = args[0]; int cols = Integer.parseInt(args[1]);
        Path base = Path.of("art-source/console-bank/damage");
        List<String> manifest = Files.readAllLines(base.resolve(family+".tsv"));
        BufferedImage raw = ImageIO.read(Path.of("art-source/tilesets/"+family+"-damage.raw.png").toFile());
        int rows = 2 * ((manifest.size()+cols-1)/cols);
        double pitchX = raw.getWidth()/(double)cols, pitchY = raw.getHeight()/(double)rows;
        List<String> output = new ArrayList<>();
        for(int i=0;i<manifest.size();i++) {
            String[] v=manifest.get(i).split("\t");
            BufferedImage atlas=ImageIO.read(Path.of(v[1]).toFile());
            int sx=Integer.parseInt(v[2]),sy=Integer.parseInt(v[3]),sw=Integer.parseInt(v[4]),sh=Integer.parseInt(v[5]);
            int[] original=bounds(atlas,sx,sy,sw,sh);
            for(int state=0;state<2;state++) {
                int x=(int)Math.round(i%cols*pitchX),y=(int)Math.round((i/cols*2+state)*pitchY);
                int right=(int)Math.round((i%cols+1)*pitchX),bottom=(int)Math.round((i/cols*2+state+1)*pitchY);
                int[] art=bounds(raw,x,y,right-x,bottom-y);
                // Damaged hulls retain the intact object's padding; rubble uses the full guide frame.
                double fx,fy,fw,fh;
                if(state==0) {
                    fw=(art[2]-art[0]+1)*(double)sw/(original[2]-original[0]+1);
                    fh=(art[3]-art[1]+1)*(double)sh/(original[3]-original[1]+1);
                    fx=art[0]-(original[0]-sx)*fw/sw; fy=art[1]-(original[1]-sy)*fh/sh;
                } else {
                    int cellsX=Integer.parseInt(v[6]),cellsY=Integer.parseInt(v[7]);
                    double scale=320.0/Math.max(cellsX,cellsY);
                    fw=cellsX*scale*pitchX/384;fh=cellsY*scale*pitchY/384;
                    fx=x+(pitchX-fw)/2;fy=y+(pitchY-fh)/2;
                    // Include loose fragments that ImageGen moved inside the slot's original gutter.
                    double farX=Math.max(fx+fw,art[2]+2),farY=Math.max(fy+fh,art[3]+2);
                    fx=Math.min(fx,art[0]-1);fy=Math.min(fy,art[1]-1);fw=farX-fx;fh=farY-fy;
                }
                int cx=Math.max(x,(int)Math.floor(fx)),cy=Math.max(y,(int)Math.floor(fy));
                int cw=Math.min(right,(int)Math.ceil(fx+fw))-cx,ch=Math.min(bottom,(int)Math.ceil(fy+fh))-cy;
                output.add(v[0]+(state==0?"-damaged":"-destroyed")+"\t"+cx+"\t"+cy+"\t"+cw+"\t"+ch
                        +"\t"+v[6]+"\t"+v[7]+"\t"+(state==0?v[8]:"none")+"\t"+(state==0?v[9]:"0"));
            }
        }
        Files.write(base.resolve(family+"-registered.tsv"),output);
        System.out.println(family+": "+raw.getWidth()+"x"+raw.getHeight()+", "+output.size()+" registered full frames");
    }
    private static int[] bounds(BufferedImage im,int x,int y,int width,int height) {
        int[] b={x+width,y+height,x-1,y-1};
        for(int py=y;py<y+height;py++)for(int px=x;px<x+width;px++)if((im.getRGB(px,py)>>>24)>160){
            b[0]=Math.min(b[0],px);b[1]=Math.min(b[1],py);b[2]=Math.max(b[2],px);b[3]=Math.max(b[3],py);
        }
        if(b[2]<b[0])throw new IllegalStateException("Empty sprite at "+x+","+y);
        return b;
    }
}
