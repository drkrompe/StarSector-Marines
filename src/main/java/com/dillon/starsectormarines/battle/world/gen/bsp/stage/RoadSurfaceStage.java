package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.road.GeneratedRoads;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralGround;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Finish a generated city's carriageways with the spline pavement after
 * placement; ground semantics and clearance stay authoritative.
 *
 * <p>Two networks feed one sweep. A precinct map's kept service roads are
 * already asphalt and keep the treatment they had. The ordinary city street —
 * {@link GroundKind#STREET} with no surface of its own, the urban paving — gains
 * asphalt over its carriageway: every such cell except the kerb strip the
 * renderer draws wherever a wall or building is within one cell. The kerb, the
 * pedestrian frames, plazas wider than {@link #CITY_MAX_BREADTH}, lanes
 * narrower than a sweep, and the bulges and pills {@link GeneratedRoads#streets}
 * refuses keep the paving, which also shows through in the corners a junction
 * turn leaves uncovered.
 *
 * <p>A city street ends square, flush with the band it was found in, and has a
 * concrete sidewalk beside its asphalt: on the kerb, the boulevard's flank or a
 * yard's paving where the street has one beside it, and otherwise out of the
 * street's own outer cells while three cells of asphalt remain. The sidewalk is
 * <em>ground</em>, not pavement: it is the underlay of every city street cell
 * the asphalt comes within {@link #PADDING} cells of, so a cell
 * the asphalt only partly covers shows concrete beside it rather than the kerb
 * or paver tile it was, and the concrete meets the untouched paving on whole
 * cell edges. {@code -Dbattle.mapgen.cityRoadEnds=round} restores the round
 * brush end and {@code -Dbattle.mapgen.citySidewalks=false} the bare asphalt.
 * Each street carries a dashed centre line, broken at junctions;
 * {@code -Dbattle.mapgen.cityLaneMarkings=false} leaves it unpainted;
 * precinct roads keep round ends and no sidewalk either way.
 *
 * <p>This is a finish, not a repaving: no cell's ground kind, surface,
 * walkability or tag other than {@link Tag#ROAD_EDGE_BLEND} changes, and no
 * random number is drawn. {@code -Dbattle.mapgen.cityRoads=false} is the
 * control: the city keeps its paving and only precinct roads are swept.
 */
public final class RoadSurfaceStage implements GenStage {
    /**
     * Wider than any street the city lays (a primary trunk is seven) and narrower
     * than a yard or square. A band broader than this is open paving.
     */
    static final int CITY_MAX_BREADTH = 9;

    static boolean cityRoadsEnabled() {
        return Boolean.parseBoolean(System.getProperty("battle.mapgen.cityRoads", "true"));
    }

    static GeneratedRoads.StreetStyle cityStreetStyle() {
        return new GeneratedRoads.StreetStyle(
                !"round".equalsIgnoreCase(System.getProperty("battle.mapgen.cityRoadEnds", "square")),
                Boolean.parseBoolean(System.getProperty("battle.mapgen.citySidewalks", "true")),
                Boolean.parseBoolean(System.getProperty("battle.mapgen.cityLaneMarkings", "true")));
    }

    @Override public void run(GenContext ctx) {
        if (ctx.topology.roadSurface() != null) return;
        int[][] owner = ctx.get(BspKeys.PRECINCT_ROAD);
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        boolean precinct = owner != null && claim != null;
        boolean city = cityRoadsEnabled();
        if (!precinct && !city) return;
        var precincts = ctx.get(BspKeys.PRECINCTS);
        RuralLayout rural = ctx.get(BspKeys.RURAL_LAYOUT);
        boolean[][] road = new boolean[ctx.width][ctx.height];
        boolean[][] street = new boolean[ctx.width][ctx.height];
        boolean[][] footway = new boolean[ctx.width][ctx.height];
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            road[x][y] = precinct && owner[x][y] != GrownTrunkPlan.UNOWNED
                    && (claim[x][y] == GrownTrunkPlan.UNOWNED || precincts == null
                    || precincts.precincts().get(claim[x][y]).isProgrammed());
            street[x][y] = city && !road[x][y] && carriageway(ctx, x, y);
            footway[x][y] = city && !road[x][y] && footway(ctx, x, y);
            // The country's roads are its own routes, laid below; none of it is a street.
            if (rural != null && rural.inCountry(x, y)) road[x][y] = street[x][y] = footway[x][y] = false;
        }
        if (city) bridgeSidewalks(ctx, road, street);
        boolean[][] swept = new boolean[ctx.width][ctx.height];
        boolean anyStreet = false;
        for (int x = 0; x < ctx.width; x++) for (int y = 0; y < ctx.height; y++) {
            swept[x][y] = road[x][y] || street[x][y];
            anyStreet |= street[x][y];
        }
        // Precinct roads are swept as they always were; only bands holding a city street are judged as streets.
        var style = cityStreetStyle();
        // Street the town paved, a city street or a road laid in asphalt: a stretch of road jogging a
        // cell off the next is swept only over it.
        boolean[][] paved = new boolean[ctx.width][ctx.height];
        for (int x = 0; x < ctx.width; x++) for (int y = 0; y < ctx.height; y++)
            paved[x][y] = street[x][y] || swept[x][y] && ctx.topology.getGroundKind(x, y) == GroundKind.STREET
                    && ctx.topology.getSurface(x, y) != 0;
        var network = anyStreet
                ? GeneratedRoads.streets(swept, street, footway, paved, ctx.seed, CITY_MAX_BREADTH, style)
                : new GeneratedRoads.Network(GeneratedRoads.routes(swept, paved, ctx.seed), List.of());
        List<RoadPlan.Route> routes = new ArrayList<>(network.routes());
        if (rural != null) routes.addAll(RuralGround.routes(rural, network.routes(), ctx.seed));
        if (routes.isEmpty()) return;
        var field = SurfaceField.of(ctx);
        var mapping = GenMappingRegistry.installed();
        int rimSurface = mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(field.world().inlandGround()));
        int floorSurface = mapping == null || field.uniform() ? rimSurface
                : ctx.topology.surfaceIndex(mapping.groundBlockId(field.floor().inlandGround()));
        var allowed = new RoadSurface.Underlay[ctx.width][ctx.height];
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            var kind = ctx.topology.getGroundKind(x, y);
            if (rural != null && rural.inCountry(x, y)) {
                allowed[x][y] = RuralGround.underlay(ctx, rural, x, y);
                continue;
            }
            if (street[x][y] || (footway[x][y] && style.sidewalks())) {
                // The city's own paving is the underlay: it shows where the sweep does not reach.
                allowed[x][y] = new RoadSurface.Underlay(kind, ctx.topology.getSurface(x, y), false, false);
                continue;
            }
            if (!precinct || !outdoorOpen(ctx, x, y) || kind == GroundKind.STRIPED) continue;
            String id = ctx.topology.getSurfaceId(x, y);
            if (id == null) continue;
            boolean asphalt = road[x][y] && id.startsWith("pavement.asphalt.");
            // Junction turns may borrow only the existing concrete shoulder.
            // Natural terrain, props, authored pads and building thresholds keep ownership.
            if (!asphalt && !(id.startsWith("pavement.concrete.") && adjacent(road, x, y))) continue;
            int underlay = ctx.topology.getSurface(x, y);
            if (asphalt) underlay = claim[x][y] == GrownTrunkPlan.UNOWNED
                    ? (field.rim(x, y) ? rimSurface : floorSurface)
                    : ctx.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.4, ctx.seed, x, y)));
            // Retain the semantic kind too: this pass cannot reroll defense placement,
            // landscape, spawns, navigation costs, or any later combat behavior.
            allowed[x][y] = new RoadSurface.Underlay(kind, underlay, false, false);
        }
        var surface = RoadSurface.generated(routes, network.markings(), ctx.topology, allowed);
        if (anyStreet && style.sidewalks()) surface = surface.withUnderlays(sidewalks(ctx, surface, footway));
        ctx.topology.setRoadSurface(surface);
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++)
            if (surface.underlay(x, y) != null) ctx.topology.setTag(x, y, Tag.ROAD_EDGE_BLEND, true);
    }

    /** Open outdoor ground a finish may draw over: no structure, threshold, pad or interior. */
    private static boolean outdoorOpen(GenContext ctx, int x, int y) {
        var kind = ctx.topology.getGroundKind(x, y);
        return ctx.grid.isWalkable(x, y) && !ctx.topology.isWall(x, y) && !ctx.topology.isFixture(x, y)
                && !ctx.topology.hasBuildingKindHint(x, y) && !ctx.grid.isDoorway(x, y)
                && !ctx.isMadeGround(x, y) && kind != GroundKind.INDOOR;
    }

    /**
     * Open city ground, or street furniture standing on it (a lamp, a hedgehog, a parked van): a fixture is not
     * walkable, but the street runs on under it, so the finish reaches under it rather than leaving the old paving
     * showing round the prop.
     */
    private static boolean streetGround(GenContext ctx, int x, int y) {
        if (outdoorOpen(ctx, x, y)) return true;
        return ctx.topology.isFixture(x, y) && !ctx.topology.isWall(x, y) && !ctx.topology.hasBuildingKindHint(x, y)
                && !ctx.grid.isDoorway(x, y) && !ctx.isMadeGround(x, y)
                && ctx.topology.getGroundKind(x, y) != GroundKind.INDOOR;
    }

    /** The widest sidewalk flank a side street's mouth crosses (a primary trunk's is two). */
    static final int MOUTH_DEPTH = 3;

    /**
     * A boulevard's {@link GroundKind#SIDEWALK} flank runs across every side
     * street's mouth, and left out it would stop each side street a cell short
     * of the road it joins. A run of flank no deeper than {@link #MOUTH_DEPTH}
     * with carriageway at both ends, across the flank, is such a mouth and is
     * carriageway; the rest of the flank stays paved.
     */
    static void bridgeSidewalks(GenContext ctx, boolean[][] road, boolean[][] street) {
        int w = ctx.width, h = ctx.height;
        boolean[][] mouth = new boolean[w][h];
        for (int pass = 0; pass < 2; pass++) {
            boolean vertical = pass == 1;
            int lines = vertical ? w : h, length = vertical ? h : w;
            for (int line = 0; line < lines; line++) for (int i = 0; i < length;) {
                int x = vertical ? line : i, y = vertical ? i : line;
                if (!flank(ctx, x, y)) { i++; continue; }
                int end = i;
                while (end + 1 < length && flank(ctx, vertical ? line : end + 1, vertical ? end + 1 : line)) end++;
                if (end - i + 1 <= MOUTH_DEPTH && i > 0 && end + 1 < length
                        && paved(road, street, vertical ? line : i - 1, vertical ? i - 1 : line)
                        && paved(road, street, vertical ? line : end + 1, vertical ? end + 1 : line))
                    for (int k = i; k <= end; k++) mouth[vertical ? line : k][vertical ? k : line] = true;
                i = end + 1;
            }
        }
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) street[x][y] |= mouth[x][y];
    }

    private static boolean paved(boolean[][] road, boolean[][] street, int x, int y) {
        return road[x][y] || street[x][y];
    }

    private static boolean flank(GenContext ctx, int x, int y) {
        return ctx.topology.getGroundKind(x, y) == GroundKind.SIDEWALK && ctx.topology.getSurface(x, y) == 0
                && streetGround(ctx, x, y) && !kerb(ctx, x, y);
    }

    /**
     * How far from the asphalt a street's paving becomes its sidewalk. A street
     * band is wider than its asphalt, and a sidewalk only one deep left the rest
     * of the band as bare paving strips wherever the street met anything but a
     * building (a landing apron, a yard); a road is padded out to its band's edge.
     */
    static final int PADDING = 3;

    /**
     * Concrete underlay for every footway cell not wholly under asphalt within
     * {@link #PADDING} cells of the asphalt, and for every building apron and
     * sidewalk flank in the city whether a road runs past or not: one sidewalk material throughout, rather
     * than concrete beside the roads and the old kerb tile everywhere else.
     * A precinct road's cells are not footway and keep their own ground.
     */
    static RoadSurface.Underlay[][] sidewalks(GenContext ctx, RoadSurface surface, boolean[][] footway) {
        int w = ctx.width, h = ctx.height;
        boolean[][] asphalt = new boolean[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            var patch = surface.patch(x, y, Material.ASPHALT.ordinal());
            asphalt[x][y] = patch != null && patch.area() > 1e-6;
        }
        var sidewalk = new RoadSurface.Underlay[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            if (!footway[x][y]) continue;
            var patch = surface.patch(x, y, Material.ASPHALT.ordinal());
            if (patch != null && patch.area() > 1 - 1e-6) continue;
            var kind = ctx.topology.getGroundKind(x, y);
            boolean apron = kind == GroundKind.SIDEWALK || kerb(ctx, x, y);
            if (!apron && !near(asphalt, x, y, PADDING)) continue;
            int concrete = ctx.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.4, ctx.seed, x, y)));
            sidewalk[x][y] = new RoadSurface.Underlay(kind, concrete, false, false);
        }
        return sidewalk;
    }

    private static boolean near(boolean[][] cells, int x, int y, int reach) {
        for (int dy = -reach; dy <= reach; dy++) for (int dx = -reach; dx <= reach; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < cells.length && ny < cells[0].length && cells[nx][ny]) return true;
        }
        return false;
    }

    /** Unsurfaced city ground a sidewalk may be laid on: a street, its kerb, a boulevard's flank. */
    static boolean footway(GenContext ctx, int x, int y) {
        var kind = ctx.topology.getGroundKind(x, y);
        return (kind == GroundKind.STREET || kind == GroundKind.SIDEWALK) && ctx.topology.getSurface(x, y) == 0
                && streetGround(ctx, x, y);
    }

    /**
     * A city carriageway cell: an unsurfaced street with no wall or building in
     * its eight neighbours. The renderer draws exactly those neighbours as kerb
     * ({@code GroundTileSelector.isSidewalkCell}), so the asphalt stops at the kerb.
     */
    static boolean carriageway(GenContext ctx, int x, int y) {
        return ctx.topology.getGroundKind(x, y) == GroundKind.STREET && ctx.topology.getSurface(x, y) == 0
                && streetGround(ctx, x, y) && !kerb(ctx, x, y);
    }

    private static boolean kerb(GenContext ctx, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy;
            if ((dx != 0 || dy != 0) && ctx.topology.inBounds(nx, ny)
                    && (ctx.topology.isWall(nx, ny) || ctx.topology.hasBuildingKindHint(nx, ny))) return true;
        }
        return false;
    }

    private static boolean adjacent(boolean[][] road, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < road.length && ny < road[0].length && road[nx][ny]) return true;
        }
        return false;
    }
}
