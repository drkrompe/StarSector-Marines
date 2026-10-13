package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Production opening at 1920x1080, 100% scale; no campaign or graphics context. */
public final class CompanyPrologueSnapshotSuite implements SnapshotSuite {
    public String id() { return "company-prologue"; }
    public String label() { return "Second Wind / campaign opening"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (var strings = new HeadlessStrings(context.modRoot())) {
            return renderPages(context);
        }
    }
    List<SnapshotArtifact> renderPages(SnapshotContext context) throws Exception {
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), CompanyPrologueScreen.COMPONENTS);
        loader.reload();
        var frames = new ArrayList<SnapshotArtifact>();
        for (int page = 0; page < CompanyPrologue.PAGE_COUNT; page++) {
            frames.add(frame(loader, renderer, page, "Jangala"));
        }
        frames.add(frame(loader, renderer, 3, "Independent Port"));
        frames.add(frame(loader, renderer, 5, null));
        return frames;
    }
    private SnapshotArtifact frame(MarkupLoader loader, HeadlessUiRenderer renderer, int page, String port) {
        var props = CompanyPrologueScreen.model(page, port, "Jangala".equals(port), 20, 500, 5000, () -> {}, () -> {}, () -> {});
        try (var instance = loader.build(new Reactor(), CompanyPrologueScreen.ROOT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return new SnapshotArtifact("page-" + (page + 1) + "-" + (port == null ? "unavailable" : port.replace(' ', '-'))
                    + "-100.png", renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }
}
