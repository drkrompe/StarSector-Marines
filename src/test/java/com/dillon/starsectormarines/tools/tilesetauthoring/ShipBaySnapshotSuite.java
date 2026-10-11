package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.VehicleBayFitting;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
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
import java.util.Random;

/** Art-only study over the production vehicle-bay fitting. No campaign adoption. */
public final class ShipBaySnapshotSuite implements SnapshotSuite {
    private static final int WIDTH = 32, HEIGHT = 24, ORIGIN = 4;
    @Override public String id() { return "ship-bay"; }
    @Override public String label() { return "Mech bay geometry-first material study"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        MapResult before = room(false), after = room(true);
        BufferedImage oldFrame = renderer.renderView(before, 42, 7.5f, 6.5f, 15, 13, 48);
        BufferedImage newFrame = renderer.renderView(after, 42, 7.5f, 6.5f, 15, 13, 48);
        return List.of(new SnapshotArtifact("ship-bay-guide.png", ShipBayReskinTemplate.render()),
                new SnapshotArtifact("ship-bay-comparison.png", comparison(oldFrame, newFrame)),
                new SnapshotArtifact("ship-bay-close.png", newFrame),
                new SnapshotArtifact("ship-bay-game-scale.png",
                        renderer.renderView(after, 42, 7.5f, 6.5f, 15, 13, 32)),
                new SnapshotArtifact("ship-bay-room.png", renderer.render(after, 42, 32)),
                new SnapshotArtifact("ship-bay-connections.png", renderer.render(connections(), 42, 64)));
    }

    static MapResult room(boolean candidate) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        int deck = candidate ? topology.surfaceIndex("ship-bay.deck-surface") : 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            if (candidate) topology.setSurface(x, y, deck);
        }
        RoomShape shape = RoomShape.rectangle(24, 16);
        for (int[] cell : shape.wall()) {
            int x = ORIGIN + cell[0], y = ORIGIN + cell[1];
            grid.setWalkable(x, y, false); topology.setWall(x, y, true);
            // Keep the existing bulkhead art on both sides of the comparison.
            topology.setSurface(x, y, topology.surfaceIndex("urban.wall"));
        }
        List<Doorway> doors = List.of(new Doorway(4, 3), new Doorway(5, 3));
        for (Doorway door : doors) {
            grid.setWalkableFloor(door.x(), door.y());
            grid.setDoorway(door.x(), door.y(), true);
            topology.setWall(door.x(), door.y(), false);
            topology.setSurface(door.x(), door.y(), deck);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(42), WIDTH, HEIGHT, 42);
        RoomFloor floor = new RoomFloor(ctx, new StudyRoom(shape, ORIGIN, ORIGIN,
                RoomPose.CANONICAL, RoomPurpose.VEHICLE_BAY, doors), RoomFit.STANDARD);
        new VehicleBayFitting().fit(floor);
        if (!floor.circulationSurvives()) throw new IllegalStateException("Study fitting seals its circulation");
        if (candidate) {
            List<Doodad> fixtures = new ArrayList<>();
            List<Doodad> paving = new ArrayList<>();
            for (Doodad d : ctx.doodads) {
                if (is(d, "doodad.fl-striped-yellow") || is(d, "doodad.fl-grate-1") || is(d, "doodad.fl-grate-2")) {
                    int localX = (d.cellX - ORIGIN - VehicleBayFitting.VESTIBULE) % 8;
                    int localY = (d.cellY - ORIGIN) % 9;
                    boolean west = localX == 0, east = localX == 4;
                    boolean south = localY == 0, north = localY == 6;
                    String part = "grate-field";
                    int turn = 0;
                    if ((west || east) && (north || south)) {
                        part = "hazard-corner";
                        turn = north ? (west ? 0 : 3) : (west ? 1 : 2);
                    } else if (west || east || north || south) {
                        part = "hazard-edge";
                        turn = north ? 0 : west ? 1 : south ? 2 : 3;
                    }
                    add(paving, d.cellX, d.cellY, part, turn);
                } else fixtures.add(d);
            }
            // A real two-cell passage outside the room and a service route in its vestibule.
            for (int x = 0; x < WIDTH; x++) {
                add(paving, x, 1, "passage", 0);
                add(paving, x, 2, "grate-run", 0);
            }
            add(paving, 4, 4, "hatch", 0);
            add(paving, 4, 6, "pipe-terminal", 2);
            add(paving, 5, 6, "pipe-elbow", 3); // west to north
            add(paving, 5, 7, "pipe-straight", 1);
            add(paving, 5, 8, "pipe-tee", 1); // north/south/east
            add(paving, 6, 8, "pipe-terminal", 0);
            add(paving, 5, 9, "pipe-straight", 1);
            add(paving, 5, 10, "pipe-terminal", 1);
            add(paving, 6, 5, "service-panel", 0);
            ctx.doodads.clear(); ctx.doodads.addAll(paving); ctx.doodads.addAll(fixtures);
        }
        WallMasks.stampUnclaimed(topology);
        return new MapResult(grid, topology, 4, 4, 5, 4, ctx.pois, ctx.doodads);
    }

    private static boolean is(Doodad d, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        return def != null && d.sheetPath.equals(def.sheetPath) && d.tile.col == def.col && d.tile.row == def.row;
    }

    private static void add(List<Doodad> list, int x, int y, String part, int turn) {
        DoodadDef def = TileRegistry.installed().doodad("ship-bay." + part);
        if (def == null) throw new IllegalStateException("Missing ship-bay part: " + part);
        list.add(new Doodad(x, y, def, turn));
    }

    /** Every elbow pose and repeated pipe/grate seams, seen through the same collector. */
    private static MapResult connections() {
        NavigationGrid grid = new NavigationGrid(12, 10);
        CellTopology topology = new CellTopology(12, 10);
        List<Doodad> tiles = new ArrayList<>();
        for (int y = 0; y < 10; y++) for (int x = 0; x < 12; x++) {
            grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, GroundKind.INDOOR);
            topology.setSurface(x, y, topology.surfaceIndex("ship-bay.deck-surface"));
        }
        // Rectangular loop: canonical elbow is west/south in image coordinates.
        for (int x = 3; x <= 8; x++) { add(tiles,x,7,"pipe-straight",0); add(tiles,x,3,"pipe-straight",0); }
        for (int y = 4; y <= 6; y++) { add(tiles,2,y,"pipe-straight",1); add(tiles,9,y,"pipe-straight",1); }
        add(tiles,2,7,"pipe-elbow",1); add(tiles,9,7,"pipe-elbow",0);
        add(tiles,2,3,"pipe-elbow",2); add(tiles,9,3,"pipe-elbow",3);
        for (int x = 1; x < 11; x++) add(tiles,x,1,"grate-run",0);
        for (int x = 4; x < 8; x++) for (int y = 4; y < 7; y++) add(tiles,x,y,"grate-field",0);
        return new MapResult(grid,topology,0,0,11,9,List.of(),tiles);
    }

    private static BufferedImage comparison(BufferedImage before, BufferedImage after) {
        BufferedImage sheet = new BufferedImage(before.getWidth()*2+16, before.getHeight()+54, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x111B23)); g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); g.setColor(new Color(0xD4E1E6));
        g.drawString("CURRENT FITTING / existing materials",16,30);
        g.drawString("SHIPBOARD STUDY / same fixtures and circulation",before.getWidth()+32,30);
        g.drawImage(before,0,54,null); g.drawImage(after,before.getWidth()+16,54,null); g.dispose();
        return sheet;
    }

    private record StudyRoom(RoomShape shape, int originX, int originY, RoomPose pose,
                             RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom { }
}
