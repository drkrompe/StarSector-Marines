package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechDoctrineScene.Sample;
import com.dillon.starsectormarines.battle.mech.MechDoctrineScene.Scene;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Four controlled loops comparing the player-selectable mech doctrines.
 *
 * <p>The chassis, installed weapons, deployment doctrine, command assignment,
 * initial contact, geometry, seed, and infantry screen are identical. Each
 * loop changes only the battle-only doctrine requested through
 * {@link BattleSimulation#getMechDoctrineService()}. The infantry screen is
 * removed halfway through every loop, so the first and second halves show the
 * supported and unsupported response without changing battles.
 *
 * <p>Captions carry the two readings that make the motion reviewable:
 * distance to the fixed hostile and signed position on the support-to-threat
 * axis. A positive support-axis value means the mech is ahead of the
 * infantry's last position, toward the threat; a negative value means it is
 * behind. The same ranges are printed after each artifact so a headless run
 * reports its own result without requiring frame-by-frame GIF inspection.
 */
public final class MechDoctrineSnapshotSuite implements SnapshotSuite {

    private static final List<MechRole> DOCTRINES = List.of(
            MechRole.ASSAULT,
            MechRole.ARMORED_SUPPORT,
            MechRole.LR_SUPPORT,
            MechRole.BALANCED);

    private static final int TICKS = 1350;
    private static final int SUPPORT_REMOVAL_TICK = TICKS / 2;
    private static final int FRAME_EVERY_TICKS = 30;
    private static final int FRAME_DELAY_MILLIS = 100;
    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 288;

    @Override public String id() { return "mech-doctrine"; }

    @Override public String label() {
        return "Mech doctrine: one Bulwark, four postures, with and without an infantry screen";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                context.modRoot(), FRAME_WIDTH, FRAME_HEIGHT);
        List<SnapshotArtifact> artifacts = new ArrayList<>(DOCTRINES.size());
        for (MechRole doctrine : DOCTRINES) {
            artifacts.add(record(renderer, doctrine));
        }
        return artifacts;
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer,
                                    MechRole doctrine) throws Exception {
        Scene scene = MechDoctrineScene.build(doctrine, SUPPORT_REMOVAL_TICK);
        List<BufferedImage> frames = new ArrayList<>(
                TICKS / FRAME_EVERY_TICKS + 1);
        PhaseMetrics screened = new PhaseMetrics();
        PhaseMetrics unsupported = new PhaseMetrics();
        String variantName;
        String deployedName;
        String effectiveName;

        try (BattleSimulation ignored = scene.sim()) {
            for (int tick = 0; tick <= TICKS; tick++) {
                Sample sample = MechDoctrineScene.sample(scene);
                (sample.supportAlive() ? screened : unsupported).include(sample);
                if (tick % FRAME_EVERY_TICKS == 0) {
                    frames.add(renderer.render(
                            scene.sim(), caption(doctrine, tick, sample)));
                }
                if (tick < TICKS) MechDoctrineScene.advance(scene, tick);
            }
            MechLoadoutComponent loadout = MechDoctrineScene.loadout(scene);
            variantName = loadout.variant.displayName;
            deployedName = loadout.deployedRole().displayName();
            effectiveName = loadout.effectiveRole().displayName();
        }

        System.out.printf(Locale.ROOT,
                "    [mech-doctrine] %-18s chassis=%s deployed=%s effective=%s "
                        + "seed=%d assignment=ATTACK_MOVE(%d,%d) contact=(%d,%d) "
                        + "screenRemoved=t%d frames=%d goal=%s%n",
                doctrine.displayName(), variantName, deployedName,
                effectiveName, MechDoctrineScene.SEED,
                MechDoctrineScene.ASSIGNMENT_X, MechDoctrineScene.LANE_Y,
                MechDoctrineScene.THREAT_X, MechDoctrineScene.LANE_Y,
                SUPPORT_REMOVAL_TICK, frames.size(), unsupported.lastGoal);
        System.out.printf(Locale.ROOT,
                "    [mech-doctrine] %-18s screened %s  |  screen removed %s%n",
                doctrine.displayName(), screened.describe(), unsupported.describe());

        return SnapshotArtifact.animation(
                "doctrine-" + slug(doctrine) + ".gif",
                frames, FRAME_DELAY_MILLIS);
    }

    private static String caption(MechRole doctrine, int tick, Sample sample) {
        return String.format(Locale.ROOT,
                "%s  •  t=%4.1fs  •  threat %.1f  •  screen %s  •  support-axis %+.1f",
                doctrine.displayName(), tick * BattleSimulation.TICK_DT,
                sample.threatDistance(), sample.supportAlive() ? "present" : "REMOVED",
                sample.supportAxisPosition());
    }

    private static String slug(MechRole doctrine) {
        return switch (doctrine) {
            case ASSAULT -> "brawler";
            case ARMORED_SUPPORT -> "tank";
            case LR_SUPPORT -> "long-range";
            case BALANCED -> "balanced";
        };
    }

    /** Min/max envelope for one half of a loop. */
    private static final class PhaseMetrics {
        private float minThreat = Float.POSITIVE_INFINITY;
        private float maxThreat = Float.NEGATIVE_INFINITY;
        private float minSupportAxis = Float.POSITIVE_INFINITY;
        private float maxSupportAxis = Float.NEGATIVE_INFINITY;
        private String lastGoal = "none";

        void include(Sample sample) {
            minThreat = Math.min(minThreat, sample.threatDistance());
            maxThreat = Math.max(maxThreat, sample.threatDistance());
            minSupportAxis = Math.min(minSupportAxis, sample.supportAxisPosition());
            maxSupportAxis = Math.max(maxSupportAxis, sample.supportAxisPosition());
            lastGoal = sample.goal();
        }

        String describe() {
            if (minThreat == Float.POSITIVE_INFINITY) return "no samples";
            return String.format(Locale.ROOT,
                    "threat=%.1f..%.1f support-axis=%+.1f..%+.1f",
                    minThreat, maxThreat, minSupportAxis, maxSupportAxis);
        }
    }
}
