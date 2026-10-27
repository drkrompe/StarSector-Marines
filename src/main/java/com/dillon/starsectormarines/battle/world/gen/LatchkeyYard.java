package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Latchkey Yard on airless Agreus: one walled working yard entered by a freight gate, with the
 * dispatch house beside it, the machine-bay tooling hall in the middle and the operators' pressure
 * entrance on the south wall. A covered service gallery joins the entrance to the hall, so the hall
 * has exactly two ways in: its north freight doors off the yard, and the gallery. The Cinder Steps
 * terraces lie east of the yard with their service lane running to the yard's east gate; the homes
 * and the district heating service stand on their own frontage north of the wall and carry no duty.
 */
public final class LatchkeyYard {
    public static final int WIDTH = 176, HEIGHT = 128;
    public static final int LANDING_X = 24, LANDING_Y = 59;
    /** The marked calibration machine. Ordinary damage applies; the inspection needs it standing. */
    public static final int TOOL_X = 107, TOOL_Y = 61;
    public static final String TOOL_ID = "doodad.industrial-machine-tool";
    public record Site(String name, int x, int y, int left, int bottom, int right, int top) { }
    public static final List<Site> SITES = List.of(
            new Site("Dispatch house / freight gate", 58, 70, 50, 64, 66, 76),
            new Site("Tooling hall", 93, 71, 74, 56, 112, 86),
            new Site("Operator entrance", 88, 29, 79, 14, 97, 36));
    /** A named place on the map with no capture duty of its own. */
    public record Place(String id, String label, int x, int y) { }
    /** Where a custody party stops and signs: the marked bay at the dispatch house's yard door. */
    public static final Place HANDOVER = new Place("handover", "Dispatch handover bay", 58, 62);
    public static final Place FREIGHT_GATE = new Place("freight-gate", "Freight gate", 46, 56);
    public static final Place CINDER_GATE = new Place("cinder-gate", "Cinder Steps gate", 128, 43);
    /** Map-edge end of the Cinder Steps service lane. An entry, not a destination. */
    public static final Place CINDER_LANE = new Place("cinder-lane", "Cinder Steps service lane", 170, 43);
    /** Map-edge end of the south service road that passes the operator entrance. */
    public static final Place SOUTH_ROAD = new Place("south-road", "South service road", 170, 8);
    /** The tooling hall's paired entrances. */
    public static final Place HALL_FREIGHT_DOORS = new Place("hall-north", "Tooling hall freight doors", 93, 86);
    public static final Place HALL_GALLERY_DOOR = new Place("hall-gallery", "Service gallery door", 88, 56);
    public static final Place VESTIBULE = new Place("vestibule", "Operator pressure vestibule", 88, 14);
    public static final Place HEATING = new Place("heating", "District heating service", 100, 113);
    /**
     * The finite counterattack's two groups, in commitment order: the first comes down the Cinder
     * Steps lane for the hall's freight doors, the second along the south road for the operator
     * entrance and, through it, the gallery door.
     */
    public static final List<Place> RESERVE_INGRESS = List.of(CINDER_LANE, SOUTH_ROAD);
    /**
     * Association work at the benches, as labelled by the fitters. The machines are ordinary
     * props with ordinary damage; the labels say whose work stops if they are lost.
     */
    public static final List<Place> WORKPLACES = List.of(
            new Place("weir-bench", "Association lathe / North Weir pump impellers", 77, 83),
            new Place("heating-bench", "Heating-service bench / Cinder Steps circulators", 105, 83),
            new Place("clinic-bench", "Shared inspection bench / Low Quay clinic sterilizer parts", 93, 59));

    /** Which chapter's visit to the yard is being generated. Both share every wall, door and site. */
    public enum Visit {
        /** The Yard's Owners: Greyhook holds the yard and the inspection has not happened. */
        INSPECTION,
        /**
         * Terms of Protection: the same yard after the inspection. The operator entrance is a
         * constables' post, the inspected calibration bay carries the captured rig tooling being
         * commissioned, and a mortar pit stands dug on the Cinder Steps terraces.
         */
        TERMS_OF_PROTECTION
    }

