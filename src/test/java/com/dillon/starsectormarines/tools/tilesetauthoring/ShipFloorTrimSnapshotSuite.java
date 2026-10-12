package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.CrewLoungeFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipEngineeringFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.SickBayFitting;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.ShipMaterialStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Connected construction fixture using production room fittings, material stage and battle collectors. */
public final class ShipFloorTrimSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ship-floor-trim"; }
    @Override public String label() { return "Connected passage, lounge, sickbay and engineering flooring"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> images = new ArrayList<>();
        MapResult after = slice(true), before = slice(false);
        for (int scale : new int[]{24, 40}) {
            images.add(new SnapshotArtifact("connected-after-" + scale + "px.png", renderer.render(after, 42, scale)));
            images.add(new SnapshotArtifact("connected-before-" + scale + "px.png", renderer.render(before, 42, scale)));
        }
        images.add(new SnapshotArtifact("lounge-sickbay-thresholds-48px.png",
                renderer.renderView(after, 42, 18.5f, 9, 35, 16, 48)));
        return images;
    }

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}

    private static MapResult slice(boolean materials) {
        int w = 62, h = 30;
        GenContext ctx = new GenContext(new NavigationGrid(w, h), new CellTopology(w, h), new Random(42), w, h, 42);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            ctx.topology.setGroundKind(x, y, GroundKind.VOID);
        for (int y = 1; y <= 5; y++) for (int x = 1; x <= 60; x++) {
            if (y == 1 || y == 5 || x == 1 || x == 60) wall(ctx, x, y);
            else floor(ctx, x, y, RoomPurpose.CORRIDOR);
        }
        room(ctx, 3, 6, 16, 12, 7, RoomPurpose.CREW_LOUNGE);
        room(ctx, 20, 6, 14, 10, 1, RoomPurpose.PATIENT_WARD);
        room(ctx, 35, 6, 24, 22, 3, RoomPurpose.ENGINE_ROOM);
        new ShipMaterialStage().run(ctx);
        WallMasks.stampUnclaimed(ctx.topology);
        if (!materials) {
            ctx.doodads.removeIf(d -> d.definitionId() != null && d.definitionId().startsWith("ship-floor-trim."));
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                String id = ctx.topology.getSurfaceId(x, y);
                if (id == null || !id.startsWith("ship-floor-trim.")) continue;
                RoomPurpose purpose = ctx.topology.getRoomPurpose(x, y);
                String old = purpose == RoomPurpose.ENGINE_ROOM ? "auto-shop.concrete"
                        : purpose == null || purpose == RoomPurpose.CORRIDOR ? "ship.passage-deck" : "ship.crew-deck";
                ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex(old));
            }
        }
        return new MapResult(ctx.grid, ctx.topology, 3, 3, 58, 3, List.of(), ctx.doodads);
    }

    private static void room(GenContext ctx, int ox, int oy, int w, int h, int hatch, RoomPurpose purpose) {
        RoomShape shape = RoomShape.rectangle(w, h);
        for (int[] cell : shape.wall()) wall(ctx, ox + cell[0], oy + cell[1]);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) floor(ctx, ox+x, oy+y, purpose);
        List<Doorway> doors = new ArrayList<>();
        for (int x = hatch; x < hatch + 2; x++) {
            floor(ctx, ox+x, oy-1, null);
            ctx.grid.setDoorway(ox+x, oy-1, true);
            doors.add(new Doorway(ox+x, oy-1));
        }
        RoomFloor floor = new RoomFloor(ctx, new Room(shape, ox, oy, RoomPose.CANONICAL, purpose, doors), RoomFit.STANDARD);
        switch (purpose) {
            case CREW_LOUNGE -> new CrewLoungeFitting().fit(floor);
            case PATIENT_WARD -> SickBayFitting.shipboard().fit(floor);
            case ENGINE_ROOM -> new ShipEngineeringFitting(purpose).fit(floor);
            default -> throw new IllegalArgumentException("Unexpected study room " + purpose);
        }
        if (!floor.circulationSurvives() || floor.dropUnreachableWork() != 0)
            throw new IllegalStateException("Connected study lost circulation or work: " + purpose);
        floor.seal();
    }
    private static void floor(GenContext ctx, int x, int y, RoomPurpose purpose) {
        ctx.grid.setWalkableFloor(x, y);
        ctx.topology.setWall(x, y, false);
        ctx.topology.setGroundKind(x, y, GroundKind.INDOOR);
        if (purpose != null) ctx.topology.setRoomPurpose(x, y, purpose);
    }
    private static void wall(GenContext ctx, int x, int y) {
        ctx.grid.setWalkable(x, y, false);
        ctx.topology.setWall(x, y, true);
    }
}
