package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BiPredicate;

/**
 * What a Cordon redoubt and the logistics base are built from, by density ({@code cordon-map-intent.md}, By
 * density; {@code cordon-allied-forces.md}, Base): the owner's picks for the town and urban redoubt and the urban
 * base, each option built on the real pooled Cordon map of its density and drawn through the look preset (real
 * ground, effects and shadows, roofs, no annotations) at play zoom and close.
 *
 * <p>Today's compound is the generated map untouched. Every other option clears the generated works off the same
 * ground and builds its own from what the generator already has ({@link CordonStructureWorks}): production building
 * stamps, walls in the colony's and the installation's own materials, sandbags, fences, hedgehogs, crate stacks and
 * the auto shop's bay and lift columns. The battle is then stood up on that map as the mission stands it up. A
 * redoubt the garrison's wall dressing cannot read (it reads only the compound's dark perimeter wall) is manned by
 * hand the way the dressing mans one: three wall teams on its loopholes and sandbags, the front first, and a gun in
 * each sandbagged nest. A swarm pack then comes at the redoubt from the front, so the stills show the works in a
 * fight; a base is played quietly with a Hound standing in its gantry.
 *
 * <p>Writes {@code <option>-play.png} and {@code <option>-close.png} per option (and {@code -wide.png} for a base,
 * which is wider than a play-zoom frame). {@code -Dsnapshot.cordonStructure.options=town-farm,urban-block} narrows the
 * run; {@code .townSeed} (8) and {@code .urbanSeed} (6) pick the pooled maps. Opt-in: a design question, not part of
 * {@code all}.
 */
public final class CordonStructureLookSuite implements SnapshotSuite {

    static final int W = LookReview.WIDTH, H = LookReview.HEIGHT;
    static final float PLAY_CELLS = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
    static final float CLOSE_CELLS = 40f;
    static final float WIDE_CELLS = 92f;
    /** Ticks a redoubt is fought for, and how often a still is considered. */
    static final int TICKS = 420, EVERY = 6, FIRST_STILL = 90;
    /** Ticks a base is played before its still. */
    static final int BASE_TICKS = 150;
    /** Cells in front of a redoubt's front face the look's swarm pack starts. */
    static final int PACK_OUT = 26;
    /** Half the side of a redoubt's ground, from its middle: the generated compound's wall box is 25 across. */
    static final int R = 12;
    private static final String ISSUER = "cordon-structure-look";
    private static final String MASONRY = "colony.masonry-wall";
    private static final String CERAMIC = "colony.ceramic-wall";
    private static final String BLAST = InstallationWalls.BUILDING;
    private static final String HEDGEHOG = "doodad.fort-hedgehog";
    private static final String CRATES = "doodad.industrial-crate-stack";

    @Override public String id() { return "cordon-structure-look"; }

    @Override public String label() { return "Cordon: what a redoubt and the logistics base are built from, by density"; }

    @Override public boolean inAll() { return false; }

    enum Subject { REDOUBT, BASE }

