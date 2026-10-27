package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.AuthoredRoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Authored small territorial profile. Decoration never adds capture requirements. */
public final class JunctionCompound {
    public static final int WIDTH = 192, HEIGHT = 112;
    /**
     * The two trench lines dug across the company's way in, each {x, top y, bottom y}: one west of the
     * Freight Yard and one west of Dispatch. A rifle squad that walks into a manned trench pays for it;
     * they are where Talon Two goes ahead to work while the company is still taking the site behind.
     */
    public static final List<int[]> TRENCHES = List.of(new int[]{95, 38, 49}, new int[]{133, 43, 54});
    /** The middle cell of trench {@code i}, where its squad is posted and Talon Two circles. */
    public static int[] trenchCentre(int i) {
        int[] t = TRENCHES.get(i);
        return new int[]{t[0], (t[1] + t[2]) / 2};
    }
    /**
     * Wayline's impounded haulers, parked nose-north in the lot between the bonded warehouse and the
     * depot wing, each {x, y} of its centre. Scenery: civilian hulls nobody is sent to fight over.
     */
    public static final List<float[]> IMPOUNDED_HAULERS = List.of(new float[]{130.5f, 71f},
            new float[]{134.5f, 71f}, new float[]{130.5f, 81f}, new float[]{134.5f, 81f});
    /** Where the second counterattack waits, just inside the map on the road from Rill. */
    public static final int RILL_ROAD_X = 174, RILL_ROAD_Y = 4;
    public static final List<String> SITE_NAMES = List.of("Western turnout", "Weigh Station", "Freight Yard", "Junction Dispatch");
    /** @param bondedWarehouse the impound hall north of the main road, across it from the Freight Yard */
    public record Layout(MapResult map, List<TacticalNode> sites, PointOfInterest reserve, PointOfInterest bondedWarehouse) {
        public Layout { sites = List.copyOf(sites); }
    }
    private JunctionCompound() {}

    public static Layout generate(long seed, TargetProfile profile) {
        GenContext c = new GenContext(new NavigationGrid(WIDTH, HEIGHT),
                new CellTopology(WIDTH, HEIGHT), new Random(seed), WIDTH, HEIGHT, seed);
        c.put(BspKeys.MARKET_PROFILE, profile == null ? TargetProfile.NEUTRAL : profile);
        new GenRecipe("Open the Junction", List.of(JunctionCompound::grounds,
                JunctionCompound::buildings, JunctionCompound::perimeter, JunctionCompound::cargo,
                finished -> RoadRasterizer.clearReplacedSurfaces(finished.topology, finished.width, finished.height),
                JunctionCompound::yardSurfaces, JunctionCompound::serviceYards, JunctionCompound::terrain,
                JunctionCompound::trenches,
                new FinalizeStage(), new OutdoorLandscapeStage(21, 56, 148, 55))).run(c);
        List<TacticalNode> sites = new ArrayList<>();
        sites.add(new TacticalNode(TacticalNode.Kind.BEACHHEAD, 21, 56,
                10, 9, 40, 60, Faction.MARINE, 20, 0));
        TacticalNode.Kind[] kinds = {TacticalNode.Kind.BARRACKS, TacticalNode.Kind.ARMORY, TacticalNode.Kind.COMMAND_POST};
        for (int i = 0; i < 3; i++) {
            PointOfInterest p = c.pois.get(i);
            sites.add(new TacticalNode(kinds[i], p.interiorAnchorX, p.interiorAnchorY,
                    p.left, p.top, p.right, p.bottom, Faction.DEFENDER, 50 + i * 10, 6 + 4 * i));
        }
        // The compounds' sandbagged doors face the landing: positions the defence and a company that takes them can man.
        CompoundPreparedGround.layForSites(c, sites, 21, 56);
        List<TacticalNode> tactical = new ArrayList<>(sites);
        tactical.addAll(HeldPlaces.fromBuildingsNotIn(c.pois, sites, poi -> Faction.DEFENDER));
        MapResult map = new MapResult(c.grid, c.topology, 21, 56, 148, 55,
                c.pois, c.doodads, new TacticalMap(tactical), c.get(BspKeys.BUILDINGS),
                List.of(), AuthoredRoadGraph.graph(RoadCatalog.plan(RoadCatalog.JUNCTION), "western-boat-apron"), c.landingPads);
        for (LandingPad pad : map.landingPads) {
            if (!pad.isClear(map.grid, map.topology)) throw new IllegalStateException("Junction berth obstructed");
            requireRoute(map, pad.centerX, pad.centerY);
        }
        for (TacticalNode site : sites) requireRoute(map, site.anchorX, site.anchorY);
        for (PointOfInterest p : c.pois) requireRoute(map, p.interiorAnchorX, p.interiorAnchorY);
        return new Layout(map, sites, c.pois.get(7), c.pois.get(5));
    }

