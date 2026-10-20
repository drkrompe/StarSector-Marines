package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.dillon.starsectormarines.campaign.start.FoundingReserve;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;
import com.dillon.starsectormarines.i18n.Strings;
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
import java.util.Map;
import com.dillon.starsectormarines.ops.CompanyYardReview.Stage;

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
        frames.add(frame(loader, renderer, 5, "Independent Port"));
        frames.add(frame(loader, renderer, CompanyPrologue.PAGE_COUNT - 1, null));
        for (Stage stage : Stage.values()) frames.add(yardFrame(loader, renderer, stage, true, null));
        frames.add(yardFrame(loader, renderer, Stage.COST, false, null));
        frames.add(yardFrame(loader, renderer, Stage.COST, false,
                Strings.format("openingCompanyYardTheConversionRequiresCredits", MercenaryCompanyConversion.CREDIT_COST)));
        return frames;
    }
    /**
     * Reserved frames follow the protected start: Castle's chits pay the bill, and the account is the starting one.
     * Account frames pay from the ship's account: a covered order, and a refused one.
     */
    private SnapshotArtifact yardFrame(MarkupLoader loader, HeadlessUiRenderer renderer, Stage stage,
                                       boolean reserved, String refusal) {
        int reserve = reserved && stage == Stage.COST ? FoundingReserve.CREDITS : 0;
        int credits = reserved ? CerberusCompanyStart.STARTING_CREDITS : refusal == null ? 6000 : 2500;
        var props = CompanyYardScreen.model(stage, "Second Wind", credits,
                reserve, refusal, null, true, () -> {}, () -> {}, () -> {});
        return renderFrame(loader, renderer, props, "yard-" + (stage.ordinal() + 1) + "-" + stage.name().toLowerCase()
                + (reserved ? "-reserve" : "-account") + (refusal == null ? "" : "-refused") + "-100.png");
    }
    private SnapshotArtifact frame(MarkupLoader loader, HeadlessUiRenderer renderer, int page, String port) {
        var props = CompanyPrologueScreen.model(page, port, 25, 473, 4862, () -> {}, () -> {}, () -> {});
        return renderFrame(loader, renderer, props, "page-" + (page + 1) + "-" + (port == null ? "unavailable" : port.replace(' ', '-')) + "-100.png");
    }
    private SnapshotArtifact renderFrame(MarkupLoader loader, HeadlessUiRenderer renderer, Map<String, Object> props, String name) {
        try (var instance = loader.build(new Reactor(), CompanyPrologueScreen.ROOT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return new SnapshotArtifact(name, renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }
}