    /** What a builder leaves for the battle: where to look, its guns, its sandbag posts and the Hounds in its gantries. */
    record Built(float focusU, float focusV, List<int[]> guns, List<int[]> posts, List<int[]> hounds) {
        static Built at(float u, float v) {
            return new Built(u, v, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        Built focusedOn(float u, float v) {
            return new Built(u, v, guns, posts, hounds);
        }
    }

    interface Builder {
        Built build(CordonStructureWorks works, Frame frame, Site site);
    }

    /** One picture card. {@code builder} null is today's generated works, untouched. */
    record Option(String key, CordonPlan.Density density, Subject subject, Builder builder) { }

    /** The ground an option stands on: the generated works' bounds, and the way to the front. */
    record Site(int x0, int y0, int x1, int y1, int front) {
        int cx() { return (x0 + x1) / 2; }
        int cy() { return (y0 + y1) / 2; }
    }

    static List<Option> options() {
        return List.of(
                new Option("town-compound", CordonPlan.Density.TOWN, Subject.REDOUBT, null),
                new Option("town-farm", CordonPlan.Density.TOWN, Subject.REDOUBT, CordonStructureLookSuite::farm),
                new Option("town-school", CordonPlan.Density.TOWN, Subject.REDOUBT, CordonStructureLookSuite::school),
                new Option("urban-compound", CordonPlan.Density.URBAN, Subject.REDOUBT, null),
                new Option("urban-block", CordonPlan.Density.URBAN, Subject.REDOUBT, CordonStructureLookSuite::block),
                new Option("urban-tenement", CordonPlan.Density.URBAN, Subject.REDOUBT, CordonStructureLookSuite::tenement),
                new Option("base-shed", CordonPlan.Density.URBAN, Subject.BASE, null),
                new Option("base-warehouse", CordonPlan.Density.URBAN, Subject.BASE, CordonStructureLookSuite::warehouse),
                new Option("base-carpark", CordonPlan.Density.URBAN, Subject.BASE, CordonStructureLookSuite::carPark));
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        String wanted = System.getProperty("snapshot.cordonStructure.options", "all").trim();
        long townSeed = Long.parseLong(System.getProperty("snapshot.cordonStructure.townSeed", "8").trim());
        long urbanSeed = Long.parseLong(System.getProperty("snapshot.cordonStructure.urbanSeed", "6").trim());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (Option option : options()) {
            if (!wanted.equals("all") && !List.of(wanted.split("\\s*,\\s*")).contains(option.key())) continue;
            long seed = option.density() == CordonPlan.Density.TOWN ? townSeed : urbanSeed;
            out.addAll(render(context, option, seed));
        }
        return out;
    }

    private List<SnapshotArtifact> render(SnapshotContext context, Option option, long seed) throws Exception {
        CordonSetup.CordonMap cordon = CordonSetup.map(CordonVariant.LINE, option.density(), SurfacePalette.VERDANT, seed);
        MapResult map = cordon.map();
        int front = CordonFront.frontward(cordon.plan());
        Site site = option.subject() == Subject.REDOUBT ? redoubtSite(cordon, front) : baseSite(cordon, front);
        Frame frame = new Frame(site.cx(), site.cy(), front);
        System.out.printf("    [cordon-structure-look/%s] %s seed %d, site %d,%d..%d,%d, front %+d%n", option.key(),
                option.density(), seed, site.x0(), site.y0(), site.x1(), site.y1(), front);
        CordonStructureWorks works = null;
        Built built;
        if (option.builder() == null) {
            built = Built.at(0f, option.subject() == Subject.REDOUBT ? 2f : 0f);
        } else {
            works = new CordonStructureWorks(map, seed);
            built = option.builder().build(works, frame, site);
            works.finish();
            // The base's shed is gone with its berths: nothing on this map is built at a berth any more.
            if (option.subject() == Subject.BASE) cordon = withoutBerths(cordon);
        }
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = CordonSetup.create(cordon, CordonVariant.LINE, List.of(),
                CordonSequence.Timetable.STANDARD)) {
            if (works != null) sim.setBuildings(works.buildings());
            Random rng = new Random(seed ^ 0x5354525543L);
            float fx0 = frame.fx(built.focusU(), built.focusV()), fy0 = frame.fy(built.focusU(), built.focusV());
            if (option.subject() == Subject.REDOUBT) {
                boolean manned = false;
                for (CordonRedoubt r : sim.getCordon().redoubts()) manned |= r.contains(site.cx(), site.cy(), 0);
                System.out.printf("    [cordon-structure-look/%s] the garrison's dressing %s%n", option.key(),
                        manned ? "read and manned it" : "could not read it; manned by hand");
                if (!manned) garrison(sim, option.key(), built, site, frame, rng);
                int[] from = frame.cell(Math.round(built.focusU()), R + PACK_OUT);
                pack(sim, from[0], from[1], Math.round(fx0), Math.round(fy0));
            } else {
                for (int[] at : built.hounds()) hound(sim, at[0], at[1]);
                if (!built.posts().isEmpty()) {
                    int squad = ColonyMilitia.seatTeam(sim, built.posts(), "Depot guard", RiskLevel.HIGH, rng);
                    sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, built.posts().get(0)[0],
                            built.posts().get(0)[1], 4), CommandAuthority.GARRISON, ISSUER, "depot guard holds");
                }
            }
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            BattleReviewFrameRenderer play = BattleReviewFrameRenderer.look(context.modRoot(), W, H, fx0, fy0, PLAY_CELLS);
            float cx = frame.fx(0, 0), cy = frame.fy(0, 0);
            boolean redoubt = option.subject() == Subject.REDOUBT;
            BattleReviewFrameRenderer close = BattleReviewFrameRenderer.look(context.modRoot(), W, H,
                    redoubt ? frame.fx(built.focusU(), built.focusV() - 2f) : fx0,
                    redoubt ? frame.fy(built.focusU(), built.focusV() - 2f) : fy0, CLOSE_CELLS);
            if (!redoubt) {
                for (int t = 0; t < BASE_TICKS; t++) {
                    sim.advance(BattleSimulation.TICK_DT);
                    fx.feed(sim);
                }
                out.add(new SnapshotArtifact(option.key() + "-play.png", play.render(sim, "", ReviewAnnotations.NONE, fx)));
                out.add(new SnapshotArtifact(option.key() + "-close.png", close.render(sim, "", ReviewAnnotations.NONE, fx)));
                out.add(new SnapshotArtifact(option.key() + "-wide.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        W, H, cx, cy, WIDE_CELLS).render(sim, "", ReviewAnnotations.NONE, fx)));
                return out;
            }
            BufferedImage bestPlay = null, bestClose = null;
            int best = -1, bestTick = -1, fired = 0;
            for (int tick = 1; tick <= TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
                fired += sim.getShotsThisFrame().size();
                if (tick < FIRST_STILL || tick % EVERY != 0) continue;
                BufferedImage still = play.render(sim, "", ReviewAnnotations.NONE, fx);
                int painted = fx.lastPainted();
                if (painted > best) {
                    best = painted;
                    bestTick = tick;
                    bestPlay = still;
                    bestClose = close.render(sim, "", ReviewAnnotations.NONE, fx);
                }
            }
            System.out.printf("    [cordon-structure-look/%s] %d rounds fired; still at tick %d (%d effect particles)%n",
                    option.key(), fired, bestTick, best);
            out.add(new SnapshotArtifact(option.key() + "-play.png", bestPlay));
            out.add(new SnapshotArtifact(option.key() + "-close.png", bestClose));
        }
        return out;
    }

    // ---------------------------------------------------------------- the options

    /**
     * Town: a fortified farm. A farmhouse and a barn make the front face, a second barn the flank; a stone yard wall
     * closes the rest round a worked-earth farmyard, with a sandbagged gun nest inside each gate.
     */
    static Built farm(CordonStructureWorks w, Frame f, Site site) {
        List<Entry> entries = entries(w, f, R + 1);
        CordonStructureWorks.Ground grass = w.sample(site.cx(), site.cy(), GroundKind.GRASS);
        f.clear(w, -R - 3, -R - 3, R + 3, R + 3, grass, null);
        Built built = Built.at(0f, 2f);
        for (int v = -R + 1; v <= 3; v++) for (int u = -R + 1; u <= R - 1; u++) f.worked(w, u, v);
        f.stamp(w, BuildingStamp.FARMHOUSE, -R, 5, 0);
        f.stamp(w, BuildingStamp.BARN, -1, 4, 0);
        f.stamp(w, BuildingStamp.BARN, -R, -R + 1, 1);
        // The yard wall: the rear and the open flank, the gap between the front buildings, the flank's gaps.
        f.wall(w, MASONRY, -R, -R, R, -R, gaps(entries, 'R'));
        f.wall(w, MASONRY, R, -R, R, R - 1, gaps(entries, 'E'));
        f.wall(w, MASONRY, -3, R - 1, -2, R - 1);
        f.wall(w, MASONRY, -R, 2, -R, 4);
        gateNests(w, f, entries, built, 'R', 'E');
        tracks(w, f, entries);
        // Farm stores and the farm's own truck in the yard.
        f.obstacle(w, 4, -2, "doodad.industrial-pallet-stack");
        f.obstacle(w, 5, -2, "doodad.industrial-pallet-stack");
        f.obstacle(w, 4, -3, "doodad.industrial-pallet-stack");
        f.prop(w, 8, 0, "main-stores.drums", 0, true);
        f.prop(w, -3, -6, "doodad.parked-flatbed-truck", 1, true);
        return built;
    }

