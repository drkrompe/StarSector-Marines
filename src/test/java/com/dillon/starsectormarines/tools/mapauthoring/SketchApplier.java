package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Rise;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Road;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a sketch onto a freshly generated map, the way the generator lays its own: roads first (they are laid before
 * structures), then the drops cut as {@code OutdoorLandscapeStage} cuts a chasm (void, unwalkable, seen and shot
 * across), their crossings decked in timber or left as land, and the rises stood up as a crevasse town's walls are
 * (void, unwalkable, blocking sight). Then the skin repaints it. The map must be fresh: this writes into it.
 */
public final class SketchApplier {

    /** The surface a bridge is decked with: the generator's footbridge timber. */
    static final String DECK_SURFACE = "colony.timber";

    /** A map with the sketch laid on it, the cells it claimed, and what could not be laid as drawn. */
    public record Applied(MapResult map, SketchRaster.Cut cut, RoadRasterizer.Raster roads, List<String> problems,
                          BiomeSkin skin) { }

    private SketchApplier() { }

    public static Applied apply(MapResult map, MapSketch sketch, SurfacePalette builtAs, BiomeSkin skin) {
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        int w = grid.getWidth(), h = grid.getHeight();
        List<String> problems = new ArrayList<>();
        RoadRasterizer.Raster roads = null;
        if (!sketch.roads().isEmpty()) {
            try {
                RoadPlan plan = roadPlan(w, h, sketch.roads());
                roads = RoadRasterizer.rasterize(plan);
                RoadRasterizer.stamp(plan, roads, grid, topology);
            } catch (IllegalArgumentException | IllegalStateException e) {
                problems.add("Roads not laid: " + e.getMessage());
                roads = null;
            }
        }
        SketchRaster.Cut cut = SketchRaster.cut(w, h, sketch);
        int timber = topology.surfaceIndex(DECK_SURFACE);
        int buildingCells = 0, firstBuilding = -1, roadCells = 0, firstRoad = -1;
        for (int i = 0; i < w * h; i++) {
            int x = i % w, y = i / w;
            if (cut.drop()[i] || cut.rise()[i]) {
                if (topology.isWall(x, y) || topology.getGroundKind(x, y) == GroundKind.INDOOR) {
                    if (buildingCells++ == 0) firstBuilding = i;
                } else if (topology.getGroundKind(x, y) == GroundKind.STREET) {
                    if (roadCells++ == 0) firstRoad = i;
                }
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, cut.drop()[i]);
                grid.setTag(x, y, NavigationGrid.CellTag.NO_EDGE_COVER, true);
                grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
                topology.setGroundKind(x, y, GroundKind.VOID);
                topology.setSurface(x, y, 0);
                topology.setNatureOverlayIndex(x, y, -1);
            } else if (cut.bridged(i) && !SketchRaster.landBridge(cut, i) && grid.isWalkable(x, y)) {
                topology.setNatureOverlayIndex(x, y, -1);
                topology.setGroundKind(x, y, GroundKind.DIRT);
                topology.setSurface(x, y, timber);
            }
        }
        if (buildingCells > 0) {
            problems.add(buildingCells + " cells of a drop or rise cut through a building, the first at "
                    + firstBuilding % w + "," + firstBuilding / w + ".");
        }
        if (roadCells > 0) {
            problems.add("A drop or rise cuts a road with no crossing there (" + roadCells + " cells, the first at "
                    + firstRoad % w + "," + firstRoad / w + "): add a crossing where the road meets it.");
        }
        if (!sketch.drops().isEmpty() && topology.voidBackdrop() != null) {
            problems.add("This map draws a void backdrop; its drops cannot be natural ones.");
        } else if (!sketch.drops().isEmpty()) {
            topology.setNaturalVoid(dropLook(sketch.drops(), skin.drop()));
        } else if (topology.naturalVoid() != null && topology.naturalVoid().style() != NaturalVoid.Style.OPEN_PIT) {
            // The map's own drop, restyled for the world it is skinned as.
            NaturalVoid own = topology.naturalVoid();
            NaturalVoidZoning.Drop row = skin.drop();
            topology.setNaturalVoid(new NaturalVoid(row.style(), own.steps(), own.eyeMeters(), row.wallTexture(),
                    row.wallTileMeters(), own.seed(), own.belts(), own.marks(), own.source()));
        }
        if (!sketch.rises().isEmpty()) topology.setNaturalRise(riseLook(sketch.rises(), skin));
        skin.repaint(topology, builtAs);
        return new Applied(map, cut, roads, problems, skin);
    }

    /** The map's drop: the skin's style and wall, its ledges as many as the first drop asks, its depth marked at every point. */
    static NaturalVoid dropLook(List<Drop> drops, NaturalVoidZoning.Drop row) {
        Drop first = drops.get(0);
        float floor = 0f;
        List<NaturalVoid.DepthMark> marks = new ArrayList<>();
        for (Drop d : drops) for (int k = 0; k < d.path().size(); k++) {
            Pt p = d.path().get(k);
            float depth = d.depths().get(k);
            floor = Math.max(floor, depth);
            marks.add(new NaturalVoid.DepthMark(p.x(), p.y(), depth));
        }
        float inset = row.steps().get(row.steps().size() - 1).insetCells();
        List<NaturalVoid.Step> steps = new ArrayList<>();
        for (int k = 1; k <= first.terraces(); k++) {
            steps.add(new NaturalVoid.Step(floor * k / first.terraces(), inset * k / first.terraces()));
        }
        return new NaturalVoid(row.style(), steps, NaturalVoidZoning.EYE_METERS, row.wallTexture(), row.wallTileMeters(),
                first.id().hashCode(), List.of(), marks);
    }

    /** The map's rise: the skin's style in its drop's wall image, banked in the first rise's terraces, its height marked at every point. */
    static NaturalRise riseLook(List<Rise> rises, BiomeSkin skin) {
        Rise first = rises.get(0);
        float top = 0f;
        List<NaturalRise.HeightMark> marks = new ArrayList<>();
        for (Rise r : rises) for (int k = 0; k < r.path().size(); k++) {
            Pt p = r.path().get(k);
            top = Math.max(top, r.heights().get(k));
            marks.add(new NaturalRise.HeightMark(p.x(), p.y(), r.heights().get(k)));
        }
        float bank = Math.max(1f, Math.min(NaturalRise.BANK_CELLS, first.width() / 2f));
        NaturalVoidZoning.Drop wall = skin.riseStyle() == NaturalRise.Style.ICE_WALL ? NaturalVoidZoning.CREVASSE : skin.drop();
        return new NaturalRise(skin.riseStyle(), NaturalRise.bank(top, bank, first.terraces()), NaturalVoidZoning.EYE_METERS,
                wall.wallTexture(), wall.wallTileMeters(), first.id().hashCode(), marks);
    }

    /** The sketch's roads as a road plan: anchors are cell indices there, world cells here. */
    static RoadPlan roadPlan(int w, int h, List<Road> roads) {
        List<RoadPlan.Route> routes = new ArrayList<>();
        for (Road r : roads) {
            List<RoadPlan.Point> points = new ArrayList<>();
            for (Pt p : r.path()) {
                RoadPlan.Point q = new RoadPlan.Point(Math.max(0, Math.min(w - 1, p.x() - .5)), Math.max(0, Math.min(h - 1, p.y() - .5)));
                if (points.isEmpty() || !points.get(points.size() - 1).equals(q)) points.add(q);
            }
            routes.add(new RoadPlan.Route(r.id(), points, r.width(), 1, .35, r.id().hashCode(), r.material()));
        }
        return new RoadPlan(w, h, routes);
    }
}
