package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

/**
 * A loading bay as a marshalling floor fed by a hatch, worked down both
 * flanks.
 *
 * <p>A loading bay is not a store. Nothing here is kept — it is a <b>working
 * floor</b> a load passes through: things come aboard or go ashore through one
 * particular hatch, get broken down or built up on clear deck, and are staged
 * against a bulkhead while they wait for their next leg. Ranking a pallet stack
 * down both sides at a pitch — the arrangement this room used to carry — reads
 * as a second stockroom, and a loading bay that reads as a stockroom has lost
 * the one thing that makes it a loading bay: a hatch this room is actually
 * arranged around.
 *
 * <p>The hatch is authored rather than discovered. {@link #hookups} names the
 * near short bulkhead, and the room is {@linkplain #handed() handed} because
 * the two ends are not interchangeable — the hatch end carries the dispatch
 * desk and the loader, the far end is only where the staged run tails off.
 *
 * <p>The middle stays clear the whole length of the room, which is where a
 * load is actually broken down or built up; see {@link #CLEAR}. One long flank
 * carries the staged run — pallets waiting to go out, worked at a pitch, each
 * its own {@link Affordance#STOW}. The other carries the hatch end's own gear:
 * a dispatch desk where the manifest is checked (the one place this room reads
 * off a terminal rather than laying hands on cargo), a loader parked ready, and
 * a roller conveyor feeding the rest of that flank from the hatch.
 */
public final class LoadingBayFitting implements RoomFitting {

    /** Cells of working band along each long bulkhead. */
    private static final int BAND = 2;
    /** Cells kept clear down the middle, at least. */
    private static final int CLEAR = 3;
    /** Cells between one staged pallet group and the next. Staged cargo sits close. */
    private static final int PITCH = 2;
    /** Cells at the hatch end given to the dispatch desk and the loader. */
    private static final int HEAD = 5;

    private static final int DEFECT_STRIDE = 5;
    private static final int DEFECT_PHASE = 2;

    /** Staged cargo waiting to go out, and its ready-use satellite. */
    private static final String PALLET = "doodad.industrial-pallet-stack";
    private static final String PALLET_MATE = "doodad.box";

    /** The dispatch desk: where the run is written up. */
    private static final String DESK = "doodad.office-workstation-bank";
    private static final String DESK_BACK = "doodad.military-command-console";

    /** The loader parked at the hatch, ready to move whatever comes off it. */
    private static final String LOADER = "doodad.stores-cargo-loader";
    private static final String LOADER_FALLBACK = "doodad.industrial-generator";

    /** The belt that feeds the marshalling floor from the hatch. */
    private static final String CONVEYOR = "doodad.stores-roller-conveyor";
    private static final String CONVEYOR_FALLBACK = "doodad.industrial-pipe-bundle";

