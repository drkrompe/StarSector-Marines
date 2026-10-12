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
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        // Full-size replacement sprites keep the original machine anchors, pose and collision.
        if (!state.operational()) {
            ctx.doodads.replaceAll(d -> {
                if (!room.contains(d.cellX, d.cellY)) return d;
                String replacement = "machine-shop.lathe".equals(d.definitionId()) ? "lathe"
                        : "machine-shop.mill".equals(d.definitionId()) ? "mill" : null;
                return replacement == null ? d : new Doodad(d.cellX, d.cellY,
                        tiles.doodad("workshop-damage." + replacement), d.quarterTurns, d.mirrored);
            });
            // Soot sits beneath the equipment; cleanup removes it when the machines are repaired.
            overlay(ctx, room, floor, 0, 0, "scorch", true, false);
            overlay(ctx, room, floor, 7, 5, "scorch", true, true);
        }
        // The welded pressure patch remains as a scar even after the room is fully equipped.
        overlay(ctx, room, floor, 4, 3, state == WorkshopState.BURIED ? "breach" : "patch", true, false);
        if (state == WorkshopState.BURIED) {
            overlay(ctx, room, floor, 0, 3, "collapse", false, false);
            overlay(ctx, room, floor, 8, 6, "collapse", false, true);
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

    /** Apply after floor/wall finalization: a decompressed floor is forbidden, not a solid bulkhead. */
    public static void restrictAccess(GenContext ctx, WorkshopState state) {
        if (state != WorkshopState.BURIED) return;
        var room = ctx.get(ShipKeys.DECK_GRAPH).largest(RoomPurpose.PARTS_CAGE);
        for (int y = room.top(); y <= room.bottom(); y++)
            for (int x = room.left(); x <= room.right(); x++)
                if (room.contains(x, y)) ctx.grid.setWalkable(x, y, false);
    }

    private static void overlay(GenContext ctx, DeckGraph.Compartment room, RoomFloor floor,
                                int x, int y, String name, boolean underneath, boolean flipped) {
        var def = TileRegistry.installed().doodad("workshop-damage." + name);
        if (def == null) throw new IllegalStateException("Missing workshop damage art: " + name);
        int[] rect = floor.toLocalRect(x, y, def.footprintCellsX, def.footprintCellsY);
        var d = new Doodad(room.left() + rect[0], room.top() + rect[1], def,
                room.pose().quarterTurns(), room.pose().mirrored() ^ flipped);
        if (underneath) ctx.doodads.add(0, d);
        else ctx.doodads.add(d);
    }
}
