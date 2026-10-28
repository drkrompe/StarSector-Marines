package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress.Chapter;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
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

/** The Wellhollow Bloom's Warden and location art on Warden Maro's board and the results screen, at 1920x1080. */
public final class WellhollowArtSnapshotSuite implements SnapshotSuite {
    public String id() { return "wellhollow-art"; }
    public String label() { return "The Wellhollow Bloom: Warden portrait and location art"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var frames = new ArrayList<SnapshotArtifact>();
        for (Chapter chapter : List.of(Chapter.COLONY_BREACH, Chapter.CORDON_LINE, Chapter.THE_BURN)) {
            var progress = wonThrough(chapter.ordinal());
            var offers = WellhollowBloomCampaign.offers(progress, "Asharu");
            Mission mission = offers.get(0);
            String name = String.format("%02d-%s", chapter.number(), chapter.code);
            var props = MissionSelectScreen.previewProps();
            props.put("missionHeader", "Hollis Maro");
            props.put("missions", MissionSelectScreen.missionRows(offers, mission, m -> {}));
            props.put("stationingRows", List.of());
            MissionSelectScreen.putDetail(props, mission, () -> {}, () -> {});
            frames.add(render(context, renderer, name + "-board", MissionSelectScreen.COMPONENT_PATHS,
                    MissionSelectScreen.ROOT_COMPONENT, props));
            var result = ResultsScreen.previewProps(true, false);
            result.put("missionName", mission.name);
            ResultsScreen.putStoryDebrief(result, MissionOutcome.builder().mission(mission).victory(true).build());
            frames.add(render(context, renderer, name + "-result", ResultsScreen.COMPONENT_PATHS,
                    ResultsScreen.ROOT_COMPONENT, result));
        }
        return frames;
    }

    private static WellhollowBloomProgress wonThrough(int chapters) {
        var p = new WellhollowBloomProgress();
        for (int i = 0; i < chapters; i++) {
            String id = p.missionId(p.current(), false);
            p.begin(id);
            p.settle(id, true, WellhollowBloomProgress.fee(id));
        }
        return p;
    }

    private SnapshotArtifact render(SnapshotContext context, HeadlessUiRenderer renderer, String name,
                                    List<String> components, String root, Map<String, Object> props) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), components);
        loader.reload();
        try (var instance = loader.build(new Reactor(), root, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return new SnapshotArtifact(name + ".png", renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }
}
