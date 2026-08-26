package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

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

        clearDoorApproaches(floor);
        int bayDepth = Math.min(BAY_DEPTH, Math.max(3, across - SERVICE_LANE - 1));
        int pitch = BAY_WIDTH + BAY_GAP;

        for (int bay = 0; bay + BAY_WIDTH <= along; bay += pitch) {
            layBay(floor, lengthwise, bay, bayDepth);
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

    /** Doors keep their approach, so a machine can actually be driven out. */
    private static void clearDoorApproaches(CompartmentFloor floor) {
        for (DeckGraph.Compartment.Door door : floor.localDoors()) {
            floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
        }
    }
}
