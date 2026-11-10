package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.elevation.CrevasseTerraces;
import com.dillon.starsectormarines.battle.world.gen.nature.Flora;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.CultivatedGround;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.LavaFlows;
import com.dillon.starsectormarines.battle.world.gen.LivingWorld;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.StormBank;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.CrevasseShelter;
import com.dillon.starsectormarines.battle.world.gen.elevation.NaturalChasm;
import com.dillon.starsectormarines.battle.world.gen.elevation.OpenPit;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.fortress.RaidWorksPrograms;
import java.util.Locale;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.elevation.OreLine;
import com.dillon.starsectormarines.battle.world.gen.elevation.PlateauPlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.TerracePlanner;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.gen.forest.LandscapeAccess;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.forest.Undergrowth;
import com.dillon.starsectormarines.battle.world.gen.forest.UndergrowthBrush;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.nature.FormationBrush;
import com.dillon.starsectormarines.battle.world.gen.nature.SignatureGround;
import com.dillon.starsectormarines.battle.world.gen.nature.SludgeGround;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.precinct.KnollGround;
import com.dillon.starsectormarines.battle.world.gen.precinct.CrevasseTown;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landform;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;
import java.util.function.Function;

/** Closing outdoor pass: optional landscape yields to finished places, circulation and access. */
public final class OutdoorLandscapeStage implements GenStage {
    private final int[] hostAnchors;

    /** Fixed-layout hosts name additional spawn/staging positions not published as places yet. */
    public OutdoorLandscapeStage(int... hostAnchors) {
        if (hostAnchors.length % 2 != 0) throw new IllegalArgumentException("Expected x/y pairs");
        this.hostAnchors = hostAnchors.clone();
    }

    @Override public void run(GenContext c) {
        SurfaceField field = SurfaceField.of(c);
        // The basin's edge is drawn as a line whether or not the landscape below dresses it.
        if (!field.uniform()) c.topology.setSurfaceField(field);
        // The ground draws from the world's own art (a toxic world's stone is stained crust).
        c.topology.setGroundPalette(field.world());
        // Every lawn and lot has been laid by now; the planet decides whether it is lawn or bare earth. Before the landscape, so a hill
        // finishes from the ground it really stands on; the landscape's own margins ask the palette as they go.
        CultivatedGround.settle(c.topology, field, c::isMadeGround);
        // The planet type's own detail over its palette, for the renderer whether or not the landscape below runs.
        TargetProfile profile = c.get(BspKeys.MARKET_PROFILE);
        c.topology.setSignature(profile == null ? SurfaceSignature.NONE : profile.signature());
        landscape(c, field);
    }

