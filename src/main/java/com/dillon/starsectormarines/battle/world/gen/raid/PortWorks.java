package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;
import java.util.Set;

/**
 * A port's site ({@code raid-site-templates.md}, The planet's chokepoint): the planet's only orbital lift, laid as the
 * cargo moves through it. The roads come in from the planet's towns (the haul road from the industrial quarter, the
 * feeder from the outer flank) to the terminal's gate, where the customs hall clears what arrives; cleared cargo goes
 * into the bonded sheds (the machines a raid wrecks, and the store a raid for valuables breaks into); from the sheds
 * it goes out of the terminal's rear gate to the container staging on the lighter apron, and from staging onto the
 * lighters on their pads at the deep edge, which lift it to orbit. The control tower stands at the terminal's rear,
 * over the apron, with the pump house that feeds the fuel main.
 *
 * <p>The apron is held ground beside the terminal at the deep end ({@link #APRON}), as a mine's pit is, and dressed
 * once the terminal is built ({@link PortApronDressing}): brought in to the terminal's rear wall, with a gate opened
 * through it; pads with the scorch of every lift, blast walls between them, the fuel farm and its main, the staging
 * rows under their crane, a lighter mid-turnaround, and haulers queued at the terminal's other gates. The company's
 * own lift lands at the other end of the map; the lighters are the defender's and the planet's traffic.
 */
public final class PortWorks implements RaidIndustry {

    /** The lighter apron: ground held beside the terminal at the deep end, read with {@code plan.layout().find}. */
    public static final String APRON = "apron";

    /** The customs hall at the gate the town roads come in by: where arriving cargo is cleared and bonded. */
    private static final RoomShape CUSTOMS_HALL = RoomShape.rectangle(14, 8);
    /** A bonded shed: cleared cargo held under seal until its lighter. */
    private static final RoomShape BONDED_SHED = RoomShape.rectangle(16, 9);
    /** The control tower, over the apron. */
    private static final RoomShape TOWER = RoomShape.rectangle(7, 7);
    /** The pump house that feeds the apron's fuel main from the farm. */
    private static final RoomShape PUMP_HOUSE = RoomShape.rectangle(8, 6);

    /** The room each step of the terminal is, in the order the cargo passes through them. */
    public static final RoomPurpose CUSTOMS = RoomPurpose.LOADING_BAY;
    public static final RoomPurpose BONDED = RoomPurpose.PARTS_CAGE;
    public static final RoomPurpose CONTROL = RoomPurpose.CONTROL_ROOM;
    public static final RoomPurpose PUMPS = RoomPurpose.ENGINE_ROOM;

    /** Yard a terminal keeps per bonded shed: the trucks' lanes between the sheds. UNTUNED. */
    static final int YARD_PER_SHED = 260;
    /** Open ground between the terminal's claim and the apron: the road out of its rear gate. UNTUNED. */
    static final int APRON_GAP = 3;

    // The terminal stands further in, with its quarter, so the deep end is the apron's.
    static final MapPlacement WORKS_AT = new MapPlacement(0.54f, 0.55f, 0.66f, 0.78f);
    static final MapPlacement INDUSTRY_AT = new MapPlacement(0.36f, 0.50f, 0.47f, 0.80f);

    @Override public String key() { return "spaceport"; }

    @Override public Set<String> industryIds() { return Set.of("spaceport", "megaport"); }

    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public MapPlacement worksAt() { return WORKS_AT; }

    @Override public MapPlacement industryAt() { return INDUSTRY_AT; }

    /** The deep end is the apron's: no yards beyond the terminal. */
    @Override public boolean yards() { return false; }

    /** The deep end is the apron's: the roads in come from the town side and the outer flank. */
    @Override public boolean deepFeeder() { return false; }

    /**
     * The cargo terminal in the order the cargo passes: the customs hall at the front gate, where the town roads come
     * in; a bonded shed per machine in the yard; the control tower and the pump house at the rear, toward the apron;
     * and the posts its gates are watched from. No keep and no airfield: the garrison beside it holds the market.
     *
     * @param sheds bonded sheds, one per machine
     */
    @Override public FortressProgram works(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a cargo terminal has at least one bonded shed");
        return new FortressProgram(List.of(
                new FortressBuilding(CUSTOMS, CUSTOMS_HALL, Ward.FRONTAGE, 1),
                new FortressBuilding(BONDED, BONDED_SHED, Ward.YARD, sheds),
                new FortressBuilding(CONTROL, TOWER, Ward.REAR, 1),
                new FortressBuilding(PUMPS, PUMP_HOUSE, Ward.REAR, 1),
                RaidWorksParts.guardPosts(2)),
                0, sheds * YARD_PER_SHED, true);
    }

