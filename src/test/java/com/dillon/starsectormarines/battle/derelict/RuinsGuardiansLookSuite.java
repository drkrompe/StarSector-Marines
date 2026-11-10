package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Derelict security's guardians (`ruins-guardians.md`) on the vast ruin, through the look preset with the roof lifted:
 * the hive cradle in its light well with its flock up, and the vault frame on its post in the vault's approach, each
 * wide and close. The site is played only until the network has stood them. Opt-in. No outcome is judged.
 */
public final class RuinsGuardiansLookSuite implements SnapshotSuite {

    static final String VAST_FIXTURE = DerelictHeaviesLookSuite.VAST_FIXTURE;
    static final int SETTLE_TICKS = 120;
    static final float WIDE_CELLS = 36f;
    static final float CLOSE_CELLS = 16f;

    @Override public String id() { return "ruins-guardians-look"; }

    @Override public String label() { return "Derelict security's guardians: the hive in its well, the vault frame"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(VAST_FIXTURE),
                    StandardCharsets.UTF_8));
            BattleSimulation sim = HeadlessBattles.fixture(fixture);
            while (sim.getSimTickIndex() < SETTLE_TICKS && step(sim)) {
                // played to the guardians' posts, or to the end of a battle decided before them
            }
            List<SnapshotArtifact> out = new ArrayList<>();
            Flock hive = null;
            for (Flock flock : sim.flocks().flocks()) if (flock.hatch() != null) hive = flock;
            if (hive != null && sim.resolveUnit(hive.cradle()) != 0L) {
                long housing = hive.cradle();
                System.out.printf("    [look/guardians] hive at (%.1f, %.1f), %d drones up%n",
                        sim.world().x(housing), sim.world().y(housing), hive.size());
                out.addAll(stills(context, sim, "guardian-hive", sim.world().renderX(housing),
                        sim.world().renderY(housing)));
            } else {
                System.out.println("    [look/guardians] no hive stood on this site");
            }
            DerelictSecurityDirector director = sim.derelictSecurity();
            if (director != null) {
                for (DerelictSecurityDirector.Walker walker : director.walkers()) {
                    if (walker.frame() != DerelictWalkerFrame.VAULT) continue;
                    if (sim.squadMemberCount(walker.squadId()) <= 0) continue;
                    long frame = sim.squadMemberAt(walker.squadId(), 0);
                    System.out.printf("    [look/guardians] vault frame at (%.1f, %.1f), %s%n",
                            sim.world().x(frame), sim.world().y(frame), director.braceOf(frame));
                    out.addAll(stills(context, sim, "guardian-vault-frame", sim.world().renderX(frame),
                            sim.world().renderY(frame)));
                    break;
                }
            }
            out.addAll(kneeClip(context, fixture));
            return out;
        }
    }

    /** The clip's length and its beats, ticks: a knee goes, then a second, and it is down. */
    static final int KNEE_TICKS = 330;
    static final int FIRST_KNEE_AT = 75;
    static final int SECOND_KNEE_AT = 225;
    /** Cells the walker covers a tick while whole: its walk. */
    static final float WALK_STEP = 0.9f * BattleSimulation.TICK_DT;
    static final float KNEE_FOLLOW_CELLS = 12f;

    /**
     * A walker losing its legs, through the look preset: the vast site's first walker paced down its route a step a
     * tick (the network keeps its walkers at their posts), its rear right knee shot out partway (it bursts, throws
     * sparks, the foot drags, the body sags and limps on at its slower pace, the LEGS marker shows), and later its
     * rear left, which downs it. The knees are struck directly, as a hit from that side would strike them.
     */
    static List<SnapshotArtifact> kneeClip(SnapshotContext context, JSONObject fixture) throws Exception {
        long[] walker = {0L};
        return LookReview.record(context.modRoot(), new LookReview.Shot("guardian-walker-knee", LookGround.verdant(),
                40, 30, placement -> limping(fixture, walker, placement),
                placement -> List.of(new LookReview.View("follow", sim -> new float[] {
                        sim.world().renderX(walker[0]), sim.world().renderY(walker[0]), KNEE_FOLLOW_CELLS})
                        .withoutRoofs()),
                KNEE_TICKS, 1, 1, new int[] {FIRST_KNEE_AT + 40, SECOND_KNEE_AT + 60}));
    }

    private static BattleSimulation limping(JSONObject fixture, long[] walker, LookReview.Placement placement) {
        DerelictWalkerLookSuite.Staged staged;
        try {
            staged = DerelictWalkerLookSuite.stage(fixture);
        } catch (Exception e) {
            throw new IllegalStateException("derelict fixture", e);
        }
        if (staged == null) throw new IllegalStateException("no walker or route in the vast site");
        walker[0] = staged.walker();
        int[] r = staged.route();
        float heading = DerelictWalkerLookSuite.heading(r);
        float[] along = {0f};
        int[] tick = {0};
        placement.beforeTick(sim -> {
            int t = tick[0]++;
            WalkerKnees knees = sim.derelictSecurity().kneesOf(staged.walker());
            if (t == FIRST_KNEE_AT) strike(knees, 2);
            if (t == SECOND_KNEE_AT) strike(knees, 1);
            along[0] += WALK_STEP * knees.speedShare();
            sim.world().setPos(staged.walker(), r[0] + 0.5f + r[2] * along[0], r[1] + 0.5f + r[3] * along[0]);
            FacingSystem.faceStanding(sim.getEntityWorld(), sim.getBattleComponents(), staged.walker(), heading);
        });
        return staged.sim();
    }

    /** Shoots out a knee, as a hit from that leg's side that finds it would. */
    private static void strike(WalkerKnees knees, int leg) {
        knees.hit(WalkerKnees.HIP_BEARING[leg], false, false, 0f, WalkerKnees.KNEE_STRUCTURE);
    }

    private static List<SnapshotArtifact> stills(SnapshotContext context, BattleSimulation sim, String name, float x,
                                                 float y) throws IOException {
        return List.of(
                new SnapshotArtifact(name + ".png", BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y,
                        WIDE_CELLS).withoutRoofs().render(sim, name)),
                new SnapshotArtifact(name + "-close.png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, x, y, CLOSE_CELLS).withoutRoofs().render(sim, name + "-close")));
    }

    /** Advances one tick; false once the battle is decided, when the tick stops advancing. */
    private static boolean step(BattleSimulation sim) {
        int before = sim.getSimTickIndex();
        sim.advance(BattleSimulation.TICK_DT);
        return sim.getSimTickIndex() != before;
    }
}
