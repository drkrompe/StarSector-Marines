package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.mech.MechLocomotion;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Each derelict walker size walking, turning and going at its top speed, for judging its motion (owner): in the vast
 * ruin's open floor, roofs lifted, the camera held on the walker. The script, a step each tick: stand; walk out at
 * {@link #WALK_SHARE} of top speed; stop and turn 180 degrees, asked round at the machine's own turn rate; come back at
 * top speed; and where top speed is under the rig's run threshold (the heavy and the hall frame never run in battle),
 * on at {@link #TROT_OVER} times that threshold so the run gait shows. One recording a size. No outcome is judged.
 */
public final class WalkerMotionLookSuite implements SnapshotSuite {

    static final int SETTLE_TICKS = 30;
    static final float FOLLOW_CELLS = 10f;
    static final float WALK_SHARE = 0.6f;
    static final float TROT_OVER = 1.2f;
    static final float STAND_S = 1f, WALK_S = 4f, HALT_S = 0.5f, TURN_EXTRA_S = 2.5f, TOP_S = 4f, TROT_S = 3f,
            END_S = 1.5f;

    @Override public String id() { return "walker-motion"; }

    @Override public String label() { return "Derelict walkers walking, turning and at top speed"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(
                    context.projectRoot().resolve(DerelictWatchPicksSuite.FIXTURE), StandardCharsets.UTF_8));
            List<SnapshotArtifact> out = new ArrayList<>();
            for (DerelictWalkerFrame frame : new DerelictWalkerFrame[] {DerelictWalkerFrame.ROUNDS, null,
                    DerelictWalkerFrame.HALL}) {
                out.addAll(recording(context, fixture, frame));
            }
            return out;
        }
    }

    /** The script for one size, in ticks and cells. */
    record Script(MechVariant variant, float top, float walk, float trot, int stand, int walkT, int halt, int turnT,
                  int topT, int trotT, int end, float clearance) {

        static Script of(MechVariant variant) {
            float size = variant.renderScale / MechVariant.SECURITY_WALKER.renderScale;
            DerelictLegRig.Build build = Math.abs(size - 1f) < 0.01f ? DerelictLegRig.WALKER
                    : DerelictLegRig.WALKER.scaled(size);
            float top = variant.moveSpeed;
            float trot = top > build.runSpeed() ? 0f : build.runSpeed() * TROT_OVER;
            float rate = MechLocomotion.turnRateDegrees(variant);
            return new Script(variant, top, top * WALK_SHARE, trot, toTicks(STAND_S), toTicks(WALK_S), toTicks(HALT_S),
                    toTicks(180f / rate + TURN_EXTRA_S), toTicks(TOP_S), trot > 0f ? toTicks(TROT_S) : 0, toTicks(END_S),
                    Math.max(1f, variant.radius + 1.5f));
        }

        int ticks() { return stand + walkT + halt + turnT + topT + trotT + end; }

        float out() { return walk * walkT * BattleSimulation.TICK_DT; }

        float back() { return (top * topT + trot * trotT) * BattleSimulation.TICK_DT; }

        /** How far along the route at tick t, measured from the route's start; the walk begins part way along. */
        float along(int t) {
            float start = Math.max(0f, back() - out());
            float dt = BattleSimulation.TICK_DT;
            int w = clamp(t - stand, walkT);
            float at = start + w * walk * dt;
            int tp = clamp(t - stand - walkT - halt - turnT, topT);
            int tr = clamp(t - stand - walkT - halt - turnT - topT, trotT);
            return at - (tp * top + tr * trot) * dt;
        }

        /** The heading asked at tick t, degrees past the route's heading: swung round at the machine's turn rate. */
        float turned(int t) {
            int k = clamp(t - stand - walkT - halt, turnT);
            return Math.min(180f, MechLocomotion.turnRateDegrees(variant) * k * BattleSimulation.TICK_DT);
        }

        int length() { return (int) Math.ceil(Math.max(back(), out()) + 2f); }
    }

    private static int toTicks(float seconds) { return Math.round(seconds / BattleSimulation.TICK_DT); }

    private static int clamp(int v, int max) { return Math.max(0, Math.min(max, v)); }

    private static List<SnapshotArtifact> recording(SnapshotContext context, JSONObject fixture,
                                                    DerelictWalkerFrame frame) throws Exception {
        String name = frame == null ? "heavy" : frame.name().toLowerCase();
        MechVariant variant = frame == null ? MechVariant.SECURITY_WALKER : frame.chassis;
        Script script = Script.of(variant);
        System.out.printf("    [look/walker-motion] %s: top %.2f, walk %.2f, trot %.2f cells/s; %d ticks, route %d%n",
                name, script.top, script.walk, script.trot, script.ticks(), script.length());
        long[] walker = {0L};
        return LookReview.record(context.modRoot(), new LookReview.Shot("walker-motion-" + name, LookGround.verdant(),
                40, 30, placement -> stage(fixture, frame, script, walker, placement),
                placement -> List.of(new LookReview.View("follow", sim -> new float[] {sim.world().renderX(walker[0]),
                        sim.world().renderY(walker[0]), FOLLOW_CELLS * Math.max(1f,
                        variant.renderScale / MechVariant.SECURITY_WALKER.renderScale)}, false).withoutRoofs()),
                script.ticks(), 1, 1, new int[] {script.stand + script.walkT + script.halt + script.turnT / 2}));
    }

    private static BattleSimulation stage(JSONObject fixture, DerelictWalkerFrame frame, Script script, long[] walker,
                                          LookReview.Placement placement) {
        BattleSimulation sim;
        try {
            sim = HeadlessBattles.fixture(fixture);
        } catch (Exception e) {
            throw new IllegalStateException("derelict fixture", e);
        }
        for (int i = 0; i < SETTLE_TICKS; i++) sim.advance(BattleSimulation.TICK_DT);
        DerelictSecurityDirector director = sim.derelictSecurity();
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            boolean match = frame == null ? w.frame() != DerelictWalkerFrame.ROUNDS && w.frame() != DerelictWalkerFrame.HALL
                    : w.frame() == frame;
            if (match && sim.squadMemberCount(w.squadId()) > 0) {
                walker[0] = sim.squadMemberAt(w.squadId(), 0);
                break;
            }
        }
        if (walker[0] == 0L) throw new IllegalStateException("no " + frame + " walker in the vast site");
        long id = walker[0];
        int[] r = route(sim.getGrid(), sim.world().cellX(id), sim.world().cellY(id), script.length(),
                Math.round(script.clearance));
        if (r == null) throw new IllegalStateException("no open route for " + frame);
        float heading = (float) Math.toDegrees(Math.atan2(-r[2], r[3]));
        int[] tick = {0};
        placement.beforeTick(s -> {
            int t = tick[0]++;
            float a = script.along(t);
            s.world().setPos(id, r[0] + 0.5f + r[2] * a, r[1] + 0.5f + r[3] * a);
            FacingSystem.faceStanding(s.getEntityWorld(), s.getBattleComponents(), id, heading + script.turned(t));
        });
        float a0 = script.along(0);
        sim.world().setPos(id, r[0] + 0.5f + r[2] * a0, r[1] + 0.5f + r[3] * a0);
        FacingSystem.faceStanding(sim.getEntityWorld(), sim.getBattleComponents(), id, heading);
        return sim;
    }

    /**
     * A straight route of {@code length} walkable cells with {@code clear} walkable cells either side of each, as
     * {x, y, stepX, stepY}, the one nearest (cx, cy). Null when the site has none.
     */
    static int[] route(NavigationGrid grid, int cx, int cy, int length, int clear) {
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                if (d >= bestD) continue;
                for (int[] s : steps) {
                    boolean ok = true;
                    for (int k = -1; k <= length && ok; k++) {
                        for (int side = -clear; side <= clear && ok; side++) {
                            int px = x + s[0] * k - s[1] * side;
                            int py = y + s[1] * k + s[0] * side;
                            if (!grid.inBounds(px, py) || !grid.isWalkable(px, py)) ok = false;
                        }
                    }
                    if (ok) {
                        best = new int[] {x, y, s[0], s[1]};
                        bestD = d;
                        break;
                    }
                }
            }
        }
        return best;
    }
}
