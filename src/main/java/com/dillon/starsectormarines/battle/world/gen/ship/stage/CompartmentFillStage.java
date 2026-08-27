package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.CompartmentFloor;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFittings;

/**
 * Step 5 (ship) — furnish the compartments.
 *
 * <p>Placement produced rooms with the right purposes, sizes and doors, and
 * nothing inside them. This runs the authored fitting for each compartment's
 * purpose at the deck's refit level, so a berth compartment comes out with bunks
 * ranked either side of a working aisle rather than as a labelled rectangle.
 *
 * <p>The deck carries one refit level for now, which is what a hull straight out
 * of the yard or straight off a prize crew looks like. Per-compartment refit —
 * the thing the upgrade chain moves, one room at a time — reads from the same
 * dispatch the moment there is an owner deciding it.
 *
 * <p>A compartment whose fill would seal it is left bare instead. A room that
 * cannot be entered is a worse outcome than an empty one, and it is the failure
 * a furnished room is most likely to produce.
 */
public final class CompartmentFillStage implements GenStage {

    private final RoomFit fit;

    public CompartmentFillStage(RoomFit fit) {
        this.fit = fit;
    }

    @Override
    public void run(GenContext ctx) {
        DeckGraph graph = ctx.get(ShipKeys.DECK_GRAPH);
        if (graph == null) {
            throw new IllegalStateException("CompartmentFillStage requires a published deck graph");
        }
        for (DeckGraph.Compartment compartment : graph.compartments()) {
            RoomFitting fitting = RoomFittings.forPurpose(compartment.purpose());
            if (fitting == null) continue;

            int doodads = ctx.doodads.size();
            int berths = ctx.gantries.size();
            int work = ctx.fixtureTasks.size();
            CompartmentFloor floor = new CompartmentFloor(ctx, compartment, fit);
            fitting.fit(floor);
            if (!floor.circulationSurvives()) {
                // Roll the room back to bare deck rather than ship one that
                // cannot be walked through — and roll back everything the
                // fitting published, not merely what it can be seen to have
                // placed. A rolled-back bay that kept its berths was a bay
                // offering to service machines on empty painted floor, and it
                // stayed that way precisely because nothing about it looked
                // wrong from the outside.
                ctx.doodads.subList(doodads, ctx.doodads.size()).clear();
                ctx.gantries.subList(berths, ctx.gantries.size()).clear();
                ctx.fixtureTasks.subList(work, ctx.fixtureTasks.size()).clear();
            }
        }
    }
}
