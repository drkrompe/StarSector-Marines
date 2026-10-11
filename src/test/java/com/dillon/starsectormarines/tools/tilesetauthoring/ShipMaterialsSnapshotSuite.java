package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.ArmoryFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.BerthingFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.MessHallFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.SickBayFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.MainStoresFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.CrewLoungeFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipBridgeFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipEngineeringFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipMachineShopFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.BoatBayFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipLoadingBayFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipBriefingRoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipGymFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipWashroomFitting;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.gen.ship.VanillaHullSilhouettes;
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
import java.util.EnumSet;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

/** Material acceptance in the review bay, cardinal openings and a generated company hull. */
public final class ShipMaterialsSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ship-materials"; }
    @Override public String label() { return "Ship bulkheads, openings and quiet decks"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> images = new ArrayList<>();
        MapResult bay = ShipNeutralSnapshotSuite.room("ship.neutral-bulkhead");
        for (int y = 0; y <= 3; y++) for (int x = 0; x < 32; x++) {
            if (!bay.topology.isWall(x, y))
                bay.topology.setSurface(x, y, bay.topology.surfaceIndex("ship.passage-deck"));
        }
        images.add(new SnapshotArtifact("ship-materials-mech-lab.png",
                renderer.renderView(bay, 42, 16, 11.5f, 28, 21, 32)));
        images.add(new SnapshotArtifact("ship-materials-openings.png", renderer.render(openings(), 42, 48)));
        VanillaHullSilhouettes.Hull hull = new VanillaHullSilhouettes(context.starsectorCore()).read("valkyrie");
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = hull == null ? generator.generateDeck(160, 64, 42)
                : generator.generateDeck(DeckSizing.planFor(hull.hullClass(), hull.role(),
                        hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.silhouette().aspect()),
                        42, hull.silhouette());
        images.add(new SnapshotArtifact("ship-materials-generated-deck.png", renderer.render(deck, 42, 8)));
        for (RoomPurpose purpose : List.of(RoomPurpose.BARRACKS, RoomPurpose.VEHICLE_BAY,
                RoomPurpose.MESS_HALL, RoomPurpose.ARMORY, RoomPurpose.PATIENT_WARD, RoomPurpose.STOCKROOM, RoomPurpose.CREW_LOUNGE, RoomPurpose.BRIDGE,
                RoomPurpose.ENGINE_ROOM, RoomPurpose.PRODUCTION_FLOOR, RoomPurpose.PARTS_CAGE, RoomPurpose.HANGAR, RoomPurpose.LOADING_BAY, RoomPurpose.CONFERENCE_ROOM, RoomPurpose.GYMNASIUM, RoomPurpose.WASHROOM)) {
            DeckGraph.Compartment room = generator.getLastDeckGraph().compartments().stream()
                    .filter(c -> c.purpose() == purpose).findFirst().orElse(null);
            if (room == null) continue;
            if (purpose == RoomPurpose.ARMORY || purpose == RoomPurpose.BARRACKS || purpose == RoomPurpose.MESS_HALL
                    || purpose == RoomPurpose.PATIENT_WARD || purpose == RoomPurpose.STOCKROOM || purpose == RoomPurpose.CREW_LOUNGE || purpose == RoomPurpose.BRIDGE
                    || purpose == RoomPurpose.ENGINE_ROOM || purpose == RoomPurpose.PRODUCTION_FLOOR || purpose == RoomPurpose.PARTS_CAGE || purpose == RoomPurpose.HANGAR || purpose == RoomPurpose.LOADING_BAY || purpose == RoomPurpose.CONFERENCE_ROOM || purpose == RoomPurpose.GYMNASIUM || purpose == RoomPurpose.WASHROOM) {
                for (int scale : new int[]{24, 64}) images.add(new SnapshotArtifact(
                        "ship-materials-" + purpose.name().toLowerCase() + "-" + scale + "px.png",
                        renderer.renderView(deck, 42, room.originX() + room.width() / 2f,
                                room.originY() + room.depth() / 2f, room.width() + 4, room.depth() + 4, scale)));
            }
            if (purpose == RoomPurpose.VEHICLE_BAY) {
                images.add(new SnapshotArtifact("ship-materials-mech-lab-game-scale.png",
                        renderer.renderView(deck, 42, room.originX() + room.width() / 2f,
                                room.originY() + room.depth() / 2f, room.width() + 4, room.depth() + 4, 24)));
                int[] storage = room.pose().map(room.pose().posedWidth(room.width(), room.depth()) - 4,
                        8, room.pose().posedWidth(room.width(), room.depth()),
                        room.pose().posedHeight(room.width(), room.depth()));
                images.add(new SnapshotArtifact("ship-materials-mech-lab-storage.png",
                        renderer.renderView(deck, 42, room.originX() + storage[0] + 0.5f,
                                room.originY() + storage[1] + 0.5f, 18, 20, 48)));
            }
            // Eight cells beyond the room include its passage and adjoining compartment boundaries.
            images.add(new SnapshotArtifact("ship-materials-" + purpose.name().toLowerCase() + ".png",
                    renderer.renderView(deck, 42, room.originX() + room.width() / 2f,
                            room.originY() + room.depth() / 2f,
                            room.width() + 8, room.depth() + 8, 32)));
        }
        DeckGraph.Compartment hangar = generator.getLastDeckGraph().compartments().stream()
                .filter(c -> c.purpose() == RoomPurpose.HANGAR).findFirst().orElseThrow();
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, generator.getLastDeckGraph(), 42, null)) {
            for (int tick=0;tick<30;tick++) scene.simulation().advance(BattleSimulation.TICK_DT);
            HeadlessUiRenderer drain = new HeadlessUiRenderer(
                    HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            for (int scale : new int[]{24, 48}) images.add(new SnapshotArtifact(
                    "ship-materials-hangar-occupied-"+scale+"px.png",
                    drain.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(hangar, 2, scale),
                            EnumSet.of(RenderLayer.GROUND, RenderLayer.VEHICLES, RenderLayer.DOODADS,
                                    RenderLayer.UNITS, RenderLayer.SHUTTLES)),
                            (hangar.width()+4)*scale, (hangar.depth()+4)*scale)));
        }
        for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                "ship-materials-armory-pose-" + turn + ".png", renderer.render(roomPose(turn, RoomPurpose.ARMORY), 42, 40)));
        for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                "ship-materials-barracks-pose-" + turn + ".png", renderer.render(roomPose(turn, RoomPurpose.BARRACKS), 42, 48)));
        for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                "ship-materials-mess-pose-" + turn + ".png", renderer.render(roomPose(turn, RoomPurpose.MESS_HALL), 42, 32)));
        for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                "ship-materials-sickbay-pose-" + turn + ".png", renderer.render(roomPose(turn, RoomPurpose.PATIENT_WARD), 42, 40)));
        for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                "ship-materials-stores-pose-" + turn + ".png", renderer.render(roomPose(turn, RoomPurpose.STOCKROOM), 42, 40)));
        for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                "ship-materials-lounge-pose-" + turn + ".png", renderer.render(roomPose(turn, RoomPurpose.CREW_LOUNGE), 42, 40)));
        for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                "ship-materials-bridge-pose-" + turn + ".png", renderer.render(roomPose(turn, RoomPurpose.BRIDGE), 42, 48)));
        for (RoomPurpose purpose : List.of(RoomPurpose.ENGINE_ROOM, RoomPurpose.PRODUCTION_FLOOR, RoomPurpose.PARTS_CAGE, RoomPurpose.HANGAR, RoomPurpose.LOADING_BAY, RoomPurpose.CONFERENCE_ROOM, RoomPurpose.GYMNASIUM, RoomPurpose.WASHROOM))
            for (int turn = 0; turn < 4; turn++) images.add(new SnapshotArtifact(
                    "ship-materials-" + purpose.name().toLowerCase() + "-pose-" + turn + ".png",
                    renderer.render(roomPose(turn, purpose), 42, 32)));
        images.add(new SnapshotArtifact("ship-materials-gig-bay.png",
                renderer.render(roomPose(0, RoomPurpose.HANGAR, true), 42, 40)));
        return images;
    }

    private record FittingRoom(RoomShape shape, int originX, int originY, RoomPose pose,
                              RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}

    /** Small posed fitting evidence; the generated hull above remains production-layout acceptance. */
    private static MapResult roomPose(int turn, RoomPurpose purpose) {
        return roomPose(turn, purpose, false);
    }

    private static MapResult roomPose(int turn, RoomPurpose purpose, boolean gig) {
        RoomPose pose = new RoomPose(turn, false);
        boolean washroom = purpose == RoomPurpose.WASHROOM;
        boolean gym = purpose == RoomPurpose.GYMNASIUM;
        boolean briefing = purpose == RoomPurpose.CONFERENCE_ROOM;
        boolean loading = purpose == RoomPurpose.LOADING_BAY;
        boolean hangar = purpose == RoomPurpose.HANGAR;
        boolean shop = purpose == RoomPurpose.PARTS_CAGE;
        boolean drive = purpose == RoomPurpose.ENGINE_ROOM;
        boolean auxiliary = purpose == RoomPurpose.PRODUCTION_FLOOR;
        boolean engineering = drive || auxiliary;
        boolean bridge = purpose == RoomPurpose.BRIDGE;
        boolean berthing = purpose == RoomPurpose.BARRACKS;
        boolean mess = purpose == RoomPurpose.MESS_HALL;
        boolean sickbay = purpose == RoomPurpose.PATIENT_WARD;
        boolean stores = purpose == RoomPurpose.STOCKROOM;
        boolean lounge = purpose == RoomPurpose.CREW_LOUNGE;
        int along = washroom ? 6 : gym ? 14 : briefing ? 14 : loading ? 18 : hangar ? (gig ? 16 : 24) : shop ? 14 : drive ? 24 : auxiliary ? 20 : bridge ? 13 : lounge ? 16 : stores ? 18 : sickbay ? 14 : mess ? 22 : berthing ? 8 : 12;
        int across = washroom ? 5 : gym ? 10 : briefing ? 10 : loading ? 12 : hangar ? (gig ? 10 : 30) : shop ? 10 : drive ? 22 : auxiliary ? 14 : bridge ? 11 : lounge ? 12 : stores ? 12 : sickbay ? 10 : mess ? 12 : berthing ? 6 : 8;
        RoomShape shape = (bridge ? RoomRecipe.COMMAND.shape() : RoomShape.rectangle(along, across)).posed(pose);
        int width = shape.width() + 4, height = shape.height() + 4;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.INDOOR);
            topology.setSurface(x, y, topology.surfaceIndex((stores || engineering || shop || hangar || loading) ? "auto-shop.concrete" : (berthing || mess || sickbay || lounge || briefing || gym) ? "ship.crew-deck" : "ship.passage-deck"));
        }
        for (int[] cell : shape.wall()) {
            int x = cell[0] + 2, y = cell[1] + 2;
            grid.setWalkable(x, y, false); topology.setWall(x, y, true);
            topology.setSurface(x, y, topology.surfaceIndex("ship.neutral-bulkhead"));
        }
        int[] door = loading ? pose.map(-1,5,along,across) : bridge ? pose.map(6, -1, along, across) : lounge ? pose.map(7, -1, along, across) : stores ? pose.map(14, -1, along, across) : sickbay ? pose.map(-1, 4, along, across)
                : pose.map(mess ? along - 2 : 3, -1, along, across);
        int dx = door[0] + 2, dy = door[1] + 2;
        grid.setWalkableFloor(dx, dy); grid.setDoorway(dx, dy, true); topology.setWall(dx, dy, false);
        topology.setSurface(dx, dy, topology.surfaceIndex("ship.passage-deck"));
        GenContext ctx = new GenContext(grid, topology, new Random(42), width, height, 42);
        RoomFloor floor = new RoomFloor(ctx, new FittingRoom(shape, 2, 2, pose, purpose,
                List.of(new Doorway(dx, dy))), RoomFit.STANDARD);
        if (washroom) new ShipWashroomFitting().fit(floor);
        else if (gym) new ShipGymFitting().fit(floor);
        else if (briefing) new ShipBriefingRoomFitting().fit(floor);
        else if (loading) new ShipLoadingBayFitting().fit(floor);
        else if (hangar) BoatBayFitting.shipboard().fit(floor);
        else if (shop) new ShipMachineShopFitting().fit(floor);
        else if (engineering) new ShipEngineeringFitting(purpose).fit(floor);
        else if (bridge) new ShipBridgeFitting().fit(floor);
        else if (berthing) BerthingFitting.shipboard(purpose).fit(floor);
        else if (mess) MessHallFitting.shipboard().fit(floor);
        else if (sickbay) SickBayFitting.shipboard().fit(floor);
        else if (stores) new MainStoresFitting().fit(floor);
        else if (lounge) new CrewLoungeFitting().fit(floor);
        else ArmoryFitting.shipboard().fit(floor);
        if (!floor.circulationSurvives() || floor.dropUnreachableWork() != 0)
            throw new IllegalStateException(purpose + " pose loses circulation or work: " + turn);
        floor.seal(); WallMasks.stampUnclaimed(topology);
        if (hangar) for (var berth : ctx.gantries)
            if (!"shuttle-bay.landing-deck".equals(topology.getSurfaceId(berth.centerX, berth.centerY)))
                throw new IllegalStateException("Hangar preview lost landing surface");
        return new MapResult(grid, topology, dx, dy, dx, dy, List.of(), ctx.doodads);
    }

    private static MapResult openings() {
        NavigationGrid grid = new NavigationGrid(20, 14);
        CellTopology t = new CellTopology(20, 14);
        for (int y = 0; y < 14; y++) for (int x = 0; x < 20; x++) {
            grid.setWalkableFloor(x, y); t.setGroundKind(x, y, GroundKind.INDOOR);
            t.setSurface(x, y, t.surfaceIndex(y < 7 ? "ship.passage-deck" : "ship.crew-deck"));
        }
        for (int y = 2; y <= 11; y++) for (int x = 2; x <= 17; x++) {
            if (x != 2 && x != 17 && y != 2 && y != 11) continue;
            grid.setWalkable(x, y, false); t.setWall(x, y, true);
            t.setSurface(x, y, t.surfaceIndex("ship.neutral-bulkhead"));
        }
        for (int[] d : new int[][]{{6, 2}, {7, 2}, {11, 11}, {2, 5}, {17, 8}, {17, 9}}) {
            int x = d[0], y = d[1];
            grid.setWalkableFloor(x, y); grid.setDoorway(x, y, true); t.setWall(x, y, false);
            t.setSurface(x, y, t.surfaceIndex(y < 7 ? "ship.passage-deck" : "ship.crew-deck"));
        }
        for (int[] w : new int[][]{{12, 2}, {6, 11}, {2, 8}, {17, 5}}) {
            grid.setWalkableFloor(w[0], w[1]); t.setWall(w[0], w[1], false);
            t.setSurface(w[0], w[1], t.surfaceIndex("ship.passage-deck"));
        }
        WallMasks.stampUnclaimed(t);
        // Glass belongs to actual shared-edge barriers, with transparent floor behind it.
        grid.placeEdgeBarrier(12, 2, Direction.S, SharedEdgeBarrier.Kind.WINDOW);
        grid.placeEdgeBarrier(6, 11, Direction.N, SharedEdgeBarrier.Kind.WINDOW);
        grid.placeEdgeBarrier(2, 8, Direction.E, SharedEdgeBarrier.Kind.WINDOW);
        grid.placeEdgeBarrier(17, 5, Direction.W, SharedEdgeBarrier.Kind.WINDOW);
        return new MapResult(grid, t, 4, 4, 15, 9, List.of(), List.of());
    }
}
