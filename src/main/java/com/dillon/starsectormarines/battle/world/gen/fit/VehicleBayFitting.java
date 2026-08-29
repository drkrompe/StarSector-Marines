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
 *
 * <p><b>A mech bay is the busiest compartment aboard, and its work is of
 * several kinds.</b> A berthed machine is serviced from five distinct positions rather
 * than one, because panels come off along both flanks and somebody is at its
 * head as well as under it. The gaps between bays are the bay's stores, worked
 * down a single-file aisle: stowage at the stacks, and a tally taken at the
 * terminal where each run begins. The shop at the end makes and counts. And a
 * deterministic slice of the bay's own equipment — a gantry rail, the shop's
 * plant — carries a defect list, which is what takes a hand to a corner nobody
 * otherwise had a reason to visit.
 *
 * <p>All of it is bounded by the room, not by a number written here. Bays are
 * laid while the length allows, stores fill the gaps those bays leave, and the
 * shop works whatever depth it is given — so a longer compartment is a busier
 * one for the same reason it services more machines.
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
     * Shallowest bay that can carry a working gap amidships in its gantry runs.
     *
     * <p>Below this the gap would be against either the head or the mouth, and
     * an access platform a stride from one somebody already has is not a second
     * place to work — it is the same place, published twice.
     */
    private static final int MIN_WAIST_DEPTH = 5;
    /** A bay too shallow to open a waist in its frame runs. */
    private static final int NO_WAIST = Integer.MIN_VALUE;
    /** Cells between one stores job and the next down a gap's aisle. */
    private static final int STORES_PITCH = 2;
    /**
     * Which of the bay's own equipment stands on the defect list, as a stride
     * over the order it is laid in.
     *
     * <p>A stride rather than a draw. Generation is reproducible from its seed
     * and a fitting holds no random source at all, so a list picked by an
     * unseeded roll would give the same room a different set of snags every time
     * it was built — and the one thing a defect has to do is still be there when
     * somebody walks back to it. Laying order is stable geometry, so this is
     * stable too.
     *
     * <p>The list falls on gear that affords nothing else: a gantry rail, and
     * the shop's plant. That is deliberate rather than convenient. A defect is
     * what takes a hand to a corner of the bay nobody otherwise had a reason to
     * visit, and one written over a bench's routine work would move a job rather
     * than add one.
     */
    private static final int DEFECT_STRIDE = 3;
    private static final int DEFECT_PHASE = 1;
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
     * The gantry frame down each side of a bay.
     *
     * <p>A bay is framed, not decorated. Scattering single tools down its sides
     * read as props left lying about; what a servicing bay actually has is
     * continuous structure the machine stands inside. Fence runs are the closest
     * thing in the registry to a gantry rail seen from above, and they read as
     * one because they run: the two breaks in each are the ways into the frame
     * rather than places the drawing gave up.
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

    /**
     * The bay's stores, stacked down both sides of the gap between one bay and
     * the next.
     *
     * <p>Stock and loose gear in one list, because that is what the gap holds:
     * the pallet that came aboard with the last consignment stands next to the
     * cable reel nobody has put away, and telling one from the other is the work
     * rather than the premise.
     */
    private static final String[] GAP_STORES = {
            "doodad.industrial-crate-stack",
            "doodad.industrial-drum-cluster",
            "doodad.industrial-pallet-stack",
            "doodad.industrial-cable-reel",
            "doodad.industrial-dumpster",
            "doodad.industrial-scrap-pile",
            "doodad.box",
            "doodad.industrial-pipe-bundle" };

    /** The terminal a run of stores is tallied against, at its outboard end. */
    private static final String GAP_TALLY = "doodad.industrial-control-console";

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

    /**
     * What each of those is for, in the same order.
     *
     * <p>Plant is not a workplace: a generator and a fluid tank are the shop's
     * services rather than its benches, and routine work published at them would
     * station somebody at the wall. They earn their keep on the defect list
     * instead, which is the one thing that does take a hand to them.
     *
     * <p>Everything else is somebody's, and the two kinds of somebody are kept
     * apart on purpose. Stowage is handling — a pallet broken down, a rack
     * restowed, a part walked from one stack to the next — and a readout is
     * counting, taken off a terminal with no hand laid on the stock. A shop that
     * published only the first has stores nobody has ever inventoried.
     */
    private static final Affordance[] SHOP_WORK = {
            Affordance.READOUT,
            Affordance.FABRICATE,
            Affordance.READOUT,
            Affordance.STOW,
            null,
            Affordance.STOW,
            Affordance.READOUT,
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
        int bay = 0;
        while (cursor + BAY_WIDTH <= bayLimit) {
            layBay(floor, cursor, 0, bayDepth, true, bay++);
            if (facingRanks) {
                layBay(floor, cursor, across - bayDepth, bayDepth, false, bay++);
            }
            layStores(floor, cursor + BAY_WIDTH, bayLimit, across, bayDepth, facingRanks);
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
                        int origin, int band, int depth, boolean headOutboard, int bay) {
        mark(floor, origin, band, BAY_WIDTH, depth);
        paveBay(floor, origin, band, depth);

        int head = headOutboard ? band : band + depth - 1;
        int mouth = headOutboard ? band + depth - 1 : band;
        int berthFrom = headOutboard ? band + 1 : band;
        int berth = berth(floor, origin + 1, berthFrom,
                BAY_WIDTH - 2, depth - 1, headOutboard);
        int waist = depth >= MIN_WAIST_DEPTH
                ? head + (headOutboard ? depth / 2 : -(depth / 2))
                : NO_WAIST;

        // The station sits at the head of the bay, against the outer bulkhead,
        // so it never stands between the machine and the lane it leaves by.
        for (int i = 0; i < BAY_STATION.length; i++) {
            place(floor, origin + 1 + i, head, BAY_STATION[i],
                    BAY_STATION_WORK[i]);
        }

        layFrame(floor, origin, band, depth, head, mouth, waist, true);
        layFrame(floor, origin + BAY_WIDTH - 1, band, depth, head, mouth, waist, false);
        layService(floor, origin, mouth, head, waist, berth,
                bay % DEFECT_STRIDE == DEFECT_PHASE);
    }

    /**
     * The gantry frame down one working column of a bay, broken at the mouth and
     * again at the waist.
     *
     * <p>Continuous is still the point. A run of separate tools down the side of
     * a bay reads as clutter; a rail broken twice in seven cells reads as
     * structure the machine is standing inside, with the two ways into it a
     * frame has to have.
     *
     * <p>Both breaks are places to work rather than gaps in the drawing. The
     * mouth is the shoulder a technician comes in at; the waist is the access
     * platform amidships, and it is what lets a machine be worked on by more
     * than the two people its mouth admits.
     */
    private void layFrame(RoomFloor floor, int column,
                          int band, int depth, int head, int mouth, int waist,
                          boolean nearSide) {
        // The run is drawn in deck space, so the sprite has to be chosen there
        // too: a rail authored running fore-and-aft is athwartships once the
        // room is turned, and a compass-named piece cannot be turned with it.
        int[] run = floor.pose().mapDirection(0, 1);
        String straight = run[0] != 0 ? FRAME_ALONG_X : FRAME_ALONG_Y;
        for (int step = 0; step < depth; step++) {
            int across = band + step;
            if (across == mouth || across == waist) continue;
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
     * Every place a technician stands to work on the machine in this bay: the
     * two shoulders at the mouth, the two access platforms at the waist, and the
     * spare cell across the head from the station.
     *
     * <p>All of it bound to the berth rather than to the cell, because the work
     * only exists while something is parked there. That has to stay true however
     * many positions a bay gains, or an empty bay reads as five people welding
     * air.
     *
     * <p>Five positions rather than one because a berthed walker is not one job.
     * Panels come off along both flanks, a fitter goes underneath from the
     * mouth, and somebody is at its head where the station is. They are distinct
     * places at distinct parts of the machine, which is the difference between
     * more work and the same work counted again — and a bay that published a
     * single point had a rank of gantries with room for two people in it.
     */
    private void layService(RoomFloor floor, int origin,
                            int mouth, int head, int waist, int berth, boolean defect) {
        int inboard = mouth < head ? mouth + 1 : mouth - 1;
        for (int column : new int[]{ origin, origin + BAY_WIDTH - 1 }) {
            berthTask(floor, column, mouth, berth, column, inboard);
        }

        // Across the head from the station, where the machine's front is. The
        // station takes the cells beside the frame corner, so a wider station
        // simply leaves nothing here rather than standing somebody on itself.
        int spare = origin + 1 + BAY_STATION.length;
        if (spare <= origin + BAY_WIDTH - 2) {
            berthTask(floor, spare, head, berth, spare, head + Integer.signum(mouth - head));
        }

        if (waist == NO_WAIST) return;
        berthTask(floor, origin + BAY_WIDTH - 1, waist, berth, origin + BAY_WIDTH - 2, waist);
        berthTask(floor, origin, waist, berth, origin + 1, waist);
        if (!defect) return;

        // The snag on this bay's port rail, stood at from inside the bay beside
        // it and published against the frame rather than against the berth: a
        // defect does not clear itself when the walker drives out.
        //
        // Added rather than substituted, and that is the whole reason it is not
        // simply written over one of the service positions. Every berth aboard
        // is worked from the same number of places, so a bay filling up moves
        // the ship's servicing work one berth at a time - and a berth that
        // quietly offered one position fewer because it had drawn a defect would
        // make that arithmetic untrue in a way nobody could see from the deck.
        int rail = waist + Integer.signum(head - waist);
        task(floor, origin + 1, rail, Affordance.REPAIR, origin, rail);
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
     * The gap between one bay and the next, worked as the bay's stores: stacks
     * down both sides of a single-file aisle running from the outer bulkhead in
     * to the service lane.
     *
     * <p>The gap used to be scenery, and the reason given was that it was packed
     * tightly enough that anywhere to stand in it would be walled in by the next
     * drum. That was true of the packing rather than of the gap. Opening one file
     * down the middle costs a third of the stock and buys what the stock was
     * missing: somewhere to stand between two stacks, which is the whole
     * difference between stores and texture.
     *
     * <p>The aisle is reserved before anything is stacked and meets the service
     * lane at the inboard end of each run, so it is circulation as well as a
     * workplace. A stores lane that dead-ended would be a pocket the fill had to
     * be lucky to leave reachable.
     *
     * <p>Two jobs at two kinds of fixture, because they are two jobs. The tally
     * is taken at a terminal against the outer bulkhead where a run starts; the
     * handling is at the stacks along it, and it alternates sides so a part is
     * carried across the aisle rather than set down where it was picked up. Work
     * goes at a pitch rather than at every stack, which leaves every other cell
     * of a one-cell aisle clear for somebody to get past whoever is already
     * working in it.
     */
    private void layStores(RoomFloor floor, int from, int limit,
                           int across, int bayDepth, boolean facingRanks) {
        int width = Math.min(BAY_GAP, limit - from);
        if (width <= 0) return;
        if (width < 3) {
            // Too narrow to stack either side of an aisle. Left as lane rather
            // than packed with stock, because a remainder nobody can work is
            // deck the service lane may as well have.
            reserve(floor, from, 0, width, across);
            return;
        }

        int aisle = from + width / 2;
        reserve(floor, aisle, 0, 1, across);

        // Seeded off the gap's own column, so two gaps do not stack identical
        // stock in identical order and the same gap stacks the same way every
        // time this room is built.
        int index = from;
        int ranks = facingRanks ? 2 : 1;
        for (int rank = 0; rank < ranks; rank++) {
            for (int step = 0; step < bayDepth; step++) {
                int row = rank == 0 ? step : across - 1 - step;
                boolean outboard = step == 0;
                for (int column = from; column < from + width; column++) {
                    if (column == aisle) continue;
                    place(floor, column, row,
                            outboard && column < aisle
                                    ? GAP_TALLY
                                    : GAP_STORES[index++ % GAP_STORES.length]);
                }
                if (outboard) {
                    task(floor, aisle, row, Affordance.READOUT, aisle - 1, row);
                } else if (step % STORES_PITCH == 0) {
                    int flank = (step / STORES_PITCH) % 2 == 0 ? aisle - 1 : aisle + 1;
                    task(floor, aisle, row, Affordance.STOW, flank, row);
                }
            }
        }
    }

    /**
     * The workshop at one end, worked densely because it is where the work
     * happens, and carrying the half of the bay's defect list that is not a
     * gantry.
     */
    private void layShop(RoomFloor floor,
                         int from, int along, int across) {
        int index = 0;
        int plant = 0;
        for (int offset = from; offset < along; offset += 2) {
            for (int depth = 0; depth < across; depth += 2) {
                int pick = index++ % SHOP.length;
                Affordance work = SHOP_WORK[pick];
                if (work == null && plant++ % DEFECT_STRIDE == DEFECT_PHASE) {
                    work = Affordance.REPAIR;
                }
                place(floor, offset, depth, SHOP[pick], work);
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

    /** Work at a cell this arrangement chose itself, authored canonically. */
    private void task(RoomFloor floor, int cellAlong, int cellAcross,
                      Affordance affordance, int fixtureAlong, int fixtureAcross) {
        int[] stand = floor.toLocal(cellAlong, cellAcross);
        int[] fixture = floor.toLocal(fixtureAlong, fixtureAcross);
        floor.fixtureTask(stand[0], stand[1], affordance, fixture[0], fixture[1]);
    }

    /** The same, for work done on whatever is parked in {@code berth}. */
    private void berthTask(RoomFloor floor, int cellAlong, int cellAcross,
                           int berth, int fixtureAlong, int fixtureAcross) {
        int[] stand = floor.toLocal(cellAlong, cellAcross);
        int[] fixture = floor.toLocal(fixtureAlong, fixtureAcross);
        floor.berthFixtureTask(stand[0], stand[1], berth, fixture[0], fixture[1]);
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
