package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.dillon.starsectormarines.ops.mission.story.OpeningOperationStory;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

/** First-work guidance through the production HQ at 1920x1080, 100%. */
public final class FirstContractSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "first-contract"; }
    @Override public String label() { return "Reliable first-contract guidance"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> frames = new ArrayList<>();
        MarineRoster roster = new MarineRoster();
        for (String state : List.of("fresh-company", "first-squad", "follow-up", "completed")) {
            if (state.equals("first-squad")) roster.bootstrapInitialComplement(12);
            if (state.equals("follow-up")) roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
            if (state.equals("completed")) roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
            if (state.equals("first-squad") || state.equals("follow-up")) {
                frames.add(new SnapshotArtifact("broker-" + state + ".png", renderBroker(context, renderer, roster)));
            }
            frames.add(new SnapshotArtifact("hq-" + state + ".png", UiSnapshotSuite.renderCompanyHq(
                    context, renderer, 1920, 1080, 1f, CompanyHqViewModel.openingPreview(roster))));
        }
        MarineRoster fresh = new MarineRoster();
        frames.add(new SnapshotArtifact("hq-fallback.png", UiSnapshotSuite.renderCompanyHq(context, renderer,
                1920, 1080, 1f, CompanyHqViewModel.openingPreview(fresh,
                new OpeningBroker.Destination("fixture-port", "Independent Port", "Outer System", null), () -> { }))));
        frames.add(new SnapshotArtifact("hq-unavailable.png", UiSnapshotSuite.renderCompanyHq(context, renderer,
                1920, 1080, 1f, CompanyHqViewModel.openingPreview(fresh, null, () -> { }))));
        frames.add(new SnapshotArtifact("broker-referral.png", renderBroker(context, renderer, fresh, true)));
        return frames;
    }

    private BufferedImage renderBroker(SnapshotContext context, HeadlessUiRenderer renderer, MarineRoster roster) throws Exception {
        return renderBroker(context, renderer, roster, false);
    }

    private BufferedImage renderBroker(SnapshotContext context, HeadlessUiRenderer renderer, MarineRoster roster,
                                       boolean referral) throws Exception {
        PlanetAPI planet = (PlanetAPI) Proxy.newProxyInstance(PlanetAPI.class.getClassLoader(),
                new Class<?>[]{PlanetAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> "jangala";
                    case "getName" -> "Jangala";
                    default -> null;
                });
        Client client = OpeningBroker.present(new Client("independent", "Independent", OpeningBroker.CREST,
                RepLevel.NEUTRAL, false, null), true);
        StoryEligibilityContext eligibility = new StoryEligibilityContext(planet, client, null, roster, 17L, true);
        List<Mission> missions = new ArrayList<>();
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            OpeningOperationStory story = new OpeningOperationStory(kind);
            if (story.isEligible(eligibility)) missions.add(story.build(eligibility));
        }
        Mission selected = missions.get(missions.size() - 1);
        Map<String, Object> props = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-venn", "client-venn-name",
                "client-venn-rep", client.crestPath, "client-card selected", client.displayName,
                "Independent broker", false, none)));
        props.put("missionHeader", client.displayName);
        props.put("missionSummary", "Wayline cooperative / militia support / repeatable work");
        props.put("missions", MissionSelectScreen.missionRows(missions, selected, mission -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putBroker(props, client);
        MissionSelectScreen.putDetail(props, selected, none, none);
        if (referral) {
            Client remote = new Client("independent", "Independent brokers", OpeningBroker.CREST,
                    RepLevel.NEUTRAL, false, null);
            props.put("locationLabel", "Another populated planet / Referral fixture");
            props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-remote", "client-remote-name",
                    "client-remote-rep", remote.crestPath, "client-card selected", remote.displayName,
                    "Neutral", false, none)));
            props.put("missionHeader", remote.displayName);
            props.put("missionSummary", "New company referral / " + OpeningBroker.NAME);
            props.put("missions", List.of());
            MissionSelectScreen.putBroker(props, remote);
            MissionSelectScreen.putDetail(props, null, none, none);
            OpeningBroker.Destination office = new OpeningBroker.Destination("jangala", "Jangala", "Corvus", null);
            MissionSelectScreen.putReferral(props, office, OpeningBroker.referral(remote, roster, office));
        }
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (MarkupInstance instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
        }
    }
}
