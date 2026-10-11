package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
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

/** Preview layout only: two warehouse rack rows replace the checkerboard shop fixtures. */
public final class WarehouseRackSnapshotSuite implements SnapshotSuite {
    private static final String[][] STOCK = {
            {"cartons", "bins", "engine"}, {"pipes", null, "cases"},
            {"cases", "engine", "cartons"}, {"bins", "picked-tray", "pipes"}
    };

    @Override public String id() { return "warehouse-racks"; }
    @Override public String label() { return "Mech Lab layered warehouse storage"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        MapResult before = AutoShopSnapshotSuite.room(), after = room();
        BufferedImage small = renderer.renderView(after, 42, 16, 11.5f, 28, 21, 24);
        return List.of(
                new SnapshotArtifact("warehouse-racks-guide.png", WarehouseRackTemplate.render()),
                new SnapshotArtifact("warehouse-racks-game-scale.png", small),
                new SnapshotArtifact("warehouse-racks-room.png",
                        renderer.renderView(after, 42, 16, 11.5f, 28, 21, 40)),
                new SnapshotArtifact("warehouse-racks-close.png",
                        renderer.renderView(after, 42, 23.5f, 12, 11, 18, 40)),
                new SnapshotArtifact("warehouse-racks-comparison.png", comparison(
                        renderer.renderView(before, 42, 16, 11.5f, 28, 21, 24), small)),
                new SnapshotArtifact("warehouse-racks-layers.png", renderer.render(layers(), 42, 48)));
    }

    static MapResult room() {
        MapResult base = AutoShopSnapshotSuite.room();
        List<Doodad> objects = new ArrayList<>();
        for (Doodad d : base.doodads) {
            if (d.cellX < 21 || d.cellX > 27 || d.cellY < 4 || d.cellY > 19) objects.add(d);
        }
        // Clear the old fixtures in this review scene before assigning the new rack footprints.
        for (int y=4; y<=19; y++) for (int x=21; x<=27; x++) {
            base.grid.setWalkableFloor(x, y);
            base.grid.setSeeThrough(x, y, true);
            base.grid.setEdgeCoverSuppressed(x, y, false);
            base.topology.setFixture(x, y, false);
        }
        int index = 0;
        for (int x : new int[]{21, 25}) for (int y : new int[]{5, 13}) {
            rack(objects, x, y, STOCK[index++], true);
            for (int dy=0; dy<6; dy++) for (int dx=0; dx<2; dx++) {
                base.grid.setWalkable(x+dx, y+dy, false);
                base.topology.setFixture(x+dx, y+dy, true);
            }
        }
        // The long aisle, middle cross-aisle and both end aisles must stay open.
        for (int y=4; y<=19; y++) for (int x=23; x<=24; x++) requireAisle(base, x, y);
        for (int y : new int[]{4, 11, 12, 19}) for (int x=21; x<=27; x++) requireAisle(base, x, y);
        return new MapResult(base.grid, base.topology, 4, 4, 5, 4, base.pointsOfInterest, objects);
    }

    private static void requireAisle(MapResult map, int x, int y) {
        if (!map.grid.isWalkable(x, y)) throw new IllegalStateException("Blocked warehouse aisle " + x + "," + y);
    }

    private static void rack(List<Doodad> objects, int x, int y, String[] stock, boolean frame) {
        add(objects, x, y, "base");
        for (int slot=0; slot<3; slot++) {
            if (stock[slot] != null) add(objects, x, y+slot*2, stock[slot]);
        }
        if (frame) add(objects, x, y, "frame");
    }

    private static void add(List<Doodad> objects, int x, int y, String part) {
        DoodadDef def = TileRegistry.installed().doodad("warehouse-racks." + part);
        if (def == null) throw new IllegalStateException("Missing rack layer: " + part);
        objects.add(new Doodad(x, y, def));
    }

    /** Left to right: empty deck, independent contents, contents behind the retaining frame. */
    private static MapResult layers() {
        NavigationGrid grid = new NavigationGrid(11, 8);
        CellTopology topology = new CellTopology(11, 8);
        for (int y=0; y<8; y++) for (int x=0; x<11; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            topology.setSurface(x, y, topology.surfaceIndex("auto-shop.concrete"));
        }
        List<Doodad> objects = new ArrayList<>();
        rack(objects, 1, 1, new String[]{null, null, null}, false);
        rack(objects, 4, 1, STOCK[0], false);
        rack(objects, 7, 1, STOCK[0], true);
        return new MapResult(grid, topology, 0, 0, 10, 7, List.of(), objects);
    }

    private static BufferedImage comparison(BufferedImage before, BufferedImage after) {
        BufferedImage out = new BufferedImage(before.getWidth()*2+16, before.getHeight()+48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x172027)); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16)); g.setColor(new Color(0xD4DFDC));
        g.drawString("BEFORE / separate fixtures across the floor", 16, 29);
        g.drawString("WAREHOUSE / continuous racks, layered stock, clear aisles", before.getWidth()+32, 29);
        g.drawImage(before, 0, 48, null); g.drawImage(after, before.getWidth()+16, 48, null);
        g.dispose(); return out;
    }
}