    /**
     * Town: a fortified school. The school's courtyard block is the front face, its hall the flank; a ceramic yard
     * wall closes the paved playground behind them, sandbags line the playground's open front, and a sandbagged gun
     * nest stands inside each gate.
     */
    static Built school(CordonStructureWorks w, Frame f, Site site) {
        List<Entry> entries = entries(w, f, R + 1);
        CordonStructureWorks.Ground grass = w.sample(site.cx(), site.cy(), GroundKind.GRASS);
        f.clear(w, -R - 3, -R - 3, R + 3, R + 3, grass, null);
        Built built = Built.at(0f, 2f);
        CordonStructureWorks.Ground paving = new CordonStructureWorks.Ground(GroundKind.COURTYARD,
                w.topology.surfaceIndex("colony.terracotta"), -1);
        f.floor(w, -R + 1, -R + 1, R - 1, R - 2, paving);
        f.stamp(w, BuildingStamp.COURT_HOUSE, -6, 2, 0);
        f.stamp(w, BuildingStamp.MARKET_HALL, -R, -R + 1, 1);
        f.wall(w, CERAMIC, -R, R - 1, -7, R - 1);
        f.wall(w, CERAMIC, 7, R - 1, R, R - 1);
        f.wall(w, CERAMIC, -R, 2, -R, R - 2);
        f.wall(w, CERAMIC, -R, -R, R, -R, gaps(entries, 'R'));
        f.wall(w, CERAMIC, R, -R + 1, R, R - 2, gaps(entries, 'E'));
        gateNests(w, f, entries, built, 'R', 'E');
        // The playground's firing step: sandbags inside the front wall's open stretches, facing the front.
        for (int u = 8; u <= R - 1; u++) if (f.sandbag(w, u, R - 2, 0, 1)) built.posts().add(f.cell(u, R - 2));
        for (int u = -R + 1; u <= -8; u++) if (f.sandbag(w, u, R - 2, 0, 1)) built.posts().add(f.cell(u, R - 2));
        tracks(w, f, entries);
        return built;
    }

    /**
     * Urban: the buildings round a crossroads walled up. A building on each corner of the crossing, their outer
     * faces the redoubt's face; a blast wall across each street arm closes the gaps, a gate in it on the road with a
     * gun nest behind and hedgehogs in front.
     */
    static Built block(CordonStructureWorks w, Frame f, Site site) {
        int[] crossing = crossing(w, site);
        Frame c = new Frame(crossing[0], crossing[1], f.f);
        int a = Math.max(1, (crossing[3] - crossing[2] + 1) / 2), b = Math.max(1, (crossing[5] - crossing[4] + 1) / 2);
        CordonStructureWorks.Ground pavement = w.sample(crossing[0], crossing[1], GroundKind.SIDEWALK);
        int reach = 19;
        c.clear(w, -reach, -reach, reach, reach, pavement, (x, y) -> road(w, x, y)
                && (inBand(x, crossing[2], crossing[3]) || inBand(y, crossing[4], crossing[5])));
        Built built = Built.at(0f, 0f);
        int off = 2;
        // Corner buildings: each one's near corner a pavement's width off both roads.
        int[] ne = place(w, c, BuildingStamp.MARKET_HALL, 0, a + off, b + off, 1, 1);
        int[] nw = place(w, c, BuildingStamp.CORNER_HOME, 0, -a - off, b + off, -1, 1);
        int[] se = place(w, c, BuildingStamp.CORNER_SHOP, 2, a + off, -b - off, 1, -1);
        int[] sw = place(w, c, BuildingStamp.OFFSET_DEPOT, 2, -a - off, -b - off, -1, -1);
        // Each building's local bounds {u0, v0, u1, v1}. An arm's wall runs where both its buildings stand.
        int frontV = Math.min(ne[3], nw[3]), rearV = Math.max(se[1], sw[1]);
        int eastU = Math.min(ne[2], se[2]), westU = Math.max(nw[0], sw[0]);
        int gate = 1, gun = gate + 1;
        // A double blast wall across each arm, a gate on the road; beside each gate a gun in the wall's own line,
        // the way the compound's gun towers stand in theirs.
        for (int d = 0; d < 2; d++) {
            int[] tower = d == 0 ? new int[]{gun, gun} : new int[]{-gate, gate};
            int[] rearTower = d == 0 ? new int[]{-gun, -gun} : new int[]{-gate, gate};
            c.wall(w, BLAST, nw[2] + 1, frontV - d, ne[0] - 1, frontV - d, new int[]{-gate, gate}, tower);
            c.wall(w, BLAST, sw[2] + 1, rearV + d, se[0] - 1, rearV + d, new int[]{-gate, gate}, rearTower);
            c.wall(w, BLAST, eastU - d, se[3] + 1, eastU - d, ne[1] - 1, new int[]{-gate, gate}, rearTower);
            c.wall(w, BLAST, westU + d, sw[3] + 1, westU + d, nw[1] - 1, new int[]{-gate, gate}, tower);
        }
        built.guns().add(c.cell(gun, frontV));
        built.guns().add(c.cell(-gun, rearV));
        built.guns().add(c.cell(eastU, -gun));
        built.guns().add(c.cell(westU, gun));
        // Hedgehogs staggered outside each gate: a man walks through, a column has to slow for them.
        for (int k : new int[]{-1, 1}) {
            c.obstacle(w, k * 2, frontV + 3 + (k > 0 ? 2 : 0), HEDGEHOG);
            c.obstacle(w, k * 2, rearV - 3 - (k > 0 ? 2 : 0), HEDGEHOG);
            c.obstacle(w, eastU + 3 + (k > 0 ? 2 : 0), k * 2, HEDGEHOG);
            c.obstacle(w, westU - 3 - (k > 0 ? 2 : 0), k * 2, HEDGEHOG);
        }
        int[] at = c.cell(0, 0);
        return built.focusedOn(f.u(at[0], at[1]), f.v(at[0], at[1]) + 2f);
    }

