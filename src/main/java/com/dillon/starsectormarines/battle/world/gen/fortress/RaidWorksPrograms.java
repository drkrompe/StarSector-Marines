package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * The works programs of the Raid sites after heavy industry's ({@code raid-mission.md}, Site templates): what each
 * industry's compound holds. Heavy industry's is {@link FortressProgram#works}. Each is {@link FortressProgram#detached()
 * detached}, so every machine stands as a building of its own, and its machines are its
 * {@link RoomPurpose#PARTS_CAGE parts cages}, which is how the raid finds them.
 */
public final class RaidWorksPrograms {

    /** A mine's winding house: the headframe and its hoist over a shaft, the floor one charge wrecks. */
    private static final RoomShape WINDING_HOUSE = RoomShape.rectangle(9, 9);
    /** The ore mill the shafts feed: one long crushing and sorting hall. */
    private static final RoomShape ORE_MILL = RoomShape.rectangle(16, 8);
    private static final RoomShape STORES = RoomShape.rectangle(12, 5);
    private static final RoomShape GUARD_POST = RoomShape.rectangle(4, 4);

    /**
     * Ore yard a mine keeps per shaft, on top of its packing slack: the stockpiles and haul ground each headframe
     * stands apart on, more than a works' yard because ore is heaped where it comes up. UNTUNED.
     */
    static final int MINE_YARD_PER_SHAFT = 260;

    /**
     * A refinery's still: a cracking column on its plinth. Square: cut corners let two columns touch diagonally,
     * which the detached packing does not count as a shared wall.
     */
    private static final RoomShape STILL = RoomShape.rectangle(10, 10);
    private static final RoomShape PUMP_HOUSE = RoomShape.rectangle(8, 6);
    private static final RoomShape CONTROL_HOUSE = RoomShape.rectangle(6, 5);
    /** Ground a refinery keeps per still: pipe runs and the fire gaps between columns. UNTUNED. */
    static final int REFINERY_YARD_PER_STILL = 320;

    /** A light-industry shop: a long, low production floor. */
    private static final RoomShape SHOP = RoomShape.rectangle(11, 7);
    private static final RoomShape LOADING_SHED = RoomShape.rectangle(14, 6);
    /** Yard a light-industry works keeps per shop: lanes and loading, closer set than a heavy works. UNTUNED. */
    static final int SHOPS_YARD_PER_SHOP = 150;

    /** A farm plant's silo: a bin on its pad, square for the same reason as a still. */
    private static final RoomShape SILO = RoomShape.rectangle(7, 7);
    private static final RoomShape PACKING_SHED = RoomShape.rectangle(16, 7);
    /** Yard a farm plant keeps per silo: the threshing floor and the trucks' turning ground. UNTUNED. */
    static final int FARM_YARD_PER_SILO = 240;

    /** A cargo terminal's shed: a bonded warehouse the lighters unload into. */
    private static final RoomShape CARGO_SHED = RoomShape.rectangle(14, 8);
    private static final RoomShape TOWER = RoomShape.rectangle(6, 6);
    private static final RoomShape FUEL_YARD = RoomShape.rectangle(8, 6);
    /** Apron a cargo terminal keeps per shed, for the lighters standing at it. UNTUNED. */
    static final int TERMINAL_APRON_PER_SHED = 360;

    /** A base's motor shed: where its vehicles stand and are worked on. */
    private static final RoomShape MOTOR_SHED = RoomShape.rectangle(14, 9);
    private static final RoomShape MAGAZINE = RoomShape.rectangle(9, 7);
    /** Vehicle park a motor pool keeps per shed. UNTUNED. */
    static final int MOTOR_POOL_PER_SHED = 170;

    private RaidWorksPrograms() { }

    /**
     * A refinery: a still per machine, the pump house that moves the stock between them, the control house that runs
     * them, and the posts at its gates.
     */
    public static FortressProgram refinery(int stills) {
        if (stills < 1) throw new IllegalArgumentException("a refinery has at least one still");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, STILL, Ward.YARD, stills),
                new FortressBuilding(RoomPurpose.ENGINE_ROOM, PUMP_HOUSE, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, CONTROL_HOUSE, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2)),
                0, stills * REFINERY_YARD_PER_STILL, true);
    }

    /** Light industry: a shop floor per machine, the loading shed they ship from, a stores and the posts. */
    public static FortressProgram shops(int shops) {
        if (shops < 1) throw new IllegalArgumentException("light industry has at least one shop");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, SHOP, Ward.YARD, shops),
                new FortressBuilding(RoomPurpose.LOADING_BAY, LOADING_SHED, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, STORES, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2)),
                0, shops * SHOPS_YARD_PER_SHOP, true);
    }

    /** A farm plant: a silo per machine, the packing shed the harvest goes through, a stores and one post. */
    public static FortressProgram farmPlant(int silos) {
        if (silos < 1) throw new IllegalArgumentException("a farm plant has at least one silo");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, SILO, Ward.YARD, silos),
                new FortressBuilding(RoomPurpose.PRODUCTION_FLOOR, PACKING_SHED, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, STORES, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 1)),
                0, silos * FARM_YARD_PER_SILO, true);
    }

    /**
     * A port's cargo terminal: a bonded shed per machine round the apron the lighters land on, the tower that
     * talks them down, the fuel yard and the posts.
     */
    public static FortressProgram cargoTerminal(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a cargo terminal has at least one shed");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, CARGO_SHED, Ward.YARD, sheds),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, TOWER, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.ENGINE_ROOM, FUEL_YARD, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2)),
                0, sheds * TERMINAL_APRON_PER_SHED, true);
    }

    /**
     * A base's motor pool and depot: a motor shed per machine (the vehicles and the shops that keep them running),
     * the magazine, and the posts. The base's barracks are the garrison beside it.
     */
    public static FortressProgram motorPool(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a motor pool has at least one shed");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, MOTOR_SHED, Ward.YARD, sheds),
                new FortressBuilding(RoomPurpose.ARMORY, MAGAZINE, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2)),
                0, sheds * MOTOR_POOL_PER_SHED, true);
    }

    /**
     * A mine's head works: one winding house per shaft, which are the machines a raid wrecks; the ore mill they
     * feed; a stores; and the posts its gates are watched from. No keep and no airfield: the garrison beside it
     * holds the market.
     *
     * @param shafts winding houses in the head works, one per machine
     */
    public static FortressProgram mine(int shafts) {
        if (shafts < 1) throw new IllegalArgumentException("a mine has at least one shaft");
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.PARTS_CAGE, WINDING_HOUSE, Ward.YARD, shafts),
                new FortressBuilding(RoomPurpose.ENGINE_ROOM, ORE_MILL, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, STORES, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2)),
                0, shafts * MINE_YARD_PER_SHAFT, true);
    }
}
