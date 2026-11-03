package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Closeups of hulls standing while they are being driven, to see what holds them: a bad spawn, a pileup, a corner.
 * Plays The Burn at several seeds and, the first time a hull under a move order or a mission leg has held one spot for
 * {@link #STAND_SECONDS}, takes a look frame centred on it and a copy marked with its footprint, the planner's padded
 * footprint, its route, the route's end and the other hulls near it. A finished move order leaves its route on the
 * control component, so a route alone does not say a hull is being driven. A plan does not say it is moving either: a
 * hull yielding to another keeps its plan. A line per stand on stdout says how long it stood in the end. Opt-in:
 * minutes per seed.
 *
 * <p>{@code -Dsnapshot.stands.fixture} names another fixture under {@code battle-fixtures}, and
 * {@code -Dsnapshot.stands.seeds} (comma-separated) and {@code -Dsnapshot.stands.ticks} the seeds and the length.
 */
public final class VehicleStandsSnapshotSuite implements SnapshotSuite {

    static final float STAND_SECONDS = 5f;
    /** Moving this far, or turning this many degrees, ends a stand. */
    static final float STAND_CELLS = .5f, STAND_DEGREES = 5f;
    static final float CLOSE_ACROSS = 28f;
    static final float NEIGHBOUR_CELLS = 8f;

    private static final class Watch {
        float x, y, facing, seconds, spawnX, spawnY, mostMoved;
        Shot shot;
    }

    private static final class Shot {
        String line;
        float seconds, mostClock, planned;
    }

    @Override public String id() { return "vehicle-stands"; }

    @Override public String label() { return "Closeups of hulls standing with no plan in a played battle"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String fixture = System.getProperty("snapshot.stands.fixture", "heart-burn-v1");
        int ticks = Integer.getInteger("snapshot.stands.ticks", 9000);
        String text = Files.readString(context.projectRoot().resolve(
                "src/test/resources/battle-fixtures/" + fixture + ".json"));
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String seed : System.getProperty("snapshot.stands.seeds", "202,203,204,205").split(",")) {
                JSONObject json = new JSONObject(text).put("seed", Long.parseLong(seed.trim()));
                try (BattleSimulation sim = HeadlessBattles.fixture(json)) {
                    play(context, sim, seed.trim(), ticks, artifacts);
                }
            }
        }
        return artifacts;
    }

    private static void play(SnapshotContext context, BattleSimulation sim, String seed, int ticks,
                             List<SnapshotArtifact> artifacts) throws Exception {
        ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, 0f, 0f,
                CLOSE_ACROSS);
        Map<Long, Watch> watches = new HashMap<>();
        List<Shot> shots = new ArrayList<>();
        for (int tick = 1; tick <= ticks; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
            fx.feed(sim);
            ConvoyService convoy = sim.convoy();
            for (long id : convoy.entityIds()) {
                GroundBody body = convoy.body(id);
                VehicleControlComponent control = convoy.control(id);
                VehicleMission mission = convoy.mission(id);
                if (body == null || control == null || mission == null || convoy.structure(id) <= 0f) continue;
                Watch w = watches.get(id);
                if (w == null) {
                    w = new Watch();
                    w.spawnX = body.x;
                    w.spawnY = body.y;
                    begin(w, body);
                    watches.put(id, w);
                }
                w.mostMoved = Math.max(w.mostMoved, (float) Math.hypot(body.x - w.spawnX, body.y - w.spawnY));
                boolean underWay = control.routeXs != null && !control.arrived && mission.speedCap > 0f
                        && (sim.getVehicleMoveOrderService().activeOrder(id) != null || control.leg != VehicleLeg.MOVE_ORDER);
                boolean moved = Math.hypot(body.x - w.x, body.y - w.y) > STAND_CELLS
                        || Math.abs(NeutralPivot.signedDegrees(body.facingDegrees - w.facing)) > STAND_DEGREES;
                if (moved || !underWay) {
                    begin(w, body);
                    continue;
                }
                w.seconds += BattleSimulation.TICK_DT;
                if (w.shot != null) {
                    w.shot.seconds = w.seconds;
                    w.shot.mostClock = Math.max(w.shot.mostClock, control.localPlanFailureTime);
                    if (control.hasTrajectory()) w.shot.planned += BattleSimulation.TICK_DT;
                } else if (w.seconds >= STAND_SECONDS) {
                    w.shot = new Shot();
                    w.shot.seconds = w.seconds;
                    shots.add(w.shot);
                    shoot(sim, renderer, fx, id, w, seed, tick, shots.size(), artifacts);
                }
            }
        }
        for (Shot shot : shots) {
            System.out.printf(Locale.ROOT, "STAND %s stood=%.0fs withPlan=%.0fs mostClock=%.1f%n", shot.line, shot.seconds,
                    shot.planned, shot.mostClock);
        }
        System.out.printf(Locale.ROOT, "STANDS seed=%s count=%d hulls=%d%n", seed, shots.size(), watches.size());
    }

    private static float toRouteEnd(GroundBody body, VehicleControlComponent control) {
        int last = control.routeXs.length - 1;
        return body.distanceTo(control.routeXs[last], control.routeYs[last]);
    }

    private static void begin(Watch w, GroundBody body) {
        w.x = body.x;
        w.y = body.y;
        w.facing = body.facingDegrees;
        w.seconds = 0f;
        w.shot = null;
    }

    private static void shoot(BattleSimulation sim, BattleReviewFrameRenderer renderer, ReviewImpactFx fx, long id,
                              Watch w, String seed, int tick, int number, List<SnapshotArtifact> artifacts)
            throws Exception {
        ConvoyService convoy = sim.convoy();
        GroundBody body = convoy.body(id);
        VehicleType type = convoy.vehicleType(id);
        VehicleControlComponent control = convoy.control(id);
        NavigationGrid grid = sim.getGrid();
        float paddedLength = type.visualLengthCells + HybridAStarPlanner.PLANNER_CLEARANCE;
        float paddedWidth = type.visualWidthCells + HybridAStarPlanner.PLANNER_CLEARANCE;
        BufferedImage frame = renderer.refocus(body.x, body.y, CLOSE_ACROSS)
                .render(sim, "", ReviewAnnotations.NONE, fx);
        BattleCamera camera = renderer.cameraOf(sim);

        BufferedImage marked = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = marked.createGraphics();
        g.drawImage(frame, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(90, 220, 255));
        float[] xs = control.routeXs, ys = control.routeYs;
        for (int i = 1; i < xs.length; i++) {
            g.drawLine(screenX(camera, xs[i - 1]), screenY(camera, frame, ys[i - 1]),
                    screenX(camera, xs[i]), screenY(camera, frame, ys[i]));
        }
        g.setColor(Color.GREEN);
        g.fillOval(screenX(camera, xs[xs.length - 1]) - 8, screenY(camera, frame, ys[ys.length - 1]) - 8, 16, 16);
        g.setColor(Color.YELLOW);
        outline(g, camera, frame, body.x, body.y, body.facingDegrees, paddedLength, paddedWidth);
        g.setColor(Color.RED);
        outline(g, camera, frame, body.x, body.y, body.facingDegrees, type.visualLengthCells, type.visualWidthCells);
        StringBuilder near = new StringBuilder();
        for (long other : convoy.entityIds()) {
            GroundBody o = convoy.body(other);
            if (other == id || o == null || convoy.structure(other) <= 0f) continue;
            double distance = Math.hypot(o.x - body.x, o.y - body.y);
            if (distance > NEIGHBOUR_CELLS) continue;
            VehicleType otherType = convoy.vehicleType(other);
            g.setColor(Color.MAGENTA);
            outline(g, camera, frame, o.x, o.y, o.facingDegrees, otherType.visualLengthCells, otherType.visualWidthCells);
            near.append(String.format(Locale.ROOT, "%s@%.1f,", otherType, distance));
        }
        g.dispose();

        String stem = String.format(Locale.ROOT, "seed-%s/%02d-%s-tick%d", seed, number, type, tick);
        artifacts.add(new SnapshotArtifact(stem + ".png", frame));
        artifacts.add(new SnapshotArtifact(stem + "-marked.png", marked));
        w.shot.line = String.format(Locale.ROOT,
                "seed=%s n=%d %s side=%s tick=%d at=%.1f,%.1f facing=%.0f speed=%.2f toEnd=%.1f order=%b state=%s leg=%s "
                        + "fromSpawn=%.1f mostFromSpawn=%.1f "
                        + "chassisClear=%b paddedClear=%b clock=%.1f reverses=%d attempts=%d turnarounds=%d near=[%s]",
                seed, number, type, convoy.faction(id), tick, body.x, body.y, body.facingDegrees, body.speed,
                toRouteEnd(body, control), sim.getVehicleMoveOrderService().activeOrder(id) != null,
                convoy.mission(id).state, control.leg,
                Math.hypot(body.x - w.spawnX, body.y - w.spawnY), w.mostMoved,
                VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees, type.visualLengthCells,
                        type.visualWidthCells, grid),
                VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees, paddedLength, paddedWidth, grid),
                control.localPlanFailureTime, control.planlessReverses, control.recoveryAttempts,
                control.turnaroundsUsed, near);
    }

    private static void outline(Graphics2D g, BattleCamera camera, BufferedImage frame, float x, float y, float facing,
                                float length, float width) {
        double rad = Math.toRadians(facing);
        double fx = -Math.sin(rad), fy = Math.cos(rad), sx = fy, sy = -fx;
        double[][] corners = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        int[] px = new int[4], py = new int[4];
        for (int i = 0; i < 4; i++) {
            float cx = (float) (x + fx * corners[i][0] * length / 2 + sx * corners[i][1] * width / 2);
            float cy = (float) (y + fy * corners[i][0] * length / 2 + sy * corners[i][1] * width / 2);
            px[i] = screenX(camera, cx);
            py[i] = screenY(camera, frame, cy);
        }
        g.drawPolygon(px, py, 4);
    }

    private static int screenX(BattleCamera camera, float x) {
        return Math.round(camera.cellToScreenX(x));
    }

    private static int screenY(BattleCamera camera, BufferedImage frame, float y) {
        return frame.getHeight() - Math.round(camera.cellToScreenY(y));
    }
}
