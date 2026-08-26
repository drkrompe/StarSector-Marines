package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * A mech bay as a row of gantry bays with a fab shop behind them.
 *
 * <p>A vehicle bay is not one big room with gear round the edge — it is
 * <b>several bays</b>, each sized for one machine, with service access between
 * them and a workshop strip along the back. That structure is what makes the
 * room read as somewhere vehicles are worked on rather than somewhere they are
 * parked, and it is what the hand-authored Mech Lab got right.
 *
 * <p>The bay module is the unit. {@link #BAY_WIDTH} by {@link #BAY_DEPTH} cells,
 * with the middle kept clear for the machine — which may be any size, so the
 * clearance is reserved rather than filled — and the flanking columns carrying
 * the tools a technician works from. A bay a walker cannot fit into, or that a
 * technician cannot get around, is not a bay.
 *
 * <p>Capacity is the bay count, so a longer compartment services more machines.
 * Nothing records that separately.
 */
public final class VehicleBayFitting implements RoomFitting {

    /** Cells across one gantry bay: the machine plus a working column each side. */
    public static final int BAY_WIDTH = 5;
    /** Cells along one gantry bay, bow to stern of the machine standing in it. */
    public static final int BAY_DEPTH = 7;
    /** Service access between one bay and the next. Wide enough to bring a part through. */
    private static final int BAY_GAP = 4;
    /** Clear deck between the gantry line and the shop strip behind it. */
    private static final int SERVICE_LANE = 2;
    /** Cells kept clear either side of a door, so a machine can be driven through it. */
    private static final int DOOR_CLEARANCE = 1;

    /** Tools a technician works a machine from, on the columns flanking each bay. */
    private static final String[] BAY_TOOLS = {
            "doodad.industrial-machine-tool",
            "doodad.industrial-cable-reel",
            "doodad.industrial-control-console",
            "doodad.industrial-drum-cluster" };

    /** The fab shop behind the gantry line: benches, stock, and the console that runs it. */
    private static final String[] SHOP = {
            "doodad.office-workstation-bank",
            "doodad.industrial-machine-tool",
            "doodad.industrial-pallet-stack",
            "doodad.military-command-console",
            "doodad.industrial-fluid-tank",
            "doodad.industrial-crate-stack" };

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
        // leave it, and the run of deck in front of a door is the one part of a
        // vehicle bay that is never negotiable.
        List<int[]> approaches = approaches(floor, lengthwise, along, across);
        for (int[] approach : approaches) {
            reserve(floor, lengthwise, approach[0], 0, approach[1] - approach[0] + 1, across);
        }

        int bayDepth = Math.min(BAY_DEPTH, Math.max(3, across - SERVICE_LANE - 1));
        int pitch = BAY_WIDTH + BAY_GAP;

        // Bays pack around the approaches rather than being skipped at them, so
        // a door in the middle of a long bay costs the room a gap and not half
        // its capacity.
        int cursor = 0;
        while (cursor + BAY_WIDTH <= along) {
            int blockedUntil = blockedUntil(approaches, cursor, cursor + BAY_WIDTH - 1);
            if (blockedUntil >= 0) {
                cursor = blockedUntil + 1;
                continue;
            }
            layBay(floor, lengthwise, cursor, bayDepth);
            cursor += pitch;
        }

        // Everything between the gantry line and the shop is the route a part
        // travels, so it is reserved before the shop is placed rather than
        // whatever happens to be left after.
        int shopStart = bayDepth + SERVICE_LANE;
        reserve(floor, lengthwise, 0, bayDepth, along, SERVICE_LANE);
        if (shopStart < across) {
            layShop(floor, lengthwise, along, shopStart, across);
        }
    }

    /**
     * The runs of deck that have to stay clear because a door opens onto them,
     * as inclusive ranges along the compartment.
     *
     * <p>A door in a side bulkhead needs the deck in front of it clear all the
     * way across, so a machine can be driven out rather than shuffled around a
     * gantry. A door in an end bulkhead needs the end of the room instead.
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

    /**
     * One gantry bay: clearance down the middle for the machine, tools on the
     * columns either side of it.
     */
    private void layBay(CompartmentFloor floor, boolean lengthwise, int origin, int depth) {
        // The bay is marked on the deck, so it reads as a bay standing empty
        // rather than as a gap between tools.
        if (lengthwise) {
            floor.markGround(origin, 0, BAY_WIDTH, depth, GroundKind.STRIPED);
        } else {
            floor.markGround(0, origin, depth, BAY_WIDTH, GroundKind.STRIPED);
        }
        reserve(floor, lengthwise, origin + 1, 0, BAY_WIDTH - 2, depth);
        for (int step = 0; step + 1 < depth; step += 2) {
            String tool = BAY_TOOLS[(step / 2) % BAY_TOOLS.length];
            place(floor, lengthwise, origin, step, tool);
            place(floor, lengthwise, origin + BAY_WIDTH - 1, step,
                    BAY_TOOLS[(step / 2 + 2) % BAY_TOOLS.length]);
        }
    }

    /** The workshop strip along the back bulkhead. */
    private void layShop(CompartmentFloor floor, boolean lengthwise,
                         int along, int from, int across) {
        int index = 0;
        for (int offset = 0; offset + 2 <= along; offset += 3) {
            place(floor, lengthwise, offset, across - 2, SHOP[index++ % SHOP.length]);
            if (across - from >= 4) {
                place(floor, lengthwise, offset, from, SHOP[index++ % SHOP.length]);
            }
        }
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
}
