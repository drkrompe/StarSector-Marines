package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.damage.DamageCatalog;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.nature.FormationBrush;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.gen.road.AuthoredRoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * The Sounding on Warion, Blind Watch's authored map ({@code blind-watch-sounding.md}): Sable Wake's listening post in
 * a small crater on an airless moon. The company lands on the west behind a low ridge; an open regolith flat crossed
 * by the rover track runs east to the crater floor, a boulder field to its north climbs to the crater rim, and a dry
 * rille to its south runs under the dish field. On the rim stand the ranging mast and two gun nests; on the floor the
 * fenced dish field with its control hut, the prefab hab cluster and a lander pad; on a knoll in the south-east the
 * uplink. Mission behaviour belongs to the mission; this recipe lays the ground and publishes the sites it needs as a
 * {@link Layout}.
 *
 * <p>Buildings are published only as held places, so the map itself adds no capture point. y grows north.
 */
public final class BlindWatchSounding {
    public static final int WIDTH = 216, HEIGHT = 112;

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

    /** The three ways to the post the map keeps open, each a line of waypoints from the landing ground. */
    public enum Approach { FLAT, BOULDERS, RILLE }

    /** The post's three installations, each a charge site; its id is stable for the mission and its markers. */
    public enum Installation {
        MAST("blind-watch-mast"), UPLINK("blind-watch-uplink"), DISH_FIELD("blind-watch-dish-field");

        public final String id;

        Installation(String id) { this.id = id; }
    }

    /** Sable Wake's posts by duty. */
    public enum PostRole { RIM, MAST, HAB, DISH_FIELD, UPLINK, MOTOR_POOL, WATCH_BOSS }

    /**
     * A charge site: where a squad sets the charge, and the ground the installation stands on.
     *
     * @param id    the installation's stable id
     * @param plant the walkable cell a squad works from
     * @param area  the installation's ground
     */
    public record ChargeSite(String id, Cell plant, Area area) {}

    /**
     * Every site the mission names. Cells are walkable floor unless said otherwise.
     *
     * @param landing         the landing ground behind the ridge, every berth one landing area
     * @param companyStart    where the company forms up between the berths
     * @param sites           the three charge sites by installation
     * @param rim             the crater rim's top, tier one, where the mast and the rim guns stand
     * @param rimRamps        the rim's ramp feet: the boulder field's at its west tip first, then the crater floor's
     * @param knoll           the uplink knoll's top, tier one
     * @param knollRamp       the foot of the knoll's one ramp, on the crater floor
     * @param dishField       the fenced dish field, fence included
     * @param controlHut      the dish field's control hut
     * @param console         the cell a squad leader holds at the control hut's console
     * @param habCluster      the hab cluster's ground: its modules and the street between them
     * @param bunks           the two bunk modules
     * @param signalsModule   the signals racks' module
     * @param signalsTechs    where the hired signals technicians shelter, inside the signals module
     * @param riggersModule   the riggers' module
     * @param riggers         where the rigging crew waits, inside their module
     * @param riggersWalkOut  the riggers' way out, from their module's door to the landing ground
     * @param landerPad       Sable Wake's lander pad on the crater floor (scenery, not a landing berth)
     * @param podRacks        the cold drop's cryo pod racks between the riggers' module and the lander pad
     * @param podHold         the aisle cell a squad holds the racks from
     * @param chemModule      the cold drop's chem module beside the pad
     * @param chemist         where the chemist works, inside the chem module
     * @param drugCrates      the chem module's crates on pallets beside the pad
     * @param cargoCrawler    the half-loaded cargo crawler parked on the lander pad
     * @param landerCrew      where the lander crew stands, beside the crawler
     * @param posts           Sable Wake's posts by duty, about two dozen in all
     * @param nests           the two rim guns beside the mast
     * @param pillboxes       the two bermed pillboxes: the hab's airlock, the foot of the uplink ramp
     * @param mortarStart     where the post's mortar carrier starts, in the motor pool
     * @param mortarLane      the carrier's stops: out of the motor pool past the chem module onto the lander pad, up
     *                        the rover track below the rim and back, down the hab street to the dish field's gate,
     *                        below the uplink, and home; its hull drives each stop to the next
     * @param technicalStands where the rocket truck may stand on the knoll, its park first; a light hull drives to
     *                        every one up the knoll's ramp
     * @param rilleCrossings  west crossing first, then the middle one
     * @param boulderCover    walkable cells beside a boulder, in the boulder field and along the rille's south bank
     * @param approaches      waypoints of each approach, landing ground to the installation it leads to
     */
    public record Layout(MapResult map, Area landing, Cell companyStart, Map<Installation, ChargeSite> sites,
                         Area rim, List<Cell> rimRamps, Area knoll, Cell knollRamp,
                         Area dishField, PointOfInterest controlHut, Cell console,
                         Area habCluster, List<PointOfInterest> bunks, PointOfInterest signalsModule,
                         List<Cell> signalsTechs, PointOfInterest riggersModule, List<Cell> riggers,
                         List<Cell> riggersWalkOut, Area landerPad, Area podRacks, Cell podHold,
                         PointOfInterest chemModule, Cell chemist, Area drugCrates, Cell cargoCrawler,
                         List<Cell> landerCrew, Map<PostRole, List<Cell>> posts, List<Nest> nests, List<Pillbox> pillboxes,
                         Cell mortarStart, List<Cell> mortarLane, List<Cell> technicalStands,
                         List<Cell> rilleCrossings, List<Cell> boulderCover, Map<Approach, List<Cell>> approaches) {}

    /**
     * A gun nest: a light defence post (a sealed mount in a parapet ring, {@code DefensePostStamper}) on the rim.
     * The post is also in {@link MapResult#defensePosts}; spawning it, its side and its power are the mission's.
     *
     * @param cell   the mount, the post's anchor
     * @param facing the way its field of fire opens, toward what it covers
     * @param post   the stamped post, its turret spec ready for {@code BattleSetup.buildMap}
     */
    public record Nest(Cell cell, Direction facing, DefensePost post) {}

    /**
     * A regolith-bermed pillbox: a hardened building held like any other, whose crew fights from its slits.
     *
     * @param building the building (a held place, never a capture point)
     * @param covers   the ground it covers
     * @param role     the duty its posts come from
     * @param posts    the posts inside it, each a slit cell, already counted in {@link Layout#posts()}
     */
    public record Pillbox(PointOfInterest building, Cell covers, PostRole role, List<Cell> posts) {}

    /** The landing ground: three berths in a column behind the ridge, one landing area. */
    static final Area LANDING = new Area(3, 36, 31, 80);
    private static final int BERTH_X = 15;
    private static final int[] BERTH_Y = {44, 58, 72};
    static final Cell COMPANY_START = new Cell(15, 65);

