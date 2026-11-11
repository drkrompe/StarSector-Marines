package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A port's site ({@code raid-site-templates.md}, The planet's chokepoint): the planet's only orbital lift and its
 * travel and freight hub, laid at a planet's scale across the works' whole half of the map ({@link PortHub}).
 *
 * <p>The highway and the rail line come in off the deep edge from the planet's other towns and run along the map's
 * middle to the town; the garrison holds the far side of them. Between the lines and the outer edge stands the port:
 * at the town end the cargo and passenger terminal (the works), where the town's roads come in to customs and the
 * passenger hall, and cleared cargo is held in the bonded sheds (the machines a raid wrecks, and the store a raid
 * for valuables breaks into); from the terminal's rear gate the yard road runs out along the hub between the rows of
 * warehouses and the container staging under its gantry cranes, past the fuel main to the pad row along the outer
 * edge, where the lighters lift, and the heavy pad at the deep end. The company's own lift lands at the other end of
 * the map; the lighters are the defender's and the planet's traffic.
 *
 * <p>The lines' band and the apron (staging to the outer edge) are ground held from growth ({@link #LINES},
 * {@link #APRON}); the warehouse band between them is built ground ({@link #WAREHOUSES}), held from the town's growth
 * with the warehouses raised on it as layout structures before the map is finished, so they are buildings like any
 * other. The highway and the hub's own roads, which tie it to every warehouse and pad ({@link PortHub#roads}), are
 * street the city's road finish lays as it lays the town's. Everything else is dressed once the
 * terminal is built ({@link PortHubDressing}).
 */
public final class PortWorks implements RaidIndustry {

    /** The apron: staging, fuel and pads, ground held out to the outer edge, read with {@code plan.layout().find}. */
    public static final String APRON = "apron";
    /** The lines in: the highway and the rail line's band, held from growth. */
    public static final String LINES = "port-lines";
    /** The warehouse rows' band, held from the town's growth as built ground the warehouses are raised on. */
    public static final String WAREHOUSES = "port-warehouses";
    /** Each warehouse's structure is named this prefix and its index. */
    public static final String WAREHOUSE = "port-warehouse-";

    /** The customs hall at the gate the town roads come in by: where arriving cargo is cleared and bonded. */
    private static final RoomShape CUSTOMS_HALL = RoomShape.rectangle(14, 8);
    /** The passenger hall beside customs: the planet's travellers to and from orbit. */
    private static final RoomShape PASSENGER_HALL = RoomShape.rectangle(16, 8);
    /** A bonded shed: cleared cargo held under seal until its lighter. */
    private static final RoomShape BONDED_SHED = RoomShape.rectangle(16, 9);
    /** The control tower, over the hub. */
    private static final RoomShape TOWER = RoomShape.rectangle(7, 7);
    /** The pump house that feeds the hub's fuel main from the farm. */
    private static final RoomShape PUMP_HOUSE = RoomShape.rectangle(8, 6);

    /** The room each step of the terminal is, in the order the cargo passes through them. */
    public static final RoomPurpose CUSTOMS = RoomPurpose.LOADING_BAY;
    public static final RoomPurpose PASSENGERS = RoomPurpose.CIVIC_RECEPTION;
    public static final RoomPurpose BONDED = RoomPurpose.PARTS_CAGE;
    public static final RoomPurpose CONTROL = RoomPurpose.CONTROL_ROOM;
    public static final RoomPurpose PUMPS = RoomPurpose.ENGINE_ROOM;

    /** Yard a terminal keeps per bonded shed: the trucks' lanes between the sheds. UNTUNED. */
    static final int YARD_PER_SHED = 260;
    /** Open ground between the terminal's furthest claim and the hub. UNTUNED. */
    static final int HUB_GAP = 3;
    /** Cells the hub stops short of the map's edges. */
    static final int MARGIN = 2;
    /** Where the lines run across the map, as a share of its height from the far side's edge. UNTUNED. */
    static final float LINES_AT = 0.38f;

    // The terminal stands at the hub's town end, its claim clear above the lines; the industrial quarter beside it on
    // the town side, between it and the homes: the freight forwarders' yards the haul road runs out to.
    static final MapPlacement WORKS_AT = new MapPlacement(0.36f, 0.80f, 0.46f, 0.86f);
    static final MapPlacement INDUSTRY_AT = new MapPlacement(0.33f, 0.66f, 0.35f, 0.88f);
    /** The garrison's seed in the far side's deep corner, clear of the lines across the map's middle. UNTUNED. */
    static final MapPlacement GARRISON_AT = new MapPlacement(0.70f, 0.06f, 0.90f, 0.16f);

    @Override public String key() { return "spaceport"; }

    @Override public Set<String> industryIds() { return Set.of("spaceport", "megaport"); }

    /** A terminal's halls stand apart on wide yards, as a mine's do. */
    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public MapPlacement worksAt() { return WORKS_AT; }

    @Override public MapPlacement industryAt() { return INDUSTRY_AT; }

    /**
     * The garrison low in the far side's corner, below the lines rather than against them, so its wall does not run
     * across the highway and the rail line on a small map.
     */
    @Override public MapPlacement garrisonAt() { return GARRISON_AT; }

    /** The deep end is the hub's: no yards beyond the terminal. */
    @Override public boolean yards() { return false; }

    /** The deep end is the hub's, and the lines come in from it. */
    @Override public boolean deepFeeder() { return false; }

    /**
     * The cargo and passenger terminal in the order the cargo passes: customs and the passenger hall at the front
     * gate, where the town roads come in; a bonded shed per machine in the yard; the control tower and the pump house
     * at the rear, toward the hub; and the posts its gates are watched from. No keep and no airfield: the garrison
     * across the lines holds the market.
     *
     * @param sheds bonded sheds, one per machine
     */
    @Override public FortressProgram works(int sheds) {
        if (sheds < 1) throw new IllegalArgumentException("a cargo terminal has at least one bonded shed");
        return new FortressProgram(List.of(
                new FortressBuilding(CUSTOMS, CUSTOMS_HALL, Ward.FRONTAGE, 1),
                new FortressBuilding(PASSENGERS, PASSENGER_HALL, Ward.FRONTAGE, 1),
                new FortressBuilding(BONDED, BONDED_SHED, Ward.YARD, sheds),
                new FortressBuilding(CONTROL, TOWER, Ward.REAR, 1),
                new FortressBuilding(PUMPS, PUMP_HOUSE, Ward.REAR, 1),
                RaidWorksParts.guardPosts(2)),
                0, sheds * YARD_PER_SHED, true);
    }

    /** The hub's held ground and the warehouses, beside the terminal. */
    @Override public void sitePieces(SiteFrame frame, List<Piece> out) {
        Precinct terminal = frame.works();
        if (terminal == null) return;
        int reach = RaidSiteTemplate.worksReach(worksFit(), frame.security(), frame.width(), frame.height());
        Site box = hubBox(terminal.seedX(), terminal.seedY(), reach, frame.mirrorX(), frame.mirrorY(),
                frame.width(), frame.height());
        if (frame.bankWind() != null) {
            box = RaidSiteTemplate.clearOfBank(box, frame.bankWind(), frame.width(), frame.height());
        }
        PortHub hub = PortHub.lay(box.x0(), box.y0(), box.x1(), box.y1(), !frame.mirrorX(), !frame.mirrorY(),
                back(reach));
        if (hub == null) return;
        out.addAll(pieces(hub, frame.width(), frame.height()));
    }

    /**
     * The lines in run back past the hub's start this far: under the terminal to its town side, where the
     * industrial quarter's streets and the haul road meet them.
     */
    static int back(int reach) {
        return 2 * reach + HUB_GAP;
    }

    /**
     * The hub's ground: from {@link #HUB_GAP} beyond the terminal's furthest claim to the deep edge, and from the
     * lines ({@link #LINES_AT} of the map's height from the far side) to the outer edge.
     */
    static Site hubBox(int seedX, int seedY, int reach, boolean mirrorX, boolean mirrorY, int width, int height) {
        int x0 = mirrorX ? MARGIN : seedX + reach + HUB_GAP;
        int x1 = mirrorX ? seedX - reach - HUB_GAP : width - 1 - MARGIN;
        int lines = Math.round(LINES_AT * height);
        int y0 = mirrorY ? MARGIN : lines;
        int y1 = mirrorY ? height - 1 - lines : height - 1 - MARGIN;
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        return new Site(APRON, x0, y0, x1, y1, SitePurpose.GROUND, true);
    }

    /**
     * The hub's layout pieces: the lines' band and the apron held from growth, and
     * a warehouse raised on each of the hub's warehouse grounds, which lie between the two held bands.
     */
    static List<Piece> pieces(PortHub hub, int width, int height) {
        List<Piece> out = new ArrayList<>();
        out.add(site(LINES, hub.lines(), width, height));
        out.add(site(APRON, hub.apron(), width, height));
        Site band = site(WAREHOUSES, hub.built(), width, height);
        out.add(new Site(band.name(), band.x0(), band.y0(), band.x1(), band.y1(), SitePurpose.BUILT, true));
        for (int i = 0; i < hub.sheds().size(); i++) {
            PortHub.Shed shed = hub.sheds().get(i);
            out.add(new Structure(WAREHOUSE + i, StructureKind.GRANARY, shed.at().x0(), shed.at().y0(), shed.turns(),
                    false));
        }
        return out;
    }

    private static Site site(String name, PortHub.Rect r, int width, int height) {
        return new Site(name, Math.max(0, r.x0()), Math.max(0, r.y0()), Math.min(width - 1, r.x1()),
                Math.min(height - 1, r.y1()), SitePurpose.GROUND, true);
    }

    /**
     * The hub read back from its held ground on the plan: the lines' band runs back past the apron toward the town,
     * which says which way the hub was turned.
     */
    static PortHub hubOf(Site lines, Site apron) {
        boolean deepEast = lines.x0() < apron.x0();
        boolean outerNorth = apron.y1() > lines.y1();
        int back = deepEast ? apron.x0() - lines.x0() : lines.x1() - apron.x1();
        int y0 = outerNorth ? lines.y0() : apron.y0(), y1 = outerNorth ? apron.y1() : lines.y1();
        return PortHub.lay(apron.x0(), y0, apron.x1(), y1, deepEast, outerNorth, back);
    }

    // Ground held beside a works at the deep end and brought in to its claim once it is built: the port's apron
    // before the hub, and light industry's assembly line now (LightIndustryWorks).

    /** Open ground between a works' furthest claim and the ground held beside it. UNTUNED. */
    static final int APRON_GAP = 3;
    /** Cells of open ground left between a works' claim and the ground brought in to it: its rear road. */
    static final int REAR_ROAD = 2;

    /**
     * Ground beside a works at the deep end: across the map and out to the deep edge as a pit's ground is
     * ({@link RaidSiteTemplate#pitSite}, kept to the works' flank), but from {@link #APRON_GAP} beyond the works'
     * reach, since the ground's road is the works' own; named {@link #APRON}.
     */
    static Site apronSite(int seedX, int seedY, int reach, boolean mirrorX, boolean mirrorY, int width, int height) {
        Site ground = RaidSiteTemplate.pitSite(seedX, seedY, reach, mirrorX, mirrorY, width, height);
        int x0 = mirrorX ? ground.x0() : seedX + reach + APRON_GAP;
        int x1 = mirrorX ? seedX - reach - APRON_GAP : ground.x1();
        if (x1 < x0) x1 = x0;
        return new Site(APRON, x0, ground.y0(), x1, ground.y1(), ground.purpose(), true);
    }

    /**
     * The works' claim's outermost column on the held ground's side, across the ground's rows; or the held ground's
     * own near edge where the claim is not known. The held ground keeps clear of the largest claim a works may make,
     * so a works that packed tighter leaves open ground the held ground is brought in across.
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
     * The works' edge as held ground may come in to it: the claim's {@code edge}, or, where something was built on
     * the open ground between the claim and the held ground, that building's far side, so the ground never takes in
     * a building that grew there. Only the held ground's rows are looked at.
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

    /** Lays the hub once the terminal is built ({@link PortHubDressing}), the terminal's rear gate and its intake queue. */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        if (!(plan.layout().find(APRON) instanceof Site apron) || !(plan.layout().find(LINES) instanceof Site lines)) {
            return;
        }
        PortHub hub = hubOf(lines, apron);
        if (hub == null) return;
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        PortHubDressing.lay(c, hub, c.seed ^ 0x504f5254L);
        PortHubDressing.rearGate(c, hub);
        PortHubDressing.intakeQueue(c, claim, index, hub.terminalX(), c.seed ^ 0x51554555L);
        terminal(c, plan, works);
    }

    /** Cells out from a bonded shed's wall its cargo is staged: room for a door and a loader between. */
    static final int STAGED_OUT = 3;
    /** Cells between two containers staged along a shed. */
    static final int STAGED_GAP = 1;

    /**
     * The terminal read as the port's: its bonded sheds, customs shed and pump house roofed in ribbed steel as the hub's
     * warehouses are, and cleared cargo staged along the bonded sheds, containers in a row with a loader at its end.
     */
    static void terminal(GenContext c, PrecinctPlan plan, Precinct works) {
        int n = 0;
        for (PointOfInterest b : WorksProps.buildings(c, plan, works)) {
            RoomPurpose purpose = WorksProps.purposeOf(c, b);
            if (purpose != BONDED && purpose != CUSTOMS && purpose != PUMPS) continue;
            for (int x = b.left; x <= b.right; x++) {
                for (int y = b.top; y <= b.bottom; y++) c.topology.setRoofKind(x, y, CellTopology.RoofKind.SHED);
            }
            if (purpose == BONDED) staged(c, b, n++);
        }
    }

    /** Containers in a row along each long side of a bonded shed, {@link #STAGED_OUT} from its wall, a loader last. */
    private static void staged(GenContext c, PointOfInterest b, int n) {
        boolean wide = b.right - b.left >= b.bottom - b.top;
        int from = wide ? b.left : b.top, to = wide ? b.right : b.bottom;
        int[] sides = wide ? new int[]{b.top - STAGED_OUT - PortHub.BOX_DEEP, b.bottom + STAGED_OUT + 1}
                : new int[]{b.left - STAGED_OUT - PortHub.BOX_DEEP, b.right + STAGED_OUT + 1};
        int k = n;
        for (int side : sides) {
            int t = from;
            for (; t + PortHub.BOX_LONG - 1 <= to; t += PortHub.BOX_LONG + STAGED_GAP) {
                String box = PortHubDressing.BOXES[k++ % PortHubDressing.BOXES.length];
                int x = wide ? t : side, y = wide ? side : t;
                stand(c, x, y, box, wide ? 0 : 1);
            }
            int x = wide ? t : side, y = wide ? side : t;
            stand(c, x, y, LOADER, wide ? 0 : 1);
        }
    }

    /** The loader working a bonded shed's staged cargo. */
    static final String LOADER = "doodad.stores-cargo-loader";

    /** A solid prop {@code id} at {@code (x, y)} turned {@code turns}, where its footprint is open; whether it stood. */
    private static boolean stand(GenContext c, int x, int y, String id, int turns) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef def = registry == null ? null : registry.doodad(id);
        if (def == null) return false;
        Doodad prop = new Doodad(x, y, def, turns);
        if (!WorksProps.open(c, x, y, prop.footprintCellsX, prop.footprintCellsY)) return false;
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) {
            for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
                c.grid.setWalkable(xx, yy, false);
                c.grid.setSeeThrough(xx, yy, true);
                c.topology.setFixture(xx, yy, true);
            }
        }
        c.doodads.add(prop);
        return true;
    }
}
