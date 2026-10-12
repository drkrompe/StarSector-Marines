package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Condition is applied after furnishing, so repair never changes placement or its random stream. */
public final class ReservedBoatBayStage implements GenStage {
    private final BoatBayState state;
    public ReservedBoatBayStage(BoatBayState state) { this.state = state; }

    @Override public void run(GenContext ctx) {
        if (state == BoatBayState.NONE) return;
        DeckGraph graph = ctx.get(ShipKeys.DECK_GRAPH);
        List<DeckGraph.Compartment> bays = graph.compartments().stream()
                .filter(room -> room.purpose() == RoomPurpose.HANGAR)
                .sorted(Comparator.comparingInt(DeckGraph.Compartment::foreFrame)
                        .thenComparingInt(DeckGraph.Compartment::id)).toList();
        if (bays.size() != 2) throw new IllegalStateException("Restoration hull must reserve both bays");
        DeckGraph.Compartment second = bays.get(1);
        boolean damaged = state == BoatBayState.DAMAGED;
        // Campaign berth order and room paging both run fore to aft. Rebind work
        // indices with the berths, including the unchanged machine gantry.
        List<Gantry> original = List.copyOf(ctx.gantries);
        List<Gantry> ordered = new ArrayList<>(original);
        ordered.sort(Comparator.comparingInt((Gantry g) -> g.holds.ordinal())
                .thenComparingInt(g -> bays.stream().filter(b -> b.contains(g.centerX, g.centerY))
                        .mapToInt(b -> bays.indexOf(b)).findFirst().orElse(0)));
        if (damaged) ordered.removeIf(g -> g.holds == Gantry.Holds.BOAT && second.contains(g.centerX, g.centerY));
        ctx.gantries.clear();
        ctx.gantries.addAll(ordered);
        List<FixtureTask> work = new ArrayList<>();
        for (FixtureTask task : ctx.fixtureTasks) {
            if (damaged && second.contains(task.cellX(), task.cellY())) continue;
            int berth = task.berth() < 0 ? task.berth() : ordered.indexOf(original.get(task.berth()));
            if (task.berth() >= 0 && berth < 0) continue;
            work.add(new FixtureTask(task.cellX(), task.cellY(), task.affordance(),
                    task.fixtureX(), task.fixtureY(), berth, task.inService()));
        }
        ctx.fixtureTasks.clear();
        ctx.fixtureTasks.addAll(work);
        if (!damaged) return;
        RoomFloor floor = new RoomFloor(ctx, second, RoomFit.STANDARD);
        for (int x = 3; x < second.width() - 2; x += 3)
            floor.pave(x, second.depth() / 2, "doodad.industrial-scrap-pile");
        SealedShipHatches.seal(ctx, second);
        // The original launch opening remains a solid bulkhead until repaired.
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(graph.compartments(), graph.unplaced(),
                graph.apertures().stream().filter(a -> a.compartmentId() != second.id()).toList(),
                graph.boats(), Set.of(second.id())));
    }
}