    /** The crater rim's line, tier one, and its two ramps: the boulder field's at the west tip, the floor's. */
    private static final List<ElevationPlan.Point> RIM_LINE = List.of(point(119, 72), point(124, 80), point(132, 88),
            point(146, 95), point(162, 98), point(178, 96), point(192, 92), point(200, 87));
    private static final List<Double> RIM_RADII = List.of(6.0, 6.5, 7.5, 8.0, 8.0, 7.5, 6.5, 4.5);
    static final Cell RIM_WEST_FOOT = new Cell(113, 84), RIM_FLOOR_FOOT = new Cell(170, 80);
    private static final ElevationPlan.Point RIM_WEST_HEAD = point(125, 84), RIM_FLOOR_HEAD = point(170, 94);
    static final Area RIM = new Area(112, 64, 206, 108);

    /** The uplink knoll, tier one, with its one ramp up from the crater floor's west. */
    private static final List<ElevationPlan.Point> KNOLL_LINE = List.of(point(188, 24), point(198, 25));
    private static final double KNOLL_RADIUS = 9;
    static final Cell KNOLL_FOOT = new Cell(170, 24);
    private static final ElevationPlan.Point KNOLL_HEAD = point(183, 24);
    static final Area KNOLL = new Area(179, 15, 207, 34);

    /** The ranging mast's pad on the rim, its plant cell on the pad's south edge. */
    static final Area MAST_PAD = new Area(131, 88, 136, 93);
    static final Cell MAST_PLANT = new Cell(133, 87);
    /** The uplink's pad on the knoll and its plant cell. */
    static final Area UPLINK_PAD = new Area(190, 22, 196, 28);
    static final Cell UPLINK_PLANT = new Cell(193, 21);

    /**
     * The dish field's fence, the six dishes on their pads, and its gates on the north and south fences. Each dish is
     * four cells across, its corner one cell south-west of its listed cell, on a six-cell footing.
     */
    static final Area DISH_FIELD = new Area(130, 16, 166, 38);
    private static final int[] DISH_X = {144, 152, 160}, DISH_Y = {22, 31};
    private static final int DISH_CELLS = 4;
    static final Cell DISH_PLANT = new Cell(152, 27);
    static final Cell DISH_NORTH_GATE = new Cell(150, 39), DISH_SOUTH_GATE = new Cell(150, 15);
    private static final int HUT_LEFT = 132, HUT_BOTTOM = 26;

    /** The hab cluster: two rows of prefab modules either side of the hab street, the rover track down its middle. */
    static final Area HAB = new Area(150, 48, 182, 66);
    private static final int ROW_NORTH = 61, ROW_SOUTH = 49, COLUMN_WEST = 152, COLUMN_EAST = 168;
    static final Area LANDER_PAD = new Area(188, 56, 202, 68);
    static final Area MOTOR_POOL = new Area(185, 36, 199, 45);
    static final Cell MORTAR_START = new Cell(192, 40);
    /**
     * The cold drop between the riggers' module and the lander pad: the cryo pod racks (two rows and the aisle a squad
     * holds them from) and the chem module; on the pad its crates on pallets and the cargo crawler half loaded. The
     * ground east of the chem module stays open: it is the mortar carrier's way out of the motor pool.
     */
    static final Area POD_RACKS = new Area(185, 47, 190, 53);
    static final Cell POD_HOLD = new Cell(187, 50);
    private static final int CHEM_LEFT = 192, CHEM_BOTTOM = 48;
    static final Area DRUG_CRATES = new Area(201, 60, 203, 66);
    static final Cell CARGO_CRAWLER = new Cell(194, 61);

    /**
     * The impact craters on the flat and the landing ground: centre and rim radius. Their rims are raised in the
     * terrain document ({@code blind-watch.terrain.json}), which these mirror; here they place the clean bowls, the
     * ejecta thrown round them and the boulders it left.
     */
    private record Crater(int x, int y, double radius) {}
    private static final List<Crater> CRATERS = List.of(new Crater(82, 74, 9), new Crater(104, 70, 5.5),
            new Crater(60, 70, 3.5), new Crater(86, 42, 8), new Crater(62, 42, 5), new Crater(110, 36, 3.5),
            new Crater(101, 49, 2.5), new Crater(31, 51, 2.2), new Crater(8, 86, 4), new Crater(24, 93, 3),
            new Crater(10, 9, 5), new Crater(27, 33, 2.2), new Crater(101, 85, 3), new Crater(46, 8, 3.5),
            new Crater(134, 46, 3));

    /**
     * Dead rovers and debris on the flat and the landing ground, each a prop id and its corner: a survey rover
     * stripped where it died, a stack of hull plate, an armoured crawler run off the track, an intake torn from a
     * wreck and a rib section on the landing ground's north.
     */
    private record Wreck(String id, int x, int y, int turns) {}
    private static final List<Wreck> WRECKS = List.of(new Wreck("parked-utility-truck", 66, 62, 0),
            new Wreck("basin-scrap-plate-stack", 70, 31, 0), new Wreck("parked-armored-apc", 113, 61, 0),
            new Wreck("basin-scrap-intake-section", 48, 50, 0), new Wreck("basin-scrap-rib-section", 10, 98, 1));

    /** The rille's two crossings, west then middle. */
    static final Cell WEST_CROSSING = new Cell(61, 20), MIDDLE_CROSSING = new Cell(103, 20);

    /**
     * The rim guns, both beside the mast: one west of it over the rim's west ramp and the boulder approach, one east
     * of it over the crater floor. Neither has a line over the flat or the rover track; the flat is the mortar's,
     * firing on what the mast sees. Machine guns, since what comes up the boulders is infantry.
     */
    static final List<Cell> NEST_CELLS = List.of(new Cell(127, 90), new Cell(143, 93));
    private static final List<Direction> NEST_FACING = List.of(Direction.W, Direction.S);
    private static final List<String> NEST_GUNS = List.of(TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID);
    /** Cells of pad round a nest's mount, kept clear of dressing so its field of fire stays open. */
    private static final int NEST_PAD = 2;

    /**
     * The pillboxes: a corner and quarter turns of {@link BuildingStamp#PILLBOX}, and its posts as slit cells in the
     * plan's own frame. The airlock's slits look west down the rover track across the flat; the ramp's west over the
     * ground between the dish field and the knoll.
     */
    private record PillboxPlan(int left, int bottom, int turns, PostRole role, Cell covers, List<Cell> slits) {}
    private static final PillboxPlan AIRLOCK_PILLBOX = new PillboxPlan(141, 60, 1, PostRole.HAB, new Cell(136, 57),
            List.of(new Cell(1, 4), new Cell(3, 4), new Cell(5, 4)));
    private static final PillboxPlan RAMP_PILLBOX = new PillboxPlan(168, 12, 1, PostRole.UPLINK, KNOLL_FOOT,
            List.of(new Cell(1, 4), new Cell(3, 4), new Cell(5, 4)));

    /** The rocket truck's stands on the knoll top, its park first. */
    static final List<Cell> TECHNICAL_STANDS = List.of(new Cell(187, 25), new Cell(195, 21), new Cell(189, 29),
            new Cell(198, 29), new Cell(199, 24));

    /** Cells either side of an approach's line its route may use. */
    static final int APPROACH_CORRIDOR = 6;