    /** The apron's ground beside the terminal, at the deep end, stopped short of a storm bank's ground. */
    @Override public void sitePieces(SiteFrame frame, List<Piece> out) {
        Precinct terminal = frame.works();
        if (terminal == null) return;
        Site apron = apronSite(terminal.seedX(), terminal.seedY(),
                RaidSiteTemplate.worksReach(worksFit(), frame.security(), frame.width(), frame.height()),
                frame.mirrorX(), frame.mirrorY(), frame.width(), frame.height());
        out.add(frame.bankWind() != null
                ? RaidSiteTemplate.clearOfBank(apron, frame.bankWind(), frame.width(), frame.height()) : apron);
    }

    /**
     * The apron's ground: across the map and out to the deep edge as a pit's ground is ({@link RaidSiteTemplate#pitSite},
     * kept to the terminal's flank), but brought in to {@link #APRON_GAP} of the terminal's claim, since the apron's
     * road is the terminal's own; named {@link #APRON}.
     */
    static Site apronSite(int seedX, int seedY, int reach, boolean mirrorX, boolean mirrorY, int width, int height) {
        Site ground = RaidSiteTemplate.pitSite(seedX, seedY, reach, mirrorX, mirrorY, width, height);
        int x0 = mirrorX ? ground.x0() : seedX + reach + APRON_GAP;
        int x1 = mirrorX ? seedX - reach - APRON_GAP : ground.x1();
        if (x1 < x0) x1 = x0;
        return new Site(APRON, x0, ground.y0(), x1, ground.y1(), ground.purpose(), true);
    }

    /** Lays the apron on its ground once the terminal is built ({@link PortApronDressing}), and the intake queue. */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        if (!(plan.layout().find(APRON) instanceof Site ground)) return;
        TargetProfile profile = c.get(BspKeys.MARKET_PROFILE);
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        boolean deepEast = (ground.x0() + ground.x1()) / 2 > works.seedX();
        int edge = clearEdge(claimEdge(claim, index, deepEast, ground), deepEast, ground,
                (x, y) -> c.topology.getBuildingId(x, y) != 0);
        PortApron apron = apronOn(ground, deepEast, edge, c.height,
                PortApron.padsFor(profile == null ? 4 : profile.marketSize()));
        if (apron != null) {
            PortApronDressing.lay(c, apron, c.seed ^ 0x504f5254L);
            PortApronDressing.rearGate(c, apron);
        }
        PortApronDressing.intakeQueue(c, claim, index, (ground.x0() + ground.x1()) / 2, c.seed ^ 0x51554555L);
    }

    /** Cells of open ground left between the terminal's claim and its apron: the terminal's rear road. */
    static final int REAR_ROAD = 2;

    /**
     * The terminal's claim's outermost column on the apron's side, across the apron's rows; or the held ground's own
     * near edge where the claim is not known. The held ground keeps clear of the largest claim a terminal may make,
     * so a terminal that packed tighter leaves open ground the apron is brought in across.
     */
    static int claimEdge(int[][] claim, int index, boolean deepEast, Site ground) {
        int edge = deepEast ? ground.x0() - 1 - APRON_GAP : ground.x1() + 1 + APRON_GAP;
        if (claim == null || index < 0) return edge;
        int found = deepEast ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int x = 0; x < claim.length; x++) {
            for (int y = Math.max(0, ground.y0()); y <= Math.min(claim[x].length - 1, ground.y1()); y++) {
                if (claim[x][y] != index) continue;
                found = deepEast ? Math.max(found, x) : Math.min(found, x);
            }
        }
        return found == Integer.MIN_VALUE || found == Integer.MAX_VALUE ? edge : found;
    }

    /** Whether a cell is built on. */
    interface Built {
        boolean at(int x, int y);
    }

    /**
     * The terminal's edge as the apron may come in to it: the claim's {@code edge}, or, where something was built on
     * the open ground between the claim and the held ground, that building's far side, so the apron never takes in
     * a building that grew there. Only the apron's rows are looked at.
     */
    static int clearEdge(int edge, boolean deepEast, Site ground, Built built) {
        int from = deepEast ? edge + 1 : edge - 1, to = deepEast ? ground.x0() - 1 : ground.x1() + 1;
        int step = deepEast ? 1 : -1;
        int out = edge;
        for (int x = from; deepEast ? x <= to : x >= to; x += step) {
            for (int y = ground.y0(); y <= ground.y1(); y++) {
                if (built.at(x, y)) {
                    out = x;
                    break;
                }
            }
        }
        return out;
    }

    /**
     * The apron laid on its held ground, brought in to {@link #REAR_ROAD} of the terminal's claim ({@code edge}): the
     * deep edge on the far side from the terminal, the fuel farm at the end away from the map's middle.
     */
    static PortApron apronOn(Site ground, boolean deepEast, int edge, int mapHeight, int pads) {
        boolean outerNorth = ground.y0() + ground.y1() > mapHeight;
        int x0 = deepEast ? Math.min(ground.x0(), edge + 1 + REAR_ROAD) : ground.x0();
        int x1 = deepEast ? ground.x1() : Math.max(ground.x1(), edge - 1 - REAR_ROAD);
        return PortApron.lay(x0, ground.y0(), x1, ground.y1(), deepEast, outerNorth, pads);
    }
}
