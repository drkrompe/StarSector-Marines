package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
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
 * hand-authored Mech Lab had it. The middle is kept clear for the machine —
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
    private static final int BAY_GAP = 3;
    /** Clear deck down the middle, between the two ranks. The room's main lane. */
    private static final int SERVICE_LANE = 2;
    /** Cells at one end given over to the fab shop. */
    private static final int SHOP_WIDTH = 7;
    /** Cells kept clear either side of a door, so a machine can be driven through it. */
    private static final int DOOR_CLEARANCE = 1;

    /**
     * The bay floor, taken from the hand-authored Mech Lab rather than invented.
     *
     * <p>Row three of the urban sheet carries a marked industrial deck: column
     * one edges a bay, columns nought and two alternate across its middle. A
     * shade of the room colour was never going to do this job — a bay is a
     * marked-out rectangle of floor, and painting it is what stops a row of bays
     * reading as frames standing on nothing.
     */
    private static final int FLOOR_ROW = 3;
    private static final int FLOOR_EDGE_COLUMN = 1;
    private static final int[] FLOOR_FIELD_COLUMNS = { 0, 2 };

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

    @Override
    public void fit(CompartmentFloor floor) {
        boolean lengthwise = floor.width() >= floor.height();
        int along = lengthwise ? floor.width() : floor.height();
        int across = lengthwise ? floor.height() : floor.width();

        // The door decides the layout, not the corner of the bounding box. A
        // bay parked across the only way in is a bay whose machine can never
        // leave it, and the deck in front of a door is the one run of floor in
        // a vehicle bay that is never negotiable.
        List<int[]> approaches = approaches(floor, lengthwise, along, across);
        for (int[] approach : approaches) {
            reserve(floor, lengthwise, approach[0], 0, approach[1] - approach[0] + 1, across);
        }

        int bayDepth = Math.min(BAY_DEPTH, (across - SERVICE_LANE) / 2);
        boolean facingRanks = bayDepth >= 3;
        if (!facingRanks) {
            bayDepth = Math.max(3, across - SERVICE_LANE);
        }

        int shopWidth = Math.min(SHOP_WIDTH, Math.max(0, along - BAY_WIDTH - 1));
        int bayLimit = along - shopWidth;

        int cursor = 0;
        while (cursor + BAY_WIDTH <= bayLimit) {
            int blockedUntil = blockedUntil(approaches, cursor, cursor + BAY_WIDTH - 1);
            if (blockedUntil >= 0) {
                cursor = blockedUntil + 1;
                continue;
            }
            layBay(floor, lengthwise, cursor, 0, bayDepth, true);
            if (facingRanks) {
                layBay(floor, lengthwise, cursor, across - bayDepth, bayDepth, false);
            }
            layClutter(floor, lengthwise, cursor + BAY_WIDTH, across, bayDepth, facingRanks);
            cursor += BAY_WIDTH + BAY_GAP;
        }

        // The lane between the ranks is the route a machine and its parts
        // travel, reserved before anything is placed rather than being whatever
        // happens to be left over.
        reserve(floor, lengthwise, 0, bayDepth, bayLimit,
                Math.max(1, across - (facingRanks ? 2 * bayDepth : bayDepth)));
        if (shopWidth > 0) {
            layShop(floor, lengthwise, bayLimit, along, across);
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
    private void layBay(CompartmentFloor floor, boolean lengthwise,
                        int origin, int band, int depth, boolean headOutboard) {
        mark(floor, lengthwise, origin, band, BAY_WIDTH, depth);
        paveBay(floor, lengthwise, origin, band, depth);

        int head = headOutboard ? band : band + depth - 1;
        int mouth = headOutboard ? band + depth - 1 : band;
        int berthFrom = headOutboard ? band + 1 : band;
        int berth = berth(floor, lengthwise, origin + 1, berthFrom,
                BAY_WIDTH - 2, depth - 1, headOutboard);

        // The station sits at the head of the bay, against the outer bulkhead,
        // so it never stands between the machine and the lane it leaves by.
        for (int i = 0; i < BAY_STATION.length; i++) {
            place(floor, lengthwise, origin + 1 + i, head, BAY_STATION[i],
                    BAY_STATION_WORK[i]);
        }

        layFrame(floor, lengthwise, origin, band, depth, head, mouth, true);
        layFrame(floor, lengthwise, origin + BAY_WIDTH - 1, band, depth, head, mouth, false);
        layService(floor, lengthwise, origin, mouth, head, berth);
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
    private void layFrame(CompartmentFloor floor, boolean lengthwise, int column,
                          int band, int depth, int head, int mouth, boolean nearSide) {
        String straight = lengthwise ? FRAME_ALONG_Y : FRAME_ALONG_X;
        for (int step = 0; step < depth; step++) {
            int across = band + step;
            if (across == mouth) continue;
            place(floor, lengthwise, column, across,
                    across == head ? corner(lengthwise, nearSide, head < mouth) : straight);
        }
    }

    /**
     * The corner piece closing the head of one frame run, chosen by how the run
     * and the bulkhead actually meet on the deck rather than by which rank this
     * happens to be.
     */
    private static String corner(boolean lengthwise, boolean nearSide, boolean headLow) {
        boolean east = lengthwise ? !nearSide : !headLow;
        boolean south = lengthwise ? headLow : nearSide;
        return FRAME_CORNERS[(east ? 2 : 0) + (south ? 1 : 0)];
    }

    /**
     * Where a technician stands to work on the machine in this bay: the two
     * shoulders at the mouth, either side of it.
     *
     * <p>Bound to the berth rather than to the cell, because the work only
     * exists while something is parked there. An empty bay is somewhere to walk
     * through, not somewhere to weld.
     */
    private void layService(CompartmentFloor floor, boolean lengthwise,
                            int origin, int mouth, int head, int berth) {
        int inboard = mouth < head ? mouth + 1 : mouth - 1;
        for (int column : new int[]{ origin, origin + BAY_WIDTH - 1 }) {
            floor.berthTaskPoint(
                    lengthwise ? column : mouth,
                    lengthwise ? mouth : column,
                    berth,
                    lengthwise ? column : inboard,
                    lengthwise ? inboard : column);
        }
    }

    /**
     * Publish the bay's berth: the clear middle a machine stands in.
     *
     * <p>Faces away from the head of the bay, which is the way out. A berth
     * pointing at its own workstation would have the machine backing into the
     * lane every time it left.
     */
    private int berth(CompartmentFloor floor, boolean lengthwise,
                      int along, int across, int alongSpan, int acrossSpan,
                      boolean headOutboard) {
        Gantry.Facing facing;
        if (lengthwise) {
            facing = headOutboard ? Gantry.Facing.NORTH : Gantry.Facing.SOUTH;
        } else {
            facing = headOutboard ? Gantry.Facing.EAST : Gantry.Facing.WEST;
        }
        return floor.berth(
                lengthwise ? along : across,
                lengthwise ? across : along,
                lengthwise ? alongSpan : acrossSpan,
                lengthwise ? acrossSpan : alongSpan,
                facing);
    }

    /** Paint the bay's deck, edged and then checkered, before anything stands on it. */
    private void paveBay(CompartmentFloor floor, boolean lengthwise,
                         int origin, int band, int depth) {
        // The whole bay is paved and its perimeter is striped, all four sides:
        // a bay is a marked-out rectangle of deck, and marking three sides of it
        // is not marking it.
        for (int step = 0; step < depth; step++) {
            for (int side = 0; side < BAY_WIDTH; side++) {
                boolean perimeter = side == 0 || side == BAY_WIDTH - 1
                        || step == 0 || step == depth - 1;
                int column = perimeter ? FLOOR_EDGE_COLUMN
                        : FLOOR_FIELD_COLUMNS[((side + step) & 1)];
                int x = lengthwise ? origin + side : band + step;
                int y = lengthwise ? band + step : origin + side;
                floor.pave(x, y, column, FLOOR_ROW);
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
    private void layClutter(CompartmentFloor floor, boolean lengthwise,
                            int from, int across, int bayDepth, boolean facingRanks) {
        int index = from;
        for (int offset = 0; offset < BAY_GAP; offset++) {
            for (int step = 0; step < bayDepth; step += 3) {
                place(floor, lengthwise, from + offset, step,
                        BAY_CLUTTER[index++ % BAY_CLUTTER.length]);
                if (facingRanks) {
                    place(floor, lengthwise, from + offset, across - 1 - step,
                            BAY_CLUTTER[index++ % BAY_CLUTTER.length]);
                }
            }
        }
    }

    /** The workshop at one end, worked densely because it is where the work happens. */
    private void layShop(CompartmentFloor floor, boolean lengthwise,
                         int from, int along, int across) {
        int index = 0;
        for (int offset = from; offset < along; offset += 2) {
            for (int depth = 0; depth < across; depth += 2) {
                int pick = index++ % SHOP.length;
                place(floor, lengthwise, offset, depth, SHOP[pick], SHOP_WORK[pick]);
            }
        }
    }

    /**
     * The runs of deck that have to stay clear because a door opens onto them,
     * as inclusive ranges along the compartment.
     */
    private static List<int[]> approaches(CompartmentFloor floor, boolean lengthwise,
                                          int along, int across) {
        List<int[]> ranges = new ArrayList<>();
        for (DeckGraph.Compartment.Door door : floor.localDoors()) {
            int doorAlong = lengthwise ? door.x() : door.y();
            int doorAcross = lengthwise ? door.y() : door.x();
            if (doorAcross < 0 || doorAcross >= across) {
                ranges.add(new int[]{
                        Math.max(0, doorAlong - DOOR_CLEARANCE),
                        Math.min(along - 1, doorAlong + DOOR_CLEARANCE) });
            } else if (doorAlong < 0) {
                ranges.add(new int[]{ 0, Math.min(along - 1, BAY_WIDTH - 1) });
            } else if (doorAlong >= along) {
                ranges.add(new int[]{ Math.max(0, along - BAY_WIDTH), along - 1 });
            }
        }
        return ranges;
    }

    /** The far end of the first approach a bay here would block, or -1 if it blocks none. */
    private static int blockedUntil(List<int[]> approaches, int from, int to) {
        int furthest = -1;
        for (int[] approach : approaches) {
            if (from <= approach[1] && to >= approach[0]) {
                furthest = Math.max(furthest, approach[1]);
            }
        }
        return furthest;
    }

    private void place(CompartmentFloor floor, boolean lengthwise,
                       int along, int across, String id) {
        floor.place(id, lengthwise ? along : across, lengthwise ? across : along);
    }

    /** Place a fixture that is also somewhere with work at it. */
    private void place(CompartmentFloor floor, boolean lengthwise,
                       int along, int across, String id, Affordance affordance) {
        if (affordance == null) {
            place(floor, lengthwise, along, across, id);
            return;
        }
        floor.place(id, lengthwise ? along : across, lengthwise ? across : along, affordance);
    }

    private void reserve(CompartmentFloor floor, boolean lengthwise,
                         int along, int across, int alongSpan, int acrossSpan) {
        floor.reserveLane(
                lengthwise ? along : across,
                lengthwise ? across : along,
                lengthwise ? alongSpan : acrossSpan,
                lengthwise ? acrossSpan : alongSpan);
    }

    private void mark(CompartmentFloor floor, boolean lengthwise,
                      int along, int across, int alongSpan, int acrossSpan) {
        floor.markGround(
                lengthwise ? along : across,
                lengthwise ? across : along,
                lengthwise ? alongSpan : acrossSpan,
                lengthwise ? acrossSpan : alongSpan,
                GroundKind.STRIPED);
    }
}
