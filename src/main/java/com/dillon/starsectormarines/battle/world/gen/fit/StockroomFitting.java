package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * A stockroom as racked bays flanking a central gangway.
 *
 * <p>A stockroom is not a hold. Cargo in a hold is stacked wherever it lands;
 * a stockroom is <b>racked and inventoried</b> — long shelving with stock in
 * known places, worked from a gangway wide enough to walk a part down, and
 * checked periodically against what is supposed to be there rather than only
 * ever handled. Ranking one crate-stack down both bulkheads at a pitch — the
 * arrangement this room used to carry — gave the stacking and none of the
 * rest: no known place for anything in particular, and nowhere a tally was
 * ever taken.
 *
 * <p>Racking is laid as {@link #BAY_WIDTH}-wide <b>bays</b> against both long
 * bulkheads, each bay a rack run with its ready stock at the inward end. A
 * worked gap of {@link #BAY_GAP} cells separates one bay from the next, which
 * is what lets every rack face be reached square-on rather than only from the
 * gangway it backs onto — the same module a boat bay's cargo runs and a
 * vehicle bay's stores gaps already use, because a warehouse aisle and a
 * ship's own stores are the same shape.
 *
 * <p>Most bays publish {@link Affordance#STOW} at the stock they carry — the
 * handling that moves a part between two known places, which is the whole
 * reason a stockroom has more than one rack. A deterministic few carry
 * {@link Affordance#READOUT} instead, a hand terminal mounted on the rack
 * where a running tally is taken and the shelf checked against the manifest;
 * and a deterministic few carry {@link Affordance#REPAIR}, a consignment that
 * arrived damaged and has not been dealt with. None of it is drawn from a live
 * random: the same stockroom built twice carries the same tally points and the
 * same damaged crate, which is what lets somebody walk back to either.
 *
 * <p>A room too shallow for a rack on each bulkhead and a gangway between them
 * still gets one rack, not none — the same "one rank still beats nothing" rule
 * every other ranked compartment on the ship keeps, and the reason the small
 * utility variant of this room comes out furnished rather than bare.
 */
public final class StockroomFitting implements RoomFitting {

    /** Cells a rack run occupies along a bulkhead — the art's own authored length. */
    private static final int BAY_WIDTH = 3;
    /** Cells between one bay and the next, joining its rack face to the gangway. */
    private static final int BAY_GAP = 2;
    /** Cells the rack and its ready stock reach inward from the bulkhead, at most. */
    private static final int RACK_DEPTH = 2;
    /** Least width kept clear down the middle for the gangway. */
    private static final int MIN_GANGWAY = 2;

    private static final int DEFECT_STRIDE = 5;
    private static final int DEFECT_PHASE = 3;
    private static final int TALLY_STRIDE = 4;
    private static final int TALLY_PHASE = 1;

    /** The rack run: a long shelving unit, authored the width of one bay. */
    private static final String RACK_RUN = "doodad.stores-rack-run";
    /** What a bay's rack reads as until the art above lands. */
    private static final String RACK_FALLBACK = "doodad.industrial-crate-stack";

    /** Ready stock at the inward end of a rack, alternated so no two bays match. */
    private static final String[] STOCK = {
            "doodad.industrial-crate-stack",
            "doodad.industrial-drum-cluster",
            "doodad.industrial-pallet-stack" };

    /** The hand terminal a running tally is taken from, mounted on a rack. */
    private static final String RACK_TERMINAL = "doodad.industrial-control-console";

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.STOCKROOM;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();

        // How far a rack reaches, read off the art and swapped for the room's
        // pose rather than assumed. A rack run is drawn lying along the deck's
        // own x axis, so in a quarter-turned compartment it stands out from the
        // bulkhead instead of along it - which is a perfectly good warehouse,
        // and nothing like the one the numbers below would otherwise lay. Held
        // to a fixed three-along-by-one-deep the placement was quietly refused
        // for reaching through the gangway, every bay, in every turned
        // stockroom on the ship: the room still generated, still passed its own
        // connectivity check, and shipped with the stock and no shelving.
        int[] rack = span(floor, RACK_RUN);
        boolean rackAvailable = TileRegistry.installed().doodad(RACK_RUN) != null;
        int bayAlong = rackAvailable ? Math.max(1, rack[0]) : BAY_WIDTH;
        int bayDeep = rackAvailable ? Math.max(1, rack[1]) : 1;

        // The gangway is reserved before a single rack goes down. A room too
        // shallow to spare it after two full-depth bands gives up rack depth
        // rather than the gangway - a stockroom nobody can walk the length of
        // is not a stockroom, whatever is racked in it.
        int want = bayDeep + 1;
        int depth = Math.max(1, Math.min(want, (across - MIN_GANGWAY) / 2));
        if (2 * depth >= across) depth = Math.max(1, (across - 1) / 2);
        if (depth < 1) return;
        // A band too shallow for the art itself takes the per-cell stand-in
        // rather than a refused placement, which is the same "one rank still
        // beats nothing" rule this room already keeps for a narrow hull.
        if (depth < bayDeep) {
            rackAvailable = false;
            bayAlong = BAY_WIDTH;
            bayDeep = 1;
        }

        int gangwayFrom = depth;
        int gangway = Math.max(0, across - 2 * depth);
        reserve(floor, 0, gangwayFrom, along, gangway);
        for (Doorway door : floor.localDoors()) {
            stubFromDoor(floor, door, gangwayFrom, gangway, along, across);
        }

        int ordinal = 0;
        ordinal = layBulkhead(floor, 0, depth, true, along, rackAvailable,
                bayAlong, bayDeep, ordinal);
        ordinal = layBulkhead(floor, across - depth, depth, false, along, rackAvailable,
                bayAlong, bayDeep, ordinal);

        if (ordinal == 0) {
            // Neither run laid a single bay - too narrow along its own length
            // for even one. A rack against the near bulkhead beats an empty
            // gangway either side of nothing.
            layFallbackRack(floor, along, across);
        }
    }

    /**
     * One bulkhead's worth of bays: a rack run at the wall, its stock reaching
     * one cell inward where the room is deep enough to spare it.
     *
     * @return the ordinal the other bulkhead's run continues from, so the two
     *     runs draw from the same deterministic sequence rather than repeating
     *     each other cell for cell
     */
    private int layBulkhead(RoomFloor floor, int wallRow, int depth, boolean inwardIsHigher,
                            int along, boolean rackAvailable,
                            int bayAlong, int bayDeep, int ordinal) {
        // The stock sits one cell further in than the rack reaches, so a rack
        // standing out from the bulkhead is still worked from its inward end
        // rather than from inside itself.
        int rackRow = inwardIsHigher ? wallRow : wallRow + depth - bayDeep;
        int stockRow = depth > bayDeep
                ? (inwardIsHigher ? wallRow + bayDeep : wallRow + depth - bayDeep - 1)
                : rackRow;
        int cursor = 0;
        while (cursor + bayAlong <= along) {
            layBay(floor, cursor, rackRow, stockRow, depth > bayDeep, rackAvailable,
                    bayAlong, ordinal);
            ordinal++;
            cursor += bayAlong + BAY_GAP;
        }
        return ordinal;
    }

    /**
     * One rack and its ready stock, or its defect, or its running tally.
     *
     * <p>A room deep enough to spare a separate stock row publishes the work
     * there, at both ends of the rack rather than one cell in its middle — a
     * three-cell run is long enough to hold more than one known place, and a
     * rack worked from a single centred point undercounts exactly what makes
     * it a rack rather than a crate. A room too shallow for that row has
     * nowhere else to put the work, so the rack carries one job directly
     * instead.
     */
    private void layBay(RoomFloor floor, int along, int wallRow, int stockRow,
                        boolean hasStockRow, boolean rackAvailable,
                        int bayAlong, int ordinal) {
        if (!hasStockRow) {
            placeRack(floor, along, wallRow, rackAvailable, work(ordinal));
            return;
        }
        placeRack(floor, along, wallRow, rackAvailable, null);
        layStockPoint(floor, along, stockRow, ordinal * 2);
        if (bayAlong > 1) {
            layStockPoint(floor, along + bayAlong - 1, stockRow, ordinal * 2 + 1);
        }
    }

    /** One known place in the rack's stock row: its handling, its tally, or its defect. */
    private void layStockPoint(RoomFloor floor, int along, int stockRow, int slot) {
        if (slot % TALLY_STRIDE == TALLY_PHASE) {
            place(floor, along, stockRow, RACK_TERMINAL, Affordance.READOUT);
            return;
        }
        String stock = STOCK[Math.floorMod(slot, STOCK.length)];
        place(floor, along, stockRow, stock, work(slot));
    }

    /** The job a slot's ordinal resolves to: mostly handling, a deterministic tally, a deterministic defect. */
    private static Affordance work(int slot) {
        if (slot % TALLY_STRIDE == TALLY_PHASE) return Affordance.READOUT;
        if (slot % DEFECT_STRIDE == DEFECT_PHASE) return Affordance.REPAIR;
        return Affordance.STOW;
    }

    /**
     * The rack itself, as the real art or as a stand-in until it lands, and the
     * work at it where this call is the bay's only fixture.
     *
     * <p>The real art is one piece {@link #BAY_WIDTH} cells long, authored lying
     * along the deck's own x axis the way every multi-cell doodad here is —
     * doodads are placed unrotated, so a quarter-turned room needs the
     * canonical span swapped before it is carried into local coordinates, or
     * the rack reaches the wrong way and the next bay's rack is laid through
     * it. See {@link #span}. The fallback has no such span to get wrong: it is
     * three separate single-cell placements, and the work goes on the middle
     * one so a shallow room still ends up with exactly one point per bay.
     */
    private void placeRack(RoomFloor floor, int along, int wallRow,
                           boolean rackAvailable, Affordance affordance) {
        if (rackAvailable) {
            int[] span = span(floor, RACK_RUN);
            placeSpanning(floor, along, wallRow, span[0], span[1], RACK_RUN, affordance);
            return;
        }
        int mid = along + BAY_WIDTH / 2;
        for (int i = 0; i < BAY_WIDTH; i++) {
            int cell = along + i;
            if (affordance != null && cell == mid) {
                place(floor, cell, wallRow, RACK_FALLBACK, affordance);
            } else {
                place(floor, cell, wallRow, RACK_FALLBACK);
            }
        }
    }

    /**
     * How much of the canonical frame one piece of art covers, as
     * {@code {along, across}}, swapped for a quarter-turned room. See
     * {@link MachinerySpaceFitting}'s use of the same technique.
     */
    private static int[] span(RoomFloor floor, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        int x = def == null ? 1 : def.footprintCellsX;
        int y = def == null ? 1 : def.footprintCellsY;
        return floor.pose().upright() ? new int[]{ x, y } : new int[]{ y, x };
    }

    /** One fixture covering a canonical rectangle, carried across whole so a turn cannot split it. */
    private void placeSpanning(RoomFloor floor, int along, int across,
                               int spanAlong, int spanAcross, String id, Affordance affordance) {
        int[] rect = floor.toLocalRect(along, across, spanAlong, spanAcross);
        if (affordance == null) {
            floor.place(id, rect[0], rect[1]);
        } else {
            floor.place(id, rect[0], rect[1], affordance);
        }
    }

    /** The one-rack fallback for a room too narrow along its length for a bay. */
    private void layFallbackRack(RoomFloor floor, int along, int across) {
        boolean rackAvailable = TileRegistry.installed().doodad(RACK_RUN) != null;
        String id = rackAvailable ? RACK_RUN : RACK_FALLBACK;
        int[] cell = floor.toLocal(0, 0);
        floor.place(id, cell[0], cell[1], Affordance.STOW);
    }

    /**
     * Join a door to the gangway, where the gangway does not already reach it.
     *
     * <p>The same stub every ranked compartment on the ship carries: a door on
     * an end bulkhead already opens onto the gangway, and this is for the one
     * that does not, costing a single column of one bay's worth of rack.
     */
    private void stubFromDoor(RoomFloor floor, Doorway door,
                              int gangwayFrom, int gangway, int along, int across) {
        int[] canonical = floor.toCanonical(door.x(), door.y());
        int doorAcross = canonical[1];
        if (doorAcross >= gangwayFrom && doorAcross < gangwayFrom + gangway) return;
        int from = Math.min(Math.max(0, doorAcross), gangwayFrom);
        int to = Math.max(Math.min(across - 1, doorAcross), gangwayFrom + gangway - 1);
        int column = Math.max(0, Math.min(along - 1, canonical[0]));
        reserve(floor, column, from, 1, to - from + 1);
    }

    private void place(RoomFloor floor, int along, int across, String id) {
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1]);
    }

    private void place(RoomFloor floor, int along, int across, String id, Affordance affordance) {
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1], affordance);
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
