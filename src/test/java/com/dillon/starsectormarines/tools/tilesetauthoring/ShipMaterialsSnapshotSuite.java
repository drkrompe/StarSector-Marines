package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
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
                RoomPurpose.MESS_HALL, RoomPurpose.ARMORY)) {
            DeckGraph.Compartment room = generator.getLastDeckGraph().compartments().stream()
                    .filter(c -> c.purpose() == purpose).findFirst().orElse(null);
            if (room == null) continue;
            // Eight cells beyond the room include its passage and adjoining compartment boundaries.
            images.add(new SnapshotArtifact("ship-materials-" + purpose.name().toLowerCase() + ".png",
                    renderer.renderView(deck, 42, room.originX() + room.width() / 2f,
                            room.originY() + room.depth() / 2f,
                            room.width() + 8, room.depth() + 8, 32)));
        }
        return images;
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
