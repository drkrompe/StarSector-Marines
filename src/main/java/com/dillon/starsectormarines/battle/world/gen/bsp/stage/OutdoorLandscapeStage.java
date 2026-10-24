package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.PlateauPlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.TerracePlanner;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.gen.forest.LandscapeAccess;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.nature.FormationBrush;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

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
        if (landscape.raggedMargin() > 0) raggedMargins(c, landscape.raggedMargin());
        clearTracks(c, landscape);
        List<int[]> brookCells = runBrooks(c, landscape);
        boolean[] reserved = reservations(c, hostAnchors, true);
        reserveRamps(c, reserved);
        reserveTracks(c, landscape, reserved);
        // Keep crowns off the banks too: a brook is a firebreak only if nothing that burns overhangs it.
        int bank = landscape.brooks().stream().mapToInt(Landscape.Brook::width).max().orElse(3) / 2 + 2;
        for (int[] cell : brookCells) reserve(c, reserved, cell[0] - bank, cell[1] - bank, cell[0] + bank, cell[1] + bank);
        // Own substream: absent nature never moves facilities, force positions or later RNG draws.
        Random random = new Random(c.seed ^ 0x574f4f444c414e44L);
        List<RidgePlanner.Band> ridges = landscapeBands(c, landscape);
        // No stock hill grows where the mission named high ground: it would be all the ridge found there.
        boolean[][] ridgeGround = ridges.isEmpty() ? null : RidgePlanner.heldGround(ridges, c.width, c.height, 0);
        if (c.topology.terrainSurface() == null) hills(c, surface, reserved, ridgeGround, random);
        int plateaus = plateausWanted(c);
        if (plateaus > 0) plateaus(c, reserved, plateaus, new Random(c.seed ^ 0x504c4154L));
        if (!ridges.isEmpty() && c.grid.elevation() == null) {
            ridges(c, reserved, ridges, false, List.of(), new boolean[c.width * c.height]);
        }
        if (surface == SurfacePalette.VERDANT) trees(c, reserved, landscape.woodland());
        else FormationBrush.scatter(c,surface,reserved);
        // Hills, trees and formations turn cells non-walkable after TacticalRegionStage
        // published, so refresh the artifact: "non-walkable => regionId -1" must hold.
        if (c.get(BspKeys.TACTICAL_REGIONS) != null) {
            c.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(c.grid, c.topology, c.get(BspKeys.AXIS)));
        }
    }

    /**
     * Raises a mission layout's ridges before its works are laid ({@code mission-layout.md}, Ridge), against the
     * reservations this pass keeps plus the structures already raised and {@code also} (sites the layout holds),
     * so a line resolved along a ridge is dug on its crest. Returns the ramp feet of the segments raised, one per
     * segment, empty when none fitted or the map already has elevation.
     */
    /**
     * The landscape's own high ground: its ridges, the ridges it holds before growth, and each banked brook's valley
     * sides. Raised in this pass, or with a layout's ridges when the layout has any, so elevation is stamped once.
     */
    public static List<RidgePlanner.Band> landscapeBands(GenContext c) {
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        return landscapeBands(c, plan == null ? Landscape.STOCK : plan.landscape());
    }

    private static List<RidgePlanner.Band> landscapeBands(GenContext c, Landscape landscape) {
        List<RidgePlanner.Band> bands = new ArrayList<>(landscape.ridges());
        bands.addAll(landscape.held());
        bands.addAll(valleySides(c, landscape));
        return bands;
    }

    public static List<int[]> raiseRidges(GenContext c, List<RidgePlanner.Band> bands, boolean[][] also) {
        return raiseRidges(c, bands, also, List.of()).feet();
    }

    /**
     * What {@link #raiseRidges(GenContext, List, boolean[][], List)} raised: the ridges' ramp feet, one per segment,
     * and each terrace as planned, standing or refused with its reason.
     */
    public record Raised(List<int[]> feet, List<TerracePlanner.Planned> terraces) { }

    /**
     * The layout's ridges with built ground standing on their crests ({@link TerracePlanner}): each terrace is
     * planned first, against the same reservations, kept when its faces keep the ground connected, and the ridges
     * then run on through it. A refused terrace leaves its place in a pass, as before.
     */
    public static Raised raiseRidges(GenContext c, List<RidgePlanner.Band> bands, boolean[][] also,
                                     List<TerracePlanner.Terrace> terraces) {
        if (bands.isEmpty() || c.grid.elevation() != null || c.width < 32 || c.height < 32
                || TileRegistry.installed() == null
                || !Boolean.parseBoolean(System.getProperty("battle.mapgen.outdoorLandscape", "true"))) {
            List<TerracePlanner.Planned> refused = new ArrayList<>();
            for (TerracePlanner.Terrace t : terraces) refused.add(refusedTerrace(t, "no ridge was raised on this map"));
            return new Raised(List.of(), refused);
        }
        boolean[] reserved = ridgeReservations(c, also, null);
        // A terrace's place does not keep its own cliff off: the margin round built ground is for a cliff that would
        // stand beside it, and a terrace's stands round it. Everything else is reserved as for any ridge.
        boolean[] own = null;
        if (!terraces.isEmpty()) {
            own = new boolean[c.width * c.height];
            for (TerracePlanner.Terrace t : terraces) {
                for (int y = Math.max(0, t.y0()); y <= Math.min(c.height - 1, t.y1()); y++) {
                    for (int x = Math.max(0, t.x0()); x <= Math.min(c.width - 1, t.x1()); x++) own[y * c.width + x] = true;
                }
            }
        }
        boolean[] reservedRound = own == null ? reserved : ridgeReservations(c, also, own);
        int w = c.width;
        boolean[] kept = new boolean[c.width * c.height];
        long seed = c.seed ^ RIDGE_SALT;
        // Road as the layout reads it (reserved, painted, or a place's own road in open country), and any paving a
        // road surface dresses: a place's arms out through its gates are what its ramps carry.
        LayoutGround ground = new LayoutGround(c);
        IntPredicate road = i -> ground.isRoad(i % w, i / w)
                || (c.topology.roadSurface() != null && c.topology.roadSurface().underlay(i % w, i / w) != null)
                || c.topology.getGroundKind(i % w, i / w) == GroundKind.STREET;
        IntPredicate open = i -> PreparedGround.open(c, i % w, i / w) && c.topology.getBuildingId(i % w, i / w) == 0;
        IntPredicate free = ridgeFree(c, reservedRound, true);
        List<TerracePlanner.Planned> planned = new ArrayList<>(), standing = new ArrayList<>();
        boolean[] taken = new boolean[c.width * c.height];
        boolean[] main = terraces.isEmpty() ? null : mainBody(c);
        for (TerracePlanner.Terrace terrace : terraces) {
            TerracePlanner.Planned p = TerracePlanner.plan(c.width, c.height, seed, terrace, road, open, free,
                    i -> c.topology.getBuildingId(i % w, i / w) == 0 && !c.topology.isWall(i % w, i / w));
            if (p.stands()) p = keptTerrace(c, p, standing, taken, main, seed);
            planned.add(p);
            if (!p.stands()) continue;
            standing.add(p);
            for (int i = 0; i < taken.length; i++) taken[i] |= p.zone()[i] || p.built()[i];
        }
        // The ridges are held to their place only round the terraces that stand: a refused terrace's place is
        // ordinary built ground again, kept off by the ordinary margins, and a standing terrace's place is its own
        // top, on which no ridge face may land.
        boolean[] ridgeReserved = reserved, stamped = reserved;
        if (!standing.isEmpty()) {
            boolean[] ownStanding = new boolean[c.width * c.height];
            for (TerracePlanner.Planned p : standing) {
                TerracePlanner.Terrace t = p.terrace();
                for (int y = Math.max(0, t.y0()); y <= Math.min(c.height - 1, t.y1()); y++) {
                    for (int x = Math.max(0, t.x0()); x <= Math.min(c.width - 1, t.x1()); x++) {
                        ownStanding[y * c.width + x] = true;
                    }
                }
            }
            stamped = ridgeReservations(c, also, ownStanding);
            ridgeReserved = reserved.clone();
            for (int i = 0; i < ridgeReserved.length; i++) ridgeReserved[i] |= ownStanding[i];
        }
        ElevationPlan plan = ridges(c, ridgeReserved, stamped, bands, true, standing, kept, main);
        if (plan == null) return new Raised(List.of(), planned);
        List<ElevationPlan.Ramp> terraceRamps = new ArrayList<>();
        for (TerracePlanner.Planned p : standing) terraceRamps.addAll(p.ramps());
        List<int[]> feet = new ArrayList<>();
        for (ElevationPlan.Ramp ramp : plan.ramps()) {
            if (terraceRamps.contains(ramp)) continue;
            feet.add(new int[]{(int) Math.round(ramp.foot().x()), (int) Math.round(ramp.foot().y())});
        }
        return new Raised(feet, planned);
    }

    private static final long RIDGE_SALT = 0x52494447L;

    /**
     * What a layout's ridge keeps off: the landscape's reservations without the access routes (the ridge's own
     * access proof stands for them, and a route along the line would break every stretch of it; the ways through
     * are its passes and ramps), the landscape's tracks and brooks to come, the structures already raised and
     * {@code also}. {@code own} is ground whose built things reserve nothing round themselves.
     */
    private static boolean[] ridgeReservations(GenContext c, boolean[][] also, boolean[] own) {
        // Round a terrace a cliff may stand a cell off a road rather than two: the road beside a place is often
        // close behind it, and a cell of open ground keeps a vehicle's way along the road as wide as the road.
        boolean[] reserved = reservations(c, new int[0], false, own, own == null ? 2 : 1);
        // The landscape's tracks and brooks are laid after this, so their ground is kept for them now.
        PrecinctPlan precincts = c.get(BspKeys.PRECINCTS);
        Landscape landscape = precincts == null ? Landscape.STOCK : precincts.landscape();
        reserveTracks(c, landscape, reserved);
        for (Landscape.Brook brook : landscape.brooks()) {
            int bank = brook.width() / 2 + 2;
            for (int[] cell : brook.line()) reserve(c, reserved, cell[0] - bank, cell[1] - bank, cell[0] + bank, cell[1] + bank);
        }
        List<Laid> raised = c.get(BspKeys.LAYOUT_STRUCTURES);
        if (raised != null) for (Laid piece : raised) {
            if (piece.kind() == Laid.Kind.STRUCTURE && piece.isLaid() && piece.cells().size() == 2) {
                reserve(c, reserved, piece.cells().get(0)[0], piece.cells().get(0)[1],
                        piece.cells().get(1)[0], piece.cells().get(1)[1]);
            }
        }
        if (also != null) for (int x = 0; x < c.width; x++) for (int y = 0; y < c.height; y++) {
            if (also[x][y]) reserve(c, reserved, x - 2, y - 2, x + 2, y + 2);
        }
        return reserved;
    }

    private static TerracePlanner.Planned refusedTerrace(TerracePlanner.Terrace terrace, String why) {
        return new TerracePlanner.Planned(terrace, List.of(), List.of(), null, null, why);
    }

    /**
     * The terrace, if it stands clear of the terraces already kept and its faces keep the ground round it
     * connected (adding them to {@code kept}); else refused, saying which.
     */
    private static TerracePlanner.Planned keptTerrace(GenContext c, TerracePlanner.Planned p,
                                                      List<TerracePlanner.Planned> standing, boolean[] taken,
                                                      boolean[] main, long seed) {
        for (int i = 0; i < taken.length; i++) {
            if (taken[i] && (p.zone()[i] || p.built()[i])) return refusedTerrace(p.terrace(), "it runs into another terrace");
        }
        List<ElevationPlan.Plateau> plateaus = new ArrayList<>();
        List<ElevationPlan.Ramp> ramps = new ArrayList<>();
        for (TerracePlanner.Planned s : standing) {
            plateaus.addAll(s.plateaus());
            ramps.addAll(s.ramps());
        }
        plateaus.addAll(p.plateaus());
        ramps.addAll(p.ramps());
        ElevationLayout layout;
        try {
            layout = ElevationLayout.resolve(new ElevationPlan(c.width, c.height, PlateauPlanner.FACE_WIDTH,
                    PlateauPlanner.SOUTH_FACE, .6, seed, plateaus, ramps));
        } catch (IllegalArgumentException refused) {
            return refusedTerrace(p.terrace(), "it does not resolve beside the others: " + refused.getMessage());
        }
        if (!keepsWholeAccess(c, layout, main)) return refusedTerrace(p.terrace(), "its cliff would cut the ground apart");
        return p;
    }

    /** Cells a ridge, its faces and its apron may cover: open natural ground nothing reserves. */
    private static IntPredicate ridgeFree(GenContext c, boolean[] reserved, boolean overLawns) {
        int w = c.width;
        return i -> !reserved[i] && (openGround(c, i % w, i / w) || overLawns && lawn(c, i % w, i / w))
                && !terrainVisible(c, i % w, i / w);
    }

    /** Ramps already on the map (a layout's ridges, raised before this pass) stay clear: they are the only ways up. */
    private static void reserveRamps(GenContext c, boolean[] reserved) {
        var elevation = c.grid.elevation();
        if (elevation == null) return;
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (elevation.isRamp(x, y)) reserve(c, reserved, x - 1, y - 1, x + 1, y + 1);
        }
    }

    private static boolean[] reservations(GenContext c, int[] hostAnchors, boolean routes) {
        return reservations(c, hostAnchors, routes, null, 2);
    }

    /**
     * @param own cells whose made ground, doors, points of interest, nodes and props reserve only themselves rather
     *            than a margin round them (a terrace's place), or null
     */
    private static boolean[] reservations(GenContext c, int[] hostAnchors, boolean routes, boolean[] own,
                                          int roadMargin) {
        boolean[] reserved = new boolean[c.width * c.height];
        boolean[][] roads = c.get(BspKeys.ROAD_RESERVATION), roadCells = c.get(BspKeys.ROAD_CELLS);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (own != null && own[y * c.width + x]) {
                reserved[y * c.width + x] = true;
                continue;
            }
            if (x < 4 || y < 4 || x >= c.width - 4 || y >= c.height - 4
                    || c.isMadeGround(x, y) || c.grid.isDoorway(x, y))
                reserve(c, reserved, x - 2, y - 2, x + 2, y + 2);
            else if ((roads != null && roads[x][y]) || (roadCells != null && roadCells[x][y]))
                reserve(c, reserved, x - roadMargin, y - roadMargin, x + roadMargin, y + roadMargin);
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
            if (ownCell(c, own, poi.interiorAnchorX, poi.interiorAnchorY)) {
                reserve(c, reserved, poi.left, poi.top, poi.right, poi.bottom);
                continue;
            }
            reserve(c, reserved, poi.left - 3, poi.top - 3, poi.right + 3, poi.bottom + 3);
            anchors.add(new int[]{poi.interiorAnchorX, poi.interiorAnchorY});
            anchors.add(new int[]{poi.anchorCellX, poi.anchorCellY});
        }
        for (var node : c.tactical) {
            if (ownCell(c, own, node.anchorX, node.anchorY)) {
                reserve(c, reserved, node.left, node.top, node.right, node.bottom);
                continue;
            }
            reserve(c, reserved, node.left - 3, node.top - 3, node.right + 3, node.bottom + 3);
            anchors.add(new int[]{node.anchorX, node.anchorY});
        }
        for (var prop : c.doodads) {
            int margin = ownCell(c, own, prop.cellX, prop.cellY) ? 0 : 1;
            reserve(c, reserved, prop.cellX - margin, prop.cellY - margin,
                    prop.cellX + prop.footprintCellsX - 1 + margin, prop.cellY + prop.footprintCellsY - 1 + margin);
        }
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
        // A mission layout's works, the ways through them and its open sites stay as they were laid. Ground held
        // for authored terrain is not reserved here: ridges and rises are what it is for.
        LaidLayout laid = c.get(BspKeys.MISSION_LAYOUT_LAID);
        if (laid != null) for (Laid piece : laid.pieces()) {
            if (piece.kind() == Laid.Kind.GROUND) continue;
            if ((piece.kind() == Laid.Kind.SITE || piece.kind() == Laid.Kind.STRUCTURE) && piece.cells().size() == 2) {
                reserve(c, reserved, piece.cells().get(0)[0], piece.cells().get(0)[1], piece.cells().get(1)[0], piece.cells().get(1)[1]);
                continue;
            }
            for (List<int[]> cells : List.of(piece.cells(), piece.gaps(), piece.nests()))
                for (int[] cell : cells) reserve(c, reserved, cell[0] - 2, cell[1] - 2, cell[0] + 2, cell[1] + 2);
        }
        for (int[] point : anchors) reserve(c, reserved, point[0] - 5, point[1] - 5, point[0] + 5, point[1] + 5);
        if (routes) LandscapeAccess.reserveRoutes(c.grid, anchors, reserved);
        return reserved;
    }

    private static boolean ownCell(GenContext c, boolean[] own, int x, int y) {
        return own != null && x >= 0 && y >= 0 && x < c.width && y < c.height && own[y * c.width + x];
    }

    private static void hills(GenContext c, SurfacePalette surface, boolean[] reserved, boolean[][] ridgeGround,
                              Random random) {
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
                if (reserved[yy * c.width + xx] || occupied[yy * c.width + xx] || !openGround(c, xx, yy)
                        || (ridgeGround != null && ridgeGround[xx][yy])) {
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

    /** How many plateaus the map asks for: the generation key, else the switch, else none. */
    public static int plateausWanted(GenContext c) {
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

    /**
     * The mission's ridges: each band raised as hill segments on open ground,
     * broken into passes wherever the host's ground crosses it.
     */
    /**
     * @param overLawns whether the ridges may rise over lawn painted on open country, which a layout's ridge does:
     *                  its band is the mission's high ground, and a lawn is not something it yields to as a road or
     *                  a place is. The ridge's top takes the map's own ground there.
     */
    private static ElevationPlan ridges(GenContext c, boolean[] reserved, List<RidgePlanner.Band> bands,
                                        boolean overLawns, List<TerracePlanner.Planned> terraces, boolean[] kept) {
        return ridges(c, reserved, reserved, bands, overLawns, terraces, kept, null);
    }

    /**
     * @param stamped the reservations the brush is held to: {@code reserved} less the margins round the terraces'
     *                own built ground, which their cliffs may stand in
     * @param main    with terraces, the map's main body, and every segment is proved over the whole map
     *                ({@link #keepsWholeAccess}); null proves each segment's faces locally, as ever
     */
    private static ElevationPlan ridges(GenContext c, boolean[] reserved, boolean[] stamped,
                                        List<RidgePlanner.Band> bands, boolean overLawns,
                                        List<TerracePlanner.Planned> terraces, boolean[] kept, boolean[] main) {
        // Proved a segment at a time: each new segment's faces in a box round
        // them alone, with the faces already kept counted closed. One box round
        // every ridge at once spans half the map, and any pocket in it refuses
        // them all. A terrace's faces are already kept.
        ElevationPlan plan = RidgePlanner.plan(c.width, c.height, c.seed ^ RIDGE_SALT, bands,
                ridgeFree(c, reserved, overLawns),
                layout -> main == null ? keepsAccess(c, layout, kept) : keepsWholeAccess(c, layout, main), terraces);
        if (plan == null) return null;
        boolean[] built = null;
        if (!terraces.isEmpty()) {
            built = new boolean[c.width * c.height];
            for (TerracePlanner.Planned terrace : terraces) {
                for (int i = 0; i < built.length; i++) built[i] |= terrace.built()[i];
            }
        }
        if (overLawns) {
            // A lawn the ridge rises over takes the map's own ground; a terrace keeps what its place laid.
            ElevationLayout raised = ElevationLayout.resolve(plan);
            for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
                if (built != null && built[y * c.width + x] && !raised.isFace(x, y)) continue;
                if (raised.touched(x, y) && lawn(c, x, y)) c.topology.setSurface(x, y, 0);
            }
        }
        ElevationBrush.stamp(plan, c.grid, c.topology, stamped, built);
        // A ridge's ramps are its only ways up: no tree may grow on one and close it.
        ElevationLayout layout = ElevationLayout.resolve(plan);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (layout.isRamp(x, y)) reserve(c, reserved, x - 1, y - 1, x + 1, y + 1);
        }
        return plan;
    }

    /** Cells of low ground kept between a brook's sand and the foot of its valley side. */
    static final int VALLEY_FLOOR = 3;
    /** Cells a valley side stops short of a ford, either side of it, so the way down to the ford is open. */
    static final int FORD_APPROACH = 6;

    /**
     * Each banked brook's valley sides: a band along either bank, offset clear of
     * the water, the sand and the bank kept free of trees, broken at every ford
     * and climbed from the country away from the water.
     */
    private static List<RidgePlanner.Band> valleySides(GenContext c, Landscape landscape) {
        List<RidgePlanner.Band> out = new ArrayList<>();
        for (Landscape.Brook brook : landscape.brooks()) {
            if (brook.banks() <= 0) continue;
            List<int[]> line = brook.line();
            int bank = brook.width() / 2 + 2;
            double offset = bank + VALLEY_FLOOR + PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 1
                    + brook.banks() / 2.0;
            // Stretches between fords, each its own pair of sides.
            List<int[]> stretches = new ArrayList<>();
            int from = 0;
            for (int i = 0; i < line.size(); i++) {
                boolean ford = brook.isFord(i);
                if (!ford) continue;
                stretches.add(new int[]{from, i - FORD_APPROACH});
                from = i + brook.fordWidth() + FORD_APPROACH;
                i += brook.fordWidth();
            }
            stretches.add(new int[]{from, line.size() - 1});
            for (int[] stretch : stretches) {
                if (stretch[1] - stretch[0] < 8) continue;
                for (int side : new int[]{1, -1}) {
                    List<ElevationPlan.Point> path = new ArrayList<>();
                    for (int i = stretch[0]; i <= stretch[1]; i += 4) path.add(offsetPoint(line, i, side * offset));
                    path.add(offsetPoint(line, stretch[1], side * offset));
                    if (path.size() >= 2) out.add(new RidgePlanner.Band(path, brook.banks(), side, 5));
                }
            }
        }
        return out;
    }

    /** The point {@code by} cells to the left of a brook's line at step {@code i}. */
    private static ElevationPlan.Point offsetPoint(List<int[]> line, int i, double by) {
        int[] a = line.get(Math.max(0, i - 3)), b = line.get(Math.min(line.size() - 1, i + 3)), p = line.get(i);
        double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.max(1e-9, Math.hypot(dx, dy));
        return new ElevationPlan.Point(p[0] - dy / len * by, p[1] + dx / len * by);
    }

    /** Whether the ground around a layout's faces stays one connected piece once they close. */
    /**
     * Whether the faces this layout adds to {@code kept} leave the ground round
     * them connected; on yes, adds them to {@code kept}.
     */
    private static boolean keepsAccess(GenContext c, ElevationLayout layout, boolean[] kept) {
        int l = c.width, b = c.height, r = -1, t = -1;
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (!layout.isFace(x, y) || kept[y * c.width + x]) continue;
            l = Math.min(l, x); r = Math.max(r, x); b = Math.min(b, y); t = Math.max(t, y);
        }
        if (r >= 0 && !LandscapeAccess.canClose(c.grid, kept, l, b, r, t, layout::isFace)) return false;
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (layout.isFace(x, y)) kept[y * c.width + x] = true;
        }
        return true;
    }

    /**
     * Whether the ground the map's main walkable body holds stays one body once this layout's faces close and its
     * tiers part, proved over the whole map: a terrace's top joins the low ground only through its ramps, often far
     * outside any box round the faces a candidate adds, so a local proof reads every terrace as an island.
     *
     * @param main the main body before any elevation ({@link #mainBody})
     */
    static boolean keepsWholeAccess(GenContext c, ElevationLayout layout, boolean[] main) {
        int w = c.width, h = c.height;
        ElevationMap map = layout.toMap(ElevationMap.SightRule.configured());
        int start = -1, want = 0;
        for (int i = 0; i < main.length; i++) {
            if (!main[i] || layout.isFace(i % w, i / w)) continue;
            want++;
            if (start < 0) start = i;
        }
        if (start < 0) return true;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int head = 0, tail = 0, reached = 0;
        queue[tail++] = start;
        seen[start] = true;
        while (head < tail) {
            int i = queue[head++], x = i % w, y = i / w;
            if (main[i]) reached++;
            for (Direction d : Direction.CARDINALS) {
                int nx = x + d.dx, ny = y + d.dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                if (seen[n] || !c.grid.isWalkable(nx, ny) || layout.isFace(nx, ny)) continue;
                if (!c.grid.isEdgePassable(x, y, d) || !c.grid.isEdgePassable(nx, ny, d.opposite())) continue;
                if (map.separates(i, n)) continue;
                seen[n] = true;
                queue[tail++] = n;
            }
        }
        return reached == want;
    }

    /** The largest 4-connected body of walkable ground on the map as it stands. */
    static boolean[] mainBody(GenContext c) {
        int w = c.width, h = c.height;
        int[] label = new int[w * h];
        int[] queue = new int[w * h];
        int best = 0, bestSize = 0, next = 0;
        for (int s0 = 0; s0 < w * h; s0++) {
            if (label[s0] != 0 || !c.grid.isWalkable(s0 % w, s0 / w)) continue;
            int id = ++next, head = 0, tail = 0;
            queue[tail++] = s0;
            label[s0] = id;
            while (head < tail) {
                int i = queue[head++], x = i % w, y = i / w;
                for (Direction d : Direction.CARDINALS) {
                    int nx = x + d.dx, ny = y + d.dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (label[n] != 0 || !c.grid.isWalkable(nx, ny)) continue;
                    if (!c.grid.isEdgePassable(x, y, d) || !c.grid.isEdgePassable(nx, ny, d.opposite())) continue;
                    label[n] = id;
                    queue[tail++] = n;
                }
            }
            if (tail > bestSize) {
                bestSize = tail;
                best = id;
            }
        }
        boolean[] main = new boolean[w * h];
        for (int i = 0; i < main.length; i++) main[i] = best != 0 && label[i] == best;
        return main;
    }

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

    /** Rough plants a field margin grows, as tile ids; brush burns as grass does. */
    private static final String[] MARGIN_PLANTS = {"nature.grass-1", "nature.grass-3", "nature.grass-5", "nature.tuft-1", "nature.tuft-2", "nature.tuft-3", "nature.shrub-1", "nature.shrub-2"};
    /** Cells between the margin noise's lattice points: how long a stretch of edge bulges or dips together. */
    private static final int MARGIN_NOISE_CELLS = 5;

    /**
     * A ragged edge on every field: rough grass and scrub along a seeded,
     * smoothly wandering line up to {@code margin} cells either side of the
     * parcel's edge, grown out over the bare earth beside it where the line
     * bulges and eating into the parcel's own edge where it dips. A parcel is a
     * rectangle, and once a burn has taken the crop and the creep off it, it
     * shows as one, against bare earth (which does not burn, `fire-nouns.md`)
     * or wild ground alike; with this the line between them is organic. Runs
     * before the tracks are cleared, so a ride stays bare earth wherever a
     * margin reaches it.
     */
    private static void raggedMargins(GenContext c, int margin) {
        int w = c.width, h = c.height;
        TileRegistry registry = TileRegistry.installed();
        List<Integer> plants = new ArrayList<>();
        for (String id : MARGIN_PLANTS) if (registry != null && registry.has(id)) plants.add(registry.indexOf(id));
        boolean[] field = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) field[y * w + x] = c.isMadeGround(x, y) && naturalGround(c, x, y);
        // Outward: from a field's burning ground over the bare earth beside it.
        int[] out = new int[w * h];
        Arrays.fill(out, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < w * h; i++) if (field[i] && fuel(c, i % w, i / w)) { out[i] = 0; queue.add(i); }
        spread(c, out, queue, margin, n -> naturalGround(c, n % w, n / w) && !fuel(c, n % w, n / w));
        // Inward: from everything that is not a field into the parcel's own edge.
        int[] in = new int[w * h];
        Arrays.fill(in, Integer.MAX_VALUE);
        for (int i = 0; i < w * h; i++) if (!field[i]) { in[i] = 0; queue.add(i); }
        spread(c, in, queue, margin, n -> field[n]);
        long seed = c.seed ^ 0x4d415247494eL;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            float line = marginNoise(seed, x, y);
            boolean grown = out[i] > 0 && out[i] != Integer.MAX_VALUE && out[i] <= line * (margin + 1);
            boolean eaten = in[i] > 0 && in[i] != Integer.MAX_VALUE && in[i] <= (1f - line) * (margin + 1);
            if (!grown && !eaten) continue;
            c.topology.setSurface(x, y, 0);
            c.topology.setGroundKind(x, y, GroundKind.GRASS);
            long roll = mix(seed + 1, x, y);
            boolean plant = !plants.isEmpty() && Math.floorMod(roll, 3L) != 0;
            c.topology.setNatureOverlayIndex(x, y, plant ? plants.get((int) Math.floorMod(roll >>> 8, (long) plants.size())) : -1);
        }
    }

    /** Chebyshev distances out from the queued cells, over cells {@code passable} admits, up to {@code limit}. */
    private static void spread(GenContext c, int[] dist, ArrayDeque<Integer> queue, int limit, IntPredicate passable) {
        int w = c.width, h = c.height;
        while (!queue.isEmpty()) {
            int i = queue.poll(), x = i % w, y = i / w, d = dist[i] + 1;
            if (d > limit) continue;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if ((dx | dy) == 0 || nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                if (dist[n] <= d || !passable.test(n)) continue;
                dist[n] = d;
                queue.add(n);
            }
        }
    }

    /** Whether ground carries fuel of its own: grass, crop, sand, snow, or a plant on it. */
    private static boolean fuel(GenContext c, int x, int y) {
        return switch (c.topology.getGroundKind(x, y)) {
            case GRASS, SAND, SNOW -> true;
            default -> c.topology.getNatureOverlayIndex(x, y) >= 0;
        };
    }

    /** Smooth seeded value noise in [0, 1): bilinear over a lattice {@link #MARGIN_NOISE_CELLS} apart. */
    static float marginNoise(long seed, int x, int y) {
        int gx = Math.floorDiv(x, MARGIN_NOISE_CELLS), gy = Math.floorDiv(y, MARGIN_NOISE_CELLS);
        float fx = (x - gx * MARGIN_NOISE_CELLS) / (float) MARGIN_NOISE_CELLS;
        float fy = (y - gy * MARGIN_NOISE_CELLS) / (float) MARGIN_NOISE_CELLS;
        float a = lattice(seed, gx, gy), b = lattice(seed, gx + 1, gy);
        float cc = lattice(seed, gx, gy + 1), d = lattice(seed, gx + 1, gy + 1);
        float top = a + (b - a) * fx, bottom = cc + (d - cc) * fx;
        return top + (bottom - top) * fy;
    }

    private static float lattice(long seed, int x, int y) {
        return (mix(seed, x, y) >>> 40) / (float) (1L << 24);
    }

    private static long mix(long seed, int x, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    /**
     * A plan's tracks cleared to bare earth before anything grows: open natural
     * ground on a track becomes dirt and loses its shrubs. A building, a road or
     * a place on the track is left as it stands, but a field's crop rows are cut
     * through, so a track is unbroken bare earth across farmland (a firebreak).
     */
    private static void clearTracks(GenContext c, Landscape landscape) {
        for (Landscape.Track track : landscape.tracks()) {
            for (int y = Math.max(0, track.y0()); y <= Math.min(c.height - 1, track.y1()); y++)
                for (int x = Math.max(0, track.x0()); x <= Math.min(c.width - 1, track.x1()); x++) {
                    if (!naturalGround(c, x, y)) continue;
                    c.topology.setSurface(x, y, 0);
                    c.topology.setGroundKind(x, y, GroundKind.DIRT);
                    c.topology.setNatureOverlayIndex(x, y, -1);
                }
        }
    }

    /**
     * A plan's brooks run across the country: water on open natural ground
     * along each centre line, a sand strip on either bank, and at every ford
     * a stony bed right across under shallow water, waded slowly. Returns the
     * centre cells, for the reservations.
     */
    private static List<int[]> runBrooks(GenContext c, Landscape landscape) {
        List<int[]> centres = new ArrayList<>();
        for (Landscape.Brook brook : landscape.brooks()) {
            List<int[]> line = brook.line();
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
                if (!brook.isFord(i)) continue;
                int[] centre = line.get(i);
                for (int dy = -reach; dy <= reach; dy++) for (int dx = -reach; dx <= reach; dx++) {
                    int x = centre[0] + dx, y = centre[1] + dy;
                    if (x < 0 || y < 0 || x >= c.width || y >= c.height) continue;
                    if (c.topology.getGroundKind(x, y) != GroundKind.WATER) continue;
                    // A stony bed, not sand: creep over it is a film that does not burn, so a ford holds as a break.
                    c.topology.setGroundKind(x, y, GroundKind.STONE);
                    // Under shallow water: waded slowly, drawn as shallows over the stones.
                    c.topology.setTag(x, y, CellTopology.Tag.SHALLOW_WATER, true);
                    c.grid.setTag(x, y, NavigationGrid.CellTag.SHALLOW_WATER, true);
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

    /** Natural open ground under painted floor (a lawn), nothing built: what a layout's ridge may rise over. */
    private static boolean lawn(GenContext c, int x, int y) {
        return naturalGround(c, x, y) && !c.isMadeGround(x, y) && c.topology.getSurface(x, y) != 0
                && c.topology.getSurfaceId(x, y).startsWith("floors.");
    }

    private static boolean openGround(GenContext c, int x, int y) {
        return naturalGround(c, x, y) && c.topology.getSurface(x, y) == 0;
    }

    /** Walkable natural ground outside any building, door, fixture or road, whatever surface dresses it. */
    private static boolean naturalGround(GenContext c, int x, int y) {
        boolean natural = switch (c.topology.getGroundKind(x, y)) {
            case GRASS, DIRT, STONE, SAND, SNOW -> true;
            default -> false;
        };
        return natural && c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y)
                && !c.topology.isWall(x, y) && !c.topology.isFixture(x, y)
                && c.topology.getBuildingId(x, y) == 0
                && (c.topology.roadSurface() == null || c.topology.roadSurface().underlay(x, y) == null);
    }

    private static void reserve(GenContext c, boolean[] cells, int l, int b, int r, int t) {
        for (int y = Math.max(0, b); y <= Math.min(c.height - 1, t); y++)
            for (int x = Math.max(0, l); x <= Math.min(c.width - 1, r); x++) cells[y * c.width + x] = true;
    }
}
