package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * The authored rooms of every fort aspect's suite, by function (see {@code forts.md}). Each aspect is housed in a room or
 * a small suite whose main room keeps the footprint its roof art is registered to; ante-rooms stand in front of it, so
 * a squad takes an aspect by fighting through rooms rather than by reaching a point.
 *
 * <ul>
 *   <li><b>Fire control</b> is an operations hall of console banks (the crew stations) leading to a plotting vault that
 *       holds the fire-control computer against its back wall between two banks of seated plotters.</li>
 *   <li>The <b>magazine</b> is a store of ammunition racks around its core, entered through a loading bay.</li>
 *   <li>The <b>power</b> hall holds the plant: fuel tanks, generator sets and a switchgear bank around the reactor.</li>
 *   <li>The <b>anti-air</b> aspect is a small hardened control room for its plotting console.</li>
 *   <li>The shield fort's <b>control room</b> is the same operations hall and vault; its generator stands in the keep.</li>
 * </ul>
 *
 * <p>Pure geometry in the footprint frame: the ox and oy given are where the fort's main works begin.
 */
final class FortRooms {

    private FortRooms() {}

    /** Width and height of the command vault and of the anti-air control room's floor. */
    static final int COMMAND_W = 7, COMMAND_H = 5;

    /**
     * The seven-by-five command room: the aspect's core against the back wall (opposite the south-facing door) between a
     * bank of seated crew down each side wall, and the front two rows kept clear as circulation.
     *
     * @param doorSouth whether the plan's own door is on the south side; false turns the room to a north door
     * @param crew      how many of the seats the garrison stands at (the first seats of the front pockets)
     */
    static FortRoom commandRoom(FortAspect aspect, boolean main, int x, int y, boolean doorSouth, List<Doorway> doors, int crew) {
        FortRoomKit kit = new FortRoomKit(COMMAND_H, !doorSouth)
                .lane(0, 3, COMMAND_W, 2)
                .bank(true, 0, 0, 0, 2)
                .bank(false, COMMAND_W - 1, 0, COMMAND_W - 1, 2);
        List<int[]> stands = new ArrayList<>();
        for (int row = 0; row <= 2; row++) {
            stands.add(kit.cell(1, row));
            stands.add(kit.cell(COMMAND_W - 2, row));
        }
        // A stool at each seat the garrison actually sits at.
        for (int i = 0; i < Math.min(crew, stands.size()); i++) {
            int row = i / 2;
            kit.fixture(i % 2 == 0 ? 1 : COMMAND_W - 2, row, FortRoomKit.STOOL);
        }
        int[] core = kit.cell(3, 1);
        return new FortRoom(aspect, main, RoomPurpose.CONTROL_ROOM, x, y, COMMAND_W, COMMAND_H, doors, kit.ops(),
                stands.subList(0, Math.min(crew, stands.size())),
                main ? core : null);
    }

    /** The five-by-five operations hall in front of a command vault: a bank of stations along the back and the west wall. */
    static FortRoom operationsHall(FortAspect aspect, int x, int y, List<Doorway> doors) {
        FortRoomKit kit = new FortRoomKit(5, false)
                .lane(2, 1, 1, 4).lane(1, 3, 4, 1)
                .bank(true, 0, 0, 4, 0)
                .bank(true, 0, 2, 0, 4)
                .fixture(1, 1, FortRoomKit.STOOL).fixture(3, 1, FortRoomKit.STOOL).fixture(1, 2, FortRoomKit.STOOL);
        return new FortRoom(aspect, false, RoomPurpose.CONTROL_ROOM, x, y, 5, 5, doors, kit.ops(),
                List.of(new int[]{1, 1}, new int[]{3, 1}, new int[]{1, 2}), null);
    }

    /** A three-by-five operations hall: one bank of stations along its back wall, an aisle from each door. */
    static FortRoom smallHall(FortAspect aspect, int x, int y, List<Doorway> doors) {
        FortRoomKit kit = new FortRoomKit(5, false)
                .lane(1, 1, 1, 4).lane(0, 3, 2, 1)
                .bank(true, 0, 0, 2, 0)
                .fixture(0, 1, FortRoomKit.STOOL).fixture(2, 1, FortRoomKit.STOOL);
        return new FortRoom(aspect, false, RoomPurpose.CONTROL_ROOM, x, y, 3, 5, doors, kit.ops(),
                List.of(new int[]{0, 1}, new int[]{2, 1}), null);
    }