    private void landscape(GenContext c, SurfaceField field) {
        if (!Boolean.parseBoolean(System.getProperty("battle.mapgen.outdoorLandscape", "true"))) return;
        if (c.width < 32 || c.height < 32 || TileRegistry.installed() == null) return;
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        Landscape landscape = plan == null ? Landscape.STOCK : plan.landscape();
        if (landscape.raggedMargin() > 0) raggedMargins(c, landscape.raggedMargin(), field);
        clearTracks(c, landscape);
        List<int[]> brookCells = runBrooks(c, landscape, field);
        boolean[] reserved = reservations(c, hostAnchors, true);
        reserveRamps(c, reserved);
        reserveTracks(c, landscape, reserved);
        // Keep crowns off the banks too: a brook is a firebreak only if nothing that burns overhangs it.
        int bank = landscape.brooks().stream().mapToInt(Landscape.Brook::width).max().orElse(3) / 2 + 2;
        for (int[] cell : brookCells) reserve(c, reserved, cell[0] - bank, cell[1] - bank, cell[0] + bank, cell[1] + bank);
        if (plan != null) reserveWalled(reserved, plan.precincts(), c.get(BspKeys.PRECINCT_CLAIM), c.width, c.height);
        openPits(c, plan, reserved);
        if (!crevasseTown(c, field, reserved)) chasm(c, field, reserved);
        // Own substream: absent nature never moves facilities, force positions or later RNG draws.
        Random random = new Random(c.seed ^ 0x574f4f444c414e44L);
        List<RidgePlanner.Band> ridges = landscapeBands(c, landscape);
        // No stock hill grows where the mission named high ground: it would be all the ridge found there.
        boolean[][] ridgeGround = ridges.isEmpty() ? null : RidgePlanner.heldGround(ridges, c.width, c.height, 0);
        SurfaceSignature signature = c.topology.signature();
        List<TerrainPlan.Stroke> signed = c.topology.terrainSurface() == null
                ? hills(c, field, reserved, ridgeGround, random, signature) : List.of();
        int plateaus = plateausWanted(c);
        if (plateaus > 0) plateaus(c, reserved, plateaus, new Random(c.seed ^ 0x504c4154L));
        if (!ridges.isEmpty() && c.grid.elevation() == null) {
            ridges(c, reserved, reserved, ridges, false, List.of(), new boolean[c.width * c.height],
                    landscape.sealed().isEmpty() ? null : mainBody(c));
        }
        // A living world's look dresses its wild grass before the woods grow on it: snow, swamp, a sea (LivingGround).
        if (field.living() != LivingWorld.TEMPERATE)
            LivingGround.dress(c, field, reserved, reservations(c, hostAnchors, false, null, 0, 0));
        // A basin's woods grow on its floor and thin out toward its rim; the rim's own palette dresses the rest.
        // A desert with no basin grows the rim's open dry scrub across the whole map.
        if (grows(field, dryScrubInstalled())) trees(c, reserved, landscape.woodland(), field);
        // Sludge pools and sulphur flats before the formations, so no boulder stands in a pool.
        SludgeGround.paint(c, field.world(), reserved);
        if (field.world() != SurfacePalette.VERDANT) FormationBrush.scatter(c, field.world(), reserved);
        // The planet type's own detail over its palette: craters and ejecta, cracks and their rubble, vents.
        int w = c.width;
        SignatureGround.dress(c.topology, c.width, c.height, c.seed, signature, signed,
                i -> !reserved[i] && openGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                field.world().stones());
        // Hills, trees and formations turn cells non-walkable after TacticalRegionStage
        // published, so refresh the artifact: "non-walkable => regionId -1" must hold.
        if (c.get(BspKeys.TACTICAL_REGIONS) != null) {
            c.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(c.grid, c.topology, c.get(BspKeys.AXIS)));
        }
    }

    /**
     * Cuts the open pit the plan's layout names ({@code voids.md}, the open pit): a ground site called
     * {@link OpenPit#SITE}, held clear of growth, from whose open ground the pit is cut before anything else is grown
     * on the country, and then reserved. A site the reservations leave too little of, or whose cut would strand
     * ground, keeps no pit. The pit sends an ore conveyor to the largest building near it, the works it feeds. A map
     * that already draws a drop or a backdrop is left as it is.
     */
    private static void openPits(GenContext c, PrecinctPlan plan, boolean[] reserved) {
        if (plan == null || plan.layout() == null || c.topology.naturalVoid() != null
                || c.topology.voidBackdrop() != null) return;
        if (!(plan.layout().find(OpenPit.SITE) instanceof Site box) || box.purpose() != SitePurpose.GROUND) return;
        int w = c.width;
        boolean[] pit = OpenPit.plan(w, c.height, box.x0(), box.y0(), box.x1(), box.y1(),
                i -> !reserved[i] && naturalGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                i -> c.grid.isWalkable(i % w, i / w), (int) c.seed);
        if (pit == null) return;
        cutDrop(c, pit, reserved);
        // The ore goes to the works' receiving house, the first step of its processing chain; a works without one
        // takes it at its largest building.
        List<List<PointOfInterest>> chain = processingChain(c, plan);
        PointOfInterest receiving = !chain.isEmpty() && chain.get(0).size() == 1
                && c.topology.getRoomPurpose(chain.get(0).get(0).interiorAnchorX, chain.get(0).get(0).interiorAnchorY)
                == RaidWorksPrograms.MINE_RECEIVING ? chain.get(0).get(0) : pitWorksBuilding(c, plan);
        float[] works = receiving == null ? null : pitWorks(receiving, box);
        NaturalVoid.Belt belt = works == null ? null : OpenPit.belt(w, c.height, pit, works[0], works[1]);
        if (belt != null) belt = oreLine(c, pit, belt, receiving, chain, reserved);
        c.topology.setNaturalVoid(OpenPit.look((int) (c.seed ^ 0x5049L), belt == null ? List.of() : List.of(belt)));
    }

    /**
     * Lays the raised ore conveyor ({@code voids.md}, the open pit; {@link OreLine}): from the first ground cell past
     * the pit's rim on the spine's line to the works' receiving house, then the works' processing chain linked in the
     * order the ore passes (receiving, stockpile, each mill line, smelter, loadout). Each link is a course on the
     * grid over open ground, roads and walls but never a building, the pit or a link already laid. Its pieces are
     * overhead props people walk under; a trestle leg every few cells stands where a solid prop blocks no road,
     * doorway or ground, and gives cover. Returns the spine, now ending at the line's first cell, where the transfer
     * tower hands its ore on; unchanged when no course reaches the works or the conveyor's art is not installed.
     */
    private static NaturalVoid.Belt oreLine(GenContext c, boolean[] pit, NaturalVoid.Belt belt,
                                            PointOfInterest receiving, List<List<PointOfInterest>> chain,
                                            boolean[] reserved) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad("ore-conveyor.straight") == null) return belt;
        int w = c.width, h = c.height;
        boolean[] blocked = new boolean[w * h];
        for (int i = 0; i < blocked.length; i++) blocked[i] = pit[i];
        for (PointOfInterest poi : c.pois) {
            for (int y = Math.max(0, poi.top); y <= Math.min(h - 1, poi.bottom); y++) {
                for (int x = Math.max(0, poi.left); x <= Math.min(w - 1, poi.right); x++) blocked[y * w + x] = true;
            }
        }
        IntPredicate passable = i -> !blocked[i] && i % w > 0 && i / w > 0 && i % w < w - 1 && i / w < h - 1;
        float dx = belt.endX() - belt.rimX(), dy = belt.endY() - belt.rimY();
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return belt;
        int sx = -1, sy = -1;
        for (float t = 0f; t <= len; t += .25f) {
            int x = (int) Math.floor(belt.rimX() + dx / len * t), y = (int) Math.floor(belt.rimY() + dy / len * t);
            if (x < 0 || y < 0 || x >= w || y >= h || pit[y * w + x]) continue;
            if (passable.test(y * w + x)) { sx = x; sy = y; }
            break;
        }
        if (sx < 0) return belt;
        // The pit's line is fed by the tower on the rim, behind its first cell.
        int fromX = (int) Math.floor(belt.rimX()), fromY = (int) Math.floor(belt.rimY());
        if (!layLink(c, registry, blocked, passable, sx, sy, fromX, fromY, receiving, reserved, true)) return belt;
        for (int k = 0; k + 1 < chain.size(); k++) {
            for (PointOfInterest from : chain.get(k)) {
                for (PointOfInterest to : chain.get(k + 1)) {
                    int[] start = beside(from, to, passable, w, h);
                    if (start == null) continue;
                    layLink(c, registry, blocked, passable, start[0], start[1], start[2], start[3], to, reserved,
                            false);
                }
            }
        }
        return new NaturalVoid.Belt(belt.floorX(), belt.floorY(), belt.rimX(), belt.rimY(), sx + .5f, sy + .5f);
    }

    /** Cells a link inside the works may run beyond twice its straight distance before it is left out. UNTUNED. */
    private static final int LINK_SLACK = 12;

    /**
     * Lays one link of the ore line from {@code (sx, sy)}, fed from {@code (fromX, fromY)}, to the wall of {@code to}:
     * its pieces, and its legs where they may stand (on the open country by the landscape's reservations, inside the
     * works off roads and doorways). Its cells are blocked to later links. False when no course reaches {@code to}.
     */
    private static boolean layLink(GenContext c, TileRegistry registry, boolean[] blocked, IntPredicate passable,
                                   int sx, int sy, int fromX, int fromY, PointOfInterest to, boolean[] reserved,
                                   boolean open) {
        int w = c.width, h = c.height;
        IntPredicate inTo = i -> i % w >= to.left && i % w <= to.right && i / w >= to.top && i / w <= to.bottom;
        IntPredicate goal = i -> passable.test(i) && ((i % w > 0 && inTo.test(i - 1))
                || (i % w < w - 1 && inTo.test(i + 1)) || (i >= w && inTo.test(i - w))
                || (i + w < w * h && inTo.test(i + w)));
        List<int[]> path = OreLine.route(w, h, sx, sy, passable, goal);
        if (path.isEmpty()) return false;
        // A link that must wander round the works to reach the next hall is no link: the halls stand apart.
        int gx = Math.max(to.left - sx, Math.max(0, sx - to.right));
        int gy = Math.max(to.top - sy, Math.max(0, sy - to.bottom));
        if (!open && path.size() > 2 * (gx + gy) + LINK_SLACK) return false;
        int[] last = path.get(path.size() - 1);
        int intoX = last[0], intoY = last[1];
        for (int d = 0; d < 4; d++) {
            int nx = last[0] + (d == 0 ? 1 : d == 2 ? -1 : 0), ny = last[1] + (d == 1 ? 1 : d == 3 ? -1 : 0);
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && inTo.test(ny * w + nx)) { intoX = nx; intoY = ny; break; }
        }
        boolean[][] roads = c.get(BspKeys.ROAD_RESERVATION);
        List<OreLine.Piece> pieces = OreLine.pieces(path, fromX, fromY, intoX, intoY, true);
        List<int[]> legs = OreLine.legs(pieces, i -> {
            int x = i % w, y = i / w;
            if (open ? reserved[i] : roads != null && roads[x][y]) return false;
            return c.grid.isWalkable(x, y) && !c.topology.isFixture(x, y)
                    && !PlacementGuards.touchesDoorway(c.grid, x, y) && !PlacementGuards.wouldStrandGround(c.grid, x, y);
        }, w);
        for (OreLine.Piece piece : pieces) {
            DoodadDef def = registry.doodad("ore-conveyor." + piece.shape().name().toLowerCase(Locale.ROOT));
            if (def == null) continue;
            c.doodads.add(new Doodad(piece.x(), piece.y(), def, piece.quarterTurns()));
            reserved[piece.y() * w + piece.x()] = true;
            blocked[piece.y() * w + piece.x()] = true;
        }
        DoodadDef leg = registry.doodad("ore-conveyor.leg");
        if (leg != null) {
            for (int[] at : legs) {
                // The leg spans the belt: across it, so a quarter turn off a run along the map's x.
                int turns = 0;
                for (OreLine.Piece piece : pieces) {
                    if (piece.x() == at[0] && piece.y() == at[1]) turns = piece.quarterTurns() % 2;
                }
                c.grid.setWalkable(at[0], at[1], false);
                c.grid.setSeeThrough(at[0], at[1], true);
                c.topology.setFixture(at[0], at[1], true);
                c.doodads.add(new Doodad(at[0], at[1], leg, turns));
            }
        }
        return true;
    }

    /**
     * Where a link from building {@code from} to building {@code to} starts: the open cell against {@code from}'s
     * wall nearest {@code to}, with the wall cell behind it that feeds it, as {@code {x, y, fromX, fromY}}; null
     * when none is open.
     */
    private static int[] beside(PointOfInterest from, PointOfInterest to, IntPredicate passable, int w, int h) {
        float tx = (to.left + to.right) / 2f, ty = (to.top + to.bottom) / 2f;
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        for (int y = from.top - 1; y <= from.bottom + 1; y++) {
            for (int x = from.left - 1; x <= from.right + 1; x++) {
                boolean side = x == from.left - 1 || x == from.right + 1, end = y == from.top - 1 || y == from.bottom + 1;
                if (side == end || x < 0 || y < 0 || x >= w || y >= h || !passable.test(y * w + x)) continue;
                float d = Math.abs(x - tx) + Math.abs(y - ty);
                if (d >= bestD) continue;
                bestD = d;
                int fx = x < from.left ? x + 1 : x > from.right ? x - 1 : x;
                int fy = y < from.top ? y + 1 : y > from.bottom ? y - 1 : y;
                best = new int[]{x, y, fx, fy};
            }
        }
        return best;
    }

    /**
     * The works' processing chain in the plan's objective, step by step in the order the ore passes
     * ({@code RaidWorksPrograms.mine}): each step the buildings whose room is that step's, nearest the objective
     * first. A step with no building is left out.
     */
    private static List<List<PointOfInterest>> processingChain(GenContext c, PrecinctPlan plan) {
        Precinct works = plan.objective();
        List<List<PointOfInterest>> out = new ArrayList<>();
        if (works == null) return out;
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        int reach = PIT_WORKS_REACH * PIT_WORKS_REACH * 2;
        for (RoomPurpose step : List.of(RaidWorksPrograms.MINE_RECEIVING, RaidWorksPrograms.MINE_STOCKPILE,
                RaidWorksPrograms.MINE_MILL, RaidWorksPrograms.MINE_SMELTER, RaidWorksPrograms.MINE_LOADOUT)) {
            List<PointOfInterest> found = new ArrayList<>();
            for (PointOfInterest poi : c.pois) {
                if (poi.kind == PointOfInterest.Kind.RESIDENTIAL) continue;
                int ax = poi.interiorAnchorX, ay = poi.interiorAnchorY;
                if (claim != null && index >= 0) {
                    boolean inside = ax >= 0 && ay >= 0 && ax < claim.length && ay < claim[0].length;
                    if (!inside || claim[ax][ay] != index) continue;
                } else {
                    int dx = ax - works.seedX(), dy = ay - works.seedY();
                    if (dx * dx + dy * dy > reach) continue;
                }
                if (c.topology.getRoomPurpose(poi.interiorAnchorX, poi.interiorAnchorY) == step) found.add(poi);
            }
            if (!found.isEmpty()) out.add(found);
        }
        return out;
    }

    /** Stamps a drop's cells: void ground, unwalkable, seen and shot across, and reserved from growth. */
    private static void cutDrop(GenContext c, boolean[] drop, boolean[] reserved) {
        int w = c.width;
        for (int i = 0; i < drop.length; i++) {
            if (!drop[i]) continue;
            int x = i % w, y = i / w;
            c.grid.setWalkable(x, y, false);
            c.grid.setTag(x, y, NavigationGrid.CellTag.SEE_THROUGH, true);
            c.grid.setTag(x, y, NavigationGrid.CellTag.NO_EDGE_COVER, true);
            c.grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
            c.topology.setGroundKind(x, y, GroundKind.VOID);
            c.topology.setSurface(x, y, 0);
            c.topology.setNatureOverlayIndex(x, y, -1);
            reserved[i] = true;
        }
    }

    /** A sheltered town's walls: how high they stand, in metres. UNTUNED. */
    static final float CREVASSE_WALL_METERS = 36f;
    /** The houses set into a sheltered town's walls, longest first. */
    private static final List<BuildingStamp> TERRACES = List.of(BuildingStamp.TERRACE_ROW, BuildingStamp.TERRACE_BLOCK);

    /**
     * Raises a sheltered town's walls ({@code voids.md}, the crevasse town): where the plan held the walls' bands
     * ({@link CrevasseTown}, placed by the map's design), so the town grew on the floor between them, terraced banks
     * on the open ground the town left there ({@link CrevasseShelter}), in the world's skin: ice on a frozen world,
     * the ravine's rock elsewhere. The walls are a rise: unwalkable, natural solid, and blocking sight as high ground
     * does. Then the town builds its terrace houses into their feet ({@link CrevasseTerraces}). A map whose plan holds
     * no walls, that already draws a drop, a rise or a backdrop, or that has too little open ground along its edges,
     * is none. Returns whether it raised them.
     */
    private boolean crevasseTown(GenContext c, SurfaceField field, boolean[] reserved) {
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        List<RidgePlanner.Band> bands = plan == null ? List.of() : CrevasseTown.wallsOf(plan.landscape());
        if (bands.isEmpty() || c.topology.naturalVoid() != null || c.topology.naturalRise() != null
                || c.topology.voidBackdrop() != null) return false;
        int w = c.width;
        // The edge band is held against growth, not against a crevasse's walls: ask again without it.
        boolean[] held = reservations(c, hostAnchors, true, null, 2, 0);
        reserveRamps(c, held);
        boolean[] walls = CrevasseShelter.plan(w, c.height, CrevasseTown.alongX(bands),
                i -> !held[i] && shelterGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                i -> shelterGround(c, i % w, i / w),
                i -> c.grid.isWalkable(i % w, i / w),
                (int) (c.seed ^ 0x4357L));
        if (walls == null) return false;
        raise(c, walls, reserved);
        int[] lengths = new int[TERRACES.size()];
        for (int k = 0; k < lengths.length; k++) lengths[k] = TERRACES.get(k).footprintWidth(0);
        boolean[] houses = new boolean[w * c.height];
        boolean built = false;
        for (CrevasseTerraces.Site site : CrevasseTerraces.plan(w, c.height, CrevasseTown.alongX(bands), walls, lengths,
                i -> !held[i] && !reserved[i] && shelterGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                i -> c.grid.isWalkable(i % w, i / w))) {
            raise(c, site.fill(), reserved);
            dig(c, site.dig());
            BuildingStamp stamp = TERRACES.get(lengths[0] == site.length() ? 0 : 1);
            stamp.place(c, site.left(), site.bottom(), site.turns());
            for (int x = site.left(); x < site.left() + stamp.footprintWidth(site.turns()); x++) {
                for (int y = site.bottom(); y < site.bottom() + stamp.footprintHeight(site.turns()); y++) {
                    reserved[y * w + x] = true;
                    houses[y * w + x] = true;
                }
            }
            built = true;
        }
        // The houses came after the town was finalized: finish them as it finished its own.
        if (built) finishLateBuildings(c, houses);
        boolean frozen = field.world() == SurfacePalette.FROZEN;
        NaturalVoidZoning.Drop skin = frozen ? NaturalVoidZoning.CREVASSE : NaturalVoidZoning.forPalette(field.world());
        c.topology.setNaturalRise(new NaturalRise(frozen ? NaturalRise.Style.ICE_WALL : NaturalRise.Style.RAVINE_WALL,
                NaturalRise.bank(CREVASSE_WALL_METERS), NaturalVoidZoning.EYE_METERS, skin.wallTexture(),
                skin.wallTileMeters(), (int) (c.seed ^ 0x4954L)));
        return true;
    }

    /**
     * Finishes buildings stamped after {@code FinalizeStage}: their walls' hit points, cover and tags, and their
     * entries in the town's buildings, numbered on so no earlier building's id moves.
     */
    private static void finishLateBuildings(GenContext c, boolean[] houses) {
        // Hit points on their own walls only: the rise round them is natural ground, not a wall to breach.
        for (int i = 0; i < houses.length; i++) {
            if (!houses[i]) continue;
            int x = i % c.width, y = i / c.width;
            if (!c.grid.isWalkable(x, y) && !c.topology.isFixture(x, y) && c.grid.getWallHp(x, y) == 0) {
                c.grid.setWallHp(x, y, FinalizeStage.WALL_HP_DEFAULT);
            }
        }
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) c.grid.recomputeCoverAt(x, y);
        c.topology.tagDefaultWalls(c.grid);
        FinalizeStage.dressUnstyledWalls(c.topology);
        WallMasks.stampUnclaimed(c.topology);
        Buildings buildings = c.get(BspKeys.BUILDINGS);
        if (buildings == null) {
            c.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(c.topology, c.seed));
        } else {
            BuildingFloodFill.populate(c.topology, c.seed, buildings);
        }
    }

    /** Gives cells of a rise back to the ground, for a building to be stamped on: no longer natural solid. */
    private static void dig(GenContext c, boolean[] cells) {
        int w = c.width;
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i]) continue;
            int x = i % w, y = i / w;
            c.grid.setTag(x, y, NavigationGrid.CellTag.NO_EDGE_COVER, false);
            c.grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, false);
            c.topology.setGroundKind(x, y, GroundKind.DIRT);
        }
    }

    /** Stamps a rise's cells: unwalkable, natural solid, blocking sight, and reserved from growth. */
    private static void raise(GenContext c, boolean[] cells, boolean[] reserved) {
        int w = c.width;
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i]) continue;
            int x = i % w, y = i / w;
            c.grid.setWalkable(x, y, false);
            c.grid.setTag(x, y, NavigationGrid.CellTag.NO_EDGE_COVER, true);
            c.grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
            c.topology.setGroundKind(x, y, GroundKind.VOID);
            c.topology.setSurface(x, y, 0);
            c.topology.setNatureOverlayIndex(x, y, -1);
            reserved[i] = true;
        }
    }

    /** The surface a crossing's footbridge is decked with. */
    static final String DECK_SURFACE = "colony.timber";

    /**
     * Cuts a natural drop across open country where the map's design placed one ({@code voids.md}, placed by design;
     * {@link Landform.Drop}): a gorge, canyon, crevasse or rift by the world's palette ({@link NaturalVoidZoning}),
     * run edge to edge along the design's line over the ground the reservations leave, every road and route crossing
     * it left standing as a bridge and natural crossings between, and proved to strand nothing ({@link NaturalChasm}).
     * After the open pit, before anything is grown. A map whose design places none, that already draws a drop or a
     * backdrop, or whose line does not fit, keeps none.
     */
    private static void chasm(GenContext c, SurfaceField field, boolean[] reserved) {
        if (c.topology.naturalVoid() != null || c.topology.voidBackdrop() != null) return;
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        Landform.Drop design = plan == null ? null : Landform.Drop.of(plan.landscape(), c.width, c.height);
        if (design == null) return;
        int w = c.width;
        NaturalChasm.Cut cut = NaturalChasm.plan(w, c.height, design.alongX(), design.across(),
                i -> !reserved[i] && naturalGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                i -> c.grid.isWalkable(i % w, i / w), (int) (c.seed ^ 0x4348L));
        if (cut == null) return;
        cutDrop(c, cut.drop(), reserved);
        NaturalVoidZoning.Drop drop = NaturalVoidZoning.forPalette(field.world());
        // Each crossing is decked as a timber footbridge. A snow bridge on ice was tried: plain snow over the
        // crevasse read as two pits with ground between them, not as a way across.
        int timber = c.topology.surfaceIndex(DECK_SURFACE);
        boolean[] decks = cut.decks();
        for (int i = 0; i < decks.length; i++) {
            if (!decks[i]) continue;
            reserved[i] = true;
            int x = i % w, y = i / w;
            c.topology.setNatureOverlayIndex(x, y, -1);
            c.topology.setGroundKind(x, y, GroundKind.DIRT);
            c.topology.setSurface(x, y, timber);
        }
        c.topology.setNaturalVoid(drop.look((int) (c.seed ^ 0x4356L), cut.drop(), w, c.height));
        // A volcanic world's lava river has broken its banks before: its older flows lie on the plain.
        if (field.world() == SurfacePalette.VOLCANIC) {
            paintFlows(c, LavaFlows.offChannel(w, c.height, cut.drop(),
                    i -> !reserved[i] && naturalGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                    i -> !decks[i] && naturalGround(c, i % w, i / w) && !terrainVisible(c, i % w, i / w),
                    (int) (c.seed ^ 0x4c46L)), reserved);
        }
    }

    /** Lays the older flows ({@link LavaFlows#heat}) and keeps the landscape's later features off them. */
    private static void paintFlows(GenContext c, boolean[] flows, boolean[] reserved) {
        int w = c.width;
        boolean[] lava = new boolean[flows.length];
        for (int i = 0; i < flows.length; i++) {
            lava[i] = c.topology.getGroundKind(i % w, i / w) == GroundKind.VOID;
            if (!flows[i]) continue;
            reserved[i] = true;
            c.topology.setNatureOverlayIndex(i % w, i / w, -1);
        }
        c.topology.setLavaFlows(LavaFlows.heat(w, c.height, flows, lava));
    }

    /**
     * Keeps the landscape's features out of every walled place ({@link Precinct.Boundary#WALLED}: a garrison's
     * cantonment, a works compound): its whole claim is reserved before the pit, the drop, the hills, the rises, the
     * ridges and the formations are laid, so no ravine, void or hill stands inside a wall. A walled place's yard is
     * made country, not open country, whatever its ground. {@code claim} is the plan's per-cell owner
     * ({@code BspKeys.PRECINCT_CLAIM}); null reserves nothing.
     */
    static void reserveWalled(boolean[] reserved, List<Precinct> precincts, int[][] claim, int width, int height) {
        if (claim == null) return;
        for (int x = 0; x < Math.min(width, claim.length); x++) {
            for (int y = 0; y < Math.min(height, claim[x].length); y++) {
                int owner = claim[x][y];
                if (owner < 0 || owner >= precincts.size()) continue;
                if (precincts.get(owner).boundary() == Precinct.Boundary.WALLED) reserved[y * width + x] = true;
            }
        }
    }

    /** Cells from the plan's objective (the works) its buildings are looked for in. UNTUNED. */
    static final int PIT_WORKS_REACH = 32;

    /**
     * The building the pit's ore goes to: the largest non-residential building within {@link #PIT_WORKS_REACH} of
     * the plan's objective (a mine's head works, whose largest building is its ore mill); null when the plan has no
     * objective or none stands near it.
     */
    private static PointOfInterest pitWorksBuilding(GenContext c, PrecinctPlan plan) {
        Precinct works = plan.objective();
        if (works == null) return null;
        PointOfInterest best = null;
        int bestArea = 0;
        int reach = PIT_WORKS_REACH * PIT_WORKS_REACH;
        for (PointOfInterest poi : c.pois) {
            if (poi.kind == PointOfInterest.Kind.RESIDENTIAL) continue;
            int dx = poi.interiorAnchorX - works.seedX(), dy = poi.interiorAnchorY - works.seedY();
            if (dx * dx + dy * dy > reach) continue;
            int area = (poi.right - poi.left + 1) * (poi.bottom - poi.top + 1);
            if (area > bestArea) { bestArea = area; best = poi; }
        }
        return best;
    }

    /** Where the pit's spine aims: the point on the mill's wall facing the pit's site. */
    private static float[] pitWorks(PointOfInterest mill, Site box) {
        float cx = (box.x0() + box.x1()) / 2f, cy = (box.y0() + box.y1()) / 2f;
        return new float[]{Math.max(mill.left, Math.min(mill.right + 1f, cx)),
                Math.max(mill.top, Math.min(mill.bottom + 1f, cy))};
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
        // The held and sealed bands and the valleys first: proved a segment at a time in order, so the ground the
        // mission holds open is taken before anything else can come within a gap of it.
        List<RidgePlanner.Band> bands = new ArrayList<>(landscape.allHeld());
        // A sheltered town's walls and a designed drop's line are held like a ridge, but raised as a rise or cut as a
        // drop, never as hills.
        bands.removeIf(band -> band.relief() == RidgePlanner.Relief.SHELTER_WALL
                || band.relief() == RidgePlanner.Relief.DROP);
        bands.addAll(valleySides(c, landscape));
        bands.addAll(landscape.ridges());
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
        if ((bands.isEmpty() && terraces.isEmpty()) || c.grid.elevation() != null || c.width < 32 || c.height < 32
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
        // A sealed ring's inside joins the country only through its passes, which may lie far from any box round a
        // segment's faces, so its segments are proved over the whole map as a terrace's are.
        boolean sealed = false;
        PrecinctPlan precincts = c.get(BspKeys.PRECINCTS);
        if (precincts != null) for (RidgePlanner.Band band : precincts.landscape().sealed()) sealed |= bands.contains(band);
        boolean[] main = terraces.isEmpty() && !sealed ? null : mainBody(c);
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
        return reservations(c, hostAnchors, routes, null, 2, 4);
    }

    /**
     * @param own cells whose made ground, doors, points of interest, nodes and props reserve only themselves rather
     *            than a margin round them (a terrace's place), or null
     */
    private static boolean[] reservations(GenContext c, int[] hostAnchors, boolean routes, boolean[] own,
                                          int roadMargin) {
        return reservations(c, hostAnchors, routes, own, roadMargin, 4);
    }

    /** @param edgeBand how many cells in from the map's edge are reserved outright: none for a sea, which lies there */
    private static boolean[] reservations(GenContext c, int[] hostAnchors, boolean routes, boolean[] own,
                                          int roadMargin, int edgeBand) {
        boolean[] reserved = new boolean[c.width * c.height];
        boolean[][] roads = c.get(BspKeys.ROAD_RESERVATION), roadCells = c.get(BspKeys.ROAD_CELLS);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (own != null && own[y * c.width + x]) {
                reserved[y * c.width + x] = true;
                continue;
            }
            if (x < edgeBand || y < edgeBand || x >= c.width - edgeBand || y >= c.height - edgeBand
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
        boolean[][] stops = approachStops(c);
        for (var pad : c.landingPads) {
            anchors.add(new int[]{pad.centerX, pad.centerY});
            int l = pad.left() - 4, r = pad.right() + 4, b = pad.bottom() - 4, t = pad.top() + 4;
            int l0 = l, r0 = r, b0 = b, t0 = t;
            switch (pad.approach) {
                case WEST -> l = 0;
                case EAST -> r = c.width - 1;
                case SOUTH -> b = 0;
                case NORTH -> t = c.height - 1;
            }
            if (stops == null) {
                reserve(c, reserved, l, b, r, t);
                continue;
            }
            // The approach is kept clear of trees and rises out to the edge, but a sealed band or a knoll stands across it.
            for (int y = Math.max(0, b); y <= Math.min(c.height - 1, t); y++)
                for (int x = Math.max(0, l); x <= Math.min(c.width - 1, r); x++)
                    if (!stops[x][y] || (x >= l0 && x <= r0 && y >= b0 && y <= t0)) reserved[y * c.width + x] = true;
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
        if (routes) LandscapeAccess.reserveRoutes(c.grid, anchors, reserved, sealedBands(c), heldBands(c));
        return reserved;
    }

    /**
     * Ground a track, and a landing's approach, stops at: the sealed ground and each standing knoll's top and ring;
     * null when there is none.
     */
    private static boolean[][] trackStops(GenContext c) {
        boolean[][] sealed = c.get(BspKeys.SEALED_GROUND);
        List<KnollGround.Resolved> knolls = c.get(BspKeys.KNOLLS);
        if (knolls == null || knolls.stream().noneMatch(KnollGround.Resolved::stands)) return sealed;
        boolean[][] out = new boolean[c.width][c.height];
        for (int x = 0; x < c.width; x++) for (int y = 0; y < c.height; y++) {
            out[x][y] = sealed != null && sealed[x][y];
            for (KnollGround.Resolved k : knolls) out[x][y] |= k.stands() && k.onGround(x, y);
        }
        return out;
    }

    /**
     * Where a landing pad's approach stops short of the edge: the {@link #trackStops}, and a storm bank's ground
     * ({@link StormBank#ground}), so an approach toward the windward edge never reserves a stretch of the bank away.
     */
    private static boolean[][] approachStops(GenContext c) {
        boolean[][] stops = trackStops(c);
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        TargetProfile profile = c.get(BspKeys.MARKET_PROFILE);
        if (plan == null || plan.landscape().kept().isEmpty() || !StormBank.raises(profile)) return stops;
        boolean[][] bank = StormBank.ground(profile.climate().wind(), c.width, c.height);
        if (stops == null) return bank;
        boolean[][] out = new boolean[c.width][c.height];
        for (int x = 0; x < c.width; x++) for (int y = 0; y < c.height; y++) out[x][y] = stops[x][y] || bank[x][y];
        return out;
    }

    /** Cells a route keeps off a sealed band's apron: the two either side of its line it reserves, and one more. */
    static final int ROUTE_CLEARANCE = 3;

    /**
     * The cells of the plan's sealed bands, their faces and aprons and {@link #ROUTE_CLEARANCE} beyond, which the
     * access routes go round
     * ({@code elevation-nouns.md}, Sealed ground); null when the landscape seals none.
     */
    private static boolean[] sealedBands(GenContext c) {
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        if (plan == null || plan.landscape().sealed().isEmpty()) return null;
        boolean[][] band = RidgePlanner.sideGround(plan.landscape().sealed(), c.width, c.height, ROUTE_CLEARANCE);
        boolean[] out = new boolean[c.width * c.height];
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) out[y * c.width + x] = band[x][y];
        return out;
    }

    /**
     * The ground across the plan's held bands, ridges and the storm bank alike, without their ends, and the two cells
     * either side a route's width reserves: what the access routes cross rather than follow, so a bank or ridge raised
     * round them keeps its passes where they were planned ({@code elevation-nouns.md}, Held ground); null when the
     * landscape holds none.
     */
    private static boolean[] heldBands(GenContext c) {
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        if (plan == null || plan.landscape().held().isEmpty()) return null;
        boolean[][] band = RidgePlanner.sideGround(plan.landscape().held(), c.width, c.height, 2);
        boolean[] out = new boolean[c.width * c.height];
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) out[y * c.width + x] = band[x][y];
        return out;
    }

    private static boolean ownCell(GenContext c, boolean[] own, int x, int y) {
        return own != null && x >= 0 && y >= 0 && x < c.width && y < c.height && own[y * c.width + x];
    }

    /**
     * Stock hills. The finish is the map's, and is the world's own where a basin greens the middle: a rock outcrop
     * reads in green country, a grass-topped hill does not on desert. Each hill's shape is its own cell's palette.
     */
    private static List<TerrainPlan.Stroke> hills(GenContext c, SurfaceField palettes, boolean[] reserved,
                                                  boolean[][] ridgeGround, Random random, SurfaceSignature signature) {
        SurfacePalette surface = palettes.world();
        TerrainPlan plan = new TerrainPlan(c.width, c.height, TerrainPlan.Finish.of(surface),
                1.8, .55, c.seed ^ 0x48494c4cL, List.of());
        boolean[] occupied = new boolean[reserved.length], closed = new boolean[reserved.length];
        int desired = Math.max(1, Math.min(10, c.width * c.height / 6000));
        for (int attempt = 0; attempt < desired * 12 && plan.strokes().size() < desired; attempt++) {
            int radius = 7 + random.nextInt(6);
            int x = 10 + random.nextInt(c.width - 20), y = 10 + random.nextInt(c.height - 20);
            var stroke = ridge(c,palettes.at(x,y),x,y,radius,attempt);
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
        // The planet type's crater rims, under the same fit and detour proof as the hills.
        List<TerrainPlan.Stroke> signed = new ArrayList<>();
        for (TerrainPlan.Stroke stroke : SignatureGround.strokes(signature, c.width, c.height, c.seed)) {
            if (signedFits(c, plan, stroke, reserved, occupied, closed, ridgeGround)) {
                plan = plan.append(stroke);
                signed.add(stroke);
            }
        }
        if (!plan.strokes().isEmpty()) TerrainBrush.stamp(plan, c.grid, c.topology, reserved);
        return signed;
    }

    /**
     * Whether a signature stroke fits as a stock hill does: its whole visible footprint, raised or cut, on open
     * ground clear of every reservation, earlier hill and named ridge, and closing nothing a route needs.
     */
    private static boolean signedFits(GenContext c, TerrainPlan plan, TerrainPlan.Stroke stroke, boolean[] reserved,
                                      boolean[] occupied, boolean[] closed, boolean[][] ridgeGround) {
        TerrainField field = TerrainField.build(new TerrainPlan(c.width, c.height, plan.finish(),
                plan.faceDepth(), plan.ruggedness(), plan.seed(), List.of(stroke)));
        double pad = stroke.radius() + 4;
        int l = Math.max(0, (int) Math.floor(stroke.points().stream().mapToDouble(TerrainPlan.Point::x).min().orElseThrow() - pad));
        int b = Math.max(0, (int) Math.floor(stroke.points().stream().mapToDouble(TerrainPlan.Point::y).min().orElseThrow() - pad));
        int r = Math.min(c.width - 1, (int) Math.ceil(stroke.points().stream().mapToDouble(TerrainPlan.Point::x).max().orElseThrow() + pad));
        int t = Math.min(c.height - 1, (int) Math.ceil(stroke.points().stream().mapToDouble(TerrainPlan.Point::y).max().orElseThrow() + pad));
        for (int yy = b; yy <= t; yy++) for (int xx = l; xx <= r; xx++) {
            if (Math.max(field.maximum(xx, yy), field.excavationMaximum(xx, yy)) < -.65) continue;
            int i = yy * c.width + xx;
            if (reserved[i] || occupied[i] || !openGround(c, xx, yy) || (ridgeGround != null && ridgeGround[xx][yy]))
                return false;
        }
        if (!LandscapeAccess.canClose(c.grid, closed, l, b, r, t, field::blocked)) return false;
        for (int yy = b; yy <= t; yy++) for (int xx = l; xx <= r; xx++) {
            int i = yy * c.width + xx;
            if (Math.max(field.maximum(xx, yy), field.excavationMaximum(xx, yy)) >= -.65) occupied[i] = true;
            if (field.blocked(xx, yy)) closed[i] = true;
        }
        return true;
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
     * @param main    with terraces or sealed bands, the map's main body, and every segment is proved over the whole
     *                map ({@link #keepsWholeAccess}); null proves each segment's faces locally, as ever
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

    /** Cells more a valley side may stand back from the water than the nearest it may, and more or less through. */
    public static final int VALLEY_STAGGER = 4, VALLEY_VARY = 2;

    /**
     * Each banked brook's valley sides: a band along either bank, offset clear of
     * the water, the sand and the bank kept free of trees, broken at every ford
     * and climbed from the country away from the water. The two sides of a
     * stretch are never a matched pair: each stands its own seeded distance back
     * from the water and is its own width through, and its natural relief
     * ({@link RidgePlanner.Relief#NATURAL}) wanders on a seed of its own.
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
            Random stagger = new Random(c.seed ^ 0x56414c4c4559L);
            for (int[] stretch : stretches) {
                if (stretch[1] - stretch[0] < 8) continue;
                for (int side : new int[]{1, -1}) {
                    int thickness = Math.max(6, brook.banks() + stagger.nextInt(2 * VALLEY_VARY + 1) - VALLEY_VARY);
                    List<ElevationPlan.Point> path = new ArrayList<>();
                    // Back far enough that the side's swell never reaches the valley floor.
                    double by = offset + (thickness - brook.banks()) / 2.0 + RidgePlanner.naturalSpread(thickness)
                            + stagger.nextInt(VALLEY_STAGGER + 1);
                    for (int i = stretch[0]; i <= stretch[1]; i += 4) path.add(offsetPoint(line, i, side * by));
                    path.add(offsetPoint(line, stretch[1], side * by));
                    if (path.size() >= 2) out.add(new RidgePlanner.Band(path, thickness, side, 5));
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
        // A volcanic world is rock's shape in other art: its craters are cinder cones (SurfacePalette.layoutOf).
        // An irradiated world's are the same crater rims, glassed; a toxic world's are bare rock's.
        if(surface.layoutOf()==SurfacePalette.ROCK || surface==SurfacePalette.IRRADIATED || surface==SurfacePalette.TOXIC) {
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
     * margin reaches it. The rough ground is the world's own: grass and plants
     * only where things grow, the world's bare ground elsewhere.
     */
    private static void raggedMargins(GenContext c, int margin, SurfaceField palettes) {
        int w = c.width, h = c.height;
        TileRegistry registry = TileRegistry.installed();
        List<Integer> plants = new ArrayList<>();
        for (String id : MARGIN_PLANTS) if (registry != null && registry.has(id)) plants.add(registry.indexOf(id));
        boolean[] laid = laidGround(c);
        boolean[] field = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            field[y * w + x] = !laid[y * w + x] && c.isMadeGround(x, y) && naturalGround(c, x, y);
        }
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
            if (!grown && !eaten || laid[i]) continue;
            c.topology.setSurface(x, y, 0);
            SurfacePalette world = palettes.at(x, y);
            c.topology.setGroundKind(x, y, world.inlandGround());
            long roll = mix(seed + 1, x, y);
            boolean plant = world == SurfacePalette.VERDANT && !plants.isEmpty() && Math.floorMod(roll, 3L) != 0;
            c.topology.setNatureOverlayIndex(x, y, plant ? plants.get((int) Math.floorMod(roll >>> 8, (long) plants.size())) : -1);
        }
    }

    /**
     * Ground a mission layout raised or worked by hand (a structure's yard, a hamlet's lane): kept as it was laid, so
     * no field's ragged margin grows over it or eats into it.
     */
    private static boolean[] laidGround(GenContext c) {
        boolean[] out = new boolean[c.width * c.height];
        LaidLayout laid = c.get(BspKeys.MISSION_LAYOUT_LAID);
        if (laid == null) return out;
        for (Laid piece : laid.pieces()) {
            if (!piece.isLaid() || piece.cells().isEmpty()) continue;
            if (piece.kind() == Laid.Kind.STRUCTURE && piece.cells().size() == 2) {
                int[] a = piece.cells().get(0), b = piece.cells().get(1);
                for (int y = Math.max(0, a[1]); y <= Math.min(c.height - 1, b[1]); y++) {
                    for (int x = Math.max(0, a[0]); x <= Math.min(c.width - 1, b[0]); x++) out[y * c.width + x] = true;
                }
            } else if (piece.kind() == Laid.Kind.BLOCK) {
                for (int[] cell : piece.cells()) {
                    if (cell[0] >= 0 && cell[1] >= 0 && cell[0] < c.width && cell[1] < c.height) out[cell[1] * c.width + cell[0]] = true;
                }
            }
        }
        return out;
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
        boolean[][] stops = trackStops(c);
        for (Landscape.Track track : landscape.tracks()) {
            for (int y = Math.max(0, track.y0()); y <= Math.min(c.height - 1, track.y1()); y++)
                for (int x = Math.max(0, track.x0()); x <= Math.min(c.width - 1, track.x1()); x++) {
                    if (!naturalGround(c, x, y)) continue;
                    // A track stops at sealed ground, crossed only at its passes, and at a knoll, climbed only at its gates.
                    if (stops != null && stops[x][y]) continue;
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
     * centre cells, for the reservations. Where the world bears no open water (a basin's dry rim) the bed is
     * a dry wash of stones instead, walked over and still a firebreak.
     */
    private static List<int[]> runBrooks(GenContext c, Landscape landscape, SurfaceField palettes) {
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
                    if (d <= r && palettes.dryRim(x, y)) {
                        // Past a basin's floor the brook is a dry wash: a stony bed, walked, that still does not burn.
                        if (brookGround(c, x, y)) {
                            c.topology.setSurface(x, y, 0);
                            c.topology.setGroundKind(x, y, GroundKind.STONE);
                            c.topology.setNatureOverlayIndex(x, y, -1);
                        }
                    } else if (d <= r) {
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
            case GRASS, DIRT, STONE, SAND, SNOW, HARDPAN -> true;
            default -> false;
        };
        return natural && c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y)
                && !c.topology.isWall(x, y) && !c.topology.isFixture(x, y)
                && c.topology.getBuildingId(x, y) == 0
                && (c.topology.roadSurface() == null || c.topology.roadSurface().underlay(x, y) == null);
    }

    /** No tree, hill or formation stands on a track or the cell beside it, short of sealed ground and knolls. */
    private static void reserveTracks(GenContext c, Landscape landscape, boolean[] reserved) {
        boolean[][] stops = trackStops(c);
        for (Landscape.Track track : landscape.tracks()) {
            if (stops == null) {
                reserve(c, reserved, track.x0() - 1, track.y0() - 1, track.x1() + 1, track.y1() + 1);
                continue;
            }
            for (int y = Math.max(0, track.y0() - 1); y <= Math.min(c.height - 1, track.y1() + 1); y++)
                for (int x = Math.max(0, track.x0() - 1); x <= Math.min(c.width - 1, track.x1() + 1); x++)
                    if (!stops[x][y]) reserved[y * c.width + x] = true;
        }
    }

    /**
     * The clearance radius a crown keeps open: a crown may close a gap infantry still pass, never one a tank could
     * drive and no longer can, unless it is undergrowth the tank flattens ({@code mapgen-nouns.md}, Authored forests).
     * Two cells, wider than a tank's own radius now that its footprint is its sprite's width: what the woods were
     * generated with when the tank was given a 3.9-cell footprint, kept so no generated map moves.
     */
    static final int TANK_CLEARANCE = 2;

    /**
     * The woods, on green ground. With a basin they grow on its floor, thinning from full {@code woodland}
     * {@link SurfaceField#WOODS_THINNING} in from the floor's edge to none at the band ({@link SurfaceField#woods}),
     * and a desert rim grows open dry scrub instead, {@link #RIM_SCRUB} as thick (a desert with no basin is all rim), drawn with the dry twins' art
     * ({@link NaturalPropShape#DRY}) where it is installed.
     */
    private static void trees(GenContext c, boolean[] reserved, float woodland, SurfaceField palettes) {
        boolean scrub = palettes.world() == SurfacePalette.ARID && dryScrubInstalled();
        // A living world's look grows its own species, as thick as it grows them (LivingWorld); green where its art is not installed.
        LivingWorld living = palettes.living();
        Flora woods = installed(living.flora()) ? living.flora() : Flora.TEMPERATE;
        woodland = Math.min(1f, woodland * living.woodland());
        boolean[] paint = new boolean[reserved.length], closed = new boolean[reserved.length];
        // Cells a tank cannot flatten: every crown that is not undergrowth.
        boolean[] hard = new boolean[reserved.length];
        PatchField groves = new PatchField(c.seed ^ 0x47524f5645L, 19);
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (reserved[y * c.width + x]) continue;
            boolean summit = ForestBrush.isGrassySummit(c.grid, c.topology, x, y);
            boolean soil = switch (c.topology.getGroundKind(x, y)) {
                case GRASS, DIRT -> true;
                default -> false;
            };
            // Exposed faces/talus remain legible; only complete summit cells carry crowns.
            float share = palettes.woods(x, y);
            if (scrub && palettes.rim(x, y)) {
                boolean dry = switch (c.topology.getGroundKind(x, y)) {
                    case SAND, DIRT -> true;
                    default -> false;
                };
                if (dry && openGround(c, x, y) && !terrainVisible(c, x, y))
                    paint[y * c.width + x] = groves.sample(x, y) < woodland * RIM_SCRUB;
                continue;
            }
            if (share <= 0f) continue;
            if (summit || (soil && openGround(c, x, y) && !terrainVisible(c, x, y)))
                paint[y * c.width + x] = groves.sample(x, y) < (summit ? Math.max(.85f, woodland) : woodland) * share;
        }
        ForestField field = ForestField.build(c.width, c.height, c.seed ^ 0x5452454553L, ForestPlan.Woodland.MIXED, paint);
        List<ForestField.Tree> accepted = new ArrayList<>();
        for (var proposed : field.trees()) {
            // A mature crown that would shut a tank's lane is replanted as undergrowth on the same spot.
            for (var tree : replants(proposed, paint, c.width, c.height)) {
                if (!fits(c, tree, closed, hard)) continue;
                accepted.add(tree);
                for (int y = tree.y(); y < tree.y() + tree.height(); y++)
                    for (int x = tree.x(); x < tree.x() + tree.width(); x++) {
                        if (!tree.blocks(x, y)) continue;
                        closed[y * c.width + x] = true;
                        if (!tree.canopy().undergrowth()) hard[y * c.width + x] = true;
                    }
                break;
            }
        }
        ForestBrush.stampTrees(accepted, c.grid, c.topology, c.doodads, reserved, true,
                tree -> scrub && palettes.rim(tree.x() + tree.width() / 2, tree.y() + tree.height() / 2) ? Flora.DRY : woods);
        undergrowth(c, paint, reserved,
                patch -> scrub && palettes.rim(patch.x() + patch.width() / 2, patch.y() + patch.height() / 2) ? Flora.DRY : woods);
    }

    /**
     * Whether the map grows anything: woods on a living floor, and on a desert open dry scrub, drawn only with the dry
     * twins' art so a desert never grows green trees. A barren or frozen world grows nothing.
     */
    static boolean grows(SurfaceField field, boolean dryArt) {
        return field.floor() == SurfacePalette.VERDANT || field.world() == SurfacePalette.ARID && dryArt;
    }

    /** How thick a desert rim's scrub grows, as a share of the floor's woodland. */
    static final float RIM_SCRUB = .35f;

    /** Whether the dry twins' art is installed: without it a rim grows no scrub rather than green trees. */
    private static boolean dryScrubInstalled() {
        return installed(Flora.DRY);
    }

    /** Whether a flora's twins' art is installed, read off its first tree. */
    private static boolean installed(Flora flora) {
        TileRegistry tiles = TileRegistry.installed();
        return tiles != null && tiles.doodad(flora.placedId(TreeCanopy.OAK)) != null;
    }

    /** Share of the lattice points left open in a wood that grow undergrowth. */
    static final float UNDERGROWTH_SHARE = .45f;
    /** How far the wood's floor reaches past its painted ground: the fringe brambles and saplings grow along. */
    static final int UNDERGROWTH_FRINGE = 2;

    /**
     * Brambles, thickets, bracken and saplings in the gaps between the crowns and along the wood's edge, up to
     * {@link #UNDERGROWTH_FRINGE} cells out from painted wood and never out in open country. Every patch
     * is undergrowth a crushing hull flattens, so it needs no tank-lane proof; infantry keep the same local way round
     * as a crown leaves them.
     */
    private static void undergrowth(GenContext c, boolean[] paint, boolean[] reserved,
                                    Function<UndergrowthBrush.Placement, Flora> dry) {
        boolean[] floor = fringe(paint, c.width, c.height, UNDERGROWTH_FRINGE);
        Random random = new Random(c.seed ^ 0x554E444552L);
        Undergrowth[] kinds = Undergrowth.values();
        boolean[] grown = new boolean[paint.length];
        List<UndergrowthBrush.Placement> placed = new ArrayList<>();
        for (int y = 1; y < c.height - 1; y += 3) for (int x = 1; x < c.width - 1; x += 3) {
            // Draw every point's dice whether or not it grows, so one refusal never reshuffles the rest.
            float roll = random.nextFloat();
            Undergrowth kind = kinds[random.nextInt(kinds.length)];
            int turns = random.nextInt(4), jx = random.nextInt(2), jy = random.nextInt(2);
            if (roll >= UNDERGROWTH_SHARE || !floor[(y + jy) * c.width + x + jx]) continue;
            var patch = new UndergrowthBrush.Placement(x + jx - kind.width(turns) / 2, y + jy - kind.height(turns) / 2,
                    kind, turns);
            if (!UndergrowthBrush.fits(patch, c.grid, c.topology, reserved) || !inWood(patch, floor, c.width)) continue;
            if (!LandscapeAccess.canClose(c.grid, grown, patch.x(), patch.y(), patch.x() + patch.width() - 1,
                    patch.y() + patch.height() - 1, patch::blocks)) continue;
            placed.add(patch);
            for (int yy = patch.y(); yy < patch.y() + patch.height(); yy++)
                for (int xx = patch.x(); xx < patch.x() + patch.width(); xx++)
                    if (patch.blocks(xx, yy)) grown[yy * c.width + xx] = true;
        }
        UndergrowthBrush.stamp(placed, c.grid, c.topology, c.doodads, reserved, dry);
    }

    /** Cells within {@code reach} (Chebyshev) of painted wood. */
    private static boolean[] fringe(boolean[] paint, int width, int height, int reach) {
        boolean[] out = new boolean[paint.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (!paint[y * width + x]) continue;
            for (int yy = Math.max(0, y - reach); yy <= Math.min(height - 1, y + reach); yy++)
                for (int xx = Math.max(0, x - reach); xx <= Math.min(width - 1, x + reach); xx++) out[yy * width + xx] = true;
        }
        return out;
    }

    private static boolean inWood(UndergrowthBrush.Placement patch, boolean[] paint, int width) {
        for (int y = patch.y(); y < patch.y() + patch.height(); y++)
            for (int x = patch.x(); x < patch.x() + patch.width(); x++)
                if (patch.blocks(x, y) && !paint[y * width + x]) return false;
        return true;
    }

    /** Infantry keep a way round the crown, and a tank keeps its lane unless the crown is undergrowth it flattens. */
    private static boolean fits(GenContext c, ForestField.Tree tree, boolean[] closed, boolean[] hard) {
        boolean closes = false;
        for (int y = tree.y(); y < tree.y() + tree.height(); y++)
            for (int x = tree.x(); x < tree.x() + tree.width(); x++)
                if (tree.blocks(x, y) && c.grid.isWalkable(x, y) && !closed[y * c.width + x]) closes = true;
        if (!closes) return true;
        int right = tree.x() + tree.width() - 1, top = tree.y() + tree.height() - 1;
        if (!LandscapeAccess.canClose(c.grid, closed, tree.x(), tree.y(), right, top, tree::blocks)) return false;
        return tree.canopy().undergrowth()
                || LandscapeAccess.keepsClearance(c.grid, hard, tree.x(), tree.y(), right, top, tree::blocks, TANK_CLEARANCE);
    }

    /**
     * The crown as packed, then, when it is not undergrowth, each undergrowth canopy centred where it stood, smallest
     * last, kept only where every cell it closes was painted.
     */
    static List<ForestField.Tree> replants(ForestField.Tree tree, boolean[] paint, int width, int height) {
        List<ForestField.Tree> out = new ArrayList<>();
        out.add(tree);
        if (tree.canopy().undergrowth()) return out;
        int cx = tree.x() + tree.width() / 2, cy = tree.y() + tree.height() / 2;
        List<TreeCanopy> young = new ArrayList<>();
        for (TreeCanopy canopy : TreeCanopy.values()) if (canopy.undergrowth()) young.add(canopy);
        young.sort(Comparator.comparingInt((TreeCanopy canopy) -> canopy.width() * canopy.height()).reversed());
        for (TreeCanopy canopy : young) {
            var replant = new ForestField.Tree(cx - canopy.width(tree.turns()) / 2, cy - canopy.height(tree.turns()) / 2,
                    canopy, tree.turns());
            if (painted(replant, paint, width, height)) out.add(replant);
        }
        return out;
    }

    private static boolean painted(ForestField.Tree tree, boolean[] paint, int width, int height) {
        if (tree.x() < 0 || tree.y() < 0 || tree.x() + tree.width() > width || tree.y() + tree.height() > height) return false;
        for (int y = tree.y(); y < tree.y() + tree.height(); y++)
            for (int x = tree.x(); x < tree.x() + tree.width(); x++)
                if (tree.blocks(x, y) && !paint[y * width + x]) return false;
        return true;
    }

    private static boolean terrainVisible(GenContext c, int x, int y) {
        var terrain = c.topology.terrainSurface();
        if (terrain == null) return false;
        for (int layer = 0; layer < TerrainSurface.LAYERS; layer++) if (terrain.patch(x, y, layer) != null) return true;
        return false;
    }

    /** Natural open ground under painted floor (a lawn), nothing built: what a layout's ridge may rise over. */
    private static boolean lawn(GenContext c, int x, int y) {
        if (!naturalGround(c, x, y) || c.isMadeGround(x, y) || c.topology.getSurface(x, y) == 0) return false;
        // A world's own ground block (a toxic world's crust) is that world's earth, as floors.* is everyone else's.
        String id = c.topology.getSurfaceId(x, y);
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        return id.startsWith("floors.") || mapping != null && mapping.isPaletteGroundBlock(id);
    }

    private static boolean openGround(GenContext c, int x, int y) {
        return naturalGround(c, x, y) && c.topology.getSurface(x, y) == 0;
    }

    /**
     * Ground a crevasse's wall may stand on: walkable open ground outside any building, door, fixture or road, a
     * yard's or a verge's as well as the wild's. Paved and built ground is the town's.
     */
    private static boolean shelterGround(GenContext c, int x, int y) {
        boolean open = switch (c.topology.getGroundKind(x, y)) {
            case GRASS, DIRT, STONE, SAND, SNOW, COURTYARD, RUBBLE -> true;
            default -> false;
        };
        return open && c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y)
                && !c.topology.isWall(x, y) && !c.topology.isFixture(x, y)
                && c.topology.getBuildingId(x, y) == 0
                && (c.topology.roadSurface() == null || c.topology.roadSurface().underlay(x, y) == null);
    }

    /** Walkable natural ground outside any building, door, fixture or road, whatever surface dresses it. */
    private static boolean naturalGround(GenContext c, int x, int y) {
        boolean natural = switch (c.topology.getGroundKind(x, y)) {
            case GRASS, DIRT, STONE, SAND, SNOW, HARDPAN -> true;
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
