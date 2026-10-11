package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
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
import java.util.ArrayList;
import java.util.List;

/** Neutral rack palette and topology-owned bulkheads in the established review fixture. */
public final class ShipNeutralSnapshotSuite implements SnapshotSuite {
    private static final String WALL = "ship.neutral-bulkhead";
    private static final String[] PARTS = {"base", "frame", "cartons", "bins", "engine", "pipes", "picked-tray", "cases"};
    @Override public String id() { return "ship-neutral"; }
    @Override public String label() { return "Mech Lab neutral shelving and bulkheads"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        MapResult after = room(WALL), ceramic = room("colony.ceramic-wall");
        BufferedImage before = renderer.renderView(WarehouseRackSnapshotSuite.room(), 42, 16, 11.5f, 28, 21, 24);
        BufferedImage small = renderer.renderView(after, 42, 16, 11.5f, 28, 21, 24);
        BufferedImage alternate = renderer.renderView(ceramic, 42, 16, 11.5f, 28, 21, 24);
        return List.of(
                new SnapshotArtifact("ship-neutral-game-scale.png", small),
                new SnapshotArtifact("ship-neutral-room.png", renderer.renderView(after, 42, 16, 11.5f, 28, 21, 40)),
                new SnapshotArtifact("ship-neutral-close.png", renderer.renderView(after, 42, 23.5f, 12, 11, 18, 40)),
                new SnapshotArtifact("ship-neutral-comparison.png", pair(before, small,
                        "BEFORE / blue-orange racks and legacy walls", "NEUTRAL / graphite racks and enamel bulkheads")),
                new SnapshotArtifact("ship-neutral-wall-options.png", pair(small, alternate,
                        "NEW / neutral ship bulkhead", "ALTERNATIVE / existing colony ceramic wall")),
                new SnapshotArtifact("ship-neutral-wall-joins.png", renderer.render(joins(), 42, 48)),
                new SnapshotArtifact("ship-neutral-guide.png", ShipBulkheadTemplate.render()));
    }

    private static MapResult room(String wall) {
        MapResult base = WarehouseRackSnapshotSuite.room();
        for (int y=0; y<24; y++) for (int x=0; x<32; x++) {
            if (base.topology.isWall(x,y)) base.topology.setSurface(x,y,base.topology.surfaceIndex(wall));
        }
        List<Doodad> objects = new ArrayList<>();
        for (Doodad d : base.doodads) {
            Doodad replacement = d;
            for (String part : PARTS) {
                DoodadDef old = TileRegistry.installed().doodad("warehouse-racks." + part);
                if (d.sheetPath.equals(old.sheetPath) && d.tile.col==old.col && d.tile.row==old.row) {
                    DoodadDef muted = TileRegistry.installed().doodad("warehouse-racks-muted." + part);
                    replacement = new Doodad(d.cellX, d.cellY, muted, d.quarterTurns, d.mirrored);
                    break;
                }
            }
            objects.add(replacement);
        }
        return new MapResult(base.grid, base.topology, 4, 4, 5, 4, base.pointsOfInterest, objects);
    }

    /** Long joins, all outer corners, exposed ends, a T, a pier and a solid 3x3 core. */
    private static MapResult joins() {
        NavigationGrid grid = new NavigationGrid(18, 14);
        CellTopology topology = new CellTopology(18, 14);
        for (int y=0; y<14; y++) for (int x=0; x<18; x++) {
            grid.setWalkableFloor(x,y); topology.setGroundKind(x,y,GroundKind.INDOOR);
            topology.setSurface(x,y,topology.surfaceIndex("auto-shop.concrete"));
        }
        for (int y=2; y<=11; y++) for (int x=1; x<=8; x++) {
            if ((x==1 || x==8 || y==2 || y==11) && !(y==2 && (x==4 || x==5))) wall(grid,topology,x,y);
        }
        for (int x=10; x<=16; x++) wall(grid,topology,x,10);
        for (int y=5; y<=10; y++) wall(grid,topology,13,y);
        for (int y=1; y<=3; y++) for (int x=10; x<=12; x++) wall(grid,topology,x,y);
        wall(grid,topology,16,2);
        WallMasks.stampUnclaimed(topology);
        return new MapResult(grid,topology,0,0,17,13,List.of(),List.of());
    }
    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x,y,false); topology.setWall(x,y,true);
        topology.setSurface(x,y,topology.surfaceIndex(WALL));
    }
    private static BufferedImage pair(BufferedImage a, BufferedImage b, String left, String right) {
        BufferedImage out = new BufferedImage(a.getWidth()*2+16,a.getHeight()+48,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x232622)); g.fillRect(0,0,out.getWidth(),out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16)); g.setColor(new Color(0xDDDFD5));
        g.drawString(left,16,29); g.drawString(right,a.getWidth()+32,29);
        g.drawImage(a,0,48,null); g.drawImage(b,a.getWidth()+16,48,null); g.dispose(); return out;
    }
}