    /**
     * Urban: a single fortified apartment block. Four courtyard tenements built as one block across the road behind
     * the crossing, a light well between its two rows; sandbags along the pavement in front, a gun nest at each front
     * corner, and hedgehogs across the street arms it covers.
     */
    static Built tenement(CordonStructureWorks w, Frame f, Site site) {
        int[] crossing = crossing(w, site);
        Frame c = new Frame(crossing[0], crossing[1], f.f);
        int b = Math.max(1, (crossing[5] - crossing[4] + 1) / 2);
        CordonStructureWorks.Ground pavement = w.sample(crossing[0], crossing[1], GroundKind.SIDEWALK);
        int top = -b - 3;
        c.clear(w, -17, top - 25, 16, top + 1, pavement, (x, y) -> road(w, x, y) && inBand(y, crossing[4], crossing[5]));
        Built built = Built.at(0f, 0f);
        c.stamp(w, BuildingStamp.COURT_HOUSE, -13, top - 9, 0);
        c.stamp(w, BuildingStamp.COURT_HOUSE, 0, top - 9, 0);
        c.stamp(w, BuildingStamp.COURT_HOUSE, -13, top - 21, 2);
        c.stamp(w, BuildingStamp.COURT_HOUSE, 0, top - 21, 2);
        // The light well's ends walled.
        c.wall(w, BLAST, -13, top - 11, -13, top - 10);
        c.wall(w, BLAST, 12, top - 11, 12, top - 10);
        // Sandbags along the pavement in front of the block, facing the street.
        for (int u = -12; u <= 11; u++) if (c.sandbag(w, u, top + 1, 0, 1)) built.posts().add(c.cell(u, top + 1));
        nest(w, c, built, -15, top, 0, 1);
        nest(w, c, built, 14, top, 0, 1);
        // Hedgehogs across the arm in front and along the line's road either side.
        for (int k = -2; k <= 2; k += 2) c.obstacle(w, k, b + 4 + (k == 0 ? 1 : 0), HEDGEHOG);
        for (int k = -b; k <= b; k += 2) {
            c.obstacle(w, -19, k, HEDGEHOG);
            c.obstacle(w, 18, k, HEDGEHOG);
        }
        int[] at = c.cell(0, top - 6);
        return built.focusedOn(f.u(at[0], at[1]), f.v(at[0], at[1]));
    }

    /**
     * Urban base: a depot in a warehouse block. Two freight warehouses on the front side of the road, their loading
     * doors on an apron where the trucks stand; behind the road a yard with a mech gantry (the auto shop's bay between
     * lift columns, a Hound standing in it) and a second gantry empty, the depot office and its stores; an
     * industrial fence round it all.
     */
    static Built warehouse(CordonStructureWorks w, Frame f, Site site) {
        int[] road = roadRows(w, f, site);
        int hu = (site.x1() - site.x0()) / 2, hv = (site.y1() - site.y0()) / 2;
        CordonStructureWorks.Ground yard = w.sample(site.cx(), site.cy(), GroundKind.SIDEWALK);
        f.clear(w, -hu - 1, -hv - 1, hu + 1, hv + 1, yard, (x, y) -> road(w, x, y) && inBand(f.v(x, y), road[0], road[1]));
        Built built = Built.at(0f, road[0] - 3f);
        // The warehouses, doors on the apron by the road.
        int apron = road[1] + 1;
        f.stamp(w, BuildingStamp.FREIGHT_L, -hu + 2, apron + 4, 0);
        f.stamp(w, BuildingStamp.FREIGHT_T, 2, apron + 4, 0);
        for (int u : new int[]{-hu + 4, -hu + 8, 4, 8, 14}) f.prop(w, u, apron, "doodad.parked-cargo-truck", 0, true);
        // Behind the road: the gantry yard.
        int v0 = road[0] - 12;
        gantry(w, f, -12, v0, built, true);
        gantry(w, f, -1, v0, built, false);
        f.stamp(w, BuildingStamp.OFFSET_DEPOT, hu - 12, road[0] - 15, 2);
        for (int u = -hu + 3; u <= -hu + 8; u++) f.obstacle(w, u, road[0] - 17, "doodad.industrial-pallet-stack");
        f.prop(w, -hu + 3, v0 + 2, "main-stores.cases", 0, true);
        f.prop(w, -hu + 3, v0 + 5, "main-stores.drums", 0, true);
        f.prop(w, -hu + 6, v0 + 2, "main-stores.pallet", 0, true);
        fenceRing(w, f, hu, hv, road);
        for (int u = -6; u <= -2; u++) built.posts().add(f.cell(u, road[0] - 3));
        return built;
    }

