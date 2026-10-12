package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.DoorwayClearanceStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InteriorAnchorFitStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctDefenceStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalLinkStage;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraphBuilder;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Building;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Cluster;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Yard;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A working outpost: accommodation court, service yard and a projecting vehicle shed. */
public final class MaintenanceOutpost {
    private MaintenanceOutpost() {}

    /** The optional magazine changes capacity without moving the court, gates or vehicle lane. */
    public static CompoundLayout layout(boolean extraMagazine) {
        String[] rows = new String[58];
        Arrays.fill(rows, 0, 44, "#".repeat(68));
        Arrays.fill(rows, 44, 58, "#".repeat(44));
        var stores = new ArrayList<Building>();
        stores.add(building(RoomPurpose.VEHICLE_BAY, 3, 39, 31, 16,
                new Doorway(31, 6), new Doorway(31, 7), new Doorway(31, 8),
                new Doorway(1, -1), new Doorway(2, -1)));
        stores.add(building(RoomPurpose.PARTS_CAGE, 5, 17, 10, 8,
                new Doorway(4, 8), new Doorway(5, 8)));
        if (extraMagazine) stores.add(building(RoomPurpose.ARMORY, 21, 16, 12, 8,
                new Doorway(5, 8), new Doorway(6, 8)));
        return new CompoundLayout(RoomShape.of(rows), List.of(
                new Cluster("Maintenance yard", stores),
                new Cluster("Barracks court", List.of(
                        building(RoomPurpose.BARRACKS, 43, 3, 16, 6, new Doorway(7, 6), new Doorway(8, 6), new Doorway(-1, 2)),
                        building(RoomPurpose.BARRACKS, 43, 19, 16, 6, new Doorway(7, -1), new Doorway(8, -1), new Doorway(-1, 2)),
                        building(RoomPurpose.MESS_HALL, 30, 4, 11, 8, new Doorway(11, 6), new Doorway(11, 7), new Doorway(5, 8)))),
                new Cluster("Utilities", List.of(
                        building(RoomPurpose.ENGINE_ROOM, 19, 4, 8, 8, new Doorway(3, 8), new Doorway(4, 8)))),
                new Cluster("Dispatch and gate", List.of(
                        building(RoomPurpose.CIVIC_OFFICE, 47, 35, 12, 6, new Doorway(5, -1), new Doorway(6, -1), new Doorway(-1, 3)),
                        building(RoomPurpose.KEEP_THRONE, 4, 4, 12, 6, new Doorway(5, 6), new Doorway(6, 6), new Doorway(-1, 3)),
                        building(RoomPurpose.CONTROL_ROOM, 62, 21, 3, 5, new Doorway(-1, 3)),
                        building(RoomPurpose.CONTROL_ROOM, 62, 33, 3, 5, new Doorway(-1, 2))))),
                List.of(new Yard("Working apron", 2, 26, 41, 11),
                        new Yard("Bay approach", 35, 37, 8, 19),
                        new Yard("Barracks court", 42, 10, 21, 8)),
                List.of(new Route("Gate to service yard", List.of(new Point(73, 29), new Point(39, 29),
                                new Point(25, 31), new Point(-6, 31)), 5, 1, .4, 0, Material.ASPHALT),
                        new Route("Vehicle shed approach", List.of(new Point(40, 29), new Point(40, 39),
                                new Point(39, 46), new Point(35, 46)), 5, 1, .4, 0, Material.ASPHALT,
                                RoadPlan.EndCap.BUTT)));
    }

    private static Building building(RoomPurpose purpose, int x, int y, int w, int h, Doorway... doors) {
        return new Building(purpose, RoomShape.rectangle(w, h), x, y, List.of(doors));
    }

