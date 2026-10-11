package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipBayDecking;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/** Ship-only material defaults; authored surfaces, fixtures and physical topology remain authoritative. */
public final class ShipMaterialStage implements GenStage {
    @Override
    public void run(GenContext ctx) {
        CellTopology topology = ctx.topology;
        for (int y = 0; y < ctx.height; y++) {
            for (int x = 0; x < ctx.width; x++) {
                if (topology.getGroundKind(x, y) == GroundKind.VOID) continue;
                String existing = topology.getSurfaceId(x, y);
                if (existing != null && !existing.equals("urban.wall")) continue;
                String material = topology.isWall(x, y) ? "ship.neutral-bulkhead"
                        : ctx.grid.isDoorway(x, y) ? "ship.passage-deck"
                        : floor(topology.getRoomPurpose(x, y));
                topology.setSurface(x, y, topology.surfaceIndex(material));
            }
        }
        ShipBayDecking.apply(ctx);
    }

    private static String floor(RoomPurpose purpose) {
        if (purpose == null) return "ship.passage-deck";
        return switch (purpose) {
            case BARRACKS, CREW_QUARTERS, MESS_HALL, CREW_LOUNGE, CONFERENCE_ROOM,
                    BRIDGE, PATIENT_WARD, WASHROOM -> "ship.crew-deck";
            case VEHICLE_BAY, HANGAR, STOCKROOM, LOADING_BAY, PRODUCTION_FLOOR,
                    PARTS_CAGE, ENGINE_ROOM -> "auto-shop.concrete";
            default -> "ship.passage-deck";
        };
    }
}
