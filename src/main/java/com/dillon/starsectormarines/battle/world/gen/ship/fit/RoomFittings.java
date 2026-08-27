package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.ship.fit.AisleFitting.FixtureGroup;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.AisleFitting.FixtureGroup.Satellite;
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
        // Bunks ranked either side of the aisle, each with the locker that turns
        // a bed into somebody's billet.
        register(new AisleFitting(RoomPurpose.BARRACKS, FixtureGroup.of(
                "doodad.military-bunk", 2, 2,
                new Satellite("doodad.chest-1", 1, 0))));

        // The ship's own hands berth the same way her passengers do. Same
        // arrangement, different room, because a bunk belongs to somebody.
        register(new AisleFitting(RoomPurpose.CREW_QUARTERS, FixtureGroup.of(
                "doodad.military-bunk", 2, 2,
                new Satellite("doodad.chest-2", 1, 0))));

        // Tables with their seating. A mess is chairs or it is a hall.
        register(new AisleFitting(RoomPurpose.MESS_HALL, FixtureGroup.of(
                "doodad.office-conference-table", 3, 2,
                new Satellite("doodad.chair-south-green", 0, 1),
                new Satellite("doodad.chair-south-yellow", 2, 1))));

        // Racks against the bulkhead with the ready crates that get drawn from.
        register(new AisleFitting(RoomPurpose.ARMORY, FixtureGroup.of(
                "doodad.shelf-1", 2, 2,
                new Satellite("doodad.crate", 1, 1))));

        // Stores are stacked, not shelved: pallets and drums, packed close.
        register(new AisleFitting(RoomPurpose.STOCKROOM, FixtureGroup.of(
                "doodad.industrial-crate-stack", 2, 2,
                new Satellite("doodad.industrial-drum-cluster", 1, 1))));

        register(new AisleFitting(RoomPurpose.LOADING_BAY, FixtureGroup.of(
                "doodad.industrial-pallet-stack", 2, 2,
                new Satellite("doodad.box", 1, 1))));

        // Machinery: the plant and the pipework running off it.
        register(new AisleFitting(RoomPurpose.PRODUCTION_FLOOR, FixtureGroup.of(
                "doodad.industrial-generator", 3, 3,
                new Satellite("doodad.industrial-pipe-bundle", 2, 1))));

        register(new AisleFitting(RoomPurpose.ENGINE_ROOM, FixtureGroup.of(
                "doodad.industrial-fluid-tank", 3, 3,
                new Satellite("doodad.industrial-cable-reel", 2, 2),
                new Satellite("doodad.industrial-pipe-bundle", 0, 2))));

        register(new AisleFitting(RoomPurpose.PARTS_CAGE, FixtureGroup.of(
                "doodad.shelf-2", 2, 2,
                new Satellite("doodad.industrial-scrap-pile", 1, 1))));

        register(new AisleFitting(RoomPurpose.SERVER_ROOM, FixtureGroup.of(
                "doodad.office-server-rack", 2, 2,
                new Satellite("doodad.industrial-control-console", 1, 1))));

        register(new AisleFitting(RoomPurpose.PATIENT_WARD, FixtureGroup.of(
                "doodad.residential-bed-h", 2, 2,
                new Satellite("doodad.chest-2", 1, 1))));

        register(new AisleFitting(RoomPurpose.WASHROOM, FixtureGroup.of(
                "doodad.box", 1, 2)));

        // The bridge is consoles around a plot, not ranks of furniture, but the
        // aisle arrangement still reads correctly at this size.
        register(new AisleFitting(RoomPurpose.CONTROL_ROOM, FixtureGroup.of(
                "doodad.military-command-console", 2, 2,
                new Satellite("doodad.military-tactical-table", 1, 1))));

        register(new AisleFitting(RoomPurpose.CONFERENCE_ROOM, FixtureGroup.of(
                "doodad.office-conference-table", 3, 2,
                new Satellite("doodad.chair-south-green", 0, 1),
                new Satellite("doodad.chair-south-yellow", 2, 1))));

        // Rooms whose point is the empty middle. Gear against the bulkheads,
        // deck clear for the machine or the lane.
        // A mech bay is bays, not one room: see VehicleBayFitting.
        register(new VehicleBayFitting());

        register(new PerimeterFitting(RoomPurpose.HANGAR, FixtureGroup.of(
                "doodad.industrial-crate-stack", 2, 2,
                new Satellite("doodad.industrial-cable-reel", 1, 1))));

        register(new PerimeterFitting(RoomPurpose.FIRING_RANGE, FixtureGroup.of(
                "doodad.sandbag-straight-n", 2, 2,
                new Satellite("doodad.crate", 1, 1))));
    }

    private static void register(RoomFitting fitting) {
        BY_PURPOSE.put(fitting.purpose(), fitting);
    }

    /** The fitting for this purpose, or null where none is authored yet. */
    public static RoomFitting forPurpose(RoomPurpose purpose) {
        return purpose == null ? null : BY_PURPOSE.get(purpose);
    }
}
