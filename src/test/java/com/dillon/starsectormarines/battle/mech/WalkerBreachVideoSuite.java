package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.ClearanceRoutePlanner;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelveStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;

/**
 * Video of a heavy walker's crush ({@link MechWallCrush}) for the owner's {@code heavy-walker-crush-r3} card: a
 * heavy walker breaks ordinary walls and props at once, as a heavy tank does, under its legs as well as its torso. Every clip is a short staged move order on real generated ground, recorded at 30 frames a second
 * through the look preset; none is a battle, and none is read for who wins.
 *
 * <ul>
 *   <li>{@code look/walker-breach-close-close.mp4}: an MG-40 walking through a colony house wall, close.</li>
 *   <li>{@code look/walker-crush-props-play.mp4}: a Hauler walking through a sandbag line and a fence line laid across
 *       its way, crates beside its path under its legs, at play zoom.</li>
 *   <li>{@code look/walker-fortified-holds-close.mp4}: an MG-70 sent straight at a ruins facility wall
 *       ({@link RuinsDelveStage#FACILITY_WALL_HP}), which holds.</li>
 * </ul>
 *
 * Each frame carries a readout of the walker's structure and the wall cells it has brought down.
 */
public final class WalkerBreachVideoSuite implements SnapshotSuite {

    private static final int FPS_TICKS = 1;
    private static final int ROUTE_BUDGET = 60_000;
    private static final int MAX_TRIES = 400;
    /** Ticks a stage may play before the recording starts, walking the walker up to the wall. */
    private static final int MAX_PREROLL = 900;

    @Override public String id() { return "walker-breach-video"; }

