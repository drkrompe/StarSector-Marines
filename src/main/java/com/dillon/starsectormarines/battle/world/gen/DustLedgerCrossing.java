package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.damage.DamageCatalog;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.road.AuthoredRoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Saltwell Cross on Asharu, The Dust Ledger's authored map ({@code dust-ledger-crossing.md}): a public freight apron
 * on the west edge, a crossroads market where the apron road meets the crawler road, the Harrow salvage yard on the
 * crawler road, a standable ridge under the north escarpment, the dry canal with two crossings in the south, and
 * Sable Wake's pump house compound in the east. Mission behaviour belongs to the mission; this recipe lays the ground
 * and publishes the sites it needs as a {@link Layout}.
 *
 * <p>Buildings are published only as held places, so the map itself adds no capture point. y grows north.
 */
public final class DustLedgerCrossing {
    public static final int WIDTH = 192, HEIGHT = 112;

    public record Cell(int x, int y) {}

    /** An axis-aligned block of cells, both corners inclusive. */
    public record Area(int left, int bottom, int right, int top) {
        public boolean contains(int x, int y) { return x >= left && x <= right && y >= bottom && y <= top; }
        public Cell centre() { return new Cell((left + right) / 2, (bottom + top) / 2); }
        public List<Cell> cells() {
            List<Cell> out = new ArrayList<>();
            for (int y = bottom; y <= top; y++) for (int x = left; x <= right; x++) out.add(new Cell(x, y));
            return out;
        }
    }

    /** The three ways to the compound the map keeps open, each a line of waypoints from the apron. */
    public enum Approach { MARKET, RIDGE, CRAWLER_ROAD }

    /** Sable Wake's posts by duty. */
    public enum PostRole { GATE, WALL, ROOF, CLERKS, CRAWLER, CANTINA }

    /**
     * Every site the mission names. Cells are walkable floor unless said otherwise.
     *
     * @param landing          the freight apron, every berth one landing area
     * @param companyStart     where the company forms up between the berths
     * @param hauler           centre of Wayline's parked hauler stand (three by seven cells, kept open)
     * @param couplingCrate    the salvagers' drive coupling, set down beside the hauler
     * @param ledgerRoom       the ledger room's floor
     * @param ledgerTarget     the seizure cell: a Raid hold of radius two round it stays inside the room
     * @param burnBarrel       the barrel the clerks feed the books into (a fixture, not walkable)
     * @param countingRoom     the counting room's floor, through which the ledger room is also entered
     * @param brokerOffice     where the broker sits, in his office off the corridor
     * @param brokerOfficeRoom the broker's office floor
     * @param radioRoom        the radio room's floor
     * @param pumpHouse        the pump house building (administrative interior)
     * @param skimmerPad       the broker's skimmer pad beyond the southern canal crossing
     * @param skimmerBoarding  the cell the broker boards from
     * @param compound         the walled yard, walls included
     * @param exclusionRing    the compound grounds plus a short margin, the quiet phase's no-go ground
     * @param gate             the compound gate on the crawler road, outside cell
     * @param postern          the west postern facing the market, outside cell
     * @param southGate        the south gate toward the skimmer pad, outside cell
     * @param posts            Sable Wake's posts by duty, about eighteen in all
     * @param cantina          the cantina building
     * @param wellHouse        the market warden's well house, interior cell
     * @param wellHouseArea    the well house building
     * @param harrowYard       the Harrow salvage yard on the crawler road
     * @param crateDrop        where the coupling is set down in the yard
     * @param salvageTeam      one crawler-road cell per member of the Harrow salvage team, where it stands when it moves
     * @param spotterPosts     the warden's two rooftop watchmen
     * @param canalCrossings   west crossing first, then the southern crossing toward the skimmer pad
     * @param ridgeOverwatch   rim cells on the ridge that look down on the compound yard
     * @param approaches       waypoints of each approach, apron to compound
     * @param warehouses       market warehouses a crew can staff
     * @param shops            market shops (the cantina among them) a crew can staff
     * @param homes            market buildings ambient civilians live in; the compound's bunkhouse is not one
     */
    public record Layout(MapResult map, Area landing, Cell companyStart, Cell hauler, Cell couplingCrate,
                         Area ledgerRoom, Cell ledgerTarget, Cell burnBarrel, Area countingRoom,
                         Cell brokerOffice, Area brokerOfficeRoom, Area radioRoom, PointOfInterest pumpHouse,
                         Area skimmerPad, Cell skimmerBoarding,
                         Area compound, Area exclusionRing, Cell gate, Cell postern, Cell southGate,
                         Map<PostRole, List<Cell>> posts,
                         PointOfInterest cantina, Cell wellHouse, PointOfInterest wellHouseArea,
                         Area harrowYard, Cell crateDrop, List<Cell> salvageTeam, List<Cell> spotterPosts,
                         List<Cell> canalCrossings, List<Cell> ridgeOverwatch,
                         Map<Approach, List<Cell>> approaches,
                         List<PointOfInterest> warehouses, List<PointOfInterest> shops, List<PointOfInterest> homes) {}

