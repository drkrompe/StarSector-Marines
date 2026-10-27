package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
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
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Saltwell Cross on Asharu, The Dust Ledger's authored map ({@code dust-ledger-crossing.md}): a public freight apron
 * on the west edge, a caravanserai market where the apron track meets the crawler road, the Harrow salvage yard on
 * the crawler road, a standable ridge growing out of the north escarpment, the meandering dry canal with its
 * irrigated plots and two crossings in the south, and Sable's pump house compound in the east, fed by the broken
 * terraforming pipeline from the canal. Mission behaviour belongs to the mission; this recipe lays the ground and
 * publishes the sites it needs as a {@link Layout}.
 *
 * <p>Buildings are published only as held places, so the map itself adds no capture point. y grows north.
 */
public final class DustLedgerCrossing {
    public static final int WIDTH = 240, HEIGHT = 112;

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

    /** Sable's posts by duty. */
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
     * @param posts            Sable's posts by duty, about eighteen in all
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
     * @param nests            the compound's three powered gun nests: gate drive, tank farm, east yard
     * @param pillboxes        the three manned pillboxes: compound gate, postern, pump house yard doors
     * @param feedJunction     where a squad stands to cut the guns' feed at the junction box
     * @param feedJunctionArea the junction's pad, outside the exclusion ring
     * @param technicalStands  where Sable's rocket truck may stand, its park in the east yard first; every one is
     *                         joined to the others by a lane a light hull clears, round the pump house's east side
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
                         List<PointOfInterest> warehouses, List<PointOfInterest> shops, List<PointOfInterest> homes,
                         List<Nest> nests, List<Pillbox> pillboxes, Cell feedJunction, Area feedJunctionArea,
                         List<Cell> technicalStands) {}

    /**
     * A gun nest: a light defence post (a sealed mount in a parapet ring, {@code DefensePostStamper}) on a paved pad.
     * The post is also in {@link MapResult#defensePosts}; spawning it, its side and its power are the mission's.
     *
     * @param cell   the mount, the post's anchor
     * @param facing the way its field of fire opens, toward what it covers
     * @param post   the stamped post, its turret spec ready for {@code BattleSetup.buildMap}
     */
    public record Nest(Cell cell, Direction facing, DefensePost post) {}

    /**
     * A pillbox: a hardened building held like any other, whose crew fights from its slits.
     *
     * @param building the building (a held place, never a capture point)
     * @param covers   the entrance it covers
     * @param role     the duty its posts come from
     * @param posts    the posts inside it, each a slit cell, already counted in {@link Layout#posts()}
     */
    public record Pillbox(PointOfInterest building, Cell covers, PostRole role, List<Cell> posts) {}

    /** The apron slab: three berths down its west side, freight stacked between and along the east. */
    static final Area APRON = new Area(4, 40, 29, 80);
    private static final int BERTH_X = 16;
    private static final int[] BERTH_Y = {45, 59, 73};
    static final Cell COMPANY_START = new Cell(16, 66);
    static final Cell HAULER = new Cell(28, 66);
    private static final int HAULER_HALF_WIDTH = 1, HAULER_HALF_LENGTH = 3;
    static final Cell COUPLING_CRATE = new Cell(24, 66);
    static final Cell CROSSROADS = new Cell(88, 60);

    static final Area COMPOUND = new Area(136, 30, 222, 70);
    private static final int RING_MARGIN = 5;
    static final Cell GATE = new Cell(198, 72);
    static final Cell POSTERN = new Cell(134, 38);
    static final Cell SOUTH_GATE = new Cell(155, 28);
    /** The pump house at the yard's heart; the motor pool and quarters west of it, workshop and stores east. */
    private static final int PUMP_LEFT = 172, PUMP_BOTTOM = 44;
    private static final int GARAGE_LEFT = 139, GARAGE_BOTTOM = 58;
    /** The bunkhouse and mess, east of the postern's yard so the postern pillbox sees it whole. */
    private static final int BUNK_LEFT = 146, BUNK_BOTTOM = 32, MESS_LEFT = 159, MESS_BOTTOM = 34;
    /** The terraforming pipeline's line, from the canal bank north through the south wall to the pump gear. */
    private static final int PIPELINE_X = 184;

    /** The gun nests' mounts, each on a paved pad: the gate drive, by the tank farm, the east yard. */
    private static final List<Cell> NEST_CELLS = List.of(new Cell(198, 63), new Cell(153, 47), new Cell(212, 47));
    private static final List<Direction> NEST_FACING = List.of(Direction.N, Direction.W, Direction.W);
    /** The light cannon on the drive, where a crawler would come in; machine guns where infantry does. */
    private static final List<String> NEST_GUNS = List.of(TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID);
    /** Cells of paved pad round a nest's mount, kept clear of dressing so its field of fire stays open. */
    private static final int NEST_PAD = 2;

    /**
     * The pillboxes, by the entrance each covers: a corner and quarter turns of {@link BuildingStamp#PILLBOX}, and its
     * posts as slit cells in the plan's own frame. The gate's looks across the drive, the postern's down on the
     * postern, the yard's over the pump house's east and south yard doors.
     */
    private record PillboxPlan(int left, int bottom, int turns, PostRole role, List<Cell> slits) {}
    private static final PillboxPlan GATE_PILLBOX = new PillboxPlan(189, 61, 3, PostRole.GATE,
            List.of(new Cell(1, 4), new Cell(5, 4), new Cell(6, 3)));
    private static final PillboxPlan POSTERN_PILLBOX = new PillboxPlan(138, 41, 2, PostRole.WALL,
            List.of(new Cell(1, 4), new Cell(3, 4)));
    private static final PillboxPlan YARD_PILLBOX = new PillboxPlan(197, 36, 0, PostRole.CLERKS,
            List.of(new Cell(3, 4), new Cell(0, 3)));

    /**
     * Sable's rocket truck's stands, its park first: the east yard by the ledger room's east door, the yard's south
     * end behind the clerks' pillbox, the pump house's south-east corner and the south yard beneath it, the lane
     * between the pump house and the workshop, and the gate drive's two sides. The east yard's cover leaves a lane
     * between them: north up the lane to the gate drive, south past the pillbox into the south yard.
     */
    static final List<Cell> TECHNICAL_STANDS = List.of(new Cell(206, 48), new Cell(205, 41), new Cell(196, 42),
            new Cell(190, 41), new Cell(203, 57), new Cell(202, 64), new Cell(209, 63));

    /** Sable's tap on the old terraforming power line: the junction box's work cell and its pad. */
    static final Cell FEED_JUNCTION = new Cell(122, 32);
    static final Area FEED_PAD = new Area(119, 30, 125, 35);

    static final Area SKIMMER_PAD = new Area(151, 5, 159, 10);
    static final Cell SKIMMER_BOARDING = new Cell(155, 11);
    static final Cell WEST_CROSSING = new Cell(82, 20);
    static final Cell SOUTH_CROSSING = new Cell(155, 20);

    static final Area HARROW_YARD = new Area(112, 55, 128, 64);
    static final Cell CRATE_DROP = new Cell(119, 62);
    static final Cell SALVAGE_TEAM = new Cell(126, 77);
    private static final List<Cell> SALVAGE_TEAM_CELLS = List.of(new Cell(124, 76), new Cell(126, 76),
            new Cell(124, 78), new Cell(126, 78));

    /** The caravanserai court at the crossroads' north-west corner, ringed by the cantina, shops and homes. */
    static final Area COURT = new Area(58, 73, 86, 81);

    /** The ridge's ramps: west at the spur's tip off the well lane, east above the gate. */
    static final Cell WEST_RAMP_FOOT = new Cell(96, 85), EAST_RAMP_FOOT = new Cell(192, 82);
    private static final ElevationPlan.Point WEST_RAMP_HEAD = new ElevationPlan.Point(96, 93);
    private static final ElevationPlan.Point EAST_RAMP_HEAD = new ElevationPlan.Point(192, 89);
    /** Cells either side of an approach's line its route may use. */
    static final int APPROACH_CORRIDOR = 6;

    private DustLedgerCrossing() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        Sites s = new Sites();
        new GenRecipe("Saltwell Cross", List.of(DustLedgerCrossing::grounds,
                context -> market(context, s), context -> compound(context, s), DustLedgerCrossing::ruins,
                context -> RoadRasterizer.clearReplacedSurfaces(context.topology, WIDTH, HEIGHT),
                DustLedgerCrossing::yardSurfaces, DustLedgerCrossing::groundWear,
                DustLedgerCrossing::apronFreight, DustLedgerCrossing::marketDressing,
                DustLedgerCrossing::compoundDressing, DustLedgerCrossing::hardpan, DustLedgerCrossing::terrain,
                DustLedgerCrossing::ridge, new FinalizeStage(), context -> nests(context, s),
                new OutdoorLandscapeStage(anchors()))).run(c);
        MapResult map = new MapResult(c.grid, c.topology, COMPANY_START.x(), COMPANY_START.y(),
                s.pumpHouse.interiorAnchorX, s.pumpHouse.interiorAnchorY, c.pois, c.doodads,
                new TacticalMap(HeldPlaces.fromBuildings(c.pois,
                        poi -> COMPOUND.contains(poi.interiorAnchorX, poi.interiorAnchorY) ? Faction.DEFENDER : Faction.CIVILIAN)),
                c.get(BspKeys.BUILDINGS), List.copyOf(c.defensePosts),
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
                l.ridgeOverwatch(), l.approaches(), l.warehouses(), l.shops(), l.homes(),
                l.nests(), l.pillboxes(), l.feedJunction(), l.feedJunctionArea(), l.technicalStands());
    }

    /** What the stages learned while building, read back into the layout. */
    private static final class Sites {
        PointOfInterest pumpHouse, cantina, wellHouse, northShop, eastHome;
        final List<PointOfInterest> warehouses = new ArrayList<>(), shops = new ArrayList<>(), homes = new ArrayList<>();
        final List<Nest> nests = new ArrayList<>();
        final List<Pillbox> pillboxes = new ArrayList<>();
    }

    private static int[] anchors() {
        List<Cell> cells = new ArrayList<>(List.of(COMPANY_START, HAULER, COUPLING_CRATE, CROSSROADS, GATE, POSTERN,
                SOUTH_GATE, SKIMMER_BOARDING, WEST_CROSSING, SOUTH_CROSSING, CRATE_DROP, SALVAGE_TEAM,
                WEST_RAMP_FOOT, EAST_RAMP_FOOT, FEED_JUNCTION));
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
        Cell apron = new Cell(40, 60);
        lines.put(Approach.MARKET, List.of(COMPANY_START, apron, CROSSROADS, new Cell(91, 46), new Cell(91, 40),
                new Cell(110, 39), new Cell(124, 40), POSTERN));
        lines.put(Approach.CRAWLER_ROAD, List.of(COMPANY_START, apron, CROSSROADS, new Cell(100, 66),
                new Cell(114, 73), new Cell(130, 77), new Cell(160, 78), new Cell(184, 77), GATE));
        lines.put(Approach.RIDGE, List.of(COMPANY_START, apron, CROSSROADS, new Cell(88, 74), new Cell(89, 84),
                WEST_RAMP_FOOT, new Cell(98, 95), new Cell(128, 93), new Cell(150, 92), new Cell(170, 93),
                new Cell(186, 92), new Cell(192, 92), EAST_RAMP_FOOT, new Cell(196, 77), GATE));
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
        var roads = RoadCatalog.plan(RoadCatalog.DUST_LEDGER);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        // Three berths on the public apron; Wayline's drop takes them all, one landing area.
        for (int y : BERTH_Y)
            c.landingPads.add(new LandingPad(BERTH_X, y, 7, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
        // Made ground, paved in yardSurfaces: the apron's slab, the compound yard and the skimmer pad.
        slab(c, APRON);
        slab(c, new Area(COMPOUND.left() + 1, COMPOUND.bottom() + 1, COMPOUND.right() - 1, COMPOUND.top() - 1));
        slab(c, SKIMMER_PAD);
        slab(c, FEED_PAD);
    }

    private static void slab(GenContext c, Area area) {
        for (int y = area.bottom(); y <= area.top(); y++) for (int x = area.left(); x <= area.right(); x++) {
            c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
            c.markMadeGround(x, y);
        }
    }

    /**
     * The caravanserai: a court at the crossroads' north-west corner, ringed by shops, the cantina, the warden's well
     * house and homes, each a two-cell alley from the next; the bonded warehouses south of the apron track; homes and
     * shops packed along the crawler road and the back lane.
     */
    private static void market(GenContext c, Sites s) {
        s.shops.add(stamp(c, BuildingStamp.CORNER_SHOP, 48, 64, 0));
        s.cantina = stamp(c, BuildingStamp.MARKET_HALL, 61, 65, 0);
        s.shops.add(s.cantina);
        s.wellHouse = stamp(c, BuildingStamp.FARMHOUSE, 76, 65, 0);
        s.shops.add(stamp(c, BuildingStamp.MARKET_HALL, 50, 76, 1));
        s.homes.add(stamp(c, BuildingStamp.COURT_HOUSE, 61, 82, 0));
        s.homes.add(stamp(c, BuildingStamp.CORNER_HOME, 75, 82, 0));
        // Between the well lane and the crawler road, facing the road.
        s.northShop = stamp(c, BuildingStamp.CORNER_SHOP, 93, 74, 0);
        s.shops.add(s.northShop);
        // South of the apron track: the bonded warehouses, staffed, a loading alley between them.
        s.warehouses.add(stamp(c, BuildingStamp.FREIGHT_L, 40, 38, 0));
        s.warehouses.add(stamp(c, BuildingStamp.OFFSET_DEPOT, 63, 45, 0));
        // East of the field track: houses fronting the crawler road and the back lane.
        s.homes.add(stamp(c, BuildingStamp.FARMHOUSE, 95, 53, 0));
        s.homes.add(stamp(c, BuildingStamp.COURT_HOUSE, 95, 42, 0));
        s.homes.add(stamp(c, BuildingStamp.CORNER_HOME, 110, 42, 0));
        s.eastHome = stamp(c, BuildingStamp.FARMHOUSE, 122, 42, 0);
        s.homes.add(s.eastHome);
        s.homes.add(s.wellHouse);
        s.homes.addAll(s.shops);
    }

    private static PointOfInterest stamp(GenContext c, BuildingStamp plan, int left, int bottom, int turns) {
        plan.place(c, left, bottom, turns);
        return c.pois.get(c.pois.size() - 1);
    }

    private static void compound(GenContext c, Sites s) {
        s.pumpHouse = stamp(c, BuildingStamp.PUMP_HOUSE, PUMP_LEFT, PUMP_BOTTOM, 0);
        // West, the motor pool and quarters: crawler garage, the vehicle shed, bunkhouse and mess.
        stamp(c, BuildingStamp.OFFSET_DEPOT, GARAGE_LEFT, GARAGE_BOTTOM, 2); // Crawler garage, doors on both long sides.
        stamp(c, BuildingStamp.BARN, 152, 59, 0); // Vehicle shed, its doors on the yard.
        stamp(c, BuildingStamp.CORNER_HOME, BUNK_LEFT, BUNK_BOTTOM, 2); // Bunkhouse, door on the yard.
        stamp(c, BuildingStamp.FARMHOUSE, MESS_LEFT, MESS_BOTTOM, 0); // Mess.
        // The pillboxes, each covering an entrance from inside, its door on the yard.
        for (PillboxPlan plan : List.of(GATE_PILLBOX, POSTERN_PILLBOX, YARD_PILLBOX)) {
            PointOfInterest building = stamp(c, BuildingStamp.PILLBOX, plan.left(), plan.bottom(), plan.turns());
            Cell covers = plan == GATE_PILLBOX ? GATE : plan == POSTERN_PILLBOX ? POSTERN
                    : new Cell(PUMP_LEFT + 22, PUMP_BOTTOM + 3);
            s.pillboxes.add(new Pillbox(building, covers, plan.role(), slitCells(plan)));
        }
        // East, the works: the pump workshop and the stores.
        stamp(c, BuildingStamp.T_WORKSHOP, 205, 50, 0);
        stamp(c, BuildingStamp.OFFSET_DEPOT, 206, 33, 0);
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

    /** The failed terraforming's roofless buildings on the hardpan (the damage plan wrecks them). */
    private static void ruins(GenContext c) {
        stamp(c, BuildingStamp.FARMHOUSE, 9, 6, 0);
        stamp(c, BuildingStamp.BARN, 115, 2, 0);
        stamp(c, BuildingStamp.T_WORKSHOP, 32, 86, 0);
        stamp(c, BuildingStamp.OFFSET_DEPOT, 138, 4, 1);
        stamp(c, BuildingStamp.FARMHOUSE, 228, 48, 0);
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
        compoundGround(c);
    }

    /**
     * The compound is not one slab: concrete round the pump house and down the drive from the gate, gravel beds in
     * the motor pool and the works yard, and worn earth with gravel patches everywhere else.
     */
    private static void compoundGround(GenContext c) {
        // Earth draws as its ground kind (surface 0), the same packed earth as the market's.
        int dirt = 0, stone = c.topology.surfaceIndex("floors.stone");
        PatchField patches = new PatchField(6156, 3f);
        for (int y = COMPOUND.bottom() + 1; y < COMPOUND.top(); y++)
            for (int x = COMPOUND.left() + 1; x < COMPOUND.right(); x++) {
                if (c.topology.getGroundKind(x, y) != GroundKind.COURTYARD || c.topology.isWall(x, y)) continue;
                boolean house = x >= PUMP_LEFT - 3 && x <= PUMP_LEFT + 25 && y >= PUMP_BOTTOM - 3 && y <= PUMP_BOTTOM + 16;
                boolean drive = Math.abs(x - GATE.x()) <= 3 && y >= PUMP_BOTTOM + 14;
                if (house || drive || nestPad(x, y)) continue;
                boolean bed = x <= 158 && y >= 44 && y <= 57 || x >= 200 && y <= 48;
                if (bed || patches.sample(x, y) < .25) {
                    c.topology.setGroundKind(x, y, GroundKind.STONE);
                    c.topology.setSurface(x, y, stone);
                } else {
                    c.topology.setGroundKind(x, y, GroundKind.DIRT);
                    c.topology.setSurface(x, y, dirt);
                }
            }
    }

    /**
     * Ground people have walked: the court and a packed apron round every building, sand drifted against their walls,
     * foot paths between doors and tracks, and the irrigated plots along the canal, furrowed with its bends.
     */
    private static void groundWear(GenContext c) {
        for (Cell cell : COURT.cells()) packed(c, cell.x(), cell.y());
        // Patches, not a rim: the trodden ground frays out two and three cells from a wall where feet go.
        PatchField fray = new PatchField(6153, 2.5f);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int near = buildingDistance(c, x, y);
            if (near == 0 || near > 3) continue;
            if (near == 2 && fray.sample(x, y) > .65 || near == 3 && fray.sample(x, y) > .3) continue;
            // A drift: on the windward west and north faces, sand piled against the wall survives the trampling.
            boolean drift = near == 1 && hash(x, y, 6151) < .45 && (buildingAt(c, x + 1, y) || buildingAt(c, x, y - 1));
            if (!drift) packed(c, x, y);
        }
        int[][] paths = {
                {60, 73, 60, 64}, {74, 73, 74, 64}, {86, 77, 87, 77}, {58, 78, 58, 85},
                {61, 55, 61, 44}, {52, 37, 52, 33}, {52, 33, 84, 33},
                {108, 41, 108, 62}, {121, 41, 121, 54}, {120, 65, 120, 70}, {92, 78, 90, 78}};
        for (int[] p : paths) footpath(c, p[0], p[1], p[2], p[3]);
        plots(c);
    }

    /** Natural ground trodden to packed earth; paving, buildings and fixtures are left as they are. */
    private static void packed(GenContext c, int x, int y) {
        if (!c.grid.inBounds(x, y)) return;
        GroundKind ground = c.topology.getGroundKind(x, y);
        if (ground == GroundKind.SAND || ground == GroundKind.STONE || ground == GroundKind.GRASS || ground == GroundKind.SNOW)
            c.topology.setGroundKind(x, y, GroundKind.DIRT);
    }

    private static boolean buildingAt(GenContext c, int x, int y) {
        return c.grid.inBounds(x, y) && c.topology.hasBuildingKindHint(x, y);
    }

    /** Chebyshev cells to the nearest building cell, up to three; zero on a building. */
    private static int buildingDistance(GenContext c, int x, int y) {
        if (buildingAt(c, x, y)) return 0;
        for (int r = 1; r <= 3; r++)
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++)
                if ((Math.abs(dx) == r || Math.abs(dy) == r) && buildingAt(c, x + dx, y + dy)) return r;
        return 4;
    }

    private static void footpath(GenContext c, int x0, int y0, int x1, int y1) {
        Cell a = new Cell(x0, y0), b = new Cell(x1, y1);
        for (int y = Math.min(y0, y1) - 1; y <= Math.max(y0, y1) + 1; y++)
            for (int x = Math.min(x0, x1) - 1; x <= Math.max(x0, x1) + 1; x++)
                if (distanceToLeg(x, y, a, b) <= .9) packed(c, x, y);
    }

    /**
     * Dead irrigated plots along the canal: a band five to twelve cells off its bank, cut into plots by earth bunds
     * across it, furrowed parallel to the water so the rows follow each bend. Some plots lie fallow.
     */
    private static void plots(GenContext c) {
        List<TerrainPlan.Stroke> canal = new ArrayList<>();
        for (TerrainPlan.Stroke stroke : TerrainCatalog.plan(TerrainCatalog.DUST_LEDGER, c.get(BspKeys.MARKET_PROFILE).surface()).strokes())
            if (stroke.mode() == TerrainPlan.Mode.EXCAVATE && stroke.radius() > 2.4) canal.add(stroke);
        int[][] north = {{28, 74}, {96, 146}}, south = {{36, 74}, {88, 112}};
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            if (c.topology.getGroundKind(x, y) != GroundKind.SAND) continue;
            double best = Double.MAX_VALUE, waterY = 0;
            for (TerrainPlan.Stroke stroke : canal) {
                TerrainPlan.Point p = stroke.points().get(0), q = stroke.points().get(stroke.points().size() - 1);
                Cell a = new Cell((int) p.x(), (int) p.y()), b = new Cell((int) q.x(), (int) q.y());
                double d = distanceToLeg(x, y, a, b) - stroke.radius();
                if (d < best) {
                    best = d;
                    waterY = x <= a.x() ? a.y() : x >= b.x() ? b.y() : a.y() + (b.y() - a.y()) * (x - a.x()) / (double) (b.x() - a.x());
                }
            }
            if (best < 3 || best > 10) continue;
            int[][] ranges = y > waterY ? north : south;
            boolean inside = false;
            for (int[] r : ranges) inside |= x >= r[0] && x <= r[1];
            if (!inside || x % 11 == 0) continue;
            int plot = x / 11 + (y > waterY ? 0 : 100);
            if (hash(plot, 0, 6152) < .22) continue;
            c.topology.setGroundKind(x, y, (int) best % 3 == 0 ? GroundKind.SAND : GroundKind.DIRT);
        }
    }

    /** A stable value in [0, 1) for a cell, independent of every random stream. */
    private static double hash(int x, int y, int salt) {
        long h = x * 73856093L ^ y * 19349663L ^ salt * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xffffff) / (double) 0x1000000;
    }

    /** The apron's freight: stacks between and beside the berths, the bowser, Wayline's hauler stand kept clear. */
    private static void apronFreight(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        yard.cargo(5, 51);
        yard.cargo(13, 52);
        yard.prop(21, 52, "stores-cargo-loader", 0);
        yard.cargo(5, 65);
        yard.prop(10, 66, "industrial-pallet-stack", 0);
        yard.cargo(6, 79);
        yard.cargo(15, 79);
        yard.prop(26, 42, "parked-tanker-truck", 0);
        for (int[] at : new int[][]{{27, 47}, {28, 47}, {27, 48}, {28, 53}, {27, 54}})
            yard.prop(at[0], at[1], "industrial-crate-stack", 0);
        yard.prop(26, 57, "industrial-pallet-stack", 0);
        yard.prop(28, 57, "industrial-drum-cluster", 0);
        yard.prop(26, 74, "parked-flatbed-truck", 0);
        yard.prop(27, 78, "industrial-crate-stack", 0);
        yard.prop(28, 78, "industrial-pallet-stack", 0);
        // Freight staged in the strips between berths and along the apron's west edge; place() keeps berths clear.
        for (int x = 6; x <= 26; x += 3) {
            for (int y : new int[]{51, 53, 65, 67, 79, 81}) {
                if (Math.abs(y - COMPANY_START.y()) <= 2 && (Math.abs(x - COMPANY_START.x()) <= 3
                        || Math.abs(x - COUPLING_CRATE.x()) <= 2)) continue;
                double roll = hash(x, y, 6154);
                if (roll < .35) place(c, x, y, "industrial-crate-stack", 0);
                else if (roll < .55) place(c, x, y, "industrial-pallet-stack", 0);
                else if (roll < .65) place(c, x, y, "industrial-drum-cluster", 0);
            }
        }
        for (int y = 42; y <= 78; y += 4) if (hash(5, y, 6155) < .6) place(c, 5, y, "industrial-crate-stack", 0);
        lamp(c, 29, 50, -1, 0);
        lamp(c, 29, 71, -1, 0);
    }

    /** Stalls clustered under awnings, carts and crawlers parked off the roads, cover strewn along the back lane. */
    private static void marketDressing(GenContext c) {
        // The court: the well at its heart, two stall clusters, a cart.
        place(c, 71, 77, "industrial-pump-set", 0);
        stall(c, 60, 75, 0);
        stall(c, 60, 78, 1);
        stall(c, 82, 74, 2);
        stall(c, 82, 77, 0);
        for (int[] at : new int[][]{{64, 74}, {65, 74}, {66, 79}, {76, 74}, {77, 74}, {69, 74}})
            place(c, at[0], at[1], "industrial-crate-stack", 0);
        place(c, 67, 75, "parked-utility-truck", 0);
        for (int x : new int[]{63, 80}) place(c, x, 76, "chair-south-yellow", 0);
        // Along the apron track: stalls on the south verge, a box van loading at the warehouses.
        stall(c, 75, 54, 1);
        stall(c, 80, 54, 2);
        place(c, 85, 55, "industrial-pallet-stack", 0);
        place(c, 64, 41, "parked-box-van", 0);
        place(c, 69, 41, "industrial-pallet-stack", 0);
        place(c, 71, 42, "industrial-crate-stack", 0);
        place(c, 60, 52, "industrial-dumpster", 0);
        // The crawler road's verges: a parked crawler and stalls at the corner, the Harrow yard.
        place(c, 106, 75, "parked-flatbed-truck", 0);
        place(c, 110, 76, "industrial-crate-stack", 0);
        stall(c, 104, 59, 0);
        place(c, 104, 62, "industrial-pallet-stack", 0);
        harrowYard(c);
        // The back lane: a broken line of fences, carts and stores between the homes and the plots.
        for (int x = 93; x <= 99; x++) place(c, x, 35, "industrial-fence-straight-h", 0);
        place(c, 102, 35, "industrial-crate-stack", 0);
        place(c, 103, 34, "industrial-crate-stack", 0);
        place(c, 111, 34, "parked-utility-truck", 0);
        place(c, 117, 35, "industrial-pallet-stack", 0);
        place(c, 124, 34, "industrial-drum-cluster", 0);
        for (int x = 126; x <= 129; x++) place(c, x, 35, "industrial-fence-straight-h", 0);
        barrier(c, 101, 41);
        barrier(c, 117, 41);
        for (int[] at : new int[][]{{72, 63}, {86, 64}, {92, 63}})
            if (open(c, at[0], at[1], 1, 1))
                StreetDressing.lamp(c.grid, c.topology, c.doodads, StreetDressing.LAMP, at[0], at[1], 0, -1);
    }

    /** Stacked cargo, crates three by two and a pallet beside them, wherever the ground lets each piece stand. */
    private static void cargo(GenContext c, int x, int y) {
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 3; dx++) place(c, x + dx, y + dy, "industrial-crate-stack", 0);
        place(c, x + 4, y, "industrial-pallet-stack", 0);
    }

    /** A two-cell jersey barrier along x, where its ground is free. */
    private static void barrier(GenContext c, int x, int y) {
        if (open(c, x, y, 2, 1)) StreetDressing.jerseyBarrier(c.grid, c.topology, c.doodads, x, y, true);
    }

    private static void lamp(GenContext c, int x, int y, int dx, int dy) {
        if (open(c, x, y, 1, 1)) StreetDressing.lamp(c.grid, c.topology, c.doodads, StreetDressing.LAMP_INDUSTRIAL, x, y, dx, dy);
    }

    /** A trestle stall with an awning over its keeper's side and a crate of stock beside it. */
    private static void stall(GenContext c, int x, int y, int colour) {
        if (!place(c, x, y, "mess-table-long", 0)) return;
        StreetDressing.awning(c.doodads, StreetDressing.AWNINGS.get(colour % StreetDressing.AWNINGS.size()),
                x + 1, y + 2, 0, -1);
        place(c, x + 3, y, "industrial-crate-stack", 0);
    }

    private static void harrowYard(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        yard.gravel(HARROW_YARD.left(), HARROW_YARD.bottom(), HARROW_YARD.right(), HARROW_YARD.top());
        yard.perimeter(new int[]{120, 64}, HARROW_YARD.left(), HARROW_YARD.bottom(), HARROW_YARD.right(),
                HARROW_YARD.bottom(), HARROW_YARD.right(), HARROW_YARD.top(), HARROW_YARD.left(), HARROW_YARD.top());
        // The stranded crawler, its dropped drive on the ground beside it, and the crew's stock.
        place(c, 115, 57, "parked-flatbed-truck", 0);
        place(c, 115, 60, "basin-scrap-plate-stack", 0);
        for (int[] at : new int[][]{{122, 57}, {124, 57}, {126, 59}, {114, 63}, {125, 62}})
            place(c, at[0], at[1], at[0] % 2 == 0 ? "field-salvage-machinery" : "field-salvage-metals", 0);
        place(c, 120, 57, "industrial-machine-tool", 0);
        place(c, 118, 58, "industrial-cable-reel", 0);
        place(c, 126, 56, "industrial-scrap-pile", 0);
    }

    /**
     * The yard as the crew works it, laid out for the fight in it: the tank farm and its pipe rack between the motor
     * pool and the pump house, the pump gear and the old pipeline south of the house, stacked cargo and a parked
     * truck in the north yard, a chicane and the emplacement behind the gate, and broken lines of barriers and crates
     * in the east yard, so the ledger room's yard doors are reached from cover rather than down one corridor.
     */
    private static void compoundDressing(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        // The tank farm in rows, fenced off from the motor pool, its pipe racks running to the pump hall.
        for (int y = 45; y <= 55; y += 2) for (int x = 160; x <= 166; x += 2) place(c, x, y, "industrial-fluid-tank", 0);
        for (int y = 44; y <= 57; y++) if (Math.abs(y - 50) > 1) place(c, 158, y, "industrial-fence-straight-v", 0);
        StreetDressing.pipeRun(c.grid, c.topology, c.doodads, 169, 45, 169, 56, 3);
        StreetDressing.pipeRun(c.grid, c.topology, c.doodads, 160, 58, 168, 58, 3);
        yard.prop(174, 36, "industrial-turbine-set", 0);
        yard.prop(179, 36, "industrial-reactor-housing", 0);
        yard.prop(187, 37, "industrial-pump-set", 0);
        yard.prop(191, 36, "industrial-generator", 0);
        place(c, 195, 38, "industrial-drum-cluster", 0);
        place(c, 196, 38, "industrial-drum-cluster", 0);
        StreetDressing.pipeRun(c.grid, c.topology, c.doodads, PIPELINE_X, 31, PIPELINE_X, 40, 3);
        // The motor pool: a crawler out of its garage, drums and a loader by the shed.
        place(c, 142, 50, "parked-flatbed-truck", 0);
        place(c, 146, 46, "industrial-drum-cluster", 0);
        place(c, 157, 55, "stores-cargo-loader", 0);
        place(c, 151, 44, "industrial-pallet-stack", 0);
        place(c, 146, 54, "parked-tanker-truck", 0);
        place(c, 152, 48, "parked-box-van", 0);
        for (int[] at : new int[][]{{140, 45}, {141, 45}, {155, 46}, {155, 52}, {150, 56}})
            place(c, at[0], at[1], "industrial-drum-cluster", 0);
        // The quarters' yard: stores and a generator between the mess and the pump gear.
        place(c, 164, 37, "industrial-generator", 0);
        place(c, 166, 33, "industrial-pallet-stack", 0);
        place(c, 168, 33, "industrial-crate-stack", 0);
        place(c, 169, 33, "industrial-crate-stack", 0);
        for (int x = 162; x <= 165; x++) place(c, x, 41, "sandbag-straight-s", 0);
        // The north yard: stacked cargo in staggered rows, the cargo truck, loose crates and pallets.
        cargo(c, 172, 61);
        cargo(c, 180, 65);
        cargo(c, 186, 61);
        cargo(c, 160, 66);
        place(c, 168, 64, "parked-cargo-truck", 1);
        for (int[] at : new int[][]{{177, 61}, {182, 60}, {191, 65}, {171, 68}, {176, 68}, {186, 68}, {165, 62}})
            place(c, at[0], at[1], at[0] % 2 == 0 ? "industrial-pallet-stack" : "industrial-crate-stack", 0);
        // Sandbags behind the gate, a staggered barrier, the boom across it, and the emplacement for a sentry tower.
        for (int x = 193; x <= 195; x++) place(c, x, 66, "sandbag-straight-n", 0);
        for (int x = 201; x <= 203; x++) place(c, x, 66, "sandbag-straight-n", 0);
        barrier(c, 196, 63);
        barrier(c, 199, 60);
        place(c, 206, 65, "fort-emplacement-n", 0);
        StreetDressing.boomGate(c.grid, c.topology, c.doodads, 195, 71, 1, 0);
        // The east yard, between the pump house and the works: cover in broken lines toward the ledger room's doors,
        // leaving the rocket truck its lane: north between the pump house and the workshop, south past the pillbox.
        barrier(c, 198, 53);
        barrier(c, 200, 47);
        for (int[] at : new int[][]{{199, 56}, {200, 49}, {198, 45}, {200, 38}, {197, 58}})
            place(c, at[0], at[1], "industrial-crate-stack", 0);
        place(c, 203, 46, "industrial-pallet-stack", 0);
        cargo(c, 196, 50);
        cargo(c, 214, 66);
        cargo(c, 209, 44);
        barrier(c, 214, 49);
        for (int[] at : new int[][]{{219, 36}, {219, 37}, {204, 35}, {215, 62}, {219, 58}, {210, 66}})
            place(c, at[0], at[1], "industrial-crate-stack", 0);
        for (int y = 52; y <= 55; y++) place(c, 200, y, "sandbag-straight-w", 0);
        place(c, 196, 57, "military-radar-dish", 0);
        place(c, 212, 46, "parked-utility-truck", 0);
        place(c, 213, 63, "industrial-scrap-pile", 0);
        place(c, 218, 45, "industrial-cable-reel", 0);
        // Sandbagged corners for the wall sentries.
        for (int x = 216; x <= 218; x++) place(c, x, 68, "sandbag-straight-n", 0);
        for (int x = 217; x <= 219; x++) place(c, x, 31, "sandbag-straight-s", 0);
        for (int x = 138; x <= 140; x++) place(c, x, 69, "sandbag-straight-n", 0);
        for (int[] at : new int[][]{{166, 60}, {220, 50}, {166, 42}, {213, 62}}) lamp(c, at[0], at[1], 1, 0);
        // Inside: the pump hall's pumps, the ledger room's records and burn barrel, the radio room's set.
        inside(c, PUMP_LEFT + 2, PUMP_BOTTOM + 8, "industrial-pump-set");
        inside(c, PUMP_LEFT + 2, PUMP_BOTTOM + 4, "industrial-pump-set");
        inside(c, PUMP_LEFT + 5, PUMP_BOTTOM + 6, "industrial-switchboard");
        inside(c, burnBarrel().x(), burnBarrel().y(), "industrial-drum-cluster");
        inside(c, PUMP_LEFT + 17, PUMP_BOTTOM + 1, "office-records-bank");
        inside(c, PUMP_LEFT + 17, PUMP_BOTTOM + 12, "industrial-switchboard");
        // The pipeline outside the wall: broken lengths along the canal bank, the old feed from the water.
        String[] lengths = {"industrial-pipe-bundle", "industrial-pipe-bundle-damaged", "industrial-pipe-bundle-destroyed"};
        for (int y = 26; y <= 28; y++) place(c, PIPELINE_X, y, lengths[y % 3], 0);
        int i = 0;
        for (int x = 190; x <= 202; x += 3) place(c, x, 27 + (i % 2), lengths[i++ % 3], 0);
        // The feed: Sable's tap on the town's old terraforming power line. The junction box on its pad south of
        // the back lane, the line carried overhead from it to the compound's west wall, its drum inside.
        place(c, FEED_JUNCTION.x() - 1, FEED_JUNCTION.y() + 1, "industrial-switchboard", 0);
        place(c, FEED_PAD.left() + 1, FEED_PAD.bottom() + 1, "industrial-cable-reel", 0);
        StreetDressing.pipeRun(c.grid, c.topology, c.doodads, FEED_PAD.right() + 1, FEED_JUNCTION.y() - 1,
                COMPOUND.left() - 1, FEED_JUNCTION.y() - 1, 3);
        place(c, COMPOUND.left() + 2, FEED_JUNCTION.y() - 1, "industrial-cable-reel", 0);
    }

    /** Wreckage of the old low-orbit battle and the terraforming's ruins framing the hardpan. */
    private static void hardpan(GenContext c) {
        place(c, 48, 90, "basin-scrap-hull-shell", 1);
        place(c, 68, 94, "basin-scrap-plate-stack", 0);
        place(c, 4, 86, "basin-scrap-wreck-frame", 3);
        place(c, 170, 1, "basin-scrap-rib-section", 2);
        place(c, 182, 2, "basin-scrap-plate-stack", 1);
        place(c, 56, 2, "basin-scrap-engine-break", 0);
        place(c, 128, 2, "basin-scrap-intake-section", 1);
        place(c, 26, 4, "basin-scrap-drive-section", 2);
        place(c, 226, 4, "basin-scrap-hull-shell", 0);
        place(c, 234, 62, "basin-scrap-wreck-frame", 1);
        place(c, 214, 4, "basin-scrap-engine-break", 2);
        for (int[] at : new int[][]{{20, 12}, {22, 11}, {46, 8}, {100, 4}, {104, 7}, {230, 34}, {232, 42}, {34, 82},
                {224, 66}, {236, 30}})
            place(c, at[0], at[1], "industrial-scrap-pile", 0);
    }

    /**
     * A prop on open ground if its whole footprint is free and clear of doors, roads and berths; returns whether it
     * stood. Dressing yields to circulation rather than refusing the map.
     */
    private static boolean place(GenContext c, int x, int y, String id, int turns) {
        DoodadDef def = TileRegistry.installed().doodad("doodad." + id);
        if (def == null) return false;
        Doodad prop = new Doodad(x, y, def, turns);
        if (!open(c, x, y, prop.footprintCellsX, prop.footprintCellsY)) return false;
        occupy(c, prop);
        return true;
    }

    private static boolean open(GenContext c, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) {
            if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy) || nestPad(xx, yy)
                    || c.topology.hasBuildingKindHint(xx, yy) || c.topology.getGroundKind(xx, yy) == GroundKind.STREET)
                return false;
            for (int ty = yy - 1; ty <= yy + 1; ty++) for (int tx = xx - 1; tx <= xx + 1; tx++)
                if (c.grid.inBounds(tx, ty) && c.grid.isDoorway(tx, ty)) return false;
            for (LandingPad pad : c.landingPads)
                if (xx >= pad.left() - 2 && xx <= pad.right() + 2 && yy >= pad.bottom() - 2 && yy <= pad.top() + 2)
                    return false;
            for (int hy = HAULER.y() - HAULER_HALF_LENGTH; hy <= HAULER.y() + HAULER_HALF_LENGTH; hy++)
                if (Math.abs(xx - HAULER.x()) <= HAULER_HALF_WIDTH && yy == hy) return false;
        }
        return true;
    }

    /** A fitting on a room's floor; the rooms are authored, so one that does not fit is a plan error. */
    private static void inside(GenContext c, int x, int y, String id) {
        Doodad prop = new Doodad(x, y, TileRegistry.installed().doodad("doodad." + id), 0);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++)
            if (!c.grid.isWalkable(xx, yy) || c.grid.isDoorway(xx, yy) || c.topology.isFixture(xx, yy))
                throw new IllegalStateException("Pump house fitting does not fit: " + id + " at " + x + "," + y);
        occupy(c, prop);
    }

    private static void occupy(GenContext c, Doodad prop) {
        for (int yy = prop.cellY; yy < prop.cellY + prop.footprintCellsY; yy++)
            for (int xx = prop.cellX; xx < prop.cellX + prop.footprintCellsX; xx++) {
                c.grid.setWalkable(xx, yy, false);
                c.grid.setSeeThrough(xx, yy, true);
                c.topology.setFixture(xx, yy, true);
            }
        c.doodads.add(prop);
    }

    /** Pump house rooms, in the stamp's own columns and rows from its south-west corner. */
    static Area ledgerRoom() { return room(16, 1, 21, 5); }
    static Area countingRoom() { return room(9, 1, 14, 5); }
    static Area brokerOfficeRoom() { return room(9, 9, 14, 12); }
    static Area radioRoom() { return room(16, 9, 21, 12); }
    static Cell ledgerTarget() { return new Cell(PUMP_LEFT + 19, PUMP_BOTTOM + 3); }
    static Cell burnBarrel() { return new Cell(PUMP_LEFT + 21, PUMP_BOTTOM + 1); }
    static Cell brokerOffice() { return new Cell(PUMP_LEFT + 11, PUMP_BOTTOM + 10); }

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
     * The ridge: a spur of the escarpment one tier up, swelling over the compound and tapering west toward the
     * market, climbed by the west ramp at its tip and the east ramp above the gate. Authored here rather than in the
     * terrain document, which raises only masses nobody stands on.
     */
    static ElevationPlan ridgePlan() {
        List<ElevationPlan.Point> line = List.of(new ElevationPlan.Point(98, 92), new ElevationPlan.Point(108, 95),
                new ElevationPlan.Point(120, 92), new ElevationPlan.Point(134, 94), new ElevationPlan.Point(148, 90),
                new ElevationPlan.Point(162, 92), new ElevationPlan.Point(178, 92), new ElevationPlan.Point(192, 91),
                new ElevationPlan.Point(206, 94));
        return new ElevationPlan(WIDTH, HEIGHT, 2, 2, .45, 6140,
                List.of(new ElevationPlan.Plateau(1, 7.5, line,
                        List.of(6.0, 6.0, 4.5, 7.5, 6.5, 6.0, 5.5, 6.0, 3.5), false)),
                List.of(new ElevationPlan.Ramp(point(WEST_RAMP_FOOT), WEST_RAMP_HEAD, 4),
                        new ElevationPlan.Ramp(point(EAST_RAMP_FOOT), EAST_RAMP_HEAD, 4)));
    }

    private static ElevationPlan.Point point(Cell cell) { return new ElevationPlan.Point(cell.x(), cell.y()); }

    /** A pillbox's slit cells in world cells, posed as the pillbox is. */
    private static List<Cell> slitCells(PillboxPlan plan) {
        List<Cell> out = new ArrayList<>();
        for (Cell slit : plan.slits()) {
            int[] at = BuildingStamp.PILLBOX.posed(slit.x(), slit.y(), plan.turns());
            out.add(new Cell(plan.left() + at[0], plan.bottom() + at[1]));
        }
        return List.copyOf(out);
    }

    private static boolean nestPad(int x, int y) {
        for (Cell nest : NEST_CELLS)
            if (Math.abs(x - nest.x()) <= NEST_PAD && Math.abs(y - nest.y()) <= NEST_PAD) return true;
        return false;
    }

    /**
     * The gun nests, stamped once the ground is final: the light post's parapet ring round one sealed mount, on its
     * paved pad. Unmanned and powered; whether they spawn, for whom, and whether the feed still runs are the mission's.
     */
    private static void nests(GenContext c, Sites s) {
        for (int i = 0; i < NEST_CELLS.size(); i++) {
            Cell at = NEST_CELLS.get(i);
            DefensePost post = DefensePostStamper.stampNest(c.grid, c.topology, c.doodads, at.x(), at.y(), NEST_GUNS.get(i));
            c.defensePosts.add(post);
            s.nests.add(new Nest(at, NEST_FACING.get(i), post));
        }
    }

    private static void ridge(GenContext c) {
        ElevationBrush.stamp(ridgePlan(), c.grid, c.topology);
    }

    private static Layout layout(MapResult map, Sites s) {
        Map<PostRole, List<Cell>> posts = new EnumMap<>(PostRole.class);
        // Gate, postern and yard posts stand at the pillboxes' slits; the count per duty is unchanged.
        posts.put(PostRole.GATE, slitCells(GATE_PILLBOX));
        List<Cell> wall = new ArrayList<>(List.of(new Cell(217, 67)));
        wall.addAll(slitCells(POSTERN_PILLBOX));
        posts.put(PostRole.WALL, List.copyOf(wall));
        posts.put(PostRole.ROOF, List.of(new Cell(PUMP_LEFT + 3, PUMP_BOTTOM + 12), new Cell(PUMP_LEFT + 19, PUMP_BOTTOM + 11)));
        List<Cell> clerks = new ArrayList<>(List.of(new Cell(PUMP_LEFT + 12, PUMP_BOTTOM + 7)));
        clerks.addAll(slitCells(YARD_PILLBOX));
        posts.put(PostRole.CLERKS, List.copyOf(clerks));
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
                List.copyOf(s.warehouses), List.copyOf(s.shops), List.copyOf(s.homes),
                List.copyOf(s.nests), List.copyOf(s.pillboxes), FEED_JUNCTION, FEED_PAD, TECHNICAL_STANDS);
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
        required.add(l.feedJunction());
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
        if (l.exclusionRing().contains(l.feedJunction().x(), l.feedJunction().y()))
            throw new IllegalStateException("Feed junction inside the exclusion ring");
        for (Pillbox pillbox : l.pillboxes())
            for (Cell post : pillbox.posts())
                if (l.map().topology.getBuildingId(post.x(), post.y()) == 0)
                    throw new IllegalStateException("Pillbox post outside its pillbox: " + post);
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
