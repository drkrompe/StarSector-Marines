package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraphBuilder;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.Arrays;
import java.util.List;

/** Shared fresh-map ground contract for a whole installation, reserved before any structures stand. */
public final class CompoundGround {
    private CompoundGround() {}

    public static void reserve(GenContext c, String name, CompoundLayout.Placement site,
                               Fortification fortification, MapPlacement attackerFrom) {
        if (c.get(BspKeys.PRECINCT_CLAIM) != null || !c.doodads.isEmpty() || !c.pois.isEmpty()
                || !c.landingPads.isEmpty() || !c.tactical.isEmpty())
            throw new IllegalArgumentException("Compound ground must be reserved before other places");
        for (int[] local : site.layout().footprint().filled()) {
            int[] at = site.cell(local[0], local[1]);
            if (!c.grid.inBounds(at[0], at[1])) throw new IllegalArgumentException("Compound footprint leaves map");
        }
        var program = new FortressProgram(site.layout().clusters().stream()
                .flatMap(group -> group.buildings().stream())
                .map(b -> new FortressBuilding(b.purpose(), b.shape(), Ward.YARD, 1)).toList(), 0);
        int[] centre = site.cell(site.layout().footprint().width()/2, site.layout().footprint().height()/2);
        var precinct = Precinct.garrison(name, centre[0], centre[1],
                GrownTrunkPlan.Profile.of(0), program, fortification);
        c.put(BspKeys.PRECINCTS, new PrecinctPlan(List.of(precinct), attackerFrom));
        var claim = new int[c.width][c.height];
        var wild = SurfaceField.of(c);
        for (int x = 0; x < c.width; x++) {
            Arrays.fill(claim[x], GrownTrunkPlan.UNOWNED);
            for (int y = 0; y < c.height; y++) {
                c.grid.setWalkableFloor(x, y);
                c.topology.setGroundKind(x, y, wild.at(x, y).inlandGround());
                if (site.contains(x, y)) claim[x][y] = 0;
            }
        }
        for (var yard : site.layout().yards()) for (int dx = 0; dx < yard.width(); dx++)
            for (int dy = 0; dy < yard.height(); dy++) {
                int[] at = site.cell(yard.x() + dx, yard.y() + dy);
                c.topology.setGroundKind(at[0], at[1], GroundKind.COURTYARD);
                c.topology.setSurface(at[0], at[1], c.topology.surfaceIndex(RoadPlan.Material.CONCRETE.block(1)));
            }
        c.put(BspKeys.PRECINCT_CLAIM, claim);
    }

    /** The host supplies external approaches alongside the template's local gate and service roads. */
    public static void roads(GenContext c, List<RoadPlan.Route> routes) {
        var plan = new RoadPlan(c.width, c.height, routes);
        var raster = RoadRasterizer.rasterize(plan);
        RoadRasterizer.stamp(plan, raster, c.grid, c.topology);
        var owner = new int[c.width][c.height];
        var reservation = new boolean[c.width][c.height];
        for (int x = 0; x < c.width; x++) for (int y = 0; y < c.height; y++) {
            owner[x][y] = raster.road(x, y) ? 0 : GrownTrunkPlan.UNOWNED;
            reservation[x][y] = raster.shoulder()[y*c.width+x];
        }
        c.put(BspKeys.PRECINCT_ROAD, owner);
        c.put(BspKeys.ROAD_RESERVATION, reservation);
        c.put(BspKeys.ROAD_CELLS, reservation);
        c.put(BspKeys.ROAD_GRAPH, RoadGraphBuilder.build(reservation));
    }
}