    /**
     * Loose gear backing the working flanks against the bulkhead, behind the
     * row that actually does the room's work.
     *
     * <p>A {@link #BAND} deep enough to carry two rows and worked in only one
     * of them is a rank with a wasted row behind it — deck with no argument for
     * being open, which is exactly the fill defect this room is meant to avoid.
     * Scenery rather than a second rank of jobs, because doubling the room's
     * publish count for a flank that is already worked once is the count
     * inflation the reference rooms warn against; the second row's purpose is
     * to keep the flank looking two deep, not to double its trade.
     */
    private static final String[] BACKING = {
            "doodad.industrial-drum-cluster", "doodad.industrial-scrap-pile",
            "doodad.industrial-cable-reel", "doodad.industrial-crate-stack" };

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.LOADING_BAY;
    }

    /**
     * A loading bay meets the deck at one hatch, not wherever the passage
     * search happens to arrive. The whole near short bulkhead is offered so the
     * placer can put the hatch anywhere along it, but it stays the near end —
     * the far end is where the staged run tails off and has no business being
     * the way in.
     */
    @Override
    public List<Hookup> hookups(RoomShape canonical) {
        int across = canonical.height();
        return List.of(Hookup.of(Hookup.DoorSlot.run(-1, 0, 1, across)));
    }

    /** The hatch end and the far end are different ends of a different room. */
    @Override
    public boolean handed() {
        return true;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        int band = Math.min(BAND, (across - CLEAR) / 2);
        if (band < 1) {
            // No room for a working band and clear floor both. The floor the
            // load needs wins, because it is the one thing a marshalling deck
            // cannot do without.
            floor.reserveLane(0, 0, floor.width(), floor.height());
            return;
        }

        reserve(floor, 0, band, along, across - 2 * band);
        mark(floor, 0, band, along, across - 2 * band);
        for (Doorway door : floor.localDoors()) {
            stubFromDoor(floor, door, band, across - 2 * band, along, across);
        }

        boolean conveyorAvailable = TileRegistry.installed().doodad(CONVEYOR) != null;
        boolean loaderAvailable = TileRegistry.installed().doodad(LOADER) != null;

        int northFront = band - 1;
        int southFront = across - band;
        layStagedRun(floor, northFront, 0, along, band);
        layHatchEnd(floor, along, southFront, loaderAvailable);
        layConveyorRun(floor, Math.min(HEAD, along), along, southFront, band, conveyorAvailable);
        if (band > 1) layBacking(floor, across - 1, along, 0);
    }

    /**
     * The staged run: pallets waiting to go out, ranked the whole length of the
     * flank so the room holds as much of it as it is long — a longer bay stages
     * more of a run rather than the same run with more empty deck round it.
     */
    private void layStagedRun(RoomFloor floor, int frontRow, int backRow, int along, int band) {
        int index = 0;
        for (int a = 0; a + 2 <= along; a += PITCH) {
            boolean defect = index % DEFECT_STRIDE == DEFECT_PHASE;
            place(floor, a, frontRow, PALLET, defect ? Affordance.REPAIR : Affordance.STOW);
            place(floor, a + 1, frontRow, PALLET_MATE);
            index++;
        }
        if (band > 1) layBacking(floor, backRow, along, index);
    }

    /** The dispatch desk and the loader, at the hatch end of the working flank. */
    private void layHatchEnd(RoomFloor floor, int along, int row, boolean loaderAvailable) {
        int deskWidth = Math.min(2, along);
        place(floor, 0, row, DESK, Affordance.READOUT);
        if (deskWidth > 1) place(floor, 1, row, DESK_BACK);

        int loaderAt = Math.min(deskWidth + 1, Math.max(0, along - 2));
        if (loaderAvailable) {
            int[] span = span(floor, LOADER);
            placeSpanning(floor, loaderAt, row, span[0], span[1], LOADER, null);
        } else {
            place(floor, loaderAt, row, LOADER_FALLBACK);
        }
    }

    /** The conveyor, feeding the rest of the working flank from the hatch end. */
    private void layConveyorRun(RoomFloor floor, int from, int along, int row,
                                int band, boolean conveyorAvailable) {
        String id = conveyorAvailable ? CONVEYOR : CONVEYOR_FALLBACK;
        int width = conveyorAvailable ? span(floor, id)[0] : 1;
        for (int a = from; a + width <= along; a += width) {
            if (conveyorAvailable) {
                int[] span = span(floor, id);
                placeSpanning(floor, a, row, span[0], span[1], id, null);
            } else {
                place(floor, a, row, id);
            }
        }
    }

    /** The backing row: loose gear filling the second row of a working band. */
    private void layBacking(RoomFloor floor, int row, int along, int seed) {
        for (int a = 0; a < along; a++) {
            place(floor, a, row, BACKING[Math.floorMod(a + seed, BACKING.length)]);
        }
    }

    /**
     * Join a door to the clear middle, where the middle does not already reach
     * it. A hatch on the near short bulkhead already opens onto it; this is for
     * a door the deck could only cut into one of the working bands.
     */
    private void stubFromDoor(RoomFloor floor, Doorway door, int clearFrom, int clearSpan,
                              int along, int across) {
        int[] canonical = floor.toCanonical(door.x(), door.y());
        int doorAcross = canonical[1];
        if (doorAcross >= clearFrom && doorAcross < clearFrom + clearSpan) return;
        int from = Math.min(Math.max(0, doorAcross), clearFrom);
        int to = Math.max(Math.min(across - 1, doorAcross), clearFrom + clearSpan - 1);
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

    private void mark(RoomFloor floor,
                      int along, int across, int alongSpan, int acrossSpan) {
        if (alongSpan <= 0 || acrossSpan <= 0) return;
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.markGround(rect[0], rect[1], rect[2], rect[3], GroundKind.STRIPED);
    }
}
