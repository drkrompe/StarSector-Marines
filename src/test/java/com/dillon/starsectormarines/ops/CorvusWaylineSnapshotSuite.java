package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.campaign.RepLevel;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production construction and launch-overlay evidence, never an ordinary unit test. */
public final class CorvusWaylineSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "wayline-corvus"; }
    @Override public String label() { return "Wayline across Corvus: chapter boards and production battlefields"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var battlefield = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080);
        var artifacts = new ArrayList<SnapshotArtifact>();
        var progress = new CorvusWaylineProgress();
        var route = CorvusWaylineCampaignTest.closedRoute(2, true);
        var report = new StringBuilder("Production chapter construction; named roster overlay; bounded 60-second observation.\n");
        for (CorvusChapter chapter : CorvusChapter.values()) {
            Mission mission = CorvusWaylineCampaign.offer(progress, chapter.planetName, route);
            String stem = (chapter.ordinal() + 1) + "-" + chapter.name().toLowerCase();
            artifacts.add(board(context, renderer, mission, route, stem + "-briefing.png"));
            var roster = new MarineRoster();
            roster.ensureActiveSoldiers(chapter.squads * 12);
            var selection = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
            var deployment = CampaignMarineDeployment.freezeSelection(roster, selection, chapter.squads * 12);
            var boats = List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE), FittedBoat.standard(ShuttleType.AEROSHUTTLE));
            var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
            var fixture = chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface));
            try (var sim = fixture.build()) {
                var overlay = BattleLaunchOverlay.capture(0, deployment, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                        List.of(), 0, mission.fieldPresencePolicy);
                overlay.applyTo(sim, CommandPowerResources.FREE);
                var named = new HashSet<String>();
                int craft = 0;
                for (long air : sim.getAirEntityIds()) {
                    if (sim.world().airFaction(air) != Faction.MARINE) continue;
                    craft++;
                    assertSame(boats.get(craft - 1), sim.world().airframe(air));
                    for (var cycle : sim.world().mission(air).cycleLoadouts) for (var seat : cycle) {
                        assertNotNull(seat.campaignSoldierId);
                        assertTrue(named.add(seat.campaignSoldierId));
                    }
                }
                assertEquals(deployment.size(), named.size());
                assertEquals(boats.size(), craft);
                assertEquals(FieldPresencePolicy.UNRESTRICTED, mission.fieldPresencePolicy);
                int defenders = 0;
                for (int i = 0; i < sim.liveUnitCount(); i++)
                    if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.DEFENDER) defenders++;
                artifacts.add(new SnapshotArtifact(stem + "-map.png", battlefield.render(sim, chapter.planetName + " / " + chapter.title)));
                int ticks = 0;
                while (ticks < 1800 && !sim.isComplete()) { sim.advance(BattleSimulation.TICK_DT); ticks++; }
                artifacts.add(new SnapshotArtifact(stem + "-arrival.png", battlefield.render(sim, chapter.title + " / 60 SECOND OBSERVATION")));
                report.append(chapter.title).append(": ").append(named.size()).append(" named seats / ")
                        .append(craft).append(" company craft / ").append(defenders).append(" initial defenders / ")
                        .append(ticks).append(" observed ticks / terminal=").append(sim.isComplete()).append('\n');
            }
            var hq = CompanyHqViewModel.corvusPreview(roster, new CorvusWaylineWorkGuide(progress,
                    new OpeningBroker.Destination(chapter.planetId, chapter.planetName, "Corvus", null), () -> { }));
            artifacts.add(new SnapshotArtifact(stem + "-hq.png",
                    UiSnapshotSuite.renderCompanyHq(context, renderer, 1920, 1080, 1f, hq)));
            progress.begin(mission.id, chapter.planetName);
            progress.settle(mission.id, chapter.planetName, true, chapter.payment);
        }
        Files.createDirectories(context.projectRoot().resolve("build/reports/wayline-corvus"));
        Files.writeString(context.projectRoot().resolve("build/reports/wayline-corvus/construction.txt"), report);
        return artifacts;
    }

    private SnapshotArtifact board(SnapshotContext context, HeadlessUiRenderer renderer, Mission mission,
                                   BrokenSupplyRouteProgress route, String filename) throws Exception {
        var roster = new MarineRoster();
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        var client = OpeningBroker.present(new Client("independent", "Independent", OpeningBroker.CREST, RepLevel.NEUTRAL, false, null), true);
        var props = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        props.put("locationLabel", mission.targetPlanetName + " / Corvus");
        props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-venn", "client-venn-name", "client-venn-rep",
                client.crestPath, "client-card selected", client.displayName, "Wayline cooperative", false, none)));
        props.put("missionHeader", client.displayName);
        props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putBroker(props, client, roster);
        MissionSelectScreen.putDetail(props, mission, none, none);
        MissionSelectScreen.putCorvus(props, route, mission);
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