    /** Calibration room interior (inside its own walls): where the commissioning work is done. */
    public static final int CALIBRATION_LEFT = 103, CALIBRATION_BOTTOM = 57, CALIBRATION_RIGHT = 111, CALIBRATION_TOP = 66;
    /** Where the commissioning technicians stand to work, inside the calibration room. */
    public static final Place COMMISSIONING = new Place("commissioning", "Captured rig tooling / commissioning", 106, 64);
    /** Inside the Cinder Steps gate: the post Dormer's estate guards hold under House Custody. */
    public static final Place GUARD_POST = new Place("guard-post", "Estate guards' post / Cinder Steps gate", 123, 43);
    /**
     * Dug pit on the Cinder Steps terraces, within a field mortar's reach of the tooling hall and
     * out of sight from inside the yard wall. Reached on foot from the lane past the Cinder Steps gate.
     */
    public static final Place MORTAR_POST = new Place("mortar-post", "Mortar pit / Cinder Steps terraces", 140, 64);
    /** Where a custody party forms up on the apron before moving in under escort. */
    public static final Place PARTY_START = new Place("party-start", "Custody party / boat apron", 8, 56);

    private static final String YARD_WALL = "installation.dark-concrete-wall";
    private static final String SHOP_WALL = "colony.olive-wall";
    private static final String LOCK_WALL = "installation.light-concrete-wall";

    private LatchkeyYard() { }

    public static MapResult generate(long seed, TargetProfile profile) { return generate(seed, profile, Visit.INSPECTION); }

