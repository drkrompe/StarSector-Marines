package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.BerthingFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.SickBayFitting;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
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

/**
 * The ground installation's quarters, which no shipboard suite shows: a sick-bay ward and a barracks berth fitted by
 * their ground fittings in all four quarter turns, one 1920x1080 board each. Real-length (three-cell) ward beds and
 * residential racks, with each head against its own bulkhead.
 */
public final class GroundQuartersSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ground-quarters"; }
    @Override public String label() { return "Ground sick-bay ward and barracks berth, four turns"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> images = new ArrayList<>();
        images.add(new SnapshotArtifact("ground-quarters-sickbay.png", board(renderer,
                "GROUND SICK BAY / WARD AND CLINIC / FOUR TURNS", RoomPurpose.PATIENT_WARD, 14, 10, 24,
                new SickBayFitting())));
        images.add(new SnapshotArtifact("ground-quarters-barracks.png", board(renderer,
                "GROUND BARRACKS / TWO RANKS OF RACKS / FOUR TURNS", RoomPurpose.BARRACKS, 8, 8, 32,
                new BerthingFitting(RoomPurpose.BARRACKS, "doodad.chest-1"))));
        return images;
    }

    private static BufferedImage board(HeadlessBattleMapRenderer renderer, String title, RoomPurpose purpose,
                                       int along, int across, int cellPx, RoomFitting fitting) {
        BufferedImage board = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = board.createGraphics();
        g.setColor(new Color(18, 26, 32)); g.fillRect(0, 0, 1920, 1080);
        g.setColor(new Color(225, 230, 225)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
        g.drawString(title, 30, 48);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        for (int turn = 0; turn < 4; turn++) {
            BufferedImage room = renderer.render(room(purpose, along, across, turn, fitting), 42, cellPx);
            int x = 30 + (turn % 2) * 945, y = 80 + (turn / 2) * 500;
            g.drawString("turn " + turn, x, y + 18);
            g.drawImage(room, x, y + 28, null);
        }
        g.dispose();
        return board;
    }

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}

    private static MapResult room(RoomPurpose purpose, int along, int across, int turn, RoomFitting fitting) {
        RoomPose pose = new RoomPose(turn, false);
        RoomShape shape = RoomShape.rectangle(along, across).posed(pose);
        int width = shape.width() + 4, height = shape.height() + 4;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            topology.setSurface(x, y, topology.surfaceIndex("floors.stone"));
        }
        for (int[] cell : shape.wall()) {
            int x = cell[0] + 2, y = cell[1] + 2;
            grid.setWalkable(x, y, false); topology.setWall(x, y, true);
            topology.setSurface(x, y, topology.surfaceIndex("colony.masonry-wall"));
        }
        // The ward is entered at its clinic end on the spine; the berth from its near bulkhead.
        int[] door = purpose == RoomPurpose.PATIENT_WARD ? pose.map(-1, across / 2, along, across)
                : pose.map(along / 4, -1, along, across);
        int dx = door[0] + 2, dy = door[1] + 2;
        grid.setWalkableFloor(dx, dy); grid.setDoorway(dx, dy, true); topology.setWall(dx, dy, false);
        GenContext ctx = new GenContext(grid, topology, new Random(42), width, height, 42);
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, 2, 2, pose, purpose, List.of(new Doorway(dx, dy))),
                RoomFit.STANDARD);
        fitting.fit(floor);
        if (!floor.circulationSurvives() || floor.dropUnreachableWork() != 0)
            throw new IllegalStateException(purpose + " loses circulation or work at turn " + turn);
        floor.seal();
        WallMasks.stampUnclaimed(topology);
        return new MapResult(grid, topology, dx, dy, dx, dy, List.of(), ctx.doodads);
    }
}
