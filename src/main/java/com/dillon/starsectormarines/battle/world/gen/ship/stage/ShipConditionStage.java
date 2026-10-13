package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipRestoration;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** A damaged hull keeps its whole room plan. Only usable facilities and work change. */
public final class ShipConditionStage implements GenStage {
    private final ShipRestoration state;
    public ShipConditionStage(ShipRestoration state) { this.state = state; }

    /** Reserve the last two bunkrooms, always leaving at least three working compartments. */
    public static List<DeckGraph.Compartment> spareBillets(DeckGraph graph) {
        var rooms = graph.compartments().stream().filter(r -> r.purpose() == RoomPurpose.BARRACKS)
                .sorted(Comparator.comparingInt(DeckGraph.Compartment::foreFrame)
                        .thenComparingInt(DeckGraph.Compartment::id)).toList();
        return rooms.subList(Math.min(rooms.size(), Math.max(3, rooms.size() - 2)), rooms.size());
    }
    public static boolean sealed(DeckGraph graph, DeckGraph.Compartment room, ShipRestoration state) {
        return state.applies() && (room.purpose() == RoomPurpose.VEHICLE_BAY && !state.mechBay()
                || !state.billets() && spareBillets(graph).contains(room));
    }
    @Override public void run(GenContext ctx) {
        if (!state.applies()) return;
        DeckGraph graph = ctx.get(ShipKeys.DECK_GRAPH);
        var damaged = new HashSet<Integer>();
        var faults = new HashSet<Integer>();
        List<DeckGraph.Compartment> closed = new ArrayList<>();
        for (var room : graph.compartments()) {
            if (graph.damaged(room.id())) damaged.add(room.id());
            // Working essential rooms may still run on unreliable local distribution.
            if (!state.power() && room.purpose() != RoomPurpose.VEHICLE_BAY
                    && room.purpose() != RoomPurpose.HANGAR && room.purpose() != RoomPurpose.PARTS_CAGE
                    && (room.id() % 3 != 0 || room.purpose() == RoomPurpose.ENGINE_ROOM)) faults.add(room.id());
            if (!sealed(graph, room, state)) {
                if (room.purpose() == RoomPurpose.VEHICLE_BAY || spareBillets(graph).contains(room))
                    WorkshopConditionStage.overlay(ctx, room, new RoomFloor(ctx, room, RoomFit.STANDARD), room.purpose() == RoomPurpose.VEHICLE_BAY ? 5 : 1,
                            room.purpose() == RoomPurpose.VEHICLE_BAY ? 5 : 1, "patch", true, false);
                continue;
            }
            closed.add(room);
            damaged.add(room.id());
            RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
            // Broken furniture, torn pressure skin and fallen ribs remain inside their authored room.
            ctx.doodads.removeIf(d -> room.contains(d.cellX, d.cellY));
            WorkshopConditionStage.overlay(ctx, room, floor, 0, 0, "scorch", true, false);
            WorkshopConditionStage.overlay(ctx, room, floor, room.purpose() == RoomPurpose.VEHICLE_BAY ? 5 : 1,
                    room.purpose() == RoomPurpose.VEHICLE_BAY ? 5 : 1, "breach", true, false);
            WorkshopConditionStage.overlay(ctx, room, floor, 1, 2, "collapse", false, false);
            if (room.purpose() == RoomPurpose.VEHICLE_BAY) {
                WorkshopConditionStage.overlay(ctx, room, floor, 10, 1, "mill", false, true);
                WorkshopConditionStage.overlay(ctx, room, floor, 9, 1, "collapse", false, true);
            }
            SealedShipHatches.seal(ctx, room);
        }
        // Removing a machine berth must rebind boat crew task indices as well.
        var original = List.copyOf(ctx.gantries);
        ctx.gantries.removeIf(g -> closed.stream().anyMatch(room -> room.contains(g.centerX, g.centerY)));
        var tasks = new ArrayList<FixtureTask>();
        for (var task : ctx.fixtureTasks) {
            if (closed.stream().anyMatch(room -> room.contains(task.cellX(), task.cellY()))) continue;
            int berth = task.berth() < 0 ? task.berth() : ctx.gantries.indexOf(original.get(task.berth()));
            if (task.berth() >= 0 && berth < 0) continue;
            tasks.add(new FixtureTask(task.cellX(), task.cellY(), task.affordance(), task.fixtureX(), task.fixtureY(), berth, task.inService()));
        }
        ctx.fixtureTasks.clear(); ctx.fixtureTasks.addAll(tasks);
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(graph.compartments(), graph.unplaced(), graph.apertures(), graph.boats(), damaged, faults));
    }
    public static void restrictAccess(GenContext ctx, ShipRestoration state) {
        var graph = ctx.get(ShipKeys.DECK_GRAPH);
        for (var room : graph.compartments()) if (sealed(graph, room, state))
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                if (room.contains(x, y)) ctx.grid.setWalkable(x, y, false);
    }
}
