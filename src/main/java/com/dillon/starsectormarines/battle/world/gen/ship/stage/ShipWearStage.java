package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Persistent history on the restoration hull, independent of its current repairs and fitting RNG. */
public final class ShipWearStage implements GenStage {
    private final boolean enabled;
    public ShipWearStage(BoatBayState state) { enabled = state != BoatBayState.NONE; }

    @Override public void run(GenContext ctx) {
        if (!enabled) return;
        var graph = ctx.get(ShipKeys.DECK_GRAPH);
        var rng = new Random(ctx.seed ^ 0x57454152L);
        List<Doodad> wear = new ArrayList<>();
        boolean[] rooms = new boolean[ctx.width * ctx.height];
        for (var room : graph.compartments()) {
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                if (room.contains(x, y)) rooms[y * ctx.width + x] = true;
            // This room already has authored breach, soot and a permanent pressure patch.
            if (room == graph.largest(RoomPurpose.PARTS_CAGE)) continue;
            int scuffX = room.centreX() - 1 + rng.nextInt(3) - 1;
            int scuffY = room.centreY() - 1 + rng.nextInt(3) - 1;
            place(ctx, room, "scuffs", scuffX, scuffY, rng.nextBoolean(), wear);
            if (room.purpose() == RoomPurpose.HANGAR || room.purpose() == RoomPurpose.VEHICLE_BAY
                    || room.purpose() == RoomPurpose.STOCKROOM || room.purpose() == RoomPurpose.PRODUCTION_FLOOR) {
                place(ctx, room, "paint", room.left() + 1, room.top() + 1, rng.nextBoolean(), wear);
                place(ctx, room, "patch", room.right() - 2, room.bottom() - 2, rng.nextBoolean(), wear);
                place(ctx, room, "scorch", room.left() + 1, room.bottom() - 2, rng.nextBoolean(), wear);
            }
        }
        // Sparse cargo-wheel/boot marks in actual passages; never over a bulkhead or an aperture.
        for (int y = 1; y < ctx.height - 2; y++) for (int x = 1; x < ctx.width - 3; x++) {
            if (Math.floorMod(x * 73856093L ^ y * 19349663L ^ ctx.seed, 83) != 0 || rooms[y * ctx.width + x]) continue;
            boolean passage = true;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 3; dx++)
                passage &= !rooms[(y + dy) * ctx.width + x + dx];
            if (passage) place(ctx, null, "scuffs", x, y, rng.nextBoolean(), wear);
        }
        ctx.doodads.addAll(0, wear);
    }

    private static void place(GenContext ctx, DeckGraph.Compartment room, String name, int x, int y,
                              boolean mirrored, List<Doodad> wear) {
        var def = TileRegistry.installed().doodad("ship-wear." + name);
        if (def == null) throw new IllegalStateException("Missing ship wear art: " + name);
        for (int dy = 0; dy < def.footprintCellsY; dy++) for (int dx = 0; dx < def.footprintCellsX; dx++) {
            int cx = x + dx, cy = y + dy;
            if (cx < 0 || cy < 0 || cx >= ctx.width || cy >= ctx.height
                    || room != null && !room.contains(cx, cy)
                    || ctx.topology.isWall(cx, cy) || ctx.topology.getGroundKind(cx, cy) == GroundKind.VOID) return;
        }
        wear.add(new Doodad(x, y, def, 0, mirrored));
    }
}
