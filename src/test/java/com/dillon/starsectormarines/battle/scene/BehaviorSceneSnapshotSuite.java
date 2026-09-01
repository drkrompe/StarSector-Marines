package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records a behaviour scene as one animation per loop.
 *
 * <p><b>The picture and the verdict come from one play</b>, so they cannot
 * disagree: the frames are drawn from the very sim the verdicts were read off,
 * rather than from a second run that might have diverged. The tallies are
 * printed here as well, because a suite that only wrote GIFs would leave the
 * finding in a file nobody opens.
 *
 * <p>A concrete scene registers a two-line subclass in
 * {@code META-INF/services/com.dillon.starsectormarines.tools.snapshot.SnapshotSuite};
 * the scene itself stays registered as a {@link BehaviorScene} for
 * {@code sceneEvidence}, which plays it without a renderer.
 */
public abstract class BehaviorSceneSnapshotSuite implements SnapshotSuite {

    private final BehaviorScene scene;
    private final int width;
    private final int height;

    protected BehaviorSceneSnapshotSuite(BehaviorScene scene, int width, int height) {
        if (scene == null) throw new IllegalArgumentException("behaviour scene is required");
        this.scene = scene;
        this.width = width;
        this.height = height;
    }

    @Override public String id() { return scene.id(); }

    @Override public String label() { return scene.label(); }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), width, height);
        Map<String, List<BufferedImage>> byLoop = new LinkedHashMap<>();
        List<SceneReport> reports = scene.play((loopId, sim, tick, caption) -> {
            try {
                byLoop.computeIfAbsent(loopId, id -> new ArrayList<>())
                        .add(renderer.render(sim, caption));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });

        for (SceneReport report : reports) {
            long passed = report.verdicts().stream().filter(Verdict::pass).count();
            System.out.printf(Locale.ROOT, "    [%s/%s] %s %d/%d%n",
                    report.sceneId(), report.loopId(), report.passed() ? "PASS" : "FAIL",
                    passed, report.verdicts().size());
            for (Verdict verdict : report.verdicts()) {
                if (verdict.pass()) continue;
                System.out.printf(Locale.ROOT, "        %s: %s%n",
                        verdict.name(), verdict.detail());
            }
        }

        List<SnapshotArtifact> artifacts = new ArrayList<>(byLoop.size());
        for (Map.Entry<String, List<BufferedImage>> loop : byLoop.entrySet()) {
            artifacts.add(SnapshotArtifact.animation(loop.getKey() + ".gif",
                    loop.getValue(), scene.frameDelayMillis()));
        }
        return artifacts;
    }
}
