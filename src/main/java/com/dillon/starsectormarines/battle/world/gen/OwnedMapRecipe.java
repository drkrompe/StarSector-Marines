package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.BiomeSkin.Adaptation;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Box;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.HabitatStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.road.AuthoredRoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds an {@link OwnedMap} under a {@link BiomeSkin} ({@code owned-maps.md}, The build): the map's form laid as the
 * document says, then dressed as the skin says. The stages run in one fixed order whatever the skin, and the skin
 * picks only what each stage lays its cells in, so the same document lays the same walls, roads, pads, sites, drops
 * and rises on every world. The one thing a skin adds is a settlement's {@link Adaptation}: a sealed perimeter's wall
 * stands on its lot's edge, never inside, and every route is proved again over it.
 *
 * <p>A mission reads the result by name: its sites ({@link Built#sites}, in the document's order) and its buildings'
 * points of interest ({@link Built#buildings}). It may lay its own mission ground on {@link Built#context} before
 * {@link #result} seals the map.
 */
public final class OwnedMapRecipe {

    /** The floor marking laid across a sealed perimeter's locks. */
    static final String LOCK_MARKING = "doodad.fl-striped-yellow";
    /** How many cells either side of a gate a perimeter leaves open as its lock. */
    static final int LOCK_HALF_WIDTH = 2;
    /** The surface a drop's crossings are decked with, as on generated maps. */
    static final String DECK_SURFACE = "colony.timber";

    private OwnedMapRecipe() { }

    /**
     * A built owned map before it is sealed into a {@link MapResult}.
     *
     * @param context     the generation context, for the mission's own ground
     * @param sites       the map's sites as tactical nodes, in the document's order
     * @param buildings   each named building's point of interest
     * @param adaptations how each settlement was adapted, by name
     */
    public record Built(OwnedMap map, BiomeSkin skin, GenContext context, List<TacticalNode> sites,
                        Map<String, PointOfInterest> buildings, Map<String, Adaptation> adaptations) { }

    /** Builds {@code map} for the world {@code profile} describes, its nature drawn from {@code seed}. */
    public static Built build(OwnedMap map, TargetProfile profile, long seed) {
        TargetProfile world = profile == null ? TargetProfile.NEUTRAL : profile;
        BiomeSkin skin = BiomeSkin.of(world);
        int w = map.width(), h = map.height();
        GenContext c = new GenContext(new NavigationGrid(w, h), new CellTopology(w, h), new Random(seed), w, h, seed);
        c.put(BspKeys.MARKET_PROFILE, world);
        Map<String, PointOfInterest> buildings = new LinkedHashMap<>();
        Map<String, Adaptation> adaptations = new LinkedHashMap<>();
        for (OwnedMap.Settlement s : map.settlements()) adaptations.put(s.name(), skin.adaptation(map, s));
        new GenRecipe(map.id(), List.of(
                g -> grounds(g, map, skin),
                g -> buildings(g, map, buildings),
                g -> walls(g, map),
                g -> props(g, map),
                g -> RoadRasterizer.clearReplacedSurfaces(g.topology, g.width, g.height),
                OwnedMapRecipe::yardSurfaces,
                g -> works(g, map),
                g -> terrain(g, map, skin),
                g -> adapt(g, map, skin, adaptations),
                new FinalizeStage(),
                // After finalize, as a generated map's drop is: a drop or rise is natural ground, never a wall.
                g -> drops(g, map, skin),
                g -> rises(g, map, skin),
                new OutdoorLandscapeStage(anchors(map))
        )).run(c);
        List<TacticalNode> sites = new ArrayList<>();
        for (OwnedMap.Site site : map.sites()) sites.add(node(site, buildings));
        return new Built(map, skin, c, sites, Map.copyOf(buildings), Map.copyOf(adaptations));
    }

    /**
     * The finished map: the sites and every other building as held places, the road graph, and every route proved
     * from the origin (each pad, site, building and listed route cell), so a skin that strands anything is refused.
     */
    public static MapResult result(Built built) {
        OwnedMap map = built.map();
        GenContext c = built.context();
        List<TacticalNode> tactical = new ArrayList<>(built.sites());
        tactical.addAll(HeldPlaces.fromBuildingsNotIn(c.pois, built.sites(), poi -> Faction.DEFENDER));
        RoadGraph roads = map.roads() == null || map.roadGraphFrom() == null ? new RoadGraph(List.of(), List.of())
                : AuthoredRoadGraph.graph(RoadCatalog.plan(map.roads()), map.roadGraphFrom());
        MapResult result = new MapResult(c.grid, c.topology, map.origin().x(), map.origin().y(),
                map.objective().x(), map.objective().y(), c.pois, c.doodads, new TacticalMap(tactical),
                c.get(BspKeys.BUILDINGS), List.of(), roads, c.landingPads);
        for (LandingPad pad : result.landingPads) {
            if (!pad.isClear(result.grid, result.topology)) throw new IllegalStateException(map.id() + ": berth obstructed at " + pad.centerX + "," + pad.centerY);
            requireRoute(map, result, pad.centerX, pad.centerY);
        }
        for (TacticalNode site : built.sites()) requireRoute(map, result, site.anchorX, site.anchorY);
        for (PointOfInterest p : c.pois) requireRoute(map, result, p.interiorAnchorX, p.interiorAnchorY);
        for (Cell cell : map.routes()) requireRoute(map, result, cell.x(), cell.y());
        return result;
    }

    /** The landscape's anchors: the origin, the objective and every site with its own cell, which nature keeps off. */
    static int[] anchors(OwnedMap map) {
        List<Cell> cells = new ArrayList<>(List.of(map.origin(), map.objective()));
        for (OwnedMap.Site site : map.sites()) if (site.at() != null && !cells.contains(site.at())) cells.add(site.at());
        int[] out = new int[cells.size() * 2];
        for (int k = 0; k < cells.size(); k++) {
            out[2 * k] = cells.get(k).x();
            out[2 * k + 1] = cells.get(k).y();
        }
        return out;
    }

    private static void requireRoute(OwnedMap map, MapResult result, int x, int y) {
        if (Paths.isEmpty(GridPathfinder.findPath(result.grid, map.origin().x(), map.origin().y(), x, y)))
            throw new IllegalStateException(map.id() + ": route disconnected at " + x + "," + y);
    }

    private static TacticalNode node(OwnedMap.Site site, Map<String, PointOfInterest> buildings) {
        if (site.building() != null) {
            PointOfInterest p = buildings.get(site.building());
            if (p == null) throw new IllegalStateException("site " + site.name() + ": building " + site.building() + " did not stand");
            return new TacticalNode(site.kind(), p.interiorAnchorX, p.interiorAnchorY, p.left, p.top, p.right, p.bottom,
                    site.guard(), site.value(), site.garrison());
        }
        Box a = site.area();
        return new TacticalNode(site.kind(), site.at().x(), site.at().y(), a.x0(), a.y0(), a.x1(), a.y1(),
                site.guard(), site.value(), site.garrison());
    }

    // ---- form ----

    private static void grounds(GenContext c, OwnedMap map, BiomeSkin skin) {
        pave(c, 0, 0, c.width - 1, c.height - 1, skin.wild());
        for (OwnedMap.Ground g : map.grounds()) if (g.role() == OwnedMap.GroundRole.YARD) lay(c, g, GroundKind.COURTYARD);
        yardSurfaces(c);
        if (map.roads() != null) {
            RoadPlan roads = RoadCatalog.plan(map.roads());
            RoadRasterizer.stamp(roads, RoadRasterizer.rasterize(roads), c.grid, c.topology);
        }
        for (OwnedMap.Pad p : map.pads())
            c.landingPads.add(new LandingPad(p.at().x(), p.at().y(), p.halfWidth(), p.halfHeight(), p.approach(), p.purpose()));
        for (OwnedMap.Ground g : map.grounds()) if (g.role() == OwnedMap.GroundRole.MARKING) lay(c, g, GroundKind.STRIPED);
    }

    /** Ground over a box, or round-cornered when it has a radius: floor of {@code kind} either way. */
    private static void lay(GenContext c, OwnedMap.Ground g, GroundKind kind) {
        Box b = g.box();
        int r = g.radius();
        if (r <= 0) {
            pave(c, b.x0(), b.y0(), b.x1(), b.y1(), kind);
            return;
        }
        for (int y = Math.max(0, b.y0() - r); y <= Math.min(c.height - 1, b.y1() + r); y++) {
            for (int x = Math.max(0, b.x0() - r); x <= Math.min(c.width - 1, b.x1() + r); x++) {
                int cx = Math.max(b.x0(), Math.min(b.x1(), x)), cy = Math.max(b.y0(), Math.min(b.y1(), y));
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) c.topology.setGroundKind(x, y, kind);
            }
        }
    }

    /** A yard's concrete, aged in patches, on every open courtyard cell. */
    private static void yardSurfaces(GenContext c) {
        int[] concrete = new int[3];
        for (int age = 0; age < 3; age++) concrete[age] = c.topology.surfaceIndex(Material.CONCRETE.block(age));
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++)
            if (c.topology.getGroundKind(x, y) == GroundKind.COURTYARD && !c.topology.isWall(x, y))
                c.topology.setSurface(x, y, concrete[RoadRasterizer.age(.55, 5219, x, y)]);
    }

    private static void buildings(GenContext c, OwnedMap map, Map<String, PointOfInterest> named) {
        for (OwnedMap.Settlement s : map.settlements()) {
            for (OwnedMap.Building b : s.buildings()) {
                int before = c.pois.size();
                switch (b.form()) {
                    case STAMP -> BuildingStamp.valueOf(b.plan()).place(c, b.at().x(), b.at().y(), b.turns());
                    case FACILITY -> OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.valueOf(b.plan()),
                            new BlockLeaf(b.box().x0(), b.box().y0(), b.box().x1(), b.box().y1(), false), c,
                            b.frontage(), b.opposed());
                }
                if (c.pois.size() > before) named.put(b.name(), c.pois.get(before));
            }
        }
    }

    private static void walls(GenContext c, OwnedMap map) {
        for (OwnedMap.Wall wall : map.walls()) {
            int x0 = Math.min(wall.from().x(), wall.to().x()), x1 = Math.max(wall.from().x(), wall.to().x());
            int y0 = Math.min(wall.from().y(), wall.to().y()), y1 = Math.max(wall.from().y(), wall.to().y());
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                c.grid.setWalkable(x, y, false);
                c.topology.setWall(x, y, true);
            }
        }
    }

    private static void props(GenContext c, OwnedMap map) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return;
        for (OwnedMap.Prop p : map.props()) {
            DoodadDef def = tiles.doodad(p.id());
            if (def != null) c.doodads.add(new Doodad(p.at().x(), p.at().y(), def));
        }
    }

    private static void works(GenContext c, OwnedMap map) {
        StoryServiceYard yard = new StoryServiceYard(c);
        for (OwnedMap.Settlement s : map.settlements()) {
            for (OwnedMap.Work w : s.works()) {
                switch (w.op()) {
                    case GRAVEL -> yard.gravel(w.box().x0(), w.box().y0(), w.box().x1(), w.box().y1());
                    case FENCE -> yard.fence(w.box().x0(), w.box().y0(), w.box().x1(), w.box().y1());
                    case BAY -> yard.bay(w.box().x0(), w.box().y0(), w.box().x1(), w.box().y1());
                    case PROP -> yard.prop(w.at().x(), w.at().y(), w.id(), w.turns());
                    case CARGO -> yard.cargo(w.at().x(), w.at().y());
                    case TEMPLATE -> ServiceYardTemplate.valueOf(w.id()).place(c, w.at().x(), w.at().y(), w.turns());
                }
            }
        }
    }

    /** The map's inaccessible masses in the world's own finish; the pads' approaches are kept clear to the west. */
    private static void terrain(GenContext c, OwnedMap map, BiomeSkin skin) {
        if (map.terrain() == null) return;
        boolean[] reserved = new boolean[c.width * c.height];
        for (LandingPad pad : c.landingPads)
            for (int y = Math.max(0, pad.bottom() - 2); y <= Math.min(c.height - 1, pad.top() + 2); y++)
                for (int x = 0; x <= pad.right() + 2; x++) reserved[y * c.width + x] = true;
        TerrainBrush.stamp(TerrainCatalog.plan(map.terrain(), skin.palette()), c.grid, c.topology, reserved);
    }

    // ---- drops and rises ----

    /**
     * The cells a drop cuts: every cell whose centre lies less than half its width from its path (path points are
     * cell centres, so an odd width is cut exactly and an even one a cell narrower). A deck marks the cut's
     * cells whose nearest point on the path lies within half a crossing's width, along the path, of a crossing.
     */
    public record Cut(boolean[] drop, boolean[] decks) { }

    /** The cells {@code drop} cuts on a {@code w} by {@code h} map, row-major. */
    public static Cut cut(OwnedMap.Drop drop, int w, int h) {
        boolean[] cells = new boolean[w * h], decks = new boolean[w * h];
        List<Cell> path = drop.path();
        float half = drop.width() / 2f;
        float[] along = new float[path.size()];
        for (int k = 1; k < path.size(); k++) along[k] = along[k - 1] + (float) Math.hypot(
                path.get(k).x() - path.get(k - 1).x(), path.get(k).y() - path.get(k - 1).y());
        float[] crossings = new float[drop.crossings().size()];
        for (int k = 0; k < crossings.length; k++) {
            Cell x = drop.crossings().get(k);
            crossings[k] = nearest(path, along, x.x() + .5f, x.y() + .5f)[1];
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] n = nearest(path, along, x + .5f, y + .5f);
                if (n[0] >= half) continue;
                int i = y * w + x;
                cells[i] = true;
                for (float s : crossings) if (Math.abs(n[1] - s) <= drop.crossingWidth() / 2f) decks[i] = true;
            }
        }
        return new Cut(cells, decks);
    }

    /** The distance from a point to the path and how far along the path its nearest point lies. */
    private static float[] nearest(List<Cell> path, float[] along, float px, float py) {
        float best = Float.MAX_VALUE, at = 0f;
        for (int k = 1; k < path.size(); k++) {
            float ax = path.get(k - 1).x() + .5f, ay = path.get(k - 1).y() + .5f;
            float bx = path.get(k).x() + .5f, by = path.get(k).y() + .5f;
            float dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            float t = len2 == 0f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / len2));
            float qx = ax + dx * t, qy = ay + dy * t;
            float d = (float) Math.hypot(px - qx, py - qy);
            if (d < best) {
                best = d;
                at = along[k - 1] + t * (along[k] - along[k - 1]);
            }
        }
        return new float[]{best, at};
    }

    /**
     * Cuts the map's drops ({@code owned-maps.md}, Drops). A drop yields to nothing the map built: where it meets a
     * road the road stands on as a bridge, and a building, wall or pad in its way refuses the map. Its crossings are
     * decked. The look is the skin's for the first drop's role, deepening and shallowing along the cut as the
     * document's depths say, or spread over the skin's band where it says none.
     */
    private static void drops(GenContext c, OwnedMap map, BiomeSkin skin) {
        if (map.drops().isEmpty()) return;
        int w = c.width, h = c.height;
        boolean[] all = new boolean[w * h];
        List<NaturalVoid.DepthMark> marks = new ArrayList<>();
        int timber = c.topology.surfaceIndex(DECK_SURFACE);
        for (OwnedMap.Drop drop : map.drops()) {
            Cut cut = cut(drop, w, h);
            for (int i = 0; i < all.length; i++) {
                if (!cut.drop()[i]) continue;
                int x = i % w, y = i / w;
                if (c.topology.getGroundKind(x, y) == GroundKind.STREET) continue;
                if (c.topology.isWall(x, y) || c.topology.getBuildingId(x, y) != 0 || c.topology.isFixture(x, y) || onPad(c, x, y))
                    throw new IllegalStateException(map.id() + ": drop " + drop.name() + " cuts built ground at " + x + "," + y);
                if (cut.decks()[i]) {
                    c.topology.setNatureOverlayIndex(x, y, -1);
                    c.topology.setGroundKind(x, y, GroundKind.DIRT);
                    c.topology.setSurface(x, y, timber);
                    continue;
                }
                all[i] = true;
                c.grid.setWalkable(x, y, false);
                c.grid.setTag(x, y, NavigationGrid.CellTag.SEE_THROUGH, true);
                c.grid.setTag(x, y, NavigationGrid.CellTag.NO_EDGE_COVER, true);
                c.grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
                c.topology.setGroundKind(x, y, GroundKind.VOID);
                c.topology.setSurface(x, y, 0);
                c.topology.setNatureOverlayIndex(x, y, -1);
            }
            NaturalVoidZoning.Drop style = skin.drop(drop.role());
            if (style.varies()) {
                if (drop.depths().isEmpty()) {
                    marks.addAll(NaturalVoidZoning.marks(cut.drop(), w, h, style.shallowest(), style.deepest(),
                            seed(map, drop.name())));
                } else {
                    for (OwnedMap.Depth d : drop.depths())
                        marks.add(new NaturalVoid.DepthMark(d.at().x() + .5f, d.at().y() + .5f, skin.floorMeters(drop.role(), d.share())));
                }
            }
        }
        NaturalVoidZoning.Drop look = skin.drop(map.drops().get(0).role());
        c.topology.setNaturalVoid(look.look(seed(map, "look")).withMarks(marks));
    }

    private static boolean onPad(GenContext c, int x, int y) {
        for (LandingPad pad : c.landingPads)
            if (x >= pad.left() && x <= pad.right() && y >= pad.bottom() && y <= pad.top()) return true;
        return false;
    }

    /** Raises the map's rises: unwalkable natural solid that blocks sight, banked in the skin's rise style. */
    private static void rises(GenContext c, OwnedMap map, BiomeSkin skin) {
        if (map.rises().isEmpty()) return;
        float top = 0f;
        OwnedMap.DropRole role = map.drops().isEmpty() ? OwnedMap.DropRole.RIFT : map.drops().get(0).role();
        for (OwnedMap.Rise rise : map.rises()) {
            Box b = rise.box();
            top = Math.max(top, skin.floorMeters(role, 1f) * rise.share());
            for (int y = Math.max(0, b.y0()); y <= Math.min(c.height - 1, b.y1()); y++) {
                for (int x = Math.max(0, b.x0()); x <= Math.min(c.width - 1, b.x1()); x++) {
                    c.grid.setWalkable(x, y, false);
                    c.grid.setTag(x, y, NavigationGrid.CellTag.NO_EDGE_COVER, true);
                    c.grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
                    c.topology.setGroundKind(x, y, GroundKind.VOID);
                    c.topology.setSurface(x, y, 0);
                    c.topology.setNatureOverlayIndex(x, y, -1);
                }
            }
        }
        NaturalVoidZoning.Drop wall = skin.riseStyle() == NaturalRise.Style.ICE_WALL ? NaturalVoidZoning.CREVASSE : skin.drop(role);
        c.topology.setNaturalRise(new NaturalRise(skin.riseStyle(), NaturalRise.bank(Math.max(1f, top)),
                NaturalVoidZoning.EYE_METERS, wall.wallTexture(), wall.wallTileMeters(), seed(map, "rise")));
    }

    /** A stable seed for a named part of a map: the same on every world and every run. */
    static int seed(OwnedMap map, String part) {
        return (map.id() + "/" + part).hashCode();
    }

    // ---- adaptation ----

    /**
     * Builds each settlement for its world: the habitat's walls and thresholds everywhere ({@link HabitatStage}), and
     * round a sealed settlement a perimeter on its lot's edge, opened only at its locks. Every lot is then held
     * ground, which the landscape's nature keeps off.
     */
    private static void adapt(GenContext c, OwnedMap map, BiomeSkin skin, Map<String, Adaptation> adaptations) {
        Habitat habitat = skin.habitat();
        // The drops are cut after finalize; a perimeter yields to the ground they will take.
        boolean[] dropped = new boolean[c.width * c.height];
        for (OwnedMap.Drop drop : map.drops()) {
            boolean[] cut = cut(drop, c.width, c.height).drop();
            for (int i = 0; i < cut.length; i++) dropped[i] |= cut[i];
        }
        for (OwnedMap.Settlement s : map.settlements()) {
            if (adaptations.get(s.name()) == Adaptation.SEALED_PERIMETER) perimeter(c, s, habitat, dropped);
            // A lot is held ground: the landscape grows nothing on it, so every world builds it on the same ground.
            Box lot = s.lot();
            for (int y = Math.max(0, lot.y0()); y <= Math.min(c.height - 1, lot.y1()); y++)
                for (int x = Math.max(0, lot.x0()); x <= Math.min(c.width - 1, lot.x1()); x++) c.markMadeGround(x, y);
        }
        HabitatStage.apply(c, habitat);
    }

    /**
     * A sealed settlement's perimeter: a wall in the habitat's shell round its lot's edge. It yields where the ground
     * is not open (a road, a building, a pad and the ground before it, a doorway's step, {@code dropped}: the cells a
     * drop will take), and leaves a lock
     * {@link #LOCK_HALF_WIDTH} cells either side of every gate, its floor marked. A side with nothing to yield to and
     * no gate gets a lock at its middle, so a perimeter never seals what it encloses.
     */
    static void perimeter(GenContext c, OwnedMap.Settlement s, Habitat habitat, boolean[] dropped) {
        String shell = habitat == Habitat.SHIELDED ? Habitat.SHIELD_WALL : "colony.ceramic-wall";
        int surface = c.topology.surfaceIndex(shell);
        Box lot = s.lot();
        int[][] sides = {{lot.x0(), lot.y0(), lot.x1(), lot.y0()}, {lot.x1(), lot.y0(), lot.x1(), lot.y1()},
                {lot.x1(), lot.y1(), lot.x0(), lot.y1()}, {lot.x0(), lot.y1(), lot.x0(), lot.y0()}};
        List<Cell> locks = new ArrayList<>();
        for (int[] side : sides) {
            int dx = Integer.signum(side[2] - side[0]), dy = Integer.signum(side[3] - side[1]);
            int length = Math.abs(side[2] - side[0]) + Math.abs(side[3] - side[1]);
            boolean[] wall = new boolean[length];
            boolean opens = false;
            for (int k = 0; k < length; k++) {
                int x = side[0] + dx * k, y = side[1] + dy * k;
                if (nearGate(s, x, y)) {
                    locks.add(new Cell(x, y));
                    opens = true;
                } else if (walls(c, x, y) && (dropped == null || !dropped[y * c.width + x])) {
                    wall[k] = true;
                } else {
                    opens = true;
                }
            }
            if (!opens) {
                for (int k = length / 2 - LOCK_HALF_WIDTH; k <= length / 2 + LOCK_HALF_WIDTH; k++) {
                    wall[k] = false;
                    locks.add(new Cell(side[0] + dx * k, side[1] + dy * k));
                }
            }
            for (int k = 0; k < length; k++) {
                if (!wall[k]) continue;
                int x = side[0] + dx * k, y = side[1] + dy * k;
                c.grid.setWalkable(x, y, false);
                c.topology.setWall(x, y, true);
                c.topology.setSurface(x, y, surface);
                c.topology.setNatureOverlayIndex(x, y, -1);
            }
        }
        TileRegistry tiles = TileRegistry.installed();
        DoodadDef marking = tiles == null ? null : tiles.doodad(LOCK_MARKING);
        if (marking == null) return;
        for (Cell lock : locks) if (c.grid.isWalkable(lock.x(), lock.y())) c.doodads.add(new Doodad(lock.x(), lock.y(), marking, 0));
    }

    private static boolean nearGate(OwnedMap.Settlement s, int x, int y) {
        for (Cell g : s.gates()) if (Math.abs(g.x() - x) <= LOCK_HALF_WIDTH && Math.abs(g.y() - y) <= LOCK_HALF_WIDTH) return true;
        return false;
    }

    /** Whether a perimeter wall may stand on this cell: open, unbuilt ground clear of a road, a pad and a door. */
    private static boolean walls(GenContext c, int x, int y) {
        if (!c.grid.inBounds(x, y) || !c.grid.isWalkable(x, y) || c.topology.isWall(x, y) || c.topology.isFixture(x, y)
                || c.topology.getBuildingId(x, y) != 0 || c.topology.hasBuildingKindHint(x, y)) return false;
        GroundKind ground = c.topology.getGroundKind(x, y);
        if (ground == GroundKind.STREET || ground == GroundKind.STRIPED || ground == GroundKind.INDOOR
                || ground == GroundKind.VOID) return false;
        for (int yy = y - 2; yy <= y + 2; yy++) for (int xx = x - 2; xx <= x + 2; xx++)
            if (c.grid.inBounds(xx, yy) && c.grid.isDoorway(xx, yy)) return false;
        for (LandingPad pad : c.landingPads)
            if (x >= pad.left() - 2 && x <= pad.right() + 2 && y >= pad.bottom() - 2 && y <= pad.top() + 2) return false;
        return true;
    }

    private static void pave(GenContext c, int x0, int y0, int x1, int y1, GroundKind kind) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            c.grid.setWalkableFloor(x, y);
            c.topology.setGroundKind(x, y, kind);
        }
    }
}
