package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class NeighborhoodLotSnapshotSuite implements SnapshotSuite {
    public String id(){return "neighborhood-lots";}
    public String label(){return "Roomed buildings and medium houses on larger lots";}
    public List<SnapshotArtifact> render(SnapshotContext context)throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer=new HeadlessBattleMapRenderer(context.modRoot());var out=new ArrayList<SnapshotArtifact>();
        for(var front:new BuildingPlacement.Side[]{BuildingPlacement.Side.TOP,BuildingPlacement.Side.RIGHT}) {
            GenContext c=NeighborhoodBuildingsTest.open(84,48);
            for(int kind=0;kind<4;kind++) {
                int x=2+kind*20;
                LotYardTemplate.place(new BlockLeaf(x,25,x+16,45,false),c,NeighborhoodBuildingsTest.config(kind),front);
                var poi=BuildingShellCore.carve(new BlockLeaf(x,5,x+16,19,false),c.grid,c.topology,c.doodads,c.rng,
                        NeighborhoodBuildingsTest.config(kind),new BuildingPlacement(front,true));c.pois.add(poi);
            }
            NeighborhoodBuildingsTest.assertConnected(c);
            out.add(new SnapshotArtifact("rooms-and-yards-"+front.name().toLowerCase()+".png",caption(renderer.renderView(
                    new MapResult(c.grid,c.topology,0,0,0,0,c.pois,c.doodads),42,40,25,80,45,24),
                    "HOME / SHOP / WORKSHOP / OFFICE", "Medium buildings on yard lots above / larger partitioned interiors below / production renderer")));
        }
        for(long seed:new long[]{0,2,7}) {
            var map=new BspCityGenerator().generate(160,120,seed);
            out.add(new SnapshotArtifact("neighborhood-seed-"+seed+".png",caption(renderer.renderView(map,seed,80,60,80,45,24),
                    "GENERATED NEIGHBORHOOD / SEED "+seed,"Room programs and lot variants / 1920 x 1080 / no live capture")));
        }
        return out;
    }
    private static BufferedImage caption(BufferedImage image,String title,String note) {
        var g=image.createGraphics();g.setColor(new Color(0x142028));g.fillRect(0,0,1920,60);g.fillRect(0,1030,1920,50);
        g.setColor(new Color(0xe2ecea));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,24));g.drawString(title,24,40);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));g.drawString(note,24,1063);g.dispose();return image;
    }
}
