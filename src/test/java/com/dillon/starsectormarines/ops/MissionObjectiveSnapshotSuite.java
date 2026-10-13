package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.SquadActivity;
import com.dillon.starsectormarines.battle.fixture.CivilianRescueBattleFixture;
import com.dillon.starsectormarines.battle.fixture.ExtractionBattleFixture;
import com.dillon.starsectormarines.battle.fixture.RaidBattleFixture;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
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

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Production initial-state wiring and explicitly controlled UI stress fixtures. Not balance evidence. */
public final class MissionObjectiveSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "mission-objectives"; }
    @Override public String label() { return "Shared mission objectives: recovery, territorial capture, raid, sabotage and extraction"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        var artifacts = new ArrayList<SnapshotArtifact>();
        try (var sim = new ExtractionBattleFixture(141418L, manifest, false, OperationTier.ESTABLISHED,
                RiskLevel.MEDIUM, TargetProfile.NEUTRAL, List.of(), List.of()).build()) {
            capture(context, artifacts, "extraction", sim, false);
        }
        try (var sim = new CivilianRescueBattleFixture(299L, manifest, false, RiskLevel.LOW, 12, TargetProfile.NEUTRAL, false).build()) {
            capture(context, artifacts, "rescue", sim, false);
        }
        try (var sim = BattleSetup.createLastLift(9201L, manifest, TargetProfile.NEUTRAL, new LastLiftProgress("rill-preview").previewAttempt())) {
            capture(context, artifacts, "last-lift", sim, false);
            var objective = MissionObjectiveProjection.capture(sim).objectives().get(0);
            assertEquals("Motor pool shelter", objective.focus().name());
            assertTrue(objective.markers().stream().anyMatch(m -> m.name().equals("Southern apron pickup")));
        }
        try (var sim = BattleSetup.createSilentColony(7231L, 414L, 8, manifest, RiskLevel.LOW)) {
            capture(context, artifacts, "silent-colony", sim, true);
            for (int tick = 0; tick < 1800 && !sim.isComplete(); tick++) sim.advance(BattleSimulation.TICK_DT);
            assertFalse(MissionObjectiveProjection.capture(sim).squads().isEmpty(), "deployed colony squads publish normal player-facing tasks");
            capture(context, artifacts, "silent-colony-deployed", sim, false);
        }
        try (var sim = BattleSetup.createSalvageRights(14101L, manifest, TargetProfile.NEUTRAL)) {
            capture(context, artifacts, "salvage-rights-initial", sim, false);
            var progress = sim.getSalvageRights().progress();
            progress.advance(4, true, false, false, true, false);
            progress.advance(2, true, false, false, true, false);
            capture(context, artifacts, "salvage-workshop-progress-fixture", sim, false);
            progress.advance(1, true, true, false, true, false);
            capture(context, artifacts, "salvage-workshop-contested-fixture", sim, false);
            progress.advance(4, true, false, false, true, false);
            progress.advance(4, true, false, true, true, false);
            progress.advance(60, true, false, true, true, false);
            capture(context, artifacts, "salvage-inspection-response-fixture", sim, false);
        }
        try (var sim = BattleSetup.createColdStart(14101L, manifest, TargetProfile.NEUTRAL, true, false)) {
            capture(context, artifacts, "cold-start-initial", sim, false);
            var progress = sim.getColdStart().progress();
            progress.advance(17.5f, true, true, true, true, true, false, false, false);
            capture(context, artifacts, "cold-start-power-progress-fixture", sim, false);
            progress.advance(17.5f, true, true, true, true, true, false, false, false);
            capture(context, artifacts, "cold-start-construction-fixture", sim, false);
        }
        try (var sim = BattleSetup.createJunction(7101L, manifest, TargetProfile.NEUTRAL)) {
            capture(context, artifacts, "junction-initial", sim, false);
            var record = sim.getCompoundService().getRecords().stream()
                    .filter(r -> r.node.defaultGuard != Faction.MARINE).findFirst().orElseThrow();
            record.state = CompoundService.CompoundState.CONTESTED;
            record.attackersPresent = true;
            record.defendersPresent = false;
            record.captureProgress = .5f;
            record.holdTimer = 2f;
            capture(context, artifacts, "junction-control-progress-fixture", sim, false);
        }
        try (var sim = new RaidBattleFixture(141418L, manifest, false, OperationTier.ESTABLISHED,
                RiskLevel.LOW, TargetProfile.NEUTRAL, List.of(), List.of(), null).build()) {
            capture(context, artifacts, "raid-initial", sim, false);
        }
        try (var sim = new SabotageBattleFixture(141418L, manifest, false, OperationTier.ESTABLISHED,
                RiskLevel.LOW, TargetProfile.NEUTRAL, List.of(), List.of()).build()) {
            capture(context, artifacts, "sabotage-initial", sim, false);
        }
        return artifacts;
    }

    private void capture(SnapshotContext context, List<SnapshotArtifact> artifacts, String name,
                         BattleSimulation sim, boolean stress) throws Exception {
        var picture = MissionObjectiveProjection.capture(sim);
        assertFalse(picture.objectives().isEmpty());
        var survey = BriefingSurvey.capture(sim);
        for (var item : picture.objectives()) for (var marker : item.markers()) {
            assertTrue(sim.getGrid().inBounds(marker.x(), marker.y()));
            // The briefing is an initial survey; controlled later-phase fixtures can introduce new live markers.
            if (!name.contains("fixture") && (name.equals("extraction") || name.equals("rescue")
                    || name.equals("last-lift") || name.equals("silent-colony")))
                assertTrue(survey.markers().stream().anyMatch(m -> m.label().equals(marker.name())
                        && m.x() == marker.x() && m.y() == marker.y()), "briefing and live map share exact named locations");
        }
        var background = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080).render(sim,
                (name.contains("fixture") ? "CONTROLLED PUBLIC OBJECTIVE FACTS / INITIAL PRODUCTION MAP"
                        : sim.getSimTickIndex() == 0 ? "INITIAL OBJECTIVE WIRING" : "SIMULATED OBJECTIVE PROGRESS / tick " + sim.getSimTickIndex()));
        renderHud(context, artifacts, name, picture, background, 1896);
        if (stress) {
            assertEquals(2, picture.objectives().size());
            List<Item> items = new ArrayList<>(picture.objectives());
            Item archive = items.get(0), survivors = items.get(1);
            items.set(0, new Item(archive.id(), archive.name(), MissionObjectiveSnapshot.State.COMPLETE, "COMPLETE",
                    "Recover archive", "Archive recovered. Recovery squads can rejoin the escort.", "Objective complete", 1, "", archive.markers(), archive.focusMarkerId()));
            items.set(1, new Item(survivors.id(), survivors.name(), MissionObjectiveSnapshot.State.ACTIVE, "ESCORTING",
                    "Secure: done  >  Escort  >  Board", "Escort every remaining survivor to the pickup.",
                    "2 aboard / 5 awaiting rescue / 1 lost", .25f,
                    "2 need close escort; 1 has no route; 3 waiting for pickup", survivors.markers(), survivors.focusMarkerId()));
            List<SquadActivity> squads = new ArrayList<>();
            for (int i = 0; i < 8; i++) squads.add(new SquadActivity(i, "Kestrel " + (i + 1),
                    i == 0 ? "Escorting survivors" : "Protecting the left flank", i % 2 == 0 ? "Holding for local contact" : "Assigned task"));
            var fixture = new MissionObjectiveSnapshot(items, squads);
            renderHud(context, artifacts, "colony-ui-stress-fixture", fixture, background, 1896);
        }
    }

    private void renderHud(SnapshotContext context, List<SnapshotArtifact> artifacts, String name,
                           MissionObjectiveSnapshot picture, BufferedImage background, int width) throws Exception {
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of());
        var presentation = model.updateMission(picture, ignored -> { });
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            BattleHudOverlay.wireLayout(instance);
            var doc = new UiDocument(instance.root());
            instance.styles().forEach(doc::addStyleSheet);
            doc.theme(MarineOpsThemes.standard());
            var hud = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore()).render(doc, width,
                    Math.round(BattleHudOverlay.documentHeight(presentation)));
            var image = new BufferedImage(width + 24, 1080, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            g.drawImage(background, 0, 0, image.getWidth(), image.getHeight(), null);
            g.drawImage(hud, 12, 42, null);
            if (name.contains("fixture")) {
                g.setColor(Color.BLACK); g.fillRect(0, 0, 1920, 30);
                g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g.drawString("CONTROLLED PUBLIC OBJECTIVE FIXTURE / 1920 x 1080 / 100% UI / initial production map", 12, 20);
            }
            g.dispose();
            artifacts.add(new SnapshotArtifact(name + ".png", image));
        }
    }
}
