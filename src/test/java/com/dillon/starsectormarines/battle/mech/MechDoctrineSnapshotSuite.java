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
 * Controlled loops comparing the player-selectable mech doctrines, plus a
 * paired Brawler run comparing the two lance-wide cohesion orders.
 *
 * <p>The chassis, installed weapons, deployment doctrine, command assignment,
 * initial contact, geometry, seed, and infantry screen are identical. Each
 * loop changes only the battle-only doctrine requested through
 * {@link BattleSimulation#getMechDoctrineService()}. The infantry screen is
 * removed halfway through every loop, so the first and second halves show the
 * supported and unsupported response without changing battles.
 *
 * <p>The two additional Brawler loops replace the infantry screen with a
 * zero-locomotion Sirocco leader in the selected Bulwark's own two-mech squad.
 * Both loops hold both chassis/loadouts, contact, attack-move command,
 * geometry, and seed fixed. Their only varied request is Form-on-Lead versus
 * Free-Reign. The contact standoff lies on the attack-move progress leash, so
 * the independent run visibly exercises that mission boundary instead of
 * merely remaining far inside it.
 *
 * <p>Captions carry the two readings that make the motion reviewable:
 * distance to the fixed hostile and signed position on the anchor-to-threat
 * axis. The four doctrine loops label their infantry anchor as support; the
 * paired loops label the same projection lead-relative to the Sirocco. A
 * positive value means the Bulwark is ahead of that live anchor toward the
 * threat. The same ranges are printed after each artifact so a headless run
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
    private static final int LANCE_ORDER_TICKS = TICKS;
    private static final float ENVELOPE_TOLERANCE = 0.25f;
    private static final float LANCE_LEADER_DRIFT_TOLERANCE = 0.5f;

    @Override public String id() { return "mech-doctrine"; }

    @Override public String label() {
        return "Mech doctrine: four postures plus paired Brawler lance orders";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                context.modRoot(), FRAME_WIDTH, FRAME_HEIGHT);
        List<SnapshotArtifact> artifacts = new ArrayList<>(DOCTRINES.size() + 2);
        for (MechRole doctrine : DOCTRINES) {
            artifacts.add(record(renderer, doctrine));
        }
        LanceOrderRecording formOnLead = recordLanceOrder(
                renderer, MechLanceOrder.FORM_ON_LEAD);
        LanceOrderRecording freeReign = recordLanceOrder(
                renderer, MechLanceOrder.FREE_REIGN);
        assertLanceOrderEvidence(formOnLead.metrics(), freeReign.metrics());
        artifacts.add(formOnLead.artifact());
        artifacts.add(freeReign.artifact());
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

    /** Records one half of the controlled Brawler lance-order pair. */
    private LanceOrderRecording recordLanceOrder(
            BattleReviewFrameRenderer renderer,
            MechLanceOrder order) throws Exception {
        Scene scene = MechDoctrineScene.buildBrawlerLanceOrder(order);
        List<BufferedImage> frames = new ArrayList<>(
                LANCE_ORDER_TICKS / FRAME_EVERY_TICKS + 1);
        PhaseMetrics metrics = new PhaseMetrics();
        String variantName;
        String deployedName;
        String effectiveName;

        try (BattleSimulation ignored = scene.sim()) {
            for (int tick = 0; tick <= LANCE_ORDER_TICKS; tick++) {
                Sample sample = MechDoctrineScene.sample(scene);
                metrics.include(sample);
                if (tick % FRAME_EVERY_TICKS == 0) {
                    frames.add(renderer.render(scene.sim(),
                            lanceOrderCaption(order, tick, sample)));
                }
                if (tick < LANCE_ORDER_TICKS) {
                    MechDoctrineScene.advance(scene, tick);
                }
            }
            MechLoadoutComponent loadout = MechDoctrineScene.loadout(scene);
            variantName = loadout.variant.displayName;
            deployedName = loadout.deployedRole().displayName();
            effectiveName = loadout.effectiveRole().displayName();
        }

        System.out.printf(Locale.ROOT,
                "    [mech-lance-order] %-12s follower=%s(%d,%d) deployed=%s "
                        + "effective=%s seed=%d assignment=ATTACK_MOVE(%d,%d) "
                        + "contact=(%d,%d) leader=Sirocco(%d,%d) "
                        + "lanceSize=2 frames=%d goal=%s%n",
                orderLabel(order), variantName,
                MechDoctrineScene.LANCE_MECH_X, MechDoctrineScene.LANE_Y,
                deployedName, effectiveName,
                MechDoctrineScene.SEED,
                MechDoctrineScene.LANCE_ASSIGNMENT_X, MechDoctrineScene.LANE_Y,
                MechDoctrineScene.THREAT_X, MechDoctrineScene.LANE_Y,
                MechDoctrineScene.LANCE_LEADER_X,
                MechDoctrineScene.LANCE_LEADER_Y,
                frames.size(), metrics.lastGoal);
        System.out.printf(Locale.ROOT,
                "    [mech-lance-order] %-12s threat=%.1f..%.1f "
                        + "lead-relative=%+.1f..%+.1f (bound=%.1f) "
                        + "mission-overshoot=%.1f (leash=%.1f) "
                        + "leader-drift=%.2f%n",
                orderLabel(order), metrics.minThreat, metrics.maxThreat,
                metrics.minAnchorAxis, metrics.maxAnchorAxis,
                BreachAndAssault.MAX_SUPPORT_LEAD,
                metrics.maxMissionOvershoot,
                MechAssignmentBoundary.ATTACK_MOVE_PROGRESS_LEASH,
                metrics.maxLanceLeaderDrift);

        SnapshotArtifact artifact = SnapshotArtifact.animation(
                "brawler-" + orderSlug(order) + ".gif",
                frames, FRAME_DELAY_MILLIS);
        return new LanceOrderRecording(metrics, artifact);
    }

    /**
     * Makes the paired artifact self-verifying in headless runs. Form-on-Lead
     * must actually reach, but never cross, the current six-cell lead clamp.
     * Free-Reign must cross that clamp and close materially farther, while
     * neither run may cross the attack-move mission-progress leash.
     */
    private static void assertLanceOrderEvidence(
            PhaseMetrics formOnLead, PhaseMetrics freeReign) {
        float leadBound = BreachAndAssault.MAX_SUPPORT_LEAD;
        float missionLeash = MechAssignmentBoundary.ATTACK_MOVE_PROGRESS_LEASH;
        float leadToThreatX = MechDoctrineScene.THREAT_X
                - MechDoctrineScene.LANCE_LEADER_X;
        float leadToThreatY = MechDoctrineScene.LANE_Y
                - MechDoctrineScene.LANCE_LEADER_Y;
        float leadAxisLength = (float) Math.sqrt(
                leadToThreatX * leadToThreatX
                + leadToThreatY * leadToThreatY);
        float assignmentAxis = ((MechDoctrineScene.LANCE_ASSIGNMENT_X
                - MechDoctrineScene.LANCE_LEADER_X) * leadToThreatX
                + (MechDoctrineScene.LANE_Y
                - MechDoctrineScene.LANCE_LEADER_Y) * leadToThreatY)
                / leadAxisLength;

        require(formOnLead.maxAnchorAxis >= leadBound - 1.5f,
                "Form-on-Lead never reached the lead clamp: max %.2f, bound %.2f",
                formOnLead.maxAnchorAxis, leadBound);
        require(formOnLead.maxAnchorAxis <= leadBound + ENVELOPE_TOLERANCE,
                "Form-on-Lead crossed the lead clamp: max %.2f, bound %.2f",
                formOnLead.maxAnchorAxis, leadBound);
        require(freeReign.maxAnchorAxis >= assignmentAxis - 1.5f,
                "Free-Reign did not close independently to the command area: "
                        + "max lead %.2f, expected at least %.2f",
                freeReign.maxAnchorAxis, assignmentAxis - 1.5f);
        require(freeReign.maxAnchorAxis > leadBound + 4f,
                "Free-Reign did not clear the Form-on-Lead envelope: "
                        + "max %.2f, bound %.2f",
                freeReign.maxAnchorAxis, leadBound);
        require(freeReign.minThreat < formOnLead.minThreat - 4f,
                "Free-Reign did not close materially farther: free %.2f, form %.2f",
                freeReign.minThreat, formOnLead.minThreat);
        require(formOnLead.maxMissionOvershoot
                        <= missionLeash + ENVELOPE_TOLERANCE,
                "Form-on-Lead crossed the assignment leash: %.2f > %.2f",
                formOnLead.maxMissionOvershoot, missionLeash);
        require(freeReign.maxMissionOvershoot
                        <= missionLeash + ENVELOPE_TOLERANCE,
                "Free-Reign crossed the assignment leash: %.2f > %.2f",
                freeReign.maxMissionOvershoot, missionLeash);
        require(freeReign.maxMissionOvershoot >= missionLeash - 1f,
                "Free-Reign never exercised the assignment leash: %.2f < %.2f",
                freeReign.maxMissionOvershoot, missionLeash - 1f);
        require(formOnLead.maxLanceLeaderDrift
                        <= LANCE_LEADER_DRIFT_TOLERANCE,
                "Form-on-Lead lance lead was not stable: drift %.2f > %.2f",
                formOnLead.maxLanceLeaderDrift,
                LANCE_LEADER_DRIFT_TOLERANCE);
        require(freeReign.maxLanceLeaderDrift
                        <= LANCE_LEADER_DRIFT_TOLERANCE,
                "Free-Reign lance lead was not stable: drift %.2f > %.2f",
                freeReign.maxLanceLeaderDrift,
                LANCE_LEADER_DRIFT_TOLERANCE);
    }

    private static void require(boolean condition, String message,
                                Object... arguments) {
        if (!condition) {
            throw new IllegalStateException(
                    String.format(Locale.ROOT, message, arguments));
        }
    }

    private static String caption(MechRole doctrine, int tick, Sample sample) {
        return String.format(Locale.ROOT,
                "%s  •  t=%4.1fs  •  threat %.1f  •  screen %s  •  support-axis %+.1f",
                doctrine.displayName(), tick * BattleSimulation.TICK_DT,
                sample.threatDistance(), sample.supportAlive() ? "present" : "REMOVED",
                sample.anchorAxisPosition());
    }

    private static String lanceOrderCaption(MechLanceOrder order, int tick,
                                            Sample sample) {
        return String.format(Locale.ROOT,
                "Brawler • %s • t=%4.1fs • threat %.1f • lead-relative %+.1f/%.0f "
                        + "• mission %.1f/%.0f",
                orderLabel(order), tick * BattleSimulation.TICK_DT,
                sample.threatDistance(), sample.anchorAxisPosition(),
                BreachAndAssault.MAX_SUPPORT_LEAD,
                sample.missionOvershoot(),
                MechAssignmentBoundary.ATTACK_MOVE_PROGRESS_LEASH);
    }

    private static String orderLabel(MechLanceOrder order) {
        return switch (order) {
            case FORM_ON_LEAD -> "Form on Lead";
            case FREE_REIGN -> "Free Reign";
        };
    }

    private static String orderSlug(MechLanceOrder order) {
        return switch (order) {
            case FORM_ON_LEAD -> "form-on-lead";
            case FREE_REIGN -> "free-reign";
        };
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
        private float minAnchorAxis = Float.POSITIVE_INFINITY;
        private float maxAnchorAxis = Float.NEGATIVE_INFINITY;
        private float maxMissionOvershoot = 0f;
        private float maxLanceLeaderDrift = 0f;
        private String lastGoal = "none";

        void include(Sample sample) {
            minThreat = Math.min(minThreat, sample.threatDistance());
            maxThreat = Math.max(maxThreat, sample.threatDistance());
            minAnchorAxis = Math.min(
                    minAnchorAxis, sample.anchorAxisPosition());
            maxAnchorAxis = Math.max(
                    maxAnchorAxis, sample.anchorAxisPosition());
            maxMissionOvershoot = Math.max(
                    maxMissionOvershoot, sample.missionOvershoot());
            maxLanceLeaderDrift = Math.max(
                    maxLanceLeaderDrift, sample.lanceLeaderDrift());
            lastGoal = sample.goal();
        }

        String describe() {
            if (minThreat == Float.POSITIVE_INFINITY) return "no samples";
            return String.format(Locale.ROOT,
                    "threat=%.1f..%.1f support-axis=%+.1f..%+.1f",
                    minThreat, maxThreat, minAnchorAxis, maxAnchorAxis);
        }
    }

    private record LanceOrderRecording(PhaseMetrics metrics,
                                       SnapshotArtifact artifact) {}
}
