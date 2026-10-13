package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.fort.FortTag;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortSiting;
import com.dillon.starsectormarines.battle.world.gen.FortSitingPolicy;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationGround;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.DepthBand;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegion;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RockDamage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Stands exactly the forts the mission declared ({@link GroundDefences#fortCount(FortKind)}), of every kind, and finds
 * ground for them. Each kind is an authored {@link FortPlan} ({@link BatteryFort}, {@link ShieldFort}); the placement,
 * stamping and gate-linking here are common to all of them and the plan supplies the geometry. Like {@link FortressBatteryStage} it never reads the market and never derives a count from the map: with no
 * declaration it places nothing and draws nothing, and the map is byte-identical to one generated without it.
 *
 * <p>A fort is an authored plan's layout placed on open ground at one of four turns. The city is
 * prior: a site must be open outdoor ground, off every road, doorway, authored infantry place, landing berth and
 * existing structure, with a margin of clear ground all round so the fort can never cut the walkable map in two.
 * Within that, sites are taken deep band first, then nearest the map centre, and the turn is the one that points the
 * main gate most nearly at the centre. Nothing here draws a random number.
 *
 * <p>Each gate is paved out to the nearest road when an open-ground route there exists, so the fort is reached the way
 * any other place is. The painted lane is ground surface only: it is not added to the published road graph, which the
 * convoys read and which a fort is no part of.
 *
 * <p>The stage reuses the compound machinery rather than a parallel one: buildings are shelled, windowed and given
 * their doors by {@link FortressInterior}, and the perimeter is dressed by {@link InstallationWalls}. Their core
 * structures, the battery platform and the nests are emitted as {@link DefensePost}s carrying a {@link FortTag}, so the
 * ordinary spawn path stands them up and the simulation assembles the fort from what was stamped.
 */
public final class FortStage implements GenStage {

    /** Hit points of a wall cell of the fort's outer wall: breachable by demolition, not by small arms. */
    public static final int FORT_WALL_HP = BatteryFort.WALL_HP;
    /** Hit points of a wall cell of a fort building. */
    public static final int BUILDING_WALL_HP = 300;

    /** Clear ground required all round a fort, in cells. */
    private static final int MARGIN = 2;
    /** Minimum distance between two forts' centres, in cells. */
    private static final int FORT_SEPARATION = 66;
    private static final int SCAN_STRIDE = 3;
    /** Longest open-ground route to the road network worth paving, in cells. */
    private static final int MAX_LINK_CELLS = 140;
    private static final EnumSet<DepthBand> ALLOWED_BANDS = EnumSet.of(DepthBand.MID, DepthBand.DEEP, DepthBand.UNSET);
    private static final EnumSet<GroundKind> REFUSED_GROUND = EnumSet.of(GroundKind.INDOOR, GroundKind.WATER,
            GroundKind.TILE, GroundKind.STREET, GroundKind.LZ_MARKER, GroundKind.VOID, GroundKind.SIDEWALK,
            GroundKind.STRIPED);
    /** UNTUNED: how much a shield fort values covering the keep against a supply compound. */
    private static final int KEEP_WEIGHT = 6;
    private static final int SUPPLY_WEIGHT = 2;
    private static final String[] NEST_STRUCTURES = {"structure.turret-heavy-mg", "structure.turret-dual-flak"};

    /** A candidate centre with the order it is taken in. */
    private record Site(int x, int y, int band, long centreDistSq) {}

    /** The kinds this stage places, in the order they are placed (and so numbered). */
    private static final List<FortPlan> PLANS = List.of(BatteryFort.PLAN, ShieldFort.PLAN);

    /** Where a fort stands, for evidence. */
    public record Placed(CompoundLayout.Placement placement, int fortId, FortPlan plan, FortSiting.Point target) {}

    private final List<Placed> placed = new ArrayList<>();

    public FortStage() {}

    /** The forts this stage stood on its last run, in the order they were placed. */
    public List<Placed> placed() {
        return placed;
    }

    @Override
    public void run(GenContext ctx) {
        placed.clear();
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        if (profile == null) return;
        boolean any = false;
        for (FortPlan plan : PLANS) any |= profile.declaredDefences().fortCount(plan.kind()) > 0;
        if (!any) return;
        TacticalRegionMap regions = ctx.get(BspKeys.TACTICAL_REGIONS);
        if (regions == null) {
            throw new IllegalStateException(
                    "FortStage requires TACTICAL_REGIONS - must run after TacticalRegionStage");
        }
        Ground ground = new Ground(ctx);
        List<Site> sites = candidates(ctx, regions);
        sites.sort(Comparator.comparingInt(Site::band).thenComparingLong(Site::centreDistSq)
                .thenComparingInt(Site::y).thenComparingInt(Site::x));

        FortSiting.Anchors anchors = profile.declaredDefences().siting() == FortSitingPolicy.COVER_THE_APPROACH
                ? anchorsOf(ctx) : null;
        List<FortSiting.Valued> garrison = anchors != null ? garrisonValues(ctx) : List.of();

        for (FortPlan plan : PLANS) {
            int budget = profile.declaredDefences().fortCount(plan.kind());
            if (budget <= 0) continue;
            CompoundLayout layout = plan.layout();
            List<FortSiting.Point> targets = anchors == null ? null : targetsFor(plan, anchors, budget);
            for (int n = 0; n < budget; n++) {
                FortSiting.Point target = targets == null ? null : targets.get(n);
                Placed made = null;
                if (target != null) {
                    made = placeNear(plan, layout, ground, sitedFor(plan, sites, anchors, target, garrison), ctx, anchors, target);
                }
                // The declared count stands where ground allows: a fort whose intended ground is taken falls back to
                // the leftover order rather than vanishing.
                if (made == null) made = placeNear(plan, layout, ground, sites, ctx, anchors, null);
                if (made == null) break;
            }
        }
        if (placed.isEmpty()) return;
        finish(ctx);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, ctx.get(BspKeys.AXIS)));
    }

    // ------------------------------------------------------------------------------------------ site

    /**
     * The first site in {@code order} where a fort of this plan can stand, stamped and recorded; {@code null} when none
     * fits. {@code target} is the ground a siting intended, kept for evidence; {@code null} for leftover ground.
     */
    private Placed placeNear(FortPlan plan, CompoundLayout layout, Ground ground, List<Site> order, GenContext ctx,
                             FortSiting.Anchors anchors, FortSiting.Point target) {
        FortSiting.Point facing = anchors == null ? null : anchors.landing();
        for (Site site : order) {
            if (tooClose(site)) continue;
            CompoundLayout.Placement at = fit(plan, layout, ground, site, ctx, facing);
            if (at == null) continue;
            int fortId = placed.size();
            stamp(ctx, plan, at, fortId, ground);
            ground.exclude(plan, at);
            Placed made = new Placed(at, fortId, plan, target);
            placed.add(made);
            return made;
        }
        return null;
    }

    /** The candidate sites in the order a siting intent wants them: a battery fort nearest its target, a shield fort over the most. */
    private static List<Site> sitedFor(FortPlan plan, List<Site> sites, FortSiting.Anchors anchors,
                                       FortSiting.Point target, List<FortSiting.Valued> garrison) {
        List<Site> order = new ArrayList<>(sites);
        Comparator<Site> byDistance = Comparator.comparingLong((Site s) -> {
            long dx = s.x() - target.x();
            long dy = s.y() - target.y();
            return dx * dx + dy * dy;
        }).thenComparingInt(Site::y).thenComparingInt(Site::x);
        if (plan.kind() == FortKind.SHIELD_FORT) {
            float radius = TurretCatalogRegistry.requireStructure(plan.centrepieceStructureId()).dome.radiusCells();
            Map<Site, Integer> covered = new IdentityHashMap<>();
            for (Site s : order) covered.put(s, FortSiting.domeCoverage(garrison, s.x(), s.y(), radius));
            order.sort(Comparator.comparingInt((Site s) -> -covered.get(s)).thenComparing(byDistance));
        } else {
            // Sliding along the approach is cheaper than leaving it, so a fort stays a barrier where its exact ground is taken.
            order.sort(Comparator.comparingLong((Site s) -> FortSiting.approachCost(anchors, target, s.x(), s.y()))
                    .thenComparing(byDistance));
        }
        return order;
    }

    private static List<FortSiting.Point> targetsFor(FortPlan plan, FortSiting.Anchors anchors, int count) {
        if (plan.kind() == FortKind.BATTERY_FORT) return FortSiting.batteryTargets(anchors, count);
        List<FortSiting.Point> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(FortSiting.shieldTarget(anchors));
        return out;
    }

    /**
     * The battle's two ends as the plan stated them: the keep (the command post, the one nearest the objective place when
     * there is more than one) and the landing place. {@code null} when the map states either no landing or no keep, in
     * which case a siting policy has nothing to aim at and forts take leftover ground.
     */
    static FortSiting.Anchors anchorsOf(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        if (plan == null) return null;
        Precinct landing = plan.landingPlace();
        if (landing == null) return null;
        Precinct objective = plan.objective();
        TacticalNode keep = null;
        long best = Long.MAX_VALUE;
        for (TacticalNode node : ctx.tactical) {
            if (node.kind != TacticalNode.Kind.COMMAND_POST) continue;
            long dx = objective == null ? 0 : node.anchorX - objective.seedX();
            long dy = objective == null ? 0 : node.anchorY - objective.seedY();
            long d = dx * dx + dy * dy;
            if (keep == null || d < best) {
                keep = node;
                best = d;
            }
        }
        if (keep == null) return null;
        return new FortSiting.Anchors(new FortSiting.Point(keep.anchorX, keep.anchorY),
                new FortSiting.Point(landing.seedX(), landing.seedY()), ctx.width, ctx.height);
    }

    /** The garrison's places a dome should cover, weighted: the keep most, then the supply compounds. */
    private static List<FortSiting.Valued> garrisonValues(GenContext ctx) {
        List<FortSiting.Valued> out = new ArrayList<>();
        for (TacticalNode node : ctx.tactical) {
            int weight = switch (node.kind) {
                case COMMAND_POST -> KEEP_WEIGHT;
                case BARRACKS, ARMORY, AIRBASE -> SUPPLY_WEIGHT;
                default -> 0;
            };
            if (weight > 0) out.add(new FortSiting.Valued(node.centerX(), node.centerY(), weight));
        }
        return out;
    }

    private boolean tooClose(Site site) {
        long sq = (long) FORT_SEPARATION * FORT_SEPARATION;
        for (Placed p : placed) {
            int[] c = p.placement().cell(p.plan().width() / 2, p.plan().height() / 2);
            long dx = c[0] - site.x();
            long dy = c[1] - site.y();
            if (dx * dx + dy * dy < sq) return true;
        }
        return false;
    }

    private static List<Site> candidates(GenContext ctx, TacticalRegionMap regions) {
        List<Site> out = new ArrayList<>();
        float cx = ctx.width * 0.5f;
        float cy = ctx.height * 0.5f;
        for (int y = 0; y < ctx.height; y += SCAN_STRIDE) {
            for (int x = 0; x < ctx.width; x += SCAN_STRIDE) {
                TacticalRegion region = regions.regionAt(x, y);
                if (region != null && !ALLOWED_BANDS.contains(region.depthBand)) continue;
                int band = region != null && region.depthBand == DepthBand.DEEP ? 0 : 1;
                long dx = Math.round(x - cx);
                long dy = Math.round(y - cy);
                out.add(new Site(x, y, band, dx * dx + dy * dy));
            }
        }
        return out;
    }

    /**
     * The placement of the layout centred on {@code site} at the first turn, ordered by how nearly the main gate faces
     * the map centre, whose whole box and margin is open ground; {@code null} when no turn fits.
     */
    private static CompoundLayout.Placement fit(FortPlan plan, CompoundLayout layout, Ground ground, Site site,
                                                GenContext ctx, FortSiting.Point facing) {
        int[][] order = turnsByGateFacing(plan, layout, ctx, site, facing);
        for (int[] turn : order) {
            int q = turn[0];
            int w = q % 2 == 0 ? plan.width() : plan.height();
            int h = q % 2 == 0 ? plan.height() : plan.width();
            int x = site.x() - w / 2;
            int y = site.y() - h / 2;
            if (x < 0 || y < 0 || x + w > ctx.width || y + h > ctx.height) continue;
            CompoundLayout.Placement at = layout.at(x, y, q);
            // The works and the glacis round them need clear ground; the belt beyond follows whatever is open.
            if (ground.clear(plan, at, facing != null)) return at;
        }
        return null;
    }

    /** Turns ordered by the main gate's outward direction against the direction to the map centre, best first. */
    private static int[][] turnsByGateFacing(FortPlan plan, CompoundLayout layout, GenContext ctx, Site site,
                                              FortSiting.Point facing) {
        double toCentreX = (facing != null ? facing.x() : ctx.width * 0.5) - site.x();
        double toCentreY = (facing != null ? facing.y() : ctx.height * 0.5) - site.y();
        double length = Math.hypot(toCentreX, toCentreY);
        if (length > 0) {
            toCentreX /= length;
            toCentreY /= length;
        }
        int[][] order = new int[4][2];
        for (int q = 0; q < 4; q++) {
            var at = layout.at(0, 0, q);
            FortPlan.Gate main = plan.gates().get(0);
            int[] inside = at.cell(main.mouthX(), main.mouthY());
            int[] outside = at.cell(main.mouthX() + main.outX(), main.mouthY() + main.outY());
            double dot = (outside[0] - inside[0]) * toCentreX + (outside[1] - inside[1]) * toCentreY;
            order[q][0] = q;
            order[q][1] = (int) Math.round(-dot * 1000);
        }
        Arrays.sort(order, Comparator.<int[]>comparingInt(t -> t[1]).thenComparingInt(t -> t[0]));
        return order;
    }

    /** Which ground a fort may stand on, as a summed-area table so a rectangle is one lookup. */
    private static boolean looseStone(Doodad doodad) {
        String id = doodad.definitionId();
        return id != null && RockDamage.IDS.contains(id);
    }

    /** Takes loose stone off every cell the predicate claims, leaving open floor where it stood. */
    private static void levelRocks(GenContext ctx, BiPredicate<Integer, Integer> claimed) {
        List<Doodad> gone = new ArrayList<>();
        for (Doodad d : ctx.doodads) {
            if (!looseStone(d)) continue;
            boolean hit = false;
            for (int dx = 0; dx < d.footprintCellsX && !hit; dx++) {
                for (int dy = 0; dy < d.footprintCellsY && !hit; dy++) hit = claimed.test(d.cellX + dx, d.cellY + dy);
            }
            if (!hit) continue;
            gone.add(d);
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                for (int dy = 0; dy < d.footprintCellsY; dy++) {
                    int x = d.cellX + dx, y = d.cellY + dy;
                    if (!ctx.grid.inBounds(x, y) || ctx.topology.isWall(x, y)) continue;
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.topology.setFixture(x, y, false);
                }
            }
        }
        ctx.doodads.removeAll(gone);
    }

    private static final class Ground {
        private final int width;
        private final int height;
        private final boolean[][] bad;
        private final boolean[][] rock;
        /** Cells refused only because a road crosses them: a sited fort lets a road run through its glacis. */
        private final boolean[][] roadOnly;
        private int[][] sums;
        private boolean dirty = true;

        Ground(GenContext ctx) {
            width = ctx.width;
            height = ctx.height;
            bad = new boolean[width][height];
            rock = new boolean[width][height];
            roadOnly = new boolean[width][height];
            // Loose stone is scatter, not ground the city holds: a fort levels it. Mountains and shelves stay.
            for (Doodad doodad : ctx.doodads) {
                if (!looseStone(doodad)) continue;
                for (int dx = 0; dx < doodad.footprintCellsX; dx++) {
                    for (int dy = 0; dy < doodad.footprintCellsY; dy++) {
                        int rx = doodad.cellX + dx, ry = doodad.cellY + dy;
                        if (rx < width && ry < height && !ctx.grid.isWalkable(rx, ry) && ctx.topology.isFixture(rx, ry)
                                && !ctx.topology.isWall(rx, ry)) {
                            rock[rx][ry] = true;
                        }
                    }
                }
            }
            NavigationGrid grid = ctx.grid;
            CellTopology topology = ctx.topology;
            boolean[][] road = ctx.get(BspKeys.ROAD_RESERVATION);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    boolean levelled = rock[x][y];
                    boolean refuse = (!levelled && (!grid.isWalkable(x, y) || topology.isFixture(x, y)))
                            || REFUSED_GROUND.contains(topology.getGroundKind(x, y))
                            || topology.isWall(x, y)
                            || PlacementGuards.touchesDoorway(grid, x, y);
                    boolean onRoad = road != null && road[x][y];
                    bad[x][y] = refuse || onRoad;
                    roadOnly[x][y] = onRoad && !refuse;
                }
            }
            int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
            if (claim != null) {
                // A place the plan has claimed is prior: the fort stands in the open country between them.
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) if (claim[x][y] != GrownTrunkPlan.UNOWNED) take(x, y);
                }
            }
            for (TacticalNode node : ctx.tactical) {
                markBox(node.left - MARGIN, node.top - MARGIN, node.right + MARGIN, node.bottom + MARGIN);
            }
            for (LandingPad pad : ctx.landingPads) {
                markBox(pad.left() - 8, pad.bottom() - 8, pad.right() + 8, pad.top() + 8);
            }
            for (DefensePost post : ctx.defensePosts) {
                markBox(post.anchorX - 4, post.anchorY - 4, post.anchorX + 4, post.anchorY + 4);
            }
            for (var poi : ctx.pois) {
                markBox(poi.left - MARGIN, poi.top - MARGIN, poi.right + MARGIN, poi.bottom + MARGIN);
            }
        }

        private void markBox(int x0, int y0, int x1, int y1) {
            for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++) {
                for (int y = Math.max(0, y0); y <= Math.min(height - 1, y1); y++) take(x, y);
            }
            dirty = true;
        }

        /** Takes a placed fort's whole footprint, belt included, and a margin: forts never stand flush. */
        void exclude(FortPlan plan, CompoundLayout.Placement at) {
            int[] a = at.cell(0, 0);
            int[] b = at.cell(plan.width() - 1, plan.height() - 1);
            markBox(Math.min(a[0], b[0]) - MARGIN, Math.min(a[1], b[1]) - MARGIN,
                    Math.max(a[0], b[0]) + MARGIN, Math.max(a[1], b[1]) + MARGIN);
        }

        /** Whether the cell is loose stone this fort may level. */
        boolean isRock(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && rock[x][y];
        }

        /** Whether one cell is open ground: in bounds and not taken by the city, a road or another fort. */
        boolean ok(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && !bad[x][y];
        }

        /** Takes one cell, so no later fort's belt can be drawn over it. */
        void take(int x, int y) {
            if (x >= 0 && y >= 0 && x < width && y < height) {
                bad[x][y] = true;
                roadOnly[x][y] = false;
                dirty = true;
            }
        }

        /** Whether every cell of the works and their glacis is open ground when the layout is placed as given. */
        boolean clear(FortPlan plan, CompoundLayout.Placement at, boolean roadsInGlacis) {
            for (int[] local : plan.clearCells()) {
                int[] cell = at.cell(local[0], local[1]);
                if (ok(cell[0], cell[1])) continue;
                // A road may cross the glacis, never the works: the fort stands beside the road and its gates meet it.
                if (roadsInGlacis && !plan.isWorks(local[0], local[1]) && roadOnly(cell[0], cell[1])) continue;
                return false;
            }
            return true;
        }

        private boolean roadOnly(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && roadOnly[x][y];
        }

        boolean clear(int x, int y, int w, int h) {
            if (x < 0 || y < 0 || x + w > width || y + h > height) return false;
            if (dirty) rebuild();
            int bads = sums[x + w][y + h] - sums[x][y + h] - sums[x + w][y] + sums[x][y];
            return bads == 0;
        }

        private void rebuild() {
            sums = new int[width + 1][height + 1];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    sums[x + 1][y + 1] = sums[x][y + 1] + sums[x + 1][y] - sums[x][y] + (bad[x][y] ? 1 : 0);
                }
            }
            dirty = false;
        }
    }

    // ----------------------------------------------------------------------------------------- stamp

    static void stamp(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, int fortId) {
        stamp(ctx, plan, at, fortId, null);
    }

    /**
     * Stamps one fort from the outside in: the works and their glacis first, then the buildings, structures and wall,
     * the ground and interior dressing, and last the belt, which takes only the cells the ground will give it. A null
     * {@code ground} means open country everywhere, which is what a bare test field is.
     */
    private static void stamp(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, int fortId, Ground ground) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        int w = plan.width();
        int h = plan.height();
        boolean[][] cleared = new boolean[ctx.width][ctx.height];
        for (int[] local : plan.clearCells()) {
            int[] cell = at.cell(local[0], local[1]);
            if (ctx.grid.inBounds(cell[0], cell[1])) cleared[cell[0]][cell[1]] = true;
        }
        // The glacis is open ground: nothing of the country's scatter stands in front of the works.
        levelRocks(ctx, (x, y) -> ctx.grid.inBounds(x, y) && cleared[x][y]);
        ctx.doodads.removeIf(d -> ctx.grid.inBounds(d.cellX, d.cellY) && cleared[d.cellX][d.cellY]);

        // The ground: every cell of the works starts as bare earth; aprons and lanes are paved over it below.
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                if (!plan.isWorks(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                grid.setWalkableFloor(cell[0], cell[1]);
                topology.setNatureOverlayIndex(cell[0], cell[1], -1);
                topology.setGroundKind(cell[0], cell[1], GroundKind.DIRT);
                // No surface yet: a wall ring is dressed only where nobody has given the cell one, so the paving
                // goes down last, over what is still open court.
                topology.setSurface(cell[0], cell[1], 0);
            }
        }

        // The buildings: shelled, windowed and doored by the compound machinery, left bare for their cores.
        List<RoomPacker.Placed> rooms = at.buildings();
        FortressInterior.author(ctx, rooms, rooms);
        for (RoomPacker.Placed room : rooms) {
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (grid.isWalkable(x, y)) continue;
                // Finalize tags walls from what is not walkable, and it has run; here that would also tag the
                // structures, so the building rings are tagged by hand.
                grid.setWallHp(x, y, BUILDING_WALL_HP);
                topology.setWall(x, y, true);
            }
        }

        // The structures: one core in each aspect building, the fort's centrepiece, and a nest in each bastion.
        for (FortAspect aspect : plan.aspects()) {
            int[] centre = plan.coreCentre(aspect);
            emitStructure(ctx, plan.coreStructureId(aspect), at.cell(centre[0], centre[1]), new FortTag(fortId, aspect, at.x(), at.y(), at.pose().quarterTurns()));
        }
        int[] centrepiece = plan.centrepiece();
        emitStructure(ctx, plan.centrepieceStructureId(), at.cell(centrepiece[0], centrepiece[1]),
                new FortTag(fortId, plan.centrepieceAspect(), at.x(), at.y(), at.pose().quarterTurns()));
        int nestNumber = 0;
        for (int[] local : plan.nests()) {
            emitNest(ctx, at.cell(local[0], local[1]), fortId, at, nestNumber++);
        }

        stampWall(ctx, plan, at);

        // The ground: compacted yard inside the wall with concrete aprons round every building, and paving only on
        // the lanes vehicles actually drive. Marked as made ground once dressed so the landscape stages leave it alone.
        boolean[][] yard = new boolean[ctx.width][ctx.height];
        boolean[][] lane = new boolean[ctx.width][ctx.height];
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                if (!plan.isWorks(lx, ly) || plan.isWall(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                yard[cell[0]][cell[1]] = true;
            }
        }
        InstallationGround.dress(ctx, yard, new boolean[ctx.width][ctx.height], rooms);
        for (int[] rect : plan.lanes()) {
            for (int dx = 0; dx < rect[2]; dx++) {
                for (int dy = 0; dy < rect[3]; dy++) {
                    int[] cell = at.cell(rect[0] + dx, rect[1] + dy);
                    if (grid.inBounds(cell[0], cell[1]) && grid.isWalkable(cell[0], cell[1])) {
                        pave(ctx, cell[0], cell[1]);
                        lane[cell[0]][cell[1]] = true;
                    }
                }
            }
        }
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                if (!plan.isWorks(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                ctx.markMadeGround(cell[0], cell[1]);
                // The gate cells are on the wall, outside the dressed yard: they take the paving.
                if (!yard[cell[0]][cell[1]] && grid.isWalkable(cell[0], cell[1])) pave(ctx, cell[0], cell[1]);
            }
        }
        paveGates(ctx, plan, at);
        dressInterior(ctx, plan, at, lane);
        stampBelt(ctx, plan, at, fortId, ground);
    }

    private static void emitNest(GenContext ctx, int[] cell, int fortId, CompoundLayout.Placement at, int number) {
        ctx.grid.setWalkable(cell[0], cell[1], false);
        ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, cell[0], cell[1],
                List.of(new DefensePost.TurretSpec(NEST_STRUCTURES[number % NEST_STRUCTURES.length], cell[0], cell[1])),
                new FortTag(fortId, null, at.x(), at.y(), at.pose().quarterTurns())));
    }

    /**
     * The outer belt: trench, sandbag line, wire and nests, drawn only on cells the ground gives up. A cell the city
     * holds is skipped, so the belt follows the land rather than overriding it.
     */
    private static void stampBelt(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, int fortId, Ground ground) {
        FortDressing draw = new FortDressing(ctx, at);
        Set<Long> taken = new HashSet<>();
        int nestNumber = plan.nests().size();
        for (FortBelt.Cell c : plan.belt()) {
            if (plan.isWorks(c.x(), c.y())) continue;
            int[] cell = at.cell(c.x(), c.y());
            if (!ctx.grid.inBounds(cell[0], cell[1]) || (ground != null && !ground.ok(cell[0], cell[1]))) continue;
            boolean levelled = ground != null && ground.isRock(cell[0], cell[1]);
            if (!levelled && (!ctx.grid.isWalkable(cell[0], cell[1]) || ctx.topology.isFixture(cell[0], cell[1]))) continue;
            taken.add(FortDressing.key(cell[0], cell[1]));
            if (ground != null) ground.take(cell[0], cell[1]);
        }
        levelRocks(ctx, (x, y) -> taken.contains(FortDressing.key(x, y)));
        ctx.doodads.removeIf(d -> taken.contains(FortDressing.key(d.cellX, d.cellY)));
        Set<Long> trench = new HashSet<>();
        for (FortBelt.Cell c : plan.belt()) {
            int[] cell = at.cell(c.x(), c.y());
            if (c.role() == FortBelt.Role.TRENCH && taken.contains(FortDressing.key(cell[0], cell[1]))) {
                trench.add(FortDressing.key(cell[0], cell[1]));
            }
        }
        for (FortBelt.Cell c : plan.belt()) {
            int[] cell = at.cell(c.x(), c.y());
            if (!taken.contains(FortDressing.key(cell[0], cell[1]))) continue;
            switch (c.role()) {
                case TRENCH -> {
                    draw.earth(c.x(), c.y());
                    draw.trench(cell[0], cell[1], trench);
                }
                case EARTH -> draw.earth(c.x(), c.y());
                case BAG -> {
                    draw.earth(c.x(), c.y());
                    draw.sandbag(c.x(), c.y(), c.dx(), c.dy(), c.cornerDx(), c.cornerDy());
                }
                case WIRE -> {
                    draw.concertina(c.x(), c.y(), c.dx() != 0);
                    // A single strand is a hairline at game scale: lay a second row beside it on bare glacis.
                    for (int side = 1; side >= -1; side -= 2) {
                        int lx = c.x() - c.dy() * side;
                        int ly = c.y() + c.dx() * side;
                        int[] beside = at.cell(lx, ly);
                        if (taken.contains(FortDressing.key(beside[0], beside[1]))) continue;
                        if (ctx.grid.inBounds(beside[0], beside[1])
                                && ctx.topology.getGroundKind(beside[0], beside[1]) == GroundKind.COURTYARD) continue;
                        if (draw.concertina(lx, ly, c.dx() != 0)) break;
                    }
                }
                case NEST -> {
                    draw.earth(c.x(), c.y());
                    emitNest(ctx, cell, fortId, at, nestNumber++);
                }
            }
        }
    }

    /** The interior: gun pit, berms and yard furniture, kept off the paved lanes. */
    private static void dressInterior(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, boolean[][] lane) {
        FortDressing d = new FortDressing(ctx, at);
        switch (plan.kind()) {
            case BATTERY_FORT -> dressBatteryInterior(d, lane);
            case SHIELD_FORT -> dressShieldInterior(d, lane);
        }
    }

    private static void dressBatteryInterior(FortDressing d, boolean[][] lane) {
        int ox = BatteryFort.OX, oy = BatteryFort.OY;
        // The battery sits in a revetted pit: a gravel floor inside two sandbag rings whose gaps do not line up, so the
        // way in turns once and the guns are never open to a straight shot from the gate.
        int px = BatteryFort.PLATFORM[0], py = BatteryFort.PLATFORM[1];
        for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) d.gravel(px + dx, py + dy);
        d.thickRing(px - 1, py - 1, 3, 3, 3, -1, 0, py, 3);
        d.thickRing(px - 1, py - 1, 3, 3, 5, 0, -1, px, 3);

        // The fire-control bunker is low and hardened: one ring of bags a step off its walls, open at its door.
        int[] bunker = BatteryFort.floor(FortAspect.FIRE_CONTROL);
        int[] bunkerDoor = BatteryFort.door(FortAspect.FIRE_CONTROL);
        d.thickRing(bunker[0] - 1, bunker[1] - 1, bunker[2] + 2, bunker[3] + 2, 2, 0, 1, bunkerDoor[0], 3);
        // The magazine stands apart from the guns inside a double berm.
        int[] mag = BatteryFort.floor(FortAspect.MAGAZINE);
        int[] magDoor = BatteryFort.door(FortAspect.MAGAZINE);
        d.thickRing(mag[0] - 1, mag[1] - 1, mag[2] + 2, mag[3] + 2, 2, 0, 1, magDoor[0], 3);
        d.thickRing(mag[0] - 1, mag[1] - 1, mag[2] + 2, mag[3] + 2, 4, 0, 1, magDoor[0], 3);

        // Firing lines inside each gate cover the baffle, so a breach still has to cross open ground.
        d.line(ox + 9, oy + 15, ox + 9, oy + 19, -1, 0);
        d.line(ox + 19, oy + 9, ox + 27, oy + 9, 0, -1);

        // The power plant's fenced yard, with its plant standing in it.
        for (int x = ox + 20; x <= ox + 28; x++) {
            if (x < ox + 23 || x > ox + 25) d.wire(x, oy + 24, true);
            d.wire(x, oy + 32, true);
        }
        for (int y = oy + 25; y <= oy + 31; y++) {
            if (y < oy + 27 || y > oy + 29) d.wire(ox + 28, y, false);
        }
        d.prop(ox + 22, oy + 26, "industrial-turbine-set", lane);
        d.prop(ox + 22, oy + 29, "industrial-reactor-housing", lane);
        d.prop(ox + 25, oy + 29, "industrial-generator", lane);
        d.prop(ox + 26, oy + 29, "industrial-generator", lane);
        d.prop(ox + 27, oy + 26, "industrial-fluid-tank", lane);
        d.prop(ox + 27, oy + 27, "industrial-fluid-tank", lane);
        d.prop(ox + 22, oy + 31, "industrial-switchboard", lane);

        // Yard clutter that is cover as well: stores by the magazine, a parked carrier, spools and drums.
        d.prop(ox + 29, oy + 12, "industrial-crate-stack", lane);
        d.prop(ox + 30, oy + 12, "industrial-crate-stack", lane);
        d.prop(ox + 29, oy + 13, "industrial-crate-stack", lane);
        d.prop(ox + 29, oy + 15, "industrial-pallet-stack", lane);
        d.prop(ox + 16, oy + 15, "parked-armored-apc", lane);
        d.prop(ox + 16, oy + 17, "industrial-cable-reel", lane);
        d.prop(ox + 17, oy + 17, "industrial-drum-cluster", lane);
        d.prop(ox + 32, oy + 16, "industrial-scrap-pile", lane);
        d.prop(ox + 42, oy + 16, "industrial-crate-stack", lane);
        d.prop(ox + 43, oy + 16, "industrial-crate-stack", lane);
        d.prop(ox + 18, oy + 12, "military-radar-dish", lane);
    }

    /**
     * The shield fort's interior: a gravel bed under the generator inside its keep, firing lines inside each gate
     * covering the baffle, the control room low inside its own berm, and the power plant standing in a fenced yard.
     */
    private static void dressShieldInterior(FortDressing d, boolean[][] lane) {
        int ox = ShieldFort.OX, oy = ShieldFort.OY;
        int gx = ShieldFort.GENERATOR[0], gy = ShieldFort.GENERATOR[1];
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) if (dx * dx + dy * dy <= 30) d.gravel(gx + dx, gy + dy);
        }
        // Firing lines a step inside each gate, so a breach still has to cross open ground under the baffle's guns.
        d.line(ox + 19, oy + 35, ox + 27, oy + 35, 0, 1);
        d.line(ox + 10, oy + 19, ox + 10, oy + 27, -1, 0);
        d.line(ox + 35, oy + 19, ox + 35, oy + 27, 1, 0);

        // The control room is low and hardened: one ring of bags a step off its walls, open at its door.
        int[] room = ShieldFort.floor(FortAspect.CONTROL_ROOM);
        int[] roomDoor = ShieldFort.door(FortAspect.CONTROL_ROOM);
        d.thickRing(room[0] - 1, room[1] - 1, room[2] + 2, room[3] + 2, 2, 0, 1, roomDoor[0], 3);

        // The power plant's fenced yard, with its plant standing in it.
        int[] plant = ShieldFort.floor(FortAspect.POWER);
        for (int x = plant[0] - 2; x <= plant[0] + plant[2] + 1; x++) {
            if (x < plant[0] + 4 || x > plant[0] + 6) d.wire(x, oy + 15, true);
        }
        d.prop(ox + 20, oy + 10, "industrial-turbine-set", lane);
        d.prop(ox + 23, oy + 11, "industrial-reactor-housing", lane);
        d.prop(ox + 20, oy + 13, "industrial-generator", lane);
        d.prop(ox + 21, oy + 13, "industrial-generator", lane);
        d.prop(ox + 14, oy + 12, "industrial-fluid-tank", lane);
        d.prop(ox + 14, oy + 13, "industrial-fluid-tank", lane);
        d.prop(ox + 24, oy + 13, "industrial-switchboard", lane);

        // Inside the keep and about the yard: the field projector's own clutter, which is cover as well.
        d.prop(gx + 4, gy - 4, "military-radar-dish", lane);
        d.prop(gx - 5, gy + 2, "industrial-switchboard", lane);
        d.prop(ox + 38, oy + 10, "industrial-crate-stack", lane);
        d.prop(ox + 39, oy + 10, "industrial-crate-stack", lane);
        d.prop(ox + 38, oy + 12, "industrial-pallet-stack", lane);
        d.prop(ox + 14, oy + 33, "parked-armored-apc", lane);
        d.prop(ox + 30, oy + 33, "industrial-drum-cluster", lane);
        d.prop(ox + 31, oy + 35, "industrial-cable-reel", lane);
    }

    private static void emitStructure(GenContext ctx, String structureId, int[] centre, FortTag tag) {
        StructureDef def = TurretCatalogRegistry.requireStructure(structureId);
        StructureFootprint.seal(ctx.grid, def, centre[0], centre[1]);
        ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, centre[0], centre[1],
                List.of(new DefensePost.TurretSpec(def.id, centre[0], centre[1])), tag));
    }

    /** The outer wall: the footprint ring less the gates, with a face on every open side so it draws. */
    private static void stampWall(GenContext ctx, FortPlan plan, CompoundLayout.Placement at) {
        boolean[][] wall = new boolean[ctx.width][ctx.height];
        for (int lx = 0; lx < plan.width(); lx++) {
            for (int ly = 0; ly < plan.height(); ly++) {
                if (!plan.isWall(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                wall[cell[0]][cell[1]] = true;
                ctx.grid.setWalkable(cell[0], cell[1], false);
                ctx.grid.setWallHp(cell[0], cell[1], FORT_WALL_HP);
                ctx.topology.setWall(cell[0], cell[1], true);
                ctx.topology.setGroundKind(cell[0], cell[1], GroundKind.STRIPED);
            }
        }
        for (int lx = 0; lx < plan.width(); lx++) {
            for (int ly = 0; ly < plan.height(); ly++) {
                if (!plan.isWall(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                int x = cell[0], y = cell[1];
                int mask = 0;
                if (y + 1 >= ctx.height || !wall[x][y + 1]) mask |= CellTopology.WALL_DIR_N;
                if (y == 0 || !wall[x][y - 1]) mask |= CellTopology.WALL_DIR_S;
                if (x + 1 >= ctx.width || !wall[x + 1][y]) mask |= CellTopology.WALL_DIR_E;
                if (x == 0 || !wall[x - 1][y]) mask |= CellTopology.WALL_DIR_W;
                ctx.topology.setWallDirMask(x, y, mask);
            }
        }
        InstallationWalls.perimeter(ctx, wall);
    }

    /** Paves a short apron outside each gate, and a lane from it to the nearest road when open ground allows. */
    private static void paveGates(GenContext ctx, FortPlan plan, CompoundLayout.Placement at) {
        boolean[][] road = ctx.get(BspKeys.ROAD_RESERVATION);
        for (FortPlan.Gate gate : plan.gates()) {
            int[] mouth = {gate.mouthX(), gate.mouthY()};
            int nx = gate.outX();
            int ny = gate.outY();
            int px = ny != 0 ? 1 : 0;
            int py = nx != 0 ? 1 : 0;
            for (int d = 0; d < 4; d++) {
                for (int l = -2; l <= 2; l++) {
                    int[] cell = at.cell(mouth[0] + nx * d + px * l, mouth[1] + ny * d + py * l);
                    if (ctx.grid.inBounds(cell[0], cell[1]) && ctx.grid.isWalkable(cell[0], cell[1])) {
                        pave(ctx, cell[0], cell[1]);
                    }
                }
            }
            if (road == null) continue;
            int[] start = at.cell(mouth[0] + nx * 3, mouth[1] + ny * 3);
            int[] goal = nearestRoad(ctx, road, start[0], start[1]);
            if (goal == null) continue;
            int[] path = GridPathfinder.findPath(ctx.grid, start[0], start[1], goal[0], goal[1]);
            if (path.length == 0 || path.length / 2 > MAX_LINK_CELLS) continue;
            if (!laneIsOpenGround(ctx, path)) continue;
            for (int i = 0; i + 1 < path.length; i += 2) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int x = path[i] + dx, y = path[i + 1] + dy;
                        if (ctx.grid.inBounds(x, y) && ctx.grid.isWalkable(x, y)
                                && !ctx.grid.isDoorway(x, y) && openGround(ctx.topology.getGroundKind(x, y))) {
                            pave(ctx, x, y);
                        }
                    }
                }
            }
        }
    }

    private static void pave(GenContext ctx, int x, int y) {
        ctx.topology.setGroundKind(x, y, GroundKind.COURTYARD);
        ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex(
                Material.CONCRETE.block(RoadRasterizer.age(.55, ctx.seed, x, y))));
        ctx.markMadeGround(x, y);
    }

    private static boolean openGround(GroundKind kind) {
        return !REFUSED_GROUND.contains(kind);
    }

    /** Whether the whole lane crosses open outdoor ground, so paving it can never run through a building. */
    private static boolean laneIsOpenGround(GenContext ctx, int[] path) {
        for (int i = 0; i + 1 < path.length; i += 2) {
            GroundKind kind = ctx.topology.getGroundKind(path[i], path[i + 1]);
            if (kind == GroundKind.INDOOR || kind == GroundKind.WATER || ctx.grid.isDoorway(path[i], path[i + 1])) {
                return false;
            }
        }
        return true;
    }

    private static int[] nearestRoad(GenContext ctx, boolean[][] road, int x, int y) {
        int[] best = null;
        long bestSq = Long.MAX_VALUE;
        for (int rx = 0; rx < ctx.width; rx++) {
            for (int ry = 0; ry < ctx.height; ry++) {
                if (!road[rx][ry] || !ctx.grid.isWalkable(rx, ry)) continue;
                long dx = rx - x, dy = ry - y;
                long sq = dx * dx + dy * dy;
                if (sq < bestSq) {
                    bestSq = sq;
                    best = new int[]{rx, ry};
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------- finish

    /**
     * Brings the derived layers up to what was stamped after finalize had already run: cover round each fort and the
     * building list the roofs and fog read, which now includes the fort's own buildings.
     */
    private void finish(GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        for (Placed fort : placed) {
            int[] a = fort.placement().cell(0, 0);
            int[] b = fort.placement().cell(fort.plan().width() - 1, fort.plan().height() - 1);
            for (int y = Math.max(0, Math.min(a[1], b[1]) - 2); y <= Math.min(ctx.height - 1, Math.max(a[1], b[1]) + 2); y++) {
                for (int x = Math.max(0, Math.min(a[0], b[0]) - 2); x <= Math.min(ctx.width - 1, Math.max(a[0], b[0]) + 2); x++) {
                    grid.recomputeCoverAt(x, y);
                }
            }
        }
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, ctx.seed));
    }
}
