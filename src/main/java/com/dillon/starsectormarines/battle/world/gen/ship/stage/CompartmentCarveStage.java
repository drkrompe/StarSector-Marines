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
 * signal spaces forward, volume amidships, power and fabrication aft. That zone
 * affinity is the point of the longitudinal model — a compartment finds its
 * place from the axis rather than from a coordinate table.
 *
 * <p>Compartments <b>follow the hull</b> rather than squaring off inside it. A
 * rectangle has to shrink to the narrowest frame it spans, which throws away
 * every cell where the plating flares and leaves a rind of dead structure along
 * the whole ship. Carving per frame instead means the room reaches the hull
 * everywhere, and the deck's port/starboard asymmetry actually shows up in the
 * rooms rather than being flattened away.
 */
public final class CompartmentCarveStage implements GenStage {

    /** Fewest outboard rows a frame needs before a compartment may claim it. */
    private static final int MIN_DEPTH = 3;
    /** Fewest frames a compartment may span. */
    private static final int MIN_WIDTH = 5;
    /** A span at least this long may be divided into two rooms instead of one. */
    private static final int SPLIT_THRESHOLD = MIN_WIDTH * 2 + 3;
    /** Walkable width of a door. */
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
                for (int[] span : usableSpans(profile, bay[0], bay[1], side)) {
                    for (int[] room : divide(ctx, span[0], span[1])) {
                        carved.add(carve(ctx, profile, room[0], room[1], side, carved.size()));
                    }
                }
            }
        }
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(carved, corridors));
    }

    /** Column spans between corridors, leaving a wall column against each corridor and end cap. */
    static List<int[]> bays(int frames, int[] corridors) {
        List<int[]> result = new ArrayList<>();
        int cursor = 1;
        for (int corridor : corridors) {
            if (corridor - 2 >= cursor) result.add(new int[]{ cursor, corridor - 2 });
            cursor = corridor + TransverseCorridorStage.CORRIDOR_WIDTH + 1;
        }
        if (frames - 2 >= cursor) result.add(new int[]{ cursor, frames - 2 });
        return result;
    }

    /**
     * The runs of frames inside a bay where the hull is deep enough to hold a
     * room. Rejecting a whole bay because its bow-most frames pinch out would
     * surrender the entire taper to dead structure; this reclaims the part that
     * is actually usable and leaves only the genuinely too-narrow tip solid.
     */
    private static List<int[]> usableSpans(DeckProfile profile, int left, int right, DeckSide side) {
        List<int[]> spans = new ArrayList<>();
        int runStart = -1;
        for (int x = left; x <= right + 1; x++) {
            boolean deepEnough = x <= right && depthAt(profile, x, side) >= MIN_DEPTH;
            if (deepEnough && runStart < 0) {
                runStart = x;
            } else if (!deepEnough && runStart >= 0) {
                if (x - runStart >= MIN_WIDTH) spans.add(new int[]{ runStart, x - 1 });
                runStart = -1;
            }
        }
        return spans;
    }

    /** Rows available outboard of the spine bulkhead at one frame. */
    private static int depthAt(DeckProfile profile, int x, DeckSide side) {
        return side == DeckSide.PORT
                ? profile.spineTop() - 1 - profile.top(x)
                : profile.bottom(x) - profile.spineBottom() - 1;
    }

    /**
     * Split a long span into two rooms with a dividing wall, or leave it whole.
     * Uniform bays read as an office block rather than a ship, and this is the
     * cheapest source of variety that does not disturb the circulation.
     */
    private static List<int[]> divide(GenContext ctx, int left, int right) {
        List<int[]> rooms = new ArrayList<>();
        if (right - left + 1 < SPLIT_THRESHOLD || ctx.rng.nextInt(3) == 0) {
            rooms.add(new int[]{ left, right });
            return rooms;
        }
        int slack = (right - left + 1) - (MIN_WIDTH * 2 + 1);
        int cut = left + MIN_WIDTH + ctx.rng.nextInt(slack + 1);
        rooms.add(new int[]{ left, cut - 1 });
        rooms.add(new int[]{ cut + 1, right });
        return rooms;
    }

    private DeckGraph.Compartment carve(GenContext ctx, DeckProfile profile,
                                        int left, int right, DeckSide side, int id) {
        DeckZone zone = profile.zone((left + right) / 2);
        RoomPurpose purpose = purposeFor(ctx, zone);
        CellTopology topology = ctx.topology;

        int boundsTop = Integer.MAX_VALUE;
        int boundsBottom = Integer.MIN_VALUE;
        for (int x = left; x <= right; x++) {
            int from = side == DeckSide.PORT ? profile.top(x) : profile.spineBottom() + 2;
            int to = side == DeckSide.PORT ? profile.spineTop() - 2 : profile.bottom(x);
            boundsTop = Math.min(boundsTop, from);
            boundsBottom = Math.max(boundsBottom, to);
            for (int y = from; y <= to; y++) {
                ctx.grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setRoomPurpose(x, y, purpose);
            }
        }

        openOntoSpine(ctx, profile, left, right, side);
        openOntoCorridor(ctx, profile, right, side);

        return new DeckGraph.Compartment(id, left, boundsTop, right, boundsBottom, side, zone, purpose);
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
     * Placed against the spine, where the compartment is guaranteed to be deep.
     */
    private void openOntoCorridor(GenContext ctx, DeckProfile profile, int right, DeckSide side) {
        int wallColumn = right + 1;
        if (wallColumn >= ctx.width) return;
        int inboard = side == DeckSide.PORT ? profile.spineTop() - 2 : profile.spineBottom() + 2;
        int step = side == DeckSide.PORT ? -1 : 1;
        for (int i = 0; i < DOOR_WIDTH; i++) {
            carveDoorCell(ctx, wallColumn, inboard + i * step);
        }
    }

    /**
     * Convert one solid cell into a doorway. Cells that are already walkable are
     * left alone, so a door appears only in the wall gap it crosses.
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