    static final Area APRON = new Area(6, 38, 34, 82);
    static final Cell COMPANY_START = new Cell(22, 65);
    static final Cell HAULER = new Cell(31, 66);
    private static final int HAULER_HALF_WIDTH = 1, HAULER_HALF_LENGTH = 3;
    static final Cell COUPLING_CRATE = new Cell(27, 66);
    static final Cell CROSSROADS = new Cell(88, 60);

    static final Area COMPOUND = new Area(136, 30, 184, 70);
    private static final int RING_MARGIN = 5;
    static final Cell GATE = new Cell(172, 72);
    static final Cell POSTERN = new Cell(134, 38);
    static final Cell SOUTH_GATE = new Cell(155, 28);
    /** Pump house, bunkhouse and crawler garage corners inside the yard. */
    private static final int PUMP_LEFT = 153, PUMP_BOTTOM = 44;
    private static final int GARAGE_LEFT = 139, GARAGE_BOTTOM = 58;
    private static final int BUNK_LEFT = 139, BUNK_BOTTOM = 32;

    static final Area SKIMMER_PAD = new Area(151, 5, 159, 10);
    static final Cell SKIMMER_BOARDING = new Cell(155, 11);
    static final Cell WEST_CROSSING = new Cell(82, 20);
    static final Cell SOUTH_CROSSING = new Cell(155, 20);

    static final Area HARROW_YARD = new Area(112, 55, 128, 64);
    static final Cell CRATE_DROP = new Cell(119, 62);
    static final Cell SALVAGE_TEAM = new Cell(126, 77);
    private static final List<Cell> SALVAGE_TEAM_CELLS = List.of(new Cell(124, 76), new Cell(126, 76),
            new Cell(124, 78), new Cell(126, 78));

    /** The ridge's ramps: west from the well lane, east above the gate. */
    static final Cell WEST_RAMP_FOOT = new Cell(88, 83), EAST_RAMP_FOOT = new Cell(156, 84);
    private static final ElevationPlan.Point WEST_RAMP_HEAD = new ElevationPlan.Point(88, 92);
    private static final ElevationPlan.Point EAST_RAMP_HEAD = new ElevationPlan.Point(156, 92);
    /** Cells either side of an approach's line its route may use. */
    static final int APPROACH_CORRIDOR = 6;

