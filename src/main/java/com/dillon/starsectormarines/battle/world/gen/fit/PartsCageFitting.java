package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * A parts cage as a counter behind a wire front, with the dense stock kept
 * back of it.
 *
 * <p>A parts cage is not a second stockroom. Its stock is small enough to
 * carry off a cell at a time, which is exactly why it is secured: a cage
 * front separates the <b>issue side</b>, where a marine stands and asks, from
 * the <b>stock side</b>, where the small-bin racking actually is, with one
 * gate in the front joining them. Ranking a shelf down both bulkheads at a
 * pitch — the arrangement this room used to carry — gives neither side of
 * that: no front to be secured behind, and no distinction at all between where
 * somebody draws from and where the stock is kept.
 *
 * <p>The gate falls out of the gangway rather than being cut separately: the
 * cage front is attempted the whole width of the room and simply cannot land
 * on the gangway, which was reserved first — the same trick a firing range's
 * beaten zone and a boat bay's clear middle already use to make later
 * furniture skip the cells it must not touch.
 *
 * <p>The stock side racks its bins {@link #BAND}-deep against both long
 * bulkheads, but only the row facing the gangway is worked — {@link
 * Affordance#STOW} at most of it, a deterministic few carrying {@link
 * Affordance#READOUT} where a bin doubles as a stock-take terminal, and a
 * deterministic few carrying {@link Affordance#REPAIR}. A row driven straight
 * back from that face to the bulkhead would have nowhere for anybody to stand
 * beside it, so it backs the front row as dense scenery instead — the same
 * choice a loading bay's working bands make for the same reason.
 */
public final class PartsCageFitting implements RoomFitting {

    /** Least width kept clear down the middle for the gangway. */
    private static final int GANGWAY = 2;
    /** Cells the stock racking reaches inward from each bulkhead, at most. */
    private static final int BAND = 3;
    /** Cells given to the issue side, nearest along=0. */
    private static final int ISSUE_DEPTH = 3;
    /** Cells between one worked bin and the next. Shoulder to shoulder, secured stock is dense. */
    private static final int BIN_WIDTH = 2;

    private static final int DEFECT_STRIDE = 6;
    private static final int DEFECT_PHASE = 4;
    private static final int TALLY_STRIDE = 5;
    private static final int TALLY_PHASE = 2;

    /** The wire front dividing issue side from stock side, one gate where the gangway crosses it. */
    private static final String CAGE_FRONT = "doodad.stores-cage-front";
    private static final String CAGE_FALLBACK = "doodad.industrial-fence-straight-v";

    /** Dense small-bin racking. */
    private static final String BIN_RACK = "doodad.stores-bin-rack";
    private static final String BIN_FALLBACK = "doodad.shelf-2";
    /** The bin's own stand-in for a stock-take terminal, where the tally is taken. */
    private static final String BIN_TERMINAL = "doodad.industrial-control-console";
    /** Backing stock behind the worked row — dense and deliberately not worked. */
    private static final String[] BACKING = { "doodad.shelf-3", "doodad.chest-1", "doodad.crate" };

    /** The issue counter: what is drawn is drawn here, not fetched from the cage by hand. */
    private static final String COUNTER = "doodad.shelf-1";
    private static final String COUNTER_MATE = "doodad.crate";

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.PARTS_CAGE;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();

        // Band and gangway are sized together, band flush against the gangway
        // on both sides, so nothing is left over between them. Sizing the
        // gangway to a fixed width and centring it independently of the band
        // is what leaves a strip of floor on each side that is neither -
        // reserved as circulation nor reachable as a worked row, and nothing
        // downstream says why it is there.
        int band = Math.max(1, Math.min(BAND, (across - GANGWAY) / 2));
        if (2 * band >= across) band = Math.max(1, (across - 1) / 2);
        int gangwayFrom = band;
        int gangway = Math.max(0, across - 2 * band);
        reserve(floor, 0, gangwayFrom, along, gangway);
        for (Doorway door : floor.localDoors()) {
            stubFromDoor(floor, door, gangwayFrom, gangway, along, across);
        }

        int issueDepth = Math.max(1, Math.min(ISSUE_DEPTH, along - 2));
        boolean cageAvailable = TileRegistry.installed().doodad(CAGE_FRONT) != null;
        layCageFront(floor, issueDepth, across, cageAvailable);
        // The counter goes down before the reservation below, so its own cells
        // are already claimed and the reservation only ever touches what is
        // still open. Everything the counter did not take is left as circulation
        // rather than as floor with nothing said about it - the issue side is a
        // place to stand and be served, not a second stockroom to furnish.
        layCounter(floor, issueDepth, gangwayFrom);
        reserve(floor, 0, 0, issueDepth, across);

        int ordinal = 0;
        ordinal = layStockBand(floor, issueDepth + 1, along, 0, band, ordinal);
        layStockBand(floor, issueDepth + 1, along, across - band, band, ordinal);
    }

    /**
     * The cage front, attempted the room's whole depth in tall segments. It
     * lands everywhere except the gangway, which was reserved first and is
     * never free — so the gate is exactly as wide as the gangway rather than a
     * width authored here to match it, and the two can never drift apart.
     */
    private void layCageFront(RoomFloor floor, int gate, int across, boolean cageAvailable) {
        if (!cageAvailable) {
            for (int row = 0; row < across; row++) place(floor, gate, row, CAGE_FALLBACK);
            return;
        }
        int[] span = span(floor, CAGE_FRONT);
        int covered = 0;
        for (int row = 0; row + span[1] <= across; row += span[1]) {
            placeSpanning(floor, gate, row, span[0], span[1], CAGE_FRONT, null);
            covered = row + span[1];
        }
        // The tail, where the segment height does not divide the room's depth
        // evenly: single-cell fence so the partition still reaches the far
        // bulkhead rather than leaving a second, unintended gap.
        for (int row = covered; row < across; row++) place(floor, gate, row, CAGE_FALLBACK);
    }

    /**
     * The counter, on the issue side of the gate. Parts are drawn here, across
     * the counter, rather than fetched by hand from the cage beyond it.
     */
    private void layCounter(RoomFloor floor, int gateAlong, int gangwayFrom) {
        int along = Math.max(0, gateAlong - 1);
        int row = Math.max(0, gangwayFrom - 1);
        place(floor, along, row, COUNTER_MATE, Affordance.STOW);
        if (along > 0) place(floor, along - 1, row, COUNTER);
    }

    /**
     * One stock-side band: a worked row of bins facing the gangway, backed by
     * dense stock nobody is published against.
     *
     * @return the ordinal the far bulkhead's band continues from
     */
    private int layStockBand(RoomFloor floor, int from, int along, int wallRow, int band, int ordinal) {
        boolean binAvailable = TileRegistry.installed().doodad(BIN_RACK) != null;
        int frontRow = wallRow == 0 ? band - 1 : wallRow;
        int width = binAvailable ? span(floor, BIN_RACK)[0] : BIN_WIDTH;
        for (int a = from; a + width <= along; a += width) {
            boolean tally = ordinal % TALLY_STRIDE == TALLY_PHASE;
            boolean defect = !tally && ordinal % DEFECT_STRIDE == DEFECT_PHASE;
            if (tally) {
                // Every cell of the slot is filled either way, the terminal
                // taking only the first: a slot that swapped its whole width
                // for one narrow terminal would leave the rest of it bare.
                place(floor, a, frontRow, BIN_TERMINAL, Affordance.READOUT);
                for (int i = 1; i < width; i++) place(floor, a + i, frontRow, BIN_FALLBACK);
            } else {
                placeBin(floor, a, frontRow, width, binAvailable,
                        defect ? Affordance.REPAIR : Affordance.STOW);
            }
            ordinal++;
        }
        if (band > 1) {
            int backFrom = wallRow == 0 ? 0 : wallRow + 1;
            int backTo = wallRow == 0 ? frontRow : wallRow + band;
            layBacking(floor, backFrom, backTo, from, along, ordinal);
        }
        return ordinal;
    }

    /**
     * One bin slot, as the real art or as a stand-in until it lands.
     *
     * <p>The fallback is not one cell standing in for the whole slot: it is the
     * slot's full width in single-cell shelving, or a fallback bin at a pitch
     * of two cells would leave every other cell of the row bare — dense stock
     * with half its face unfurnished, which is the uniform-low-density defect
     * this room exists to avoid, reintroduced by the very fallback meant to
     * keep it furnished until the real art lands.
     */
    private void placeBin(RoomFloor floor, int along, int row, int width,
                          boolean binAvailable, Affordance work) {
        if (binAvailable) {
            int[] span = span(floor, BIN_RACK);
            placeSpanning(floor, along, row, span[0], span[1], BIN_RACK, work);
            return;
        }
        int mid = along + width / 2;
        for (int i = 0; i < width; i++) {
            int cell = along + i;
            if (cell == mid) {
                place(floor, cell, row, BIN_FALLBACK, work);
            } else {
                place(floor, cell, row, BIN_FALLBACK);
            }
        }
    }

    /** Dense scenery filling whatever rows of a band are not the worked face. */
    private void layBacking(RoomFloor floor, int rowFrom, int rowTo, int from, int along, int seed) {
        int index = seed;
        for (int row = rowFrom; row < rowTo; row++) {
            for (int a = from; a < along; a++) {
                place(floor, a, row, BACKING[Math.floorMod(index++, BACKING.length)]);
            }
        }
    }

    /**
     * Join a door to the gangway, where the gangway does not already reach it.
     */
    private void stubFromDoor(RoomFloor floor, Doorway door, int gangwayFrom, int gangway,
                              int along, int across) {
        int[] canonical = floor.toCanonical(door.x(), door.y());
        int doorAcross = canonical[1];
        if (doorAcross >= gangwayFrom && doorAcross < gangwayFrom + gangway) return;
        int from = Math.min(Math.max(0, doorAcross), gangwayFrom);
        int to = Math.max(Math.min(across - 1, doorAcross), gangwayFrom + gangway - 1);
        int column = Math.max(0, Math.min(along - 1, canonical[0]));
        reserve(floor, column, from, 1, to - from + 1);
    }

    /** How much of the canonical frame one piece of art covers, swapped for a quarter-turned room. */
    private static int[] span(RoomFloor floor, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        int x = def == null ? 1 : def.footprintCellsX;
        int y = def == null ? 1 : def.footprintCellsY;
        return floor.pose().upright() ? new int[]{ x, y } : new int[]{ y, x };
    }

    private void placeSpanning(RoomFloor floor, int along, int across,
                               int spanAlong, int spanAcross, String id, Affordance affordance) {
        int[] rect = floor.toLocalRect(along, across, spanAlong, spanAcross);
        if (affordance == null) {
            floor.place(id, rect[0], rect[1]);
        } else {
            floor.place(id, rect[0], rect[1], affordance);
        }
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
        if (alongSpan <= 0 || acrossSpan <= 0) return;
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
