package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipBayDecking;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckTrim;
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
                // Hull boundary walls can retain VOID ground beneath their structural
                // shell, but solid cells out in space are VOID walls too and must stay
                // unpainted, or they cover the hull art drawn behind the deck.
                if (topology.getGroundKind(x, y) == GroundKind.VOID
                        && !(topology.isWall(x, y) && touchesDeck(topology, x, y, ctx))) continue;
                String existing = topology.getSurfaceId(x, y);
                if (existing != null && !existing.equals("urban.wall")) continue;
                String material = topology.isWall(x, y) ? "ship.neutral-bulkhead"
                        : ctx.grid.isDoorway(x, y) ? "ship-floor-trim.passage"
                        : floor(topology.getRoomPurpose(x, y));
                topology.setSurface(x, y, topology.surfaceIndex(material));
            }
        }
        ShipBayDecking.apply(ctx);
        ShipDeckTrim.apply(ctx);
    }

    private static boolean touchesDeck(CellTopology topology, int x, int y, GenContext ctx) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= ctx.width || ny >= ctx.height) continue;
                if (topology.getGroundKind(nx, ny) != GroundKind.VOID) return true;
            }
        }
        return false;
    }

    private static String floor(RoomPurpose purpose) {
        if (purpose == null) return "ship-floor-trim.passage";
        return switch (purpose) {
            case CORRIDOR -> "ship-floor-trim.passage";
            case CREW_LOUNGE -> "ship-floor-trim.lounge";
            case PATIENT_WARD -> "ship-floor-trim.clinical";
            case ENGINE_ROOM, PRODUCTION_FLOOR -> "ship-floor-trim.engineering";
            case BARRACKS, CREW_QUARTERS, MESS_HALL, CONFERENCE_ROOM,
                    BRIDGE, WASHROOM, GYMNASIUM -> "ship.crew-deck";
            case VEHICLE_BAY, HANGAR, STOCKROOM, LOADING_BAY, PARTS_CAGE -> "auto-shop.concrete";
            default -> "ship.passage-deck";
        };
    }
}