    /** The eight-by-six ammunition store: racks down both side walls, the core against the back wall. */
    static FortRoom store(int x, int y, List<Doorway> doors) {
        FortRoomKit kit = new FortRoomKit(6, false)
                .lane(1, 3, 6, 3)
                .run(0, 0, 0, 5, FortRoomKit.CRATES)
                .run(7, 0, 7, 5, FortRoomKit.CRATES);
        return new FortRoom(FortAspect.MAGAZINE, true, RoomPurpose.ARMORY, x, y, 8, 6, doors, kit.ops(),
                List.of(), new int[]{4, 1});
    }

    /** The loading bay in front of the store: pallets along the walls, a wide door onto the road. */
    static FortRoom loadingBay(int x, int y, List<Doorway> doors) {
        FortRoomKit kit = new FortRoomKit(3, false)
                .lane(2, 0, 3, 3)
                .run(0, 0, 0, 1, FortRoomKit.PALLETS)
                .run(7, 0, 7, 2, FortRoomKit.PALLETS)
                .fixture(6, 2, FortRoomKit.PALLETS);
        return new FortRoom(FortAspect.MAGAZINE, false, RoomPurpose.LOADING_BAY, x, y, 8, 3, doors, kit.ops(),
                List.of(), null);
    }

    /**
     * The eleven-by-five generator hall. The plan is written for a south door, with the reactor against the north wall
     * between fuel tanks and generator sets on the west and a generator set and a switchgear bank on the east.
     */
    static FortRoom powerHall(int x, int y, boolean doorSouth, List<Doorway> doors) {
        FortRoomKit kit = new FortRoomKit(5, !doorSouth)
                .lane(0, 3, 11, 2)
                .run(0, 0, 0, 2, FortRoomKit.TANK)
                .run(1, 0, 2, 0, FortRoomKit.GENERATOR)
                .run(1, 1, 2, 1, FortRoomKit.GENERATOR)
                .run(7, 0, 8, 0, FortRoomKit.GENERATOR)
                .run(7, 1, 8, 1, FortRoomKit.GENERATOR)
                .bank(false, 10, 0, 10, 2)
                .fixture(9, 0, FortRoomKit.STOOL).fixture(9, 2, FortRoomKit.STOOL);
        return new FortRoom(FortAspect.POWER, true, RoomPurpose.ENGINE_ROOM, x, y, 11, 5, doors, kit.ops(),
                List.of(kit.cell(9, 0), kit.cell(9, 2)), kit.cell(5, 1));
    }

    /** The battery fort's rooms, in the footprint frame whose main works begin at (ox, oy). Main rooms lead each aspect. */
    static List<FortRoom> battery(int ox, int oy) {
        List<FortRoom> rooms = new ArrayList<>();
        rooms.add(commandRoom(FortAspect.FIRE_CONTROL, true, ox + 9, oy + 7, true, List.of(new Doorway(3, 5), new Doorway(-1, 3)), 0));
        rooms.add(operationsHall(FortAspect.FIRE_CONTROL, ox + 3, oy + 7, List.of(new Doorway(2, 5), new Doorway(5, 3))));
        rooms.add(store(ox + 36, oy + 5, List.of(new Doorway(3, 6))));
        rooms.add(loadingBay(ox + 36, oy + 13, List.of(new Doorway(3, -1), new Doorway(2, 3), new Doorway(3, 3), new Doorway(4, 3))));
        rooms.add(powerHall(ox + 8, oy + 27, false, List.of(new Doorway(3, -1))));
        return List.copyOf(rooms);
    }

    /** The shield fort's rooms; its generator stands in the keep and is not a room of this suite. */
    static List<FortRoom> shield(int ox, int oy) {
        List<FortRoom> rooms = new ArrayList<>();
        rooms.add(powerHall(ox + 13, oy + 4, true, List.of(new Doorway(5, 5))));
        rooms.add(commandRoom(FortAspect.CONTROL_ROOM, true, ox + 28, oy + 7, true, List.of(new Doorway(3, 5), new Doorway(7, 3)), 0));
        rooms.add(smallHall(FortAspect.CONTROL_ROOM, ox + 37, oy + 7, List.of(new Doorway(-1, 3), new Doorway(1, 5))));
        return List.copyOf(rooms);
    }
}
