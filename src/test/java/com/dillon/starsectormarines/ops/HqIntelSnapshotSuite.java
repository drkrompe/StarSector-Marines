package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in HQ motion evidence through the production document and canvas producer. */
public final class HqIntelSnapshotSuite implements SnapshotSuite {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int FRAME_DELAY_MILLIS = 80;
    private static final int FRAME_COUNT = 84;
    private static final List<String> COMPONENTS = List.of(
            "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/company/company-hq.mlx");

    @Override public String id() { return "hq-intel"; }
    @Override public String label() { return "Company HQ intelligence motion"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                context.modRoot(), context.starsectorCore());
        Reactor reactor = new Reactor();
        CompanyHqNewsDesk desk = new CompanyHqNewsDesk(reactor);
        Map<String, Object> props = CompanyHqViewModel.preview().props();
        desk.put(props);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(context.modRoot().resolve(path)), COMPONENTS);
        loader.reload();

        List<BufferedImage> frames = new ArrayList<>(FRAME_COUNT);
        BufferedImage defaultView = null;
        BufferedImage rumorView = null;
        BufferedImage arrivalView = null;
        try (MarkupInstance instance = loader.build(reactor, "company-hq", props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            CompanyIntelTerminal terminal = new CompanyIntelTerminal(desk);
            terminal.bind(document, instance);
            terminal.advance(0f);

            // One enduring document and producer: selection and arrival happen
            // inside playback, and both clocks move forward between frames.
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                if (frame == 24) {
                    click(document, instance, "hq-wire-tab-rumor");
                    requireHeadline(desk, "Reports of Compact action against House Umbra");
                }
                if (frame == 48) {
                    click(document, instance, "hq-source-confirmed-preview-news-1");
                    requireHeadline(desk, "House Cavor displaces House Tobin");
                }
                if (frame == 60) {
                    receiveFixtureReport(props, desk);
                    desk.select("fixture-arrival");
                }
                reactor.flush();
                terminal.advance(FRAME_DELAY_MILLIS / 1000f);
                document.advance(FRAME_DELAY_MILLIS / 1000f);
                BufferedImage image = renderer.renderRelative(document, WIDTH, HEIGHT, 1f,
                        MarineOpsUiViewport.REFERENCE_WIDTH,
                        MarineOpsUiViewport.REFERENCE_HEIGHT);
                frames.add(image);
                if (frame == 12) defaultView = image;
                if (frame == 36) rumorView = image;
                if (frame == 72) arrivalView = image;
            }
        }
        return List.of(
                new SnapshotArtifact("company-hq-default-1920x1080.png", defaultView),
                new SnapshotArtifact("company-hq-rumor-1920x1080.png", rumorView),
                new SnapshotArtifact("company-hq-arrival-1920x1080.png", arrivalView),
                SnapshotArtifact.animation("company-hq-intel-1920x1080.gif",
                        frames, FRAME_DELAY_MILLIS));
    }

    @SuppressWarnings("unchecked")
    private static void receiveFixtureReport(Map<String, Object> props, CompanyHqNewsDesk desk) {
        List<CompanyHqViewModel.NewsPost> reports = new ArrayList<>(
                (List<CompanyHqViewModel.NewsPost>) props.get("newsPosts"));
        // Explicit preview input, never inserted into the campaign news archive.
        // Corroborates the same named houses/place already in the preview fixture.
        reports.add(0, new CompanyHqViewModel.NewsPost("fixture-arrival", "news-card",
                "CONFIRMED  /  JUST RECEIVED", "Kazeron report corroborates Compact action",
                "PREVIEW FIXTURE / Company channels corroborate the earlier report of Compact action "
                        + "against House Umbra at Kazeron. This arrival demonstrates the received-report projection.",
                false, false));
        props.put("newsPosts", List.copyOf(reports));
        desk.put(props);
    }

    private static void click(UiDocument document, MarkupInstance instance, String id) {
        var box = instance.requireElement(id).box().borderBox();
        float x = box.x() + box.width() / 2f;
        float y = box.y() + box.height() / 2f;
        document.pointerMoved(x, y);
        document.pointerDown(x, y);
        document.pointerUp(x, y);
    }

    private static void requireHeadline(CompanyHqNewsDesk desk, String expected) {
        if (!expected.equals(desk.snapshot().headline())) {
            throw new IllegalStateException("HQ evidence interaction did not select: " + expected);
        }
    }
}
