package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.PlateauPlanner;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.gen.forest.LandscapeAccess;
import com.dillon.starsectormarines.battle.world.gen.nature.FormationBrush;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Closing outdoor pass: optional landscape yields to finished places, circulation and access. */
public final class OutdoorLandscapeStage implements GenStage {
    private final int[] hostAnchors;

    /** Fixed-layout hosts name additional spawn/staging positions not published as places yet. */
    public OutdoorLandscapeStage(int... hostAnchors) {
        if (hostAnchors.length % 2 != 0) throw new IllegalArgumentException("Expected x/y pairs");
        this.hostAnchors = hostAnchors.clone();
    }

    @Override public void run(GenContext c) {
        if (!Boolean.parseBoolean(System.getProperty("battle.mapgen.outdoorLandscape", "true"))) return;
        if (c.width < 32 || c.height < 32 || TileRegistry.installed() == null) return;
        TargetProfile profile = c.get(BspKeys.MARKET_PROFILE);
        SurfacePalette surface = profile == null ? SurfacePalette.ROCK : profile.surface();
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        Landscape landscape = plan == null ? Landscape.STOCK : plan.landscape();
        clearTracks(c, landscape);
        List<int[]> brookCells = runBrooks(c, landscape);
        boolean[] reserved = reservations(c);
        reserveTracks(c, landscape, reserved);
        for (int[] cell : brookCells) reserve(c, reserved, cell[0] - 2, cell[1] - 2, cell[0] + 2, cell[1] + 2);
        // Own substream: absent nature never moves facilities, force positions or later RNG draws.
        Random random = new Random(c.seed ^ 0x574f4f444c414e44L);
        if (c.topology.terrainSurface() == null) hills(c, surface, reserved, random);
        int plateaus = plateausWanted(c);
        if (plateaus > 0) plateaus(c, reserved, plateaus, new Random(c.seed ^ 0x504c4154L));
        if (surface == SurfacePalette.VERDANT) trees(c, reserved, landscape.woodland());
        else FormationBrush.scatter(c,surface,reserved);
        // Hills, trees and formations turn cells non-walkable after TacticalRegionStage
        // published, so refresh the artifact: "non-walkable => regionId -1" must hold.
        if (c.get(BspKeys.TACTICAL_REGIONS) != null) {
            c.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(c.grid, c.topology, c.get(BspKeys.AXIS)));
        }
    }

    private boolean[] reservations(GenContext c) {
        boolean[] reserved = new boolean[c.width * c.height];
        boolean[][] roads = c.get(BspKeys.ROAD_RESERVATION), roadCells = c.get(BspKeys.ROAD_CELLS);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (x < 4 || y < 4 || x >= c.width - 4 || y >= c.height - 4
                    || c.isMadeGround(x, y) || c.grid.isDoorway(x, y)
                    || (roads != null && roads[x][y]) || (roadCells != null && roadCells[x][y]))
                reserve(c, reserved, x - 2, y - 2, x + 2, y + 2);
        }
        List<int[]> anchors = new ArrayList<>();
        for (var key : List.of(BspKeys.MARINE_SPAWN, BspKeys.DEFENDER_SPAWN)) {
            int[] point = c.get(key);
            if (point != null) anchors.add(point);
        }
        for (int i = 0; i < hostAnchors.length; i += 2) anchors.add(new int[]{hostAnchors[i], hostAnchors[i + 1]});
        for (var pad : c.landingPads) {
            anchors.add(new int[]{pad.centerX, pad.centerY});
            int l = pad.left() - 4, r = pad.right() + 4, b = pad.bottom() - 4, t = pad.top() + 4;
            switch (pad.approach) {
                case WEST -> l = 0;
                case EAST -> r = c.width - 1;
                case SOUTH -> b = 0;
                case NORTH -> t = c.height - 1;
            }
            reserve(c, reserved, l, b, r, t);
        }
        for (var poi : c.pois) {
            reserve(c, reserved, poi.left - 3, poi.top - 3, poi.right + 3, poi.bottom + 3);
            anchors.add(new int[]{poi.interiorAnchorX, poi.interiorAnchorY});
            anchors.add(new int[]{poi.anchorCellX, poi.anchorCellY});
        }
        for (var node : c.tactical) {
            reserve(c, reserved, node.left - 3, node.top - 3, node.right + 3, node.bottom + 3);
            anchors.add(new int[]{node.anchorX, node.anchorY});
        }
        for (var prop : c.doodads)
            reserve(c, reserved, prop.cellX - 1, prop.cellY - 1,
                    prop.cellX + prop.footprintCellsX, prop.cellY + prop.footprintCellsY);
        // An open-field landing keeps its whole disc clear: a grove or a hill
        // there is cover for whatever comes at the berths.
        FieldOfFire field = FieldOfFire.of(c.get(BspKeys.PRECINCTS));
        if (field != null) {
            int r = field.radius();
            for (int y = field.centerY() - r; y <= field.centerY() + r; y++)
                for (int x = field.centerX() - r; x <= field.centerX() + r; x++)
                    if (x >= 0 && y >= 0 && x < c.width && y < c.height && field.contains(x, y))
                        reserved[y * c.width + x] = true;
        }
        var lanes = c.get(BspKeys.LANES);
        if (lanes != null) for (var lane : lanes) for (var cell : lane.route())
            reserve(c, reserved, cell.x() - 2, cell.y() - 2, cell.x() + 2, cell.y() + 2);
        for (int[] point : anchors) reserve(c, reserved, point[0] - 5, point[1] - 5, point[0] + 5, point[1] + 5);
        LandscapeAccess.reserveRoutes(c.grid, anchors, reserved);
        return reserved;
    }

    private static void hills(GenContext c, SurfacePalette surface, boolean[] reserved, Random random) {
        TerrainPlan plan = new TerrainPlan(c.width, c.height,
                surface == SurfacePalette.VERDANT ? TerrainPlan.Finish.GRASS_TOP
                        : surface == SurfacePalette.FROZEN ? TerrainPlan.Finish.ICE : TerrainPlan.Finish.ROCK,
                1.8, .55, c.seed ^ 0x48494c4cL, List.of());
        boolean[] occupied = new boolean[reserved.length], closed = new boolean[reserved.length];
        int desired = Math.max(1, Math.min(10, c.width * c.height / 6000));
        for (int attempt = 0; attempt < desired * 12 && plan.strokes().size() < desired; attempt++) {
            int radius = 7 + random.nextInt(6);
            int x = 10 + random.nextInt(c.width - 20), y = 10 + random.nextInt(c.height - 20);
            var stroke = ridge(c,surface,x,y,radius,attempt);
            TerrainField field = TerrainField.build(new TerrainPlan(c.width, c.height, plan.finish(),
                    plan.faceDepth(), plan.ruggedness(), plan.seed(), List.of(stroke)));
            int l = Math.max(0,(int)Math.floor(stroke.points().stream().mapToDouble(TerrainPlan.Point::x).min().orElseThrow()-stroke.radius()-4));
            int b = Math.max(0,(int)Math.floor(stroke.points().stream().mapToDouble(TerrainPlan.Point::y).min().orElseThrow()-stroke.radius()-4));
            int r = Math.min(c.width-1,(int)Math.ceil(stroke.points().stream().mapToDouble(TerrainPlan.Point::x).max().orElseThrow()+stroke.radius()+4));
            int t = Math.min(c.height-1,(int)Math.ceil(stroke.points().stream().mapToDouble(TerrainPlan.Point::y).max().orElseThrow()+stroke.radius()+4));
            boolean fits = true;
            for (int yy = b; yy <= t && fits; yy++) for (int xx = l; xx <= r; xx++) {
                if (field.maximum(xx, yy) < -.65) continue;
                if (reserved[yy * c.width + xx] || occupied[yy * c.width + xx] || !openGround(c, xx, yy)) {
                    fits = false; break;
                }
            }
            if (!fits || !LandscapeAccess.canClose(c.grid, closed, l, b, r, t, field::blocked)) continue;
            plan = plan.append(stroke);
            for (int yy = b; yy <= t; yy++) for (int xx = l; xx <= r; xx++) {
                if (field.maximum(xx, yy) >= -.65) occupied[yy * c.width + xx] = true;
                if (field.blocked(xx, yy)) closed[yy * c.width + xx] = true;
            }
        }
        if (!plan.strokes().isEmpty()) TerrainBrush.stamp(plan, c.grid, c.topology, reserved);
    }

    private static int plateausWanted(GenContext c) {
        Integer asked = c.get(BspKeys.PLATEAUS);
        return asked != null ? asked : Integer.getInteger("battle.mapgen.plateaus", 0);
    }

    /**
     * Raised ground the mission asked for: plateaus on open ground clear of
     * every reservation and of the hills, each ramped toward the nearest
     * reserved ground, so it overlooks what the host keeps open and is climbed
     * from it. Own random stream, so asking for none changes nothing.
     */
    private static void plateaus(GenContext c, boolean[] reserved, int count, Random random) {
        int w = c.width;
        ElevationPlan plan = PlateauPlanner.plan(c.width, c.height, c.seed ^ 0x504c4154L, count, random,
                i -> !reserved[i] && openGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                i -> reserved[i],
                layout -> keepsAccess(c, layout));
        if (plan != null) ElevationBrush.stamp(plan, c.grid, c.topology, reserved);
    }

    /** Whether the ground around a layout's faces stays one connected piece once they close. */
    private static boolean keepsAccess(GenContext c, ElevationLayout layout) {
        int l = c.width, b = c.height, r = -1, t = -1;
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (!layout.touched(x, y)) continue;
            l = Math.min(l, x); r = Math.max(r, x); b = Math.min(b, y); t = Math.max(t, y);
        }
        return r < 0 || LandscapeAccess.canClose(c.grid, new boolean[c.width * c.height], l, b, r, t,
                layout::isFace);
    }

    private static TerrainPlan.Stroke ridge(GenContext c,SurfacePalette surface,int x,int y,int radius,int attempt) {
        List<TerrainPlan.Point> points=new ArrayList<>();
        double brushRadius=radius;
        if(surface==SurfacePalette.ROCK) {
            // Open crescent rim: a crater suggests enclosure without sealing a movement island.
            brushRadius=3;
            for(int i=0;i<=6;i++) {
                double angle=i*Math.PI/6+(attempt%4)*Math.PI/2;
                points.add(new TerrainPlan.Point(Math.max(0,Math.min(c.width-1,x+Math.cos(angle)*radius)),
                        Math.max(0,Math.min(c.height-1,y+Math.sin(angle)*radius))));
            }
        } else if(surface==SurfacePalette.FROZEN) {
            brushRadius=3.5;
            for(int i=-2;i<=2;i++) {
                double along=i*radius*.55,across=(i%2)*2;
                points.add(new TerrainPlan.Point(Math.max(0,Math.min(c.width-1,x+((attempt&1)==0?along:across))),
                        Math.max(0,Math.min(c.height-1,y+((attempt&1)==0?across:along)))));
            }
        } else {
            points.add(new TerrainPlan.Point(x,y));
            points.add(new TerrainPlan.Point(Math.min(c.width-1,x+radius/2),y+2));
        }
        return new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE,brushRadius,points);
    }

    /**
     * A plan's tracks cleared to bare earth before anything grows: open natural
     * ground on a track becomes dirt and loses its shrubs. A building, a road or
     * a place on the track is left as it stands.
     */
    private static void clearTracks(GenContext c, Landscape landscape) {
        for (Landscape.Track track : landscape.tracks()) {
            for (int y = Math.max(0, track.y0()); y <= Math.min(c.height - 1, track.y1()); y++)
                for (int x = Math.max(0, track.x0()); x <= Math.min(c.width - 1, track.x1()); x++) {
                    if (!openGround(c, x, y)) continue;
                    c.topology.setGroundKind(x, y, GroundKind.DIRT);
                    c.topology.setNatureOverlayIndex(x, y, -1);
                }
        }
    }

    /**
     * A plan's brooks run across the country: water on open natural ground
     * along each centre line, a sand strip on either bank, and at every ford
     * sand right across. Returns the centre cells, for the reservations.
     */
    private static List<int[]> runBrooks(GenContext c, Landscape landscape) {
        List<int[]> centres = new ArrayList<>();
        for (Landscape.Brook brook : landscape.brooks()) {
            List<int[]> line = new ArrayList<>();
            for (int i = 0; i + 1 < brook.path().size(); i++) {
                int[] a = brook.path().get(i), b = brook.path().get(i + 1);
                int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                for (int k = i == 0 ? 0 : 1; k <= steps; k++) {
                    line.add(new int[]{Math.round(a[0] + (b[0] - a[0]) * k / (float) Math.max(1, steps)),
                            Math.round(a[1] + (b[1] - a[1]) * k / (float) Math.max(1, steps))});
                }
            }
            float r = brook.width() / 2f;
            int reach = (int) Math.ceil(r) + 1;
            for (int[] centre : line) {
                for (int dy = -reach; dy <= reach; dy++) for (int dx = -reach; dx <= reach; dx++) {
                    int x = centre[0] + dx, y = centre[1] + dy;
                    if (x < 0 || y < 0 || x >= c.width || y >= c.height) continue;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d <= r) {
                        if (brookGround(c, x, y)) {
                            c.topology.setSurface(x, y, 0);
                            c.topology.setGroundKind(x, y, GroundKind.WATER);
                            c.topology.setNatureOverlayIndex(x, y, -1);
                            c.grid.setWalkable(x, y, false);
                            c.grid.setSeeThrough(x, y, true);
                            c.grid.setEdgeCoverSuppressed(x, y, true);
                        }
                    } else if (d <= r + 1f && brookGround(c, x, y) && c.topology.getGroundKind(x, y) != GroundKind.WATER) {
                        c.topology.setSurface(x, y, 0);
                        c.topology.setGroundKind(x, y, GroundKind.SAND);
                        c.topology.setNatureOverlayIndex(x, y, -1);
                    }
                }
            }
            // Fords last, so a ford is open across the whole stream whatever drew water over it.
            for (int i = 0; i < line.size(); i++) {
                if (i % brook.fordEvery() >= brook.fordWidth() || i < brook.fordWidth()) continue;
                int[] centre = line.get(i);
                for (int dy = -reach; dy <= reach; dy++) for (int dx = -reach; dx <= reach; dx++) {
                    int x = centre[0] + dx, y = centre[1] + dy;
                    if (x < 0 || y < 0 || x >= c.width || y >= c.height) continue;
                    if (c.topology.getGroundKind(x, y) != GroundKind.WATER) continue;
                    c.topology.setGroundKind(x, y, GroundKind.SAND);
                    c.grid.setWalkable(x, y, true);
                    c.grid.setEdgeCoverSuppressed(x, y, false);
                }
            }
            centres.addAll(line);
        }
        return centres;
    }

    /**
     * Ground a brook may run over: walkable natural ground outside any building,
     * door, fixture or road, whatever ground dressing lies on it.
     */
    private static boolean brookGround(GenContext c, int x, int y) {
        boolean natural = switch (c.topology.getGroundKind(x, y)) {
            case GRASS, DIRT, STONE, SAND, SNOW -> true;
            default -> false;
        };
        return natural && c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y)
                && !c.topology.isWall(x, y) && !c.topology.isFixture(x, y)
                && c.topology.getBuildingId(x, y) == 0
                && (c.topology.roadSurface() == null || c.topology.roadSurface().underlay(x, y) == null);
    }

    /** No tree, hill or formation stands on a track or the cell beside it. */
    private static void reserveTracks(GenContext c, Landscape landscape, boolean[] reserved) {
        for (Landscape.Track track : landscape.tracks())
            reserve(c, reserved, track.x0() - 1, track.y0() - 1, track.x1() + 1, track.y1() + 1);
    }

    private static void trees(GenContext c, boolean[] reserved, float woodland) {
        boolean[] paint = new boolean[reserved.length], closed = new boolean[reserved.length];
        PatchField groves = new PatchField(c.seed ^ 0x47524f5645L, 19);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (reserved[y * c.width + x]) continue;
            boolean summit = ForestBrush.isGrassySummit(c.grid, c.topology, x, y);
            boolean soil = switch (c.topology.getGroundKind(x, y)) {
                case GRASS, DIRT -> true;
                default -> false;
            };
            // Exposed faces/talus remain legible; only complete summit cells carry crowns.
            if (summit || (soil && openGround(c, x, y) && !terrainVisible(c, x, y)))
                paint[y * c.width + x] = groves.sample(x, y) < (summit ? Math.max(.85f, woodland) : woodland);
        }
        ForestField field = ForestField.build(c.width, c.height, c.seed ^ 0x5452454553L, ForestPlan.Woodland.MIXED, paint);
        List<ForestField.Tree> accepted = new ArrayList<>();
        for (var tree : field.trees()) {
            boolean closes = false;
            for (int y = tree.y(); y < tree.y() + tree.height(); y++)
                for (int x = tree.x(); x < tree.x() + tree.width(); x++)
                    if (tree.blocks(x, y) && c.grid.isWalkable(x, y) && !closed[y * c.width + x]) closes = true;
            if (closes && !LandscapeAccess.canClose(c.grid, closed, tree.x(), tree.y(),
                    tree.x() + tree.width() - 1, tree.y() + tree.height() - 1, tree::blocks)) continue;
            accepted.add(tree);
            for (int y = tree.y(); y < tree.y() + tree.height(); y++)
                for (int x = tree.x(); x < tree.x() + tree.width(); x++)
                    if (tree.blocks(x, y)) closed[y * c.width + x] = true;
        }
        ForestBrush.stampTrees(accepted, c.grid, c.topology, c.doodads, reserved, true);
    }

    private static boolean terrainVisible(GenContext c, int x, int y) {
        var terrain = c.topology.terrainSurface();
        if (terrain == null) return false;
        for (int layer = 0; layer < TerrainSurface.LAYERS; layer++) if (terrain.patch(x, y, layer) != null) return true;
        return false;
    }

    private static boolean openGround(GenContext c, int x, int y) {
        boolean natural = switch (c.topology.getGroundKind(x, y)) {
            case GRASS, DIRT, STONE, SAND, SNOW -> true;
            default -> false;
        };
        return natural && c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y)
                && !c.topology.isWall(x, y) && !c.topology.isFixture(x, y)
                && c.topology.getBuildingId(x, y) == 0 && c.topology.getSurface(x, y) == 0
                && (c.topology.roadSurface() == null || c.topology.roadSurface().underlay(x, y) == null);
    }

    private static void reserve(GenContext c, boolean[] cells, int l, int b, int r, int t) {
        for (int y = Math.max(0, b); y <= Math.min(c.height - 1, t); y++)
            for (int x = Math.max(0, l); x <= Math.min(c.width - 1, r); x++) cells[y * c.width + x] = true;
    }
}
