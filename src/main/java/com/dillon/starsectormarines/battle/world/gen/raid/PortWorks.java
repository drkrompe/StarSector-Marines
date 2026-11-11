package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.air.PortFreighter;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
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
import com.dillon.starsectormarines.battle.world.gen.precinct.DefenceDoctrine;
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
import java.util.Map;
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
    /** The forecourt between the terminal and the hub, held from growth: the yard road's way in to the rear gate. */
    public static final String FORECOURT = "port-forecourt";
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
        Site forecourt = forecourt(hub, terminal.seedX(), reach, frame.width(), frame.height());
        if (forecourt != null) out.add(forecourt);
    }

    /**
     * The forecourt: the ground between the terminal and the hub, from half the terminal's reach out to the hub's start
     * and across the hub's bands beside the lines, held from the town's growth so no strip of lanes and sheds stands
     * between the two. The terminal's own square may still take the part of it nearest its seed. Null when the hub
     * starts at the terminal.
     */
    static Site forecourt(PortHub hub, int seedX, int reach, int width, int height) {
        PortHub.Rect paved = hub.paved(), built = hub.built();
        int x0 = hub.deepEast() ? seedX + reach / 2 : paved.x1() + 1;
        int x1 = hub.deepEast() ? paved.x0() - 1 : seedX - reach / 2;
        int y0 = hub.outerNorth() ? built.y0() : paved.y0();
        int y1 = hub.outerNorth() ? paved.y1() : built.y1();
        x0 = Math.max(0, x0);
        x1 = Math.min(width - 1, x1);
        if (x1 < x0 || y1 < y0) return null;
        return new Site(FORECOURT, x0, Math.max(0, y0), x1, Math.min(height - 1, y1), SitePurpose.GROUND, false);
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
    // before the hub, and light industry's assembly line before its assembly hall.

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
        PortHub.Rect lane = PortHubDressing.rearGate(c, hub);
        if (plan.layout().find(FORECOURT) instanceof Site forecourt) {
            PortHubDressing.forecourt(c, new PortHub.Rect(forecourt.x0(), forecourt.y0(), forecourt.x1(),
                    forecourt.y1()), lane, claim, index, c.seed ^ 0x434f5552L);
        }
        PortHubDressing.intakeQueue(c, claim, index, hub.terminalX(), c.seed ^ 0x51554555L);
        terminal(c, plan, works);
    }

    @Override
    public GuardedGrounds.Grounds guardedGrounds(GenContext c, PrecinctPlan plan, Precinct works,
                                                 WorksDefences.Layer layer) {
        if (!(plan.layout().find(APRON) instanceof Site apron) || !(plan.layout().find(LINES) instanceof Site lines)) {
            return null;
        }
        PortHub hub = hubOf(lines, apron);
        if (hub == null) return null;
        List<LandingPad> stands = new ArrayList<>();
        for (LandingPad pad : c.landingPads) if (pad.purpose == LandingPad.Purpose.PORT_LIGHTER) stands.add(pad);
        return grounds(hub, stands, layer == WorksDefences.Layer.FORTIFIED);
    }

    /** The place the port's lines in are guarded as: the highway, the rail line and the warehouse rows. */
    public static final String GUARD_LINES = "port-lines";
    /** The fuel farm's guarded place. */
    public static final String GUARD_FUEL = "port-fuel";
    /** Each guarded pad's place is named this prefix and the pad's index along the row. */
    public static final String GUARD_PAD = "port-pad-";
    /** Pads that hold a gun, guarded and fortified: spread along the row among the pads with ships on them. UNTUNED. */
    static final int GUARDED_PADS = 2, FORTIFIED_PADS = 3;

    /**
     * Where the port's guns stand (owner on {@code raid-site-port-r6}: "Where would the defenders logically be here?
     * Likely they would be spread out"): over what the port lives by rather than round the terminal alone, each place
     * sited by the defence doctrine ({@link GuardedGrounds}).
     *
     * <ul>
     *   <li><b>The lines in</b>: the highway, the rail line and the warehouse rows. An attack comes from the map's
     *       middle across the open ground beyond the highway, so its guns flank that approach first, out beyond the
     *       highway facing it, then the highway where it comes in past the terminal from the town (the truck gate's
     *       road meets it there).</li>
     *   <li><b>The fuel farm</b>: a gun off its open end, toward the terminal; the pads and the service lane close
     *       its other sides.</li>
     *   <li><b>The pads</b>: the ships on them are what a gun on a pad guards; {@link #GUARDED_PADS} or
     *       {@link #FORTIFIED_PADS} of the pads with ships, spread along the row, each with a gun on its apron at its
     *       ship's side toward the hub, laid over the service lane.</li>
     * </ul>
     *
     * <p>A Raid plan names no landing for the doctrine to face, so each place names its own way in on the side an
     * attack comes from: {@code DefenceDoctrine} flanks every place's ways in before anything else.
     *
     * No gun stands on a road, the highway or the rail line. The terminal keeps the guns its own wall stands behind.
     */
    static GuardedGrounds.Grounds grounds(PortHub hub, List<LandingPad> stands, boolean fortified) {
        List<GuardedGrounds.Place> places = new ArrayList<>();
        // The lines from the terminal's end out along the hub: back past it they run under the terminal to the town.
        int tx = hub.terminalX();
        PortHub.Rect l = hub.lines();
        PortHub.Rect ownLines = hub.deepEast() ? new PortHub.Rect(Math.max(l.x0(), tx), l.y0(), l.x1(), l.y1())
                : new PortHub.Rect(l.x0(), l.y0(), Math.min(l.x1(), tx), l.y1());
        PortHub.Rect highway = hub.highway();
        // The way an attack comes: from the map's middle, across the lines toward the outer edge. And the way back
        // along the hub to the terminal's end and the town.
        int inY = hub.outerNorth() ? -1 : 1, backX = hub.deepEast() ? -1 : 1;
        DefenceDoctrine.Gate approach = new DefenceDoctrine.Gate((ownLines.x0() + ownLines.x1()) / 2,
                hub.outerNorth() ? highway.y0() : highway.y1(), 0, inY, APPROACH_HALF);
        DefenceDoctrine.Gate truckGate = new DefenceDoctrine.Gate(tx, highway.centerY(), backX, 0,
                highway.height() / 2);
        places.add(new GuardedGrounds.Place(GUARD_LINES, List.of(ownLines, hub.built()), List.of(approach, truckGate),
                fortified ? Map.of(DefensePostKind.LARGE, 1, DefensePostKind.MEDIUM, 1, DefensePostKind.LIGHT, 1)
                        : Map.of(DefensePostKind.MEDIUM, 1, DefensePostKind.LIGHT, 1)));
        // The farm's open side is its terminal end: the pads stand on one side of it, the service lane on another.
        PortHub.Rect farm = hub.fuelFarm();
        DefenceDoctrine.Gate farmEnd = new DefenceDoctrine.Gate(backX < 0 ? farm.x0() : farm.x1(), farm.centerY(),
                backX, 0, APPROACH_HALF);
        places.add(new GuardedGrounds.Place(GUARD_FUEL, List.of(farm), List.of(farmEnd),
                Map.of(fortified ? DefensePostKind.MEDIUM : DefensePostKind.LIGHT, 1)));
        // The pads with ships, spread: the k of n at the middle of each of k equal runs of the row.
        List<Integer> withShips = new ArrayList<>();
        List<List<PortHub.Rect>> ships = new ArrayList<>();
        for (int i = 0; i < hub.pads().size(); i++) {
            List<PortHub.Rect> on = new ArrayList<>();
            for (LandingPad stand : stands) {
                if (!hub.pads().get(i).contains(stand.centerX, stand.centerY)) continue;
                PortHub.Rect ship = ship(stand);
                if (ship != null) on.add(ship);
            }
            if (on.isEmpty()) continue;
            withShips.add(i);
            ships.add(on);
        }
        int k = Math.min(withShips.size(), fortified ? FORTIFIED_PADS : GUARDED_PADS);
        for (int j = 0; j < k; j++) {
            int pick = (int) ((j + .5f) * withShips.size() / k);
            // Its gun on the apron at the ship's side toward the hub, laid over the service lane an attack crosses.
            PortHub.Rect pad = hub.pads().get(withShips.get(pick));
            DefenceDoctrine.Gate apronSide = new DefenceDoctrine.Gate(pad.centerX(),
                    edge(ships.get(pick), inY), 0, inY, APPROACH_HALF);
            places.add(new GuardedGrounds.Place(GUARD_PAD + withShips.get(pick), ships.get(pick), List.of(apronSide),
                    Map.of(fortified ? DefensePostKind.MEDIUM : DefensePostKind.LIGHT, 1)));
        }
        List<PortHub.Rect> keepClear = new ArrayList<>(hub.roads());
        keepClear.add(highway);
        keepClear.addAll(hub.tracks());
        keepClear.add(hub.fuelMain());
        keepClear.addAll(hub.blastWalls());
        return new GuardedGrounds.Grounds(places, keepClear);
    }

    /**
     * Half the width of a guarded place's way in that no road marks (the highway's face, a pad's apron side, the farm's
     * end): its guns flank it this far either side and {@code DefenceDoctrine.FLANK_OFFSET} more. UNTUNED.
     */
    static final int APPROACH_HALF = 2;

    /** The row of {@code rects}' edge furthest along {@code dy} (-1: least y; 1: most). */
    private static int edge(List<PortHub.Rect> rects, int dy) {
        int at = dy < 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (PortHub.Rect r : rects) at = dy < 0 ? Math.min(at, r.y0()) : Math.max(at, r.y1());
        return at;
    }

    /** The ground the ship on a stand covers: the largest its berth holds, nose along its long side; null for none. */
    static PortHub.Rect ship(LandingPad stand) {
        boolean longY = stand.halfHeight >= stand.halfWidth;
        int along = 2 * Math.max(stand.halfWidth, stand.halfHeight) + 1;
        int across = 2 * Math.min(stand.halfWidth, stand.halfHeight) + 1;
        PortFreighter ship = PortFreighter.forBerth(along, across);
        if (ship == null) return null;
        int halfLong = (int) Math.ceil(ship.lengthCells / 2f), halfBeam = (int) Math.ceil(ship.beamCells / 2f);
        int hx = longY ? halfBeam : halfLong, hy = longY ? halfLong : halfBeam;
        return new PortHub.Rect(stand.centerX - hx, stand.centerY - hy, stand.centerX + hx, stand.centerY + hy);
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
