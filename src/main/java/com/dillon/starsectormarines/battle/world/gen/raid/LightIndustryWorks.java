package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;
import java.util.Set;

/**
 * Light industry's site: the planet's central fabrication works ({@code raid-site-templates.md}, The planet's
 * chokepoint), laid out as the process it runs, in a dense industrial quarter. Stock and parts come in on the feeder
 * lines from the planet's shops to the receiving dock and the parts stores at the rear, by the feeders' gates. They go
 * out to the fabrication shops in the yard, one line per machine; each shop's output goes to the assembly hall, then
 * to the finishing shop, where the goods are painted, cured and packed. They leave from the dispatch sheds at the
 * frontage, by the gate the haul road leaves from. The canteen stands in the yard among the shops, where the shifts
 * eat.
 *
 * <p>Each step is a building of its own (the program is {@link FortressProgram#detached() detached}) and its own room
 * purpose, so the finished map can tell the steps apart and {@link FabricationYard} can dress the yard between them:
 * the overhead parts line, the feeders' trucks, the stock at each hall's doors, the shifts' paths.
 */
public final class LightIndustryWorks implements RaidIndustry {

    /** Where the feeders' trucks unload the planet's stock and parts: a long dock with bays along its yard face. */
    public static final RoomPurpose RECEIVING = RoomPurpose.LOADING_BAY;
    /** The parts stores: racks of bar stock, fasteners and bought-in parts the shops draw from. */
    public static final RoomPurpose STORES = RoomPurpose.STOCKROOM;
    /** A fabrication shop: one line of presses, mills and fabricators, the floor one charge wrecks. */
    public static final RoomPurpose SHOP = RoomPurpose.PARTS_CAGE;
    /** The assembly hall the shops' output meets in: the works' one long line. */
    public static final RoomPurpose ASSEMBLY = RoomPurpose.PRODUCTION_FLOOR;
    /** Finishing and packing: the paint and curing booths, then the packing benches. */
    public static final RoomPurpose FINISHING = RoomPurpose.ENGINE_ROOM;
    /** A dispatch shed: trucks back in and are loaded for the haul road. */
    public static final RoomPurpose DISPATCH = RoomPurpose.VEHICLE_BAY;
    /** The canteen the shifts eat in. */
    public static final RoomPurpose CANTEEN = RoomPurpose.MESS_HALL;

    /** The steps the work passes through, in order: the parts line links each to the next. */
    public static final List<RoomPurpose> PROCESS = List.of(RECEIVING, STORES, SHOP, ASSEMBLY, FINISHING, DISPATCH);

    private static final RoomShape RECEIVING_DOCK = RoomShape.rectangle(14, 8);
    private static final RoomShape PARTS_STORES = RoomShape.rectangle(12, 7);
    private static final RoomShape SHOP_FLOOR = RoomShape.rectangle(12, 8);
    private static final RoomShape ASSEMBLY_HALL = RoomShape.rectangle(18, 9);
    private static final RoomShape FINISHING_SHOP = RoomShape.rectangle(12, 8);
    private static final RoomShape DISPATCH_SHED = RoomShape.rectangle(12, 7);
    private static final RoomShape CANTEEN_HALL = RoomShape.rectangle(10, 7);

    /**
     * Dispatch sheds: two, so a truck loads at one while the next backs in. A planet's output does not leave through
     * one door. UNTUNED.
     */
    public static final int DISPATCH_SHEDS = 2;

    /** Yard kept per shop: the parts line's runs, the work in progress staged between halls, the lanes. UNTUNED. */
    static final int YARD_PER_SHOP = 120;
    /** Yard kept whatever the shops: the trucks' staging at the dock and the loading apron at dispatch. UNTUNED. */
    static final int STAGING_YARD = 240;

    @Override public String key() { return "light-industry"; }

    @Override public Set<String> industryIds() { return Set.of("lightindustry"); }

    /** A hall for each step of the process, with the staging and lanes between them: a mine's share of the map. */
    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    /**
     * The works with one fabrication shop per machine: the receiving dock and the parts stores at the rear, the
     * shops, the assembly hall and the canteen in the yard, the finishing shop, the dispatch sheds and the guard posts
     * at the frontage.
     *
     * @param shops fabrication shops, one per machine the raid wrecks
     */
    @Override public FortressProgram works(int shops) {
        if (shops < 1) throw new IllegalArgumentException("a fabrication works has at least one shop");
        return new FortressProgram(List.of(
                new FortressBuilding(RECEIVING, RECEIVING_DOCK, Ward.REAR, 1),
                new FortressBuilding(STORES, PARTS_STORES, Ward.REAR, 1),
                new FortressBuilding(SHOP, SHOP_FLOOR, Ward.YARD, shops),
                new FortressBuilding(ASSEMBLY, ASSEMBLY_HALL, Ward.YARD, 1),
                new FortressBuilding(CANTEEN, CANTEEN_HALL, Ward.YARD, 1),
                new FortressBuilding(FINISHING, FINISHING_SHOP, Ward.FRONTAGE, 1),
                new FortressBuilding(DISPATCH, DISPATCH_SHED, Ward.FRONTAGE, DISPATCH_SHEDS),
                RaidWorksParts.guardPosts(2)),
                0, shops * YARD_PER_SHOP + STAGING_YARD, true);
    }

    /** The yard dressed for the work it does ({@link FabricationYard}). */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        FabricationYard.dress(c, works.name());
    }
}