    @Override public String label() {
        return "Heavy walker breach video: an MG-40 through a house wall, a Hauler through props, an MG-70 held by a "
                + "ruins facility wall";
    }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        // -Dsnapshot.walkerShots=<name>[,<name>] records only those shots, for a quick re-take of one.
        String only = System.getProperty("snapshot.walkerShots", "");
        List<LookReview.Shot> shots = List.of(houseBreach(), propCrush(), fortifiedHolds());
        List<SnapshotArtifact> out = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (LookReview.Shot shot : shots) {
            if (!only.isBlank() && !List.of(only.split(",")).contains(shot.name())) continue;
            try {
                out.addAll(LookReview.record(context.modRoot(), shot));
            } catch (RuntimeException e) {
                System.out.println("    [look/" + shot.name() + "] FAILED: " + e);
                failed.add(shot.name());
            }
        }
        if (out.isEmpty()) throw new IllegalStateException("every walker breach shot failed: " + failed);
        return out;
    }

    /** Where a clip's leg runs: the walker's start, its goal, and the first breach cell it meets. */
    record Leg(float[] start, float[] goal, int[] breach, Set<Integer> crossed, int score) {
        float bx() { return breach[0] + .5f; }

        float by() { return breach[1] + .5f; }
    }

    /** What the stage found, shared with the views that are built before it runs. */
    private static final class Holder {
        Leg leg;
        long walker;
    }

    // ------------------------------------------------------------------ clip 1: house wall, close

    private static LookReview.Shot houseBreach() {
        Holder holder = new Holder();
        MechVariant variant = MechVariant.SECURITY_WALKER;
        return new LookReview.Shot("walker-breach-close", LookGround.verdant(), 72, 40, placement -> {
            NavigationGrid grid = placement.map().grid;
            CellTopology topology = placement.map().topology;
            NavigationGrid view = WallCrush.breachView(grid, topology, null);
            List<int[]> pairs = new ArrayList<>();
            int[][] sides = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}};
            for (int y = 40; y < grid.getHeight() - 40 && pairs.size() < MAX_TRIES; y += 2) {
                for (int x = 40; x < grid.getWidth() - 40 && pairs.size() < MAX_TRIES; x += 2) {
                    if (topology.getBuildingId(x, y) == 0 || !roomy(grid, x, y)) continue;
                    for (int[] side : sides) {
                        int sx = x + side[0], sy = y + side[1];
                        if (outdoors(grid, topology, sx, sy)) pairs.add(new int[] {sx, sy, x, y});
                    }
                }
            }
            // One house wall, crossed once: a cell of ordinary wall scores, the leg crossing the most of one run wins.
            Leg leg = bestLeg(view, variant.radius, pairs, (x, y) -> topology.isWall(x, y)
                    && grid.getWallHp(x, y) >= 60 && grid.getWallHp(x, y) <= WallCrush.HEAVY_CEILING ? 1 : -4);
            return stage(placement, holder, leg, variant, "MG-40 through a city wall (100): it falls at once, as to a "
                    + "heavy tank");
        }, placement -> List.of(new LookReview.View("close", sim -> holder.leg == null
                ? new float[] {placement.site().centreX(), placement.site().centreY(), 24f}
                : new float[] {holder.leg.bx(), holder.leg.by(), 24f})), 330, FPS_TICKS, 0,
                new int[] {30, 120, 210, 300});
    }

    // ------------------------------------------------------------------ clip 2: props and light walls, play zoom

    /** Cells between the walker's start and the first barricade line, and between the two lines. */
    private static final int LINE_GAP = 5;

    /**
     * A Hauler at play zoom walking through two barricade lines laid across the whole map in the open middle of the
     * colony window, as the vehicle-ram look lays its line: sandbags where it crosses the first and fence beyond, then
     * an unbroken fence, so its cheapest way on is through both.
     */
    private static LookReview.Shot propCrush() {
        Holder holder = new Holder();
        MechVariant variant = MechVariant.HAULER;
        return new LookReview.Shot("walker-crush-props", LookGround.verdant(), 72, 40, placement -> {
            float[] mid = placement.openNear(30f, 20f);
            int lineX = (int) mid[0], y = (int) mid[1];
            int w = placement.map().grid.getWidth();
            Set<Integer> crossed = new LinkedHashSet<>();
            for (int line : new int[] {lineX, lineX + LINE_GAP}) {
                for (int dy = -2; dy <= 2; dy++) crossed.add((y + dy) * w + line);
            }
            for (int side : new int[] {-1, 1}) {
                for (int cx = lineX + 2; cx <= lineX + 3; cx++) crossed.add((y + side) * w + cx);
            }
            Leg leg = new Leg(new float[] {lineX - LINE_GAP + .5f, y + .5f},
                    new float[] {lineX + 2 * LINE_GAP + .5f, y + .5f}, new int[] {lineX, y}, crossed, 0);
            return stage(placement, holder, leg, variant, "Hauler: sandbags, fences and crates, under its legs too",
                    sim -> {
                TileRegistry registry = TileRegistry.installed();
                NavigationGrid grid = sim.getGrid();
                for (int cy = 0; cy < grid.getHeight(); cy++) {
                    for (int line : new int[] {lineX, lineX + LINE_GAP}) {
                        if (!grid.isWalkable(line, cy)) continue;
                        grid.setWalkable(line, cy, false);
                        boolean bags = line == lineX && Math.abs(cy - y) <= 4;
                        sim.addDoodad(new Doodad(line, cy, registry.doodad(bags ? "doodad.sandbag-straight-e"
                                : "doodad.industrial-fence-straight-v")));
                    }
                }
                // Crates either side of its path between the lines: off its torso's line, under its legs.
                for (int side : new int[] {-1, 1}) {
                    for (int cx = lineX + 2; cx <= lineX + 3; cx++) {
                        int cy = y + side;
                        if (!grid.isWalkable(cx, cy)) continue;
                        grid.setWalkable(cx, cy, false);
                        sim.addDoodad(new Doodad(cx, cy, registry.doodad("doodad.crate")));
                    }
                }
            });
        }, placement -> List.of(new LookReview.View("play", sim -> holder.walker == 0L
                ? new float[] {placement.site().centreX(), placement.site().centreY(),
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS}
                : new float[] {sim.world().renderX(holder.walker), sim.world().renderY(holder.walker),
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS})), 420, FPS_TICKS, 0,
                new int[] {30, 150, 270, 390});
    }

    // ------------------------------------------------------------------ clip 3: a ruins facility wall holds

    private static LookReview.Shot fortifiedHolds() {
        Holder holder = new Holder();
        MechVariant variant = MechVariant.SECURITY_HALL;
        return new LookReview.Shot("walker-fortified-holds", LookGround.ruins(SurfacePalette.ARID, 11L, 2), 72, 40,
                placement -> {
                    NavigationGrid grid = placement.map().grid;
                    CellTopology topology = placement.map().topology;
                    Leg leg = facilityLeg(grid, topology, variant.radius);
                    return stage(placement, holder, leg, variant,
                            "Ruins facility wall: " + RuinsDelveStage.FACILITY_WALL_HP
                                    + " structure; a heavy walker breaks " + WallCrush.HEAVY_CEILING + " at most");
                }, placement -> List.of(new LookReview.View("close", sim -> holder.leg == null
                        ? new float[] {placement.site().centreX(), placement.site().centreY(), 24f}
                        : new float[] {holder.leg.bx(), holder.leg.by(), 24f})), 330, FPS_TICKS, 0,
                new int[] {30, 120, 210, 300});
    }

    /**
     * A facility wall run with open ground outside it and floor inside: the walker starts eight cells out and is sent
     * to the floor just inside, the run at least three cells long either side, so nothing but the wall stands between.
     */
    private static Leg facilityLeg(NavigationGrid grid, CellTopology topology, float radius) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int w = grid.getWidth(), h = grid.getHeight();
        Leg best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = 12; y < h - 12; y++) {
            for (int x = 12; x < w - 12; x++) {
                if (!fortified(grid, x, y)) continue;
                for (int[] d : dirs) {
                    int thick = 0;
                    while (thick < 6 && fortified(grid, x + thick * d[0], y + thick * d[1])) thick++;
                    int ix = x + thick * d[0], iy = y + thick * d[1];
                    if (thick == 6 || !grid.isWalkable(ix, iy)) continue;
                    if (!grid.isWalkable(x - d[0], y - d[1])) continue;
                    boolean clear = true;
                    for (int k = 2; k <= 8 && clear; k++) clear = roomy(grid, x - k * d[0], y - k * d[1]);
                    if (!clear) continue;
                    // The run along the wall: five fortified cells either side.
                    int px = d[1], py = d[0];
                    boolean run = true;
                    for (int s = -3; s <= 3 && run; s++) run = fortified(grid, x + s * px, y + s * py);
                    if (!run) continue;
                    double distance = Math.hypot(x - w / 2.0, y - h / 2.0);
                    if (distance >= bestDistance) continue;
                    bestDistance = distance;
                    Set<Integer> crossed = new LinkedHashSet<>();
                    for (int t = 0; t < thick; t++) {
                        for (int s = -2; s <= 2; s++) {
                            crossed.add((y + t * d[1] + s * py) * w + x + t * d[0] + s * px);
                        }
                    }
                    best = new Leg(new float[] {x - 8 * d[0] + .5f, y - 8 * d[1] + .5f},
                            new float[] {ix + d[0] * 2 + .5f, iy + d[1] * 2 + .5f}, new int[] {x, y}, crossed, thick);
                }
            }
        }
        if (best == null) {
            java.util.TreeMap<Integer, Integer> hp = new java.util.TreeMap<>();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (!grid.isWalkable(x, y)) hp.merge(grid.getWallHp(x, y), 1, Integer::sum);
            throw new IllegalStateException("no facility wall with open ground outside it; closed cells by structure " + hp);
        }
        System.out.printf("    [look/walker-fortified-holds] wall at %d,%d, %d thick%n", best.breach[0],
                best.breach[1], best.score);
        return best;
    }

    private static boolean fortified(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && !grid.isWalkable(x, y) && grid.getWallHp(x, y) >= RuinsDelveStage.FACILITY_WALL_HP;
    }

    // ------------------------------------------------------------------ shared

    /**
     * Stands {@code variant} at the leg's start, gives it the move order, plays it forward until it is a few cells
     * off the wall, and installs the readout.
     */
    private static BattleSimulation stage(LookReview.Placement placement, Holder holder, Leg leg, MechVariant variant,
                                          String title) {
        return stage(placement, holder, leg, variant, title, null);
    }

    /**
     * {@link #stage(LookReview.Placement, Holder, Leg, MechVariant, String)}, {@code dress} laying ground of
     * the shot's own into the built simulation before the walker is given its order.
     */
    private static BattleSimulation stage(LookReview.Placement placement, Holder holder, Leg leg, MechVariant variant,
                                          String title, Consumer<BattleSimulation> dress) {
        holder.leg = leg;
        SceneBuilder builder = placement.builder();
        builder.squad("walker").faction(Faction.MARINE).size(1)
                .at((int) leg.start[0] - placement.site().x0(), (int) leg.start[1] - placement.site().y0())
                .mech(variant, MechRole.ARMORED_SUPPORT).done();
        placement.holdouts(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        if (dress != null) dress.accept(sim);
        long walker = placement.subject("walker", world.members("walker")[0]);
        holder.walker = walker;
        int gx = (int) leg.goal[0], gy = (int) leg.goal[1];
        boolean forced = leg.crossed.stream().anyMatch(c -> sim.getGrid().getWallHp(c % sim.getGrid().getWidth(),
                c / sim.getGrid().getWidth()) > WallCrush.HEAVY_CEILING);
        if (forced) openInRouteView(sim, walker, leg);
        int reorder = forced ? 45 : 150;
        int[] tick = {0};
        placement.beforeTick(s -> {
            if (tick[0]++ % reorder == 0) {
                if (forced) openInRouteView(s, walker, leg);
                s.getMechMoveOrderService().requestMove(walker, gx, gy);
            }
        });
        // Walk it up to the wall before the recording starts, so the clip opens a few strides off it.
        float reach = variant.radius + 3.5f;
        int preroll = 0;
        while (preroll < MAX_PREROLL) {
            float dx = sim.world().x(walker) - leg.bx(), dy = sim.world().y(walker) - leg.by();
            if (dx * dx + dy * dy <= reach * reach) break;
            for (var step : placement.beforeTicks()) step.accept(sim);
            sim.advance(BattleSimulation.TICK_DT);
            BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
            preroll++;
        }
        System.out.printf(Locale.ROOT, "    [look/%s] %s from %.1f,%.1f to %d,%d, breach cell %d,%d, %d crossed,"
                        + " preroll %d ticks%n", placement.ground().surface(), variant, leg.start[0], leg.start[1], gx,
                gy, leg.breach[0], leg.breach[1], leg.crossed.size(), preroll);
        String chassis = chassisName(variant);
        placement.readout(s -> title);
        placement.readout(s -> {
            float hp = s.world().isAlive(walker) ? s.world().hp(walker) : 0f;
            return String.format(Locale.ROOT, "%s structure %.0f / %.0f", chassis, hp, s.world().maxHp(walker));
        });
        placement.readout(s -> {
            int down = 0;
            int w = s.getGrid().getWidth();
            for (int cell : leg.crossed) if (s.getGrid().isWalkable(cell % w, cell / w)) down++;
            return "Wall and prop cells on its way brought down: " + down + " of " + leg.crossed.size();
        });
        return sim;
    }

    /**
     * Opens the facility wall cells of {@code leg} in the walker's own route view, so it plans straight at the wall as
     * though it could push it; its body still meets the live wall, which holds. A staging device for the contrast
     * clip only: the shipped planner sends it round.
     */
    private static void openInRouteView(BattleSimulation sim, long walker, Leg leg) {
        NavigationGrid view = sim.mechRouteGrid(walker);
        if (view == sim.getGrid()) return;
        int w = view.getWidth();
        for (int cell : leg.crossed) {
            int x = cell % w, y = cell / w;
            view.setWalkable(x, y, true);
            view.openAllEdges(x, y);
        }
    }

    private static String chassisName(MechVariant variant) {
        return switch (variant) {
            case SECURITY_WALKER -> "MG-40";
            case SECURITY_HALL -> "MG-70";
            case HAULER -> "Hauler";
            case PILGRIM -> "Pilgrim";
            case BULWARK -> "Bulwark";
            default -> variant.name();
        };
    }

    /**
     * The leg among {@code pairs} ({sx, sy, gx, gy}) whose route over {@code view} scores most by {@code score} summed
     * over the distinct breach cells it crosses; a leg crossing none never wins.
     */
    static Leg bestLeg(NavigationGrid view, float radius, List<int[]> pairs, IntBinaryOperator score) {
        ClearanceRoutePlanner planner = new ClearanceRoutePlanner();
        Leg best = null;
        for (int[] pair : pairs) {
            var result = planner.findRoute(view, pair[0] + .5f, pair[1] + .5f, pair[2] + .5f, pair[3] + .5f, radius,
                    0f, ROUTE_BUDGET);
            if (result.status() != ClearanceRoutePlanner.Status.FOUND) continue;
            Set<Integer> crossed = new LinkedHashSet<>();
            int[] first = null;
            var points = result.waypoints();
            for (int i = 1; i < points.size(); i++) {
                var a = points.get(i - 1);
                var b = points.get(i);
                int steps = Math.max(1, (int) Math.ceil(Math.hypot(b.x() - a.x(), b.y() - a.y()) * 4f));
                for (int s = 0; s < steps; s++) {
                    float t = (float) s / steps;
                    int cx = (int) Math.floor(a.x() + (b.x() - a.x()) * t);
                    int cy = (int) Math.floor(a.y() + (b.y() - a.y()) * t);
                    if (!view.hasTag(cx, cy, NavigationGrid.CellTag.BREACH)) continue;
                    if (first == null) first = new int[] {cx, cy};
                    // The cells either side of the line too: the walker bears on a strip as wide as it is.
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (view.hasTag(cx + ox, cy + oy, NavigationGrid.CellTag.BREACH)) {
                                crossed.add((cy + oy) * view.getWidth() + cx + ox);
                            }
                        }
                    }
                }
            }
            if (first == null) continue;
            int total = 0;
            for (int cell : crossed) total += score.applyAsInt(cell % view.getWidth(), cell / view.getWidth());
            if (best == null || total > best.score) {
                best = new Leg(new float[] {pair[0] + .5f, pair[1] + .5f}, new float[] {pair[2] + .5f, pair[3] + .5f},
                        first, crossed, total);
            }
        }
        if (best == null) throw new IllegalStateException("no leg crosses a breach among " + pairs.size());
        System.out.printf("    [look/walker-breach-video] %d legs tried, best scores %d over %d cells%n", pairs.size(),
                best.score, best.crossed.size());
        return best;
    }

    /** Whether cell ({@code x}, {@code y}) and the eight round it are walkable. */
    private static boolean roomy(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) if (!grid.isWalkable(x + dx, y + dy)) return false;
        }
        return true;
    }

    /** Whether a walker could stand at cell ({@code x}, {@code y}) out of doors with room round it. */
    private static boolean outdoors(NavigationGrid grid, CellTopology topology, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!grid.isWalkable(x + dx, y + dy) || topology.getBuildingId(x + dx, y + dy) != 0) return false;
            }
        }
        return true;
    }
}
