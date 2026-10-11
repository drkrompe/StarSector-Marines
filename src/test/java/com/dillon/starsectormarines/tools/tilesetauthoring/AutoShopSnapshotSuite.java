package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Second art study: whole concrete bays and a continuous service assembly. */
public final class AutoShopSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "auto-shop"; }
    @Override public String label() { return "Mech Lab automotive service shop study"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        MapResult after = room();
        BufferedImage before = renderer.renderView(ShipBaySnapshotSuite.room(true),42,16,11.5f,28,21,24);
        BufferedImage small = renderer.renderView(after,42,16,11.5f,28,21,24);
        return List.of(new SnapshotArtifact("auto-shop-guide.png",AutoShopTemplate.render()),
                new SnapshotArtifact("auto-shop-comparison.png",comparison(before,small)),
                new SnapshotArtifact("auto-shop-room.png",renderer.renderView(after,42,16,11.5f,28,21,40)),
                new SnapshotArtifact("auto-shop-game-scale.png",small),
                new SnapshotArtifact("auto-shop-close.png",renderer.renderView(after,42,8,7,12,12,48)));
    }

    private static MapResult room() {
        MapResult base = ShipBaySnapshotSuite.room(false);
        int concrete = base.topology.surfaceIndex("auto-shop.concrete");
        int passage = base.topology.surfaceIndex("auto-shop.passage");
        for(int y=0;y<24;y++) for(int x=0;x<32;x++) {
            if(!base.topology.isWall(x,y)) base.topology.setSurface(x,y,y<=2?passage:concrete);
        }
        List<Doodad> paving=new ArrayList<>(), fixtures=new ArrayList<>();
        // The same four 5x7 footprints, with their open mouths toward the central lane.
        for(int x:new int[]{7,15}) {
            add(paving,x,4,"bay",2);
            add(paving,x,13,"bay",0);
        }
        add(paving,4,6,"service-run",0);
        for(int x:new int[]{8,16,24}) add(paving,x,2,"drain",0);
        for(Doodad d:base.doodads) {
            if(is(d,"doodad.fl-striped-yellow") || is(d,"doodad.fl-grate-1") || is(d,"doodad.fl-grate-2")) continue;
            String replacement=null;
            if(is(d,"doodad.industrial-machine-tool") || is(d,"doodad.box")) replacement="tool-chest";
            if(is(d,"doodad.industrial-control-console") || is(d,"doodad.office-workstation-bank")
                    || is(d,"doodad.military-command-console")) replacement="diagnostic";
            for(String corner:new String[]{"nw","ne","sw","se"})
                if(is(d,"doodad.industrial-fence-corner-"+corner)) replacement="lift-column";
            if(replacement==null) fixtures.add(d);
            else {
                DoodadDef art=TileRegistry.installed().doodad("auto-shop."+replacement);
                // Reskin only: retain the fitted fixture's tactical properties and footprint.
                DoodadDef def=new DoodadDef(art.id,art.sheetPath,art.col,art.row,
                        DoodadCover.fromLevel(d.cover),d.ballisticHalfHeight,
                        d.sourceCellsX,d.sourceCellsY,d.preferredWallSide,art.sourceCellPx);
                fixtures.add(new Doodad(d.cellX,d.cellY,def,d.quarterTurns,d.mirrored));
            }
        }
        paving.addAll(fixtures);
        return new MapResult(base.grid,base.topology,4,4,5,4,base.pointsOfInterest,paving);
    }

    private static boolean is(Doodad d,String id) {
        DoodadDef def=TileRegistry.installed().doodad(id);
        return def!=null && d.sheetPath.equals(def.sheetPath) && d.tile.col==def.col && d.tile.row==def.row;
    }
    private static void add(List<Doodad> list,int x,int y,String part,int turn) {
        DoodadDef def=TileRegistry.installed().doodad("auto-shop."+part);
        if(def==null) throw new IllegalStateException("Missing auto-shop part: "+part);
        list.add(new Doodad(x,y,def,turn));
    }
    private static BufferedImage comparison(BufferedImage before,BufferedImage after) {
        BufferedImage out=new BufferedImage(before.getWidth()*2+16,before.getHeight()+48,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();g.setColor(new Color(0x172027));g.fillRect(0,0,out.getWidth(),out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));g.setColor(new Color(0xD4DFDC));
        g.drawString("FIRST STUDY / grating and modular pipes",16,29);
        g.drawString("AUTO SHOP / concrete and complete bays / 24 px per cell",before.getWidth()+32,29);
        g.drawImage(before,0,48,null);g.drawImage(after,before.getWidth()+16,48,null);g.dispose();return out;
    }
}
