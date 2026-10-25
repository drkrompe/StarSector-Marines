package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The company's strike detachments as the owner would see them ({@code cordon-nouns.md}, Line posture): the town Line
 * the probe plays (the Line fixture's company) played once unrecorded to find the moment, then played again to just before it and recorded through the look preset
 * with the camera on the detachment's leaders. {@code strike-push} plays Push from shortly before its two squads come
 * within {@link #AT_BORE} cells of the bore; {@code strike-recalled} plays Opportunistic with the gate closing on its
 * one squad halfway through the shot.
 *
 * <p>{@code -Dsnapshot.cordonStrike.seed=10}, {@code .ticks=900} and {@code .only=push|recalled} choose the battle,
 * the length and the shot. Opt-in: it plays a battle twice before it draws.
 */
public final class CordonStrikeLookSuite implements SnapshotSuite {

    /** The latest tick a moment is waited for. */
    static final int BY = 9000;
    /** Cells from the bore a detachment has come up to it. */
    static final float AT_BORE = 30f;
    /** Ticks of the march a Push shot keeps before the detachment comes up to the bore. */
    static final int APPROACH = 150;

    @Override public String id() { return "cordon-strike-look"; }

    @Override public String label() { return "Cordon: the company's strike detachments at a bore, and one recalled"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonStrike.seed", "10").trim());
        int ticks = Integer.parseInt(System.getProperty("snapshot.cordonStrike.ticks", "900").trim());
        String only = System.getProperty("snapshot.cordonStrike.only", "").trim().toLowerCase(Locale.ROOT);
        List<SnapshotArtifact> out = new ArrayList<>();
        if (only.isEmpty() || only.equals("push")) {
            int at = moment(CordonPosture.PUSH, seed, CordonStrikeLookSuite::atBore);
            out.addAll(LookReview.record(context.modRoot(),
                    shot("strike-push", CordonPosture.PUSH, seed, at - APPROACH, ticks)));
        }
        if (only.isEmpty() || only.equals("recalled")) {
            int at = moment(CordonPosture.OPPORTUNISTIC, seed, (sim, strikes) -> strikes.recalled() > 0);
            out.addAll(LookReview.record(context.modRoot(),
                    shot("strike-recalled", CordonPosture.OPPORTUNISTIC, seed, at - ticks / 2, ticks)));
        }
        return out;
    }

    /** A test on the battle as it plays. */
    interface Moment {
        boolean reached(BattleSimulation sim, CordonStrikes strikes);
    }

    /** The battle the probe plays: the Line fixture's company on this seed's town map. */
    private static BattleSimulation battle(CordonPosture posture, long seed) {
        try {
            return HeadlessBattles.build("cordon:variant=line,density=town,biome=arid,seed=" + seed + ",posture="
                    + posture.name().toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The first tick {@code moment} holds in the battle played unrecorded. */
    private static int moment(CordonPosture posture, long seed, Moment moment) {
        BattleSimulation sim = battle(posture, seed);
        CordonStrikes strikes = sim.getCordon().strikes();
        while (!moment.reached(sim, strikes)) {
            if (sim.getSimTickIndex() >= BY) throw new IllegalStateException(posture + ": no moment by tick " + BY + "; closest to the bore "
                    + closest + ", strikes " + strikes.readings());
            sim.advance(BattleSimulation.TICK_DT);
        }
        return sim.getSimTickIndex();
    }

    /** The nearest any detachment member has come to its bore so far, for the failure message. */
    private static double closest = Double.MAX_VALUE;

    /** Whether any living member of the detachment has come within {@link #AT_BORE} of its bore. */
    private static boolean atBore(BattleSimulation sim, CordonStrikes strikes) {
        if (strikes.target() == null) return false;
        for (int squad : strikes.members()) {
            for (int i = 0; i < sim.squadMemberCount(squad); i++) {
                long id = sim.squadMemberAt(squad, i);
                if (!sim.world().isAlive(id)) continue;
                double d = Math.hypot(sim.world().renderX(id) - strikes.target().x(),
                        sim.world().renderY(id) - strikes.target().y());
                closest = Math.min(closest, d);
                if (d <= AT_BORE) return true;
            }
        }
        return false;
    }

    private static LookReview.Shot shot(String name, CordonPosture posture, long seed, int from, int ticks) {
        return new LookReview.Shot(name, LookGround.of(SurfacePalette.ARID, seed), 40, 30, placement -> {
            BattleSimulation sim = battle(posture, seed);
            CordonStrikes strikes = sim.getCordon().strikes();
            while (sim.getSimTickIndex() < from || strikes.target() == null) {
                if (sim.getSimTickIndex() >= BY) throw new IllegalStateException("no strike out by tick " + BY);
                sim.advance(BattleSimulation.TICK_DT);
            }
            int i = 0;
            for (int squad : strikes.members()) {
                if (i == 2) break;
                placement.subject(i == 0 ? "a" : "b", sim.squadMemberAt(squad, 0));
                i++;
            }
            System.out.printf("    [look/%s] %s seed %d: squads %s at bore %s, recorded from tick %d%n", name,
                    posture, seed, strikes.members(), strikes.target().key(), sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(
                LookReview.View.follow("play", placement, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, "a", "b"),
                LookReview.View.follow("close", placement, 32f, "a", "b")), ticks, 2, 3)
                .withStills(0, ticks / 8, ticks / 4, 3 * ticks / 8, ticks / 2 - 1);
    }
}
