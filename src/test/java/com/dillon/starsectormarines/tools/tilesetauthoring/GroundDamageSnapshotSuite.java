package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;

public final class GroundDamageSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ground-damage"; }
    @Override public String label() { return "Material-matched ground damage"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        NavigationGrid grid = new NavigationGrid(18, 15);
        CellTopology topology = new CellTopology(18, 15);
        for (int y = 0; y < 15; y++) for (int x = 0; x < 18; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            int family = (2 - y / 5) * 3 + x / 6;
            topology.setSurface(x, y, topology.surfaceIndex("colony." + GroundDamageReskinTemplate.MATERIALS[family]));
        }
        MapResult map = new MapResult(grid, topology, 0, 0, 17, 14, List.of(), List.of());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        BufferedImage before = renderer.render(map, 42, 40);
        MapEditor editor = new MapEditor(new NavigationService(grid, topology));
        for (int y = 0; y < 15; y++) for (int x = 0; x < 18; x++) {
            // A compact patch within each 6x5 material field, with intact neighbors on all sides.
            if (x % 6 >= 2 && x % 6 <= 3 && y % 5 >= 1 && y % 5 <= 3) editor.damageGround(x, y);
        }
        BufferedImage after = renderer.render(map, 42, 40);
        BufferedImage comparison = new BufferedImage(before.getWidth() * 2 + 16, before.getHeight() + 62,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = comparison.createGraphics();
        g.setColor(new Color(0x191D20)); g.fillRect(0, 0, comparison.getWidth(), comparison.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); g.setColor(new Color(0xE9E1CE));
        g.drawString("INTACT", 16, 25); g.drawString("DAMAGED / same floor, localized fractures", before.getWidth() + 32, 25);
        g.drawImage(before, 0, 40, null); g.drawImage(after, before.getWidth() + 16, 40, null);
        g.dispose();
        List<SnapshotArtifact> artifacts = new ArrayList<>(List.of(new SnapshotArtifact("ground-damage-template.png", GroundDamageReskinTemplate.render()),
                new SnapshotArtifact("ground-damage-comparison.png", comparison),
                new SnapshotArtifact("ground-damage.png", after)));
        artifacts.add(new SnapshotArtifact("terrain-blast-template.png",TerrainBlastTemplate.render()));
        artifacts.add(new SnapshotArtifact("terrain-blast-comparison.png",terrainComparison(context)));
        return artifacts;
    }

    private BufferedImage terrainComparison(SnapshotContext context) throws Exception {
        var kinds = new CellTopology.GroundKind[]{CellTopology.GroundKind.GRASS,CellTopology.GroundKind.DIRT,
                CellTopology.GroundKind.SAND,CellTopology.GroundKind.STREET,CellTopology.GroundKind.BRICK,
                CellTopology.GroundKind.INDOOR,CellTopology.GroundKind.TILE,CellTopology.GroundKind.STRIPED,
                CellTopology.GroundKind.INDOOR};
        String[] surfaces = {null,null,null,null,null,"urban.floor","road.tile","road.striped","road.vent"};
        String[] labels = {"GRASS","DIRT","SAND","STREET","BRICK","INDOOR","METAL","SAFETY FLOOR","GRATING"};
        NavigationGrid grid = new NavigationGrid(27,21);
        CellTopology topology = new CellTopology(27,21);
        for (int y=0;y<21;y++) for(int x=0;x<27;x++) {
            int family = (2-y/7)*3+x/9;
            grid.setWalkableFloor(x,y); topology.setGroundKind(x,y,kinds[family]);
            if (surfaces[family]!=null) topology.setSurface(x,y,topology.surfaceIndex(surfaces[family]));
        }
        MapResult map = new MapResult(grid,topology,0,0,26,20,List.of(),List.of());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        topology.setNatureOverlayIndex(3,17,TileRegistry.installed().tile("nature.rock-small-1").index);
        BufferedImage before = renderer.render(map,42,32);
        MapEditor editor = new MapEditor(new NavigationService(grid,topology));
        for(int row=0;row<3;row++) for(int col=0;col<3;col++) {
            editor.damageGroundInRadius(col*9+3.2f,row*7+3.3f,2.2f);
            editor.damageGroundInRadius(col*9+5.8f,row*7+3.8f,1.2f);
        }
        BufferedImage after = renderer.render(map,42,32);
        BufferedImage comparison = new BufferedImage(before.getWidth()*2+16,before.getHeight()+40,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=comparison.createGraphics();
        g.setColor(new Color(0x191D20));g.fillRect(0,0,comparison.getWidth(),comparison.getHeight());
        g.drawImage(before,0,40,null);g.drawImage(after,before.getWidth()+16,40,null);
        g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));
        g.drawString("INTACT",12,25);g.drawString("AFTER BLASTS / 32 pixels per cell",before.getWidth()+28,25);
        for(int i=0;i<9;i++) for(int half=0;half<2;half++) {
            int x=half*(before.getWidth()+16)+(i%3)*9*32+8,y=40+(i/3)*7*32+20;
            g.setColor(new Color(0x191D20));g.fillRect(x-3,y-16,150,21);
            g.setColor(Color.WHITE);g.drawString(labels[i],x,y);
        }
        g.dispose();return comparison;
    }
}
