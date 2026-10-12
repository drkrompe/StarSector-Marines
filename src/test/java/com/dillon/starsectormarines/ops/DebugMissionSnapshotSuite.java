package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Production launcher documents at 1920x1080 and 100% scale. */
public final class DebugMissionSnapshotSuite implements SnapshotSuite {
    private static void click(UiDocument document, UiElement element) {
        var box = element.box().borderBox();
        float x = box.x() + box.width() / 2f, y = box.y() + box.height() / 2f;
        document.pointerDown(x, y);
        document.pointerUp(x, y);
    }
    public String id() { return "debug-missions"; }
    public String label() { return "Debug mission launcher"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.modRoot().getParent());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var frames = new ArrayList<SnapshotArtifact>();
        var missions = DebugMissionCatalog.missions(null);
        for (int group = 0; group < 3; group++) {
            final int filter = group;
            var filtered = DebugMissionScreen.visible(missions, group, "All locations");
            for (int page = 0; page * 8 < filtered.size(); page++) {
                var selected = filtered.get(page * 8);
                int[] clicked = {0};
                var props = DebugMissionScreen.model(missions, selected, group, page, "All locations", place -> clicked[0]++, m -> clicked[0]++, i -> clicked[0]++, i -> clicked[0]++, () -> clicked[0]++, () -> clicked[0]++);
                var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), DebugMissionScreen.COMPONENTS);
                loader.reload();
                try (var instance = loader.build(new Reactor(), DebugMissionScreen.ROOT, props)) {
                    var document = new UiDocument(instance.root());
                    for (var style : instance.styles()) document.addStyleSheet(style);
                    document.theme(MarineOpsThemes.standard());
                    frames.add(new SnapshotArtifact("0" + group + "-page-" + page + ".png", renderer.renderRelative(document, 1920, 1080, 1f,
                            MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
                    var launch = instance.requireElement("debug-launch");
                    assertTrue(launch.box().borderBox().bottom() < instance.root().box().borderBox().bottom());
                    click(document, launch);
                    click(document, instance.requireElement("debug-back"));
                    click(document, instance.requireElement("debug-card-" + page * 8));
                    click(document, instance.requireElement("debug-place-0"));
                    assertEquals(4, clicked[0], "Launch, back, card selection and planet filtering are wired");
                }
            }
        }
        var hqProps = CompanyHqViewModel.preview().props();
        hqProps.put("debugClasses", "hq-debug console-action");
        int[] opened = {0};
        hqProps.put("debugAction", (Runnable) () -> opened[0]++);
        var reactor = new Reactor();
        new CompanyHqNewsDesk(reactor).put(hqProps);
        var hqLoader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                List.of("data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/company-hq.mlx"));
        hqLoader.reload();
        try (var instance = hqLoader.build(reactor, "company-hq", hqProps)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact("hq-entry.png", renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
            click(document, instance.requireElement("company-hq-debug"));
            assertEquals(1, opened[0]);
        }
        return frames;
    }
}
