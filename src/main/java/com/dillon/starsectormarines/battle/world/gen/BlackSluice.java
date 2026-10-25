package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.SummitDressing;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Black Sluice, the outer Silt Basin's fuel and dispatch apron on airless Agreus.
 *
 * <p>The ground fight is the northern band. One causeway transfer road runs east between walled settling beds, so the
 * road itself is the exposed approach; sealed service galleries north and south of the beds are the infantry flanks.
 * Customs stands at the causeway's landward end, the sheltered fuel-control house at its seaward end beside the
 * marked fuel tanks, and dispatch on the pier beyond. A separate service road runs east to a vehicle pressure lock
 * under the berth yard's wall: Greyhook's declared way out. East of dispatch the walled berth yard (warehouses, fuel
 * mains, sealed galleries and pressure vestibules) ends in the berth gate.
 *
 * <p>Dispatch's berths lie south, below a broken ridge, where Greyhook's ships sit at true landed scale
 * ({@code landed-ships-true-scale.md}): a concrete cradle for the Venture with an empty frigate pad either side, on a pad
 * field among rock hills. The ridge and the rock massif west of the pads have no way through; the only way down is
 * out of the berth gate and along the berth road down the east side ({@link #SOUTH_GROUND_TOP}). The duel overhead
 * lifts from those pads and goes where its fighting takes it.
 *
 * <p>West of the landing apron lies {@link #APPROACH} cells of open approach ground ({@link #approach}): the basin's
 * flats with the transfer road running on inland north of the ridge, and rim country south of it. Nothing of the
 * ground fight is out there; it is the ground Rusk's ships cross, sized so that they enter outside their own reach of
 * Greyhook's pads.
 */
public final class BlackSluice {
    /**
     * The open approach ground west of the landing apron, in cells: the outer Silt Basin's flats and the broken rim
     * country south of them, which Rusk's ships fly in across. Every place of the station lies this far east of the map's
     * west edge. Sized off the hull fight's reach ({@link #approach}).
     */
    public static final int APPROACH = 160;
    public static final int WIDTH = APPROACH + 300, HEIGHT = 232;
    /** Bottom row of the northern band, where the ground fight is: everything north of the ridge. */
    public static final int BAND_BOTTOM = 104;
    public static final int LANDING_X = APPROACH + 24, LANDING_Y = 163;
    /** Plain staging ground south of customs: an open bay off the apron, nothing to build. */
    public static final int STAGING_X = APPROACH + 54, STAGING_Y = 152;
    /** One visible support gun on the causeway shoulder in front of fuel control. */
    public static final int GUN_X = APPROACH + 121, GUN_Y = 164;
    public static final String TANK_ID = "doodad.industrial-fluid-tank";
    /** The marked preservation fuel infrastructure: the four-tank farm behind fuel control. */
    public static final List<int[]> TANKS = List.of(new int[]{305, 188}, new int[]{307, 188}, new int[]{305, 190}, new int[]{307, 190});
    /** Service-road gate: the mouth of the vehicle pressure lock; physical departures leave the battle here. */
    public static final int EXIT_X = APPROACH + 196, EXIT_Y = 127;
    /** Named ingress points for the finite reserves. */
    public static final int SERVICE_INGRESS_X = APPROACH + 186, SERVICE_INGRESS_Y = 127;
    public static final int PIER_INGRESS_X = APPROACH + 188, PIER_INGRESS_Y = 156;
    /** Inner ends of the two service galleries, where flanking infantry come out. */
    public static final int NORTH_GALLERY_EXIT_X = APPROACH + 122, NORTH_GALLERY_EXIT_Y = 188;
    public static final int SOUTH_GALLERY_EXIT_X = APPROACH + 112, SOUTH_GALLERY_EXIT_Y = 139;
    /** The berth gate: the pressure lock through the berth yard's east wall, the one way down to the pads. */
    public static final int GATE_X = APPROACH + 266, GATE_Y = 160;
    /** Where the berth road runs down the east side, and the pad field's taxiway point every berth is walked to from. */
    public static final int BERTH_ROAD_X = APPROACH + 283, PADS_X = APPROACH + 196, PADS_Y = 68;
    public record Site(String id, String name, int x, int y, int left, int bottom, int right, int top) { }
    public static final List<Site> SITES = List.of(
            new Site("customs", "Causeway customs block", 218, 173, 212, 168, 224, 178),
            new Site("fuel-control", "Fuel-control house", 283, 174, 278, 170, 288, 179),
            new Site("dispatch", "Dispatch pier", 326, 160, 320, 152, 339, 168));
    public record Place(String id, String label, int x, int y) { }
    public static final List<Place> PLACES = List.of(
            new Place("apron", "Western landing apron", LANDING_X, LANDING_Y),
            new Place("staging", "Staging ground south of customs", STAGING_X, STAGING_Y),
            new Place("north-gallery", "North service gallery", NORTH_GALLERY_EXIT_X, NORTH_GALLERY_EXIT_Y),
            new Place("south-gallery", "South service gallery", SOUTH_GALLERY_EXIT_X, SOUTH_GALLERY_EXIT_Y),
            new Place("gun", "Support-gun position", GUN_X - 2, GUN_Y),
            new Place("service-road", "Service road gate", SERVICE_INGRESS_X, SERVICE_INGRESS_Y),
            new Place("pier", "Pier berth", PIER_INGRESS_X, PIER_INGRESS_Y),
            new Place("berth-gate", "Berth gate", GATE_X, GATE_Y),
            new Place("berth-road", "Berth road down to the pads", BERTH_ROAD_X, 110),
            new Place("pads", "Southern berths", PADS_X, PADS_Y));
    /** The walled berth yard east of the dispatch apron, walls included; it runs to the band's north edge. */
    public static final int DOCK_LEFT = APPROACH + 202, DOCK_BOTTOM = 106, DOCK_RIGHT = APPROACH + 265, DOCK_TOP = HEIGHT - 1;
    /**
     * The top of the southern ground: every row below this, across the whole width, is ridge, rock and pad field, with
     * nobody of the ground fight on it.
     */
    public static final int SOUTH_GROUND_TOP = 88;
    /**
     * A landed ship's berth: a concrete pad ({@code left..right, bottom..top}), open to sight and closed to feet, and
     * where the hull's drawn centre sits on it, nose toward its climb-out ({@code facing}, in warship degrees).
     */
    public record Berth(String id, String label, float x, float y, float facing, int left, int bottom, int right, int top) {
        public boolean contains(int cx, int cy) { return cx >= left && cx <= right && cy >= bottom && cy <= top; }
    }
    /** The Venture's cradle: fifty by fifty-one cells of hull at true scale, nose west, with a cell or two of clearance. */
    public static final Berth VENTURE_CRADLE = new Berth("venture-cradle", "Venture cradle", 356.5f, 33.5f, 90f, 330, 6, 382, 60);
    /**
     * The two frigate pads flanking the cradle, each sized for nineteen by sixteen cells of hull. They stand empty: the
     * <i>Greyhook</i> sits on the field alone, and the side pads are open ground with their gantry arms.
     */
    public static final List<Berth> SIDE_PADS = List.of(
            new Berth("pad-west", "West pad", 302.5f, 34f, 90f, 291, 25, 313, 42),
            new Berth("pad-east", "East pad", 410.5f, 34f, 90f, 399, 25, 421, 42));
    /**
     * Gantry arms: walkable boarding catwalks reaching from the kerb over each pad to its hull's beam, as
     * {@code left, bottom, right, top, rootX, rootY} where the root is the kerb cell the arm is boarded from.
     */
    private static final int[][] GANTRY_ARMS = {
            {346, 57, 348, 60, 347, 61}, {364, 57, 366, 60, 365, 61}, {346, 6, 348, 9, 347, 5}, {364, 6, 366, 9, 365, 5},
            {301, 40, 303, 42, 302, 43}, {301, 25, 303, 27, 302, 24}, {409, 40, 411, 42, 410, 43}, {409, 25, 411, 27, 410, 24}};
    /** Interior of the berth gate's lock, for the check that it is the only way down. */
    private static final int GATE_LEFT = APPROACH + 262, GATE_BOTTOM = 155, GATE_RIGHT = APPROACH + 270, GATE_TOP = 165;
    private BlackSluice() { }

    public static MapResult generate(long seed, TargetProfile profile) {
        var c = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Black Sluice fuel and dispatch apron", List.of(BlackSluice::grounds, BlackSluice::terrain,
                BlackSluice::basin, BlackSluice::dock, BlackSluice::berths, BlackSluice::facilities, new FinalizeStage(),
                new OutdoorLandscapeStage(LANDING_X, LANDING_Y, STAGING_X, STAGING_Y,
                        SITES.get(0).x(), SITES.get(0).y(), SITES.get(1).x(), SITES.get(1).y(), SITES.get(2).x(), SITES.get(2).y(),
                        SERVICE_INGRESS_X, SERVICE_INGRESS_Y, PIER_INGRESS_X, PIER_INGRESS_Y, GATE_X, GATE_Y, BERTH_ROAD_X, 110))).run(c);
        var map = new MapResult(c.grid, c.topology, LANDING_X, LANDING_Y, PIER_INGRESS_X, PIER_INGRESS_Y,
                c.pois, c.doodads, new TacticalMap(HeldPlaces.fromBuildings(c.pois, poi -> Faction.DEFENDER)), c.get(BspKeys.BUILDINGS), List.of(), null, c.landingPads);
        for (var site : SITES)
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, site.x(), site.y()).length == 0)
                throw new IllegalStateException("Disconnected Black Sluice objective: " + site.name());
        for (var place : PLACES)
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, place.x(), place.y()).length == 0)
                throw new IllegalStateException("Disconnected Black Sluice place: " + place.label());
        if (GridPathfinder.findPath(map.grid, SITES.get(1).x(), SITES.get(1).y(), EXIT_X, EXIT_Y).length == 0)
            throw new IllegalStateException("Black Sluice service road does not reach its gate");
        // Every berth is reached on foot, through the gate: each gantry arm's root is on the pad field's own floor.
        for (int[] arm : GANTRY_ARMS)
            if (GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, arm[4], arm[5]).length == 0)
                throw new IllegalStateException("Black Sluice gantry arm cut off at " + arm[4] + "," + arm[5]);
        // ... and only through the gate: with its lock shut, the pads are out of reach of the northern band.
        List<int[]> shut = new ArrayList<>();
        for (int y = GATE_BOTTOM; y <= GATE_TOP; y++) for (int x = GATE_LEFT; x <= GATE_RIGHT; x++)
            if (map.grid.isWalkable(x, y)) { shut.add(new int[]{x, y}); map.grid.setWalkable(x, y, false); }
        boolean bypass = GridPathfinder.findPath(map.grid, LANDING_X, LANDING_Y, PADS_X, PADS_Y).length != 0;
        for (int[] cell : shut) map.grid.setWalkable(cell[0], cell[1], true);
        if (bypass) throw new IllegalStateException("Black Sluice pads are reachable without the berth gate");
        return map;
    }

    private static void grounds(GenContext c) {
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, GroundKind.STONE);
            boolean band = y >= BAND_BOTTOM;
            boolean apron = band && x >= APPROACH && x < 204;
            boolean causeway = x >= 204 && x < 320 && y >= 160 && y <= 167;
            boolean pier = x >= 318 && x < DOCK_LEFT && y >= 144 && y <= 176;
            boolean serviceRoad = x >= 280 && x <= 356 && y >= 124 && y <= 130 || x >= 291 && x <= 296 && y >= 124 && y <= 167;
            boolean dock = x > DOCK_LEFT && x < DOCK_RIGHT && y > DOCK_BOTTOM && y < DOCK_TOP;
            boolean rear = x >= 204 && x <= 224 && y >= 146 && y <= 159;
            // South of the ridge: the berth road from the gate down the east side, the taxiway along the pads and
            // the working aprons around each pad and its sheds. The ground between is left as rock.
            boolean gateSpur = x >= 432 && x <= 448 && y >= 154 && y <= 166;
            boolean berthRoad = x >= 438 && x <= 448 && y >= 64 && y <= 166;
            boolean taxiway = x >= 268 && x <= 448 && y >= 64 && y <= 71;
            boolean ventureApron = x >= 324 && x <= 388 && y >= 2 && y <= 63;
            boolean sidePadAprons = (x >= 285 && x <= 319 || x >= 393 && x <= 427) && y >= 19 && y <= 63;
            boolean shedYard = x >= 268 && x <= 284 && y >= 42 && y <= 63;
            boolean controlYard = x >= 428 && x <= 448 && y >= 28 && y <= 63;
            if (apron || causeway || pier || serviceRoad || rear || dock || gateSpur || berthRoad || taxiway
                    || ventureApron || sidePadAprons || shedYard || controlYard) {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                // Worn as it was before the approach was added: the wear pattern is keyed to the station, not the map.
                c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.75, 18535, x - APPROACH, y))));
                c.markMadeGround(x, y);
            } else if (band && x < APPROACH && transferRoad(x, y)) {
                // The transfer road on inland from the causeway's landward end, across the flats: older and more broken.
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, c.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.95, 18536, x, y))));
                c.markMadeGround(x, y);
            }
        }
        // The company's boats come in over the north edge, the apron's nearest: from the west they would cross the
        // whole approach on every trip, and three trips each made the company land over three minutes instead of one.
        for (int[] pad : new int[][]{{178, 152}, {190, 162}, {178, 174}, {190, 185}})
            c.landingPads.add(new LandingPad(pad[0], pad[1], 8, 3, LandingPad.Approach.NORTH, LandingPad.Purpose.FREIGHT_APRON));
    }

    /**
     * The transfer road across the approach: it leaves the apron on the causeway's line and wanders a little on its
     * way to the west edge, five or six cells wide.
     */
    static boolean transferRoad(int x, int y) {
        double centre = 163.5 + 3 * Math.sin(x / 23.0) + 1.5 * Math.sin(x / 9.0 + 1) - 3 * Math.sin(APPROACH / 23.0)
                - 1.5 * Math.sin(APPROACH / 9.0 + 1);
        return Math.abs(y + .5 - centre) <= 2.8;
    }

    /**
     * The rock between the fight and the pads, laid on bare ground before anything is built.
     *
     * <p>A broken, knolled ridge runs the width of the map under the northern band; it is what makes the berth gate
     * the only way down. South of it the pad field sits in a basin: the basin's rim is the planet's own country,
     * massed off the south edge, down the east side and along the basin's west rim, its inland toe a line of
     * overlapping lobes and spurs of different heights broken by gullies, with outcrops standing out on the flats
     * below. Scree fans spill from the toes and regolith drifts blend the rock into the open ground.
     *
     * <p>West of the station lies the approach ({@link #approach}): the open country Rusk's ships fly in across.
     */
    private static void terrain(GenContext c) {
        TargetProfile profile = c.get(BspKeys.MARKET_PROFILE);
        SurfacePalette surface = profile == null ? SurfacePalette.ROCK : profile.surface();
        TerrainPlan.Finish finish = surface == SurfacePalette.VERDANT ? TerrainPlan.Finish.GRASS_TOP
                : surface == SurfacePalette.FROZEN ? TerrainPlan.Finish.ICE : TerrainPlan.Finish.ROCK;
        // The station's own country, authored from the apron's west edge and laid APPROACH cells east.
        List<TerrainPlan.Stroke> station = new ArrayList<>();
        // The ridge: a meandering spine of varying height, shouldered by knolls on both faces.
        station.add(raise(6.5, 0, 88, 16, 85, 34, 90, 52, 93, 70, 88, 88, 84, 108, 87, 126, 92, 146, 89, 166, 84, 186, 86,
                204, 91, 224, 88, 244, 84, 262, 88, 266, 90));
        for (double[] knoll : new double[][]{{30, 89, 8.5}, {62, 84, 8.5}, {98, 89, 8.5}, {138, 85, 8}, {176, 89, 8.5},
                {214, 85, 8}, {248, 88, 8}})
            station.add(raise(knoll[2], knoll[0] - 6, knoll[1] - 1, knoll[0] + 6, knoll[1] + 1));
        // The basin's west and south-west rim: lobes of different sizes reaching inland.
        station.add(raise(20, 0, 2, 0, 70));
        station.add(raise(18, 2, 0, 92, 0));
        station.add(raise(26, 0, 34));
        station.add(raise(22, 14, 8));
        station.add(raise(19, 42, 2));
        station.add(raise(15, 70, 0, 80, 3));
        station.add(raise(17, 6, 60));
        // Spurs running out from the rim toward the pads, each a different length and height.
        station.add(raise(9, 24, 40, 44, 46, 58, 44));
        station.add(raise(7, 20, 22, 46, 26, 66, 22));
        station.add(raise(6, 14, 66, 32, 70, 44, 66));
        station.add(raise(5.5, 58, 10, 78, 16, 88, 12));
        // Broken ribs, not round knobs: low, narrow, running the way the spurs do and nicked through.
        station.add(raise(2.5, 72, 40, 78, 37, 84, 38, 89, 34));
        station.add(raise(2, 64, 55, 69, 53, 72, 49));
        station.add(raise(2, 86, 50, 92, 47));
        station.add(raise(1.5, 50, 64, 55, 62));
        station.add(erase(1.5, 80, 41, 82, 34));
        // The south rim past the pads, low and broken, and the east rim behind the berth road.
        station.add(raise(7, 108, 0, 132, 2, 150, 0));
        station.add(raise(8, 244, 0, 270, 2, 299, 0));
        station.add(raise(9, 299, 0, 299, 14));
        station.add(raise(7, 299, 60, 299, 104, 299, 150));
        station.add(raise(3, 112, 12));
        station.add(raise(3, 274, 12));
        station.add(raise(6, 284, 200, 290, 214));
        // Gullies cut back into the rim, so the lobes read as eroded country rather than placed masses.
        station.add(erase(4, 40, 30, 22, 34));
        station.add(erase(3.5, 48, 14, 34, 18));
        station.add(erase(3.5, 30, 56, 14, 52));
        station.add(erase(3, 70, 8, 62, 2));
        // An open crescent north of fuel control: shape for the band, never a sealed island.
        List<Double> rim = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            double a = Math.PI * 0.15 + i * Math.PI / 6;
            rim.add(165 + Math.cos(a) * 9);
            rim.add(212 + Math.sin(a) * 9);
        }
        station.add(raise(3, rim.stream().mapToDouble(Double::doubleValue).toArray()));
        List<TerrainPlan.Stroke> strokes = approach();
        for (TerrainPlan.Stroke stroke : station) strokes.add(shifted(stroke, APPROACH));
        TerrainBrush.stamp(new TerrainPlan(WIDTH, HEIGHT, finish, 3, .9, c.seed ^ 0x534c55494345L, strokes), c.grid, c.topology);
        new SummitDressing(c).scatter(surface);
        dressBasin(c, surface);
    }

    /**
     * The approach west of the apron, in map cells. It is sized by the hull fight rather than the ground fight: it
     * was sized for a Wolf (H)'s battery of about two hundred cells, when two Wolfs crossing an edge at the station
     * had the western Hound in reach as it lifted. {@link #APPROACH} puts the nearest of Greyhook's pads three hundred
     * cells from the edge; an Enforcer's battery reaches two hundred and seventy, so Rusk's ships are still sighted
     * crossing the edge outside their reach and close for most of a minute before a gun bears.
     *
     * <p>Two kinds of ground. North of the ridge the outer basin's floor runs on from the landing apron as open silt
     * flats, the transfer road crossing them inland ({@link #grounds}), a low broken rim along the north edge and a few
     * worn ribs standing out of the drifts. South of it the ridge runs on to the west edge over rim country: a massif
     * backed off the south and west edges, its lobes and spurs reaching north-east onto a second floor that opens east
     * under the basin's west rim toward the pads. The ridge stays unbroken, so the berth gate is still the only way
     * down on foot.
     */
    private static List<TerrainPlan.Stroke> approach() {
        List<TerrainPlan.Stroke> strokes = new ArrayList<>();
        // The ridge runs on to the west edge, lower and more broken than over the station.
        strokes.add(raise(6.5, 0, 91, 18, 88, 38, 85, 58, 89, 80, 92, 100, 88, 120, 85, 140, 89, APPROACH, 88));
        for (double[] knoll : new double[][]{{12, 88, 8}, {48, 86, 8.5}, {88, 91, 8}, {126, 87, 8.5}})
            strokes.add(raise(knoll[2], knoll[0] - 6, knoll[1] - 1, knoll[0] + 6, knoll[1] + 1));
        // The rim country under Rusk's line of approach: backs off the south and west edges, a mass in the corner.
        strokes.add(raise(22, 0, 0, 0, 60));
        strokes.add(raise(14, 0, 60, 0, 82));
        strokes.add(raise(16, 0, 0, APPROACH, 0));
        strokes.add(raise(30, 10, 12));
        strokes.add(raise(20, 40, 6));
        strokes.add(raise(18, 6, 48));
        // Lobes and spurs reaching north-east onto the floor, each a different length and height.
        strokes.add(raise(11, 20, 30, 38, 36, 50, 34));
        strokes.add(raise(9, 30, 62, 46, 66, 56, 62));
        strokes.add(raise(8, 70, 0, 80, 14, 92, 12));
        strokes.add(raise(7, 112, 0, 118, 12));
        strokes.add(raise(10, 140, 8, 132, 20));
        // Spurs off the far face of the basin's west rim, so it does not stand as a wall over the floor.
        strokes.add(raise(6, 136, 22, 120, 26, 108, 22));
        strokes.add(raise(5, 138, 50, 124, 54, 114, 50));
        // Gullies cut back into the massif and the rim.
        strokes.add(erase(4, 44, 24, 26, 20));
        strokes.add(erase(3.5, 22, 44, 8, 40));
        strokes.add(erase(3.5, 52, 8, 46, 18));
        strokes.add(erase(3, 146, 36, 134, 34));
        strokes.add(erase(3, 150, 60, 138, 62));
        // Worn ribs on the floor.
        strokes.add(raise(2.5, 68, 44, 74, 40, 80, 41, 86, 37));
        strokes.add(raise(2, 96, 58, 101, 55, 104, 50));
        strokes.add(raise(2, 82, 24, 88, 22));
        strokes.add(raise(1.5, 62, 70, 67, 68));
        strokes.add(erase(1.5, 76, 44, 78, 37));
        // North of the ridge: the basin's north rim, backed off the north edge, higher in the west and tapering out
        // before the abandoned beds, with spurs reaching down onto the flats and gullies cut back into it.
        strokes.add(raise(14, 0, HEIGHT - 1, 0, HEIGHT - 40));
        strokes.add(raise(11, 0, HEIGHT - 1, 30, HEIGHT - 4, 58, HEIGHT - 1));
        strokes.add(raise(7, 58, HEIGHT - 1, 82, HEIGHT - 3));
        strokes.add(raise(4, 82, HEIGHT - 3, 90, HEIGHT - 1));
        strokes.add(raise(6, 14, HEIGHT - 10, 26, HEIGHT - 24, 30, HEIGHT - 36));
        strokes.add(raise(4.5, 44, HEIGHT - 6, 50, HEIGHT - 18, 58, HEIGHT - 26));
        strokes.add(raise(3.5, 70, HEIGHT - 3, 74, HEIGHT - 14));
        strokes.add(erase(3, 38, HEIGHT - 1, 36, HEIGHT - 14));
        strokes.add(erase(2.5, 8, HEIGHT - 26, 2, HEIGHT - 30));
        // Worn ribs: long, low and narrow, running the way the drifts lie and broken through in places.
        strokes.add(raise(1.8, 18, 132, 28, 128, 40, 127, 52, 122, 60, 121));
        strokes.add(erase(1.5, 36, 124, 38, 131));
        strokes.add(raise(1.6, 62, 112, 72, 110, 86, 111));
        strokes.add(raise(1.8, 20, 190, 30, 186, 42, 186, 50, 181));
        strokes.add(erase(1.5, 33, 183, 35, 190));
        strokes.add(raise(1.6, 58, 204, 66, 200, 76, 199));
        strokes.add(raise(1.6, 140, 118, 150, 114));
        return strokes;
    }

    /** A stroke moved {@code dx} cells east. */
    private static TerrainPlan.Stroke shifted(TerrainPlan.Stroke stroke, double dx) {
        List<TerrainPlan.Point> points = new ArrayList<>();
        for (TerrainPlan.Point point : stroke.points()) points.add(new TerrainPlan.Point(point.x() + dx, point.y()));
        return new TerrainPlan.Stroke(stroke.mode(), stroke.radius(), points);
    }

    /**
     * The basin floor between the rock and the pads, and the approach's flats: regolith drifts on the open natural
     * ground, and scree fanning out from every toe, thick against the rock and thinning onto the flats. Paint only;
     * footing is unchanged.
     */
    private static void dressBasin(GenContext c, SurfacePalette surface) {
        var terrain = c.topology.terrainSurface();
        boolean[] open = new boolean[WIDTH * HEIGHT];
        int[] distance = new int[WIDTH * HEIGHT];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int i = y * WIDTH + x;
            if (!dressed(x, y)) continue;
            if (!c.grid.isWalkable(x, y)) {
                if (c.grid.hasTag(x, y, CellTag.NATURAL_SOLID)) { distance[i] = 0; frontier.add(new int[]{x, y}); }
                continue;
            }
            if (c.isMadeGround(x, y) || c.topology.getSurface(x, y) != 0) continue;
            boolean patch = false;
            for (int layer = 0; layer < TerrainSurface.LAYERS && !patch; layer++) patch = terrain.patch(x, y, layer) != null;
            open[i] = !patch;
        }
        NatureZoneFiller.dressOpenGround(c, open);
        int reach = 7;
        while (!frontier.isEmpty()) {
            int[] at = frontier.poll();
            int d = distance[at[1] * WIDTH + at[0]];
            if (d >= reach) continue;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = at[0] + step[0], ny = at[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= WIDTH || ny >= HEIGHT || !dressed(nx, ny)) continue;
                int j = ny * WIDTH + nx;
                if (distance[j] <= d + 1) continue;
                distance[j] = d + 1;
                frontier.add(new int[]{nx, ny});
            }
        }
        var tiles = TileRegistry.installed();
        String[] stones = Stream.of("small-1", "small-2", "medium-1").map(surface.stones()::stone).toArray(String[]::new);
        var fans = new PatchField(c.seed ^ 0x5343524545L, 9);
        var random = new Random(c.seed ^ 0x54414c5553L);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int i = y * WIDTH + x;
            if (!open[i] || distance[i] > reach) continue;
            float near = 1f - (distance[i] - 1) / (float) reach;
            // Fans, not a halo: the patch field gathers the scree into tongues below some stretches of toe.
            float chance = near * near * (fans.sample(x, y) < .5f ? .55f : .12f);
            if (random.nextFloat() < chance)
                c.topology.setNatureOverlayIndex(x, y, tiles.tile(stones[random.nextInt(stones.length)]).index);
        }
    }

    /** Natural ground the basin dressing paints: everything south of the band, and the approach. */
    private static boolean dressed(int x, int y) {
        return y < BAND_BOTTOM || x < APPROACH;
    }

    private static TerrainPlan.Stroke erase(double radius, double... xy) {
        List<TerrainPlan.Point> points = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) points.add(new TerrainPlan.Point(xy[i], xy[i + 1]));
        return new TerrainPlan.Stroke(TerrainPlan.Mode.ERASE, radius, points);
    }

    private static TerrainPlan.Stroke raise(double radius, double... xy) {
        List<TerrainPlan.Point> points = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) points.add(new TerrainPlan.Point(xy[i], xy[i + 1]));
        return new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE, radius, points);
    }

    /**
     * Walled settling beds flank the causeway so it is a corridor with no cover of its own; the
     * sealed galleries outside them are the covered alternative. Gallery ends are pressure
     * vestibules: a doorway in each end wall.
     */
    private static void basin(GenContext c) {
        bed(c, 228, 169, 266, 184);
        bed(c, 228, 143, 266, 158);
        bed(c, 228, 192, 266, HEIGHT - 1);
        bed(c, 228, BAND_BOTTOM, 266, 135);
        // The north gallery runs on past the beds to come out behind fuel control, out of the gun's sight.
        hall(c, 224, 185, 280, 191, new int[][]{{224, 187}, {224, 188}, {224, 189}, {280, 187}, {280, 188}, {280, 189}});
        hall(c, 224, 136, 270, 142, new int[][]{{224, 138}, {224, 139}, {224, 140}, {270, 138}, {270, 139}, {270, 140}});
        // Out on the approach, the first beds: silted full and abandoned when the causeway went in, their walls
        // fallen in stretches. Nothing of the fight uses them; they are what the basin looked like before the station.
        ruinedBed(c, 98, 169, 134, 184, new int[][]{{0, 104, 111}, {0, 124, 127}, {1, 101, 106}, {3, 174, 179}});
        ruinedBed(c, 92, 192, 134, 220, new int[][]{{0, 110, 122}, {1, 96, 99}, {1, 118, 131}, {2, 200, 207}, {3, 194, 197}});
        ruinedBed(c, 104, 132, 134, 150, new int[][]{{1, 108, 119}, {0, 126, 131}, {2, 138, 142}, {3, 144, 149}});
    }

    /**
     * An abandoned settling bed: the same masonry wall as a working one, with stretches of it fallen, around silt that
     * has dried and can be walked on. Each gap is {@code {side, from, to}}: side 0 the south wall, 1 the north, 2 the
     * west and 3 the east, and the run of cells along it that is down.
     */
    private static void ruinedBed(GenContext c, int l, int b, int r, int t, int[][] gaps) {
        int wall = c.topology.surfaceIndex("colony.masonry-wall"), silt = c.topology.surfaceIndex("floors.dirt");
        // Regolith has drifted in over the silt in tongues, so the bed reads as given back to the basin.
        var drifts = new PatchField(c.seed ^ 0x4452494654L, 7);
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            int side = y == b ? 0 : y == t ? 1 : x == l ? 2 : x == r ? 3 : -1;
            if (side < 0 && drifts.sample(x, y) < .42f) continue;
            boolean down = false;
            for (int[] gap : gaps) {
                int along = gap[0] < 2 ? x : y;
                down |= gap[0] == side && along >= gap[1] && along <= gap[2];
            }
            boolean standing = side >= 0 && !down;
            c.grid.setWalkable(x, y, !standing);
            c.grid.setSeeThrough(x, y, !standing);
            c.topology.setWall(x, y, standing);
            c.topology.setGroundKind(x, y, standing ? GroundKind.STONE : GroundKind.DIRT);
            c.topology.setSurface(x, y, standing ? wall : silt);
            c.markMadeGround(x, y);
        }
        faceWalls(c, l, b, r, t);
    }

    /**
     * The berth yard: its walls, the vehicle lock the service road ends in, pressure vestibules through the west
     * wall, sealed galleries along the north and south walls, warehouses either side of the concourse, the fuel
     * mains that run along it to the berths, and the berth gate through the east wall. A retaining wall carries the
     * yard's east wall down onto the ridge, so nobody walks round the yard's foot to the berth road.
     */
    private static void dock(GenContext c) {
        int masonry = c.topology.surfaceIndex("colony.masonry-wall");
        for (int y = DOCK_BOTTOM; y <= DOCK_TOP; y++) for (int x = DOCK_LEFT; x <= DOCK_RIGHT; x++)
            if (x == DOCK_LEFT || x == DOCK_RIGHT || y == DOCK_BOTTOM || y == DOCK_TOP) solid(c, x, y, masonry);
        for (int x = DOCK_RIGHT - 1; x <= DOCK_RIGHT; x++)
            for (int y = DOCK_BOTTOM - 1; y > SOUTH_GROUND_TOP && c.grid.isWalkable(x, y); y--) solid(c, x, y, masonry);
        // The service road ends at a vehicle pressure lock under the yard: its frame and its closed inner door.
        for (int x = 350; x < DOCK_LEFT; x++) { solid(c, x, 121, masonry); solid(c, x, 133, masonry); }
        for (int y = 122; y <= 132; y++) for (int x = 357; x < DOCK_LEFT; x++) solid(c, x, y, masonry);
        faceWalls(c, 349, 120, DOCK_LEFT, 134);
        faceWalls(c, DOCK_RIGHT - 2, 0, DOCK_RIGHT + 1, DOCK_TOP);
        faceWalls(c, DOCK_LEFT, DOCK_BOTTOM, DOCK_RIGHT, DOCK_TOP);
        // Sealed galleries along the north and south walls: the covered way round the yard.
        hall(c, DOCK_LEFT, 225, DOCK_RIGHT, DOCK_TOP, new int[][]{{DOCK_LEFT, 227}, {DOCK_LEFT, 228}, {DOCK_LEFT, 229},
                {374, 225}, {375, 225}, {376, 225}, {412, 225}, {413, 225}, {414, 225}});
        hall(c, DOCK_LEFT, DOCK_BOTTOM, DOCK_RIGHT, 112, new int[][]{{DOCK_LEFT, 108}, {DOCK_LEFT, 109}, {DOCK_LEFT, 110},
                {374, 112}, {375, 112}, {376, 112}, {412, 112}, {413, 112}, {414, 112}});
        // Pressure vestibules through the west wall: the main lock off the dispatch apron and two flank locks.
        hall(c, 358, 154, 366, 166, new int[][]{{358, 159}, {358, 160}, {358, 161}, {366, 159}, {366, 160}, {366, 161}});
        hall(c, 358, 202, 366, 210, new int[][]{{358, 205}, {358, 206}, {358, 207}, {366, 205}, {366, 206}, {366, 207}});
        hall(c, 358, 137, 366, 145, new int[][]{{358, 140}, {358, 141}, {358, 142}, {366, 140}, {366, 141}, {366, 142}});
        // The berth gate: a pressure lock through the east wall, on the concourse's line, onto the berth road.
        hall(c, GATE_LEFT - 1, GATE_BOTTOM - 1, GATE_RIGHT + 1, GATE_TOP + 1, new int[][]{
                {GATE_LEFT - 1, 159}, {GATE_LEFT - 1, 160}, {GATE_LEFT - 1, 161},
                {GATE_RIGHT + 1, 159}, {GATE_RIGHT + 1, 160}, {GATE_RIGHT + 1, 161}});
        var yard = new StoryServiceYard(c);
        // Fuel mains: pipe runs either side of the concourse from the pump sets by the main lock toward the gate.
        yard.prop(367, 151, "industrial-pump-set", 0);
        yard.prop(367, 179, "industrial-pump-set", 0);
        for (int x = 372; x <= 388; x++) { yard.prop(x, 151, "industrial-pipe-bundle", 0); yard.prop(x, 169, "industrial-pipe-bundle", 0); }
        for (int x = 402; x <= 416; x++) { yard.prop(x, 151, "industrial-pipe-bundle", 0); yard.prop(x, 169, "industrial-pipe-bundle", 0); }
        yard.prop(410, 126, "industrial-pump-set", 0);
        yard.prop(410, 211, "industrial-pump-set", 0);
        // Cargo lines: stacks, pallets and loaders along the yard walls.
        yard.cargo(369, 216); yard.cargo(369, 119); yard.cargo(363, 184); yard.cargo(363, 148);
        yard.prop(376, 208, "stores-cargo-loader", 0);
        yard.prop(376, 127, "stores-cargo-loader", 0);
        yard.prop(363, 196, "parked-cargo-truck", 0);
        yard.prop(414, 218, "industrial-drum-cluster", 0);
        yard.prop(414, 118, "industrial-drum-cluster", 0);
        yard.prop(368, 192, "industrial-cable-reel", 0);
        yard.prop(368, 146, "industrial-cable-reel", 0);
    }

    /**
     * The pad field south of the ridge: the Venture cradle and the two empty frigate pads, their gantry arms, a pad shed at
     * the west end of the taxiway and a pad-control house by the berth road.
     */
    private static void berths(GenContext c) {
        pad(c, VENTURE_CRADLE);
        for (Berth berth : SIDE_PADS) pad(c, berth);
        int deck = c.topology.surfaceIndex("colony.concrete");
        for (int[] arm : GANTRY_ARMS)
            for (int y = arm[1]; y <= arm[3]; y++) for (int x = arm[0]; x <= arm[2]; x++) {
                c.grid.setWalkable(x, y, true);
                c.grid.setSeeThrough(x, y, true);
                c.topology.setGroundKind(x, y, GroundKind.STRIPED);
                c.topology.setSurface(x, y, deck);
            }
        padAccents(c, VENTURE_CRADLE);
        for (Berth berth : SIDE_PADS) padAccents(c, berth);
        BuildingStamp.T_WORKSHOP.place(c, 270, 47, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 433, 36, 0);
        var yard = new StoryServiceYard(c);
        // Fuel points between the pads, and the stores that serve them.
        yard.prop(317, 50, "industrial-pump-set", 0);
        yard.prop(395, 50, "industrial-pump-set", 0);
        yard.prop(317, 20, "industrial-drum-cluster", 0);
        yard.prop(386, 12, "industrial-drum-cluster", 0);
        yard.cargo(287, 56); yard.cargo(424, 56);
        yard.prop(430, 52, "industrial-cable-reel", 0);
    }

    /**
     * A berth pad: a plain concrete landing slab, open to sight and closed to feet, edged with a painted border and
     * ringed by a walkable kerb.
     */
    private static void pad(GenContext c, Berth berth) {
        int slab = c.topology.surfaceIndex("pavement.concrete.new"), border = c.topology.surfaceIndex("colony.concrete"),
                kerb = c.topology.surfaceIndex("installation.concrete-floor");
        for (int y = berth.bottom() - 1; y <= berth.top() + 1; y++) for (int x = berth.left() - 1; x <= berth.right() + 1; x++) {
            boolean floor = berth.contains(x, y);
            if (!floor && (c.topology.isWall(x, y) || !c.grid.isWalkable(x, y))) continue;
            boolean edge = floor && (x == berth.left() || x == berth.right() || y == berth.bottom() || y == berth.top());
            c.grid.setWalkable(x, y, !floor);
            c.grid.setSeeThrough(x, y, true);
            c.topology.setGroundKind(x, y, GroundKind.STRIPED);
            c.topology.setSurface(x, y, !floor ? kerb : edge ? border : slab);
            c.markMadeGround(x, y);
        }
    }

    /**
     * Restrained working marks on a pad, laid after the gantry arms: hazard-marked corners and tie-down points along
     * the long edges, a line of floor drains inside each, two service hatches, and engine-wash scorch at the stern end.
     * Flat marks only; nothing here changes sight or footing.
     */
    private static void padAccents(GenContext c, Berth berth) {
        var tiles = TileRegistry.installed();
        int w = berth.right() - berth.left(), h = berth.top() - berth.bottom();
        // Drainage: floor drains in a line inset from each long edge, where the slab falls away.
        var drain = tiles.doodad("auto-shop.drain");
        int every = Math.max(5, w / 6);
        for (int y : new int[]{berth.bottom() + 3, berth.top() - 3})
            for (int x = berth.left() + every / 2 + 2; x <= berth.right() - 3; x += every)
                if (onSlab(c, berth, x, y)) c.doodads.add(new Doodad(x, y, drain));
        // Service hatches: a pair of flush deck plates a third of the way in from bow and stern.
        var hatch = tiles.doodad("ship-bay.hatch");
        for (int hx : new int[]{berth.left() + w / 3, berth.right() - w / 3 - 1}) {
            int hy = (berth.bottom() + berth.top()) / 2 + (hx < (berth.left() + berth.right()) / 2 ? -h / 4 : h / 4);
            for (int x = hx; x < hx + 2; x++) if (onSlab(c, berth, x, hy)) c.doodads.add(new Doodad(x, hy, hatch));
        }
        // Hazard-marked corners and tie-down points along the painted edges.
        var tie = tiles.doodad("doodad.fl-striped-yellow");
        int step = Math.max(5, Math.min(w, h) / 3);
        for (int x = berth.left() + 1; x <= berth.right() - 1; x += step) {
            c.doodads.add(new Doodad(x, berth.bottom() + 1, tie, 1));
            c.doodads.add(new Doodad(x, berth.top() - 1, tie, 0));
        }
        c.doodads.add(new Doodad(berth.right() - 1, berth.bottom() + 1, tie, 1));
        c.doodads.add(new Doodad(berth.right() - 1, berth.top() - 1, tie, 0));
        // Engine-wash scorch at the stern end, where the nozzles stand over the slab.
        double nose = Math.toRadians(berth.facing() + 90f);
        int sternward = Math.cos(nose) < 0 ? -1 : 1;
        var scorch = tiles.doodad("ship-wear.scorch");
        var patch = tiles.doodad("ship-wear.patch");
        int cy = (berth.bottom() + berth.top()) / 2;
        int[][] marks = h > 30
                ? new int[][]{{3, -21}, {5, -17}, {3, 15}, {5, 19}, {9, -3}, {7, 1}}
                : new int[][]{{2, -4}, {4, 0}, {2, 3}};
        for (int i = 0; i < marks.length; i++) {
            int x = sternward < 0 ? berth.right() - marks[i][0] - 1 : berth.left() + marks[i][0];
            int y = cy + marks[i][1];
            if (onSlab(c, berth, x, y) && onSlab(c, berth, x + 1, y + 1))
                c.doodads.add(new Doodad(x, y, i % 3 == 2 ? patch : scorch, 0, i % 2 == 0));
        }
    }

    private static boolean onSlab(GenContext c, Berth berth, int x, int y) {
        return berth.contains(x, y) && x > berth.left() && x < berth.right() && y > berth.bottom() && y < berth.top()
                && !c.grid.isWalkable(x, y);
    }

    private static void solid(GenContext c, int x, int y, int surface) {
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, false);
        c.grid.setDoorway(x, y, false);
        c.topology.setWall(x, y, true);
        c.topology.setGroundKind(x, y, GroundKind.STONE);
        c.topology.setSurface(x, y, surface);
        c.markMadeGround(x, y);
    }

    private static void facilities(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 212, 168, 0);
        BuildingStamp.OFFSET_DEPOT.place(c, 278, 170, 0);
        BuildingStamp.FREIGHT_L.place(c, 320, 152, 0);
        // Warehouses either side of the berth yard's concourse.
        BuildingStamp.FREIGHT_L.place(c, 382, 120, 0);
        BuildingStamp.FREIGHT_T.place(c, 382, 178, 0);
        var yard = new StoryServiceYard(c);
        yard.bay(208, 148, 220, 157);
        yard.bay(GUN_X - 2, GUN_Y - 2, GUN_X + 2, GUN_Y + 2);
        yard.gravel(301, 183, 316, 194);
        for (int[] tank : TANKS) {
            var prop = new Doodad(tank[0], tank[1], TileRegistry.installed().doodad(TANK_ID));
            for (int y = tank[1]; y < tank[1] + prop.footprintCellsY; y++)
                for (int x = tank[0]; x < tank[0] + prop.footprintCellsX; x++) {
                    if (!c.grid.isWalkable(x, y) || c.topology.isFixture(x, y))
                        throw new IllegalStateException("Black Sluice fuel tank overlaps circulation");
                    c.grid.setWalkable(x, y, false); c.grid.setSeeThrough(x, y, true); c.topology.setFixture(x, y, true);
                }
            c.doodads.add(prop);
        }
        yard.prop(306, 188, "industrial-pipe-bundle", 0);
        yard.prop(306, 190, "industrial-pipe-bundle", 0);
        yard.prop(309, 189, "industrial-pump-set", 0);
        yard.bay(303, 186, 310, 192);
        yard.prop(312, 185, "parked-tanker-truck", 0);
        yard.prop(312, 191, "parked-tanker-truck", 0);
        yard.perimeter(new int[]{301, 188}, 300, 182, 317, 182, 317, 195, 300, 195);
        yard.cargo(204, 182); yard.cargo(310, 170); yard.cargo(342, 170);
        yard.bay(342, 148, 356, 164);
    }

    /** A walled settling bed: perimeter wall around inaccessible open silt. */
    private static void bed(GenContext c, int l, int b, int r, int t) {
        int wall = c.topology.surfaceIndex("colony.masonry-wall"), silt = c.topology.surfaceIndex("floors.dirt");
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            boolean edge = x == l || x == r || y == b || y == t;
            // The silt inside is ground nobody can reach: the sealed wall is what keeps people out.
            c.grid.setWalkable(x, y, !edge);
            c.grid.setSeeThrough(x, y, !edge);
            c.topology.setWall(x, y, edge);
            c.topology.setGroundKind(x, y, edge ? GroundKind.STONE : GroundKind.DIRT);
            c.topology.setSurface(x, y, edge ? wall : silt);
            c.markMadeGround(x, y);
        }
        faceWalls(c, l, b, r, t);
    }

    /** A sealed service gallery: walled corridor with doorway vestibules at the named cells. */
    private static void hall(GenContext c, int l, int b, int r, int t, int[][] doors) {
        int wall = c.topology.surfaceIndex("colony.olive-wall"), floor = c.topology.surfaceIndex("colony.tread");
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            boolean edge = x == l || x == r || y == b || y == t;
            boolean door = false;
            for (int[] d : doors) door |= d[0] == x && d[1] == y;
            boolean solid = edge && !door;
            c.grid.setWalkable(x, y, !solid);
            c.grid.setSeeThrough(x, y, !solid);
            c.grid.setDoorway(x, y, door);
            c.topology.setWall(x, y, solid);
            c.topology.setGroundKind(x, y, GroundKind.STRIPED);
            c.topology.setBuildingKindHint(x, y, BuildingKind.INDUSTRIAL);
            c.topology.setSurface(x, y, solid ? wall : floor);
            c.markMadeGround(x, y);
            if (door) c.topology.setWallDirMask(x, y, x == l ? CellTopology.WALL_DIR_W : x == r ? CellTopology.WALL_DIR_E
                    : y == b ? CellTopology.WALL_DIR_S : CellTopology.WALL_DIR_N);
        }
        faceWalls(c, l, b, r, t);
    }

    private static void faceWalls(GenContext c, int l, int b, int r, int t) {
        for (int y = Math.max(0, b); y <= Math.min(HEIGHT - 1, t); y++) for (int x = Math.max(0, l); x <= Math.min(WIDTH - 1, r); x++) {
            if (!c.topology.isWall(x, y)) continue;
            int mask = 0;
            if (!wallAt(c, x, y + 1)) mask |= CellTopology.WALL_DIR_N;
            if (!wallAt(c, x, y - 1)) mask |= CellTopology.WALL_DIR_S;
            if (!wallAt(c, x + 1, y)) mask |= CellTopology.WALL_DIR_E;
            if (!wallAt(c, x - 1, y)) mask |= CellTopology.WALL_DIR_W;
            c.topology.setWallDirMask(x, y, mask);
        }
    }

    private static boolean wallAt(GenContext c, int x, int y) {
        return c.grid.inBounds(x, y) && c.topology.isWall(x, y);
    }

    /** Interior of one named site; walls do not count as being inside. */
    public static boolean inside(CellTopology topology, int x, int y, int index) {
        var site = SITES.get(index);
        return x >= site.left() && x <= site.right() && y >= site.bottom() && y <= site.top()
                && topology.hasBuildingKindHint(x, y) && !topology.isWall(x, y);
    }

    /** The declared service-road departure gate, reached by ordinary infantry travel. */
    public static boolean atExit(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && x >= EXIT_X - 3 && x < EXIT_X + 1 && y >= 122 && y <= 132;
    }
}
