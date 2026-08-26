package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 4 (ship) — carve the compartments filling each bay, port and starboard,
 * and open them onto the deck's circulation.
 *
 * <p>A compartment takes its purpose from its {@link DeckZone}: command and
 * signal spaces forward, volume amidships, power and fabrication aft. That
 * zone affinity is the whole point of the longitudinal model — a compartment
 * finds its place from the axis rather than from a coordinate table.
 *
 * <p>A bay too shallow or too short to hold a usable room is left as solid
 * structure. The bow tapers to the spine and the hull plating wanders, so
 * without that floor the stage would otherwise carve one-cell slivers that
 * read as damage rather than rooms.
 */
public final class CompartmentCarveStage implements GenStage {

    /** Fewest outboard rows a compartment may have. Below this it is structure, not a room. */
    private static final int MIN_DEPTH = 3;
    /** Fewest frames a compartment may span. */
    private static final int MIN_WIDTH = 4;
    /** Walkable width of a door, matching the station carve idiom. */
    private static final int DOOR_WIDTH = 2;

    private static final RoomPurpose[] FORE_PURPOSES = {
            RoomPurpose.CONTROL_ROOM, RoomPurpose.SERVER_ROOM, RoomPurpose.CONFERENCE_ROOM };
    private static final RoomPurpose[] MIDSHIPS_PURPOSES = {
            RoomPurpose.VEHICLE_BAY, RoomPurpose.BARRACKS, RoomPurpose.ARMORY, RoomPurpose.STOCKROOM };
    private static final RoomPurpose[] AFT_PURPOSES = {
            RoomPurpose.PRODUCTION_FLOOR, RoomPurpose.PARTS_CAGE, RoomPurpose.CONTROL_ROOM };

    @Override
    public void run(GenContext ctx) {
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        int[] corridors = ctx.get(ShipKeys.CORRIDOR_FRAMES);
        if (profile == null || corridors == null) {
            throw new IllegalStateException(
                    "CompartmentCarveStage requires a deck profile and carved transverse corridors");
        }

        List<DeckGraph.Compartment> carved = new ArrayList<>();
        for (int[] bay : bays(profile.frames(), corridors)) {
            for (DeckSide side : DeckSide.values()) {
                DeckGraph.Compartment compartment =
                        carve(ctx, profile, bay[0], bay[1], side, carved.size());
                if (compartment != null) carved.add(compartment);
            }
        }
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(carved, corridors));
    }

    /**
     * The compartment-eligible column spans between corridors, leaving one wall
     * column against each corridor and against each end cap.
     */
    static List<int[]> bays(int frames, int[] corridors) {
        List<int[]> result = new ArrayList<>();
        int cursor = 1;
        for (int corridor : corridors) {
            result.add(new int[]{ cursor, corridor - 2 });
            cursor = corridor + TransverseCorridorStage.CORRIDOR_WIDTH + 1;
        }
        result.add(new int[]{ cursor, frames - 2 });
        return result;
    }

    private DeckGraph.Compartment carve(GenContext ctx, DeckProfile profile,
                                        int left, int right, DeckSide side, int id) {
        if (right - left + 1 < MIN_WIDTH) return null;

        int top;
        int bottom;
        if (side == DeckSide.PORT) {
            // The innermost plating across the span bounds the rect, so the
            // compartment never pokes outside the hull at its narrowest frame.
            int innermost = Integer.MIN_VALUE;
            for (int x = left; x <= right; x++) innermost = Math.max(innermost, profile.top(x));
            top = innermost;
            bottom = profile.spineTop() - 2;
        } else {
            int innermost = Integer.MAX_VALUE;
            for (int x = left; x <= right; x++) innermost = Math.min(innermost, profile.bottom(x));
            top = profile.spineBottom() + 2;
            bottom = innermost;
        }
        if (bottom - top + 1 < MIN_DEPTH) return null;

        DeckZone zone = profile.zone((left + right) / 2);
        RoomPurpose purpose = purposeFor(ctx, zone);

        CellTopology topology = ctx.topology;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                ctx.grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setRoomPurpose(x, y, purpose);
            }
        }

        openOntoSpine(ctx, profile, left, right, side);
        openOntoCorridor(ctx, top, bottom, right);

        return new DeckGraph.Compartment(id, left, top, right, bottom, side, zone, purpose);
    }

    /** Punch the compartment's door through the spine bulkhead, centred on its span. */
    private void openOntoSpine(GenContext ctx, DeckProfile profile, int left, int right, DeckSide side) {
        int bulkheadRow = side == DeckSide.PORT
                ? profile.spineTop() - 1
                : profile.spineBottom() + 1;
        int doorLeft = (left + right) / 2 - DOOR_WIDTH / 2;
        for (int x = doorLeft; x < doorLeft + DOOR_WIDTH; x++) {
            carveDoorCell(ctx, x, bulkheadRow);
        }
    }

    /**
     * Punch a second door through the wall against the next corridor aft, so the
     * deck carries loops instead of every room being a dead end off the spine.
     * Skipped for the stern-most bay, which has no corridor behind it.
     */
    private void openOntoCorridor(GenContext ctx, int top, int bottom, int right) {
        int wallColumn = right + 1;
        if (wallColumn >= ctx.width) return;
        int doorTop = (top + bottom) / 2 - DOOR_WIDTH / 2;
        for (int y = doorTop; y < doorTop + DOOR_WIDTH; y++) {
            if (y < top || y > bottom) continue;
            carveDoorCell(ctx, wallColumn, y);
        }
    }

    /**
     * Convert one solid cell into a doorway. Cells that are already walkable are
     * left alone, so a door appears only in the wall gap it crosses — the same
     * rule the station carve follows.
     */
    private static void carveDoorCell(GenContext ctx, int x, int y) {
        if (!ctx.grid.inBounds(x, y) || ctx.grid.isWalkable(x, y)) return;
        ctx.grid.setWalkableFloor(x, y);
        ctx.topology.setGroundKind(x, y, GroundKind.STRIPED);
        ctx.topology.setRoomPurpose(x, y, RoomPurpose.CORRIDOR);
    }

    private static RoomPurpose purposeFor(GenContext ctx, DeckZone zone) {
        RoomPurpose[] table = switch (zone) {
            case FORE -> FORE_PURPOSES;
            case MIDSHIPS -> MIDSHIPS_PURPOSES;
            case AFT -> AFT_PURPOSES;
        };
        return table[ctx.rng.nextInt(table.length)];
    }
}
