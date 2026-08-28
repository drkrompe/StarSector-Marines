package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * A mech bay as two ranks of gantry bays facing each other across a service
 * lane, with the fab shop at one end.
 *
 * <p>A vehicle bay is not one big room with gear round the edge — it is
 * <b>several bays</b>, each sized for one machine, and they face each other
 * because that is what makes the deck between them a working lane instead of
 * dead floor. A single rank against one bulkhead left most of a sixteen-deep
 * compartment as void: plausible on the plan and obviously wrong close up.
 *
 * <p>The bay module is {@link #BAY_WIDTH} by {@link #BAY_DEPTH}, as the
 * hand-authored Mech Lab had it. It and the runs around it are public because a
 * room that means to hold bays has to be sized from them: a compound hangar
 * derives its own footprint here rather than picking dimensions that look about
 * right and discovering the arrangement will not fit. The middle is kept clear for the machine —
 * which may be any size, so the clearance is reserved rather than filled — and
 * the flanking columns carry the tools a technician works from. A bay a walker
 * cannot fit into, or that a technician cannot get around, is not a bay.
 *
 * <p>Capacity is the bay count, so a longer compartment services more machines.
 * Nothing records that separately.
 *
 * <p>Each bay publishes a {@link Gantry} — the berth a machine stands in — and
 * leaves those cells clear. The machines themselves are units, not scenery, so
 * whoever hosts the deck fills the berths from a roster: the player's own lance
 * when this bay is their lab, somebody else's when the deck is a prize.
 */
public final class VehicleBayFitting implements RoomFitting {

    /** Cells across one gantry bay: the machine plus a working column each side. */
    public static final int BAY_WIDTH = 5;
    /** Cells along one gantry bay, bow to stern of the machine standing in it. */
    public static final int BAY_DEPTH = 7;
    /** Bulkhead between one bay and the next. Enough to walk a part through. */
    public static final int BAY_GAP = 3;
    /** Clear deck down the middle, between the two ranks. The room's main lane. */
    public static final int SERVICE_LANE = 2;
    /** Cells at one end given over to the fab shop. */
    public static final int SHOP_WIDTH = 7;
    /** Cells of bulkhead a doorway may take, which is what a machine needs to pass. */
    private static final int DOORWAY = 2;
    /**
     * Cells at the forward end kept as the vestibule: the athwartships run that
     * joins the bay's two doors to each other and to the service lane.
     *
     * <p>Authored, not reacted to. The bay used to clear a band the full depth
     * of the room wherever a door turned out to be, and then skip any bay that
     * overlapped it — which cost berths, and cost them in a different place on
     * every deck. Now the room says where its doors are, so the deck they open
     * onto can be part of the arrangement instead of an apology for it.
     */
    public static final int VESTIBULE = DOORWAY + 1;

    /**
     * The bay floor, taken from the hand-authored Mech Lab rather than invented.
     *
     * <p>The urban sheet carries a marked industrial deck: a yellow stripe edges
     * a bay, and two grates alternate across its middle. A shade of the room
     * colour was never going to do this job — a bay is a marked-out rectangle of
     * floor, and painting it is what stops a row of bays reading as frames
     * standing on nothing.
     *
     * <p>Named rather than pointed at. These used to be a row and three columns
     * into {@code urban-tileset.png}, which is a coordinate into a packed atlas
     * that the tileset exporter is free to lay out however it likes. Nothing
     * downstream can notice such a reference going stale: the deck still paints,
     * and simply paints shelves.
     */
    private static final String FLOOR_EDGE = "doodad.fl-striped-yellow";
    private static final String[] FLOOR_FIELD = { "doodad.fl-grate-1", "doodad.fl-grate-2" };

    /**
     * The gantry frame down each side of a bay, and the clutter that collects
     * between bays.
     *
     * <p>A bay is framed, not decorated. Scattering single tools down its sides
     * read as props left lying about; what a servicing bay actually has is
     * continuous structure the machine stands inside, with the loose gear —
     * drums, reels, spoil — pushed into the gaps between bays where it is out of
     * the way. Fence runs are the closest thing in the registry to a gantry rail
     * seen from above, and they read as one because they are unbroken.
     */
    private static final String FRAME_ALONG_X = "doodad.industrial-fence-straight-h";
    private static final String FRAME_ALONG_Y = "doodad.industrial-fence-straight-v";
    private static final String[] FRAME_CORNERS = {
            "doodad.industrial-fence-corner-nw", "doodad.industrial-fence-corner-sw",
            "doodad.industrial-fence-corner-ne", "doodad.industrial-fence-corner-se" };

    /** The station at the head of a bay, where the work on that machine is run from. */
    private static final String[] BAY_STATION = {
            "doodad.industrial-machine-tool",
            "doodad.industrial-control-console" };

    /**
     * What the head of a bay is good for: a tool to make a part at, and the
     * terminal a machine's condition is read off rather than felt for.
     */
    private static final Affordance[] BAY_STATION_WORK = {
            Affordance.FABRICATE,
            Affordance.READOUT };

    /** Loose gear, pushed into the gaps between bays. */
    private static final String[] BAY_CLUTTER = {
            "doodad.industrial-drum-cluster",
            "doodad.industrial-cable-reel",
            "doodad.industrial-scrap-pile",
            "doodad.industrial-pipe-bundle",
            "doodad.industrial-pallet-stack" };

    /** The fab shop: benches, stock, and the console that runs it. */
    private static final String[] SHOP = {
            "doodad.office-workstation-bank",
            "doodad.industrial-machine-tool",
            "doodad.military-command-console",
            "doodad.industrial-pallet-stack",
            "doodad.industrial-fluid-tank",
            "doodad.industrial-crate-stack",
            "doodad.office-server-rack",
            "doodad.industrial-generator" };

    /** What each of those is for, in the same order. Plant is not a workplace. */
    private static final Affordance[] SHOP_WORK = {
            Affordance.READOUT,
            Affordance.FABRICATE,
            Affordance.READOUT,
            Affordance.STOW,
            null,
            Affordance.STOW,
            null,
            null };

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.VEHICLE_BAY;
    }

    /**
     * Two ways a bay meets the deck, in order of preference.
     *
     * <p>The first is a drive-through: a door at the forward end of each long
     * side, so a machine has a way in and a way out that is not the way it came.
     * That is what a vehicle bay is for, and it puts both doors clear of the
     * gantry ranks rather than through them.
     *
     * <p>The second is a single door on the forward bulkhead, amidships, for the
     * hull that simply has no passage down either side of the bay. It is a
     * worse bay and it is offered second, but it is a bay rather than a hold
     * with gantries in it.
     */
    @Override
    public boolean handed() {
        return true;
    }

    @Override
    public List<Hookup> hookups(RoomShape canonical) {
        int length = canonical.width();
        int depth = canonical.height();
        return List.of(
                Hookup.of(
                        Hookup.DoorSlot.run(0, -1, DOORWAY, 1),
                        Hookup.DoorSlot.run(0, depth, DOORWAY, 1)),
                Hookup.of(Hookup.DoorSlot.run(-1, depth / 2 - 1, 1, DOORWAY)),
                Hookup.of(Hookup.DoorSlot.run(length - DOORWAY, -1, DOORWAY, 1)));
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();

        int bayDepth = Math.min(BAY_DEPTH, (across - SERVICE_LANE) / 2);
        boolean facingRanks = bayDepth >= 3;
        if (!facingRanks) {
            bayDepth = Math.max(3, across - SERVICE_LANE);
        }

        int vestibule = Math.min(VESTIBULE, Math.max(0, along - BAY_WIDTH));
        int shopWidth = Math.min(SHOP_WIDTH, Math.max(0, along - vestibule - BAY_WIDTH - 1));
        int bayLimit = along - shopWidth;
        int laneFrom = bayDepth;
        int laneSpan = Math.max(1, across - (facingRanks ? 2 * bayDepth : bayDepth));

        // Circulation first, both runs of it: the vestibule the doors open into
        // and the service lane the ranks face across. Everything placed after
        // this has to work around them, which is the point.
        reserve(floor, 0, 0, vestibule, across);
        reserve(floor, vestibule, laneFrom, bayLimit - vestibule, laneSpan);

        int cursor = vestibule;
        while (cursor + BAY_WIDTH <= bayLimit) {
            layBay(floor, cursor, 0, bayDepth, true);
            if (facingRanks) {
                layBay(floor, cursor, across - bayDepth, bayDepth, false);
            }
            layClutter(floor, cursor + BAY_WIDTH, across, bayDepth, facingRanks);
            cursor += BAY_WIDTH + BAY_GAP;
        }

        stubStrandedDoors(floor, along, across, laneFrom, laneSpan, vestibule);
        if (shopWidth > 0) {
            layShop(floor, bayLimit, along, across);
        }
    }

    /**
     * Join any door the vestibule does not already serve to the service lane,
     * one cell wide.
     *
     * <p>A room states where it hooks up, and is placed with an ordinary door
     * where no hull could serve that. This is the cost of that promise, kept as
     * small as it can be: a single file stub across one rank, rather than the
     * full-depth band that used to be cleared for every door and took a bay
     * with it.
     */
    private void stubStrandedDoors(RoomFloor floor, int along, int across,
                                   int laneFrom, int laneSpan, int vestibule) {
        for (Doorway door : floor.localDoors()) {
            int[] canonical = floor.toCanonical(door.x(), door.y());
            int doorAlong = Math.max(0, Math.min(along - 1, canonical[0]));
            if (doorAlong < vestibule) continue;
            int doorAcross = Math.max(0, Math.min(across - 1, canonical[1]));
            int from = Math.min(doorAcross, laneFrom);
            int to = Math.max(doorAcross, laneFrom + laneSpan - 1);
            reserve(floor, doorAlong, from, 1, to - from + 1);
        }
    }

    /**
     * One gantry bay: clearance down the middle for the machine, framing down
     * the columns either side of it, its station across the head, and the deck
     * marked so the bay reads as a bay standing empty rather than as a gap
     * between tools.
     *
     * <p>The head row belongs to the station and the berth is everything abaft
     * of it. Berthing the full depth and then placing the station inside that
     * reservation is how a bay came to contain nothing at all: a berth keeps its
     * cells clear for the machine, so every station placement was refused and
     * the bay was left as painted floor.
     */
    private void layBay(RoomFloor floor,
                        int origin, int band, int depth, boolean headOutboard) {
        mark(floor, origin, band, BAY_WIDTH, depth);
        paveBay(floor, origin, band, depth);

        int head = headOutboard ? band : band + depth - 1;
        int mouth = headOutboard ? band + depth - 1 : band;
        int berthFrom = headOutboard ? band + 1 : band;
        int berth = berth(floor, origin + 1, berthFrom,
                BAY_WIDTH - 2, depth - 1, headOutboard);

        // The station sits at the head of the bay, against the outer bulkhead,
        // so it never stands between the machine and the lane it leaves by.
        for (int i = 0; i < BAY_STATION.length; i++) {
            place(floor, origin + 1 + i, head, BAY_STATION[i],
                    BAY_STATION_WORK[i]);
        }

        layFrame(floor, origin, band, depth, head, mouth, true);
        layFrame(floor, origin + BAY_WIDTH - 1, band, depth, head, mouth, false);
        layService(floor, origin, mouth, head, berth);
    }

    /**
     * The gantry frame down one working column of a bay, stopping a cell short
     * of the mouth.
     *
     * <p>Unbroken is the whole point. A run of separate tools down the side of a
     * bay reads as clutter; a continuous rail reads as structure the machine is
     * standing inside. The cell left open at the mouth is the shoulder a
     * technician comes in at, which is the one thing an unbroken run would take
     * away.
     */
    private void layFrame(RoomFloor floor, int column,
                          int band, int depth, int head, int mouth, boolean nearSide) {
        // The run is drawn in deck space, so the sprite has to be chosen there
        // too: a rail authored running fore-and-aft is athwartships once the
        // room is turned, and a compass-named piece cannot be turned with it.
        int[] run = floor.pose().mapDirection(0, 1);
        String straight = run[0] != 0 ? FRAME_ALONG_X : FRAME_ALONG_Y;
        for (int step = 0; step < depth; step++) {
            int across = band + step;
            if (across == mouth) continue;
            place(floor, column, across,
                    across == head ? corner(floor, nearSide, head < mouth) : straight);
        }
    }

    /**
     * The corner piece closing the head of one frame run, chosen by how the run
     * and the bulkhead actually meet on the deck rather than by which rank this
     * happens to be.
     */
    private static String corner(RoomFloor floor, boolean nearSide, boolean headLow) {
        int[] toColumn = floor.pose().mapDirection(nearSide ? -1 : 1, 0);
        int[] toHead = floor.pose().mapDirection(0, headLow ? -1 : 1);
        return FRAME_CORNERS[(toColumn[0] > 0 ? 2 : 0) + (toHead[1] < 0 ? 1 : 0)];
    }

    /**
     * Where a technician stands to work on the machine in this bay: the two
     * shoulders at the mouth, either side of it.
     *
     * <p>Bound to the berth rather than to the cell, because the work only
     * exists while something is parked there. An empty bay is somewhere to walk
     * through, not somewhere to weld.
     */
    private void layService(RoomFloor floor,
                            int origin, int mouth, int head, int berth) {
        int inboard = mouth < head ? mouth + 1 : mouth - 1;
        for (int column : new int[]{ origin, origin + BAY_WIDTH - 1 }) {
            int[] stand = floor.toLocal(column, mouth);
            int[] frame = floor.toLocal(column, inboard);
            floor.berthFixtureTask(stand[0], stand[1], berth, frame[0], frame[1]);
        }
    }

    /**
     * Publish the bay's berth: the clear middle a machine stands in.
     *
     * <p>Faces away from the head of the bay, which is the way out. A berth
     * pointing at its own workstation would have the machine backing into the
     * lane every time it left.
     */
    private int berth(RoomFloor floor,
                      int along, int across, int alongSpan, int acrossSpan,
                      boolean headOutboard) {
        // Canonically the head of a bay is at low y, so a machine leaves toward
        // high y. The pose carries that heading onto the deck, which is why a
        // flipped bay faces its machines the other way without being told to.
        int[] out = floor.pose().mapDirection(0, headOutboard ? 1 : -1);
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        return floor.berth(rect[0], rect[1], rect[2], rect[3],
                Gantry.Facing.of(out[0], out[1]));
    }

    /** Paint the bay's deck, edged and then checkered, before anything stands on it. */
    private void paveBay(RoomFloor floor,
                         int origin, int band, int depth) {
        // The whole bay is paved and its perimeter is striped, all four sides:
        // a bay is a marked-out rectangle of deck, and marking three sides of it
        // is not marking it.
        for (int step = 0; step < depth; step++) {
            for (int side = 0; side < BAY_WIDTH; side++) {
                boolean perimeter = side == 0 || side == BAY_WIDTH - 1
                        || step == 0 || step == depth - 1;
                String paving = perimeter ? FLOOR_EDGE : FLOOR_FIELD[((side + step) & 1)];
                int[] cell = floor.toLocal(origin + side, band + step);
                floor.pave(cell[0], cell[1], paving);
            }
        }
    }

    /**
     * Loose gear in the gap between one bay and the next, clear of the lane.
     *
     * <p>Scenery, deliberately: the gap is where what nobody has dealt with yet
     * gets pushed, and it is packed tightly enough that somewhere to stand in it
     * would be walled in by the next drum. Stores worth handling live in the
     * shop, where there is room to carry a part from one stack to another.
     */
    private void layClutter(RoomFloor floor,
                            int from, int across, int bayDepth, boolean facingRanks) {
        int index = from;
        for (int offset = 0; offset < BAY_GAP; offset++) {
            for (int step = 0; step < bayDepth; step += 3) {
                place(floor, from + offset, step,
                        BAY_CLUTTER[index++ % BAY_CLUTTER.length]);
                if (facingRanks) {
                    place(floor, from + offset, across - 1 - step,
                            BAY_CLUTTER[index++ % BAY_CLUTTER.length]);
                }
            }
        }
    }

    /** The workshop at one end, worked densely because it is where the work happens. */
    private void layShop(RoomFloor floor,
                         int from, int along, int across) {
        int index = 0;
        for (int offset = from; offset < along; offset += 2) {
            for (int depth = 0; depth < across; depth += 2) {
                int pick = index++ % SHOP.length;
                place(floor, offset, depth, SHOP[pick], SHOP_WORK[pick]);
            }
        }
    }

    private void place(RoomFloor floor,
                       int along, int across, String id) {
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1]);
    }

    /** Place a fixture that is also somewhere with work at it. */
    private void place(RoomFloor floor,
                       int along, int across, String id, Affordance affordance) {
        if (affordance == null) {
            place(floor, along, across, id);
            return;
        }
        int[] cell = floor.toLocal(along, across);
        floor.place(id, cell[0], cell[1], affordance);
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }

    private void mark(RoomFloor floor,
                      int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.markGround(rect[0], rect[1], rect[2], rect[3], GroundKind.STRIPED);
    }
}
