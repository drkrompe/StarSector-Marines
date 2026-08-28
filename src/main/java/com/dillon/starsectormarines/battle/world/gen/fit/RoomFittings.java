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

        // Machinery: the plant and the pipework running off it. The work is at
        // the plant — a pipe run is what the plant needs, not a second machine.
        register(new AisleFitting(RoomPurpose.PRODUCTION_FLOOR, FixtureGroup.working(
                "doodad.industrial-generator", 3, 3, Affordance.TEND,
                new Satellite("doodad.industrial-pipe-bundle", 2, 1))));

        register(new AisleFitting(RoomPurpose.ENGINE_ROOM, FixtureGroup.working(
                "doodad.industrial-fluid-tank", 3, 3, Affordance.TEND,
                new Satellite("doodad.industrial-cable-reel", 2, 2),
                new Satellite("doodad.industrial-pipe-bundle", 0, 2))));

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

        // The bridge is consoles around a plot, not ranks of furniture, but the
        // aisle arrangement still reads correctly at this size. The console is
        // the watch station; the plot is what the watch stands around.
        register(new AisleFitting(RoomPurpose.CONTROL_ROOM, FixtureGroup.working(
                "doodad.military-command-console", 2, 2, Affordance.WATCH,
                new Satellite("doodad.military-tactical-table", 1, 1))));

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

        // A boat bay is worked as a hold is: the deck is kept clear for the
        // boat, and the stores that go up and down with it are along the sides.
        register(new PerimeterFitting(RoomPurpose.HANGAR, FixtureGroup.working(
                "doodad.industrial-crate-stack", 2, 2, Affordance.STOW,
                new Satellite("doodad.industrial-cable-reel", 1, 1))));

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
