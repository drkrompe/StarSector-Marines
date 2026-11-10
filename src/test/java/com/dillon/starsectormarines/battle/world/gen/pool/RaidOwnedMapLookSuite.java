package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidOwnedMap;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

/**
 * The Raid's owned maps on their home ({@code raid-mission.md}, The owned map), through the look preset at 1920x1080,
 * with how each would play marked on it: the landing pads, the objective and its egress, every defender and
 * emplacement standing at the start, the shortest walk from the landing nearest the objective to it and out, and the
 * walk that keeps furthest from the defenders. Read off the ground at setup; nothing is played. Opt-in. The owned
 * maps under every world are {@code planet-skin-look} with {@code -Dsnapshot.planetSkin.maps=raid,...}.
 *
 * <ul>
 *   <li>{@code -Dsnapshot.raidOwned.candidates=2-7}: every Raid map's seeds in that range
 *       ({@code candidates/<map>-seed<n>.png}), to pick an owned seed from.</li>
 *   <li>{@code -Dsnapshot.raidOwned.play=true}: each map's owned seed whole ({@code play/<map>.png}) and close on
 *       the objective ({@code play/<map>-objective.png}).</li>
 * </ul>
 * {@code -Dsnapshot.raidOwned.maps=raid,raid-mining} narrows the maps.
 */