    public static MapResult generate(int width, int height, long seed, TargetProfile profile,
                                     Fortification fortification) {
        if (width < 136 || height < 76) throw new IllegalArgumentException("Maintenance outpost needs approach space");
        var placement = layout(true).at(7, (height - 58) / 2, 0);
        var c = new GenContext(new NavigationGrid(width, height), new CellTopology(width, height),
                new Random(seed), width, height, seed);
        c.put(BspKeys.MARKET_PROFILE, profile);
        int marineX = width - 15, marineY = height / 2;
        int[] gate = placement.cell(66, 29);
        new GenRecipe("Maintenance outpost", List.of(
                ctx -> grounds(ctx, placement, fortification),
                ctx -> PrecinctWardStage.finishAuthored(ctx,
                        FortressInterior.author(ctx, placement.buildings()), 0),
                new PrecinctDefenceStage(), new TacticalLinkStage(), new FinalizeStage(),
                new DoorwayClearanceStage(),
                new OutdoorLandscapeStage(marineX, marineY, gate[0], gate[1]),
                new InteriorAnchorFitStage())).run(c);
        var result = new MapResult(c.grid, c.topology, marineX, marineY, gate[0], gate[1],
                c.pois, c.doodads, c.get(BspKeys.TACTICAL_MAP), c.get(BspKeys.BUILDINGS),
                c.defensePosts, c.get(BspKeys.ROAD_GRAPH), c.landingPads, List.of(), null,
                c.gantries, c.fixtureTasks);
        for (var poi : result.pointsOfInterest)
            if (GridPathfinder.findPath(c.grid, marineX, marineY, poi.interiorAnchorX, poi.interiorAnchorY).length == 0)
                throw new IllegalStateException("Outpost facility is unreachable: " + poi.kind);
        for (var pad : c.landingPads) if (!pad.isClear(c.grid, c.topology))
            throw new IllegalStateException("Outpost pickup berth obstructed");
        return result;
    }

    private static void grounds(GenContext c, CompoundLayout.Placement site, Fortification fortification) {
        var program = new FortressProgram(site.layout().clusters().stream()
                .flatMap(group -> group.buildings().stream())
                .map(b -> new FortressBuilding(b.purpose(), b.shape(), Ward.YARD, 1)).toList(), 0);
        var precinct = Precinct.garrison("Maintenance outpost", site.x() + 34, site.y() + 29,
                GrownTrunkPlan.Profile.of(0), program, fortification);
        c.put(BspKeys.PRECINCTS, new PrecinctPlan(List.of(precinct), MapPlacement.EAST));
        var claim = new int[c.width][c.height];
        var road = new int[c.width][c.height];
        for (int x = 0; x < c.width; x++) {
            Arrays.fill(claim[x], GrownTrunkPlan.UNOWNED);
            Arrays.fill(road[x], GrownTrunkPlan.UNOWNED);
            for (int y = 0; y < c.height; y++) {
                c.grid.setWalkableFloor(x, y);
                c.topology.setGroundKind(x, y, c.get(BspKeys.MARKET_PROFILE).surface().inlandGround());
                if (site.contains(x, y)) claim[x][y] = 0;
            }
        }
        for (var yard : site.layout().yards()) for (int dx = 0; dx < yard.width(); dx++)
            for (int dy = 0; dy < yard.height(); dy++) {
                int[] at = site.cell(yard.x() + dx, yard.y() + dy);
                c.topology.setGroundKind(at[0], at[1], GroundKind.COURTYARD);
                c.topology.setSurface(at[0], at[1], c.topology.surfaceIndex(Material.CONCRETE.block(1)));
            }
        var localRoads = new ArrayList<>(site.roads(c.seed));
        localRoads.add(new Route("Outpost access", List.of(point(site, 73, 29),
                new Point(c.width - 1, site.y() + 29)), 5, 1, .4, c.seed, Material.ASPHALT));
        var routes = new RoadPlan(c.width, c.height, localRoads);
        var raster = RoadRasterizer.rasterize(routes);
        RoadRasterizer.stamp(routes, raster, c.grid, c.topology);
        var reservation = new boolean[c.width][c.height];
        for (int x = 0; x < c.width; x++) for (int y = 0; y < c.height; y++) {
            if (raster.road(x, y)) road[x][y] = 0;
            reservation[x][y] = raster.shoulder()[y * c.width + x];
        }
        // Four clear field berths support both the ordinary pair and four-boat launches.
        for (int y : new int[]{18, 32, 48, 62})
            c.landingPads.add(new LandingPad(c.width - 15, y, 5, 3, LandingPad.Approach.EAST,
                    LandingPad.Purpose.FALLBACK));
        c.put(BspKeys.PRECINCT_CLAIM, claim);
        c.put(BspKeys.PRECINCT_ROAD, road);
        c.put(BspKeys.ROAD_RESERVATION, reservation);
        c.put(BspKeys.ROAD_CELLS, reservation);
        c.put(BspKeys.ROAD_GRAPH, RoadGraphBuilder.build(reservation));
    }

    private static Point point(CompoundLayout.Placement site, int x, int y) {
        int[] cell = site.cell(x, y);
        return new Point(cell[0], cell[1]);
    }
}
