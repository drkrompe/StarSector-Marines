package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ReservedFacility;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seal the hull's reserves that have not been opened: compartments kept for
 * refits she has not had.
 *
 * <p>A reserve is laid and furnished like any room, so its walls, doors and
 * passage are the ones its refit will open onto, and an opened one is simply
 * left as furnished: its doors work and its fixtures publish their work. Every
 * other reserve is stripped of what the fill put inside, left with salvage
 * stacked on the bare deck, closed at every entrance with a pressure hatch and
 * marked damaged, so it supplies no work, no berth and no facility. Either way
 * the fill ran first and identically, so opening a reserve moves nothing on
 * the deck. Run after the other condition stages, because it adds to their
 * damage rather than replacing it.
 */
public final class ReservedFacilityStage implements GenStage {

    private final Set<ReservedFacility> open;

    /** @param open the reserves that have been opened; every other one is sealed */
    public ReservedFacilityStage(Set<ReservedFacility> open) {
        this.open = Set.copyOf(open);
    }

    /** Every reserve sealed, as the hull is converted. */
    public ReservedFacilityStage() {
        this(Set.of());
    }

    @Override public void run(GenContext ctx) {
        DeckGraph graph = ctx.get(ShipKeys.DECK_GRAPH);
        List<DeckGraph.Compartment> reserves = graph.compartments().stream()
                .filter(room -> graph.reserved(room.id()) && !open.contains(graph.reserve(room.id()))).toList();
        if (reserves.isEmpty()) return;
        var damaged = new HashSet<Integer>();
        var faults = new HashSet<Integer>();
        for (var room : graph.compartments()) {
            if (graph.damaged(room.id()) || reserves.contains(room)) damaged.add(room.id());
            if (graph.emergencyLighting(room.id()) && !graph.damaged(room.id())) faults.add(room.id());
        }
        for (var room : reserves) {
            ctx.doodads.removeIf(d -> room.contains(d.cellX, d.cellY));
            RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
            for (int x = 2; x < room.width() - 1; x += 3)
                floor.pave(x, room.depth() / 2, "doodad.industrial-scrap-pile");
            SealedShipHatches.seal(ctx, room);
        }
        // A berth inside a reserve would be work on closed floor; rebind the
        // survivors so every task still names its own berth.
        var original = List.copyOf(ctx.gantries);
        ctx.gantries.removeIf(g -> reserves.stream().anyMatch(room -> room.contains(g.centerX, g.centerY)));
        var tasks = new ArrayList<FixtureTask>();
        for (var task : ctx.fixtureTasks) {
            if (reserves.stream().anyMatch(room -> room.contains(task.cellX(), task.cellY())
                    || room.contains(task.fixtureX(), task.fixtureY()))) continue;
            int berth = task.berth() < 0 ? task.berth() : ctx.gantries.indexOf(original.get(task.berth()));
            if (task.berth() >= 0 && berth < 0) continue;
            tasks.add(new FixtureTask(task.cellX(), task.cellY(), task.affordance(),
                    task.fixtureX(), task.fixtureY(), berth, task.inService()));
        }
        ctx.fixtureTasks.clear();
        ctx.fixtureTasks.addAll(tasks);
        ctx.put(ShipKeys.DECK_GRAPH, graph.withCondition(graph.apertures(), damaged, faults));
    }
}
