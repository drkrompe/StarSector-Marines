package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.StationGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawn placement for the concentric station — defender at the core, marine at
 * the outer ring, so the assault crosses the whole onion from the breach inward.
 * The defender holds {@link StationGraph#coreRoom()} (its last stand); the marine
 * starts in an outermost-ring room (the breach point). Depth-from-entry then runs
 * radially from the marine's outer room to the besieged core.
 *
 * <p>Binds {@link BspKeys#MARINE_SPAWN} / {@link BspKeys#DEFENDER_SPAWN}.
 */
public final class CoreSpawnStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        StationGraph graph = ctx.get(BspKeys.STATION_GRAPH);
        if (graph == null || graph.roomCount() == 0) {
            int[] c = { ctx.width / 2, ctx.height / 2 };
            ctx.put(BspKeys.MARINE_SPAWN, c);
            ctx.put(BspKeys.DEFENDER_SPAWN, c.clone());
            return;
        }

        StationGraph.Room core = graph.coreRoom() >= 0 ? graph.room(graph.coreRoom()) : graph.room(0);

        // Marine breaches from a random outermost-ring room — varies the assault
        // direction (and the radial depth gradient) per seed.
        int maxRing = 0;
        for (StationGraph.Room r : graph.rooms()) maxRing = Math.max(maxRing, graph.ringOf(r.id));
        List<StationGraph.Room> outer = new ArrayList<>();
        for (StationGraph.Room r : graph.rooms()) {
            if (graph.ringOf(r.id) == maxRing) outer.add(r);
        }
        StationGraph.Room breach = outer.isEmpty() ? core : outer.get(ctx.rng.nextInt(outer.size()));

        ctx.put(BspKeys.MARINE_SPAWN, spawnCell(ctx.grid, breach));
        ctx.put(BspKeys.DEFENDER_SPAWN, spawnCell(ctx.grid, core));
    }

    /** The room center if walkable, else the first walkable cell in its rect, else the center anyway. */
    private static int[] spawnCell(NavigationGrid grid, StationGraph.Room room) {
        if (grid.isWalkable(room.centerX, room.centerY)) {
            return new int[]{ room.centerX, room.centerY };
        }
        for (int y = room.top; y <= room.bottom; y++) {
            for (int x = room.left; x <= room.right; x++) {
                if (grid.isWalkable(x, y)) return new int[]{ x, y };
            }
        }
        return new int[]{ room.centerX, room.centerY };
    }
}
