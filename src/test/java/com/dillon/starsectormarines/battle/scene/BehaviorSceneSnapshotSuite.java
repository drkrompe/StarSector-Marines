package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.ops.battleview.WarshipFollowRecording;
import com.dillon.starsectormarines.tools.snapshot.CompressedFrameList;
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

    /** Optional, read-only diagnostic marks over the same production frame. */
    protected ReviewAnnotations annotations(BattleSimulation simulation) {
        return ReviewAnnotations.NONE;
    }

    /**
     * Opt-in: draw the game's impact particles (landing blasts, wall dust,
     * smoke) and the shot layer into the frames. Off by default because most
     * reviews are about where bodies stand and a blast would hide them.
     */
    protected boolean showImpactEffects() {
        return false;
    }

    /**
     * Opt-in: draw the sun's body shadows into the frames. Off by default for the
     * same reason as impacts; a scene about a hovering hull asks for it.
     */
    protected boolean showUnitShadows() {
        return false;
    }

    /**
     * Optional second, closer recording of a loop as {@code <loop>-close} at two thirds of the frame size (the suite holds every frame in memory until the runner writes them), as
     * {@code {centreX, centreY, cellsAcross}}; null records only the whole map.
     * Asked per loop so a control that shows nothing new is not stored twice.
     */
    protected float[] closeView(String loopId) {
        return null;
    }

    /**
     * The close view for this frame of a loop, for a close view that follows something; by default the fixed
     * {@link #closeView(String)}. Asked every recorded frame once the loop has a close view at all.
     */
    protected float[] closeView(String loopId, BattleSimulation sim) {
        return closeView(loopId);
    }

    /**
     * Opt-in: record each loop through a {@link WarshipFollowRecording}, a camera that follows the hulls in the fight
     * rather than framing the whole map, densely while two hostile hulls are over the map, with a locator inset.
     * {@code -Dsnapshot.duelCamera=false} records the whole map at the scene's cadence instead.
     */
    protected boolean followWarships() {
        return false;
    }

    /** Allow review suites to keep their close view at the requested output resolution. */
    protected float closeViewScale() { return 2f / 3f; }

    /**
     * Opt-in: assemble the animations with the adaptive palette, which keeps
     * soft gradients (glow, dust, cloud) that the compact palette posterises
     * into bands. Larger files, so a scene that is about shading asks for it.
     */
    protected boolean colourAnimation() {
        return false;
    }

    /**
     * Opt-in: indices of recorded frames of a loop to also write as full-colour
     * PNG stills ({@code <loop>-frame-<n>.png}), for reading a moment closely.
     */
    protected int[] stillFrames(String loopId) {
        return new int[0];
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean impacts = showImpactEffects();
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), width, height);
        Map<String, BattleReviewFrameRenderer> closeRenderers = new LinkedHashMap<>();
        boolean shadows = showUnitShadows();
        if (impacts) renderer.withImpactEffects();
        if (shadows) renderer.withUnitShadows();
        boolean follow = followWarships() && WarshipFollowRecording.enabled();
        BattleReviewFrameRenderer mainRenderer = follow
                ? WarshipFollowRecording.mainRenderer(context.modRoot(), width, height) : renderer;
        BattleReviewFrameRenderer insetRenderer = follow
                ? WarshipFollowRecording.insetRenderer(context.modRoot(), width, height) : null;
        Map<String, WarshipFollowRecording> followByLoop = new LinkedHashMap<>();
        Map<String, ReviewImpactFx> fxByLoop = new LinkedHashMap<>();
        Map<String, List<BufferedImage>> byLoop = new LinkedHashMap<>();
        List<SceneReport> reports = scene.play(new FrameSink() {
            @Override
            public boolean records(String loopId, BattleSimulation sim, int tick, int sceneEveryTicks) {
                if (!follow) return FrameSink.super.records(loopId, sim, tick, sceneEveryTicks);
                return followFor(loopId).records(sim, tick, sceneEveryTicks);
            }

            @Override
            public void frame(String loopId, BattleSimulation sim, int tick, String caption) {
                try {
                    ReviewImpactFx fx = impacts ? fxFor(loopId) : null;
                    WarshipFollowRecording following = follow ? followFor(loopId) : null;
                    if (following != null) following.aim(mainRenderer, sim, tick);
                    BufferedImage image = mainRenderer.render(sim, caption, annotations(sim), fx);
                    if (following != null) following.decorate(image, sim, tick, insetRenderer, fx);
                    byLoop.computeIfAbsent(loopId, id -> new CompressedFrameList()).add(image);
                    BattleReviewFrameRenderer closeRenderer = closeRenderers.computeIfAbsent(loopId, id -> {
                        float[] close = closeView(id);
                        if (close == null) return null;
                        BattleReviewFrameRenderer closer = new BattleReviewFrameRenderer(context.modRoot(),
                                Math.round(width * closeViewScale()), Math.round(height * closeViewScale()),
                                close[0], close[1], close[2]);
                        // A close view is where a blast reads, so it draws what the main view was asked to.
                        if (impacts) closer.withImpactEffects();
                        return shadows ? closer.withUnitShadows() : closer;
                    });
                    if (closeRenderer != null) {
                        float[] now = closeView(loopId, sim);
                        if (now != null) closeRenderer.refocus(now[0], now[1], now[2]);
                        byLoop.computeIfAbsent(loopId + "-close", id -> new CompressedFrameList())
                                .add(closeRenderer.render(sim, caption, annotations(sim), fx));
                    }
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }

            @Override
            public void advanced(String loopId, BattleSimulation sim, int tick) {
                if (impacts) fxFor(loopId).feed(sim);
            }

            private WarshipFollowRecording followFor(String loopId) {
                return followByLoop.computeIfAbsent(loopId, id -> new WarshipFollowRecording(width, height));
            }

            private ReviewImpactFx fxFor(String loopId) {
                return fxByLoop.computeIfAbsent(loopId, id -> new ReviewImpactFx(context.modRoot()));
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
            artifacts.add(colourAnimation()
                    ? SnapshotArtifact.colorAnimation(loop.getKey() + ".gif",
                            loop.getValue(), scene.frameDelayMillis())
                    : SnapshotArtifact.animation(loop.getKey() + ".gif",
                            loop.getValue(), scene.frameDelayMillis()));
            for (int index : stillFrames(loop.getKey())) {
                if (index < 0 || index >= loop.getValue().size()) continue;
                artifacts.add(new SnapshotArtifact(loop.getKey() + "-frame-" + index + ".png",
                        loop.getValue().get(index)));
            }
        }
        return artifacts;
    }
}
