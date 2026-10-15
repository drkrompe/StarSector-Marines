package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.tilesetauthoring.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.tools.tilesetauthoring.ConsoleBankLayout.Piece;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Same six exported cells recombined by the prototype stroke resolver and production prop renderer. */
public final class ConsoleBankSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "console-banks"; }
    @Override public String label() { return "Modular console bank prototype"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        return List.of(
                new SnapshotArtifact("console-bank-assemblies.png", board(context, false)),
                new SnapshotArtifact("console-bank-grid.png", board(context, true)));
    }

    private static BufferedImage board(SnapshotContext context, boolean gridOverlay) {
        BufferedImage board=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=board.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x101A23));g.fillRect(0,0,1920,1080);
        text(g,"CONSOLE BANKS",64,64,30,0xEDF3EA);
        text(g,"Six reusable cells / geometry-guided ImageGen / prototype",64,100,18,0xA8BABD);
        String[] titles={"01   L BANK","02   U BANK","03   ISLAND"};
        String[] captions={"Controls face the inner working area","Open operator bay / capped ends","Controls face outward / service core inside"};
        for(int shape=0;shape<3;shape++) {
            int left=48+shape*624;
            List<Cell> stroke=stroke(shape);
            List<Piece> pieces=ConsoleBankLayout.resolve(stroke,shape==2,shape!=2);
            MapResult map=map(pieces);
            HeadlessBattleMapRenderer renderer=new HeadlessBattleMapRenderer(context.modRoot());
            // Eight cells at 64px, drawn directly at the review camera scale; no resized UI.
            BufferedImage detail=renderer.render(map,42,64);
            g.drawImage(detail,left+32,180,null);
            if(gridOverlay) {
                g.setColor(new Color(125,207,214,70));
                for(int i=0;i<=8;i++) {
                    g.drawLine(left+32+i*64,180,left+32+i*64,692);
                    g.drawLine(left+32,180+i*64,left+544,180+i*64);
                }
            }
            text(g,titles[shape],left+32,150,23,0xD5E9DC);
            text(g,captions[shape],left+32,728,16,0xB3C5C6);
            text(g,pieces.size()+" cells / "+(shape==2 ? "4 outside corners" : shape==1 ? "2 inside corners + 2 caps" : "1 inside corner + 2 caps"),
                    left+32,757,15,0x7FA3A8);
            BufferedImage scale=renderer.render(map,42,24);
            g.drawImage(scale,left+192,800,null);
            text(g,"24 px/cell",left+32,835,15,0xA8BABD);
        }
        text(g,"Upper views: 64 px/cell  |  Lower views: 24 px/cell  |  1920 x 1080 / UI scale 100%",64,1031,16,0x7FA3A8);
        g.dispose();
        return board;
    }

    static List<Cell> stroke(int shape) {
        List<Cell> cells=new ArrayList<>();
        if(shape<2) {
            for(int y=5;y>=1;y--) cells.add(new Cell(1,y));
            for(int x=2;x<=5;x++) cells.add(new Cell(x,1));
            if(shape==1) for(int y=2;y<=5;y++) cells.add(new Cell(5,y));
        } else {
            for(int x=1;x<=5;x++) cells.add(new Cell(x,1));
            for(int y=2;y<=5;y++) cells.add(new Cell(5,y));
            for(int x=4;x>=1;x--) cells.add(new Cell(x,5));
            for(int y=4;y>=2;y--) cells.add(new Cell(1,y));
        }
        return cells;
    }

    private static MapResult map(List<Piece> pieces) {
        NavigationGrid grid=new NavigationGrid(8,8);
        CellTopology topology=new CellTopology(8,8);
        for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
            grid.setWalkableFloor(x,y);
            topology.setGroundKind(x,y,CellTopology.GroundKind.STONE);
            topology.setSurface(x,y,topology.surfaceIndex("ship.passage-deck"));
        }
        List<Doodad> props=new ArrayList<>();
        for(Piece p:pieces) {
            int x=p.cell().x(), y=7-p.cell().y();
            props.add(new Doodad(x,y,TileRegistry.installed().doodad(p.id()),-p.clockwiseTurns()));
            grid.setWalkable(x,y,false);
            grid.setSeeThrough(x,y,true);
            topology.setFixture(x,y,true);
        }
        return new MapResult(grid,topology,0,0,7,7,List.of(),props);
    }

    private static void text(Graphics2D g,String text,int x,int y,int size,int rgb) {
        g.setFont(new Font("SansSerif",Font.PLAIN,size));
        g.setColor(new Color(rgb));g.drawString(text,x,y);
    }
}
