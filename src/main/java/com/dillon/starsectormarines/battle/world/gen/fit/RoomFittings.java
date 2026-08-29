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

        // Tables with their seating. A mess is chairs or it is a hall — and the
        // job is at a chair rather than at the table, because a table is where
        // four people sit and a seat is where one of them does.
        register(new AisleFitting(RoomPurpose.MESS_HALL, FixtureGroup.of(
                "doodad.office-conference-table", 3, 2,
                new Satellite("doodad.chair-south-green", 0, 1, Affordance.MESS),
                new Satellite("doodad.chair-south-yellow", 2, 1, Affordance.MESS))));

        // Racks against the bulkhead with the ready crates that get drawn from.
        // The counter is the crate rather than the rack: a weapon is handed over
        // where it is broken out, and the rack behind it is stock.
        register(new AisleFitting(RoomPurpose.ARMORY, FixtureGroup.of(
                "doodad.shelf-1", 2, 2,
                new Satellite("doodad.crate", 1, 1, Affordance.ISSUE))));

        // Stores are stacked, not shelved: pallets and drums, packed close.
        // Stowage is work between two points rather than at one, so a hold
        // publishes it at the stacks that get broken down and built back up.
        register(new AisleFitting(RoomPurpose.STOCKROOM, FixtureGroup.working(
                "doodad.industrial-crate-stack", 2, 2, Affordance.STOW,
                new Satellite("doodad.industrial-drum-cluster", 1, 1))));

        register(new AisleFitting(RoomPurpose.LOADING_BAY, FixtureGroup.working(
                "doodad.industrial-pallet-stack", 2, 2, Affordance.STOW,
                new Satellite("doodad.box", 1, 1))));

        // Machinery is a hierarchy, not a repeat: heavy plant against the
        // outboard bulkhead, the auxiliaries that serve it ranked inboard,
        // pipework threaded between them, a board somebody stands a watch at,
        // and a standing list of defects. See MachinerySpaceFitting. Ranked as
        // one machine repeated down both sides, a drive room read as shelving.
        register(MachinerySpaceFitting.driveRoom());
        register(MachinerySpaceFitting.auxiliaryPlant());

        register(new AisleFitting(RoomPurpose.PARTS_CAGE, FixtureGroup.working(
                "doodad.shelf-2", 2, 2, Affordance.STOW,
                new Satellite("doodad.industrial-scrap-pile", 1, 1))));

        // The console, not the racks. A server room is somewhere a reading is
        // taken; the racks are what it is taken from.
        register(new AisleFitting(RoomPurpose.SERVER_ROOM, FixtureGroup.of(
                "doodad.office-server-rack", 2, 2,
                new Satellite("doodad.industrial-control-console", 1, 1,
                        Affordance.READOUT))));

        // A ward bed is work rather than rest: it is checked and made up whether
        // or not anybody is in it, and a bunk is somewhere else entirely.
        register(new AisleFitting(RoomPurpose.PATIENT_WARD, FixtureGroup.working(
                "doodad.residential-bed-h", 2, 2, Affordance.TREAT,
                new Satellite("doodad.chest-2", 1, 1))));

        register(new AisleFitting(RoomPurpose.WASHROOM, FixtureGroup.working(
                "doodad.box", 1, 2, Affordance.WASH)));

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

        // Islands, not ranks: seating grouped round tables with deck between the
        // groups, and one motif never repeated twice running. See
        // LoungeFitting. Sofas ranked down both bulkheads seat the same
        // complement and read as a waiting room, which is the one thing a room
        // for choosing to be in must not read as.

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
