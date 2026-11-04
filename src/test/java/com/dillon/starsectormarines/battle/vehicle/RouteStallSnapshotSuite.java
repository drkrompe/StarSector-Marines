package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Closeups of infantry route stalls where they repeat, to see what holds them: plays The Burn and, on the 3rd, 10th and
 * 30th stall in a {@link #BUCKET}-cell square, takes a look frame centred on the mover and a marked copy. Solid cells are
 * tinted red, see-through blocked cells (a sealed structure, water) blue, refused edges orange, other bodies ringed
 * white for its side and magenta for the other, the mover yellow, and its target joined in red. A stall drops the route,
 * so a {@code STALL} line on stdout has none; {@code -Dsnapshot.stalls.at=tick:id,...} takes the shot and prints a
 * {@code ROUTE} line on the tick you name, the one before the stall, with the route in green. Opt-in: minutes per seed.
 *
 * <p>{@code -Dsnapshot.stalls.seeds} (comma-separated) and {@code -Dsnapshot.stands.ticks} set the seeds and the length.
 */
public final class RouteStallSnapshotSuite implements SnapshotSuite {

    static final float ACROSS = 14f;
    static final int BUCKET = 16;

    @Override public String id() { return "route-stalls"; }
    @Override public String label() { return "Closeups of infantry route stalls where they repeat"; }
    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        int ticks = Integer.getInteger("snapshot.stands.ticks", 9000);
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (String seed : System.getProperty("snapshot.stalls.seeds", "202,203,204,205").split(",")) {
            System.out.println("SEED " + seed);
            playSeed(context, seed, ticks, artifacts);
        }
        return artifacts;
    }

    private static void playSeed(SnapshotContext context, String seed, int ticks, List<SnapshotArtifact> artifacts) throws Exception {
        String text = Files.readString(context.projectRoot().resolve("src/test/resources/battle-fixtures/heart-burn-v1.json"));
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot());
             BattleSimulation sim = HeadlessBattles.fixture(new JSONObject(text).put("seed", Long.parseLong(seed)))) {
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, 0f, 0f, ACROSS);
            Map<Long, Integer> buckets = new HashMap<>();
            int seen = 0;
            for (int tick = 1; tick <= ticks && !sim.isComplete(); tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
                fx.feed(sim);
                for (String spec : System.getProperty("snapshot.stalls.at", "").split(",")) {
                    if (spec.isEmpty()) continue;
                    String[] parts = spec.split(":");
                    long watched = Long.parseLong(parts[1]);
                    if (Integer.parseInt(parts[0]) == tick && sim.world().isAlive(watched)) {
                        System.out.println(routeLine(sim, watched, tick));
                        shoot(sim, renderer, fx, watched, tick, 0, seed, artifacts);
                    }
                }
                int now = sim.movement().routeStalls();
                if (now == seen) continue;
                seen = now;
                long id = sim.movement().lastStalled();
                if (id == 0L || !sim.world().isAlive(id)) continue;
                World w = sim.world();
                long key = (long) (w.x(id) / BUCKET) * 100000L + (long) (w.y(id) / BUCKET);
                int count = buckets.merge(key, 1, Integer::sum);
                System.out.println(line(sim, id, tick, count));
                if (count == 3 || count == 10 || count == 30) {
                    shoot(sim, renderer, fx, id, tick, count, seed, artifacts);
                }
            }
            System.out.println("STALLS seed=" + seed + " total=" + seen + " buckets=" + buckets);
        }
    }

    private static String routeLine(BattleSimulation sim, long id, int tick) {
        World w = sim.world();
        NavigationGrid grid = sim.getGrid();
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "ROUTE tick=%d id=%d %s at=%.2f,%.2f radius=%.2f settled=%b",
                tick, id, sim.identity().type(id), w.x(id), w.y(id), sim.physicalRadius(id), sim.movement().settled(id)));
        int next = sim.movement().nextWaypointIndex(id), total = sim.movement().waypointCount(id);
        out.append(" next=").append(next).append('/').append(total).append(" wps=");
        for (int i = next; i < total && i < next + 10; i++) {
            out.append(String.format(Locale.ROOT, "(%.1f,%.1f)", sim.movement().waypointX(id, i), sim.movement().waypointY(id, i)));
        }
        if (total > 0) {
            out.append(String.format(Locale.ROOT, " end=(%.1f,%.1f)", sim.movement().waypointX(id, total - 1), sim.movement().waypointY(id, total - 1)));
        }
        if (sim.identity().type(id).isSwarm()) out.append(" load=").append(sim.swarmHarvest().loadOf(id));
        LongBucket near = new LongBucket();
        sim.getUnitIndex().gather(w.x(id), w.y(id), 4f, near);
        for (int i = 0; i < near.size; i++) {
            long o = near.ids[i];
            if (o == id) continue;
            if (sim.identity().type(o).isTurret()) {
                out.append(String.format(Locale.ROOT, " turret@(%.2f,%.2f) d=%.2f radius=%.2f cellWalkable=%b", w.x(o), w.y(o),
                        Math.hypot(w.x(o) - w.x(id), w.y(o) - w.y(id)), sim.physicalRadius(o), grid.isWalkable((int) w.x(o), (int) w.y(o))));
            } else if (sim.convoy().isVehicle(o)) {
                out.append(String.format(Locale.ROOT, " hull[%s]@(%.2f,%.2f) d=%.2f state=%s speed=%.2f", sim.convoy().vehicleType(o), w.x(o), w.y(o),
                        Math.hypot(w.x(o) - w.x(id), w.y(o) - w.y(id)), sim.convoy().mission(o).state, sim.convoy().body(o).speed));
            } else {
                out.append(String.format(Locale.ROOT, " %s#%d@%.2f", sim.identity().type(o), o, Math.hypot(w.x(o) - w.x(id), w.y(o) - w.y(id))));
            }
        }
        return out.toString();
    }

    private static String line(BattleSimulation sim, long id, int tick, int count) {
        World w = sim.world();
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "STALL tick=%d n=%d id=%d %s:%s at=%.2f,%.2f hp=%.0f",
                tick, count, id, sim.identity().faction(id), sim.identity().type(id), w.x(id), w.y(id), w.hp(id)));
        int next = sim.movement().nextWaypointIndex(id), total = sim.movement().waypointCount(id);
        if (next < total) {
            out.append(String.format(Locale.ROOT, " wp=%.1f,%.1f left=%d", sim.movement().waypointX(id, next),
                    sim.movement().waypointY(id, next), total - next));
        }
        if (sim.squad().hasSquad(id)) {
            Squad squad = sim.squadOf(id);
            ObjectiveAssignment order = squad == null ? null : squad.assignmentForExecution();
            out.append(" order=").append(order == null ? "none" : order.kind().name());
        }
        long target = w.targetId(id);
        out.append(String.format(Locale.ROOT, " range=%.1f", w.attackRange(id)));
        if (target != 0L && w.isAlive(target)) {
            out.append(String.format(Locale.ROOT, " target=%s:%s@%.2f", sim.identity().faction(target), sim.identity().type(target),
                    Math.hypot(w.x(target) - w.x(id), w.y(target) - w.y(id))));
        } else {
            out.append(" target=none");
        }
        out.append(" beside=[");
        LongBucket near = new LongBucket();
        sim.getUnitIndex().gather(w.x(id), w.y(id), 3f, near);
        for (int i = 0; i < near.size; i++) {
            long o = near.ids[i];
            if (o != id) out.append(String.format(Locale.ROOT, "%s:%s@%.1f ", sim.identity().faction(o), sim.identity().type(o),
                    Math.hypot(w.x(o) - w.x(id), w.y(o) - w.y(id))));
        }
        return out.append(']').toString();
    }

    private static void shoot(BattleSimulation sim, BattleReviewFrameRenderer renderer, ReviewImpactFx fx, long id, int tick,
                              int count, String seed, List<SnapshotArtifact> artifacts) throws Exception {
        World w = sim.world();
        float cx = w.x(id), cy = w.y(id);
        BufferedImage frame = renderer.refocus(cx, cy, ACROSS).render(sim, "", ReviewAnnotations.NONE, fx);
        BattleCamera camera = renderer.cameraOf(sim);
        BufferedImage marked = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = marked.createGraphics();
        g.drawImage(frame, 0, 0, null);
        NavigationGrid grid = sim.getGrid();
        int reach = (int) ACROSS;
        for (int y = (int) cy - reach; y <= (int) cy + reach; y++) {
            for (int x = (int) cx - reach; x <= (int) cx + reach; x++) {
                if (!grid.inBounds(x, y)) continue;
                int x0 = sx(camera, x), x1 = sx(camera, x + 1), y0 = sy(camera, frame, y + 1), y1 = sy(camera, frame, y);
                if (!grid.isWalkable(x, y)) {
                    g.setColor(grid.isSeeThrough(x, y) ? new Color(60, 200, 255, 110) : new Color(255, 60, 60, 110));
                    g.fillRect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
                    continue;
                }
                g.setStroke(new BasicStroke(4f));
                g.setColor(Color.ORANGE);
                if (grid.inBounds(x + 1, y) && grid.isWalkable(x + 1, y) && !grid.canTraverseCellStep(x, y, x + 1, y)) {
                    g.drawLine(x1, y0, x1, y1);
                }
                if (grid.inBounds(x, y + 1) && grid.isWalkable(x, y + 1) && !grid.canTraverseCellStep(x, y, x, y + 1)) {
                    g.drawLine(x0, y0, x1, y0);
                }
            }
        }
        LongBucket near = new LongBucket();
        sim.getUnitIndex().gather(cx, cy, ACROSS, near);
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < near.size; i++) {
            long o = near.ids[i];
            g.setColor(sim.identity().faction(o) == sim.identity().faction(id) ? Color.WHITE : Color.MAGENTA);
            ring(g, camera, frame, w.x(o), w.y(o), 9);
        }
        g.setStroke(new BasicStroke(3f));
        g.setColor(Color.YELLOW);
        ring(g, camera, frame, cx, cy, 16);
        int next = sim.movement().nextWaypointIndex(id);
        int fromX = sx(camera, cx), fromY = sy(camera, frame, cy);
        g.setColor(Color.GREEN);
        for (int i = next; i < sim.movement().waypointCount(id); i++) {
            int toX = sx(camera, sim.movement().waypointX(id, i)), toY = sy(camera, frame, sim.movement().waypointY(id, i));
            g.drawLine(fromX, fromY, toX, toY);
            g.fillOval(toX - 5, toY - 5, 10, 10);
            fromX = toX;
            fromY = toY;
        }
        long target = w.targetId(id);
        if (target != 0L && w.isAlive(target)) {
            g.setColor(Color.RED);
            g.drawLine(sx(camera, cx), sy(camera, frame, cy), sx(camera, w.x(target)), sy(camera, frame, w.y(target)));
        }
        g.dispose();
        String stem = String.format(Locale.ROOT, "seed-%s/%d-%d-tick%d-n%d", seed, (int) cx, (int) cy, tick, count);
        artifacts.add(new SnapshotArtifact(stem + ".png", frame));
        artifacts.add(new SnapshotArtifact(stem + "-marked.png", marked));
    }

    private static void ring(Graphics2D g, BattleCamera camera, BufferedImage frame, float x, float y, int r) {
        g.drawOval(sx(camera, x) - r, sy(camera, frame, y) - r, r * 2, r * 2);
    }

    private static int sx(BattleCamera camera, float x) { return Math.round(camera.cellToScreenX(x)); }

    private static int sy(BattleCamera camera, BufferedImage frame, float y) {
        return frame.getHeight() - Math.round(camera.cellToScreenY(y));
    }
}
