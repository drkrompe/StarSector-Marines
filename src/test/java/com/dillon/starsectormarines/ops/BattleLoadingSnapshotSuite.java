package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.LoadingProgress;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen a battle shows while its textures load, at 1920x1080 and 100%
 * ({@code battle-textures.md}, Loading screen): the card part-way through, and the spinner at
 * each of its three steps so its walk can be read off stills.
 */
public final class BattleLoadingSnapshotSuite implements SnapshotSuite {

    private static final String OPERATION = "TAKE BACK THE DEPOT";
    private static final String AREA = "Agreus";

    /** A stand-in loader part-way through its sprites. */
    private record Progress(int stepsDone, int stepCount, String stage) implements LoadingProgress { }

    @Override public String id() { return "battle-loading"; }
    @Override public String label() { return "Battle loading screen"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        Progress progress = new Progress(5, 11, "Loading sprites");
        List<SnapshotArtifact> out = new ArrayList<>();
        float step = BattleLoadingOverlayModel.SPIN_PERIOD_SECONDS / BattleLoadingOverlayModel.DOTS;
        for (int dot = 0; dot < BattleLoadingOverlayModel.DOTS; dot++) {
            out.add(new SnapshotArtifact("0" + (dot + 1) + "-card-spinner-" + dot + ".png",
                    frame(context, renderer, progress, dot * step + step / 2f)));
        }
        return out;
    }

    private static BufferedImage frame(SnapshotContext context, HeadlessUiRenderer renderer,
                                       LoadingProgress progress, float spinSeconds) throws Exception {
        Reactor reactor = new Reactor();
        BattleLoadingOverlayModel model = new BattleLoadingOverlayModel(reactor);
        model.heading(OPERATION, AREA);
        model.update(spinSeconds, progress);
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                List.of(BattleLoadingOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance markup = loader.build(reactor, BattleLoadingOverlay.COMPONENT, model.props())) {
            markup.requireElement("battle-loading-progress").align(UiAlign.START, UiAlign.STRETCH);
            UiDocument document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
        }
    }
}
