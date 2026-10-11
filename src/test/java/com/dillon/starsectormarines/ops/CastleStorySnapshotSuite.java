package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Production dialogue surfaces at 1920x1080 and 100%; no live battle is implied. */
public final class CastleStorySnapshotSuite implements SnapshotSuite {
    public String id() { return "castle-story"; }
    public String label() { return "Castle / Wayward recovery"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var catalog = WaylineNarration.parse(new JSONObject(Files.readString(context.modRoot().resolve(WaylineNarration.PATH))));
        var artifacts = new ArrayList<SnapshotArtifact>();
        for (boolean disabled : new boolean[]{false, true}) {
            var outcome = MissionOutcome.builder().missionId(OpeningOperationKind.COUNTERATTACK.missionId)
                    .missionName("TAKE BACK THE DEPOT").missionSource(MissionSource.STORY).victory(true)
                    .payoutEarned(9000).marinesEngaged(12).marinesLost(2).alliesEngaged(12).alliesLost(3)
                    .waylineHound(new WaylineHoundRecovery.Report(true, disabled)).build();
            var props = ResultsScreen.previewProps(true, false);
            props.put("missionName", outcome.missionName);
            props.put("squadRows", List.of(new ResultsScreen.PersonnelRow("result-company", "1st Squad",
                    "10R 0W 0M 2K", "label heading results-squad-summary tone-danger",
                    "10 returned / 2 killed")));
            props.put("resultRows", ResultsScreen.resultRows(outcome, LootManifest.EMPTY, ModStrings.fromDisk(context.modRoot())));
            ResultsScreen.putStoryDebrief(props, outcome);
            ResultsScreen.putSalvageConversation(props, HoundSalvageDebrief.lines(outcome, catalog));
            var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), ResultsScreen.COMPONENT_PATHS);
            loader.reload();
            try (var markup = loader.build(new Reactor(), ResultsScreen.ROOT_COMPONENT, props)) {
                var document = new UiDocument(markup.root());
                for (var style : markup.styles()) document.addStyleSheet(style);
                document.theme(MarineOpsThemes.standard());
                artifacts.add(new SnapshotArtifact(disabled ? "salvage-repaired.png" : "salvage-transfer.png",
                        renderer.renderRelative(document, 1920, 1080, 1f,
                                MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
            }
        }
        var line = catalog.find(OpeningOperationKind.COUNTERATTACK.missionId, WaylineHoundRecovery.READY);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(StorySubtitleOverlay.COMPONENT_PATH));
        loader.reload();
        try (var markup = loader.build(new Reactor(), StorySubtitleOverlay.COMPONENT,
                Map.of("speaker", line.speaker(), "portrait", line.portrait(), "subtitle", line.text()))) {
            var document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            BufferedImage frame = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
            var graphics = frame.createGraphics();
            graphics.setColor(new Color(21, 28, 26)); graphics.fillRect(0, 0, 1920, 1080);
            graphics.drawImage(renderer.render(document, 640, 116), 12, 12, null);
            graphics.dispose();
            artifacts.add(new SnapshotArtifact("castle-ready-subtitle.png", frame));
        }
        return artifacts;
    }
}
