package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;

/** Production art placements at 1920x1080 and 100% scale, without a live campaign. */
public final class StoryArtSnapshotSuite implements SnapshotSuite {
    public String id() { return "story-art"; }
    public String label() { return "Shared story mission artwork"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var frames = new ArrayList<SnapshotArtifact>();
        var seen = new HashSet<StoryScene>();
        var missions = DebugMissionCatalog.missions(null);
        for (var scenario : DebugMissionCatalog.Scenario.values()) {
            var debug = scenario.mission();
            var mission = StorySceneTest.campaign(scenario);
            if (!seen.add(StoryScene.forMission(mission))) continue;
            String name = scenario.name().toLowerCase(Locale.ROOT).replace('_', '-');
            var visible = DebugMissionScreen.visible(missions, 0, "All locations");
            int index = 0;
            for (int i = 0; i < visible.size(); i++) if (visible.get(i).id.equals(debug.id)) index = i;
            var debugProps = DebugMissionScreen.model(missions, debug, 0, index / 8, "All locations",
                    s -> {}, m -> {}, i -> {}, i -> {}, () -> {}, () -> {});
            frames.add(render(context, renderer, name + "-debug", DebugMissionScreen.COMPONENTS,
                    DebugMissionScreen.ROOT, debugProps));
            var props = MissionSelectScreen.previewProps();
            props.put("missionHeader", "Mission location file");
            props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, m -> {}));
            props.put("stationingRows", List.of());
            MissionSelectScreen.putDetail(props, mission, () -> {}, () -> {});
            frames.add(render(context, renderer, name + "-briefing", MissionSelectScreen.COMPONENT_PATHS,
                    MissionSelectScreen.ROOT_COMPONENT, props));
            var result = ResultsScreen.previewProps(true, false);
            result.put("missionName", mission.name);
            ResultsScreen.putStoryDebrief(result, MissionOutcome.builder().mission(mission).victory(true).build());
            frames.add(render(context, renderer, name + "-result", ResultsScreen.COMPONENT_PATHS,
                    ResultsScreen.ROOT_COMPONENT, result));
        }
        return frames;
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
