package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.AisleFitting.FixtureGroup;
import com.dillon.starsectormarines.battle.world.gen.fit.AisleFitting.FixtureGroup.Satellite;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.EnumMap;
import java.util.Map;

/**
 * The authored fittings, by the purpose each one furnishes.
 *
 * <p>Every fixture here is an id already in the tile registry. A theme that
 * wants a prop the registry does not have is asking for art, which is a
 * different job from arranging a room, and pretending otherwise produces rooms
 * furnished with whatever happened to exist under a hopeful name.
 *
 * <p>A purpose with no fitting is left bare rather than filled with something
 * generic. An empty compartment is honest about not being authored yet; one
 * scattered with crates looks finished and is not.
 */
public final class RoomFittings {

    private RoomFittings() {}

    private static final Map<RoomPurpose, RoomFitting> BY_PURPOSE = new EnumMap<>(RoomPurpose.class);

    static {
        // Racks athwart a fore-and-aft passage, entered from one side: see
        // BerthingFitting. Ranked along the bulkhead instead, a berth held half
        // as many hands in the same floor.
        register(new BerthingFitting(RoomPurpose.BARRACKS, "doodad.chest-1"));

        // The ship's own hands berth the same way her passengers do. Same
        // arrangement, different room, because a bunk belongs to somebody.
        register(new BerthingFitting(RoomPurpose.CREW_QUARTERS, "doodad.chest-2"));

        // A galley, a counter, and the floor past it: see MessHallFitting. This
        // is the one room aboard that is a workplace and an amenity at once, and
        // ranked tables could only express the second — a hall where the whole
        // ship ate three meals a day that nobody made.
        register(new MessHallFitting());

        // A counter with a workshop behind it, not a stockroom with guns in it:
        // see ArmoryFitting. The counter is the arrangement — it divides the
        // room into the apron anybody queues on and the secure floor only the
        // armourer goes behind, where the racks, the ready-use lockers, the
        // bench a weapon is actually mended at and the ammunition kept apart
        // from the arms all live. Ranked like any other room it stamped ISSUE on
        // every length of shelf, which is six armourers in a room that has one.
        register(new ArmoryFitting());

        // Racked and inventoried rather than stacked: rack runs against both
        // long bulkheads with a gangway between them, stock in known places at
        // either end of a run, a tally taken along the way and a damaged
        // consignment standing against it. See StockroomFitting.
        register(new StockroomFitting());

        // A working floor arranged around one hatch, not a second stockroom:
        // marshalling floor kept clear down the middle to break a load down on,
        // outbound pallets staged along one flank, and the dispatch desk,
        // loader and conveyor working the hatch end of the other. See
        // LoadingBayFitting. Three holds fitted alike are one hold three times.
        register(new LoadingBayFitting());

        // Machinery is a hierarchy, not a repeat: heavy plant against the
        // outboard bulkhead, the auxiliaries that serve it ranked inboard,
        // pipework threaded between them, a board somebody stands a watch at,
        // and a standing list of defects. See MachinerySpaceFitting. Ranked as
        // one machine repeated down both sides, a drive room read as shelving.
        register(MachinerySpaceFitting.driveRoom());
        register(MachinerySpaceFitting.auxiliaryPlant());

        // A counter behind a wire front rather than shelves down both
        // bulkheads: the issue side is somewhere to stand and be served, the
        // stock side racks its bins dense, and the one gate between them falls
        // out of where the gangway crosses rather than being cut for it. See
        // PartsCageFitting.
        register(new PartsCageFitting());

        // The console, not the racks. A server room is somewhere a reading is
        // taken; the racks are what it is taken from.
        //
        // The group is one rank deep rather than two, because that is how deep
        // it actually reaches. A declared depth the satellite never occupies is
        // not slack: at this room's five-by-four it tripped the aisle fitting's
        // one-rank-beats-none fallback and left half the floor bare.
        register(new AisleFitting(RoomPurpose.SERVER_ROOM, FixtureGroup.of(
                "doodad.office-server-rack", 2, 1,
                new Satellite("doodad.industrial-control-console", 1, 0,
                        Affordance.READOUT))));

        // A ward plus the things that make it clinical: see SickBayFitting.
        // Beds ranked with their own monitors and a stretcher's width of clear
        // floor beside each, a treatment station and a secured dispensary that
        // are not beds, and the desk the ward is actually written up at. Ranked
        // as one domestic bed repeated, it read as a dormitory — and a ward bed
        // is work rather than rest, which nothing in that arrangement said.
        register(new SickBayFitting());

        // Basins against one bulkhead, stalls against the other, the middle
        // left to walk through: see WashroomFitting. This is the most used room
        // on the ship — everybody aboard, several times a watch — and it was
        // furnished with a single crate for the whole complement to queue at.
        register(new WashroomFitting());

        // A bridge is a plot with the watch ringed round it, facing inward over
        // their own boards: see BridgeFitting. Ranked in aisles it read as a
        // warehouse of identical consoles, because ranks are what a warehouse
        // is made of.
        register(new BridgeFitting());

        // Furnished and deliberately publishing nothing. A briefing is an event
        // rather than a watch, and giving these chairs a job would station
        // somebody in them permanently — a ship whose officers spend their lives
        // in the briefing room, which is a population invented out of furniture.
        // It is the same mistake as making every prop a work point, arrived at
        // from the other end.
        register(new AisleFitting(RoomPurpose.CONFERENCE_ROOM, FixtureGroup.of(
                "doodad.office-conference-table", 3, 2,
                new Satellite("doodad.chair-south-green", 0, 1),
                new Satellite("doodad.chair-south-yellow", 2, 1))));

        // Rooms whose point is the empty middle. Gear against the bulkheads,
        // deck clear for the machine or the lane.
        // A mech bay is bays, not one room: see VehicleBayFitting.
        register(new VehicleBayFitting());

        // Four worked bulkhead runs around a clear deck: see BoatBayFitting. The
        // clear middle is a constraint on the room rather than the room — a boat
        // is moved through it and troops form up on it — and fitting the bay as
        // though the constraint were the design made it a hold with a hole in
        // the middle, publishing stowage and nothing else.
        register(new BoatBayFitting());

        // Islands, not ranks: seating grouped round tables with deck between the
        // groups, and one motif never repeated twice running. See
        // LoungeFitting. Sofas ranked down both bulkheads seat the same
        // complement and read as a waiting room, which is the one thing a room
        // for choosing to be in must not read as.
        register(new LoungeFitting());

        // Gear against the bulkheads and matting on the deck between them, with
        // the training stations out on the floor facing inboard: see
        // GymFitting. What the room is for is the middle, so the middle is
        // reserved before anything is placed.
        register(new GymFitting());

        // A range is a firing line looking down lanes, not gear round a clear
        // middle: see FiringRangeFitting. The perimeter treatment kept the deck
        // clear, which looked right and was clear in no particular direction.
        register(new FiringRangeFitting());
    }

    private static void register(RoomFitting fitting) {
        BY_PURPOSE.put(fitting.purpose(), fitting);
    }

    /** The fitting for this purpose, or null where none is authored yet. */
    public static RoomFitting forPurpose(RoomPurpose purpose) {
        return purpose == null ? null : BY_PURPOSE.get(purpose);
    }
}