    /**
     * Urban base: a depot in a car park. The lot under asphalt, trucks and vans parked in rows on the front side of
     * the road, each bay's head marked; behind it two open-air gantries with a Hound standing in one, a site office
     * and stacked stores; a stockade of stacked crates round it with sandbagged ways in.
     */
    static Built carPark(CordonStructureWorks w, Frame f, Site site) {
        int[] road = roadRows(w, f, site);
        int hu = (site.x1() - site.x0()) / 2, hv = (site.y1() - site.y0()) / 2;
        CordonStructureWorks.Ground asphalt = new CordonStructureWorks.Ground(GroundKind.COURTYARD,
                w.topology.surfaceIndex("pavement.asphalt.worn"), -1);
        f.clear(w, -hu - 1, -hv - 1, hu + 1, hv + 1, asphalt, (x, y) -> road(w, x, y) && inBand(f.v(x, y), road[0], road[1]));
        Built built = Built.at(0f, road[0] - 3f);
        String[] vehicles = {"doodad.parked-cargo-truck", "doodad.parked-box-van", "doodad.parked-flatbed-truck",
                "doodad.parked-utility-truck", "doodad.parked-tanker-truck", "doodad.parked-armored-apc"};
        int n = 0;
        // Parking rows on the front side of the road, each bay's head marked.
        for (int row = 0; row < 3; row++) {
            int v = road[1] + 3 + row * 6;
            for (int k = 0, u = -hu + 3; u + 1 <= hu - 3; u += 3, k++) {
                f.prop(w, u, v + 3, "doodad.fl-striped-yellow", 0, false);
                f.prop(w, u + 1, v + 3, "doodad.fl-striped-yellow", 0, false);
                if ((k * 7 + row * 3) % 5 != 0) f.prop(w, u, v, vehicles[n++ % vehicles.length], 1, true);
            }
        }
        // Behind the road: the gantries in the open, the site office and the stores.
        int v0 = road[0] - 12;
        gantry(w, f, -12, v0, built, true);
        gantry(w, f, -1, v0, built, false);
        f.stamp(w, BuildingStamp.CORNER_SHOP, hu - 12, road[0] - 15, 2);
        for (int u = -hu + 3; u <= -hu + 9; u += 2) {
            f.prop(w, u, road[0] - 18, "main-stores.cases", 0, true);
            f.prop(w, u, road[0] - 21, "main-stores.cartons", 0, true);
        }
        stockade(w, f, hu, hv, road);
        for (int u = -6; u <= -2; u++) built.posts().add(f.cell(u, road[0] - 3));
        return built;
    }

    // ---------------------------------------------------------------- pieces

    /** A gantry: the auto shop's bay floor between four lift columns, a service run beside; a Hound in it if asked. */
    static void gantry(CordonStructureWorks w, Frame f, int u0, int v0, Built built, boolean hound) {
        f.prop(w, u0, v0, "auto-shop.bay", 0, false);
        for (int du : new int[]{-1, 5}) for (int dv : new int[]{-1, 7}) f.prop(w, u0 + du, v0 + dv, "auto-shop.lift-column", 0, true);
        f.prop(w, u0 + 6, v0, "auto-shop.service-run", 0, true);
        f.prop(w, u0 - 2, v0 + 2, "auto-shop.tool-chest", 0, true);
        if (hound) built.hounds().add(f.cell(u0 + 2, v0 + 3));
    }

    /** A sandbagged gun nest: the gun at {@code (u, v)}, bags across the side facing {@code (du, dv)} and its flanks. */
    static void nest(CordonStructureWorks w, Frame f, Built built, int u, int v, int du, int dv) {
        int pu = dv, pv = du;
        for (int k = -1; k <= 1; k++) f.sandbag(w, u + du + pu * k, v + dv + pv * k, du, dv);
        f.sandbag(w, u - pu, v - pv, -pu, -pv);
        f.sandbag(w, u + pu, v + pv, pu, pv);
        built.guns().add(f.cell(u, v));
    }

    /** A gun nest inside each gate on the named sides, beside the way in. */
    static void gateNests(CordonStructureWorks w, Frame f, List<Entry> entries, Built built, char... sides) {
        for (Entry e : entries) {
            boolean wanted = false;
            for (char s : sides) wanted |= e.side() == s;
            if (!wanted) continue;
            for (int beside : new int[]{e.hi() + 3, e.lo() - 3}) {
                int[] at = switch (e.side()) {
                    case 'R' -> new int[]{beside, -R + 2, 0, -1};
                    case 'F' -> new int[]{beside, R - 2, 0, 1};
                    case 'E' -> new int[]{R - 2, beside, 1, 0};
                    default -> new int[]{-R + 2, beside, -1, 0};
                };
                if (!f.roomFor(w, at[0], at[1])) continue;
                nest(w, f, built, at[0], at[1], at[2], at[3]);
                break;
            }
        }
    }

    /** Worked-earth tracks from each road coming in to the middle of the yard. */
    static void tracks(CordonStructureWorks w, Frame f, List<Entry> entries) {
        for (Entry e : entries) {
            for (int t = R + 3; t >= 0; t--) {
                for (int k = e.lo(); k <= e.hi(); k++) {
                    switch (e.side()) {
                        case 'R' -> f.worked(w, k, -t);
                        case 'F' -> f.worked(w, k, t);
                        case 'E' -> f.worked(w, t, k);
                        default -> f.worked(w, -t, k);
                    }
                }
            }
        }
    }

    /** An industrial fence on the base's edge, open where the roads come in. */
    static void fenceRing(CordonStructureWorks w, Frame f, int hu, int hv, int[] road) {
        List<Entry> entries = entries(w, f, Math.max(hu, hv) + 2);
        for (int u = -hu; u <= hu; u++) {
            if (!open(entries, 'F', u)) f.fence(w, u, hv, true);
            if (!open(entries, 'R', u)) f.fence(w, u, -hv, true);
        }
        for (int v = -hv + 1; v <= hv - 1; v++) {
            if (inBand(v, road[0] - 1, road[1] + 1)) continue;
            if (!open(entries, 'E', v)) f.fence(w, hu, v, false);
            if (!open(entries, 'W', v)) f.fence(w, -hu, v, false);
        }
    }

    /** A stockade of stacked crates on the base's edge, sandbags either side of each way in. */
    static void stockade(CordonStructureWorks w, Frame f, int hu, int hv, int[] road) {
        List<Entry> entries = entries(w, f, Math.max(hu, hv) + 2);
        for (int u = -hu; u <= hu; u++) {
            stack(w, f, entries, 'F', u, u, hv, 0, 1);
            stack(w, f, entries, 'R', u, u, -hv, 0, -1);
        }
        for (int v = -hv + 1; v <= hv - 1; v++) {
            if (inBand(v, road[0] - 1, road[1] + 1)) {
                if (v == road[0] - 1 || v == road[1] + 1) {
                    f.sandbag(w, hu - 1, v, 1, 0);
                    f.sandbag(w, -hu + 1, v, -1, 0);
                }
                continue;
            }
            stack(w, f, entries, 'E', v, hu, v, 1, 0);
            stack(w, f, entries, 'W', v, -hu, v, -1, 0);
        }
    }

