package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.campaign.RepLevel;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * The mission board's "Replay Story Missions" toggle, off and on, at Jangala for a company midway through the
 * campaign: its Asharu and Agreus chapters stay off this board.
 */
public final class ReplayCompletedSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "replay-completed"; }
    @Override public String label() { return "Mission board: Replay Story Missions toggle and the Company records contact"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var roster = new MarineRoster();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        roster.markStoryComplete(BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.JUNCTION + "_0_0");
        roster.markStoryComplete(BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.THUNDER + "_0_0");
        roster.markStoryComplete(ShepherdRecoveryProgress.PREFIX + "0");
        roster.markStoryComplete(AgreusArcProgress.PREFIX + "0");
        // Headless there is no sector to place the opening and the route, so both stand at Jangala as on a fresh start.
        List<Mission> replays = CompletedReplays.offers(roster, "jangala", "Jangala", scenario -> switch (scenario) {
            case RELIEF, COUNTERATTACK, JUNCTION, THUNDER_RUN -> "jangala";
            default -> CompletedReplays.fixedSiteId(scenario);
        });
        Runnable none = () -> { };
        var broker = OpeningBroker.present(new Client("independent", "Independent", OpeningBroker.CREST,
                RepLevel.NEUTRAL, false, null), true);
        var venn = new MissionSelectScreen.ClientRow("client-0", "client-0-name", "client-0-rep",
                broker.crestPath, "client-card", broker.displayName, "Independent broker", false, none);
        var records = CompletedReplays.client();

        var off = MissionSelectScreen.previewProps();
        off.put("locationLabel", "Jangala  ·  Current local offers");
        off.put("clients", List.of(venn));
        off.put("missionHeader", Strings.get("missionSelectContracts"));
        off.put("missionSummary", MissionSelectScreen.missionSummary(null, List.of()));
        off.put("missions", List.of());
        off.put("stationingRows", List.of());
        off.put("missionEmpty", Strings.get("missionSelectChooseAClientToInspectTheirCurrent"));
        MissionSelectScreen.putDetail(off, null, none, none);
        MissionSelectScreen.putReplayToggle(off, true, false, none);

        var on = MissionSelectScreen.previewProps();
        Mission selected = replays.get(2);
        on.put("locationLabel", "Jangala  ·  Current local offers");
        on.put("clients", List.of(venn, new MissionSelectScreen.ClientRow("client-replay", "client-replay-name",
                "client-replay-rep", records.crestPath, "client-card selected", records.displayName,
                "Completed campaign missions", false, none)));
        on.put("missionHeader", records.displayName);
        on.put("missionSummary", MissionSelectScreen.missionSummary(records, replays));
        on.put("missions", MissionSelectScreen.missionRows(replays, selected, ignored -> { }));
        on.put("stationingRows", List.of());
        on.put("missionEmpty", "");
        MissionSelectScreen.putBroker(on, records, roster);
        MissionSelectScreen.putDetail(on, selected, none, none);
        MissionSelectScreen.putReplayToggle(on, true, true, none);

        return List.of(board(context, renderer, off, "1-toggle-off.png"), board(context, renderer, on, "2-toggle-on.png"));
    }

    private static SnapshotArtifact board(SnapshotContext context, HeadlessUiRenderer renderer,
                                          Map<String, Object> props, String filename) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return new SnapshotArtifact(filename, renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }
}
