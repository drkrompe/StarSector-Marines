package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.HoundHeatScene;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Real range frames and live production HUD bindings at 1920x1080, 100% scale. */
public final class HoundHeatSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "hound-heat"; }
    @Override public String label() { return "Hound heat and manual movement"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        var battleRenderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080);
        var uiRenderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        List<BufferedImage> reverseFrames = new ArrayList<>();
        var reports = new HoundHeatScene().play((loop, sim, tick, caption) -> {
            boolean reverse = loop.equals("reverse-strafe") && tick <= 180;
            if (!reverse && tick != 0 && tick != 90 && tick != 180 && tick != 360) return;
            try {
                var frame = battleRenderer.render(sim, caption);
                if (sim.directControl().active()) {
                    try (var fixture = BattleDirectControlHudTest.fixture(context.modRoot(),
                            BattleDirectControlStatus.snapshot(sim), 0, false, ignored -> {}, () -> {}, () -> {})) {
                        Graphics2D graphics = frame.createGraphics();
                        try {
                            graphics.drawImage(uiRenderer.render(fixture.document(), 640, 70), 640, 980, null);
                        } finally { graphics.dispose(); }
                    }
                }
                if (reverse) reverseFrames.add(frame);
                if (tick == 0 || tick == 90 || tick == 180 || tick == 360) {
                    artifacts.add(new SnapshotArtifact(loop + "-" + tick + ".png", frame));
                }
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        for (var report : reports) {
            System.out.println(report.loopId() + " " + (report.passed() ? "PASS " : "FAIL ") + report.metrics());
            if (!report.passed()) throw new IllegalStateException(report.verdicts().toString());
        }
        artifacts.add(SnapshotArtifact.animation("reverse-strafe.gif", reverseFrames, 1000));
        return artifacts;
    }
}