    private DustLedgerCrossing() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        Sites s = new Sites();
        new GenRecipe("Saltwell Cross", List.of(DustLedgerCrossing::grounds,
                context -> market(context, s), context -> compound(context, s),
                context -> RoadRasterizer.clearReplacedSurfaces(context.topology, WIDTH, HEIGHT),
                DustLedgerCrossing::yardSurfaces, context -> dressing(context, s), DustLedgerCrossing::terrain,
                DustLedgerCrossing::ridge, new FinalizeStage(), new OutdoorLandscapeStage(anchors()))).run(c);
        MapResult map = new MapResult(c.grid, c.topology, COMPANY_START.x(), COMPANY_START.y(),
                s.pumpHouse.interiorAnchorX, s.pumpHouse.interiorAnchorY, c.pois, c.doodads,
                new TacticalMap(HeldPlaces.fromBuildings(c.pois,
                        poi -> COMPOUND.contains(poi.interiorAnchorX, poi.interiorAnchorY) ? Faction.DEFENDER : Faction.CIVILIAN)),
                c.get(BspKeys.BUILDINGS), List.of(),
                AuthoredRoadGraph.graph(RoadCatalog.plan(RoadCatalog.DUST_LEDGER), ""), c.landingPads);
        Layout layout = layout(map, s);
        validate(layout);
        return withDamage(layout);
    }

    /** The same layout over the map with the town's old ruin and wear applied. */
    private static Layout withDamage(Layout l) {
        MapResult damaged = l.map().withInitialDamage(DamageCatalog.dustLedger());
        return new Layout(damaged, l.landing(), l.companyStart(), l.hauler(), l.couplingCrate(), l.ledgerRoom(),
                l.ledgerTarget(), l.burnBarrel(), l.countingRoom(), l.brokerOffice(), l.brokerOfficeRoom(),
                l.radioRoom(), l.pumpHouse(), l.skimmerPad(), l.skimmerBoarding(), l.compound(), l.exclusionRing(),
                l.gate(), l.postern(), l.southGate(), l.posts(), l.cantina(), l.wellHouse(), l.wellHouseArea(),
                l.harrowYard(), l.crateDrop(), l.salvageTeam(), l.spotterPosts(), l.canalCrossings(),
                l.ridgeOverwatch(), l.approaches(), l.warehouses(), l.shops(), l.homes());
    }

    /** What the stages learned while building, read back into the layout. */
    private static final class Sites {
        PointOfInterest pumpHouse, cantina, wellHouse, northShop, eastHome;
        final List<PointOfInterest> warehouses = new ArrayList<>(), shops = new ArrayList<>(), homes = new ArrayList<>();
    }

    private static int[] anchors() {
        List<Cell> cells = new ArrayList<>(List.of(COMPANY_START, HAULER, COUPLING_CRATE, CROSSROADS, GATE, POSTERN,
                SOUTH_GATE, SKIMMER_BOARDING, WEST_CROSSING, SOUTH_CROSSING, CRATE_DROP, SALVAGE_TEAM,
                WEST_RAMP_FOOT, EAST_RAMP_FOOT));
        for (List<Cell> line : approachLines().values()) cells.addAll(line);
        int[] out = new int[cells.size() * 2];
        for (int i = 0; i < cells.size(); i++) {
            out[2 * i] = cells.get(i).x();
            out[2 * i + 1] = cells.get(i).y();
        }
        return out;
    }

    /** The approaches' lines as authored, from the company's start to the compound's ways in. */
    static Map<Approach, List<Cell>> approachLines() {
        Map<Approach, List<Cell>> lines = new EnumMap<>(Approach.class);
        lines.put(Approach.MARKET, List.of(COMPANY_START, new Cell(60, 60), CROSSROADS, new Cell(90, 46),
                new Cell(90, 38), new Cell(115, 38), POSTERN));
        lines.put(Approach.CRAWLER_ROAD, List.of(COMPANY_START, new Cell(60, 60), CROSSROADS, new Cell(106, 69),
                new Cell(124, 77), new Cell(150, 77), GATE));
        lines.put(Approach.RIDGE, List.of(COMPANY_START, new Cell(60, 60), CROSSROADS, new Cell(88, 79),
                WEST_RAMP_FOOT, new Cell(88, 93), new Cell(120, 94), new Cell(150, 93), new Cell(156, 93),
                EAST_RAMP_FOOT, new Cell(164, 78), GATE));
        return lines;
    }

    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case ARID -> GroundKind.SAND;
            case FROZEN -> GroundKind.SNOW;
            default -> GroundKind.STONE;
        };
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, wild);
        }
        // Dead terraforming fields along both banks of the canal: furrowed earth, never paving.
        fields(c, 30, 26, 78, 34);
        fields(c, 96, 26, 128, 33);
        fields(c, 44, 8, 76, 15);
        fields(c, 90, 9, 110, 15);
        var roads = RoadCatalog.plan(RoadCatalog.DUST_LEDGER);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        // Three berths on the public apron; Wayline's drop takes them all, one landing area.
        for (int y : new int[]{44, 58, 72})
            c.landingPads.add(new LandingPad(20, y, 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
        // Made ground, paved in yardSurfaces: the apron's slab, the compound yard and the skimmer pad.
        slab(c, APRON);
        slab(c, new Area(COMPOUND.left() + 1, COMPOUND.bottom() + 1, COMPOUND.right() - 1, COMPOUND.top() - 1));
        slab(c, SKIMMER_PAD);
    }

    private static void slab(GenContext c, Area area) {
        for (int y = area.bottom(); y <= area.top(); y++) for (int x = area.left(); x <= area.right(); x++) {
            c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
            c.markMadeGround(x, y);
        }
    }

    /** Furrows run east to west, two rows of turned earth to one of dry sand. */
    private static void fields(GenContext c, int l, int b, int r, int t) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++)
            c.topology.setGroundKind(x, y, y % 3 == 0 ? GroundKind.SAND : GroundKind.DIRT);
    }

    private static void market(GenContext c, Sites s) {
        // North of the apron road: a shop, the cantina and the warden's well house beside the well lane.
        s.shops.add(stamp(c, BuildingStamp.CORNER_SHOP, 46, 66, 0));
        s.cantina = stamp(c, BuildingStamp.MARKET_HALL, 62, 66, 0);
        s.shops.add(s.cantina);
        s.wellHouse = stamp(c, BuildingStamp.FARMHOUSE, 76, 66, 0);
        // Between the well lane and the crawler road.
        s.northShop = stamp(c, BuildingStamp.CORNER_SHOP, 93, 74, 0);
        s.shops.add(s.northShop);
        // South of the apron road: the bonded warehouses, staffed.
        s.warehouses.add(stamp(c, BuildingStamp.FREIGHT_L, 44, 37, 0));
        s.warehouses.add(stamp(c, BuildingStamp.OFFSET_DEPOT, 68, 42, 0));
        // East of the field track: the residential cluster on the back lane.
        s.homes.add(stamp(c, BuildingStamp.COURT_HOUSE, 95, 42, 0));
        s.homes.add(stamp(c, BuildingStamp.CORNER_HOME, 110, 42, 0));
        s.eastHome = stamp(c, BuildingStamp.FARMHOUSE, 122, 42, 0);
        s.homes.add(s.eastHome);
        s.homes.add(s.wellHouse);
        s.homes.addAll(s.shops);
        // The failed terraforming's ruins on the south hardpan, roofless since before the market (the damage plan).
        stamp(c, BuildingStamp.FARMHOUSE, 9, 6, 0);
        stamp(c, BuildingStamp.BARN, 115, 4, 0);
    }

    private static PointOfInterest stamp(GenContext c, BuildingStamp plan, int left, int bottom, int turns) {
        plan.place(c, left, bottom, turns);
        return c.pois.get(c.pois.size() - 1);
    }

    private static void compound(GenContext c, Sites s) {
        s.pumpHouse = stamp(c, BuildingStamp.PUMP_HOUSE, PUMP_LEFT, PUMP_BOTTOM, 0);
        stamp(c, BuildingStamp.OFFSET_DEPOT, GARAGE_LEFT, GARAGE_BOTTOM, 2); // Crawler garage, doors on both long sides.
        stamp(c, BuildingStamp.CORNER_HOME, BUNK_LEFT, BUNK_BOTTOM, 2); // Bunkhouse, door on the yard.
        // A modest wall, not a fortress: the gate on the crawler road, a postern to the market, a south gate.
        for (int x = COMPOUND.left(); x <= COMPOUND.right(); x++) {
            if (Math.abs(x - GATE.x()) > 2) wall(c, x, COMPOUND.top());
            if (Math.abs(x - SOUTH_GATE.x()) > 1) wall(c, x, COMPOUND.bottom());
        }
        for (int y = COMPOUND.bottom(); y <= COMPOUND.top(); y++) {
            if (Math.abs(y - POSTERN.y()) > 1) wall(c, COMPOUND.left(), y);
            wall(c, COMPOUND.right(), y);
        }
    }

    private static void wall(GenContext c, int x, int y) {
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, false);
        c.topology.setWall(x, y, true);
        c.markMadeGround(x, y);
    }

    private static void yardSurfaces(GenContext c) {
        int[] concrete = new int[3];
        for (int age = 0; age < 3; age++) concrete[age] = c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            if (c.topology.getGroundKind(x, y) == GroundKind.COURTYARD && !c.topology.isWall(x, y))
                c.topology.setSurface(x, y, concrete[RoadRasterizer.age(.6, 6131, x, y)]);
    }

    /** Working props: the apron's freight, the market's stalls, the salvage yard and the compound's posts. */
    private static void dressing(GenContext c, Sites s) {
        StoryServiceYard yard = new StoryServiceYard(c);
        // Apron: fuel bowser and stacked freight clear of the berths and the hauler stand.
        yard.prop(31, 40, "parked-tanker-truck", 0);
        yard.cargo(13, 79);
        yard.cargo(28, 79);
        // Market stalls along the south side of the crossroads.
        for (int x : new int[]{96, 102}) yard.prop(x, 55, "mess-table-long", 0);
        yard.prop(108, 55, "industrial-crate-stack", 0);
        for (int x : new int[]{57, 70, 75}) yard.prop(x, 54, "mess-table-long", 0);
        yard.prop(66, 54, "industrial-pallet-stack", 0);
        yard.prop(80, 54, "industrial-crate-stack", 0);
        // Wreckage of the old low-orbit battle on the open hardpan: a hull shell under the escarpment, plates south.
        yard.prop(44, 89, "basin-scrap-hull-shell", 1);
        yard.prop(60, 96, "basin-scrap-plate-stack", 0);
        yard.prop(172, 5, "basin-scrap-rib-section", 2);
        yard.prop(182, 6, "basin-scrap-plate-stack", 1);
        // The warden's well behind the well house.
        yard.prop(79, 75, "industrial-pump-set", 0);
        // Harrow's yard: the stranded crawler waiting on its coupling, and the crew's stock.
        yard.gravel(HARROW_YARD.left(), HARROW_YARD.bottom(), HARROW_YARD.right(), HARROW_YARD.top());
        yard.prop(114, 57, "parked-flatbed-truck", 0);
        yard.prop(122, 56, "field-salvage-machinery", 0);
        yard.prop(126, 60, "field-salvage-metals", 0);
        yard.prop(113, 63, "industrial-scrap-pile", 0);
        // Compound: the crawler in front of its garage, drums by the pump hall, sandbags at the gate.
        yard.prop(141, 50, "parked-flatbed-truck", 0);
        yard.prop(162, 39, "industrial-drum-cluster", 0);
        yard.prop(168, 39, "industrial-fluid-tank", 0);
        yard.prop(171, 39, "industrial-fluid-tank", 0);
        yard.prop(176, 38, "industrial-generator", 0);
        yard.cargo(158, 63);
        yard.prop(152, 33, "industrial-pallet-stack", 0);
        yard.prop(166, 33, "industrial-pipe-bundle", 0);
        yard.prop(174, 33, "industrial-scrap-pile", 0);
        yard.prop(144, 47, "industrial-drum-cluster", 0);
        yard.prop(180, 62, "parked-cargo-truck", 1);
        yard.prop(182, 40, "industrial-cable-reel", 0);
        for (int x = 167; x <= 169; x++) yard.prop(x, 66, "sandbag-straight-n", 0);
        for (int x = 175; x <= 177; x++) yard.prop(x, 66, "sandbag-straight-n", 0);
        // The pump hall's pumps, the ledger room's records and burn barrel, the radio room's set.
        yard.stock(PUMP_LEFT + 3, PUMP_BOTTOM + 8, "industrial-pump-set", 0, -9, -9);
        yard.stock(PUMP_LEFT + 7, PUMP_BOTTOM + 4, "industrial-pump-set", 0, -9, -9);
        Cell barrel = burnBarrel();
        if (!yard.stock(barrel.x(), barrel.y(), "industrial-drum-cluster", 0, ledgerTarget().x(), ledgerTarget().y()))
            throw new IllegalStateException("Ledger room has no room for its burn barrel");
        yard.stock(PUMP_LEFT + 23, PUMP_BOTTOM + 1, "office-records-bank", 0, ledgerTarget().x(), ledgerTarget().y());
        yard.stock(PUMP_LEFT + 23, PUMP_BOTTOM + 14, "industrial-switchboard", 0, PUMP_LEFT + 24, PUMP_BOTTOM + 12);
    }

    /** Pump house rooms, in the stamp's own columns and rows from its south-west corner. */
    static Area ledgerRoom() { return room(21, 1, 27, 5); }
    static Area countingRoom() { return room(13, 1, 19, 5); }
    static Area brokerOfficeRoom() { return room(13, 10, 19, 14); }
    static Area radioRoom() { return room(21, 10, 27, 14); }
    static Cell ledgerTarget() { return new Cell(PUMP_LEFT + 24, PUMP_BOTTOM + 3); }
    static Cell burnBarrel() { return new Cell(PUMP_LEFT + 27, PUMP_BOTTOM + 2); }
    static Cell brokerOffice() { return new Cell(PUMP_LEFT + 16, PUMP_BOTTOM + 12); }

    private static Area room(int l, int b, int r, int t) {
        return new Area(PUMP_LEFT + l, PUMP_BOTTOM + b, PUMP_LEFT + r, PUMP_BOTTOM + t);
    }

    private static void terrain(GenContext c) {
        boolean[] reserved = new boolean[WIDTH * HEIGHT];
        for (LandingPad pad : c.landingPads)
            for (int y = pad.bottom() - 3; y <= pad.top() + 3; y++)
                for (int x = 0; x <= pad.right() + 3; x++) reserved[y * WIDTH + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.DUST_LEDGER,
                c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }

    /**
     * The ridge under the escarpment: one tier up, climbed by the west ramp off the well lane and the east ramp
     * above the gate. Authored here rather than in the terrain document, which raises only masses nobody stands on.
     */
    static ElevationPlan ridgePlan() {
        List<ElevationPlan.Point> line = List.of(new ElevationPlan.Point(76, 93), new ElevationPlan.Point(100, 94),
                new ElevationPlan.Point(126, 93), new ElevationPlan.Point(150, 93), new ElevationPlan.Point(166, 92));
        return new ElevationPlan(WIDTH, HEIGHT, 2, 2, .35, 6140,
                List.of(new ElevationPlan.Plateau(1, 6.5, line, List.of(4.5, 6.0, 6.5, 6.5, 4.5), false)),
                List.of(new ElevationPlan.Ramp(point(WEST_RAMP_FOOT), WEST_RAMP_HEAD, 3),
                        new ElevationPlan.Ramp(point(EAST_RAMP_FOOT), EAST_RAMP_HEAD, 3)));
    }

    private static ElevationPlan.Point point(Cell cell) { return new ElevationPlan.Point(cell.x(), cell.y()); }

    private static void ridge(GenContext c) {
        ElevationBrush.stamp(ridgePlan(), c.grid, c.topology);
    }

    private static Layout layout(MapResult map, Sites s) {
        Map<PostRole, List<Cell>> posts = new EnumMap<>(PostRole.class);
        posts.put(PostRole.GATE, List.of(new Cell(168, 65), new Cell(172, 67), new Cell(176, 65)));
        posts.put(PostRole.WALL, List.of(new Cell(183, 68), new Cell(183, 31), new Cell(137, 68)));
        posts.put(PostRole.ROOF, List.of(new Cell(PUMP_LEFT + 4, PUMP_BOTTOM + 14), new Cell(PUMP_LEFT + 24, PUMP_BOTTOM + 13)));
        posts.put(PostRole.CLERKS, List.of(new Cell(PUMP_LEFT + 16, PUMP_BOTTOM + 7), new Cell(PUMP_LEFT + 16, PUMP_BOTTOM + 3),
                new Cell(PUMP_LEFT + 22, PUMP_BOTTOM + 4)));
        posts.put(PostRole.CRAWLER, List.of(new Cell(151, 62), new Cell(146, 53), new Cell(140, 55)));
        posts.put(PostRole.CANTINA, interiorCells(map, s.cantina, 4));
        return new Layout(map, APRON, COMPANY_START, HAULER, COUPLING_CRATE, ledgerRoom(), ledgerTarget(), burnBarrel(),
                countingRoom(), brokerOffice(), brokerOfficeRoom(), radioRoom(), s.pumpHouse, SKIMMER_PAD,
                SKIMMER_BOARDING, COMPOUND, new Area(COMPOUND.left() - RING_MARGIN, COMPOUND.bottom() - RING_MARGIN,
                        COMPOUND.right() + RING_MARGIN, COMPOUND.top() + RING_MARGIN),
                GATE, POSTERN, SOUTH_GATE, posts, s.cantina,
                new Cell(s.wellHouse.interiorAnchorX, s.wellHouse.interiorAnchorY), s.wellHouse,
                HARROW_YARD, CRATE_DROP, SALVAGE_TEAM_CELLS,
                List.of(new Cell(s.northShop.interiorAnchorX, s.northShop.interiorAnchorY),
                        new Cell(s.eastHome.interiorAnchorX, s.eastHome.interiorAnchorY)),
                List.of(WEST_CROSSING, SOUTH_CROSSING), overwatch(map), approachLines(),
                List.copyOf(s.warehouses), List.copyOf(s.shops), List.copyOf(s.homes));
    }

    /** Up to {@code count} walkable floor cells spread through a building, its interior anchor first. */
    private static List<Cell> interiorCells(MapResult map, PointOfInterest building, int count) {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(building.interiorAnchorX, building.interiorAnchorY));
        for (int y = building.top + 1; y < building.bottom && cells.size() < count; y += 2)
            for (int x = building.left + 1; x < building.right && cells.size() < count; x += 3)
                if (map.grid.isWalkable(x, y) && !map.grid.isDoorway(x, y)
                        && !cells.contains(new Cell(x, y))) cells.add(new Cell(x, y));
        return List.copyOf(cells);
    }

    /** Rim cells on the ridge's south lip above the compound, every third column. */
    private static List<Cell> overwatch(MapResult map) {
        ElevationMap elevation = map.grid.elevation();
        List<Cell> cells = new ArrayList<>();
        for (int x = COMPOUND.left(); x <= COMPOUND.right(); x += 3)
            for (int y = COMPOUND.top(); y < HEIGHT; y++) {
                if (elevation.tier(x, y) == 1 && !elevation.isFace(x, y) && !elevation.isRamp(x, y)
                        && map.grid.isWalkable(x, y)) {
                    cells.add(new Cell(x, y));
                    break;
                }
            }
        return List.copyOf(cells);
    }

    /** Every site reached from the company's start, and each approach open along its own line. */
    static void validate(Layout l) {
        NavigationGrid grid = l.map().grid;
        boolean[] reached = reach(grid, l.companyStart(), null);
        List<Cell> required = new ArrayList<>(List.of(l.hauler(), l.couplingCrate(), l.ledgerTarget(),
                l.brokerOffice(), l.skimmerBoarding(), l.gate(), l.postern(), l.southGate(), l.wellHouse(),
                l.crateDrop()));
        required.addAll(l.salvageTeam());
        for (List<Cell> cells : l.posts().values()) required.addAll(cells);
        required.addAll(l.spotterPosts());
        required.addAll(l.canalCrossings());
        required.addAll(l.ridgeOverwatch());
        for (PointOfInterest poi : l.map().pointsOfInterest) required.add(new Cell(poi.interiorAnchorX, poi.interiorAnchorY));
        for (LandingPad pad : l.map().landingPads) {
            if (!pad.isClear(grid, l.map().topology)) throw new IllegalStateException("Saltwell apron berth obstructed");
            required.add(new Cell(pad.centerX, pad.centerY));
        }
        for (Cell cell : required)
            if (!reached[grid.index(cell.x(), cell.y())]) throw new IllegalStateException("Saltwell site cut off: " + cell);
        for (int y = HAULER.y() - HAULER_HALF_LENGTH; y <= HAULER.y() + HAULER_HALF_LENGTH; y++)
            for (int x = HAULER.x() - HAULER_HALF_WIDTH; x <= HAULER.x() + HAULER_HALF_WIDTH; x++)
                if (!grid.isWalkable(x, y) || l.map().topology.getBuildingId(x, y) != 0)
                    throw new IllegalStateException("Saltwell hauler stand obstructed: " + x + "," + y);
        for (int y = l.ledgerTarget().y() - 2; y <= l.ledgerTarget().y() + 2; y++)
            for (int x = l.ledgerTarget().x() - 2; x <= l.ledgerTarget().x() + 2; x++)
                if (!l.ledgerRoom().contains(x, y)) throw new IllegalStateException("Ledger hold leaves its room");
        if (l.ridgeOverwatch().size() < 4) throw new IllegalStateException("Ridge overlooks too little of the compound");
        for (Approach approach : Approach.values())
            if (!approachOpen(grid, l.approaches().get(approach)))
                throw new IllegalStateException("Saltwell approach closed: " + approach);
        for (Cell cell : List.of(l.harrowYard().centre(), l.crateDrop(), l.wellHouse(), l.companyStart()))
            if (l.exclusionRing().contains(cell.x(), cell.y()))
                throw new IllegalStateException("Quiet-phase site inside the exclusion ring: " + cell);
        if (l.exclusionRing().contains(l.skimmerBoarding().x(), l.skimmerBoarding().y()))
            throw new IllegalStateException("Skimmer pad inside the exclusion ring");
    }

    /**
     * Whether each leg of a line connects within {@link #APPROACH_CORRIDOR} cells of the leg, so the approach is
     * open on its own ground rather than by going round through another one.
     */
    static boolean approachOpen(NavigationGrid grid, List<Cell> line) {
        for (int i = 1; i < line.size(); i++) {
            Cell a = line.get(i - 1), b = line.get(i);
            boolean[] corridor = new boolean[grid.getWidth() * grid.getHeight()];
            for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++)
                corridor[grid.index(x, y)] = distanceToLeg(x, y, a, b) <= APPROACH_CORRIDOR;
            if (!reach(grid, a, corridor)[grid.index(b.x(), b.y())]) return false;
        }
        return true;
    }

    private static double distanceToLeg(int x, int y, Cell a, Cell b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.x()) * dx + (y - a.y()) * dy) / length));
        return Math.hypot(x - a.x() - t * dx, y - a.y() - t * dy);
    }

    /** Cells a walker reaches from {@code start}, kept inside {@code within} when it is given. */
    static boolean[] reach(NavigationGrid grid, Cell start, boolean[] within) {
        int w = grid.getWidth();
        boolean[] reached = new boolean[w * grid.getHeight()];
        if (!grid.isWalkable(start.x(), start.y())) return reached;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        reached[grid.index(start.x(), start.y())] = true;
        queue.add(grid.index(start.x(), start.y()));
        while (!queue.isEmpty()) {
            int at = queue.remove(), x = at % w, y = at / w;
            for (Direction dir : Direction.ALL) {
                if (!grid.canTraverseCellStep(x, y, dir)) continue;
                int to = grid.index(x + dir.dx, y + dir.dy);
                if (reached[to] || (within != null && !within[to])) continue;
                reached[to] = true;
                queue.add(to);
            }
        }
        return reached;
    }
}
