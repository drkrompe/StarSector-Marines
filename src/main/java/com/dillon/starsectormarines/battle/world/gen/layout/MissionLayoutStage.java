package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.TerracePlanner;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.precinct.KnollGround;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lays the plan's {@link MissionLayout} over the finished places, roads and
 * lanes, before the landscape grows round them ({@link Phase#WORKS}), and
 * binds what it made under {@link BspKeys#MISSION_LAYOUT_LAID}.
 *
 * <p>Only ever in a recipe whose plan states a layout or stands a place on a
 * knoll (raised and reported here with the layout's ridges), so a map without
 * either is generated exactly as before. Every placement yields to what is there: a cell
 * that is not open ground, or is road, is skipped, so a line through a town is
 * broken by its buildings and reported {@link Laid.Status#PARTIAL}. A line is
 * never allowed to seal the map: wire and nests that would cut the walkable
 * ground apart are not laid, and the line says so.
 */
public final class MissionLayoutStage implements GenStage {

    /** Share of a line's planned works it must lay to count as laid whole. */
    static final float WHOLE = 0.9f;
    /** Walkable cells the closed works may cut off from the main ground beyond their own (pockets behind wire). */
    static final int SEAL_SLACK = 48;
    private static final String NEST_STRUCTURE = "structure.turret-heavy-mg";

    private final Phase phase;

    /** The works phase: lines, sites and blocks, before the landscape. */
    public MissionLayoutStage() {
        this(Phase.WORKS);
    }

    /**
     * The stage for one phase: {@link Phase#STRUCTURES} raises the structures before the map is finished,
     * {@link Phase#WORKS} lays everything else and reports the whole layout.
     */
    public MissionLayoutStage(Phase phase) {
        this.phase = phase;
    }

    @Override
    public void run(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        MissionLayout layout = plan == null ? MissionLayout.NONE : plan.layout();
        if (layout.isEmpty() && (plan == null || plan.landscape().knolls().isEmpty())) return;
        LayoutGround ground = new LayoutGround(ctx);
        boolean[][] held = layout.keepClear(ctx.width, ctx.height);
        List<Piece> pieces = new ArrayList<>();
        List<Laid> out = new ArrayList<>();
        if (phase == Phase.WORKS) {
            // Roads were carved as the places grew and structures raised before the map was finished; they are
            // reported here with the rest.
            List<Laid> roads = ctx.get(BspKeys.LAYOUT_ROADS);
            if (roads != null) out.addAll(roads);
            List<Laid> raised = ctx.get(BspKeys.LAYOUT_STRUCTURES);
            if (raised != null) out.addAll(raised);
        }
        for (Piece piece : layout.pieces()) {
            if (piece instanceof Road) continue;
            if (piece instanceof Deferred deferred) {
                if (deferred.phase() != phase) continue;
                List<Piece> resolved = deferred.resolve().apply(ground);
                if (resolved != null) pieces.addAll(resolved);
            } else if ((piece instanceof Structure) == (phase == Phase.STRUCTURES)) {
                pieces.add(piece);
            }
        }
        // A site resolved with the works holds its ground against them as one stated up front does.
        for (Piece piece : pieces) {
            if (!(piece instanceof Site site)) continue;
            if (held == null) held = new boolean[ctx.width][ctx.height];
            for (int x = Math.max(0, site.x0()); x <= Math.min(ctx.width - 1, site.x1()); x++) {
                for (int y = Math.max(0, site.y0()); y <= Math.min(ctx.height - 1, site.y1()); y++) held[x][y] = true;
            }
        }
        // Ridges first, so the works resolved along them are dug on their crests. Every ridge goes up in one
        // stamp, the landscape's own with them: elevation is stamped once, and a second stamp is refused.
        Map<String, List<int[]>> ridges = new HashMap<>();
        boolean closed = false;
        if (phase == Phase.WORKS) {
            refuseSecondElevation(ctx, pieces);
            List<Ridge> layoutRidges = new ArrayList<>();
            for (Piece piece : pieces) {
                if (piece instanceof Ridge ridge && layoutRidges.stream().noneMatch(r -> r.name().equals(ridge.name()))) {
                    layoutRidges.add(ridge);
                }
            }
            // A knoll is a terrace of its own, raised with the ridges; one refused before growth is reported so.
            List<TerracePlanner.Terrace> knolls = new ArrayList<>();
            for (KnollGround.Resolved knoll : knolls(ctx, plan)) {
                if (knoll.stands()) {
                    knolls.add(new TerracePlanner.Terrace(knoll.knoll().name(), knoll.x0(), knoll.y0(), knoll.x1(),
                            knoll.y1(), knoll.knoll().required()));
                } else {
                    out.add(Laid.refused(knoll.knoll().name(), Laid.Kind.GROUND, knoll.knoll().required(),
                            knoll.refusal()));
                }
            }
            if (!layoutRidges.isEmpty() || !knolls.isEmpty()) {
                List<RidgePlanner.Band> bands = new ArrayList<>();
                List<TerracePlanner.Terrace> terraces = new ArrayList<>(knolls);
                for (Ridge ridge : layoutRidges) {
                    bands.add(ridge.band());
                    terraces.addAll(ridge.terraces());
                }
                // A band the landscape holds for a layout Ridge (the Cordon holds its line's) is the same ground once.
                for (RidgePlanner.Band band : OutdoorLandscapeStage.landscapeBands(ctx)) {
                    if (!bands.contains(band)) bands.add(band);
                }
                OutdoorLandscapeStage.Raised raised = OutdoorLandscapeStage.raiseRidges(ctx, bands, held, terraces);
                List<int[]> feet = raised.feet();
                closed = !feet.isEmpty();
                for (TerracePlanner.Planned terrace : raised.terraces()) {
                    closed |= terrace.stands();
                    out.add(terraceLaid(terrace));
                }
                // Each ramp's foot belongs to the ridge whose ground it stands on; the landscape's report none.
                for (Ridge ridge : layoutRidges) {
                    boolean[][] band = RidgePlanner.heldGround(List.of(ridge.band()), ctx.width, ctx.height, 1);
                    List<int[]> own = new ArrayList<>();
                    for (int[] foot : feet) if (band[foot[0]][foot[1]]) own.add(foot);
                    ridges.put(ridge.name(), own);
                }
            }
        }
        Set<String> names = new HashSet<>();
        for (Laid laid : out) names.add(laid.name());
        List<int[]> streets = new ArrayList<>();
        for (Piece piece : pieces) {
            if (!(piece instanceof Road) && !streets.isEmpty()) {
                if (held == null) held = new boolean[ctx.width][ctx.height];
                for (int[] c : streets) held[c[0]][c[1]] = true;
                streets.clear();
            }
            if (!names.add(piece.name())) {
                out.add(Laid.refused(piece.name(), Laid.Kind.SITE, false, "named twice"));
                continue;
            }
            if (piece instanceof Structure structure) {
                if (phase != Phase.STRUCTURES) {
                    out.add(Laid.refused(structure.name(), Laid.Kind.STRUCTURE, structure.required(),
                            "a structure is raised in the structures phase"));
                    continue;
                }
                out.add(LayoutStructures.raise(ctx, ground, structure, held));
            } else if (piece instanceof Worked worked) {
                out.add(layWorked(ctx, worked, held));
            } else if (piece instanceof Props props && phase == Phase.STRUCTURES) {
                out.add(layProps(ctx, props));
            } else if (phase == Phase.STRUCTURES) {
                out.add(Laid.refused(piece.name(), Laid.Kind.SITE, piece.required(),
                        "only structures are raised in the structures phase"));
            } else if (piece instanceof Ridge ridge) {
                List<int[]> feet = ridges.getOrDefault(ridge.name(), List.of());
                // Reported by its ramps' feet, the ways up: a ridge's cells are the elevation map's.
                out.add(feet.isEmpty()
                        ? Laid.refused(ridge.name(), Laid.Kind.GROUND, ridge.required(), "no stretch of its band fits")
                        : new Laid(ridge.name(), Laid.Kind.GROUND, Laid.Status.LAID, feet.size() + " segments",
                                ridge.required(), List.of(), feet, List.of()));
            } else if (piece instanceof Road road) {
                // A road resolved with the works runs along the streets standing; the works laid after it keep off
                // it, but the other roads may share it (every supply road leaves the base by the same street).
                Laid laid = LayoutStreets.follow(ctx, ground, road, held);
                out.add(laid);
                if (laid.isLaid()) {
                    streets.addAll(laid.cells());
                    closed = true;
                }
            } else if (piece instanceof Line line) {
                Laid laid = lay(ctx, ground, line, held);
                out.add(laid);
                for (int[] c : laid.cells()) closed |= !ctx.grid.isWalkable(c[0], c[1]);
                closed |= !laid.nests().isEmpty();
            } else if (piece instanceof Block block) {
                Laid laid = LayoutBlocks.lay(ctx, block, held);
                out.add(laid);
                for (int[] c : laid.cells()) closed |= !ctx.grid.isWalkable(c[0], c[1]);
            } else if (piece instanceof Props props) {
                Laid laid = layProps(ctx, props);
                out.add(laid);
                closed |= laid.isLaid();
            } else if (piece instanceof Site site) {
                boolean inside = site.x0() >= 0 && site.y0() >= 0 && site.x1() < ctx.width && site.y1() < ctx.height;
                Laid.Kind kind = site.purpose() == SitePurpose.GROUND ? Laid.Kind.GROUND : Laid.Kind.SITE;
                out.add(inside
                        ? new Laid(site.name(), kind, Laid.Status.LAID, "", site.required(),
                                List.of(new int[]{site.x0(), site.y0()}, new int[]{site.x1(), site.y1()}), List.of(), List.of())
                        : Laid.refused(site.name(), kind, site.required(), "off the map"));
            } else {
                out.add(Laid.refused(piece.name(), Laid.Kind.SITE, piece.required(), "a deferred piece resolved to another"));
            }
        }
        if (phase == Phase.STRUCTURES) {
            ctx.put(BspKeys.LAYOUT_STRUCTURES, out);
            return;
        }
        // Wire, nests and blocks close cells after the regions were published: "non-walkable => region -1" must hold.
        if (closed && ctx.get(BspKeys.TACTICAL_REGIONS) != null) {
            ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, ctx.get(BspKeys.AXIS)));
        }
        ctx.put(BspKeys.MISSION_LAYOUT_LAID, new LaidLayout(out, onWays(ground, out)));
    }

    /** Each laid structure or line of works standing on a way, with the cells it covers there (LaidLayout.onWays). */
    static Map<String, List<int[]>> onWays(LayoutGround ground, List<Laid> laid) {
        Map<String, List<int[]>> out = new HashMap<>();
        for (Laid piece : laid) {
            if (!piece.isLaid() || piece.cells().isEmpty()) continue;
            List<int[]> on = new ArrayList<>();
            if (piece.kind() == Laid.Kind.STRUCTURE && !LayoutStructures.PAVING_REASON.equals(piece.reason())) {
                int[] a = piece.cells().get(0), b = piece.cells().get(piece.cells().size() - 1);
                for (int x = a[0]; x <= b[0]; x++) {
                    for (int y = a[1]; y <= b[1]; y++) if (ground.isUsedWay(x, y)) on.add(new int[]{x, y});
                }
            } else if (piece.kind() == Laid.Kind.LINE) {
                for (int[] c : piece.cells()) if (ground.isUsedWay(c[0], c[1])) on.add(c);
            }
            if (!on.isEmpty()) out.put(piece.name(), on);
        }
        // The farmland's own farms, yard and buildings, read the same way: nothing stands on a way.
        RuralLayout rural = ground.rural();
        if (rural != null) {
            for (int i = 0; i < rural.farmsteads().size(); i++) {
                RuralLayout.Farmstead f = rural.farmsteads().get(i);
                List<int[]> on = new ArrayList<>();
                for (int x = f.yardX0(); x <= f.yardX1(); x++) {
                    for (int y = f.yardY0(); y <= f.yardY1(); y++) if (ground.isUsedWay(x, y)) on.add(new int[]{x, y});
                }
                if (!on.isEmpty()) out.put(FARMLAND_FARMSTEAD + (i + 1), on);
            }
        }
        return out;
    }

    /** How the reading names the farmland's own farms: this plus their number. */
    static final String FARMLAND_FARMSTEAD = "farmland-farmstead-";

    /** The plan's knolls as the places' growth read them, or worked out again where nothing bound them. */
    private static List<KnollGround.Resolved> knolls(GenContext ctx, PrecinctPlan plan) {
        if (plan.landscape().knolls().isEmpty()) return List.of();
        List<KnollGround.Resolved> bound = ctx.get(BspKeys.KNOLLS);
        return bound != null ? bound : KnollGround.resolve(plan, ctx.width, ctx.height);
    }

    /**
     * A terrace as reported: its place's ground by its corners, its ramps' feet as the ways up, or refused with why.
     */
    static Laid terraceLaid(TerracePlanner.Planned planned) {
        TerracePlanner.Terrace t = planned.terrace();
        if (!planned.stands()) return Laid.refused(t.name(), Laid.Kind.GROUND, t.required(), planned.refusal());
        return new Laid(t.name(), Laid.Kind.GROUND, Laid.Status.LAID, planned.ramps().size() + " ramps", t.required(),
                List.of(new int[]{t.x0(), t.y0()}, new int[]{t.x1(), t.y1()}), planned.feet(), List.of());
    }

    /**
     * Each prop through the ordinary placement, and a tree's canopy through the forest brush, which refuses it on
     * ground a tree may not stand on; laid when any stood, reported by the low corners of those that did.
     */
    /** Worked earth over a {@link Worked} piece's open cells, off held ground. */
    static Laid layWorked(GenContext ctx, Worked worked, boolean[][] held) {
        List<int[]> done = new ArrayList<>();
        for (int[] c : worked.cells()) {
            if (held != null && ctx.grid.inBounds(c[0], c[1]) && held[c[0]][c[1]]) continue;
            if (PreparedGround.workedGround(ctx, c[0], c[1])) done.add(c);
        }
        return done.isEmpty()
                ? Laid.refused(worked.name(), Laid.Kind.BLOCK, worked.required(), "no open ground")
                : new Laid(worked.name(), Laid.Kind.BLOCK, Laid.Status.LAID, done.size() + " of " + worked.cells().size(),
                        worked.required(), done, List.of(), List.of());
    }

    static Laid layProps(GenContext ctx, Props props) {
        TileRegistry tiles = TileRegistry.installed();
        List<int[]> stood = new ArrayList<>();
        for (Props.Placement p : props.props()) {
            TreeCanopy canopy = canopy(p.doodad());
            if (canopy != null) {
                if (tree(ctx, new ForestField.Tree(p.x(), p.y(), canopy, p.quarterTurns()))) stood.add(new int[]{p.x(), p.y()});
                continue;
            }
            DoodadDef def = tiles == null ? null : tiles.doodad(p.doodad());
            if (def != null && PreparedGround.prop(ctx, p.x(), p.y(), def, p.quarterTurns())) stood.add(new int[]{p.x(), p.y()});
        }
        return stood.isEmpty()
                ? Laid.refused(props.name(), Laid.Kind.BLOCK, props.required(), "no prop had open ground")
                : new Laid(props.name(), Laid.Kind.BLOCK, Laid.Status.LAID, stood.size() + " of " + props.props().size(),
                        props.required(), stood, List.of(), List.of());
    }

    private static TreeCanopy canopy(String doodad) {
        for (TreeCanopy c : TreeCanopy.values()) if (c.id.equals(doodad)) return c;
        return null;
    }

    /**
     * One tree stamped as the woods are: planted, so paving under its canopy is lifted to a grass bed first. Refused
     * where its canopy would cover a road, a building or anything already standing.
     */
    private static boolean tree(GenContext ctx, ForestField.Tree tree) {
        if (tree.x() < 0 || tree.y() < 0 || tree.x() + tree.width() > ctx.width || tree.y() + tree.height() > ctx.height) return false;
        RoadSurface roads = ctx.topology.roadSurface();
        for (int y = tree.y(); y < tree.y() + tree.height(); y++) {
            for (int x = tree.x(); x < tree.x() + tree.width(); x++) {
                if (!tree.blocks(x, y)) continue;
                if (!PreparedGround.open(ctx, x, y) || ctx.topology.getBuildingId(x, y) != 0
                        || roads != null && roads.underlay(x, y) != null) return false;
            }
        }
        for (int y = tree.y(); y < tree.y() + tree.height(); y++) {
            for (int x = tree.x(); x < tree.x() + tree.width(); x++) {
                if (!tree.blocks(x, y)) continue;
                ctx.topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
                ctx.topology.setSurface(x, y, 0);
            }
        }
        try {
            ForestBrush.stampTrees(List.of(tree), ctx.grid, ctx.topology, ctx.doodads, null, false);
            return true;
        } catch (IllegalArgumentException | IllegalStateException refused) {
            return false;
        }
    }

    private static Laid lay(GenContext ctx, LayoutGround ground, Line line, boolean[][] held) {
        boolean dug = line.style().dug();
        LineCourse.Course course = LineCourse.plan(line.path(), (x, y) -> breaks(ground, x, y), line.roadGap(), line.frontward(),
                line.style() == LineStyle.TRENCH_WIRE ? line.funnelEvery() : 0,
                dug ? line.depth() : 1, dug, line.nestEvery());
        int[] front = line.frontward();
        int planned = 0;
        List<int[]> works = new ArrayList<>();
        List<int[]> blocked = new ArrayList<>();
        for (List<int[]> run : course.runs()) {
            planned += run.size();
            for (int[] c : run) {
                if (takes(ctx, ground, held, c)) works.add(c);
                else if (!breaks(ground, c[0], c[1])) blocked.add(c);
            }
        }
        if (planned == 0) return Laid.refused(line.name(), Laid.Kind.LINE, line.required(), "no course between roads");
        // A street line keeps to the house fronts its course runs into, so a course that is all houses is a line.
        List<int[]> fronts = line.style() == LineStyle.STREETS ? houseFronts(ctx, ground, held, blocked, front)
                : List.of();
        if (works.isEmpty() && fronts.isEmpty()) {
            return Laid.refused(line.name(), Laid.Kind.LINE, line.required(), "no open ground on its course");
        }

        Set<Long> worksKeys = keys(works);
        List<int[]> closing = new ArrayList<>();
        if (line.style() == LineStyle.TRENCH_WIRE) {
            for (int[] c : course.wire()) {
                if (!worksKeys.contains(PreparedGround.key(c[0], c[1])) && takes(ctx, ground, held, c)) closing.add(c);
            }
        }
        List<int[]> nests = new ArrayList<>();
        if (line.nestsAtEnds()) {
            for (int[] c : course.nests()) {
                if (!takes(ctx, ground, held, c)) continue;
                // A street line is broken by every house, so its runs are short: one nest a stretch of street.
                if (line.style() == LineStyle.STREETS && near(nests, c, STREET_NEST_SPACING)) continue;
                nests.add(c);
            }
        }
        List<int[]> barricades = line.barricadeGaps() ? barricades(ctx, ground, held, course.gaps()) : List.of();
        List<int[]> shut = new ArrayList<>(closing);
        shut.addAll(nests);
        shut.addAll(barricades);
        String reason = "";
        if (!shut.isEmpty() && seals(ctx, shut)) {
            closing.clear();
            nests.clear();
            barricades = List.of();
            reason = "wire, nests and barricades would seal the ground; works only";
        }

        List<int[]> rear = new ArrayList<>();
        List<int[]> lip = new ArrayList<>();
        if (dug) {
            for (int[] c : course.rear()) if (takes(ctx, ground, held, c)) rear.add(c);
            for (int[] c : course.parapet()) if (takes(ctx, ground, held, c)) lip.add(c);
        }
        Set<Long> taken = keys(works);
        taken.addAll(keys(rear));
        taken.addAll(keys(lip));
        taken.addAll(keys(fronts));
        taken.addAll(keys(closing));
        taken.addAll(keys(nests));
        taken.addAll(keys(barricades));
        ctx.doodads.removeIf(d -> taken.contains(PreparedGround.key(d.cellX, d.cellY)));

        List<int[]> laidCells = new ArrayList<>();
        int laidWorks;
        if (dug) {
            // The course and the rows behind it are one trench; its nests stand in it.
            List<int[]> trench = new ArrayList<>(works);
            trench.addAll(rear);
            PreparedGround.trenchLine(ctx, trench);
            laidCells.addAll(trench);
            laidWorks = works.size();
            for (int[] c : lip) if (PreparedGround.sandbag(ctx, c[0], c[1], front, null)) laidCells.add(c);
        } else {
            // Bags stand on turned earth, so a street line reads against lawns and paving alike.
            for (int[] c : works) {
                PreparedGround.workedGround(ctx, c[0], c[1]);
                if (PreparedGround.sandbag(ctx, c[0], c[1], front, null)) laidCells.add(c);
            }
            laidWorks = laidCells.size();
            for (int[] c : fronts) {
                PreparedGround.workedGround(ctx, c[0], c[1]);
                if (PreparedGround.sandbag(ctx, c[0], c[1], front, null)) laidCells.add(c);
            }
        }
        boolean horizontal = front[1] != 0;
        for (int[] c : closing) if (PreparedGround.fence(ctx, c[0], c[1], horizontal)) laidCells.add(c);
        for (int i = 0; i < barricades.size(); i++) {
            int[] c = barricades.get(i);
            String piece = i % CRATE_EVERY == CRATE_EVERY - 1 ? CRATES : HEDGEHOG;
            if (PreparedGround.obstacle(ctx, c[0], c[1], piece)) laidCells.add(c);
        }
        for (int[] c : nests) {
            ctx.grid.setWalkable(c[0], c[1], false);
            ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, c[0], c[1],
                    List.of(new DefensePost.TurretSpec(NEST_STRUCTURE, c[0], c[1]))));
        }
        List<int[]> worked = new ArrayList<>(works);
        worked.addAll(fronts);
        workGround(ctx, ground, held, line, worked, nests, laidCells);

        List<int[]> gaps = new ArrayList<>();
        for (List<int[]> gap : course.gaps()) {
            int[] middle = gapMiddle(ctx, ground, gap);
            if (middle != null) gaps.add(middle);
        }
        if (!closing.isEmpty()) {
            for (int[] c : course.funnels()) {
                if (ctx.grid.inBounds(c[0], c[1]) && ctx.grid.isWalkable(c[0], c[1])) gaps.add(c);
            }
        }
        Laid.Status status = laidWorks + fronts.size() >= WHOLE * planned && reason.isEmpty()
                ? Laid.Status.LAID : Laid.Status.PARTIAL;
        if (reason.isEmpty() && status == Laid.Status.PARTIAL) reason = "laid " + laidWorks + " of " + planned + " cells";
        return new Laid(line.name(), Laid.Kind.LINE, status, reason, line.required(), laidCells, gaps, nests);
    }

    /** The nearest two nests stand on a street line. */
    static final int STREET_NEST_SPACING = 24;

    private static boolean near(List<int[]> cells, int[] c, int distance) {
        for (int[] o : cells) if (Math.abs(o[0] - c[0]) + Math.abs(o[1] - c[1]) < distance) return true;
        return false;
    }

    /** Cells across a barricaded gap left open in its middle: a truck and room either side. */
    static final int GAP_OPENING = 5;
    /** Every this many barricade cells, a crate stack in place of a hedgehog. */
    private static final int CRATE_EVERY = 4;
    private static final String HEDGEHOG = "doodad.fort-hedgehog";
    private static final String CRATES = "doodad.industrial-crate-stack";
    /** How far in front of a course cell a house front is looked for. */
    static final int HOUSE_REACH = 12;

    /**
     * The turned earth round a line, which is what reads it from a distance: for a dug line, behind the trench and in
     * front of the parapet up to the wire; for a street line, two steps behind its bags.
     */
    private static void workGround(GenContext ctx, LayoutGround ground, boolean[][] held, Line line,
                                   List<int[]> works, List<int[]> nests, List<int[]> laid) {
        int[] front = line.frontward();
        Set<Long> worked = keys(laid);
        worked.addAll(keys(nests));
        // A street line has no spoil to throw: only the ground its bags stand on and two steps behind them is worked.
        boolean dug = line.style().dug();
        int back = dug ? line.depth() + 1 : 2;
        int ahead = line.style() == LineStyle.TRENCH_WIRE ? LineCourse.PARAPET_WIRE_OFFSET - 1 : dug ? 2 : 0;
        List<int[]> course = new ArrayList<>(works);
        course.addAll(nests);
        for (int[] c : course) {
            for (int k = -back; k <= ahead; k++) {
                int x = c[0] + front[0] * k, y = c[1] + front[1] * k;
                if (worked.contains(PreparedGround.key(x, y)) || ground.isRoad(x, y)) continue;
                if (held != null && ctx.grid.inBounds(x, y) && held[x][y]) continue;
                PreparedGround.workedGround(ctx, x, y);
            }
        }
    }

    /** The road cell nearest the middle of a gap that is open ground, or {@code null}. */
    private static int[] gapMiddle(GenContext ctx, LayoutGround ground, List<int[]> gap) {
        int[] middle = null;
        for (int[] c : gap) {
            if (ground.isRoad(c[0], c[1]) && ctx.grid.inBounds(c[0], c[1]) && ctx.grid.isWalkable(c[0], c[1])) {
                middle = c;
                if (gap.indexOf(c) >= gap.size() / 2) break;
            }
        }
        return middle;
    }

    /**
     * The barricade across each road gap: every open cell of the gap but the {@link #GAP_OPENING} cells round its
     * middle, so the street is closed to all but one lane. A gap with less than two cells to close is left bare.
     */
    private static List<int[]> barricades(GenContext ctx, LayoutGround ground, boolean[][] held,
                                          List<List<int[]>> gaps) {
        List<int[]> out = new ArrayList<>();
        for (List<int[]> gap : gaps) {
            int[] middle = gapMiddle(ctx, ground, gap);
            if (middle == null) continue;
            int at = gap.indexOf(middle);
            List<int[]> row = new ArrayList<>();
            for (int i = 0; i < gap.size(); i++) {
                if (Math.abs(i - at) <= GAP_OPENING / 2) continue;
                int[] c = gap.get(i);
                if (PreparedGround.open(ctx, c[0], c[1]) && (held == null || !held[c[0]][c[1]])) row.add(c);
            }
            if (row.size() >= 2) out.addAll(row);
        }
        return out;
    }

    /**
     * Where a street line meets a house: the first open cell in front of it, for each course cell a house stands
     * on, so the line runs on along the house's front.
     */
    private static List<int[]> houseFronts(GenContext ctx, LayoutGround ground, boolean[][] held, List<int[]> blocked,
                                           int[] front) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] c : blocked) {
            for (int k = 1; k <= HOUSE_REACH; k++) {
                int x = c[0] + front[0] * k, y = c[1] + front[1] * k;
                if (!ctx.grid.inBounds(x, y) || ground.isRoad(x, y)) break;
                if (!PreparedGround.open(ctx, x, y)) continue;
                if ((held == null || !held[x][y]) && seen.add(PreparedGround.key(x, y))) out.add(new int[]{x, y});
                break;
            }
        }
        return out;
    }

    /** Whether the cell may take a work: open ground that is not road, nor held by a site the layout states up front. */
    private static boolean takes(GenContext ctx, LayoutGround ground, boolean[][] held, int[] c) {
        return PreparedGround.open(ctx, c[0], c[1]) && !breaks(ground, c[0], c[1])
                && (held == null || !held[c[0]][c[1]]);
    }

    /**
     * Whether a line of works breaks at the cell: at a road, and at every way when the layout says so
     * ({@link MissionLayout.Ways#worksBreakAtWays}).
     */
    static boolean breaks(LayoutGround ground, int x, int y) {
        return ground.isRoad(x, y) || ground.ways().worksBreakAtWays() && ground.isWay(x, y);
    }

    /**
     * Whether closing {@code cells} would cut the walkable ground apart: the main (largest) 4-connected body of
     * walkable ground shrinks by more than the cells themselves and a little slack for pockets behind the wire.
     */
    static boolean seals(GenContext ctx, List<int[]> cells) {
        int before = largestBody(ctx);
        for (int[] c : cells) ctx.grid.setWalkable(c[0], c[1], false);
        int after = largestBody(ctx);
        for (int[] c : cells) ctx.grid.setWalkable(c[0], c[1], true);
        return before - after > cells.size() + SEAL_SLACK;
    }

    private static int largestBody(GenContext ctx) {
        int w = ctx.width, h = ctx.height;
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int best = 0;
        for (int start = 0; start < w * h; start++) {
            if (seen[start] || !ctx.grid.isWalkable(start % w, start / w)) continue;
            int size = 0;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                size++;
                int x = i % w, y = i / w;
                if (x > 0) visit(ctx, seen, queue, i - 1, x - 1, y);
                if (x < w - 1) visit(ctx, seen, queue, i + 1, x + 1, y);
                if (y > 0) visit(ctx, seen, queue, i - w, x, y - 1);
                if (y < h - 1) visit(ctx, seen, queue, i + w, x, y + 1);
            }
            best = Math.max(best, size);
        }
        return best;
    }

    private static void visit(GenContext ctx, boolean[] seen, ArrayDeque<Integer> queue, int i, int x, int y) {
        if (seen[i] || !ctx.grid.isWalkable(x, y)) return;
        seen[i] = true;
        queue.add(i);
    }

    private static Set<Long> keys(List<int[]> cells) {
        Set<Long> out = new HashSet<>();
        for (int[] c : cells) out.add(PreparedGround.key(c[0], c[1]));
        return out;
    }
    /**
     * Elevation is stamped once per map ({@code ElevationBrush}): whatever is raised first is the map's relief, and
     * every later pass finds elevation already there and does nothing. A layout's ridges and the landscape's own
     * ridges, held bands and valley sides go up together in one plan, but plateaus are planned on their own, so a
     * plan that asks for plateaus beside a layout Ridge is refused here, loudly, rather than losing them without a
     * word.
     */
    private static void refuseSecondElevation(GenContext ctx, List<Piece> pieces) {
        String reason = secondElevation(pieces, OutdoorLandscapeStage.plateausWanted(ctx));
        if (reason != null) throw new IllegalStateException(reason);
    }

    /** Why this layout's ridges cannot be raised on this map, or null when they can. */
    static String secondElevation(List<Piece> pieces, int plateaus) {
        boolean ridged = false;
        for (Piece piece : pieces) ridged |= piece instanceof Ridge;
        if (ridged && plateaus > 0) {
            return "a mission layout Ridge and plateaus cannot share a map: elevation is stamped once";
        }
        return null;
    }
}