    private static void stack(CordonStructureWorks w, Frame f, List<Entry> entries, char side, int along, int u, int v,
                              int du, int dv) {
        if (open(entries, side, along)) return;
        if (open(entries, side, along - 1) || open(entries, side, along + 1)) {
            f.sandbag(w, u - du, v - dv, du, dv);
            return;
        }
        f.obstacle(w, u, v, CRATES);
    }

    private static boolean open(List<Entry> entries, char side, int along) {
        for (Entry e : entries) if (e.side() == side && along >= e.lo() - 1 && along <= e.hi() + 1) return true;
        return false;
    }

    /**
     * Places a stamp in a corner of the crossing: its near corner at local {@code (u, v)}, growing away along
     * {@code (su, sv)}. Returns its local bounds {@code {u0, v0, u1, v1}}.
     */
    static int[] place(CordonStructureWorks w, Frame c, BuildingStamp stamp, int turns, int u, int v, int su, int sv) {
        int[] size = CordonStructureWorks.size(stamp, turns);
        int u0 = su > 0 ? u : u - size[0] + 1, v0 = sv > 0 ? v : v - size[1] + 1;
        c.stamp(w, stamp, u0, v0, turns);
        return new int[]{u0, v0, u0 + size[0] - 1, v0 + size[1] - 1};
    }

    // ---------------------------------------------------------------- the ground

    /** The same map and plan with no berths on it: the same world, its shed's berths gone with the shed. */
    static CordonSetup.CordonMap withoutBerths(CordonSetup.CordonMap cordon) {
        MapResult m = cordon.map();
        MapResult bare = new MapResult(m.grid, m.topology, m.marineSpawnX, m.marineSpawnY, m.defenderSpawnX,
                m.defenderSpawnY, m.pointsOfInterest, m.doodads, m.tacticalMap, m.buildings, m.defensePosts, m.roadGraph,
                m.landingPads, m.landingAreas, m.biomeMap, List.of(), m.fixtureTasks, m.runways, m.shelters,
                m.vehicleCorridor, m.frontDepth, m.lanes).withFieldOfFire(m.fieldOfFire).withLayout(m.layout);
        if (m.initialDamage != null) bare = bare.withInitialDamage(m.initialDamage);
        return new CordonSetup.CordonMap(bare, cordon.plan(), cordon.battleSeed(), cordon.corridor(), cordon.biome(),
                cordon.density());
    }

    /** The generated redoubt 2's wall box and front. */
    static Site redoubtSite(CordonSetup.CordonMap cordon, int front) {
        Precinct p = CordonPlan.redoubts(cordon.plan()).get(1);
        int[] box = CordonGarrisons.wallBox(cordon.map().topology, p);
        if (box == null) box = new int[]{p.seedX() - R, p.seedY() - R, p.seedX() + R, p.seedY() + R};
        return new Site(box[0], box[1], box[2], box[3], front);
    }

