package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.turret.FortWorks;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.fort.FortTag;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FortReservations;
import com.dillon.starsectormarines.battle.world.gen.fortress.AirDefenceYard;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.BeltStandCells;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortSiting;
import com.dillon.starsectormarines.battle.world.gen.FortSitingPolicy;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoomFurnisher;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;
import com.dillon.starsectormarines.battle.world.gen.fortress.GateTowers;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationFloors;
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
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.TileManifest;

import org.apache.log4j.Logger;

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

    private static final Logger LOG = Logger.getLogger(FortStage.class);

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
            GroundKind.TILE, GroundKind.STREET, GroundKind.VOID, GroundKind.SIDEWALK,
            GroundKind.STRIPED);
    /** UNTUNED: how much a shield fort values covering the keep against a supply compound. */
    private static final int KEEP_WEIGHT = 6;
    private static final int SUPPLY_WEIGHT = 2;
    /**
     * The guns a fort's nests carry in turn: anti-infantry, the Hephaestus cannon (for the vehicles and mechs that come up the
     * gates), anti-infantry again, and a Vulcan to suppress. Anti-air is the missile towers' job, not the nests'. A strong defence
     * is a mix of fire, not a single gun. UNTUNED mix.
     */
    private static final String[] NEST_STRUCTURES = {"structure.turret-heavy-mg", TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID,
            "structure.turret-heavy-mg", TurretCatalogRegistry.VULCAN_STRUCTURE_ID};

    /** Members in a fort's token picket, the eyes that raise the pressure the commander's reactive manning answers. UNTUNED. */
    static final int PICKET_SIZE = 4;

    /** Trench and sandbag cells offered to one belt garrison as stand positions. */
    private static final int BELT_STAND_CELLS = 8;
    /** A belt run shorter than this is not worth a garrison. */
    private static final int BELT_MIN_STAND_CELLS = 4;
    /** How far (Manhattan) from its nest a belt garrison may stand. */
    private static final int BELT_REACH = 14;
    /** Below a compound perimeter post (50), so a belt takes only the defenders the places that matter more leave over. */
    private static final int BELT_PRIORITY = 45;
    /** Priority of a fort room's crew: below the belt's picket, which is what answers the first contact. UNTUNED. */
    private static final int ROOM_PRIORITY = 40;

    /** A candidate centre with the order it is taken in. */
    private record Site(int x, int y, int band, long centreDistSq) {}

    /** The kinds this stage places, in the order they are placed (and so numbered). */
    private static final List<FortPlan> PLANS = List.of(BatteryFort.PLAN, ShieldFort.PLAN);

    /**
     * Where a fort stands, for evidence. {@code domeRadius} is the reach a shield fort's dome was declared to cover, in
     * cells, and {@code 0} for a fort with no dome.
     */
    public record Placed(CompoundLayout.Placement placement, int fortId, FortPlan plan, FortSiting.Point target,
                         float domeRadius, boolean airDefence) {
        /** A fort placed with no anti-air yard. */
        public Placed(CompoundLayout.Placement placement, int fortId, FortPlan plan, FortSiting.Point target,
                      float domeRadius) {
            this(placement, fortId, plan, target, domeRadius, false);
        }
    }

    /**
     * A fort stood at a stated centre rather than sited: an authored map (a trial ground) says where its fort is and
     * which way the main gate faces ({@code facingX/Y}, a point the gate turns toward). The ground there must be open.
     */
    public record Pin(FortKind kind, int centreX, int centreY, int facingX, int facingY, boolean airDefence) {
        /** A pinned fort with no anti-air yard. */
        public Pin(FortKind kind, int centreX, int centreY, int facingX, int facingY) {
            this(kind, centreX, centreY, facingX, facingY, false);
        }
    }

    private final List<Placed> placed = new ArrayList<>();
    private final List<Pin> pins;

    public FortStage() {
        this.pins = null;
    }

    private FortStage(List<Pin> pins) {
        this.pins = List.copyOf(pins);
    }

    /**
     * A stage that stands exactly these forts where they are pinned, ignoring any declaration in the market profile,
     * and fails when one cannot stand. Must run after the tactical regions are built, like the sited stage.
     */
    public static FortStage pinned(List<Pin> pins) {
        return new FortStage(pins);
    }

    /** The forts this stage stood on its last run, in the order they were placed. */
    public List<Placed> placed() {
        return placed;
    }

    @Override
    public void run(GenContext ctx) {
        placed.clear();
        if (pins != null) {
            runPinned(ctx);
            return;
        }
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
        TacticalNode keep = anchors != null ? keepNode(ctx) : null;
        Map<FortKind, List<FortSiting.Point>> reserved = ctx.get(BspKeys.FORT_TARGETS);

        for (FortPlan plan : PLANS) {
            int budget = profile.declaredDefences().fortCount(plan.kind());
            if (budget <= 0) continue;
            CompoundLayout layout = plan.layout();
            List<FortSiting.Point> targets = anchors == null ? null : targetsFor(plan, anchors, budget, reserved);
            for (int n = 0; n < budget; n++) {
                FortSiting.Point target = targets == null ? null : targets.get(n);
                // The first forts of a kind, as many as the declaration asks, carry the anti-air yard.
                boolean air = n < profile.declaredDefences().airDefendedCount(plan.kind());
                Placed made = null;
                if (target != null) {
                    made = placeNear(plan, layout, ground, sitedFor(plan, sites, anchors, target, garrison), ctx, anchors, target, keep, air);
                }
                // The declared count stands where ground allows: a fort whose intended ground is taken falls back to
                // the leftover order rather than vanishing.
                if (made == null) made = placeNear(plan, layout, ground, sites, ctx, anchors, null, keep, air);
                if (made == null) {
                    // A declaration that cannot be met is a finding about the map, not something to drop quietly.
                    LOG.warn("FortStage: the mission declared " + budget + " " + plan.kind() + " and only " + n
                            + " could stand: no open ground left on this " + ctx.width + "x" + ctx.height + " map for a "
                            + plan.width() + "x" + plan.height() + " fort");
                    break;
                }
            }
        }
        if (placed.isEmpty()) return;
        finish(ctx);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, ctx.get(BspKeys.AXIS)));
    }

    private void runPinned(GenContext ctx) {
        if (ctx.get(BspKeys.TACTICAL_REGIONS) == null) {
            throw new IllegalStateException("FortStage requires TACTICAL_REGIONS - must run after TacticalRegionStage");
        }
        Ground ground = new Ground(ctx);
        for (Pin pin : pins) {
            FortPlan plan = PLANS.stream().filter(p -> p.kind() == pin.kind()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no fort plan for " + pin.kind()));
            Site site = new Site(pin.centreX(), pin.centreY(), 0, 0L);
            CompoundLayout.Placement at = fit(plan, plan.layout(), ground, site, ctx,
                    new FortSiting.Point(pin.facingX(), pin.facingY()));
            if (at == null) {
                throw new IllegalStateException("pinned " + pin.kind() + " cannot stand at " + pin.centreX() + ","
                        + pin.centreY() + ": the ground there is not open");
            }
            int fortId = placed.size();
            float dome = domeRadius(plan, at, null);
            stamp(ctx, plan, at, fortId, ground, dome, pin.airDefence());
            ground.exclude(plan, at);
            placed.add(new Placed(at, fortId, plan, null, dome, pin.airDefence()));
        }
        finish(ctx);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, ctx.get(BspKeys.AXIS)));
    }

    // ------------------------------------------------------------------------------------------ site

    /**
     * The first site in {@code order} where a fort of this plan can stand, stamped and recorded; {@code null} when none
     * fits. {@code target} is the ground a siting intended, kept for evidence; {@code null} for leftover ground.
     */
    private Placed placeNear(FortPlan plan, CompoundLayout layout, Ground ground, List<Site> order, GenContext ctx,
                             FortSiting.Anchors anchors, FortSiting.Point target, TacticalNode keep,
                             boolean air) {
        FortSiting.Point facing = anchors == null ? null : anchors.landing();
        for (Site site : order) {
            if (tooClose(site)) continue;
            CompoundLayout.Placement at = fit(plan, layout, ground, site, ctx, facing);
            if (at == null) continue;
            int fortId = placed.size();
            float dome = domeRadius(plan, at, anchors != null ? keep : null);
            stamp(ctx, plan, at, fortId, ground, dome, air);
            ground.exclude(plan, at);
            Placed made = new Placed(at, fortId, plan, target, dome, air);
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
            // A fort's dome is sized to what it covers, at least its own works; rank by that, not by a fixed figure.
            float radius = plan.worksRadius();
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

    private static List<FortSiting.Point> targetsFor(FortPlan plan, FortSiting.Anchors anchors, int count,
                                                     Map<FortKind, List<FortSiting.Point>> reserved) {
        List<FortSiting.Point> slid = reserved == null ? null : reserved.get(plan.kind());
        if (slid != null && slid.size() >= count) return new ArrayList<>(slid.subList(0, count));
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
        TacticalNode keep = keepNode(ctx);
        if (keep == null) return null;
        // The reservation was made against the plan's seeds, before the keep existed; aim at the same ends it did.
        FortSiting.Anchors stated = FortReservations.anchors(plan, ctx.width, ctx.height);
        if (stated != null && ctx.get(BspKeys.FORT_TARGETS) != null) return stated;
        return new FortSiting.Anchors(new FortSiting.Point(keep.anchorX, keep.anchorY),
                new FortSiting.Point(landing.seedX(), landing.seedY()), ctx.width, ctx.height);
    }

    /** The keep: the command post nearest the objective place, or {@code null} when the map has none. */
    private static TacticalNode keepNode(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        Precinct objective = plan == null ? null : plan.objective();
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
        return keep;
    }

    /**
     * The reach a fort's dome is declared to cover, in cells: {@code 0} for a plan with no dome; for a shield fort its
     * own works at least and, when {@code keep} is given (the fort was sited to protect it), the whole of the keep's
     * footprint.
     */
    private static float domeRadius(FortPlan plan, CompoundLayout.Placement at, TacticalNode keep) {
        if (plan.centrepieceAspect() != FortAspect.EMITTER) return 0f;
        int[] g = at.cell(plan.centrepiece()[0], plan.centrepiece()[1]);
        int[] box = keep == null ? null : new int[]{keep.left, keep.top, keep.right, keep.bottom};
        return FortSiting.domeRadius(new FortSiting.Point(g[0], g[1]), plan.worksRadius(), box);
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
        stamp(ctx, plan, at, fortId, null, domeRadius(plan, at, null), false);
    }

    /** As the four-argument form, with the anti-air yard when {@code air}. */
    static void stamp(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, int fortId, boolean air) {
        stamp(ctx, plan, at, fortId, null, domeRadius(plan, at, null), air);
    }

    /**
     * Stamps one fort from the outside in: the works and their glacis first, then the buildings, structures and wall,
     * the ground and interior dressing, and last the belt, which takes only the cells the ground will give it. A null
     * {@code ground} means open country everywhere, which is what a bare test field is.
     */
    private static void stamp(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, int fortId, Ground ground,
                              float domeRadius, boolean air) {
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

        // The fort is one lot, belt included: its wall, parapets and barricades draw in its guns' style.
        EmplacementStyle style = EmplacementStylePolicy.forDefenders(ctx.get(BspKeys.MARKET_PROFILE));
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                int[] cell = at.cell(lx, ly);
                FortWorks.mark(topology, cell[0], cell[1], style);
            }
        }

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

        // The rooms: shelled, windowed and doored by the compound machinery, left bare until their cores stand. The gate
        // towers come last and are part of the wall: its strength and its material, and the slits cut below.
        List<RoomPacker.Placed> rooms = new ArrayList<>(at.buildings());
        List<FortRoom> specs = new ArrayList<>(plan.buildings());
        // The cores stand and each room is fitted from its authored layout before the windows are cut, so no fixture
        // lands on a core's ground and no window opens behind a console bank.
        shell(ctx, rooms, () -> {
            for (FortAspect aspect : plan.aspects()) {
                int[] centre = plan.coreCentre(aspect);
                emitStructure(ctx, plan.coreStructureId(aspect), at.cell(centre[0], centre[1]),
                        new FortTag(fortId, aspect, at.x(), at.y(), at.pose().quarterTurns()));
            }
            for (int i = 0; i < specs.size(); i++) FortRoomFurnisher.furnish(ctx, rooms.get(i), specs.get(i));
        });
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).purpose() == RoomPurpose.GATE_TOWER) hardenTower(ctx, rooms.get(i));
        }

        // The structures: the fort's centrepiece and a nest in each bastion (the cores stand with their rooms above).
        int[] centrepiece = plan.centrepiece();
        emitStructure(ctx, plan.centrepieceStructureId(), at.cell(centrepiece[0], centrepiece[1]),
                new FortTag(fortId, plan.centrepieceAspect(), at.x(), at.y(), at.pose().quarterTurns(), domeRadius));
        int nestNumber = 0;
        for (int[] local : plan.nests()) {
            int[] nestCell = at.cell(local[0], local[1]);
            pad(ctx, nestCell, NEST_PAD_REACH);
            emitNest(ctx, nestCell, fortId, at, nestNumber++);
        }
        if (air) {
            rooms.addAll(stampAirDefence(ctx, plan, at, fortId));
            specs.add(plan.airDefence().room());
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
        if (air) stampAirMounts(ctx, plan, at, fortId);
        dressInterior(ctx, plan, at, lane, air);
        // The slits are cut once the yard is dressed, which would otherwise take an open slit for yard.
        cutSlits(ctx, plan, at);
        garrisonRooms(ctx, specs, at);
        stampBelt(ctx, plan, at, fortId, ground);
    }

    /**
     * Shells a set of rooms: floors, walls, windows and doors by the compound machinery, and every ring cell the machinery
     * left standing made a breachable wall. Finalize tags walls from what is not walkable and it has already run, so the
     * rings are tagged by hand (it would also tag the structures).
     */
    private static void shell(GenContext ctx, List<RoomPacker.Placed> rooms, Runnable fill) {
        FortressInterior.author(ctx, rooms, rooms, fill);
        for (RoomPacker.Placed room : rooms) {
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (ctx.grid.isWalkable(x, y)) continue;
                ctx.grid.setWallHp(x, y, BUILDING_WALL_HP);
                ctx.topology.setWall(x, y, true);
            }
        }
    }

    /**
     * Makes a gate tower's ring part of the works' wall: as strong as the wall round it, so a breach is the same work
     * there as anywhere along the face, and drawn in the wall's material.
     */
    private static void hardenTower(GenContext ctx, RoomPacker.Placed tower) {
        int surface = ctx.topology.surfaceIndex(InstallationWalls.PERIMETER);
        for (int[] cell : tower.shape().wall()) {
            int x = tower.originX() + cell[0];
            int y = tower.originY() + cell[1];
            if (!ctx.grid.inBounds(x, y) || ctx.grid.isWalkable(x, y)) continue;
            ctx.grid.setWallHp(x, y, FORT_WALL_HP);
            ctx.topology.setSurface(x, y, surface);
        }
    }

    /**
     * Cuts each gate tower's slits ({@link GateTowers#slits}): windows like any building's, each a barrier on its outward
     * edge that the garrison shoots through and nobody walks through. Cut once the wall stands and the yard is dressed.
     * The ring was already made wall, so each slit is then laid as an ordinary building's window is: no wall left in the
     * cell and the tower's floor under it, so whoever stands at the slit stands on floor with the wall either side.
     */
    private static void cutSlits(GenContext ctx, FortPlan plan, CompoundLayout.Placement at) {
        for (FortRoom tower : plan.towers()) {
            int floor = ctx.topology.surfaceIndex(InstallationFloors.material(tower.purpose()));
            for (int[] slit : GateTowers.slits(tower, plan::isWorks)) {
                int[] cell = at.cell(slit[0], slit[1]);
                int[] outside = at.cell(slit[0] + slit[2], slit[1] + slit[3]);
                Direction outward = cardinal(outside[0] - cell[0], outside[1] - cell[1]);
                FortressInterior.openWindow(ctx, cell[0], cell[1], outward);
                ctx.topology.setWall(cell[0], cell[1], false);
                ctx.topology.setSurface(cell[0], cell[1], floor);
            }
        }
    }

    private static Direction cardinal(int dx, int dy) {
        for (Direction d : Direction.CARDINALS) if (d.dx == dx && d.dy == dy) return d;
        throw new IllegalArgumentException("not a cardinal step: " + dx + "," + dy);
    }

    /**
     * Seats each room's crew by the ordinary place model: a defender-held {@link TacticalNode.Kind#INNER_POSITION}
     * (a garrison position inside a compound, with no capture marker of its own) whose authored stands are the room's
     * seats, so the garrison stands at its consoles and benches rather than outside the building. A room with no crew
     * seats none.
     */
    private static void garrisonRooms(GenContext ctx, List<FortRoom> rooms, CompoundLayout.Placement at) {
        TacticalMap finished = ctx.get(BspKeys.TACTICAL_MAP);
        for (FortRoom room : rooms) {
            List<TacticalNode.StandPosition> stands = new ArrayList<>();
            int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
            for (int[] local : room.standCells()) {
                int[] cell = at.cell(local[0], local[1]);
                if (!ctx.grid.inBounds(cell[0], cell[1]) || !ctx.grid.isWalkable(cell[0], cell[1])) continue;
                stands.add(new TacticalNode.StandPosition(cell[0], cell[1]));
                left = Math.min(left, cell[0]);
                top = Math.min(top, cell[1]);
                right = Math.max(right, cell[0]);
                bottom = Math.max(bottom, cell[1]);
            }
            if (stands.isEmpty()) continue;
            TacticalNode node = new TacticalNode(TacticalNode.Kind.INNER_POSITION, stands.get(0).x(), stands.get(0).y(),
                    left, top, right, bottom, Faction.DEFENDER, ROOM_PRIORITY, stands.size(), stands);
            if (finished != null) finished.add(node);
            else ctx.tactical.add(node);
        }
    }

    private static void emitNest(GenContext ctx, int[] cell, int fortId, CompoundLayout.Placement at, int number) {
        ctx.grid.setWalkable(cell[0], cell[1], false);
        ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, cell[0], cell[1],
                List.of(new DefensePost.TurretSpec(NEST_STRUCTURES[number % NEST_STRUCTURES.length], cell[0], cell[1])),
                new FortTag(fortId, null, at.x(), at.y(), at.pose().quarterTurns())));
    }

    /**
     * The outer belt: trench, sandbag line, fence line and nests, drawn only on cells the ground gives up. A cell the city
     * holds is skipped, so the belt follows the land rather than overriding it.
     */
    private static void stampBelt(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, int fortId, Ground ground) {
        FortDressing draw = new FortDressing(ctx, at);
        Set<Long> taken = new HashSet<>();
        List<int[]> beltNests = new ArrayList<>();
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
                case FENCE -> {
                    draw.fence(c.x(), c.y(), c.dx() != 0);
                    // One fence is a hairline at game scale and a lighter obstacle than the belt was authored against:
                    // lay a second row beside it on bare glacis, so the line is as thick as the wire it replaced.
                    for (int side = 1; side >= -1; side -= 2) {
                        int lx = c.x() - c.dy() * side;
                        int ly = c.y() + c.dx() * side;
                        int[] beside = at.cell(lx, ly);
                        if (taken.contains(FortDressing.key(beside[0], beside[1]))) continue;
                        if (ctx.grid.inBounds(beside[0], beside[1])
                                && ctx.topology.getGroundKind(beside[0], beside[1]) == GroundKind.COURTYARD) continue;
                        if (draw.fence(lx, ly, c.dx() != 0)) break;
                    }
                }
                case NEST -> {
                    draw.earth(c.x(), c.y());
                    emitNest(ctx, cell, fortId, at, nestNumber++);
                    beltNests.add(cell);
                }
            }
        }
        garrisonBelt(ctx, plan, at, taken, beltNests);
    }

    /**
     * Seats the belt's token picket by the ordinary place model: a defender-held {@link TacticalNode.Kind#GUARDPOST} of
     * {@link #PICKET_SIZE} at the belt nest nearest the main gate, which faces the landing, whose authored stand
     * positions are the trench and sandbag ground nearest the nest. The rest of the belt is empty; the defender commander
     * mans it from its mobile pool in answer to the pressure the picket reports. The node is anchored on its first stand
     * cell, not on the nest: a node on the nest's own cell would be paired with that nest's {@link DefensePost} and put
     * its squad on a patrol that wanders the box round it.
     */
    private static void garrisonBelt(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, Set<Long> taken,
                                     List<int[]> beltNests) {
        if (beltNests.isEmpty()) return;
        // The tactical map was built and linked before this stage ran, so a node reaches the battle only by being added to it.
        TacticalMap finished = ctx.get(BspKeys.TACTICAL_MAP);
        Set<Long> ground = new HashSet<>();
        for (FortBelt.Cell c : plan.belt()) {
            if (c.role() != FortBelt.Role.TRENCH && c.role() != FortBelt.Role.BAG) continue;
            int[] cell = at.cell(c.x(), c.y());
            if (taken.contains(FortDressing.key(cell[0], cell[1])) && ctx.grid.isWalkable(cell[0], cell[1])) {
                ground.add(BeltStandCells.key(cell[0], cell[1]));
            }
        }
        FortPlan.Gate gate = plan.gates().get(0);
        int[] mouth = at.cell(gate.mouthX(), gate.mouthY());
        int[] nest = null;
        long best = Long.MAX_VALUE;
        for (int[] candidate : beltNests) {
            long dx = candidate[0] - mouth[0], dy = candidate[1] - mouth[1];
            long d = dx * dx + dy * dy;
            if (d < best) {
                best = d;
                nest = candidate;
            }
        }
        List<int[]> cells = BeltStandCells.near(ground, nest[0], nest[1], BELT_STAND_CELLS, BELT_REACH);
        if (cells.size() < BELT_MIN_STAND_CELLS) return;
        List<TacticalNode.StandPosition> stands = new ArrayList<>(cells.size());
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (int[] cell : cells) {
            ground.remove(BeltStandCells.key(cell[0], cell[1]));
            stands.add(new TacticalNode.StandPosition(cell[0], cell[1]));
            left = Math.min(left, cell[0]);
            top = Math.min(top, cell[1]);
            right = Math.max(right, cell[0]);
            bottom = Math.max(bottom, cell[1]);
        }
        TacticalNode node = new TacticalNode(TacticalNode.Kind.GUARDPOST, cells.get(0)[0], cells.get(0)[1],
                left, top, right, bottom, Faction.DEFENDER, BELT_PRIORITY, Math.min(PICKET_SIZE, cells.size()),
                stands);
        if (finished != null) finished.add(node);
        else ctx.tactical.add(node);
    }

    /** The interior: gun pit, berms and yard furniture, kept off the paved lanes. */
    private static void dressInterior(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, boolean[][] lane,
                                      boolean air) {
        FortDressing d = new FortDressing(ctx, at);
        switch (plan.kind()) {
            case BATTERY_FORT -> dressBatteryInterior(d, lane);
            case SHIELD_FORT -> dressShieldInterior(d, lane);
        }
        List<FortRoom> specs = new ArrayList<>(plan.buildings());
        if (air) specs.add(plan.airDefence().room());
        traverseDoors(d, specs);
    }

    /**
     * One short offset bag screen in front of every door that opens on the works: two short runs of bags two cells out
     * either side of the door, so the way in turns once and a squad at the door is covered from both sides. It is the one
     * protective element a room's door carries (the rooms are the hardening; no ring of bags stands round them). A door
     * between two rooms of a suite, the loading bay's wide door onto the road and a gate tower's door onto the lane
     * behind the gate carry none.
     */
    private static void traverseDoors(FortDressing d, List<FortRoom> rooms) {
        for (FortRoom room : rooms) {
            if (room.purpose() == RoomPurpose.LOADING_BAY || room.purpose() == RoomPurpose.GATE_TOWER
                    || room.aspect() == FortAspect.AIR_DEFENCE) continue;
            for (int[] door : room.doorCells()) {
                int outX = door[0] < room.x() ? -1 : door[0] >= room.x() + room.width() ? 1 : 0;
                int outY = door[1] < room.y() ? -1 : door[1] >= room.y() + room.height() ? 1 : 0;
                if (outX == 0 && outY == 0) continue;
                if (opensOnARoom(rooms, room, door[0] + outX, door[1] + outY)) continue;
                int px = outY != 0 ? 1 : 0, py = outX != 0 ? 1 : 0;
                for (int k : new int[]{-2, -1, 1, 2}) {
                    d.sandbag(door[0] + outX * 2 + px * k, door[1] + outY * 2 + py * k, outX, outY);
                }
            }
        }
    }

    /**
     * Whether the cell just outside a door is another room's floor or wall ring, so the door is between two rooms of one
     * suite and opens on no yard.
     */
    private static boolean opensOnARoom(List<FortRoom> rooms, FortRoom from, int x, int y) {
        for (FortRoom room : rooms) {
            if (room == from) continue;
            if (x >= room.x() - 1 && x <= room.x() + room.width() && y >= room.y() - 1 && y <= room.y() + room.height()) return true;
        }
        return false;
    }

    private static void dressBatteryInterior(FortDressing d, boolean[][] lane) {
        int ox = BatteryFort.OX, oy = BatteryFort.OY;
        // The battery sits in one revetted pit: a gravel floor inside a single course of bags, open on its west side, with
        // a short traverse standing off the opening so the way in turns once and the guns are never open to a straight
        // shot from the gate.
        int px = BatteryFort.PLATFORM[0], py = BatteryFort.PLATFORM[1];
        for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) d.gravel(px + dx, py + dy);
        d.ring(px - 1, py - 1, 3, 3, 3, -1, 0, py, 3);
        d.line(px - 6, py - 2, px - 6, py + 2, -1, 0);

        // The magazine suite (store and loading bay) stands apart from the guns inside one berm, a single course, open
        // on the bay's road side: the only building given a berm of its own, because it is the one that must not take
        // a shell's blast. The other rooms are their own hardening and carry only the bag screen at their doors.
        int[] mag = BatteryFort.floor(FortAspect.MAGAZINE);
        int[] bay = BatteryFort.PLAN.suiteBounds(FortAspect.MAGAZINE);
        d.ring(bay[0] - 1, bay[1] - 1, bay[2] + 2, bay[3] + 2, 1, 0, 1, mag[0] + mag[2] / 2, 7);

        // Firing lines inside each gate cover the baffle, so a breach still has to cross open ground.
        d.line(ox + 9, oy + 15, ox + 9, oy + 19, -1, 0);
        d.line(ox + 19, oy + 9, ox + 27, oy + 9, 0, -1);

        // The power plant's fenced yard, with its plant standing in it.
        for (int x = ox + 20; x <= ox + 28; x++) {
            if (x < ox + 23 || x > ox + 25) d.fence(x, oy + 24, true);
            d.fence(x, oy + 32, true);
        }
        for (int y = oy + 25; y <= oy + 31; y++) {
            if (y < oy + 27 || y > oy + 29) d.fence(ox + 28, y, false);
        }
        d.prop(ox + 22, oy + 26, "industrial-turbine-set", lane);
        d.prop(ox + 27, oy + 26, "industrial-fluid-tank", lane);
        d.prop(ox + 27, oy + 27, "industrial-fluid-tank", lane);

        // Yard clutter that is cover as well: stores by the magazine, a parked carrier, spools and drums.
        d.prop(ox + 29, oy + 12, "industrial-crate-stack", lane);
        d.prop(ox + 30, oy + 12, "industrial-crate-stack", lane);
        d.prop(ox + 29, oy + 13, "industrial-crate-stack", lane);
        d.prop(ox + 29, oy + 15, "industrial-pallet-stack", lane);
        d.prop(ox + 16, oy + 15, "parked-armored-apc", lane);
        d.prop(ox + 16, oy + 17, "industrial-cable-reel", lane);
        d.prop(ox + 17, oy + 17, "industrial-drum-cluster", lane);
        d.prop(ox + 32, oy + 16, "industrial-scrap-pile", lane);
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

        // The power plant's fenced yard, with its plant standing in it.
        int[] plant = ShieldFort.floor(FortAspect.POWER);
        for (int x = plant[0] - 2; x <= plant[0] + plant[2] + 1; x++) {
            if (x < plant[0] + 4 || x > plant[0] + 6) d.fence(x, oy + 15, true);
        }
        d.prop(ox + 20, oy + 10, "industrial-turbine-set", lane);
        d.prop(ox + 14, oy + 12, "industrial-fluid-tank", lane);
        d.prop(ox + 14, oy + 13, "industrial-fluid-tank", lane);

        // Inside the keep and about the yard: the field projector's own clutter, which is cover as well.
        d.prop(gx + 4, gy - 4, "military-radar-dish", lane);
        d.prop(gx - 5, gy + 2, "industrial-switchboard", lane);
        d.prop(ox + 38, oy + 14, "industrial-crate-stack", lane);
        d.prop(ox + 39, oy + 14, "industrial-crate-stack", lane);
        d.prop(ox + 38, oy + 16, "industrial-pallet-stack", lane);
        d.prop(ox + 14, oy + 33, "parked-armored-apc", lane);
        d.prop(ox + 30, oy + 33, "industrial-drum-cluster", lane);
        d.prop(ox + 31, oy + 35, "industrial-cable-reel", lane);
    }

    /**
     * The optional anti-air aspect: its small hardened control room (shelled like every fort room), the plotting console
     * against the room's back wall, the Locust batteries on the bastions and the close-in Swarmer towers either side of the door,
     * every one tagged to the fort's {@link FortAspect#AIR_DEFENCE} so the simulation assembles the aspect from what was
     * stamped.
     *
     * @return the control room as placed, for fitting and dressing with the fort's other rooms
     */
    private static List<RoomPacker.Placed> stampAirDefence(GenContext ctx, FortPlan plan, CompoundLayout.Placement at,
                                                           int fortId) {
        AirDefenceYard yard = plan.airDefence();
        if (yard == null) throw new IllegalStateException("a " + plan.kind() + " has no anti-air yard");
        CompoundLayout layout = new CompoundLayout(RoomShape.rectangle(plan.width(), plan.height()),
                List.of(new CompoundLayout.Cluster("Anti-air control room", List.of(yard.room().building()))), List.of(), List.of());
        List<RoomPacker.Placed> placed = new ArrayList<>(layout.at(at.x(), at.y(), at.pose().quarterTurns()).buildings());
        FortTag tag = new FortTag(fortId, FortAspect.AIR_DEFENCE, at.x(), at.y(), at.pose().quarterTurns());
        shell(ctx, placed, () -> {
            emitStructure(ctx, plan.coreStructureId(FortAspect.AIR_DEFENCE), at.cell(yard.consoleCentre()[0], yard.consoleCentre()[1]), tag);
            FortRoomFurnisher.furnish(ctx, placed.get(0), yard.room());
        });
        return placed;
    }

    /**
     * The anti-air aspect's missile mounts on the bastions. They are stood after the yard has been dressed, because a
     * sealed footprint is not walkable and {@code InstallationGround.dress} leaves such cells as they were: mounts
     * stamped earlier stood on a hard-edged square of undressed ground in the middle of the bastion's floor.
     */
    private static void stampAirMounts(GenContext ctx, FortPlan plan, CompoundLayout.Placement at, int fortId) {
        FortTag tag = new FortTag(fortId, FortAspect.AIR_DEFENCE, at.x(), at.y(), at.pose().quarterTurns());
        for (int[] battery : plan.airBatteries()) {
            int[] cell = at.cell(battery[0], battery[1]);
            pad(ctx, cell, MOUNT_PAD_REACH);
            emitStructure(ctx, TurretCatalogRegistry.AA_LOCUST_STRUCTURE_ID, cell, tag);
        }
        for (int[] tower : plan.airTowers()) {
            int[] cell = at.cell(tower[0], tower[1]);
            pad(ctx, cell, MOUNT_PAD_REACH);
            emitStructure(ctx, TurretCatalogRegistry.AA_SWARMER_STRUCTURE_ID, cell, tag);
        }
    }

    /** How far a bastion nest's concrete pad reaches from its cell: the cell and the ring about it. */
    private static final int NEST_PAD_REACH = 1;
    /** How far a bastion missile mount's concrete pad reaches from its middle: its three-by-three body and a ring about it. */
    private static final int MOUNT_PAD_REACH = 2;

    /**
     * Lays the concrete pad a prepared position stands on: the paving the works already uses, over open ground within
     * {@code reach} cells of {@code centre}. Walls, sealed cells and fixtures are left alone, so it changes the ground's look
     * and nothing a unit can or cannot do. A belt nest, a field position, stays on its sandbags and earth.
     */
    private static void pad(GenContext ctx, int[] centre, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                int x = centre[0] + dx, y = centre[1] + dy;
                if (!ctx.grid.inBounds(x, y) || ctx.topology.isWall(x, y) || ctx.topology.isFixture(x, y)
                        || !ctx.grid.isWalkable(x, y) || ctx.grid.isDoorway(x, y)) continue;
                pave(ctx, x, y);
                if (Math.max(Math.abs(dx), Math.abs(dy)) == reach) {
                    // The pad's rim is the concrete collar hugging the body: edges and inner corners, wearable cover
                    // like any other prop. The ground under it is the paving's.
                    Doodad parapet = TileManifest.emplacementTightParapet(x, y, dx, dy, reach - 1);
                    if (parapet != null) ctx.doodads.add(parapet);
                }
            }
        }
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
        // The gate towers' rings carry the wall on through them, so neither draws a face against the other.
        boolean[][] tower = new boolean[ctx.width][ctx.height];
        for (FortRoom room : plan.towers()) {
            for (int[] local : GateTowers.footprint(room)) {
                int[] cell = at.cell(local[0], local[1]);
                if (ctx.grid.inBounds(cell[0], cell[1]) && !ctx.grid.isWalkable(cell[0], cell[1])
                        && !ctx.grid.isDoorway(cell[0], cell[1])) {
                    tower[cell[0]][cell[1]] = true;
                }
            }
        }
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
                if (y + 1 >= ctx.height || !(wall[x][y + 1] || tower[x][y + 1])) mask |= CellTopology.WALL_DIR_N;
                if (y == 0 || !(wall[x][y - 1] || tower[x][y - 1])) mask |= CellTopology.WALL_DIR_S;
                if (x + 1 >= ctx.width || !(wall[x + 1][y] || tower[x + 1][y])) mask |= CellTopology.WALL_DIR_E;
                if (x == 0 || !(wall[x - 1][y] || tower[x - 1][y])) mask |= CellTopology.WALL_DIR_W;
                ctx.topology.setWallDirMask(x, y, mask);
            }
        }
        for (int x = 1; x + 1 < ctx.width; x++) {
            for (int y = 1; y + 1 < ctx.height; y++) {
                if (!tower[x][y]) continue;
                int mask = ctx.topology.getWallDirMask(x, y);
                if (wall[x][y + 1]) mask &= ~CellTopology.WALL_DIR_N;
                if (wall[x][y - 1]) mask &= ~CellTopology.WALL_DIR_S;
                if (wall[x + 1][y]) mask &= ~CellTopology.WALL_DIR_E;
                if (wall[x - 1][y]) mask &= ~CellTopology.WALL_DIR_W;
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
