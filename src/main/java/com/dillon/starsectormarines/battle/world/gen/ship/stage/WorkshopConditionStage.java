package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.HashSet;
import java.util.ArrayList;

/** Overlay after furnishing: neither placement nor any other room's random stream changes. */
public final class WorkshopConditionStage implements GenStage {
    private final WorkshopState state;
    public WorkshopConditionStage(WorkshopState state) { this.state = state; }
    @Override public void run(GenContext ctx) {
        if (state == WorkshopState.NONE) return;
        DeckGraph graph = ctx.get(ShipKeys.DECK_GRAPH);
        var room = graph.largest(RoomPurpose.PARTS_CAGE);
        if (room == null) throw new IllegalStateException("Starting hull owes a machine shop");
        ctx.fixtureTasks.removeIf(task -> room.contains(task.cellX(), task.cellY())
                && (!state.operational() || state != WorkshopState.EQUIPPED && task.affordance() == Affordance.FABRICATE));
        var tiles = TileRegistry.installed();
        // Empty picking racks tell the player why restored machines still need tooling.
        if (state != WorkshopState.EQUIPPED)
            ctx.doodads.removeIf(d -> room.contains(d.cellX, d.cellY) && d.definitionId() != null
                    && (d.definitionId().equals("machine-shop.bar-stock")
                    || d.definitionId().equals("machine-shop.bearings")
                    || d.definitionId().equals("machine-shop.electrical")));
        var damage = new ArrayList<Doodad>();
        for (Doodad d : ctx.doodads) {
            if (!room.contains(d.cellX, d.cellY) || d.definitionId() == null) continue;
            if (!state.operational() && (d.definitionId().equals("machine-shop.lathe")
                    || d.definitionId().equals("machine-shop.mill"))) {
                var broken = tiles.doodad("doodad.industrial-machine-tool-damaged");
                // The original machine still occupies its full physical footprint.
                // Loose broken assemblies lie across it rather than shrinking the machine to a one-cell sprite.
                if (broken != null) damage.add(new Doodad(d.cellX + d.footprintCellsX / 2,
                        d.cellY + d.footprintCellsY / 2, broken, d.quarterTurns, d.mirrored));
            }
        }
        ctx.doodads.addAll(damage);
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        if (state == WorkshopState.BURIED) {
            // Uneven accumulations around the machinery, leaving no evenly spaced decorative row.
            for (int[] spot : new int[][]{{2, 3}, {3, 4}, {4, 3}, {7, 3}, {8, 4}, {9, 3}}) {
                int[] local = floor.toLocal(spot[0], spot[1]);
                floor.pave(local[0], local[1], "doodad.industrial-scrap-pile");
            }
            for (var door : room.doors()) {
                ctx.grid.setDoorway(door.x(), door.y(), false);
                ctx.grid.setWalkable(door.x(), door.y(), false);
                ctx.grid.setSeeThrough(door.x(), door.y(), false);
                ctx.topology.setWall(door.x(), door.y(), true);
                ctx.topology.setSurface(door.x(), door.y(), 0);
            }
        }
        if (state == WorkshopState.EQUIPPED) {
            var bench = ctx.doodads.stream().filter(d -> room.contains(d.cellX, d.cellY)
                    && "machine-shop.assembly-bench".equals(d.definitionId())).findFirst();
            var tools = tiles.doodad("armory-workshop.bench-tools");
            if (bench.isPresent() && tools != null) {
                Doodad b = bench.get();
                ctx.doodads.add(new Doodad(b.cellX, b.cellY, tools, b.quarterTurns, b.mirrored));
            }
        }
        var damaged = new HashSet<Integer>();
        graph.compartments().stream().filter(r -> graph.damaged(r.id())).forEach(r -> damaged.add(r.id()));
        if (!state.operational()) damaged.add(room.id());
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(graph.compartments(), graph.unplaced(),
                graph.apertures(), graph.boats(), damaged));
    }
}