    private static void requireRoute(MapResult map, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid, 21, 56, x, y)))
            throw new IllegalStateException("Junction route disconnected at " + x + "," + y);
    }
    private static void grounds(GenContext c) {
        GroundKind wild = switch (c.get(BspKeys.MARKET_PROFILE).surface()) {
            case VERDANT -> GroundKind.GRASS;
            case FROZEN -> GroundKind.SNOW;
            case ARID -> GroundKind.SAND;
            default -> GroundKind.STONE;
        };
        pave(c, 0, 0, WIDTH - 1, HEIGHT - 1, wild);
        // A rounded working apron, not a blue superblock pasted under the facilities.
        for (int y = 22; y <= 91; y++) for (int x = 56; x <= 164; x++) {
            if (roundedYard(x, y, 0)) c.topology.setGroundKind(x, y,
                    roundedYard(x, y, 2) ? GroundKind.COURTYARD : GroundKind.SIDEWALK);
        }
        // Swept eastern fork and service access share one authored centerline source.

        yardSurfaces(c);
        var roads = RoadCatalog.plan(RoadCatalog.JUNCTION);
        RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        // Staggered, elongated stands fit the player boats on one terminal apron.
        int[][] stands = {{18,50},{32,43},{18,36},{32,29},{18,22},{32,15}};
        for(int i=0;i<5;i++) c.landingPads.add(new LandingPad(stands[i][0],stands[i][1],
                8,3,LandingPad.Approach.WEST,LandingPad.Purpose.FREIGHT_APRON));
        for (int x = 96; x < 160; x += 8) pave(c, x, 25, x + 3, 29, GroundKind.STRIPED);
    }
    private static boolean roundedYard(int x, int y, int inset) {
        int left=56+inset, right=164-inset, bottom=22+inset, top=91-inset;
        if(x<left || x>right || y<bottom || y>top) return false;
        int radius=9-inset;
        int cx=Math.max(left+radius,Math.min(right-radius,x));
        int cy=Math.max(bottom+radius,Math.min(top-radius,y));
        return (x-cx)*(x-cx)+(y-cy)*(y-cy)<=radius*radius;
    }
    /** Apply after buildings and road cleanup so room floors and landing paint retain ownership. */
    private static void yardSurfaces(GenContext c) {
        int[] concrete = new int[3];
        for(int age=0;age<3;age++) concrete[age]=c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for(int y=0;y<c.height;y++) for(int x=0;x<c.width;x++)
            if(c.topology.getGroundKind(x,y)==GroundKind.COURTYARD && !c.topology.isWall(x,y))
                c.topology.setSurface(x,y,concrete[RoadRasterizer.age(y>62 ? .35 : .55,5119,x,y)]);
    }
    private static void terrain(GenContext c) {
        // Reserve the complete western landing approach, including the open ground
        // outside the apron. Aircraft do not gain altitude collision from this mask.
        boolean[] reserved = new boolean[c.width * c.height];
        for (LandingPad pad : c.landingPads)
            for (int y = Math.max(0, pad.bottom() - 2); y <= Math.min(c.height - 1, pad.top() + 2); y++)
                for (int x = 0; x <= pad.right() + 2; x++) reserved[y * c.width + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(TerrainCatalog.JUNCTION,
                c.get(BspKeys.MARKET_PROFILE).surface()), c.grid, c.topology, reserved);
    }
    private static void buildings(GenContext c) {
        BuildingStamp.T_WORKSHOP.place(c, 63, 40, 2); // Weigh-house hall faces the inbound road.
        BuildingStamp.OFFSET_DEPOT.place(c, 100, 39, 1); // Offset loading rooms open onto the service road.
        shell(c, 137, 41, 160, 53, Frontage.BOTTOM); // Dispatch overlooking the junction.
        shell(c, 42, 28, 53, 49, Frontage.BOTTOM); // Screens shield the apron from the gate.
        BuildingStamp.OFFSET_DEPOT.place(c, 44, 69, 1); // Smaller service annex above the apron.
        // The bonded warehouse holds the loads impounded against Wayline's debt; it also blocks a
        // map-wide firing corridor across the north apron.
        shell(c, 92, 65, 126, 87, Frontage.TOP);
        BuildingStamp.FREIGHT_L.place(c, 139, 68, 0); // Depot wing encloses an outdoor loading court.
        shell(c, 92, 8, 115, 17, Frontage.BOTTOM); // Maintenance staging beside the reserve road.
    }
    private static void serviceYards(GenContext c) {
        StoryServiceYard yard = new StoryServiceYard(c);
        // Warehouse overflow: three dispatch bays and a separate pallet-storage court.
        ServiceYardTemplate.FREIGHT_MARSHALLING.place(c, 62, 69, 0);
        // The weigh-house's inspection lay-by faces the loading street, not the main road.
        yard.bay(77, 39, 81, 50);
        yard.prop(78, 45, "parked-utility-truck", 1);
        // Freight office staging lanes leave a continuous cross-yard aisle to Dispatch.
        for (int x : new int[]{117, 125}) {
            yard.bay(x, 39, x + 5, 51);
            yard.cargo(x + 1, 47);
            yard.prop(x + 1, 41, "stores-cargo-loader", 1);
        }
        // The L depot's outdoor recess is a loading court, with the facade route kept clear.
        yard.bay(148, 78, 158, 86);
        yard.cargo(150, 82);
        yard.prop(150, 79, "stores-roller-conveyor", 0);
        yard.prop(156, 80, "stores-cargo-loader", 1);
        // Impounded hauler lot between the bonded warehouse and the depot wing, fenced, its gate on the road.
        yard.perimeter(new int[]{132, 66}, 128, 66, 137, 66, 137, 87, 128, 87);
        for (float[] h : IMPOUNDED_HAULERS)
            yard.bay(Math.round(h[0] - 1.5f), Math.round(h[1] - 3), Math.round(h[0] + 1.5f), Math.round(h[1] + 3));
        // The bonded hall's floor holds the impounded loads in two rows with aisles between: crated
        // supplies, and the heavy machinery Wayline was hauling when the junction was taken. The hall's
        // own fitting varies by seed, so a load whose place it has taken is left out.
        Object[][] goods = {
                {105, 71, "industrial-turbine-set"},
                {115, 71, "industrial-turbine-set"},
                {114, 75, "industrial-turbine-set"},
                {111, 75, "industrial-reactor-housing"},
                {120, 75, "industrial-reactor-housing"},
                {105, 75, "industrial-crate-stack"}, {106, 75, "industrial-crate-stack"}, {105, 76, "industrial-crate-stack"}, {106, 76, "industrial-crate-stack"},
                {111, 71, "industrial-crate-stack"}, {112, 71, "industrial-crate-stack"}, {113, 71, "industrial-crate-stack"}, {111, 72, "industrial-crate-stack"}, {112, 72, "industrial-crate-stack"}, {113, 72, "industrial-crate-stack"},
                {121, 71, "industrial-crate-stack"}, {122, 71, "industrial-crate-stack"}, {123, 71, "industrial-crate-stack"}, {121, 72, "industrial-crate-stack"}, {122, 72, "industrial-crate-stack"}, {123, 72, "industrial-crate-stack"},
                {107, 69, "stores-rack-run"},
                {111, 69, "stores-rack-run"},
                {119, 69, "stores-rack-run"},
                {108, 73, "stores-cargo-loader"}};
        PointOfInterest warehouse = c.pois.get(5);
        for (Object[] g : goods) yard.stock((Integer) g[0], (Integer) g[1], (String) g[2], 0,
                warehouse.interiorAnchorX, warehouse.interiorAnchorY);
        // Machinery waiting outside the loading doors, two loads already on flatbeds. The doors are seeded
        // too, so a load that would block one is left out.
        Object[][] staged = {{92, 89, "parked-flatbed-truck"}, {103, 89, "industrial-turbine-set"},
                {109, 89, "industrial-reactor-housing"}, {118, 89, "industrial-pipe-bundle"}, {123, 89, "parked-flatbed-truck"}};
        for (Object[] g : staged) yard.stock((Integer) g[0], (Integer) g[1], (String) g[2], 0,
                warehouse.interiorAnchorX, warehouse.interiorAnchorY);
        // Apron service annex: generator and spares stand on their own made ground.
        yard.gravel(44, 84, 54, 91);
        yard.prop(46, 87, "industrial-turbine-set", 0);
        yard.prop(52, 87, "industrial-cable-reel", 0);
        yard.prop(46, 85, "industrial-drum-cluster", 0);
        yard.prop(48, 84, "parked-tanker-truck", 2);
    }
    private static void shell(GenContext c, int l, int t, int r, int b, Frontage facing) {
        OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.DEPOT,
                new BlockLeaf(l, t, r, b, false), c, facing);
    }
    private static void pave(GenContext c, int l, int t, int r, int b, GroundKind kind) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) {
            c.grid.setWalkableFloor(x, y); c.topology.setGroundKind(x, y, kind);
        }
    }
    private static void trenches(GenContext c) {
        for (int[] t : TRENCHES) {
            List<int[]> line = new ArrayList<>();
            for (int y = t[1]; y <= t[2]; y++)
                if (!c.topology.isFixture(t[0], y)) line.add(new int[]{t[0], y});
            PreparedGround.trenchLine(c, line);
        }
    }
    private static void perimeter(GenContext c) {
        // The maintenance lane is screened from the freight apron. Its western
        // gate lets a finite reserve reach the rear of the weigh station.
        for (int x = 64; x <= 128; x++) {
            c.grid.setWalkable(x, 28, false);
            c.topology.setWall(x, 28, true);
        }
    }
    private static void cargo(GenContext c) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        for (int x = 97; x < 160; x += 8) {
            var prop = tiles.doodad("doodad.industrial-crate-stack");
            if (prop != null) c.doodads.add(new Doodad(x, 27, prop));
        }
    }
}
