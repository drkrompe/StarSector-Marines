package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipWearStageTest {
    @Test void scarsPersistAcrossRepairAndDoNotChangeNavigationOrTheFittingRandomStream() {
        List<String> baseline = null;
        for (var condition : BoatBayState.values()) {
            var room = new DeckGraph.Compartment(1, RoomShape.rectangle(12, 10), 2, 2,
                    RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, RoomPurpose.HANGAR, List.of());
            var ctx = new GenContext(new NavigationGrid(16, 14), new CellTopology(16, 14), new Random(7), 16, 14, 7);
            ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(List.of(room), List.of()));
            for (int y = 0; y < 14; y++) for (int x = 0; x < 16; x++) ctx.topology.setGroundKind(x, y, GroundKind.INDOOR);
            ctx.grid.setWalkableFloor(3, 3);
            ctx.topology.setWall(1, 1, true);
            new ShipWearStage(condition).run(ctx);
            assertEquals(new Random(7).nextLong(), ctx.rng.nextLong());
            assertTrue(ctx.grid.isWalkable(3, 3));
            assertTrue(ctx.topology.isWall(1, 1));
            if (condition == BoatBayState.NONE) { assertTrue(ctx.doodads.isEmpty()); continue; }
            assertFalse(ctx.doodads.isEmpty());
            var scars = ctx.doodads.stream().map(d -> d.definitionId() + ":" + d.cellX + ":" + d.cellY + ":" + d.mirrored).toList();
            if (baseline == null) baseline = scars;
            else assertEquals(baseline, scars);
            for (var d : ctx.doodads) assertTrue(d.definitionId().startsWith("ship-wear."));
        }
    }
}