public final class RaidOwnedMapLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** A defender this close to a walk is one the walk passes (cells): about a rifle's reach across open ground. */
    private static final int THREAT_RADIUS = 16;
    /** How much dearer a step at a defender's feet is than one out of its reach, for the skirting walk. */
    private static final float THREAT_COST = 40f;
    /** Cells of ground shown round the objective in the close frame. */
    private static final int OBJECTIVE_MARGIN = 24;

    @Override public String id() { return "raid-owned-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "The Raid's owned maps on their home with how they would play, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        List<String> maps = asked("snapshot.raidOwned.maps");
        String range = System.getProperty("snapshot.raidOwned.candidates", "").trim();
        boolean play = Boolean.getBoolean("snapshot.raidOwned.play");
        List<SnapshotArtifact> out = new ArrayList<>();
        for (StoryMap map : StoryMap.values()) {
            if (!RaidOwnedMap.isRaid(map) || !maps.isEmpty() && !maps.contains(map.id)) continue;
            if (!range.isEmpty()) {
                String[] r = range.split("-");
                long from = Long.parseLong(r[0]), to = r.length > 1 ? Long.parseLong(r[1]) : from;
                for (long seed = from; seed <= to; seed++) {
                    out.addAll(frames(context, map, seed, "candidates/" + map.id + "-seed" + seed, false));
                }
            }
            if (play) out.addAll(frames(context, map, StoryMapPools.pool(map).owned().seed(), "play/" + map.id, true));
        }
        return out;
    }

    /** {@code map} on {@code seed}, whole and marked, and with {@code close} a marked frame close on its objective. */
    private static List<SnapshotArtifact> frames(SnapshotContext context, StoryMap map, long seed, String name,
                                                 boolean close) throws Exception {
        RaidOwnedMap.Ground ground = RaidOwnedMap.Ground.of(map, StoryMapPools.pool(map).home());
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createPooledRaid(ground, ground.home(), seed, map != StoryMap.RAID,
                List.of())) {
            int w = sim.getTopology().getWidth(), h = sim.getTopology().getHeight();
            float fit = Math.max(w, h * 16f / 9f) + 4f;
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                    w / 2f, h / 2f, fit);
            out.add(new SnapshotArtifact(name + ".png", look.renderMarked(sim, playMarks(sim, map, seed, fit, true))));
            if (close) {
                int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
                for (RaidObjective.Site site : raid(sim, map).sites()) {
                    x0 = Math.min(x0, site.cellX()); x1 = Math.max(x1, site.cellX());
                    y0 = Math.min(y0, site.cellY()); y1 = Math.max(y1, site.cellY());
                }
                float across = Math.max(x1 - x0, (y1 - y0) * 16f / 9f) + 2f * OBJECTIVE_MARGIN * 16f / 9f;
                look.refocus((x0 + x1 + 1) / 2f, (y0 + y1 + 1) / 2f, across);
                out.add(new SnapshotArtifact(name + "-objective.png",
                        look.renderMarked(sim, playMarks(sim, map, seed, across, false))));
            }
            look.forget(sim);
        }
        return out;
    }

    private static RaidObjective raid(BattleSimulation sim, StoryMap map) {
        for (var objective : sim.getObjectives()) if (objective instanceof RaidObjective r) return r;
        throw new IllegalStateException(map.id + " stands no Raid objective");
    }

    /** The marks that say how {@code sim}'s raid would play; on a {@code whole} frame a legend and a log line of its numbers too. */
    private static ReviewAnnotations playMarks(BattleSimulation sim, StoryMap map, long seed, float cellsAcross,
                                               boolean whole) {
        NavigationGrid grid = sim.getGrid();
        int w = grid.getWidth(), h = grid.getHeight();
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        List<ReviewAnnotation.Cell> defenders = new ArrayList<>(), guns = new ArrayList<>();
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long id = sim.getRoster().get(i);
            if (sim.identity().faction(id) != Faction.DEFENDER) continue;
            int x = (int) sim.world().x(id), y = (int) sim.world().y(id);
            if (x < 0 || y < 0 || x >= w || y >= h) continue;
            (sim.turretState().isTurret(id) ? guns : defenders).add(new ReviewAnnotation.Cell(x, y));
        }
        List<ReviewAnnotation.Cell> threats = new ArrayList<>(defenders);
        threats.addAll(guns);
        marks.cells(defenders, null, ReviewStyle.KEEP);
        marks.cells(guns, null, ReviewStyle.DUG_IN);

        RaidObjective raid = raid(sim, map);
        List<int[]> goals = new ArrayList<>();
        int n = 0;
        for (RaidObjective.Site site : raid.sites()) {
            goals.add(new int[]{site.cellX(), site.cellY()});
            marks.label(site.cellX(), site.cellY(), raid.works() ? "MACHINE " + (++n) : "TARGET: " + raid.targetName(),
                    ReviewStyle.OBJECTIVE);
        }
        marks.label(raid.egressCellX(), raid.egressCellY(), "EGRESS", ReviewStyle.LANDING);

        List<LandingPad> pads = sim.getMarineLandingPads();
        LandingPad start = null;
        for (LandingPad pad : pads) {
            marks.box(pad.left(), pad.bottom(), pad.right(), pad.top(), null, ReviewStyle.LANDING);
            if (start == null || dist2(pad, goals.get(0)) < dist2(start, goals.get(0))) start = pad;
        }
        if (start == null) throw new IllegalStateException(map.id + " has no landing pad");
        marks.label(start.centerX, start.top() + 3, "LANDING (" + pads.size() + " pads)", ReviewStyle.LANDING);

        float[] threat = threatField(threats, w, h);
        int[] from = {start.centerX, start.centerY};
        int[] goal = nearest(goals, from);
        int[] egress = {raid.egressCellX(), raid.egressCellY()};
        List<int[]> direct = new ArrayList<>(walk(grid, from, goal, null));
        direct.addAll(walk(grid, goal, egress, null));
        List<int[]> skirt = new ArrayList<>(walk(grid, from, goal, threat));
        skirt.addAll(walk(grid, goal, egress, threat));
        int directPassed = passed(direct, threats), skirtPassed = passed(skirt, threats);
        marks.polyline(points(direct), null, ReviewStyle.APPROACH);
        marks.polyline(points(skirt), null, ReviewStyle.ROUTE);
        if (!whole) return marks.build();
        // A legend down the top-left corner of a whole frame, one row per line, so no route label lands on a machine.
        float row = 26f * cellsAcross / FRAME_W, y = h - 2f;
        marks.label(3, y, String.format(Locale.ROOT, "%s seed %d  %dx%d", map.id, seed, w, h), ReviewStyle.NOTE);
        marks.label(3, y -= row, String.format(Locale.ROOT, "pink: %d defenders, %d emplacements at the start",
                defenders.size(), guns.size()), ReviewStyle.KEEP);
        marks.label(3, y -= row, String.format(Locale.ROOT, "white: shortest walk in and out, %d cells, passes %d",
                direct.size(), directPassed), ReviewStyle.APPROACH);
        marks.label(3, y -= row, String.format(Locale.ROOT, "blue: skirting walk, %d cells, passes %d",
                skirt.size(), skirtPassed), ReviewStyle.ROUTE);
        marks.label(3, y - row, "passes = defenders within " + THREAT_RADIUS + " cells of the walk", ReviewStyle.NOTE);
        System.out.printf(Locale.ROOT, "[raid-owned-play] %s seed %d %dx%d defenders %d emplacements %d pads %d "
                        + "direct %d cells passes %d; skirting %d cells passes %d%n", map.id, seed, w, h, defenders.size(),
                guns.size(), pads.size(), direct.size(), directPassed, skirt.size(), skirtPassed);
        return marks.build();
    }

    private static long dist2(LandingPad pad, int[] c) {
        long dx = pad.centerX - c[0], dy = pad.centerY - c[1];
        return dx * dx + dy * dy;
    }

    private static int[] nearest(List<int[]> cells, int[] from) {
        int[] best = cells.get(0);
        for (int[] c : cells) {
            if (Math.hypot(c[0] - from[0], c[1] - from[1]) < Math.hypot(best[0] - from[0], best[1] - from[1])) best = c;
        }
        return best;
    }

    /** 1 at a defender's feet, falling to 0 at {@link #THREAT_RADIUS}, through walls (a chamfer distance). */
    private static float[] threatField(List<ReviewAnnotation.Cell> threats, int w, int h) {
        int[] d = new int[w * h];
        Arrays.fill(d, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (ReviewAnnotation.Cell c : threats) {
            d[c.y() * w + c.x()] = 0;
            queue.add(c.y() * w + c.x());
        }
        while (!queue.isEmpty()) {
            int i = queue.poll(), x = i % w, y = i / w;
            if (d[i] >= THREAT_RADIUS) continue;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (d[j] > d[i] + 1) {
                        d[j] = d[i] + 1;
                        queue.add(j);
                    }
                }
            }
        }
        float[] t = new float[w * h];
        for (int i = 0; i < t.length; i++) t[i] = d[i] >= THREAT_RADIUS ? 0f : 1f - d[i] / (float) THREAT_RADIUS;
        return t;
    }

    /** The cheapest 8-way walk over walkable cells, cutting no wall corner; a null {@code threat} is the shortest. */
    private static List<int[]> walk(NavigationGrid grid, int[] from, int[] to, float[] threat) {
        int w = grid.getWidth(), h = grid.getHeight();
        float[] cost = new float[w * h];
        int[] prev = new int[w * h];
        Arrays.fill(cost, Float.MAX_VALUE);
        Arrays.fill(prev, -1);
        int s = from[1] * w + from[0], g = to[1] * w + to[0];
        cost[s] = 0f;
        PriorityQueue<float[]> open = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        open.add(new float[]{0f, s});
        while (!open.isEmpty()) {
            float[] top = open.poll();
            int i = (int) top[1];
            if (top[0] > cost[i]) continue;
            if (i == g) break;
            int x = i % w, y = i / w;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
                    if (dx != 0 && dy != 0 && (!grid.isWalkable(x + dx, y) || !grid.isWalkable(x, y + dy))) continue;
                    int j = ny * w + nx;
                    float step = (dx != 0 && dy != 0 ? 1.4142f : 1f) * (threat == null ? 1f : 1f + THREAT_COST * threat[j]);
                    if (cost[i] + step < cost[j]) {
                        cost[j] = cost[i] + step;
                        prev[j] = i;
                        open.add(new float[]{cost[j], j});
                    }
                }
            }
        }
        List<int[]> path = new ArrayList<>();
        if (prev[g] < 0 && g != s) return path;
        for (int i = g; i >= 0; i = prev[i]) path.add(new int[]{i % w, i / w});
        Collections.reverse(path);
        return path;
    }

    /** How many of {@code threats} stand within {@link #THREAT_RADIUS} of some cell of {@code path}. */
    private static int passed(List<int[]> path, List<ReviewAnnotation.Cell> threats) {
        int count = 0;
        for (ReviewAnnotation.Cell t : threats) {
            for (int[] c : path) {
                if (Math.hypot(c[0] - t.x(), c[1] - t.y()) <= THREAT_RADIUS) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static List<ReviewAnnotation.Point> points(List<int[]> path) {
        List<ReviewAnnotation.Point> points = new ArrayList<>();
        for (int i = 0; i < path.size(); i += 2) {
            points.add(new ReviewAnnotation.Point(path.get(i)[0] + .5f, path.get(i)[1] + .5f));
        }
        if (path.size() > 1) {
            int[] last = path.get(path.size() - 1);
            points.add(new ReviewAnnotation.Point(last[0] + .5f, last[1] + .5f));
        }
        return points;
    }



    private static List<String> asked(String property) {
        String v = System.getProperty(property, "").trim();
        return v.isEmpty() ? List.of() : List.of(v.split(","));
    }
}