    private BlindWatchSounding() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        Sites s = new Sites();
        new GenRecipe("The Sounding", List.of(BlindWatchSounding::grounds, BlindWatchSounding::terrain,
                BlindWatchSounding::elevation, context -> post(context, s),
                context -> RoadRasterizer.clearReplacedSurfaces(context.topology, WIDTH, HEIGHT),
                BlindWatchSounding::padSurfaces, context -> dressing(context, s), new FinalizeStage(),
                context -> nests(context, s), BlindWatchSounding::boulders, BlindWatchSounding::regolith)).run(c);
        MapResult map = new MapResult(c.grid, c.topology, COMPANY_START.x(), COMPANY_START.y(),
                s.signals.interiorAnchorX, s.signals.interiorAnchorY, c.pois, c.doodads,
                new TacticalMap(HeldPlaces.fromBuildings(c.pois, poi -> Faction.DEFENDER)),
                c.get(BspKeys.BUILDINGS), List.copyOf(c.defensePosts),
                AuthoredRoadGraph.graph(RoadCatalog.plan(RoadCatalog.BLIND_WATCH), ""), c.landingPads);
        Layout layout = layout(map, s);
        validate(layout);
        return withDamage(layout);
    }

    /** The same layout over the map with the post's season of wear applied. */
    private static Layout withDamage(Layout l) {
        MapResult damaged = l.map().withInitialDamage(DamageCatalog.blindWatch());
        return new Layout(damaged, l.landing(), l.companyStart(), l.sites(), l.rim(), l.rimRamps(), l.knoll(),
                l.knollRamp(), l.dishField(), l.controlHut(), l.console(), l.habCluster(), l.bunks(),
                l.signalsModule(), l.signalsTechs(), l.riggersModule(), l.riggers(), l.riggersWalkOut(),
                l.landerPad(), l.podRacks(), l.podHold(), l.chemModule(), l.chemist(), l.drugCrates(),
                l.cargoCrawler(), l.landerCrew(), l.posts(), l.nests(), l.pillboxes(), l.mortarStart(), l.mortarLane(),
                l.technicalStands(), l.rilleCrossings(), l.boulderCover(), l.approaches());
    }

    /** What the stages learned while building, read back into the layout. */
    private static final class Sites {
        PointOfInterest controlHut, signals, riggers, chem;
        final List<PointOfInterest> bunks = new ArrayList<>();
        final List<Nest> nests = new ArrayList<>();
        final List<Pillbox> pillboxes = new ArrayList<>();
        final List<Cell> boulderCover = new ArrayList<>();
    }

    private static ElevationPlan.Point point(double x, double y) { return new ElevationPlan.Point(x, y); }

    private static ElevationPlan.Point point(Cell cell) { return point(cell.x(), cell.y()); }

    /** The approaches' lines as authored, from the company's start to the installation each leads to. */
    static Map<Approach, List<Cell>> approachLines() {
        Map<Approach, List<Cell>> lines = new EnumMap<>(Approach.class);
        lines.put(Approach.FLAT, List.of(COMPANY_START, new Cell(33, 58), new Cell(44, 58), new Cell(70, 57),
                new Cell(100, 56), new Cell(126, 57), AIRLOCK_PILLBOX.covers(), new Cell(148, 57)));
        lines.put(Approach.BOULDERS, List.of(COMPANY_START, new Cell(33, 76), new Cell(45, 88), new Cell(60, 94),
                new Cell(78, 98), new Cell(94, 96), new Cell(106, 92), RIM_WEST_FOOT, new Cell(126, 84), MAST_PLANT));
        lines.put(Approach.RILLE, List.of(COMPANY_START, new Cell(28, 40), new Cell(48, 31), new Cell(58, 26),
                WEST_CROSSING, new Cell(64, 12), new Cell(84, 9), new Cell(110, 8), new Cell(134, 6),
                new Cell(146, 9), DISH_SOUTH_GATE, DISH_PLANT));
        return lines;
    }

    /** The riggers' way out: their module's door, the hab street, the rover track west, the ridge's middle pass. */
    static List<Cell> riggersWalkOut(PointOfInterest module) {
        return List.of(new Cell(module.anchorCellX, module.anchorCellY), new Cell(146, 57), new Cell(126, 57),
                new Cell(100, 56), new Cell(70, 57), new Cell(44, 58), new Cell(33, 58), COMPANY_START);
    }

    /** The mortar carrier's stops in order, a loop from the motor pool out to each installation and back. */
    static List<Cell> mortarLane() {
        Cell pad = new Cell(188, 59);
        return List.of(MORTAR_START, new Cell(202, 50), pad, new Cell(170, 76), pad, new Cell(150, 45),
                new Cell(173, 37), MORTAR_START);
    }

    private static void grounds(GenContext c) {
        // Bare regolith; the open ground is dressed at the end, once everything built has claimed its own.
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, GroundKind.STONE);
        }
        var roads = RoadCatalog.plan(RoadCatalog.BLIND_WATCH);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        // Three berths behind the ridge; the company's boats take them all, one landing area.
        for (int y : BERTH_Y)
            c.landingPads.add(new LandingPad(BERTH_X, y, 7, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FALLBACK));
        // Made ground, paved in padSurfaces: the lander pad, the mast's and the uplink's pads, the motor pool.
        slab(c, LANDER_PAD);
        slab(c, MOTOR_POOL);
        for (int x : DISH_X) for (int y : DISH_Y) slab(c, new Area(x - 2, y - 2, x + DISH_CELLS - 1, y + DISH_CELLS - 1));
    }

    private static void slab(GenContext c, Area area) {
        for (int y = area.bottom(); y <= area.top(); y++) for (int x = area.left(); x <= area.right(); x++) {
            c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
            c.markMadeGround(x, y);
        }
    }

    /** The landing ridge, the rille, the impact craters on the flat and the crater's outer wall. */
    private static void terrain(GenContext c) {
        boolean[] reserved = new boolean[WIDTH * HEIGHT];
        for (LandingPad pad : c.landingPads)
            for (int y = pad.bottom() - 3; y <= pad.top() + 3; y++)
                for (int x = 0; x <= pad.right() + 3; x++) reserved[y * WIDTH + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.BLIND_WATCH,
                c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }

    /**
     * The crater rim and the uplink knoll, one tier up. Authored here rather than in the terrain document, which
     * raises only masses nobody stands on. Stamped before anything is built, so the rim and the knoll carry the
     * mast, the guns and the uplink.
     */
    static ElevationPlan elevationPlan() {
        return new ElevationPlan(WIDTH, HEIGHT, 2, 2, .45, 7140,
                List.of(new ElevationPlan.Plateau(1, 8.5, RIM_LINE, RIM_RADII, false),
                        new ElevationPlan.Plateau(1, KNOLL_RADIUS, KNOLL_LINE)),
                List.of(new ElevationPlan.Ramp(point(RIM_WEST_FOOT), RIM_WEST_HEAD, 4),
                        new ElevationPlan.Ramp(point(RIM_FLOOR_FOOT), RIM_FLOOR_HEAD, 5),
                        new ElevationPlan.Ramp(point(KNOLL_FOOT), KNOLL_HEAD, 6)));
    }

    private static void elevation(GenContext c) {
        ElevationBrush.stamp(elevationPlan(), c.grid, c.topology);
    }

    /**
     * The post: the hab cluster's prefab modules either side of the hab street (north the long bunkhouse and the short
     * bunk module joined by a tube; south the signals racks' module behind its airlock and the riggers' module, a
     * narrow tube between them), the dish field's control hut, and the two bermed pillboxes.
     */
    private static void post(GenContext c, Sites s) {
        s.bunks.add(stamp(c, BuildingStamp.PREFAB_BUNKHOUSE, COLUMN_WEST - 1, ROW_NORTH, 0));
        stamp(c, BuildingStamp.PREFAB_TUBE, COLUMN_EAST - 2, ROW_NORTH + 1, 0);
        s.bunks.add(stamp(c, BuildingStamp.PREFAB_SHORT_MODULE, COLUMN_EAST + 3, ROW_NORTH, 0));
        s.signals = stamp(c, BuildingStamp.PREFAB_LOCK_MODULE, COLUMN_WEST, ROW_SOUTH, 2);
        stamp(c, BuildingStamp.PREFAB_NARROW_TUBE, COLUMN_EAST - 3, ROW_SOUTH + 1, 2);
        s.riggers = stamp(c, BuildingStamp.PREFAB_MODULE, COLUMN_EAST, ROW_SOUTH, 2);
        s.chem = stamp(c, BuildingStamp.CONTROL_HUT, CHEM_LEFT, CHEM_BOTTOM, 2);
        s.controlHut = stamp(c, BuildingStamp.CONTROL_HUT, HUT_LEFT, HUT_BOTTOM, 0);
        for (PillboxPlan plan : List.of(AIRLOCK_PILLBOX, RAMP_PILLBOX)) {
            PointOfInterest building = stamp(c, BuildingStamp.PILLBOX, plan.left(), plan.bottom(), plan.turns());
            s.pillboxes.add(new Pillbox(building, plan.covers(), plan.role(), slitCells(plan)));
            berm(c, building);
        }
    }

    private static PointOfInterest stamp(GenContext c, BuildingStamp plan, int left, int bottom, int turns) {
        plan.place(c, left, bottom, turns);
        return c.pois.get(c.pois.size() - 1);
    }

    /** Regolith banked against a pillbox's back and sides, leaving its slits' face and its door clear. */
    private static void berm(GenContext c, PointOfInterest box) {
        int lo = south(box), hi = north(box);
        for (int y = lo - 1; y <= hi + 1; y++) for (int x = box.left - 1; x <= box.right + 1; x++) {
            boolean ring = x == box.left - 1 || x == box.right + 1 || y == lo - 1 || y == hi + 1;
            if (!ring || x == box.left - 1) continue;
            if (Math.abs(y - box.anchorCellY) <= 1 && Math.abs(x - box.anchorCellX) <= 1) continue;
            place(c, x, y, x == box.right + 1 ? "sandbag-straight-e" : y > hi ? "sandbag-straight-n"
                    : "sandbag-straight-s", 0);
        }
    }

    /** A building's southern and northern rows; a point of interest keeps them as {@code top} and {@code bottom}. */
    private static int south(PointOfInterest poi) { return Math.min(poi.top, poi.bottom); }

    private static int north(PointOfInterest poi) { return Math.max(poi.top, poi.bottom); }

    /** The pads: concrete under each dish, the lander pad and the motor pool; gravel under the mast and the uplink. */
    private static void padSurfaces(GenContext c) {
        int[] concrete = new int[3];
        for (int age = 0; age < 3; age++) concrete[age] = c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            if (c.topology.getGroundKind(x, y) == GroundKind.COURTYARD && !c.topology.isWall(x, y))
                c.topology.setSurface(x, y, concrete[RoadRasterizer.age(.5, 7131, x, y)]);
        StoryServiceYard yard = new StoryServiceYard(c);
        yard.gravel(MAST_PAD.left(), MAST_PAD.bottom(), MAST_PAD.right(), MAST_PAD.top());
        yard.gravel(UPLINK_PAD.left(), UPLINK_PAD.bottom(), UPLINK_PAD.right(), UPLINK_PAD.top());
        // The dish field is graded: the whole fenced ground raked flat round the dishes.
        int raked = c.topology.surfaceIndex("floors.stone");
        for (int y = DISH_FIELD.bottom() + 1; y < DISH_FIELD.top(); y++)
            for (int x = DISH_FIELD.left() + 1; x < DISH_FIELD.right(); x++) {
                if (!c.grid.isWalkable(x, y) || c.topology.hasBuildingKindHint(x, y) || c.topology.isFixture(x, y)
                        || c.topology.getGroundKind(x, y) == GroundKind.COURTYARD) continue;
                c.topology.setGroundKind(x, y, GroundKind.STONE);
                c.topology.setSurface(x, y, raked);
                c.markMadeGround(x, y);
            }
    }

    /**
     * The installations as the post crew works them: the mast and its gear on the rim, the six dishes and their
     * cabling in the fenced field, the uplink's transmitter and generator on the knoll, the hab street's stores and
     * suit lockers, the lander pad's marks, and the motor pool.
     */
    private static void dressing(GenContext c, Sites s) {
        StoryServiceYard yard = new StoryServiceYard(c);
        // The ranging mast: its column and dish head, the ranging gear and the generator that feeds it.
        yard.prop(133, 90, "gantry-column", 0);
        yard.prop(134, 90, "military-radar-dish", 0);
        yard.prop(132, 92, "industrial-generator", 0);
        yard.prop(134, 92, "industrial-switchboard", 0);
        yard.prop(131, 89, "industrial-cable-reel", 0);
        // The dish field: six listening dishes, each on its concrete footing with its feed drum, their cables laid
        // on the ground along the field's middle to the control hut, a spur to each dish of the south row.
        for (int x : DISH_X) for (int y : DISH_Y) {
            yard.prop(x - 1, y - 1, "listening-dish", 0);
            yard.prop(x + DISH_CELLS - 1, y + DISH_CELLS - 1, "industrial-cable-reel", 0);
        }
        for (int x = HUT_LEFT + 8; x < DISH_X[2] + 2; x += 2) lay(c, x, 28, "ship-dressing.cable-run", 0);
        for (int x : DISH_X) lay(c, x, 26, "ship-dressing.cable-run", 1);
        yard.perimeter(new int[]{DISH_NORTH_GATE.x(), DISH_FIELD.top(), DISH_SOUTH_GATE.x(), DISH_FIELD.bottom()},
                DISH_FIELD.left(), DISH_FIELD.bottom(), DISH_FIELD.right(), DISH_FIELD.bottom(),
                DISH_FIELD.right(), DISH_FIELD.top(), DISH_FIELD.left(), DISH_FIELD.top());
        // The control hut's console and its racks.
        inside(c, consoleProp().x(), consoleProp().y(), "industrial-control-console");
        inside(c, HUT_LEFT + 5, HUT_BOTTOM + 2, "civic-server-rack");
        // The uplink: its transmitter dish, the switch gear and the generator, on the knoll's pad.
        yard.prop(193, 25, "military-radar-dish", 0);
        yard.prop(191, 27, "industrial-switchboard", 0);
        yard.prop(195, 23, "industrial-generator", 0);
        yard.prop(191, 23, "industrial-reactor-housing", 0);
        // Inside: bunks in the bunk modules, the signals racks, the riggers' kit.
        for (PointOfInterest bunk : s.bunks)
            for (int x = bunk.left + 1; x <= bunk.right - 1; x += 2) inside(c, x, north(bunk) - 1, "military-bunk");
        // The signals racks along the long room's south wall, clear of the airlock at its west end.
        for (int x = s.signals.left + 4; x <= s.signals.right - 2; x += 2) inside(c, x, south(s.signals) + 1, "civic-server-rack");
        inside(c, s.riggers.left + 1, south(s.riggers) + 1, "military-bunk");
        inside(c, s.riggers.left + 3, south(s.riggers) + 1, "military-bunk");
        inside(c, s.riggers.right - 2, south(s.riggers) + 1, "industrial-cable-reel");
        // The hab street: suit lockers and stores against the modules, lamps along it.
        for (int x : new int[]{151, 165}) place(c, x, 60, "industrial-crate-stack", 0);
        for (int x : new int[]{151, 166}) place(c, x, 54, "industrial-pallet-stack", 0);
        coldDrop(c, s);
        stores(c);
        place(c, 145, 50, "industrial-generator", 0);
        // The lander pad: its bay marks; the motor pool: drums and a loader.
        yard.bay(LANDER_PAD.left() + 2, LANDER_PAD.bottom() + 2, LANDER_PAD.right() - 2, LANDER_PAD.top() - 2);
        place(c, 196, 45, "industrial-drum-cluster", 0);
        place(c, 197, 45, "industrial-drum-cluster", 0);
        place(c, 185, 39, "stores-cargo-loader", 0);
        place(c, 196, 39, "industrial-crate-stack", 0);
        // Old rover parts and spent stores along the track, and the post's first-season dump on the flat's edge.
        place(c, 120, 62, "field-salvage-supplies", 0);
        place(c, 116, 51, "industrial-scrap-pile", 0);
        place(c, 92, 62, "industrial-drum-cluster", 0);
        place(c, 76, 51, "field-salvage-metals", 0);
        // The dead: rovers and wreckage where they stopped, salvage picked off them beside each.
        for (Wreck wreck : WRECKS) place(c, wreck.x(), wreck.y(), wreck.id(), wreck.turns());
        place(c, 69, 61, "field-salvage-metals", 0);
        place(c, 117, 60, "industrial-scrap-pile", 0);
        place(c, 53, 52, "field-salvage-supplies", 0);
    }

    /**
     * What an airless post lives on, kept outside where a leak vents to nothing: oxygen spheres and gas bottle racks
     * behind the bunks, fuel skids on the lander pad, the tanker in the motor pool, shipping containers and crates;
     * antenna masts over the habs and the pad, and the power laid along the hab street.
     */
    private static void stores(GenContext c) {
        for (int x = 152; x <= 160; x += 3) place(c, x, 67, "oxygen-sphere", 0);
        place(c, 163, 67, "origin-lab.tank-rack-clean", 0);
        place(c, 163, 69, "origin-lab.tank-rack-clean", 0);
        place(c, 172, 67, "origin-freight.container-blue-clean", 0);
        place(c, 172, 70, "origin-freight.container-rust", 0);
        place(c, 176, 67, "industrial-crate-stack", 0);
        place(c, 196, 67, "shuttle-bay.fluid-skid", 0);
        place(c, 189, 36, "parked-tanker-truck", 0);
        place(c, 131, 33, "origin-freight.container-rust", 0);
        place(c, 131, 31, "industrial-crate-stack", 0);
        // Antenna masts: a column and its ranging head over each place the post talks from.
        for (int[] at : new int[][]{{149, 64}, {180, 62}, {187, 66}, {167, 60}}) {
            place(c, at[0], at[1], "gantry-column", 0);
            place(c, at[0], at[1] + 1, "military-radar-dish", 0);
        }
        // Power laid along the hab street's south side, a run to each module's door.
        for (int x = 153; x <= 177; x += 2) lay(c, x, 54, "ship-dressing.cable-run", 0);
        for (int x = 152; x <= 178; x += 2) lay(c, x, 60, "ship-dressing.cable-run", 0);
    }

    /**
     * A floor-level prop laid where a walker still passes (a cable on the ground): drawn, never cover or obstacle.
     * Skipped where anything else stands or a door opens.
     */
    private static void lay(GenContext c, int x, int y, String id, int turns) {
        DoodadDef def = TileRegistry.installed().doodad(id.contains(".") ? id : "doodad." + id);
        if (def == null) return;
        Doodad prop = new Doodad(x, y, def, turns);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++)
            if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.grid.isDoorway(xx, yy)
                    || c.topology.isFixture(xx, yy) || c.topology.hasBuildingKindHint(xx, yy)) return;
        c.doodads.add(prop);
    }

    /**
     * The cold drop: cryo pods racked in two rows on the open ground where the cold costs nothing, their chiller at
     * the end of the aisle; the chem module's bench and lockers; its pressed product crated on pallets; and the cargo
     * crawler on the pad, half loaded, the rest of its load stacked beside it.
     */
    private static void coldDrop(GenContext c, Sites s) {
        StoryServiceYard yard = new StoryServiceYard(c);
        for (int y = POD_RACKS.bottom(); y < POD_RACKS.top(); y += 2) {
            yard.prop(POD_RACKS.left(), y, "galley-cold-store", 0);
            yard.prop(POD_RACKS.left() + 1, y, "galley-cold-store", 0);
            yard.prop(POD_RACKS.right() - 1, y, "galley-cold-store", 0);
            yard.prop(POD_RACKS.right(), y, "galley-cold-store", 0);
        }
        yard.prop(POD_HOLD.x(), POD_RACKS.top(), "industrial-generator", 0);
        yard.prop(POD_HOLD.x() + 1, POD_RACKS.top(), "industrial-fluid-tank", 0);
        inside(c, chemist().x() - 2, south(s.chem) + 1, "medical-drug-locker");
        inside(c, chemist().x() - 2, north(s.chem) - 1, "medical-treatment-table");
        for (int y = DRUG_CRATES.bottom(); y <= DRUG_CRATES.top(); y += 2) {
            yard.prop(DRUG_CRATES.left(), y, "industrial-crate-stack", 0);
            yard.prop(DRUG_CRATES.left() + 1, y, "industrial-pallet-stack", 0);
            yard.prop(DRUG_CRATES.right(), y, "industrial-crate-stack", 0);
        }
        yard.prop(CARGO_CRAWLER.x(), CARGO_CRAWLER.y(), "parked-cargo-truck", 1);
        for (int[] at : new int[][]{{198, 60}, {198, 62}, {199, 61}, {191, 64}})
            yard.prop(at[0], at[1], at[0] % 2 == 0 ? "industrial-crate-stack" : "industrial-pallet-stack", 0);
    }

    /** The chemist's place at the chem module's lockers, against its south wall. */
    static Cell chemist() { return new Cell(CHEM_LEFT + 3, CHEM_BOTTOM + 2); }

    /** The control hut's console, against its north wall. */
    static Cell consoleProp() { return new Cell(HUT_LEFT + 2, HUT_BOTTOM + 3); }

    /** The cell a squad leader holds at the console: the floor in front of it. */
    static Cell console() { return new Cell(HUT_LEFT + 2, HUT_BOTTOM + 2); }

    /**
     * A prop on open ground if its whole footprint is free and clear of doors, roads and berths; returns whether it
     * stood. Dressing yields to circulation rather than refusing the map.
     */
    private static boolean place(GenContext c, int x, int y, String id, int turns) {
        DoodadDef def = TileRegistry.installed().doodad(id.contains(".") ? id : "doodad." + id);
        if (def == null) return false;
        Doodad prop = new Doodad(x, y, def, turns);
        if (!open(c, x, y, prop.footprintCellsX, prop.footprintCellsY)) return false;
        occupy(c, prop);
        return true;
    }

    private static boolean open(GenContext c, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) {
            if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy) || nestPad(xx, yy)
                    || c.topology.hasBuildingKindHint(xx, yy) || c.topology.getGroundKind(xx, yy) == GroundKind.STREET
                    || (c.grid.elevation() != null && c.grid.elevation().isRamp(xx, yy)))
                return false;
            for (int ty = yy - 1; ty <= yy + 1; ty++) for (int tx = xx - 1; tx <= xx + 1; tx++)
                if (c.grid.inBounds(tx, ty) && c.grid.isDoorway(tx, ty)) return false;
            for (LandingPad pad : c.landingPads)
                if (xx >= pad.left() - 2 && xx <= pad.right() + 2 && yy >= pad.bottom() - 2 && yy <= pad.top() + 2)
                    return false;
        }
        return true;
    }

    /** A fitting on a room's floor; the rooms are authored, so one that does not fit is a plan error. */
    private static void inside(GenContext c, int x, int y, String id) {
        Doodad prop = new Doodad(x, y, TileRegistry.installed().doodad("doodad." + id), 0);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++)
            if (!c.grid.isWalkable(xx, yy) || c.grid.isDoorway(xx, yy) || c.topology.isFixture(xx, yy))
                throw new IllegalStateException("Sounding fitting does not fit: " + id + " at " + x + "," + y);
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
     * The rim guns, stamped once the ground is final: the light post's parapet ring round one sealed mount.
     * Unmanned and powered; whether they spawn, for whom, and whether the mast spots for them are the mission's.
     */
    private static void nests(GenContext c, Sites s) {
        for (int i = 0; i < NEST_CELLS.size(); i++) {
            Cell at = NEST_CELLS.get(i);
            DefensePost post = DefensePostStamper.stampNest(c.grid, c.topology, c.doodads, at.x(), at.y(), NEST_GUNS.get(i));
            c.defensePosts.add(post);
            s.nests.add(new Nest(at, NEST_FACING.get(i), post));
        }
    }

    /**
     * Basalt boulders: the boulder field between the ridge's north pass and the rim's west ramp, and clusters strung
     * along the rille's south bank. Each stands on a jittered lattice, off every approach's own line and the rover
     * track, so the field is slow and covered without closing the way through it.
     */
    private static void boulders(GenContext c) {
        List<FormationBrush.Placement> placements = new ArrayList<>();
        boolean[] closed = new boolean[WIDTH * HEIGHT];
        Map<Approach, List<Cell>> lines = approachLines();
        field(c, placements, closed, new Area(50, 80, 116, 108), 6, lines.get(Approach.BOULDERS), 7151);
        field(c, placements, closed, new Area(64, 2, 140, 15), 9, lines.get(Approach.RILLE), 7152);
        ejecta(c, placements, closed, lines);
        FormationBrush.stamp(placements, c.grid, c.topology, c.doodads, null);
    }

    /**
     * The blocks an impact threw out: round every crater five or more cells across, a few boulders on the apron just
     * outside its rim, kept off every approach's line. Cover for a squad working across the flat.
     */
    private static void ejecta(GenContext c, List<FormationBrush.Placement> out, boolean[] closed,
                               Map<Approach, List<Cell>> lines) {
        List<Cell> keepOpen = new ArrayList<>();
        for (List<Cell> line : lines.values()) {
            keepOpen.addAll(line);
            keepOpen.add(null);
        }
        for (int i = 0; i < CRATERS.size(); i++) {
            Crater crater = CRATERS.get(i);
            if (crater.radius() < 5) continue;
            for (int k = 0; k < 6; k++) {
                double angle = (k + hash(i, k, 7153)) * Math.PI / 3, reach = crater.radius() + 4.5 + hash(k, i, 7154) * 2;
                int x = (int) Math.round(crater.x() + Math.cos(angle) * reach) - 2;
                int y = (int) Math.round(crater.y() + Math.sin(angle) * reach) - 1;
                FormationBrush.Placement p = new FormationBrush.Placement(x, y, MineralFormation.BOULDERS,
                        (int) (hash(x, y, 7155) * 4));
                if (fits(c, p, closed, keepOpen)) {
                    out.add(p);
                    for (int yy = p.y(); yy < p.y() + p.height(); yy++) for (int xx = p.x(); xx < p.x() + p.width(); xx++)
                        if (p.blocks(xx, yy)) closed[yy * WIDTH + xx] = true;
                }
            }
        }
    }

    private static void field(GenContext c, List<FormationBrush.Placement> out, boolean[] closed, Area area,
                              int spacing, List<Cell> keepOpen, int salt) {
        MineralFormation[] pool = {MineralFormation.BOULDERS, MineralFormation.BOULDERS, MineralFormation.SPIRES};
        for (int gy = area.bottom(); gy <= area.top(); gy += spacing)
            for (int gx = area.left() + (gy / spacing % 2) * spacing / 2; gx <= area.right(); gx += spacing) {
                int x = gx + (int) (hash(gx, gy, salt) * 3) - 1, y = gy + (int) (hash(gy, gx, salt) * 3) - 1;
                MineralFormation shape = pool[(int) (hash(x, y, salt + 1) * pool.length)];
                int turns = (int) (hash(x, y, salt + 2) * 4);
                FormationBrush.Placement p = new FormationBrush.Placement(x, y, shape, turns);
                if (fits(c, p, closed, keepOpen)) {
                    out.add(p);
                    for (int yy = p.y(); yy < p.y() + p.height(); yy++) for (int xx = p.x(); xx < p.x() + p.width(); xx++)
                        if (p.blocks(xx, yy)) closed[yy * WIDTH + xx] = true;
                }
            }
    }

    private static boolean fits(GenContext c, FormationBrush.Placement p, boolean[] closed, List<Cell> keepOpen) {
        if (p.x() < 1 || p.y() < 1 || p.x() + p.width() >= WIDTH || p.y() + p.height() >= HEIGHT) return false;
        for (int y = p.y() - 1; y <= p.y() + p.height(); y++) for (int x = p.x() - 1; x <= p.x() + p.width(); x++) {
            // A cell of walkable gap round every formation, so two never weld into a wall.
            if (closed[y * WIDTH + x]) return false;
            if (!p.blocks(x, y)) continue;
            if (!natural(c, x, y)) return false;
            // A null between two lines' cells ends one line; no leg runs across it.
            for (int i = 1; i < keepOpen.size(); i++)
                if (keepOpen.get(i - 1) != null && keepOpen.get(i) != null
                        && distanceToLeg(x, y, keepOpen.get(i - 1), keepOpen.get(i)) < 3) return false;
        }
        return true;
    }

    /** Wild ground a formation may stand on: the rules {@code FormationBrush.stamp} enforces, asked before it. */
    private static boolean natural(GenContext c, int x, int y) {
        GroundKind ground = c.topology.getGroundKind(x, y);
        if ((ground != GroundKind.STONE && ground != GroundKind.DIRT) || !c.grid.isWalkable(x, y) || c.grid.isDoorway(x, y)
                || c.topology.isWall(x, y) || c.topology.isFixture(x, y) || c.topology.getBuildingId(x, y) != 0
                || c.topology.getSurface(x, y) != 0
                || (c.topology.roadSurface() != null && c.topology.roadSurface().underlay(x, y) != null))
            return false;
        if (c.grid.elevation() != null && (c.grid.elevation().tier(x, y) != 0 || c.grid.elevation().isRamp(x, y)))
            return false;
        if (c.topology.terrainSurface() != null)
            for (int layer = 0; layer < TerrainSurface.LAYERS; layer++)
                if (c.topology.terrainSurface().patch(x, y, layer) != null) return false;
        for (LandingPad pad : c.landingPads)
            if (x >= pad.left() - 3 && x <= pad.right() + 3 && y >= pad.bottom() - 3 && y <= pad.top() + 3) return false;
        return true;
    }

    /**
     * The open ground: the planet's own regolith and loose stones on every natural cell nothing built claims, and
     * scree fanning out from the rock, thick against the ridge, the rim, the knoll and the boulders and thinning onto
     * the flats. Paint only; footing is unchanged.
     */
    private static void regolith(GenContext c) {
        SurfacePalette surface = c.get(BspKeys.MARKET_PROFILE).surface();
        TerrainSurface terrain = c.topology.terrainSurface();
        boolean[] open = new boolean[WIDTH * HEIGHT];
        int[] distance = new int[WIDTH * HEIGHT];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int i = y * WIDTH + x;
            if (!c.grid.isWalkable(x, y)) {
                if (c.grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) {
                    distance[i] = 0;
                    frontier.add(new int[]{x, y});
                }
                continue;
            }
            GroundKind ground = c.topology.getGroundKind(x, y);
            if (c.isMadeGround(x, y) || c.topology.getSurface(x, y) != 0 || c.topology.isFixture(x, y)
                    || (ground != GroundKind.STONE && ground != GroundKind.DIRT)
                    || (c.topology.roadSurface() != null && c.topology.roadSurface().underlay(x, y) != null)) continue;
            boolean patch = false;
            for (int layer = 0; layer < TerrainSurface.LAYERS && terrain != null && !patch; layer++)
                patch = terrain.patch(x, y, layer) != null;
            open[i] = !patch;
        }
        NatureZoneFiller.dressOpenGround(c, open);
        // An airless moon's loose stones lie in drifts round old impacts, not evenly over the ground: thin them out.
        PatchField drifts = new PatchField(c.seed ^ 0x4452494654L, 11);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            if (open[y * WIDTH + x] && drifts.sample(x, y) < .62f) c.topology.setNatureOverlayIndex(x, y, -1);
        craterGround(c, open, surface);
        int reach = 6;
        while (!frontier.isEmpty()) {
            int[] at = frontier.poll();
            int d = distance[at[1] * WIDTH + at[0]];
            if (d >= reach) continue;
            for (Direction step : Direction.CARDINALS) {
                int nx = at[0] + step.dx, ny = at[1] + step.dy;
                if (!c.grid.inBounds(nx, ny)) continue;
                int j = ny * WIDTH + nx;
                if (distance[j] <= d + 1) continue;
                distance[j] = d + 1;
                frontier.add(new int[]{nx, ny});
            }
        }
        TileRegistry tiles = TileRegistry.installed();
        String[] stones = Stream.of("small-1", "small-2", "medium-1").map(surface.stones()::stone).toArray(String[]::new);
        PatchField fans = new PatchField(c.seed ^ 0x5343524545L, 9);
        Random random = new Random(c.seed ^ 0x54414c5553L);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int i = y * WIDTH + x;
            if (!open[i] || distance[i] > reach) continue;
            float near = 1f - (distance[i] - 1) / (float) reach;
            // Fans, not a halo: the patch field gathers the scree into tongues below some stretches of rock.
            float chance = near * near * (fans.sample(x, y) < .5f ? .5f : .1f);
            if (random.nextFloat() < chance)
                c.topology.setNatureOverlayIndex(x, y, tiles.tile(stones[random.nextInt(stones.length)]).index);
        }
    }

    /**
     * Each impact's ground: its bowl swept bare (and kept out of the scree that follows), and outside its rim the
     * ejecta, stones thrown out in rays, thick and coarse against the rim and thinning over about a radius more.
     * Paint only; the rim itself is the terrain document's.
     */
    private static void craterGround(GenContext c, boolean[] open, SurfacePalette surface) {
        TileRegistry tiles = TileRegistry.installed();
        int[] fine = Stream.of("small-1", "small-2").mapToInt(id -> tiles.tile(surface.stones().stone(id)).index).toArray();
        int[] coarse = Stream.of("medium-1", "medium-2", "large-1").map(surface.stones()::stone).filter(tiles::has)
                .mapToInt(id -> tiles.tile(id).index).toArray();
        for (int i = 0; i < CRATERS.size(); i++) {
            Crater crater = CRATERS.get(i);
            double r = crater.radius(), apron = r * 1.1 + 2, inner = r + 1.5;
            int rays = 5 + i % 3;
            double phase = hash(i, 0, 7156) * Math.PI * 2;
            int span = (int) Math.ceil(inner + apron);
            for (int y = crater.y() - span; y <= crater.y() + span; y++)
                for (int x = crater.x() - span; x <= crater.x() + span; x++) {
                    if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT || !open[y * WIDTH + x]) continue;
                    double d = Math.hypot(x - crater.x(), y - crater.y());
                    if (d < r - .8) {
                        c.topology.setNatureOverlayIndex(x, y, -1);
                        open[y * WIDTH + x] = false;
                        continue;
                    }
                    if (d < inner || d > inner + apron) continue;
                    double t = (d - inner) / apron;
                    double ray = .5 + .5 * Math.cos(Math.atan2(y - crater.y(), x - crater.x()) * rays + phase);
                    double chance = Math.pow(1 - t, 1.2) * (.25 + .75 * ray * ray);
                    if (hash(x, y, 7157 + i) >= chance) continue;
                    int[] pool = t < .35 && coarse.length > 0 ? coarse : fine;
                    c.topology.setNatureOverlayIndex(x, y, pool[(int) (hash(y, x, 7158) * pool.length)]);
                }
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

    private static Layout layout(MapResult map, Sites s) {
        Map<PostRole, List<Cell>> posts = new EnumMap<>(PostRole.class);
        posts.put(PostRole.RIM, List.of(new Cell(126, 87), new Cell(130, 88), new Cell(146, 94), new Cell(140, 92)));
        posts.put(PostRole.MAST, List.of(new Cell(130, 91), new Cell(137, 90)));
        List<Cell> hab = new ArrayList<>(slitCells(AIRLOCK_PILLBOX));
        hab.addAll(List.of(new Cell(160, 58), new Cell(176, 56)));
        posts.put(PostRole.HAB, List.copyOf(hab));
        posts.put(PostRole.DISH_FIELD, List.of(new Cell(148, 35), new Cell(156, 19), new Cell(164, 27),
                new Cell(137, 21), new Cell(HUT_LEFT + 4, HUT_BOTTOM + 1)));
        List<Cell> uplink = new ArrayList<>(slitCells(RAMP_PILLBOX));
        uplink.addAll(List.of(new Cell(189, 21), new Cell(197, 27)));
        posts.put(PostRole.UPLINK, List.copyOf(uplink));
        posts.put(PostRole.MOTOR_POOL, List.of(new Cell(189, 42), new Cell(196, 42)));
        posts.put(PostRole.WATCH_BOSS, List.of(new Cell(s.signals.interiorAnchorX, s.signals.interiorAnchorY)));
        Map<Installation, ChargeSite> sites = new EnumMap<>(Installation.class);
        sites.put(Installation.MAST, new ChargeSite(Installation.MAST.id, MAST_PLANT, MAST_PAD));
        sites.put(Installation.UPLINK, new ChargeSite(Installation.UPLINK.id, UPLINK_PLANT, UPLINK_PAD));
        sites.put(Installation.DISH_FIELD, new ChargeSite(Installation.DISH_FIELD.id, DISH_PLANT,
                new Area(DISH_X[0] - 2, DISH_Y[0] - 2, DISH_X[2] + DISH_CELLS - 1, DISH_Y[1] + DISH_CELLS - 1)));
        return new Layout(map, LANDING, COMPANY_START, sites, RIM, List.of(RIM_WEST_FOOT, RIM_FLOOR_FOOT), KNOLL,
                KNOLL_FOOT, DISH_FIELD, s.controlHut, console(), HAB, List.copyOf(s.bunks), s.signals,
                interiorCells(map, s.signals, 5), s.riggers, interiorCells(map, s.riggers, 4), riggersWalkOut(s.riggers),
                LANDER_PAD, POD_RACKS, POD_HOLD, s.chem, chemist(), DRUG_CRATES, CARGO_CRAWLER,
                List.of(new Cell(192, 59), new Cell(197, 64)), posts, List.copyOf(s.nests), List.copyOf(s.pillboxes), MORTAR_START, mortarLane(),
                TECHNICAL_STANDS, List.of(WEST_CROSSING, MIDDLE_CROSSING), cover(map), approachLines());
    }

    /** Up to {@code count} walkable floor cells spread through a building, its interior anchor first. */
    private static List<Cell> interiorCells(MapResult map, PointOfInterest building, int count) {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(building.interiorAnchorX, building.interiorAnchorY));
        for (int y = south(building) + 1; y < north(building) && cells.size() < count; y++)
            for (int x = building.left + 1; x < building.right && cells.size() < count; x += 2)
                if (map.grid.isWalkable(x, y) && !map.grid.isDoorway(x, y)
                        && !cells.contains(new Cell(x, y))) cells.add(new Cell(x, y));
        return List.copyOf(cells);
    }

    /** Walkable cells on the low ground with a boulder beside them, every third one. */
    private static List<Cell> cover(MapResult map) {
        List<Cell> out = new ArrayList<>();
        NavigationGrid grid = map.grid;
        int n = 0;
        for (int y = 1; y < HEIGHT - 1; y++) for (int x = 1; x < WIDTH - 1; x++) {
            if (!grid.isWalkable(x, y) || grid.elevation().tier(x, y) != 0) continue;
            boolean beside = false;
            for (Direction d : Direction.CARDINALS) {
                int nx = x + d.dx, ny = y + d.dy;
                beside |= !grid.isWalkable(nx, ny) && grid.hasTag(nx, ny, NavigationGrid.CellTag.NATURAL_SOLID)
                        && grid.elevation().tier(nx, ny) == 0 && !grid.elevation().isFace(nx, ny)
                        && map.topology.isFixture(nx, ny) && (y > 78 || y < 16);
            }
            if (beside && n++ % 3 == 0) out.add(new Cell(x, y));
        }
        return List.copyOf(out);
    }

    /** Every site reached from the company's start, and each approach open along its own line. */
    static void validate(Layout l) {
        NavigationGrid grid = l.map().grid;
        boolean[] reached = reach(grid, l.companyStart(), null);
        List<Cell> required = new ArrayList<>(List.of(l.console(), l.knollRamp(), l.mortarStart(), l.podHold(),
                l.chemist()));
        required.addAll(l.landerCrew());
        for (ChargeSite site : l.sites().values()) required.add(site.plant());
        required.addAll(l.rimRamps());
        for (List<Cell> cells : l.posts().values()) required.addAll(cells);
        required.addAll(l.signalsTechs());
        required.addAll(l.riggers());
        required.addAll(l.rilleCrossings());
        required.addAll(l.technicalStands());
        required.addAll(l.mortarLane());
        for (PointOfInterest poi : l.map().pointsOfInterest) required.add(new Cell(poi.interiorAnchorX, poi.interiorAnchorY));
        for (LandingPad pad : l.map().landingPads) {
            if (!pad.isClear(grid, l.map().topology)) throw new IllegalStateException("Sounding berth obstructed");
            required.add(new Cell(pad.centerX, pad.centerY));
        }
        for (Cell cell : required)
            if (!reached[grid.index(cell.x(), cell.y())]) throw new IllegalStateException("Sounding site cut off: " + cell);
        for (Approach approach : Approach.values())
            if (!approachOpen(grid, l.approaches().get(approach)))
                throw new IllegalStateException("Sounding approach closed: " + approach);
        if (!approachOpen(grid, l.riggersWalkOut())) throw new IllegalStateException("Riggers' walk-out closed");
        for (Pillbox pillbox : l.pillboxes())
            for (Cell post : pillbox.posts())
                if (l.map().topology.getBuildingId(post.x(), post.y()) == 0)
                    throw new IllegalStateException("Pillbox post outside its pillbox: " + post);
        if (grid.elevation().tier(MAST_PLANT.x(), MAST_PLANT.y()) != 1
                || grid.elevation().tier(UPLINK_PLANT.x(), UPLINK_PLANT.y()) != 1)
            throw new IllegalStateException("Mast and uplink stand on the high ground");
        for (Nest nest : l.nests())
            if (grid.elevation().tier(nest.cell().x(), nest.cell().y()) != 1)
                throw new IllegalStateException("Rim gun off the rim: " + nest.cell());
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
