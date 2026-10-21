package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.ReservedFacility;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipRestoration;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A reserve the hull has opened is left as furnished; every other one is stripped and sealed. */
class ReservedFacilityStageTest {
    private static final DeckGraph.Compartment GYM = room(0, 2, 12, 8, RoomPurpose.GYMNASIUM);
    private static final DeckGraph.Compartment BRIG = room(1, 20, 6, 6, RoomPurpose.BRIG);
    private static final DeckGraph.Compartment MESS = room(2, 32, 6, 6, RoomPurpose.MESS_HALL);

    @Test void everyReserveIsSealedUntilTheHullOpensIt() {
        assertEquals(Set.of(), ShipRestoration.WRECK.openReserves());
        assertEquals(Set.of(), ShipRestoration.NONE.openReserves());
        var opened = ShipRestoration.WRECK.withOpenReserves(EnumSet.of(ReservedFacility.GYMNASIUM));
        assertTrue(opened.opens(ReservedFacility.GYMNASIUM));
        assertFalse(opened.opens(ReservedFacility.BRIG));
        assertFalse(opened.opens(null));
        assertEquals(opened, ShipRestoration.WRECK.withOpenReserves(Set.of(ReservedFacility.GYMNASIUM)),
                "a saved set reads back as the same hull, so a cached deck is reused");

        DeckGraph graph = graph();
        assertEquals(ReservedFacility.GYMNASIUM, graph.reserve(GYM.id()));
        assertEquals(ReservedFacility.BRIG, graph.reserve(BRIG.id()));
        assertNull(graph.reserve(MESS.id()), "a programmed room is no reserve");
        assertTrue(ShipConditionStage.sealed(graph, GYM, ShipRestoration.NONE));
        assertTrue(ShipConditionStage.sealed(graph, GYM, ShipRestoration.WRECK));
        assertFalse(ShipConditionStage.sealed(graph, GYM, opened));
        assertTrue(ShipConditionStage.sealed(graph, BRIG, opened));
        assertFalse(ShipConditionStage.sealed(graph, MESS, ShipRestoration.NONE.withOpenReserves(
                EnumSet.allOf(ReservedFacility.class))), "opening reserves never touches the program");
    }

    @Test void anOpenedReserveKeepsItsWorkAndHatchesWhileTheRestAreSealed() {
        var ctx = new GenContext(new NavigationGrid(48, 16), new CellTopology(48, 16), new Random(7), 48, 16, 7);
        DeckGraph graph = graph();
        ctx.put(ShipKeys.DECK_GRAPH, graph);
        for (var room : List.of(GYM, BRIG)) {
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                ctx.grid.setWalkableFloor(x, y);
            Doorway door = room.doors().get(0);
            ctx.grid.setWalkableFloor(door.x(), door.y());
            ctx.fixtureTasks.add(FixtureTask.at(room.left() + 1, room.top() + 1, Affordance.EXERCISE,
                    room.left() + 1, room.top() + 2));
        }

        new ReservedFacilityStage(EnumSet.of(ReservedFacility.GYMNASIUM)).run(ctx);

        DeckGraph after = ctx.get(ShipKeys.DECK_GRAPH);
        assertFalse(after.damaged(GYM.id()), "the opened gymnasium is in service");
        assertTrue(after.damaged(BRIG.id()), "the brig is still sealed");
        assertTrue(after.reserved(GYM.id()) && after.reserved(BRIG.id()), "opening a reserve keeps it on the plan");
        assertEquals(1, ctx.fixtureTasks.size());
        assertTrue(GYM.contains(ctx.fixtureTasks.get(0).cellX(), ctx.fixtureTasks.get(0).cellY()));
        Doorway gymDoor = GYM.doors().get(0), brigDoor = BRIG.doors().get(0);
        assertTrue(ctx.grid.isWalkable(gymDoor.x(), gymDoor.y()), "the opened room's hatch works");
        assertFalse(ctx.grid.isWalkable(brigDoor.x(), brigDoor.y()), "the sealed room's hatch is closed");
    }

    private static DeckGraph graph() {
        return new DeckGraph(List.of(GYM, BRIG, MESS), List.of(), List.of(), null, Set.of(), Set.of(),
                Set.of(GYM.id(), BRIG.id()));
    }

    private static DeckGraph.Compartment room(int id, int x, int w, int h, RoomPurpose purpose) {
        return new DeckGraph.Compartment(id, RoomShape.rectangle(w, h), x, 2, new RoomPose(0, false),
                DeckSide.PORT, DeckZone.AFT, purpose, List.of(new Doorway(x + 1, 1)));
    }
}
