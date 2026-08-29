package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * The heads: a bank of basins against one bulkhead, a bank of stalls against
 * the other, and the walk-through kept clear between them.
 *
 * <p>{@link Affordance#WASH} is the most ordinary job on a ship — everybody
 * aboard goes several times a watch, never far from where they sleep — and a
 * single fixture standing in for the whole compartment gave the affordance one
 * point to be claimed at, which is a queue the size of the ship's complement.
 * This room is ranked the way a berth is (see {@link BerthingFitting}), not
 * swept clean the way a range is: fixtures against both long bulkheads, the
 * middle left for the traffic a washroom actually carries.
 *
 * <p>Basins and stalls are laid as {@link #BANK_WIDTH}-wide banks — the
 * authored length of one piece of art — so a run of three basins or three
 * stalls reads as fixed plumbing rather than as one prop repeated sideways.
 * Each position in a bank publishes its own {@link Affordance#WASH} rather
 * than the bank publishing one for the whole run, because a washroom with one
 * basin and one stall is a room where the queue <em>is</em> the room.
 *
 * <p>The real multi-cell art degrades to already-registered ship's furniture
 * where the tile registry does not yet carry {@link #BASIN_RUN} or
 * {@link #STALL_BANK} — the same guard {@link GymFitting} and
 * {@link StockroomFitting} keep, because a fitting that assumed art existed
 * would place nothing and ship the room bare rather than furnished worse. A
 * degraded washroom still publishes the same job count at the same cells; only
 * the art standing on them changes once the atlas catches up.
 */
public final class WashroomFitting implements RoomFitting {

    /** Cells the basin run reaches inward from its bulkhead. A shallow counter. */
    private static final int BASIN_DEPTH = 1;
    /** Cells the stall bank reaches inward from its bulkhead. An enclosed booth. */
    private static final int STALL_DEPTH = 2;
    /** Least cells kept clear between the two bulkheads. Two abreast, as everywhere else. */
    private static final int MIN_CLEAR = 2;
    /** Basins or stalls per authored run — the art's own length. */
    private static final int BANK_WIDTH = 3;

    /** Three basins in a row, mounted flush to the bulkhead. */
    private static final String BASIN_RUN = "doodad.ship-washbasin-run";
    /** What a basin position reads as until that art lands. */
    private static final String BASIN_FALLBACK = "doodad.box";
    /** Three enclosed stalls in a row, doors facing the walk-through. */
    private static final String STALL_BANK = "doodad.ship-head-stall-bank";
    /** What a stall reads as until that art lands. */
    private static final String STALL_FALLBACK = "doodad.chest-1";

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.WASHROOM;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        int lane = across - BASIN_DEPTH - STALL_DEPTH;
        if (lane < MIN_CLEAR) {
            // Too shallow for both banks and a walk-through two abreast: one
            // rank of basins beats none, the same "beats nothing" fallback
            // every ranked compartment on the ship keeps.
            layBasins(floor, along, 0);
            return;
        }

        int laneFrom = BASIN_DEPTH;
        int stallFrom = across - STALL_DEPTH;
        reserve(floor, 0, laneFrom, along, lane);
        for (Doorway door : floor.localDoors()) {
            stubFromDoor(floor, door, laneFrom, lane, along, across);
        }

        layBasins(floor, along, 0);
        layStalls(floor, along, stallFrom);
    }

    /**
     * A bank of basins against the near bulkhead, each publishing its own
     * {@link Affordance#WASH} from the lane immediately inboard of it.
     */
    private void layBasins(RoomFloor floor, int along, int row) {
        boolean available = fits(floor, BASIN_RUN, BASIN_DEPTH);
        int width = bankWidth(floor, BASIN_RUN, available);
        for (int start = 0; start + width <= along; start += width) {
            boolean bank = available && placeRun(floor, start, row, BASIN_RUN);
            for (int i = 0; i < width; i++) {
                boolean here = available ? bank : place(floor, start + i, row, BASIN_FALLBACK);
                if (!here) continue;
                int[] stand = floor.toLocal(start + i, row + BASIN_DEPTH);
                int[] fixture = floor.toLocal(start + i, row);
                floor.fixtureTask(stand[0], stand[1], Affordance.WASH, fixture[0], fixture[1]);
            }
        }
    }

    /**
     * A bank of stalls against the far bulkhead, each publishing its own
     * {@link Affordance#WASH} from the lane immediately in front of its door.
     */
    private void layStalls(RoomFloor floor, int along, int row) {
        boolean available = fits(floor, STALL_BANK, STALL_DEPTH);
        int width = bankWidth(floor, STALL_BANK, available);
        for (int start = 0; start + width <= along; start += width) {
            boolean bank = available && placeRun(floor, start, row, STALL_BANK);
            for (int i = 0; i < width; i++) {
                boolean here = available
                        ? bank
                        : fillColumn(floor, start + i, row, STALL_DEPTH, STALL_FALLBACK);
                if (!here) continue;
                int[] stand = floor.toLocal(start + i, row - 1);
                int[] fixture = floor.toLocal(start + i, row);
                floor.fixtureTask(stand[0], stand[1], Affordance.WASH, fixture[0], fixture[1]);
            }
        }
    }

    /**
     * Whether this piece of art can stand in a band of the given depth, in the
     * orientation this room actually ended up in.
     *
     * <p>Having the id is not enough. A run drawn three cells along the deck's
     * x axis stands three cells <em>deep</em> in a quarter-turned compartment,
     * and a band one cell deep simply refuses it — silently, every bank, in
     * every turned washroom on the ship. The room then generated with its
     * lane, its doors and nothing else in it, which is the one failure mode a
     * fallback exists to prevent and the one it was not being asked about.
     */
    private static boolean fits(RoomFloor floor, String id, int depth) {
        if (TileRegistry.installed().doodad(id) == null) return false;
        return span(floor, id)[1] <= depth;
    }

    /** Cells of the bulkhead one bank covers: the art's posed length, or the stand-in's. */
    private static int bankWidth(RoomFloor floor, String id, boolean available) {
        return available ? Math.max(1, span(floor, id)[0]) : BANK_WIDTH;
    }

    /**
     * The real art: one multi-cell run, pose-swapped so it survives a quarter
     * turn. See {@link MachinerySpaceFitting} and {@link StockroomFitting} for
     * the same technique — doodads are placed unrotated, so a piece authored
     * three cells along the canonical frame has to be carried into local
     * coordinates through the room's own pose before it is drawn.
     */
    private boolean placeRun(RoomFloor floor, int along, int across, String id) {
        int[] span = span(floor, id);
        int[] rect = floor.toLocalRect(along, across, span[0], span[1]);
        return floor.place(id, rect[0], rect[1]);
    }

    private static int[] span(RoomFloor floor, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        int x = def == null ? 1 : def.footprintCellsX;
        int y = def == null ? 1 : def.footprintCellsY;
        return floor.pose().upright() ? new int[]{ x, y } : new int[]{ y, x };
    }

    /**
     * Fallback furniture filling one stall's whole footprint, front cell
     * first, so a degraded washroom reads as a block of ship's furniture
     * rather than a single crate standing in for an enclosed booth.
     *
     * @return whether the front cell — the one the standing point references —
     *     went down
     */
    private boolean fillColumn(RoomFloor floor, int along, int across, int depth, String id) {
        boolean front = place(floor, along, across, id);
        for (int d = 1; d < depth; d++) place(floor, along, across + d, id);
        return front;
    }

    private boolean place(RoomFloor floor, int along, int across, String id) {
        int[] cell = floor.toLocal(along, across);
        return floor.place(id, cell[0], cell[1]);
    }

    /**
     * Join a door to the walk-through, where it does not already open onto it.
     *
     * <p>The same stub every ranked compartment on the ship carries: a door on
     * an end bulkhead already opens onto the lane, and this is for the one that
     * lands on a basin or a stall instead, costing a single column of one
     * fixture rather than the whole bank it would otherwise have to give up.
     */
    private void stubFromDoor(RoomFloor floor, Doorway door,
                              int laneFrom, int lane, int along, int across) {
        int[] canonical = floor.toCanonical(door.x(), door.y());
        int doorAcross = canonical[1];
        if (doorAcross >= laneFrom && doorAcross < laneFrom + lane) return;
        int from = Math.min(Math.max(0, doorAcross), laneFrom);
        int to = Math.max(Math.min(across - 1, doorAcross), laneFrom + lane - 1);
        int column = Math.max(0, Math.min(along - 1, canonical[0]));
        reserve(floor, column, from, 1, to - from + 1);
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
