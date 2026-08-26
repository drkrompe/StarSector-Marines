package com.dillon.starsectormarines.battle.world.gen.ship.fit;

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

    /** Tools a technician works a machine from, on the columns flanking each bay. */
    private static final String[] BAY_TOOLS = {
            "doodad.industrial-machine-tool",
            "doodad.industrial-cable-reel",
            "doodad.industrial-control-console",
            "doodad.industrial-drum-cluster",
            "doodad.industrial-pipe-bundle",
            "doodad.industrial-scrap-pile" };

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
            layBay(floor, lengthwise, cursor, 0, bayDepth);
            if (facingRanks) {
                layBay(floor, lengthwise, cursor, across - bayDepth, bayDepth);
            }
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
     * One gantry bay: clearance down the middle for the machine, tools on the
     * columns either side of it, and the deck marked so the bay reads as a bay
     * standing empty rather than as a gap between tools.
     */
    private void layBay(CompartmentFloor floor, boolean lengthwise,
                        int origin, int band, int depth) {
        mark(floor, lengthwise, origin, band, BAY_WIDTH, depth);
        reserve(floor, lengthwise, origin + 1, band, BAY_WIDTH - 2, depth);
        for (int step = 0; step < depth; step += 2) {
            place(floor, lengthwise, origin, band + step,
                    BAY_TOOLS[(step / 2) % BAY_TOOLS.length]);
            place(floor, lengthwise, origin + BAY_WIDTH - 1, band + step,
                    BAY_TOOLS[(step / 2 + 3) % BAY_TOOLS.length]);
        }
    }

    /** The workshop at one end, worked densely because it is where the work happens. */
    private void layShop(CompartmentFloor floor, boolean lengthwise,
                         int from, int along, int across) {
        int index = 0;
        for (int offset = from; offset < along; offset += 2) {
            for (int depth = 0; depth < across; depth += 2) {
                place(floor, lengthwise, offset, depth, SHOP[index++ % SHOP.length]);
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