    /** The logistics base's fence box and front. */
    static Site baseSite(CordonSetup.CordonMap cordon, int front) {
        Precinct base = null;
        for (Precinct p : cordon.plan().precincts()) if (p.name().equals(CordonPlan.BASE_PLACE)) base = p;
        if (base == null) throw new IllegalStateException("no logistics base on this map");
        CellTopology topology = cordon.map().topology;
        int lx = Integer.MAX_VALUE, ly = Integer.MAX_VALUE, hx = Integer.MIN_VALUE, hy = Integer.MIN_VALUE;
        int reach = 40;
        for (int y = base.seedY() - reach; y <= base.seedY() + reach; y++) {
            for (int x = base.seedX() - reach; x <= base.seedX() + reach; x++) {
                if (!topology.inBounds(x, y) || !topology.isWall(x, y)
                        || !InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y))) continue;
                lx = Math.min(lx, x);
                ly = Math.min(ly, y);
                hx = Math.max(hx, x);
                hy = Math.max(hy, y);
            }
        }
        if (lx > hx) return new Site(base.seedX() - 22, base.seedY() - 22, base.seedX() + 22, base.seedY() + 22, front);
        return new Site(lx, ly, hx, hy, front);
    }

    /** Whether the cell is road: a street, or hard pavement the road surface laid. */
    static boolean road(CordonStructureWorks w, int x, int y) {
        if (!w.grid.inBounds(x, y) || w.topology.isWall(x, y)) return false;
        if (w.topology.isStreet(x, y)) return true;
        RoadSurface surface = w.topology.roadSurface();
        return surface != null && surface.underlay(x, y) != null && surface.underlay(x, y).hard();
    }

    static boolean inBand(int value, int lo, int hi) {
        return value >= Math.min(lo, hi) && value <= Math.max(lo, hi);
    }

    /**
     * The crossing nearest the site: the road column and road row through the site's ground with the longest runs
     * of road. Returns {@code {x, y, colLo, colHi, rowLo, rowHi}}.
     */
    static int[] crossing(CordonStructureWorks w, Site site) {
        int pad = 14;
        int x0 = site.x0() - pad, x1 = site.x1() + pad, y0 = site.y0() - pad, y1 = site.y1() + pad;
        int[] col = band(w, x0, x1, y0, y1, true, 0.7);
        int[] row = band(w, y0, y1, x0, x1, false, 0.7);
        int x = (col[0] + col[1]) / 2, y = (row[0] + row[1]) / 2;
        System.out.printf("    [cordon-structure-look] crossing at %d,%d (road columns %d..%d, rows %d..%d)%n",
                x, y, col[0], col[1], row[0], row[1]);
        return new int[]{x, y, col[0], col[1], row[0], row[1]};
    }

    /** The run of neighbouring lines (columns when {@code columns}) round the most-road line, each this share of it. */
    private static int[] band(CordonStructureWorks w, int a0, int a1, int b0, int b1, boolean columns, double share) {
        int[] count = new int[a1 - a0 + 1];
        int bestLine = a0, best = -1;
        for (int a = a0; a <= a1; a++) {
            for (int b = b0; b <= b1; b++) if (columns ? road(w, a, b) : road(w, b, a)) count[a - a0]++;
            if (count[a - a0] > best) {
                best = count[a - a0];
                bestLine = a;
            }
        }
        int lo = bestLine, hi = bestLine;
        while (lo - 1 >= a0 && count[lo - 1 - a0] >= best * share) lo--;
        while (hi + 1 <= a1 && count[hi + 1 - a0] >= best * share) hi++;
        return new int[]{lo, hi};
    }

    /** The main road through the base, as local {@code v} rows {@code {rear, front}}. */
    static int[] roadRows(CordonStructureWorks w, Frame f, Site site) {
        int[] row = band(w, site.y0(), site.y1(), site.x0(), site.x1(), false, 0.95);
        int a = f.v(site.cx(), row[0]), b = f.v(site.cx(), row[1]);
        System.out.printf("    [cordon-structure-look] base road rows %d..%d (local %d..%d)%n", row[0], row[1],
                Math.min(a, b), Math.max(a, b));
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }

    /** A road coming in to the works: which side, and its span along that side in local cells. */
    record Entry(char side, int lo, int hi) { }

    /**
     * The roads that reach the works' edge, within a few cells of a ring {@code ring} cells out: 'F' front, 'R' rear,
     * 'E' the +u flank, 'W' the -u flank.
     */
    static List<Entry> entries(CordonStructureWorks w, Frame f, int ring) {
        List<Entry> out = new ArrayList<>();
        for (char side : new char[]{'F', 'R', 'E', 'W'}) {
            int start = Integer.MIN_VALUE;
            for (int t = -ring + 1; t <= ring; t++) {
                boolean on = false;
                // A road reaching to within a few cells of the edge counts: a compound's gate road stops at its wall.
                for (int d = ring; t < ring && d <= ring + 3 && !on; d++) {
                    int u = side == 'E' ? d : side == 'W' ? -d : t;
                    int v = side == 'F' ? d : side == 'R' ? -d : t;
                    on = road(w, f.x(u, v), f.y(u, v));
                }
                if (on && start == Integer.MIN_VALUE) start = t;
                if (!on && start != Integer.MIN_VALUE) {
                    // A road coming in, not one running along the side: a gate's width at most, at its middle.
                    int lo = start, hi = t - 1;
                    if (hi - lo > 6) {
                        int mid = (lo + hi) / 2;
                        lo = mid - 2;
                        hi = mid + 2;
                    }
                    out.add(new Entry(side, lo, hi));
                    start = Integer.MIN_VALUE;
                }
            }
        }
        System.out.printf("    [cordon-structure-look] roads in: %s%n", out);
        return out;
    }

    static int[][] gaps(List<Entry> entries, char side) {
        List<int[]> out = new ArrayList<>();
        for (Entry e : entries) if (e.side() == side) out.add(new int[]{e.lo(), e.hi()});
        return out.toArray(new int[0][]);
    }

    // ---------------------------------------------------------------- the battle

    /**
     * Mans a redoubt the garrison's dressing could not read, as the dressing mans one: its guns seated in their nests,
     * and three wall teams on its loopholes (the windows its buildings face out of) and sandbags, the front first,
     * each team holding the redoubt.
     */
    static void garrison(BattleSimulation sim, String key, Built built, Site site, Frame frame, Random rng) {
        int k = 0;
        for (int[] gun : built.guns()) {
            if (!sim.getGrid().isWalkable(gun[0], gun[1])) continue;
            ColonyMilitia.seatEmplacement(sim, gun, "Redoubt 2 gun " + (++k));
        }
        int reach = 22;
        int cx = frame.x(Math.round(built.focusU()), Math.round(built.focusV()));
        int cy = frame.y(Math.round(built.focusU()), Math.round(built.focusV()));
        List<int[]> posts = new ArrayList<>(built.posts());
        NavigationGrid grid = sim.getGrid();
        for (int y = cy - reach; y <= cy + reach; y++) {
            for (int x = cx - reach; x <= cx + reach; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                for (Direction side : Direction.CARDINALS) {
                    SharedEdgeBarrier edge = grid.getEdgeBarrier(x, y, side);
                    if (edge != null && edge.kind() == SharedEdgeBarrier.Kind.WINDOW) {
                        posts.add(new int[]{x, y});
                        break;
                    }
                }
            }
        }
        posts.removeIf(p -> !grid.isWalkable(p[0], p[1]));
        posts.sort(Comparator.<int[]>comparingInt(p -> -frame.v(p[0], p[1]))
                .thenComparingInt(p -> Math.abs(frame.u(p[0], p[1]))).thenComparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
        int seats = CordonGarrisons.TEAMS * ColonyMilitia.POST_SIZE;
        List<int[]> chosen = new ArrayList<>(posts.subList(0, Math.min(seats, posts.size())));
        for (int t = 0; t < CordonGarrisons.TEAMS; t++) {
            List<int[]> cells = new ArrayList<>();
            for (int i = t; i < chosen.size(); i += CordonGarrisons.TEAMS) cells.add(chosen.get(i));
            if (cells.isEmpty()) continue;
            int squad = ColonyMilitia.seatTeam(sim, cells, "Redoubt 2 wall team " + (t + 1), RiskLevel.HIGH, rng);
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, cx, cy, reach),
                    CommandAuthority.GARRISON, ISSUER, "hold redoubt 2");
        }
        System.out.printf("    [cordon-structure-look/%s] %d guns, %d loopholes and bags, %d seated%n", key, k, posts.size(),
                chosen.size());
    }

    /** A swarm pack coming at the redoubt: fourteen runners and three brutes attack-moving on it. */
    static void pack(BattleSimulation sim, int x, int y, int towardX, int towardY) {
        swarm(sim, UnitType.SWARM_RUNNER, 14, x, y, towardX, towardY);
        swarm(sim, UnitType.SWARM_BRUTE, 3, x + 5, y, towardX, towardY);
    }

    private static void swarm(BattleSimulation sim, UnitType type, int count, int x, int y, int towardX, int towardY) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, count);
        int squad = sim.mintSquad(Faction.DEFENDER, type);
        if (cells.isEmpty()) return;
        for (int i = 0; i < cells.size(); i++) {
            long id = sim.spawn(new EntitySpec("look pack " + type.name().toLowerCase(Locale.ROOT) + " " + i,
                    Faction.DEFENDER, type, cells.get(i)[0], cells.get(i)[1]).squad(squad));
            if (sim.getSquad(squad).leaderId == 0L) sim.getSquad(squad).leaderId = id;
        }
        sim.getSquad(squad).originalSize = cells.size();
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, towardX, towardY),
                CommandAuthority.SCRIPTED, ISSUER, "look pack");
    }

    /** An allied Hound standing in a gantry, under the garrison's authority, going nowhere. */
    static void hound(BattleSimulation sim, int x, int y) {
        int squad = sim.mintSquad(Faction.ALLY, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("Depot Hound", Faction.ALLY, UnitType.HEAVY_MECH, x, y)
                .atPosition(x + 0.5f, y + 0.5f).mechVariant(MechVariant.HOUND).squad(squad));
        sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(MechVariant.HOUND, MechVariant.HOUND.defaultRole,
                "independent"));
        Squad s = sim.getSquad(squad);
        s.leaderId = mech;
        s.originalSize = 1;
        sim.claimSquadCommand(squad, CommandAuthority.GARRISON, ISSUER, "in the gantry");
    }

    // ---------------------------------------------------------------- local frame

    /**
     * Cells local to a redoubt or base: {@code u} across the line, {@code v} toward the front. On a map whose front is
     * south the whole layout is turned half round, so a plan written once faces the front on either.
     */
    static final class Frame {
        final int ox, oy, f;

        Frame(int ox, int oy, int front) {
            this.ox = ox;
            this.oy = oy;
            this.f = front >= 0 ? 1 : -1;
        }

        int x(int u, int v) { return ox + f * u; }
        int y(int u, int v) { return oy + f * v; }
        float fx(float u, float v) { return ox + f * u + 0.5f; }
        float fy(float u, float v) { return oy + f * v + 0.5f; }
        int u(int x, int y) { return f * (x - ox); }
        int v(int x, int y) { return f * (y - oy); }
        int[] cell(int u, int v) { return new int[]{x(u, v), y(u, v)}; }

        void clear(CordonStructureWorks w, int u0, int v0, int u1, int v1, CordonStructureWorks.Ground ground,
                   BiPredicate<Integer, Integer> keep) {
            w.clear(x(u0, v0), y(u0, v0), x(u1, v1), y(u1, v1), ground, keep);
        }

        void floor(CordonStructureWorks w, int u0, int v0, int u1, int v1, CordonStructureWorks.Ground ground) {
            w.floor(x(u0, v0), y(u0, v0), x(u1, v1), y(u1, v1), ground);
        }

        void worked(CordonStructureWorks w, int u, int v) { w.worked(x(u, v), y(u, v)); }

        void stamp(CordonStructureWorks w, BuildingStamp stamp, int u0, int v0, int turns) {
            int[] size = CordonStructureWorks.size(stamp, turns);
            int left = f > 0 ? ox + u0 : ox - (u0 + size[0] - 1);
            int bottom = f > 0 ? oy + v0 : oy - (v0 + size[1] - 1);
            w.stamp(stamp, left, bottom, f > 0 ? turns : (turns + 2) % 4);
        }

        /** A straight wall from local {@code (u0, v0)} to {@code (u1, v1)}; gaps are local spans along it. */
        void wall(CordonStructureWorks w, String material, int u0, int v0, int u1, int v1, int[]... gaps) {
            boolean across = v0 == v1;
            int a = across ? Math.min(u0, u1) : Math.min(v0, v1), b = across ? Math.max(u0, u1) : Math.max(v0, v1);
            for (int t = a; t <= b; t++) {
                boolean open = false;
                for (int[] gap : gaps) open |= t >= gap[0] && t <= gap[1];
                if (open) continue;
                int u = across ? t : u0, v = across ? v0 : t;
                w.wall(x(u, v), y(u, v), material);
            }
        }

        /** Whether a gun nest fits at {@code (u, v)}: the cell and its eight neighbours open floor. */
        boolean roomFor(CordonStructureWorks w, int u, int v) {
            for (int dv = -1; dv <= 1; dv++) {
                for (int du = -1; du <= 1; du++) {
                    if (!PreparedGround.open(w.ctx, x(u + du, v + dv), y(u + du, v + dv))) return false;
                }
            }
            return true;
        }

        boolean sandbag(CordonStructureWorks w, int u, int v, int du, int dv) {
            return w.sandbag(x(u, v), y(u, v), new int[]{f * du, f * dv}, null);
        }

        boolean obstacle(CordonStructureWorks w, int u, int v, String id) {
            return w.obstacle(x(u, v), y(u, v), id);
        }

        boolean fence(CordonStructureWorks w, int u, int v, boolean acrossLine) {
            return w.fence(x(u, v), y(u, v), acrossLine);
        }

        /** A prop whose footprint's local lower-left is {@code (u, v)}. */
        boolean prop(CordonStructureWorks w, int u, int v, String id, int turns, boolean blocking) {
            if (f > 0) return w.prop(x(u, v), y(u, v), id, turns, blocking);
            int[] size = w.propSize(id, turns);
            return w.prop(ox - (u + size[0] - 1), oy - (v + size[1] - 1), id, (turns + 2) % 4, blocking);
        }
    }
}
