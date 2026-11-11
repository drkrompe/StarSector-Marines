package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
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

    /** The assembly line's held ground, beside the works ({@link AssemblyLine}). */
    public static final String LINE = "assembly-line";
    /**
     * Cells of ground the line holds out from the works: a craft's width and its work strip, the lanes either side,
     * and room for the line to sit back from the works' rear road. UNTUNED.
     */
    static final int LINE_DEPTH = 36;
    /** Cells the line's ground keeps off the map's edge. */
    static final int LINE_MARGIN = 2;

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

    /**
     * The assembly line's ground beside the works, at the deep end, as a port's apron is: from the works' reach out
     * {@link #LINE_DEPTH} cells, across the map as the apron's ground runs, stopped short of a storm bank's ground.
     */
    @Override public void sitePieces(SiteFrame frame, List<Piece> out) {
        Precinct works = frame.works();
        if (works == null) return;
        int reach = RaidSiteTemplate.worksReach(worksFit(), frame.security(), frame.width(), frame.height());
        Site ground = PortWorks.apronSite(works.seedX(), works.seedY(), reach, frame.mirrorX(), frame.mirrorY(),
                frame.width(), frame.height());
        Site line = lineSite(ground, frame.mirrorX(), works.seedY(), reach, frame.height());
        out.add(frame.bankWind() != null
                ? RaidSiteTemplate.clearOfBank(line, frame.bankWind(), frame.width(), frame.height()) : line);
    }

    /**
     * The line's ground: the {@link #LINE_DEPTH} cells of the apron's ground nearest the works, and the works' whole
     * height ({@code reach} either side of its seed row), so the line runs the length of the halls it is fed from.
     */
    static Site lineSite(Site ground, boolean mirrorX, int seedY, int reach, int height) {
        int x0 = mirrorX ? Math.max(ground.x0(), ground.x1() - LINE_DEPTH + 1) : ground.x0();
        int x1 = mirrorX ? ground.x1() : Math.min(ground.x1(), ground.x0() + LINE_DEPTH - 1);
        int y0 = Math.max(LINE_MARGIN, seedY - reach), y1 = Math.min(height - 1 - LINE_MARGIN, seedY + reach);
        return new Site(LINE, x0, y0, x1, Math.max(y0, y1), ground.purpose(), true);
    }

    /**
     * The yard dressed for the work it does, and the assembly line laid on its ground ({@link FabricationYard}),
     * brought in against the works as a port's apron is: to the works' rear road where the works packed tighter than
     * its reach, so the line runs along the halls it is fed from.
     */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        int[] line = null;
        if (plan.layout().find(LINE) instanceof Site ground) {
            int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
            boolean deepEast = (ground.x0() + ground.x1()) / 2 > works.seedX();
            int edge = PortWorks.clearEdge(PortWorks.claimEdge(claim, plan.precincts().indexOf(works), deepEast, ground),
                    deepEast, ground, (x, y) -> c.topology.getBuildingId(x, y) != 0);
            int near = deepEast ? Math.min(ground.x0(), edge + 1 + PortWorks.REAR_ROAD)
                    : Math.max(ground.x1(), edge - 1 - PortWorks.REAR_ROAD);
            line = deepEast ? new int[]{near, ground.y0(), near + LINE_DEPTH - 1, ground.y1()}
                    : new int[]{near - LINE_DEPTH + 1, ground.y0(), near, ground.y1()};
        }
        FabricationYard.dress(c, works.name(), line);
    }
}
