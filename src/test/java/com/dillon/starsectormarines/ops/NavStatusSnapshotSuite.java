package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import org.json.JSONObject;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Opt-in: the look study for a room's mark on the page bar, on the real Company HQ at 1920x1080, 100%, with wounded in
 * Barracks and a damaged machine and shuttle. {@code symbol} is what ships ({@link RoomAttention}); the others are
 * drawn over the production bar for the owner's pick, and go with this suite once it is answered.
 */
public final class NavStatusSnapshotSuite implements SnapshotSuite {
    private static final List<String> ROOMS = List.of("barracks", "mech-lab", "boats");
    private static final List<String> COUNTS = List.of("3", "1", "1");

    @Override public String id() { return "nav-status"; }
    @Override public String label() { return "Page bar: a room's status mark, options"; }
    @Override public boolean inAll() { return false; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        var frames = new ArrayList<SnapshotArtifact>();
        try (var strings = new HeadlessStrings(context.modRoot())) {
            var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                    context.modRoot(), context.starsectorCore());
            frames.add(frame(context, renderer, "01-symbol.png", NavStatusSnapshotSuite::symbol));
            frames.add(frame(context, renderer, "02-symbol-count.png", NavStatusSnapshotSuite::symbolAndCount));
            frames.add(frame(context, renderer, "03-lamp.png", NavStatusSnapshotSuite::lamp));
            frames.add(frame(context, renderer, "04-none.png", markup -> { }));
        }
        return frames;
    }

    private static SnapshotArtifact frame(SnapshotContext context, HeadlessUiRenderer renderer, String name,
                                          Consumer<MarkupInstance> marks) throws Exception {
        UiSnapshotSuite.navOverlay = marks;
        try {
            return new SnapshotArtifact(name, UiSnapshotSuite.renderCompanyHq(context, renderer, 1920, 1080, 1f,
                    CompanyHqViewModel.preview()));
        } finally {
            UiSnapshotSuite.navOverlay = null;
        }
    }

    private static void symbol(MarkupInstance markup) {
        for (String room : ROOMS) markup.requireElement("page-nav-" + room).classed("page-nav-attention", true);
    }

    private static void symbolAndCount(MarkupInstance markup) {
        symbol(markup);
        for (int i = 0; i < ROOMS.size(); i++) {
            markup.requireElement("page-nav-" + ROOMS.get(i)).classed("page-nav-new", true);
            markup.requireElement("page-nav-" + ROOMS.get(i) + "-news").text(COUNTS.get(i));
        }
    }

    private static void lamp(MarkupInstance markup) {
        for (String room : ROOMS) {
            markup.requireElement("page-nav-" + room + "-lamp")
                    .style("display: flex; width: 9px; height: 9px; background-color: #f2b33d");
        }
    }
}