    public static MapResult generate(long seed, TargetProfile profile, Visit visit) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        boolean terms = visit == Visit.TERMS_OF_PROTECTION;
        List<GenStage> stages = new ArrayList<>(List.of(LatchkeyYard::grounds, LatchkeyYard::structures,
                LatchkeyYard::frontage, LatchkeyYard::equipment, LatchkeyYard::cinderSteps));
        if (terms) stages.add(LatchkeyYard::afterInspection);
        stages.add(new FinalizeStage());
        stages.add(new OutdoorLandscapeStage(LANDING_X, LANDING_Y, 58, 70, 93, 71, 88, 29,
                HANDOVER.x(), HANDOVER.y(), CINDER_LANE.x(), CINDER_LANE.y(), SOUTH_ROAD.x(), SOUTH_ROAD.y(),
                CINDER_GATE.x(), CINDER_GATE.y(), 158, 100, MORTAR_POST.x(), MORTAR_POST.y(),
                GUARD_POST.x(), GUARD_POST.y(), PARTY_START.x(), PARTY_START.y()));
        new GenRecipe(terms ? "Latchkey Yard / Terms of Protection" : "Latchkey Yard", stages).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, CINDER_LANE.x(), CINDER_LANE.y(),
                c.pois, c.doodads, new TacticalMap(HeldPlaces.fromBuildings(c.pois, poi -> Faction.DEFENDER)), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (var pad : map.landingPads)
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Latchkey berth obstructed");
        for (var site : SITES) requireRoute(map, site.x(), site.y(), site.name());
        for (var place : List.of(HANDOVER, FREIGHT_GATE, CINDER_GATE, CINDER_LANE, SOUTH_ROAD, HEATING))
            requireRoute(map, place.x(), place.y(), place.label());
        for (var poi : map.pointsOfInterest) requireRoute(map, poi.interiorAnchorX, poi.interiorAnchorY, "building");
        if (terms) for (var place : List.of(GUARD_POST, MORTAR_POST, PARTY_START, COMMISSIONING))
            requireRoute(map, place.x(), place.y(), place.label());
        return map;
    }

    private static void requireRoute(MapResult map, int x, int y, String name) {
        if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, x, y).length == 0)
            throw new IllegalStateException("Disconnected Latchkey place: " + name);
    }

    /** Wild regolith, the boat apron, the yard's working floor and its two service roads. */
    private static void grounds(GenContext c) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, GroundKind.STONE);
            boolean apron = x < 44;
            boolean yard = x >= 46 && x <= 128 && y >= 24 && y <= 98;
            if (apron || yard) pave(c, x, y, GroundKind.COURTYARD, Material.CONCRETE, .7);
            // Freight road: apron, gate, past the dispatch house and round to the hall's freight doors.
            if (x >= 40 && x <= 72 && y >= 52 && y <= 60 || x >= 67 && x <= 72 && y > 60 && y <= 92
                    || x > 72 && x <= 98 && y >= 88 && y <= 92) pave(c, x, y, GroundKind.STREET, Material.ASPHALT, .5);
            // South service road past the operator entrance; homes' frontage street north of the wall.
            if (y >= 4 && y <= 11 || y >= 100 && y <= 104) pave(c, x, y, GroundKind.STREET, Material.ASPHALT, .6);
        }
        for (int[] pad : new int[][]{{18,48},{30,58},{18,70},{30,81}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.WEST, LandingPad.Purpose.FREIGHT_APRON));
    }

    private static void pave(GenContext c, int x, int y, GroundKind kind, Material material, double wear) {
        c.topology.setGroundKind(x, y, kind);
        c.topology.setSurface(x, y, c.topology.surfaceIndex(material.block(RoadRasterizer.age(wear, 18534, x, y))));
        c.markMadeGround(x, y);
    }

    /** The yard wall and the four buildings that carry the operation. */
    private static void structures(GenContext c) {
        var d = new Draft(c);
        // Yard wall: freight gate on the west, Cinder Steps gate on the east. The operator entrance
        // takes the place of the south wall where it stands.
        for (int y = 24; y <= 98; y++) {
            if (y < 52 || y > 60) d.wall(46, y, YARD_WALL, null);
            if (y < 40 || y > 46) d.wall(128, y, YARD_WALL, null);
        }
        for (int x = 46; x <= 128; x++) { d.wall(x, 24, YARD_WALL, null); d.wall(x, 98, YARD_WALL, null); }

        dispatchHouse(d);
        toolingHall(d);
        operatorEntrance(d);
        // Covered service gallery: the operators' sealed route from their entrance to the hall.
        d.shell(86, 36, 90, 56, SHOP_WALL, "colony.tread", BuildingKind.INDUSTRIAL);
        d.door(88, 36); d.door(88, 56);
        d.window(86, 46, Direction.W); d.window(90, 46, Direction.E);
        d.finish();
        c.pois.add(new PointOfInterest(PointOfInterest.Kind.DEPOT, 50, 64, 66, 76, 58, 63, 58, 70));
        c.pois.add(new PointOfInterest(PointOfInterest.Kind.DEPOT, 74, 56, 112, 86, 93, 87, 93, 71));
        c.pois.add(new PointOfInterest(PointOfInterest.Kind.DEPOT, 79, 14, 97, 36, 88, 13, 88, 29));
    }

    /** Gate office overlooking the freight road, the dispatch floor and the records room. */
    private static void dispatchHouse(Draft d) {
        d.shell(50, 64, 66, 76, SHOP_WALL, "colony.tread", BuildingKind.INDUSTRIAL);
        d.line(55, 65, 55, 75); d.door(55, 70);
        d.line(56, 72, 65, 72); d.door(61, 72);
        d.door(58, 64); d.door(66, 68);
        d.window(52, 64, Direction.S); d.window(50, 71, Direction.W);
        d.window(53, 76, Direction.N); d.window(60, 76, Direction.N); d.window(66, 74, Direction.E);
        for (int y = 65; y <= 71; y++) for (int x = 56; x <= 65; x++) d.accent(x, y, "colony.ochre");
    }

    /**
     * Machine bays either side of a working nave, a tool crib, and the calibration room at the east
     * end. Its only doors are the north freight doors and the south gallery door.
     */
    private static void toolingHall(Draft d) {
        d.shell(74, 56, 112, 86, SHOP_WALL, "colony.concrete", BuildingKind.INDUSTRIAL);
        for (int x = 92; x <= 94; x++) d.door(x, 86);
        d.door(88, 56);
        // Bay partitions stop short of the nave, so the floor stays one fighting space.
        d.line(84, 78, 84, 85); d.line(102, 78, 102, 85);
        // Tool crib, south-west.
        d.line(84, 57, 84, 64); d.line(75, 64, 83, 64); d.door(79, 64);
        // Calibration room, south-east: one door off the nave.
        d.line(102, 57, 102, 67); d.line(103, 67, 111, 67); d.door(106, 67);
        for (int y : new int[]{62, 66, 76, 80}) d.window(74, y, Direction.W);
        for (int y : new int[]{76, 80}) d.window(112, y, Direction.E);
        for (int x : new int[]{80, 86, 100, 106}) d.window(x, 86, Direction.N);
        for (int x : new int[]{80, 96}) d.window(x, 56, Direction.S);
        for (int y = 68; y <= 75; y++) for (int x = 75; x <= 111; x++) d.accent(x, y, "colony.ochre");
    }

    /**
     * The operators' entrance: an outer and an inner pressure door five cells apart, the suit room
     * and pressurisation plant beside the lock, and the shift lobby inside the yard wall.
     */
    private static void operatorEntrance(Draft d) {
        d.shell(79, 14, 97, 36, LOCK_WALL, "installation.concrete-floor", BuildingKind.INDUSTRIAL);
        d.line(85, 15, 85, 18); d.line(91, 15, 91, 18); d.line(80, 19, 96, 19);
        d.door(88, 14); d.door(88, 19);
        d.door(82, 19); d.door(94, 19);
        d.line(80, 28, 84, 28); d.line(92, 28, 96, 28);
        d.door(88, 36);
        d.window(79, 22, Direction.W); d.window(97, 22, Direction.E);
        d.window(79, 31, Direction.W); d.window(97, 31, Direction.E);
        for (int y = 15; y <= 18; y++) for (int x = 86; x <= 90; x++) d.accent(x, y, "installation.command-floor");
    }

    /** Homes and the heating service share a street outside the wall; nobody is asked to take them. */
    private static void frontage(GenContext c) {
        BuildingStamp.CORNER_HOME.place(c, 52, 108, 0);
        BuildingStamp.COURT_HOUSE.place(c, 66, 108, 0);
        BuildingStamp.CORNER_HOME.place(c, 128, 108, 0);
        BuildingStamp.COURT_HOUSE.place(c, 142, 108, 0);
        BuildingStamp.CORNER_HOME.place(c, 158, 108, 0);
        var d = new Draft(c);
        d.shell(90, 107, 118, 121, SHOP_WALL, "colony.tread", BuildingKind.INDUSTRIAL);
        d.door(96, 107); d.door(112, 107);
        d.line(104, 108, 104, 120); d.door(104, 114);
        for (int x : new int[]{93, 100, 108, 115}) d.window(x, 107, Direction.S);
        d.finish();
        c.pois.add(new PointOfInterest(PointOfInterest.Kind.DEPOT, 90, 107, 118, 121, 96, 106, HEATING.x(), HEATING.y()));
    }

    /** Machines, the calibration bay, lock plant, heating plant and the yard's working stock. */
    private static void equipment(GenContext c) {
        var f = new Fixtures(c);
        // Dispatch: counter facing the yard door, records along the north room, the gate desk.
        f.put(60, 67, "doodad.office-reception-counter", 0);
        f.put(64, 70, "doodad.office-workstation-bank", 0);
        f.put(57, 75, "doodad.office-records-bank", 0);
        f.put(63, 75, "doodad.office-records-bank", 0);
        f.put(52, 74, "colony-furnishings.desk", 0);
        f.put(51, 66, "colony-furnishings.locker", 0);
        // Tooling hall. North-west bay: the association's lathe and mill (weir pump impellers).
        f.put(76, 83, "machine-shop.lathe", 0);
        f.put(76, 79, "machine-shop.mill", 0);
        f.put(81, 83, "machine-shop.bearings", 0);
        f.put(81, 79, "doodad.industrial-machine-tool", 0);
        // North-east bay: a heating circulator stripped on the assembly bench.
        f.put(104, 83, "machine-shop.assembly-bench", 0);
        f.put(104, 80, "doodad.industrial-pump-set", 0);
        f.put(109, 83, "machine-shop.electrical", 0);
        // Receiving aisle under the freight doors.
        f.put(86, 84, "doodad.stores-cargo-loader", 0);
        f.put(99, 84, "doodad.industrial-pallet-stack", 0);
        f.put(99, 80, "doodad.stores-bin-rack", 0);
        // South bay by the gallery door: the shared inspection bench and stock.
        f.put(92, 59, "machine-shop.inspection-bench", 0);
        f.put(97, 58, "machine-shop.bar-stock", 0);
        // Tool crib.
        f.put(76, 58, "doodad.stores-rack-run", 0);
        f.put(80, 58, "doodad.stores-bin-rack", 0);
        f.put(76, 62, "auto-shop.tool-chest", 0);
        // Calibration room: the marked machine on its painted plinth, its console and bench.
        f.put(TOOL_X, TOOL_Y, TOOL_ID, 0);
        f.mark(TOOL_X - 2, TOOL_Y - 2, TOOL_X + 2, TOOL_Y + 2);
        f.put(110, 65, "doodad.industrial-control-console", 0);
        f.put(104, 58, "doodad.industrial-engine-control-board", 0);
        // Operator entrance: suit lockers west of the lock, pressurisation pumps east of it.
        f.put(80, 16, "colony-furnishings.locker", 0);
        f.put(80, 17, "colony-furnishings.locker", 0);
        f.put(83, 16, "colony-furnishings.locker", 0);
        f.put(93, 15, "doodad.industrial-pump-set", 0);
        f.put(95, 17, "doodad.industrial-fluid-tank", 0);
        f.put(81, 33, "colony-furnishings.desk", 0);
        f.put(94, 33, "doodad.industrial-switchboard", 0);
        // District heating service: turbines and accumulators west, pumps and switchgear east.
        f.put(93, 116, "doodad.industrial-turbine-set", 0);
        f.put(93, 111, "doodad.industrial-fluid-tank", 0);
        f.put(97, 111, "doodad.industrial-fluid-tank", 0);
        f.put(107, 117, "doodad.industrial-pump-set", 0);
        f.put(111, 117, "doodad.industrial-pump-set", 0);
        f.put(108, 112, "doodad.industrial-switchboard", 0);
        f.put(115, 119, "doodad.industrial-generator", 0);
        var yard = new StoryServiceYard(c);
        // The heat main runs in the gap between the yard wall and the frontage street.
        for (int x = 92; x <= 120; x += 4) yard.prop(x, 99, "industrial-pipe-bundle", 0);
        // Utility runs beside the gallery; lay-down stock leaves the courts open for lift and mechs.
        yard.prop(92, 41, "industrial-pipe-bundle", 0);
        yard.prop(92, 51, "industrial-pipe-bundle", 0);
        yard.prop(84, 41, "industrial-cable-reel", 0);
        yard.cargo(52, 30); yard.cargo(66, 44); yard.cargo(104, 44); yard.cargo(118, 30);
        yard.cargo(116, 92); yard.cargo(56, 92);
        yard.prop(120, 66, "industrial-drum-cluster", 0);
        yard.prop(78, 95, "industrial-scrap-pile", 0);
        // Inbound truck at its lay-down bay, clear of the gate's line of advance.
        yard.bay(48, 36, 60, 46);
        yard.prop(50, 40, "parked-cargo-truck", 0);
        // The handover bay at the dispatch yard door.
        yard.bay(HANDOVER.x() - 4, 61, HANDOVER.x() + 4, 63);
    }

    /**
     * Old furnace terraces east of the yard. The service lane crosses them to the Cinder Steps gate,
     * with a spur north to the homes' street. Each terrace edge is a broken line of furnace scrap.
     */
    private static void cinderSteps(GenContext c) {
        int gravel = c.topology.surfaceIndex("floors.stone");
        for (int y = 14; y <= 99; y++) for (int x = 130; x < WIDTH; x++) {
            boolean lane = y >= 40 && y <= 46 || x >= 155 && x <= 160 && y > 46;
            if (lane) { pave(c, x, y, GroundKind.STREET, Material.CONCRETE, .8); continue; }
            if (((x - 130) / 8) % 2 == 1) { c.topology.setSurface(x, y, gravel); c.markMadeGround(x, y); }
        }
        var yard = new StoryServiceYard(c);
        String[] scrap = {"industrial-scrap-pile", "industrial-drum-cluster", "industrial-scrap-pile"};
        int piece = 0;
        for (int x : new int[]{137, 145, 153, 163, 171}) for (int y = 18; y <= 95; y += 5) {
            if (y >= 36 && y <= 50 || x >= 153 && x <= 163 && y > 46) continue;
            if ((y / 5 + x) % 3 == 0) continue;
            yard.prop(x, y, scrap[piece++ % scrap.length], 0);
        }
        // Cold furnace housings on the terraces, standing off the lane.
        for (int[] at : new int[][]{{140, 30}, {148, 58}, {140, 74}, {166, 26}, {166, 66}})
            yard.prop(at[0], at[1], "industrial-reactor-housing", 0);
    }

    /**
     * The yard as the third chapter left it, for Terms of Protection. Nothing the inspection
     * established is erased: the operator entrance is now a constables' sandbagged post on the
     * service road, the inspected calibration room holds the captured Greyhook rig tooling on its
     * benches for commissioning, and the nave keeps the inspection's cable runs and crates. On the
     * Cinder Steps terraces a dug pit faces the yard; it is a place, not an enemy.
     */
    private static void afterInspection(GenContext c) {
        var yard = new StoryServiceYard(c);
        // Constables' post outside the outer pressure door, either side of the threshold.
        for (int x = 82; x <= 85; x++) yard.prop(x, 12, "sandbag-straight-s", 0);
        for (int x = 91; x <= 94; x++) yard.prop(x, 12, "sandbag-straight-s", 0);
        // The inspection's work left in place: captured rig tooling on the calibration-room benches.
        var f = new Fixtures(c);
        f.near(109, 58, "machine-shop.assembly-bench", 0, CALIBRATION_LEFT, CALIBRATION_BOTTOM, CALIBRATION_RIGHT, CALIBRATION_TOP,
                COMMISSIONING.x(), COMMISSIONING.y());
        f.near(104, 62, "auto-shop.tool-chest", 0, CALIBRATION_LEFT, CALIBRATION_BOTTOM, CALIBRATION_RIGHT, CALIBRATION_TOP,
                COMMISSIONING.x(), COMMISSIONING.y());
        // Inspection stock along the nave's south side, clear of both doors and the handover bay.
        f.near(98, 69, "doodad.industrial-pallet-stack", 0, 92, 66, 101, 71, HANDOVER.x(), HANDOVER.y());
        f.near(78, 70, "doodad.stores-bin-rack", 0, 72, 66, 84, 71, HANDOVER.x(), HANDOVER.y());
        // The mortar pit on the terraces: a sandbag wall facing the yard, open to the lane behind it.
        // The terrace scrap already standing in the pit's line serves as its wall where it lies.
        for (int y = MORTAR_POST.y() - 2; y <= MORTAR_POST.y() + 2; y++) pitBag(c, yard, MORTAR_POST.x() - 3, y, "sandbag-straight-w");
        pitBag(c, yard, MORTAR_POST.x() - 2, MORTAR_POST.y() - 3, "sandbag-straight-s");
        pitBag(c, yard, MORTAR_POST.x() - 2, MORTAR_POST.y() + 3, "sandbag-straight-n");
    }

    private static void pitBag(GenContext c, StoryServiceYard yard, int x, int y, String id) {
        if (c.grid.isWalkable(x, y) && !c.topology.isFixture(x, y)) yard.prop(x, y, id, 0);
    }

    /** Whether a cell lies inside the calibration room's own walls, where commissioning is done. */
    public static boolean insideCalibrationRoom(int x, int y) {
        return x >= CALIBRATION_LEFT && x <= CALIBRATION_RIGHT && y >= CALIBRATION_BOTTOM && y <= CALIBRATION_TOP;
    }

    public static boolean inside(CellTopology topology, int x, int y, int index) {
        var site = SITES.get(index);
        return x >= site.left() && x <= site.right() && y >= site.bottom() && y <= site.top()
                && topology.hasBuildingKindHint(x,y) && !topology.isWall(x,y);
    }

    /** Authored building outlines on the generation grid. Masks and windows are applied by {@link #finish}. */
    private static final class Draft {
        private final GenContext c;
        private final boolean[] drafted;
        private final List<int[]> windows = new ArrayList<>();
        private String wall, floor;
        private BuildingKind kind;

        Draft(GenContext c) { this.c = c; drafted = new boolean[c.width * c.height]; }

        void shell(int l, int b, int r, int t, String wall, String floor, BuildingKind kind) {
            this.wall = wall; this.floor = floor; this.kind = kind;
            for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
                if (x == l || x == r || y == b || y == t) wall(x, y, wall, kind);
                else floor(x, y);
            }
        }

        void wall(int x, int y, String surface, BuildingKind hint) {
            c.grid.setWalkable(x, y, false); c.grid.setSeeThrough(x, y, false); c.grid.setDoorway(x, y, false);
            c.topology.setWall(x, y, true);
            if (hint != null) { c.topology.setGroundKind(x, y, GroundKind.STRIPED); c.topology.setBuildingKindHint(x, y, hint); }
            c.topology.setSurface(x, y, c.topology.surfaceIndex(surface));
            c.markMadeGround(x, y);
            drafted[y * c.width + x] = true;
        }

        void line(int x0, int y0, int x1, int y1) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) wall(x, y, wall, kind);
        }

        private void floor(int x, int y) {
            c.grid.setWalkable(x, y, true); c.grid.setSeeThrough(x, y, true); c.grid.setDoorway(x, y, false);
            c.topology.setWall(x, y, false);
            c.topology.setGroundKind(x, y, GroundKind.STRIPED);
            c.topology.setBuildingKindHint(x, y, kind);
            c.topology.setSurface(x, y, c.topology.surfaceIndex(floor));
            c.markMadeGround(x, y);
            drafted[y * c.width + x] = true;
        }

        void accent(int x, int y, String surface) {
            if (!c.topology.isWall(x, y) && !c.grid.isDoorway(x, y)) c.topology.setSurface(x, y, c.topology.surfaceIndex(surface));
        }

        void door(int x, int y) { floor(x, y); c.grid.setDoorway(x, y, true); }

        void window(int x, int y, Direction outward) { floor(x, y); windows.add(new int[]{x, y, outward.ordinal()}); }

        void finish() {
            for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
                if (!drafted[y * c.width + x]) continue;
                int mask = 0;
                if (c.topology.isWall(x, y)) {
                    for (Direction side : Direction.CARDINALS) {
                        int nx = x + side.dx, ny = y + side.dy;
                        if (!c.grid.inBounds(nx, ny) || !c.topology.isWall(nx, ny)) mask |= bit(side);
                    }
                } else if (c.grid.isDoorway(x, y)) {
                    boolean upright = c.topology.isWall(x, y - 1) || c.topology.isWall(x, y + 1);
                    mask = bit(upright ? Direction.E : Direction.N);
                    for (Direction side : Direction.CARDINALS) {
                        int nx = x + side.dx, ny = y + side.dy;
                        if (c.grid.inBounds(nx, ny) && !c.topology.hasBuildingKindHint(nx, ny)) { mask = bit(side); break; }
                    }
                }
                c.topology.setWallDirMask(x, y, mask);
            }
            for (int[] w : windows)
                c.grid.placeEdgeBarrier(w[0], w[1], Direction.values()[w[2]], SharedEdgeBarrier.Kind.WINDOW);
            windows.clear();
        }

        private static int bit(Direction side) {
            return switch (side) {
                case N -> CellTopology.WALL_DIR_N;
                case E -> CellTopology.WALL_DIR_E;
                case S -> CellTopology.WALL_DIR_S;
                default -> CellTopology.WALL_DIR_W;
            };
        }
    }

    /** Authored equipment: every footprint must land on open floor away from a threshold, or generation fails. */
    private static final class Fixtures {
        private final GenContext c;
        Fixtures(GenContext c) { this.c = c; }

        /**
         * Places equipment at the legal cell nearest the preferred one inside the given bounds, for a
         * later visit whose room was already furnished by an earlier stage; the cell kept free stays free.
         */
        void near(int x, int y, String id, int turns, int left, int bottom, int right, int top, int keepX, int keepY) {
            var def = TileRegistry.installed().doodad(id);
            if (def == null) throw new IllegalStateException("Missing Latchkey asset: " + id);
            for (int radius = 0; radius <= Math.max(right - left, top - bottom); radius++)
                for (int yy = y - radius; yy <= y + radius; yy++) for (int xx = x - radius; xx <= x + radius; xx++) {
                    if (Math.max(Math.abs(xx - x), Math.abs(yy - y)) != radius) continue;
                    var prop = new Doodad(xx, yy, def, turns);
                    if (xx < left || yy < bottom || xx + prop.footprintCellsX - 1 > right || yy + prop.footprintCellsY - 1 > top) continue;
                    if (keepX >= xx - 1 && keepX <= xx + prop.footprintCellsX && keepY >= yy - 1 && keepY <= yy + prop.footprintCellsY) continue;
                    if (fits(prop)) { put(xx, yy, id, turns); return; }
                }
            throw new IllegalStateException("No room for Latchkey fixture " + id + " near " + x + "," + y);
        }

        private boolean fits(Doodad prop) {
            for (int yy = prop.cellY; yy < prop.cellY + prop.footprintCellsY; yy++) for (int xx = prop.cellX; xx < prop.cellX + prop.footprintCellsX; xx++) {
                if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy)) return false;
                for (Direction side : Direction.CARDINALS)
                    if (c.grid.inBounds(xx + side.dx, yy + side.dy) && c.grid.isDoorway(xx + side.dx, yy + side.dy)) return false;
            }
            return true;
        }

        void put(int x, int y, String id, int turns) {
            var def = TileRegistry.installed().doodad(id);
            if (def == null) throw new IllegalStateException("Missing Latchkey asset: " + id);
            var prop = new Doodad(x, y, def, turns);
            for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
                if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy))
                    throw new IllegalStateException("Latchkey fixture " + id + " overlaps at " + xx + "," + yy);
                for (Direction side : Direction.CARDINALS)
                    if (c.grid.inBounds(xx + side.dx, yy + side.dy) && c.grid.isDoorway(xx + side.dx, yy + side.dy))
                        throw new IllegalStateException("Latchkey fixture " + id + " blocks a threshold at " + xx + "," + yy);
            }
            for (int yy = y; yy < y + prop.footprintCellsY; yy++) for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
                c.grid.setWalkable(xx, yy, false); c.grid.setSeeThrough(xx, yy, true); c.topology.setFixture(xx, yy, true);
            }
            c.doodads.add(prop);
        }

        /** Hazard corner marks painted on the floor; they occupy nothing. */
        void mark(int l, int b, int r, int t) {
            var def = TileRegistry.installed().doodad("doodad.fl-striped-yellow");
            for (int[] at : new int[][]{{l, b}, {r, b}, {l, t}, {r, t}})
                c.doodads.add(new Doodad(at[0], at[1], def, at[1] == t ? 0 : 1));
        }
    }
}
