package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.LiftSchedule.DropOff;
import com.dillon.starsectormarines.battle.air.LiftSchedule.Phase;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.CompressedFrameList;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The lift timer on a Conquest beachhead, through the look preset with the mission markers on: one drop-off point at
 * play zoom from the opening drop through the next lift setting down, as a GIF and three stills (counting down, the
 * lift inbound, the lift down). 1920x1080. Run it with {@code -Dsnapshot.backend=java2d}: the GPU review drain draws
 * no bitmap labels, so its frames carry the ring and none of the text.
 *
 * <p>It also measures the timer against the battle: every second it notes when each lift says it will be down, and
 * when the lift actually lands it prints how far out those readings were, by how long before touchdown they were
 * taken. A timer that drifts by more than it can show (a second) is a timer the player cannot trust.
 */
public final class LiftTimerSnapshotSuite implements SnapshotSuite {

    static final String FIXTURE = "src/test/resources/battle-fixtures/conquest-reinforced-south-v3.json";
    /** The last tick recorded, should the second lift never come. */
    static final int RECORD_BY = 2400;
    static final int EVERY_TICKS = 10;

    @Override public String id() { return "lift-timer"; }

    @Override public String label() { return "Lift timer: when the next shuttle sets down at a Conquest drop-off"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot());
             BattleSimulation sim = HeadlessBattles.fixture(
                     new JSONObject(Files.readString(context.projectRoot().resolve(FIXTURE))))) {
            installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<DropOff> points = LiftSchedule.capture(sim, Faction.MARINE);
            if (points.isEmpty()) throw new IllegalStateException("the fixture flies no marine lifts");
            DropOff watched = points.get(0);
            BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, watched.x(), watched.y(), BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                    .withMissionMarkers();
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            List<BufferedImage> frames = new CompressedFrameList();
            Map<Long, ShuttleState> was = new HashMap<>();
            Map<Long, List<float[]>> said = new HashMap<>();
            List<float[]> errors = new ArrayList<>();
            // A wave is the drop-off going from waiting to a lift on the pad; a pair landing together is one wave.
            int waves = 0, countdown = -1, inbound = -1, down = -1;
            boolean wasDown = false;
            for (int tick = 0; tick <= RECORD_BY; tick++) {
                if (tick > 0) {
                    sim.advance(BattleSimulation.TICK_DT);
                    fx.feed(sim);
                }
                readLifts(sim, tick * BattleSimulation.TICK_DT, tick % 30 == 0, was, said, errors);
                DropOff point = nearest(LiftSchedule.capture(sim, Faction.MARINE), watched);
                boolean isDown = point != null && point.next().phase() == Phase.UNLOADING;
                if (isDown && !wasDown) waves++;
                wasDown = isDown;
                if (tick % EVERY_TICKS != 0) continue;
                if (point != null && waves >= 1 && !isDown) {
                    float eta = point.next().secondsToTouchdown();
                    if (countdown < 0 && eta > 6f) countdown = frames.size();
                    if (inbound < 0 && countdown >= 0 && eta <= 4f) inbound = frames.size();
                }
                if (down < 0 && waves >= 2 && isDown) down = frames.size();
                frames.add(renderer.render(sim, "", ReviewAnnotations.NONE, fx));
                if (down >= 0 && frames.size() > down + 20) break;
            }
            report(errors);
            int delay = Math.round(1000f * BattleSimulation.TICK_DT * EVERY_TICKS);
            List<SnapshotArtifact> out = new ArrayList<>();
            out.add(SnapshotArtifact.colorAnimation("look/lift-timer-play.gif", frames, delay));
            int[] stills = {countdown, inbound, down};
            String[] names = {"01-counting-down", "02-inbound", "03-down"};
            for (int i = 0; i < stills.length; i++) {
                if (stills[i] >= 0) out.add(new SnapshotArtifact("look/lift-timer-" + names[i] + ".png", frames.get(stills[i])));
            }
            System.out.printf("    [lift-timer] drop-off at %.0f,%.0f, %d frames, stills at %d %d %d%n",
                    watched.x(), watched.y(), frames.size(), countdown, inbound, down);
            return out;
        }
    }

    /**
     * Notes each marine lift's predicted touchdown (when {@code sample}) and, on a landing, scores the predictions
     * made for it.
     */
    private static void readLifts(BattleSimulation sim, float now, boolean sample, Map<Long, ShuttleState> was,
                                  Map<Long, List<float[]>> said, List<float[]> errors) {
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            if (world.airFaction(id) != Faction.MARINE) continue;
            ShuttleMission m = world.mission(id);
            AirBody body = world.kinematics(id);
            Airframe frame = world.airframe(id);
            if (m == null || body == null || frame == null) continue;
            ShuttleState before = was.put(id, m.state);
            if (m.state == ShuttleState.LANDED && before != ShuttleState.LANDED) {
                for (float[] p : said.getOrDefault(id, List.of())) errors.add(new float[]{now - p[0], p[1] - now});
                said.remove(id);
            }
            if (!sample) continue;
            LiftSchedule.Lift lift = LiftSchedule.lift(m, body.x, body.y, frame.flight().maxSpeed());
            if (lift != null && lift.phase() == Phase.INBOUND) {
                said.computeIfAbsent(id, k -> new ArrayList<>()).add(new float[]{now, now + lift.secondsToTouchdown()});
            }
        }
    }

    /** The label fonts' metrics, which the game would have loaded; without them a label measures and draws nothing. */
    private static void installFonts(SnapshotContext context, BitmapFont... fonts) throws Exception {
        for (BitmapFont font : fonts) {
            Path path = context.modRoot().resolve(font.sourcePath());
            if (!Files.exists(path)) path = context.starsectorCore().resolve(font.sourcePath());
            font.installMetrics(Files.readString(path));
        }
    }

    private static DropOff nearest(List<DropOff> points, DropOff to) {
        DropOff best = null;
        for (DropOff p : points) {
            if (Math.hypot(p.x() - to.x(), p.y() - to.y()) > LiftSchedule.SAME_POINT_CELLS) continue;
            if (best == null || Math.hypot(p.x() - to.x(), p.y() - to.y()) < Math.hypot(best.x() - to.x(), best.y() - to.y())) best = p;
        }
        return best;
    }

    /** Prediction error (predicted minus actual touchdown, seconds) by how long before touchdown it was read. */
    private static void report(List<float[]> errors) {
        float[][] bands = {{0, 3}, {3, 6}, {6, 10}, {10, 20}, {20, 60}, {60, 9999}};
        for (float[] band : bands) {
            int n = 0;
            float sum = 0, worst = 0;
            for (float[] e : errors) {
                if (e[0] < band[0] || e[0] >= band[1]) continue;
                n++;
                sum += e[1];
                if (Math.abs(e[1]) > Math.abs(worst)) worst = e[1];
            }
            if (n > 0) System.out.printf(Locale.ROOT, "    [lift-timer] read %4.0f-%4.0fs out: n=%d mean %+.2fs worst %+.2fs%n",
                    band[0], band[1], n, sum / n, worst);
        }
    }
}
